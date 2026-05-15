package com.nssi.salangikopu.Activity;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import android.view.WindowManager;
import androidx.appcompat.app.AppCompatActivity;
import android.view.inputmethod.InputMethodManager;

import com.nssi.salangikopu.Adapter.StoreInventoryAdapter;
import com.nssi.salangikopu.Connection.ENV;
import com.nssi.salangikopu.Model.StoreInventoryItem;
import com.nssi.salangikopu.R;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class StoreInventoryActivity extends AppCompatActivity {

    private long lastScanTime = 0;
    private static final long DEBOUNCE_MS = 100;

    private static final long SCAN_RESET_DELAY_MS = 60_000L;
    private final Handler resetHandler = new Handler(Looper.getMainLooper());
    private Runnable resetRunnable = null;

    ListView listStoreInventory;
    ProgressBar progressBar;
    EditText etSearch;
    TextView tvStoreName;
    TextView tvTotalItems, tvTotalCost;
    Spinner spinnerSnapshot;
    TextView tvNoAccess;

    StoreInventoryAdapter adapter;
    List<StoreInventoryItem> allItems = new ArrayList<>();
    List<StoreInventoryItem> filteredItems = new ArrayList<>();
    Map<Integer, String> snapshotMap = new LinkedHashMap<>();
    List<Integer> snapshotIds = new ArrayList<>();

    int userStoreId = 0;
    String userStoreName = "";
    int selectedSnapshotId = -1;

//    DecimalFormat costFmt = new DecimalFormat("#,##0.00");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_store_inventory);

        listStoreInventory = findViewById(R.id.listStoreInventory);
        progressBar = findViewById(R.id.progressBar);
        etSearch = findViewById(R.id.etSearch);
        tvStoreName = findViewById(R.id.tvStoreName);
        tvTotalItems = findViewById(R.id.tvTotalItems);
        tvNoAccess = findViewById(R.id.tvNoAccess);
        spinnerSnapshot = findViewById(R.id.spinnerSnapshot); // ADD THIS

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        userStoreId = getIntent().getIntExtra("store_id", 0);
        userStoreName = getIntent().getStringExtra("store_name");
        if (userStoreName == null) userStoreName = "";

        if (userStoreId <= 0) {
            tvStoreName.setText("Store Inventory");
            tvNoAccess.setVisibility(View.VISIBLE);
            listStoreInventory.setVisibility(View.GONE);
            tvTotalItems.setVisibility(View.GONE);
            tvTotalCost.setVisibility(View.GONE);
            spinnerSnapshot.setVisibility(View.GONE);
            return;
        }

        getWindow().setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
        );

        tvStoreName.setText(userStoreName);
        tvNoAccess.setVisibility(View.GONE);

        adapter = new StoreInventoryAdapter(this, filteredItems);
        listStoreInventory.setAdapter(adapter);

        loadSnapshots();

        etSearch.requestFocus();

        etSearch.setOnEditorActionListener((v, actionId, event) -> {
            handleScan();
            return true;
        });

        etSearch.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_UP
                    && keyCode == KeyEvent.KEYCODE_ENTER) {
                handleScan();
                return true;
            }
            return false;
        });
    }

    private void handleScan() {
        long now = System.currentTimeMillis();
        if (now - lastScanTime < DEBOUNCE_MS) {
            etSearch.setText("");
            return;
        }
        lastScanTime = now;

        String raw = etSearch.getText().toString().trim();
        etSearch.setText("");

        if (!raw.isEmpty()) {
            String code = resolveProductCode(raw);
            filterList(code);
            scheduleAutoReset();
        }
    }

    private void scheduleAutoReset() {
        if (resetRunnable != null) resetHandler.removeCallbacks(resetRunnable);
        resetRunnable = () -> {
            filterList("");
            etSearch.requestFocus();
        };
        resetHandler.postDelayed(resetRunnable, SCAN_RESET_DELAY_MS);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (resetRunnable != null) resetHandler.removeCallbacks(resetRunnable);
    }

    private void loadSnapshots() {
        progressBar.setVisibility(View.VISIBLE);

        new Thread(() -> {
            HttpURLConnection conn = null;

            try {
                URL url = new URL(ENV.STORE_SNAPSHOTS_URL + "?store_id=" + userStoreId);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Accept", "application/json");
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);

                int statusCode = conn.getResponseCode();
                InputStream is = (statusCode >= 200 && statusCode < 300)
                        ? conn.getInputStream()
                        : conn.getErrorStream();

                if (is == null) {
                    throw new Exception("Empty server response");
                }

                BufferedReader reader = new BufferedReader(new InputStreamReader(is));
                StringBuilder res = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    res.append(line);
                }
                reader.close();

                JSONObject json = new JSONObject(res.toString());
                boolean success = json.optBoolean("success", false);

                if (!success) {
                    throw new Exception(json.optString("message", "Failed to load snapshots"));
                }

                snapshotMap.clear();
                snapshotIds.clear();

                snapshotMap.put(-1, "Latest");
                snapshotIds.add(-1);

                JSONArray arr = json.getJSONArray("snapshots");
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject s = arr.getJSONObject(i);
                    int id = s.getInt("snapshot_id");
                    String date = s.getString("as_of_date");
                    snapshotMap.put(id, date);
                    snapshotIds.add(id);
                }

                List<String> labels = new ArrayList<>(snapshotMap.values());

                runOnUiThread(() -> {
                    ArrayAdapter<String> sa = new ArrayAdapter<>(
                            this,
                            android.R.layout.simple_spinner_item,
                            labels
                    );
                    sa.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                    spinnerSnapshot.setAdapter(sa);
                    spinnerSnapshot.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                        @Override
                        public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                            selectedSnapshotId = snapshotIds.get(pos);
                            loadInventory(selectedSnapshotId);
                        }

                        @Override
                        public void onNothingSelected(AdapterView<?> p) {
                        }
                    });
                });

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Failed to load snapshots: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            } finally {
                if (conn != null) conn.disconnect();
            }
        }).start();
    }

    private void loadInventory(int snapshotId) {
        progressBar.setVisibility(View.VISIBLE);

        new Thread(() -> {
            HttpURLConnection conn = null;

            try {
                URL url = new URL(
                        ENV.STORE_INVENTORY_URL
                                + "?store_id=" + userStoreId
                                + "&snapshot_id=" + snapshotId
                );

                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Accept", "application/json");
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);

                int statusCode = conn.getResponseCode();
                InputStream is = (statusCode >= 200 && statusCode < 300)
                        ? conn.getInputStream()
                        : conn.getErrorStream();

                if (is == null) {
                    throw new Exception("Empty server response");
                }

                BufferedReader reader = new BufferedReader(new InputStreamReader(is));
                StringBuilder res = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    res.append(line);
                }
                reader.close();

                JSONObject json = new JSONObject(res.toString());
                boolean success = json.optBoolean("success", false);

                if (!success) {
                    throw new Exception(json.optString("message", "Failed to load inventory"));
                }

                JSONArray arr = json.getJSONArray("items");
                allItems.clear();

                for (int i = 0; i < arr.length(); i++) {
                    JSONObject row = arr.getJSONObject(i);
                    allItems.add(new StoreInventoryItem(
                            row.getString("product_code"),
                            row.getString("item_description"),
                            row.getDouble("quantity_in_stock"),
                            row.getDouble("unit_price"),
                            row.getDouble("total_cost"),
                            row.optString("warehouse_name", ""),
                            row.optString("transfer_no", "")
                    ));
                }

                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    filterList("");
                });

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Failed to load inventory: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            } finally {
                if (conn != null) conn.disconnect();
            }
        }).start();
    }

    private void filterList(String query) {
        filteredItems.clear();
        String q = query.toLowerCase().trim();

        for (StoreInventoryItem item : allItems) {
            if (q.isEmpty()
                    || item.getProductCode().toLowerCase().contains(q)
                    || q.endsWith(item.getProductCode().toLowerCase())
                    || item.getDescription().toLowerCase().contains(q)
                    || (item.getWarehouseName() != null
                    && item.getWarehouseName().toLowerCase().contains(q))
                    || (item.getTransferNo() != null
                    && item.getTransferNo().toLowerCase().contains(q))) {
                filteredItems.add(item);
            }
        }

        double totalCost = 0;
        for (StoreInventoryItem item : filteredItems) {
            totalCost += item.getTotalCost();
        }

        tvTotalItems.setText("Items: " + filteredItems.size());
//        tvTotalCost.setText("Total Cost: ₱" + costFmt.format(totalCost));
        adapter.notifyDataSetChanged();
    }

    private String resolveProductCode(String scanned) {
        if (scanned == null) return "";

        return scanned.trim()
                .replaceAll("[^0-9A-Za-z]", "");
    }

}