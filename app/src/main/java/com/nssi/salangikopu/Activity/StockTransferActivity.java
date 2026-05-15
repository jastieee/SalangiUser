package com.nssi.salangikopu.Activity;

import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.nssi.salangikopu.Adapter.StockTransferAdapter;
import com.nssi.salangikopu.Connection.ENV;
import com.nssi.salangikopu.Model.StockTransferItem;
import com.nssi.salangikopu.R;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

public class StockTransferActivity extends AppCompatActivity {

    private EditText etBarcode;
    private Button btnSave, btnBack;
    private Spinner spinnerWarehouse, spinnerStore;
    private LinearLayout layoutWarehouse;
    private ListView listTransferItems;
    private TextView tvTransferNo, tvStoreName, tvStoreAddress, tvWarehouseDetected,
            tvItemCount, tvTotalAmount;

    private StockTransferAdapter adapter;
    private final List<StockTransferItem> transferItems = new ArrayList<>();

    private final List<Integer> warehouseIds = new ArrayList<>();
    private final List<String> warehouseNames = new ArrayList<>();

    private final List<Integer> storeIds = new ArrayList<>();
    private final List<String> storeNames = new ArrayList<>();
    private final List<String> storeAddresses = new ArrayList<>();

    private int userId = -1;
    private int selectedWarehouseId = -1;
    private int storeId = -1;

    private String transferNo = "";

    private long lastScanTime = 0;
    private static final long DEBOUNCE_MS = 100;

