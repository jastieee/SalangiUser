package com.nssi.salangikopu.Activity;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

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
import java.util.ArrayList;
import java.util.List;

public class StoreInventoryActivity extends AppCompatActivity {

    public static final String EXTRA_USER_ID    = "user_id";
    public static final String EXTRA_STORE_ID   = "store_id";   // back-compat
    public static final String EXTRA_STORE_NAME = "store_name"; // back-compat

    private long lastScanTime = 0;
    private static final long DEBOUNCE_MS = 100;
    private static final long SCAN_RESET_DELAY_MS = 60_000L;

    private boolean storeSpinnerUserSelected = false;

    private final Handler resetHandler = new Handler(Looper.getMainLooper());
    private Runnable resetRunnable = null;

    ListView listStoreInventory;
    ProgressBar progressBar;
    EditText etSearch;
    Spinner spinnerStore;
    LinearLayout layoutStorePicker;
    TextView tvTotalItems, tvTotalCost, tvNoAccess;

    StoreInventoryAdapter adapter;
    List<StoreInventoryItem> allItems = new ArrayList<>();
    List<StoreInventoryItem> filteredItems = new ArrayList<>();

    // user’s allowed stores
    private final List<Integer> storeIds = new ArrayList<>();
    private final List<String> storeNames = new ArrayList<>();

    int userId = 0;
    int selectedStoreId = 0;
    String selectedStoreName = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_store_inventory);

        listStoreInventory = findViewById(R.id.listStoreInventory);
        progressBar = findViewById(R.id.progressBar);
        etSearch = findViewById(R.id.etSearch);
        tvTotalItems = findViewById(R.id.tvTotalItems);
        tvNoAccess = findViewById(R.id.tvNoAccess);
        spinnerStore = findViewById(R.id.spinnerStore);
        layoutStorePicker = findViewById(R.id.layoutStorePicker);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        userId = getIntent().getIntExtra(EXTRA_USER_ID, 0);

        int initialStoreId = getIntent().getIntExtra(EXTRA_STORE_ID, 0);
        String initialStoreName = getIntent().getStringExtra(EXTRA_STORE_NAME);
        if (initialStoreName == null) initialStoreName = "";

        if (userId <= 0 && initialStoreId <= 0) {
            showNoAccess();
            return;
        }

        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);

        adapter = new StoreInventoryAdapter(this, filteredItems);
        listStoreInventory.setAdapter(adapter);

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

        if (userId > 0) {
            loadAllowedStores();
        } else {
            selectedStoreId = initialStoreId;
            selectedStoreName = initialStoreName;
            layoutStorePicker.setVisibility(View.GONE);
            loadInventory();
        }
    }

    private void showNoAccess() {
        tvNoAccess.setVisibility(View.VISIBLE);
        listStoreInventory.setVisibility(View.GONE);
        tvTotalItems.setVisibility(View.GONE);
        if (tvTotalCost != null) tvTotalCost.setVisibility(View.GONE);
        layoutStorePicker.setVisibility(View.GONE);
    }

    private void loadAllowedStores() {
        progressBar.setVisibility(View.VISIBLE);

        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(ENV.ALLOWED_STORES_URL + "?user_id=" + userId);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Accept", "application/json");
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);

                InputStream is = conn.getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(is));
                StringBuilder res = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) res.append(line);
                reader.close();

                JSONObject json = new JSONObject(res.toString());
                JSONArray arr = json.getJSONArray("stores");

                storeIds.clear();
                storeNames.clear();

                for (int i = 0; i < arr.length(); i++) {
                    JSONObject obj = arr.getJSONObject(i);
                    storeIds.add(obj.getInt("store_id"));
                    storeNames.add(obj.getString("store_name"));
                }

                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);

                    if (storeIds.isEmpty()) {
                        showNoAccess();
                        return;
                    }

                    if (storeIds.size() == 1) {
                        layoutStorePicker.setVisibility(View.GONE);
                        selectedStoreId = storeIds.get(0);
                        selectedStoreName = storeNames.get(0);
                        loadInventory();
                    } else {
                        layoutStorePicker.setVisibility(View.VISIBLE);

                        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(
                                this,
                                android.R.layout.simple_spinner_item,
                                storeNames
                        );
                        spinnerAdapter.setDropDownViewResource(
                                android.R.layout.simple_spinner_dropdown_item
                        );
                        spinnerStore.setAdapter(spinnerAdapter);

                        spinnerStore.setOnTouchListener((v, event) -> {
                            storeSpinnerUserSelected = true;
                            return false;
                        });

                        spinnerStore.setOnItemSelectedListener(
                                new AdapterView.OnItemSelectedListener() {
                                    @Override
                                    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                                        selectedStoreId = storeIds.get(position);
                                        selectedStoreName = storeNames.get(position);
                                        storeSpinnerUserSelected = false;
                                        loadInventory();
                                    }
                                    @Override
                                    public void onNothingSelected(AdapterView<?> parent) {}
                                });
                    }
                });

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(this,
                            "Failed to load stores: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
            } finally {
                if (conn != null) conn.disconnect();
            }
        }).start();
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
    public void onBackPressed() {
        super.onBackPressed();
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (resetRunnable != null) resetHandler.removeCallbacks(resetRunnable);
    }

    private void loadInventory() {
        if (selectedStoreId <= 0) return;
        progressBar.setVisibility(View.VISIBLE);

        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(
                        ENV.STORE_INVENTORY_URL + "?store_id=" + selectedStoreId
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

                if (is == null) throw new Exception("Empty server response");

                BufferedReader reader = new BufferedReader(new InputStreamReader(is));
                StringBuilder res = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) res.append(line);
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
                            "",   // warehouse_name no longer returned by API
                            ""    // transfer_no no longer returned by API
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
                    Toast.makeText(this,
                            "Failed to load inventory: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
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

        tvTotalItems.setText("Items: " + filteredItems.size());
        adapter.notifyDataSetChanged();
    }

    private String resolveProductCode(String scanned) {
        if (scanned == null) return "";
        return scanned.trim().replaceAll("[^0-9A-Za-z]", "");
    }
}