package com.nssi.salangikopu.Activity;

import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatActivity;
import com.nssi.salangikopu.Adapter.BadOrderAdapter;
import com.nssi.salangikopu.Connection.ENV;
import com.nssi.salangikopu.Model.BadOrderItem;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class BadOrderActivity extends AppCompatActivity {

    EditText etBoNumber, etSupplierName, etBarcode;
    Spinner spinnerStore;
    ListView listBadOrderItems;
    TextView tvItemCount, tvTotalAmount;
    Button btnSave, btnGenerateBo, btnBack;

    List<BadOrderItem> badOrderItems = new ArrayList<>();
    BadOrderAdapter adapter;

    Map<Integer, String> storeMap = new LinkedHashMap<>();
    List<Integer> storeIds = new ArrayList<>();

    int userId = -1;
    int storeId = -1;

    private long lastScanTime = 0;
    private static final long DEBOUNCE_MS = 100;

    DecimalFormat pesoFmt = new DecimalFormat("#,##0.00");
    DecimalFormat qtyFmt = new DecimalFormat("#,##0.##");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bad_order);

        etBoNumber = findViewById(R.id.etBoNumber);
        etSupplierName = findViewById(R.id.etSupplierName);
        etBarcode = findViewById(R.id.etBarcode);
        spinnerStore = findViewById(R.id.spinnerStore);
        listBadOrderItems = findViewById(R.id.listBadOrderItems);
        tvItemCount = findViewById(R.id.tvItemCount);
        tvTotalAmount = findViewById(R.id.tvTotalAmount);
        btnSave = findViewById(R.id.btnSave);
        btnGenerateBo = findViewById(R.id.btnGenerateBo);
        btnBack = findViewById(R.id.btnBack);

        userId = getIntent().getIntExtra("user_id", -1);
        storeId = getIntent().getIntExtra("store_id", -1);

        adapter = new BadOrderAdapter(this, badOrderItems, this::updateSummary);
        listBadOrderItems.setAdapter(adapter);

        loadStores();
        generateBoNumber();

        btnGenerateBo.setOnClickListener(v -> generateBoNumber());
        btnSave.setOnClickListener(v -> saveBadOrder());

        btnBack.setOnClickListener(v -> {
            if (!badOrderItems.isEmpty()) {
                new AlertDialog.Builder(this)
                        .setTitle("Discard Bad Order")
                        .setMessage("You have unsaved BO items. Are you sure you want to go back?")
                        .setPositiveButton("Discard", (d, w) -> finish())
                        .setNegativeButton("Stay", null)
                        .show();
            } else {
                finish();
            }
        });

        etBarcode.requestFocus();

        etBarcode.setOnEditorActionListener((v, actionId, event) -> {
            handleScan();
            return true;
        });

        etBarcode.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_UP &&
                    keyCode == KeyEvent.KEYCODE_ENTER) {
                handleScan();
                return true;
            }
            return false;
        });
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

        if (raw.isEmpty()) return;

        String code = resolveProductCode(raw);
        lookupProduct(code);
    }

    private void lookupProduct(String scannedCode) {
        int selectedStoreId = getSelectedStoreId();

        if (selectedStoreId <= 0) {
            Toast.makeText(this, "Please select a store first.", Toast.LENGTH_SHORT).show();
            etBarcode.requestFocus();
            return;
        }

        new Thread(() -> {
            HttpURLConnection conn = null;

            try {
                String encoded = URLEncoder.encode(scannedCode, "UTF-8");
                URL url = new URL(
                        ENV.BAD_ORDER_LOOKUP_URL +
                                "?code=" + encoded +
                                "&store_id=" + selectedStoreId
                );

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

                if (!json.optBoolean("success", false)) {
                    runOnUiThread(() -> {
                        Toast.makeText(this, "Product not found.", Toast.LENGTH_SHORT).show();
                        etBarcode.requestFocus();
                    });
                    return;
                }

                JSONObject p = json.getJSONObject("product");

                String code = p.getString("product_code");
                String desc = p.optString("item_description", "");
                double unitPrice = p.optDouble("stock_unit_price", p.optDouble("unit_price", 0));
                double stockQty = p.optDouble("stock_qty", 0);

                runOnUiThread(() -> showItemDialog(code, desc, unitPrice, stockQty));

            } catch (Exception e) {
                e.printStackTrace();

                runOnUiThread(() -> {
                    Toast.makeText(this, "Lookup failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    etBarcode.requestFocus();
                });

            } finally {
                if (conn != null) conn.disconnect();
            }
        }).start();
    }

    private void showItemDialog(String code, String desc, double unitPrice, double stockQty) {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_bad_order_item, null);

        TextView tvDialogCode = view.findViewById(R.id.tvDialogCode);
        TextView tvDialogDescription = view.findViewById(R.id.tvDialogDescription);
        TextView tvDialogStock = view.findViewById(R.id.tvDialogStock);
        EditText etDialogQty = view.findViewById(R.id.etDialogQty);
        EditText etDialogReason = view.findViewById(R.id.etDialogReason);
        TextView tvDialogSubtotal = view.findViewById(R.id.tvDialogSubtotal);

        tvDialogCode.setText(code);
        tvDialogDescription.setText(cleanItemName(desc));
        tvDialogStock.setText("Available Stock: " + qtyFmt.format(stockQty));
        tvDialogSubtotal.setText("Subtotal: ₱0.00");

        BadOrderItem existing = null;

        for (BadOrderItem item : badOrderItems) {
            if (item.getProductCode().equals(code)) {
                existing = item;
                break;
            }
        }

        if (existing != null) {
            etDialogQty.setText(fmtNoComma(existing.getQuantity()));
            etDialogReason.setText(existing.getRemarks());
            tvDialogSubtotal.setText("Subtotal: ₱" + pesoFmt.format(existing.getSubtotal()));
        } else {
            etDialogQty.setText("1");
            etDialogReason.setText("");
            tvDialogSubtotal.setText("Subtotal: ₱" + pesoFmt.format(unitPrice));
        }

        etDialogQty.addTextChangedListener(new SimpleWatcher(() -> {
            double qty = parseDouble(etDialogQty.getText().toString());
            tvDialogSubtotal.setText("Subtotal: ₱" + pesoFmt.format(qty * unitPrice));
        }));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Bad Order Item")
                .setView(view)
                .setCancelable(false)
                .setPositiveButton("Add", null)
                .setNegativeButton("Cancel", (d, w) -> etBarcode.requestFocus())
                .create();

        dialog.setOnShowListener(d -> {
            Button btnAdd = dialog.getButton(AlertDialog.BUTTON_POSITIVE);

            btnAdd.setOnClickListener(v -> {
                double qty = parseDouble(etDialogQty.getText().toString());
                String remarks = etDialogReason.getText().toString().trim();

                if (qty <= 0) {
                    etDialogQty.setError("Required");
                    etDialogQty.requestFocus();
                    return;
                }

                if (qty > stockQty) {
                    etDialogQty.setError("Not enough stock");
                    etDialogQty.requestFocus();
                    return;
                }

                if (remarks.isEmpty()) {
                    etDialogReason.setError("Reason is required");
                    etDialogReason.requestFocus();
                    return;
                }

                BadOrderItem found = null;

                for (BadOrderItem item : badOrderItems) {
                    if (item.getProductCode().equals(code)) {
                        found = item;
                        break;
                    }
                }

                if (found != null) {
                    found.setQuantity(qty);
                    found.setRemarks(remarks);
                    found.setUnitPrice(unitPrice);
                } else {
                    badOrderItems.add(
                            new BadOrderItem(
                                    code,
                                    desc,
                                    qty,
                                    unitPrice,
                                    stockQty,
                                    remarks
                            )
                    );
                }

                adapter.notifyDataSetChanged();
                updateSummary();

                dialog.dismiss();
                etBarcode.requestFocus();
            });
        });

        dialog.show();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
            );
        }

        etDialogQty.requestFocus();
        etDialogQty.selectAll();
    }

    private void saveBadOrder() {
        String boNo = etBoNumber.getText().toString().trim();
        String supplierName = etSupplierName.getText().toString().trim();
        int selectedStoreId = getSelectedStoreId();

        if (boNo.isEmpty()) {
            Toast.makeText(this, "BO number is required.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (selectedStoreId <= 0) {
            Toast.makeText(this, "Please select a store.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (userId <= 0) {
            Toast.makeText(this, "Session error. Please login again.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (badOrderItems.isEmpty()) {
            Toast.makeText(this, "Please scan at least one BO item.", Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Save Bad Order")
                .setMessage("Save BO " + boNo + " with " + badOrderItems.size() + " item(s)?\n\n"
                        + "Total: ₱" + pesoFmt.format(getTotalAmount()))
                .setPositiveButton("Save", (d, w) -> processSaveBadOrder(
                        boNo,
                        supplierName,
                        selectedStoreId
                ))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void processSaveBadOrder(String boNo, String supplierName, int selectedStoreId) {
        btnSave.setEnabled(false);

        new Thread(() -> {
            HttpURLConnection conn = null;

            try {
                URL url = new URL(ENV.SAVE_BAD_ORDER_URL);
                conn = (HttpURLConnection) url.openConnection();

                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                conn.setRequestProperty("Accept", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);

                JSONObject body = new JSONObject();
                body.put("bo_no", boNo);
                body.put("store_id", selectedStoreId);
                body.put("user_id", userId);
                body.put("supplier_name", supplierName);
                body.put("reason", "Bad Order");

                JSONArray arr = new JSONArray();

                for (BadOrderItem item : badOrderItems) {
                    JSONObject obj = new JSONObject();
                    obj.put("product_code", item.getProductCode());
                    obj.put("quantity", item.getQuantity());
                    obj.put("unit_price", item.getUnitPrice());
                    obj.put("remarks", item.getRemarks());
                    arr.put(obj);
                }

                body.put("items", arr);

                OutputStream os = conn.getOutputStream();
                os.write(body.toString().getBytes("UTF-8"));
                os.flush();
                os.close();

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
                String message = json.optString("message", "Save failed.");

                runOnUiThread(() -> {
                    btnSave.setEnabled(true);

                    if (success) {
                        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                        finish();
                    } else {
                        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                    }
                });

            } catch (Exception e) {
                e.printStackTrace();

                runOnUiThread(() -> {
                    btnSave.setEnabled(true);
                    Toast.makeText(this, "Save failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });

            } finally {
                if (conn != null) conn.disconnect();
            }
        }).start();
    }

    private void loadStores() {
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
                JSONArray arr = json.optJSONArray("stores");

                if (arr == null) {
                    arr = json.optJSONArray("data");
                }

                if (arr == null) {
                    arr = new JSONArray();
                }

                storeMap.clear();
                storeIds.clear();

                List<String> names = new ArrayList<>();

                for (int i = 0; i < arr.length(); i++) {
                    JSONObject s = arr.getJSONObject(i);

                    int id = s.optInt("store_id");
                    String name = s.optString("store_name", "Store " + id);

                    storeIds.add(id);
                    storeMap.put(id, name);
                    names.add(name);
                }

                runOnUiThread(() -> {
                    ArrayAdapter<String> sa = new ArrayAdapter<>(
                            this,
                            android.R.layout.simple_spinner_item,
                            names
                    );
                    sa.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                    spinnerStore.setAdapter(sa);

                    if (storeId > 0) {
                        for (int i = 0; i < storeIds.size(); i++) {
                            if (storeIds.get(i) == storeId) {
                                spinnerStore.setSelection(i);
                                break;
                            }
                        }
                    }
                });

            } catch (Exception e) {
                e.printStackTrace();

                runOnUiThread(() ->
                        Toast.makeText(this, "Failed to load stores.", Toast.LENGTH_SHORT).show()
                );

            } finally {
                if (conn != null) conn.disconnect();
            }
        }).start();
    }

    private void generateBoNumber() {
        new Thread(() -> {
            HttpURLConnection conn = null;

            try {
                URL url = new URL(ENV.GENERATE_BO_URL);
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
                String boNo = json.optString("bo_no", "");

                runOnUiThread(() -> etBoNumber.setText(boNo));

            } catch (Exception e) {
                e.printStackTrace();

                runOnUiThread(() ->
                        Toast.makeText(this, "Failed to generate BO number.", Toast.LENGTH_SHORT).show()
                );

            } finally {
                if (conn != null) conn.disconnect();
            }
        }).start();
    }

    private int getSelectedStoreId() {
        if (storeIds.isEmpty()) return storeId;

        int pos = spinnerStore.getSelectedItemPosition();

        if (pos < 0 || pos >= storeIds.size()) return storeId;

        return storeIds.get(pos);
    }

    private double getTotalAmount() {
        double total = 0;

        for (BadOrderItem item : badOrderItems) {
            total += item.getSubtotal();
        }

        return total;
    }

    private void updateSummary() {
        double total = 0;
        double qty = 0;

        for (BadOrderItem item : badOrderItems) {
            total += item.getSubtotal();
            qty += item.getQuantity();
        }

        tvItemCount.setText("Items: " + badOrderItems.size() + " | Qty: " + qtyFmt.format(qty));
        tvTotalAmount.setText("₱" + pesoFmt.format(total));
    }

    private String resolveProductCode(String scanned) {
        if (scanned == null) return "";

        return scanned.trim()
                .replaceAll("[^0-9A-Za-z]", "");
    }

    private double parseDouble(String value) {
        if (value == null || value.trim().isEmpty()) return 0;
        return Double.parseDouble(value.trim());
    }

    private String fmtNoComma(double value) {
        if (value == (long) value) return String.valueOf((long) value);
        return String.valueOf(value);
    }

    private String cleanItemName(String value) {
        if (value == null) return "";

        String text = value
                .replace("️", "")
                .replace("–", "-")
                .replace("—", "-")
                .replaceAll("\\s+", " ")
                .trim();

        text = text.replaceFirst("\\s*-\\s+.*$", "").trim();

        text = text.replaceFirst("(?i)\\s+!\\s*.*$", "").trim();
        text = text.replaceFirst("(?i)\\s+is the\\s+.*$", "").trim();
        text = text.replaceFirst("(?i)\\s+perfect for\\s+.*$", "").trim();
        text = text.replaceFirst("(?i)\\s+great for\\s+.*$", "").trim();
        text = text.replaceFirst("(?i)\\s+ideal for\\s+.*$", "").trim();
        text = text.replaceFirst("(?i)\\s+by\\s+.*$", "").trim();

        return text.replaceAll("\\s+", " ").trim();
    }

    private static class SimpleWatcher implements android.text.TextWatcher {
        private final Runnable runnable;

        SimpleWatcher(Runnable runnable) {
            this.runnable = runnable;
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            if (runnable != null) runnable.run();
        }

        @Override
        public void afterTextChanged(android.text.Editable s) {}
    }
}