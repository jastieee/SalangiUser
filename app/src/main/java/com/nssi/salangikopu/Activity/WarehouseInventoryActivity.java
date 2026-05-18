package com.nssi.salangikopu.Activity;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.nssi.salangikopu.Adapter.WarehouseInventoryAdapter;
import com.nssi.salangikopu.Connection.ENV;
import com.nssi.salangikopu.Model.WarehouseInventoryItem;
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
import java.util.List;

public class WarehouseInventoryActivity extends AppCompatActivity {

    public static final String EXTRA_WAREHOUSE_ID   = "warehouse_id";
    public static final String EXTRA_WAREHOUSE_NAME = "warehouse_name";

    private long lastScanTime = 0;
    private static final long DEBOUNCE_MS = 100;

    private static final long SCAN_RESET_DELAY_MS = 60_000L;
    private final Handler resetHandler = new Handler(Looper.getMainLooper());
    private Runnable resetRunnable = null;

    ListView listInventory;
    ProgressBar progressBar;
    EditText etSearch;
    TextView tvWarehouseName;
    TextView tvTotalItems, tvTotalCost;
    TextView tvNoAccess;

    WarehouseInventoryAdapter adapter;
    List<WarehouseInventoryItem> allItems = new ArrayList<>();
    List<WarehouseInventoryItem> filteredItems = new ArrayList<>();

    int userWarehouseId = 0;
    String userWarehouseName = "";

    DecimalFormat costFmt = new DecimalFormat("#,##0.00");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_warehouse_inventory);

        listInventory = findViewById(R.id.listInventory);
        progressBar = findViewById(R.id.progressBar);
        etSearch = findViewById(R.id.etSearch);
        tvWarehouseName = findViewById(R.id.tvWarehouseName);
        tvTotalItems = findViewById(R.id.tvTotalItems);
        tvTotalCost = findViewById(R.id.tvTotalCost);
        tvNoAccess = findViewById(R.id.tvNoAccess);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        userWarehouseId = getIntent().getIntExtra(EXTRA_WAREHOUSE_ID, 0);
        userWarehouseName = getIntent().getStringExtra(EXTRA_WAREHOUSE_NAME);
        if (userWarehouseName == null) userWarehouseName = "";

        if (userWarehouseId == 0) {
            tvWarehouseName.setText("Warehouse Inventory");
            tvNoAccess.setVisibility(TextView.VISIBLE);
            listInventory.setVisibility(ListView.GONE);
            tvTotalItems.setVisibility(TextView.GONE);
            tvTotalCost.setVisibility(TextView.GONE);
            return;
        }

        tvWarehouseName.setText(userWarehouseName);
        tvNoAccess.setVisibility(TextView.GONE);

        adapter = new WarehouseInventoryAdapter(this, filteredItems);
        listInventory.setAdapter(adapter);

        loadInventory();

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

    private void loadInventory() {
        progressBar.setVisibility(ProgressBar.VISIBLE);

        new Thread(() -> {
            HttpURLConnection conn = null;

            try {
                URL url = new URL(ENV.WAREHOUSE_INVENTORY_URL + "?warehouse_id=" + userWarehouseId);
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
                    allItems.add(new WarehouseInventoryItem(
                            row.getString("product_code"),
                            row.getString("item_description"),
                            row.getDouble("quantity"),
                            row.getDouble("unit_price"),
                            row.getDouble("total_cost")
                    ));
                }

                runOnUiThread(() -> {
                    progressBar.setVisibility(ProgressBar.GONE);
                    filterList("");
                });

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    progressBar.setVisibility(ProgressBar.GONE);
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

        for (WarehouseInventoryItem item : allItems) {
            String code = item.getProductCode().toLowerCase();
            if (q.isEmpty()
                    || code.contains(q)
                    || q.endsWith(code)
                    || item.getDescription().toLowerCase().contains(q)) {
                filteredItems.add(item);
            }
        }

        double totalCost = 0;
        for (WarehouseInventoryItem item : filteredItems) {
            totalCost += item.getTotalCost();
        }

        tvTotalItems.setText("Items: " + filteredItems.size());
        tvTotalCost.setText("Total Cost: ₱" + costFmt.format(totalCost));
        adapter.notifyDataSetChanged();
    }

    private String resolveProductCode(String scanned) {
        if (scanned == null) return "";

        return scanned.trim()
                .replaceAll("[^0-9A-Za-z]", "");
    }



}