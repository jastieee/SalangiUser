package com.nssi.salangikopu.Activity;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
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

    public static final String EXTRA_USER_ID        = "user_id";
    public static final String EXTRA_WAREHOUSE_ID   = "warehouse_id";   // kept for back-compat
    public static final String EXTRA_WAREHOUSE_NAME = "warehouse_name"; // kept for back-compat

    private long lastScanTime = 0;
    private static final long DEBOUNCE_MS = 100;
    private static final long SCAN_RESET_DELAY_MS = 60_000L;

    private boolean warehouseSpinnerReady = false;
    private boolean warehouseSpinnerUserSelected = false;

    private final Handler resetHandler = new Handler(Looper.getMainLooper());
    private Runnable resetRunnable = null;

    ListView listInventory;
    ProgressBar progressBar;
    EditText etSearch;
    Spinner spinnerWarehouse;
    LinearLayout layoutWarehousePicker;
    TextView tvTotalItems, tvTotalCost, tvNoAccess;

    WarehouseInventoryAdapter adapter;
    List<WarehouseInventoryItem> allItems = new ArrayList<>();
    List<WarehouseInventoryItem> filteredItems = new ArrayList<>();

    // user’s allowed warehouses
    private final List<Integer> warehouseIds = new ArrayList<>();
    private final List<String> warehouseNames = new ArrayList<>();

    int userId = 0;
    int selectedWarehouseId = 0;
    String selectedWarehouseName = "";

    DecimalFormat costFmt = new DecimalFormat("#,##0.00");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_warehouse_inventory);

        listInventory = findViewById(R.id.listInventory);
        progressBar = findViewById(R.id.progressBar);
        etSearch = findViewById(R.id.etSearch);
        tvTotalItems = findViewById(R.id.tvTotalItems);
        tvTotalCost = findViewById(R.id.tvTotalCost);
        tvNoAccess = findViewById(R.id.tvNoAccess);
        spinnerWarehouse = findViewById(R.id.spinnerWarehouse);
        layoutWarehousePicker = findViewById(R.id.layoutWarehousePicker);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        userId = getIntent().getIntExtra(EXTRA_USER_ID, 0);

        // Back-compat: caller passed a specific warehouse
        int initialWarehouseId = getIntent().getIntExtra(EXTRA_WAREHOUSE_ID, 0);
        String initialWarehouseName = getIntent().getStringExtra(EXTRA_WAREHOUSE_NAME);
        if (initialWarehouseName == null) initialWarehouseName = "";

        if (userId <= 0 && initialWarehouseId <= 0) {
            showNoAccess();
            return;
        }

        adapter = new WarehouseInventoryAdapter(this, filteredItems);
        listInventory.setAdapter(adapter);

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
            loadAllowedWarehouses();
        } else {
            // Legacy path: single warehouse passed in
            selectedWarehouseId = initialWarehouseId;
            selectedWarehouseName = initialWarehouseName;
            layoutWarehousePicker.setVisibility(View.GONE);
            loadInventory();
        }
    }

    private void showNoAccess() {
        tvNoAccess.setVisibility(View.VISIBLE);
        listInventory.setVisibility(View.GONE);
        tvTotalItems.setVisibility(View.GONE);
        tvTotalCost.setVisibility(View.GONE);
        layoutWarehousePicker.setVisibility(View.GONE);
    }

    private void loadAllowedWarehouses() {
        progressBar.setVisibility(View.VISIBLE);

        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(ENV.ALLOWED_WAREHOUSES_URL + "?user_id=" + userId);
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
                JSONArray arr = json.getJSONArray("warehouses");

                warehouseIds.clear();
                warehouseNames.clear();

                for (int i = 0; i < arr.length(); i++) {
                    JSONObject obj = arr.getJSONObject(i);
                    warehouseIds.add(obj.getInt("warehouse_id"));
                    warehouseNames.add(obj.getString("warehouse_name"));
                }

                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);

                    if (warehouseIds.isEmpty()) {
                        showNoAccess();
                        return;
                    }

                    if (warehouseIds.size() == 1) {
                        // Single warehouse: hide picker
                        layoutWarehousePicker.setVisibility(View.GONE);
                        selectedWarehouseId = warehouseIds.get(0);
                        selectedWarehouseName = warehouseNames.get(0);
                        loadInventory();
                    } else {
                        // Multiple: show picker
                        layoutWarehousePicker.setVisibility(View.VISIBLE);

                        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(
                                this,
                                android.R.layout.simple_spinner_item,
                                warehouseNames
                        );
                        spinnerAdapter.setDropDownViewResource(
                                android.R.layout.simple_spinner_dropdown_item
                        );
                        // Then in loadAllowedWarehouses(), replace the listener block:
                        spinnerWarehouse.setAdapter(spinnerAdapter);

                        spinnerWarehouse.setOnTouchListener((v, event) -> {
                            warehouseSpinnerUserSelected = true;
                            return false;
                        });

                        spinnerWarehouse.setOnItemSelectedListener(
                                new AdapterView.OnItemSelectedListener() {
                                    @Override
                                    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                                        selectedWarehouseId = warehouseIds.get(position);
                                        selectedWarehouseName = warehouseNames.get(position);
                                        if (warehouseSpinnerUserSelected) {
                                            warehouseSpinnerUserSelected = false;
                                            loadInventory();
                                        } else {
                                            // Initial programmatic selection — load silently
                                            loadInventory();
                                        }
                                    }
                                    @Override
                                    public void onNothingSelected(AdapterView<?> parent) {}
                                });

                        // Initial selection triggers loadInventory via listener
                    }
                });

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(this,
                            "Failed to load warehouses: " + e.getMessage(),
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
        if (selectedWarehouseId <= 0) return;

        progressBar.setVisibility(View.VISIBLE);

        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(ENV.WAREHOUSE_INVENTORY_URL
                        + "?warehouse_id=" + selectedWarehouseId);
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
                    allItems.add(new WarehouseInventoryItem(
                            row.getString("product_code"),
                            row.getString("item_description"),
                            row.getDouble("quantity"),
                            row.getDouble("unit_price"),
                            row.getDouble("total_cost")
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
        return scanned.trim().replaceAll("[^0-9A-Za-z]", "");
    }
}