    private final DecimalFormat fmt = new DecimalFormat("#,##0.00");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stock_transfer);

        etBarcode = findViewById(R.id.etBarcode);
        btnSave = findViewById(R.id.btnSave);
        btnBack = findViewById(R.id.btnBack);
        spinnerWarehouse = findViewById(R.id.spinnerWarehouse);
        spinnerStore = findViewById(R.id.spinnerStore);
        layoutWarehouse = findViewById(R.id.layoutWarehouse);
        listTransferItems = findViewById(R.id.listTransferItems);
        tvTransferNo = findViewById(R.id.tvTransferNo);
        tvStoreName = findViewById(R.id.tvStoreName);
        tvStoreAddress = findViewById(R.id.tvStoreAddress);
        tvWarehouseDetected = findViewById(R.id.tvWarehouseDetected);
        tvItemCount = findViewById(R.id.tvItemCount);
        tvTotalAmount = findViewById(R.id.tvTotalAmount);

        userId = getIntent().getIntExtra("user_id", -1);

        adapter = new StockTransferAdapter(this, transferItems, this::updateSummary);
        listTransferItems.setAdapter(adapter);

        generateTransferNo();
        loadAllowedWarehouses();
        loadAllowedStores();

        btnBack.setOnClickListener(v -> {
            if (!transferItems.isEmpty()) {
                showStyledDialog(
                        "Discard Transfer",
                        "You have unsaved changes.",
                        "You have unsaved items. Are you sure you want to go back?",
                        "Items:",
                        String.valueOf(transferItems.size()),
                        "Discard",
                        "Stay",
                        this::finish,
                        () -> etBarcode.requestFocus()
                );
            } else {
                finish();
            }
        });

        etBarcode.setOnEditorActionListener((v, actionId, event) -> {
            handleScan();
            return true;
        });

        etBarcode.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_UP
                    && keyCode == KeyEvent.KEYCODE_ENTER) {
                handleScan();
                return true;
            }
            return false;
        });

        btnSave.setOnClickListener(v -> saveTransfer());

        etBarcode.requestFocus();
    }

    private AlertDialog showStyledDialog(
            String title,
            String subtitle,
            String message,
            String highlightLabel,
            String highlightValue,
            String confirmText,
            String cancelText,
            Runnable onConfirm,
            Runnable onCancel
    ) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_app_action, null);

        TextView tvDialogTitle = dialogView.findViewById(R.id.tvDialogTitle);
        TextView tvDialogSubtitle = dialogView.findViewById(R.id.tvDialogSubtitle);
        TextView tvDialogMessage = dialogView.findViewById(R.id.tvDialogMessage);
        TextView tvDialogHighlightLabel = dialogView.findViewById(R.id.tvDialogHighlightLabel);
        TextView tvDialogHighlightValue = dialogView.findViewById(R.id.tvDialogHighlightValue);
        View layoutDialogHighlight = dialogView.findViewById(R.id.layoutDialogHighlight);
        Button btnDialogCancel = dialogView.findViewById(R.id.btnDialogCancel);
        Button btnDialogConfirm = dialogView.findViewById(R.id.btnDialogConfirm);

        tvDialogTitle.setText(title);
        tvDialogSubtitle.setText(subtitle);
        tvDialogMessage.setText(message);

        if (highlightLabel == null || highlightValue == null) {
            layoutDialogHighlight.setVisibility(View.GONE);
        } else {
            layoutDialogHighlight.setVisibility(View.VISIBLE);
            tvDialogHighlightLabel.setText(highlightLabel);
            tvDialogHighlightValue.setText(highlightValue);
        }

        btnDialogConfirm.setText(confirmText);
        btnDialogCancel.setText(cancelText);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .setCancelable(false)
                .create();

        dialog.show();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            dialog.getWindow().setLayout(
                    (int) (getResources().getDisplayMetrics().widthPixels * 0.92),
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            );
        }

        btnDialogConfirm.setOnClickListener(v -> {
            dialog.dismiss();
            if (onConfirm != null) onConfirm.run();
        });

        btnDialogCancel.setOnClickListener(v -> {
            dialog.dismiss();
            if (onCancel != null) onCancel.run();
        });

        return dialog;
    }

    private void loadAllowedWarehouses() {
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
                while ((line = reader.readLine()) != null) {
                    res.append(line);
                }
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
                    if (warehouseIds.isEmpty()) {
                        selectedWarehouseId = -1;
                        layoutWarehouse.setVisibility(View.GONE);
                        tvWarehouseDetected.setText("No warehouse access");
                        Toast.makeText(this, "You do not have warehouse access.", Toast.LENGTH_LONG).show();
                        return;
                    }

                    if (warehouseIds.size() == 1) {
                        selectedWarehouseId = warehouseIds.get(0);
                        layoutWarehouse.setVisibility(View.GONE);
                        tvWarehouseDetected.setText("Source: " + warehouseNames.get(0));
                    } else {
                        layoutWarehouse.setVisibility(View.VISIBLE);

                        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                                this,
                                android.R.layout.simple_spinner_item,
                                warehouseNames
                        );
                        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                        spinnerWarehouse.setAdapter(adapter);

                        selectedWarehouseId = warehouseIds.get(0);
                        tvWarehouseDetected.setText("Source: " + warehouseNames.get(0));

                        spinnerWarehouse.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                            @Override
                            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                                selectedWarehouseId = warehouseIds.get(position);
                                tvWarehouseDetected.setText("Source: " + warehouseNames.get(position));
                            }

                            @Override
                            public void onNothingSelected(AdapterView<?> parent) {
                            }
                        });
                    }
                });

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() ->
                        Toast.makeText(this, "Failed to load warehouses: " + e.getMessage(), Toast.LENGTH_LONG).show()
                );
            } finally {
                if (conn != null) conn.disconnect();
            }
        }).start();
    }

    private void loadAllowedStores() {
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
                while ((line = reader.readLine()) != null) {
                    res.append(line);
                }
                reader.close();

                JSONObject json = new JSONObject(res.toString());
                JSONArray arr = json.getJSONArray("stores");

                storeIds.clear();
                storeNames.clear();
                storeAddresses.clear();

                for (int i = 0; i < arr.length(); i++) {
                    JSONObject s = arr.getJSONObject(i);
                    storeIds.add(s.getInt("store_id"));
                    storeNames.add(s.getString("store_name"));
                    storeAddresses.add(s.optString("address", ""));
                }

                runOnUiThread(() -> {
                    if (storeIds.isEmpty()) {
                        storeId = -1;
                        tvStoreName.setText("—");
                        tvStoreAddress.setText("No store available");
                        Toast.makeText(this, "No destination store available.", Toast.LENGTH_LONG).show();
                        return;
                    }

                    List<String> storeDisplayList = new ArrayList<>();
                    for (int i = 0; i < storeNames.size(); i++) {
                        String name = storeNames.get(i);
                        String address = storeAddresses.get(i);

                        if (address != null && !address.trim().isEmpty()) {
                            storeDisplayList.add(name + " - " + address);
                        } else {
                            storeDisplayList.add(name);
                        }
                    }

                    ArrayAdapter<String> adapter = new ArrayAdapter<>(
                            this,
                            android.R.layout.simple_spinner_item,
                            storeDisplayList
                    );
                    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                    spinnerStore.setAdapter(adapter);

                    storeId = storeIds.get(0);
                    tvStoreName.setText(storeNames.get(0));
                    tvStoreAddress.setText(storeAddresses.get(0));

                    spinnerStore.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                        @Override
                        public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                            storeId = storeIds.get(position);
                            tvStoreName.setText(storeNames.get(position));
                            tvStoreAddress.setText(storeAddresses.get(position));
                        }

                        @Override
                        public void onNothingSelected(AdapterView<?> parent) {
                        }
                    });
                });

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() ->
                        Toast.makeText(this, "Failed to load stores: " + e.getMessage(), Toast.LENGTH_LONG).show()
                );
            } finally {
                if (conn != null) conn.disconnect();
            }
        }).start();
    }

    private String resolveProductCode(String scanned) {
        if (scanned == null) return "";

        return scanned.trim()
                .replaceAll("[^0-9A-Za-z]", "");
    }


    private void handleScan() {
        long now = System.currentTimeMillis();
        if (now - lastScanTime < DEBOUNCE_MS) {
            etBarcode.setText("");
            return;
        }
        lastScanTime = now;

        String raw = etBarcode.getText().toString().trim();
        etBarcode.setText("");

        if (!raw.isEmpty()) {
            String code = resolveProductCode(raw);
            lookupProduct(code);
        }
    }

    private void generateTransferNo() {
        new Thread(() -> {
            HttpURLConnection conn = null;

            try {
                URL url = new URL(ENV.GENERATE_TRANSFER_URL);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Accept", "application/json");
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);

                InputStream is = conn.getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(is));

                StringBuilder res = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    res.append(line);
                }
                reader.close();

                JSONObject json = new JSONObject(res.toString());
                transferNo = json.getString("transfer_no");

                runOnUiThread(() -> tvTransferNo.setText(transferNo));

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() ->
                        Toast.makeText(this, "Failed to generate transfer number.", Toast.LENGTH_SHORT).show()
                );
            } finally {
                if (conn != null) conn.disconnect();
            }
        }).start();
    }

    private void lookupProduct(String productCode) {
        new Thread(() -> {
            HttpURLConnection conn = null;

            try {
                if (selectedWarehouseId <= 0) {
                    runOnUiThread(() ->
                            Toast.makeText(this, "Please select a source warehouse first.", Toast.LENGTH_SHORT).show()
                    );
                    return;
                }

                String query = "?code=" + URLEncoder.encode(productCode, "UTF-8")
                        + "&user_id=" + userId
                        + "&warehouse_id=" + selectedWarehouseId;

                URL url = new URL(ENV.TRANSFER_LOOKUP_URL + query);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Accept", "application/json");
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);

                int statusCode = conn.getResponseCode();
                InputStream is = (statusCode >= 200 && statusCode < 300)
                        ? conn.getInputStream()
                        : conn.getErrorStream();

                if (is == null) throw new Exception("Empty response");

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
                    String message = json.optString("message", "Not found");
                    runOnUiThread(() ->
                            showStyledDialog(
                                    "Product Not Found",
                                    "The scanned code could not be used.",
                                    message,
                                    "Code:",
                                    productCode,
                                    "OK",
                                    "Close",
                                    () -> etBarcode.requestFocus(),
                                    () -> etBarcode.requestFocus()
                            )
                    );
                    return;
                }

                JSONObject item = json.getJSONObject("item");
                String resolvedCode = item.optString("product_code");

                runOnUiThread(() -> {
                    for (StockTransferItem existing : transferItems) {
                        if (existing.getProductCode().equals(resolvedCode)) {
                            existing.setTransferQty(existing.getTransferQty() + 1);
                            adapter.notifyDataSetChanged();
                            updateSummary();
                            etBarcode.requestFocus();
                            return;
                        }
                    }

                    transferItems.add(new StockTransferItem(
                            resolvedCode,
                            item.optString("description"),
                            item.optInt("warehouse_id"),
                            item.optString("warehouse_name"),
                            item.optDouble("quantity"),
                            item.optDouble("unit_price")
                    ));
                    adapter.notifyDataSetChanged();
                    updateSummary();
                    etBarcode.requestFocus();
                });

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() ->
                        showStyledDialog(
                                "Lookup Error",
                                "An error occurred while finding the item.",
                                e.getMessage(),
                                "Code:",
                                productCode,
                                "OK",
                                "Close",
                                () -> etBarcode.requestFocus(),
                                () -> etBarcode.requestFocus()
                        )
                );
            } finally {
                if (conn != null) conn.disconnect();
            }
        }).start();
    }

    private void saveTransfer() {
        if (selectedWarehouseId <= 0) {
            Toast.makeText(this, "Please select a source warehouse.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (storeId <= 0) {
            Toast.makeText(this, "Please select a destination store.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (transferItems.isEmpty()) {
            Toast.makeText(this, "Please scan at least one item.", Toast.LENGTH_SHORT).show();
            return;
        }

        double total = 0;
        for (StockTransferItem item : transferItems) {
            total += item.getSubtotal();
        }

        showStyledDialog(
                "Confirm Transfer",
                "Please review before saving.",
                "Save transfer " + transferNo + " from warehouse to selected store?",
                "Total Amount:",
                "₱" + fmt.format(total),
                "Confirm",
                "Cancel",
                this::performSaveTransfer,
                () -> etBarcode.requestFocus()
        );
    }

    private void performSaveTransfer() {
        new Thread(() -> {
            HttpURLConnection conn = null;

            try {
                URL url = new URL(ENV.SAVE_TRANSFER_URL);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                conn.setRequestProperty("Accept", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);

                JSONObject body = new JSONObject();
                body.put("transfer_no", transferNo);
                body.put("warehouse_id", selectedWarehouseId);
                body.put("store_id", storeId);
                body.put("user_id", userId);

                JSONArray itemsArr = new JSONArray();

                for (StockTransferItem item : transferItems) {
                    JSONObject obj = new JSONObject();
                    obj.put("product_code", item.getProductCode());
                    obj.put("quantity", item.getTransferQty());
                    obj.put("unit_price", item.getUnitPrice());
                    itemsArr.put(obj);
                }

                body.put("items", itemsArr);

                OutputStream os = conn.getOutputStream();
                os.write(body.toString().getBytes("UTF-8"));
                os.flush();
                os.close();

                int statusCode = conn.getResponseCode();
                InputStream is = (statusCode >= 200 && statusCode < 300)
                        ? conn.getInputStream()
                        : conn.getErrorStream();

                if (is == null) throw new Exception("Empty server response");

                BufferedReader reader = new BufferedReader(new InputStreamReader(is));
                StringBuilder res = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    res.append(line);
                }
                reader.close();

                JSONObject json = new JSONObject(res.toString());
                boolean success = json.optBoolean("success", false);
                String message = json.optString("message", success ? "Transfer saved!" : "Transfer failed.");

                runOnUiThread(() -> {
                    if (success) {
                        showStyledDialog(
                                "Transfer Saved",
                                "The stock transfer was recorded successfully.",
                                message,
                                "Transfer No:",
                                transferNo,
                                "OK",
                                "Close",
                                this::finish,
                                this::finish
                        );
                    } else {
                        showStyledDialog(
                                "Transfer Failed",
                                "The stock transfer could not be saved.",
                                message,
                                "Transfer No:",
                                transferNo,
                                "OK",
                                "Close",
                                () -> etBarcode.requestFocus(),
                                () -> etBarcode.requestFocus()
                        );
                    }
                });

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() ->
                        showStyledDialog(
                                "Save Failed",
                                "An error occurred while saving.",
                                e.getMessage(),
                                "Transfer No:",
                                transferNo,
                                "OK",
                                "Close",
                                () -> etBarcode.requestFocus(),
                                () -> etBarcode.requestFocus()
                        )
                );
            } finally {
                if (conn != null) conn.disconnect();
            }
        }).start();
    }

    private void updateSummary() {
        double total = 0;
        for (StockTransferItem item : transferItems) {
            total += item.getSubtotal();
        }
        tvItemCount.setText("Items: " + transferItems.size());
        tvTotalAmount.setText("₱" + fmt.format(total));
    }
}