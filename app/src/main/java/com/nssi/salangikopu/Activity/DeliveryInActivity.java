package com.nssi.salangikopu.Activity;

import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.nssi.salangikopu.Adapter.DeliveryItemAdapter;
import com.nssi.salangikopu.Connection.ENV;
import com.nssi.salangikopu.Model.DeliveryItem;
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

public class DeliveryInActivity extends AppCompatActivity {

    EditText etPoNumber, etBarcode;
    CheckBox cbAutoPoNumber;
    Spinner spinnerWarehouse;
    ListView listDeliveryItems;
    TextView tvItemCount, tvTotalAmount;
    Button btnSave;
    AutoCompleteTextView actSupplier;
    EditText etInvoiceNo, etDrNo;

    Map<Integer, String> supplierMap = new LinkedHashMap<>();
    Map<String, Integer> supplierNameToId = new LinkedHashMap<>();


    private Integer selectedSupplierId = null;

    private long lastScanTime = 0;
    private static final long DEBOUNCE_MS = 100;

    List<DeliveryItem> deliveryItems = new ArrayList<>();
    DeliveryItemAdapter adapter;

    Map<Integer, String> warehouseMap = new LinkedHashMap<>();
    List<Integer> warehouseIds = new ArrayList<>();

    DecimalFormat fmt = new DecimalFormat("#,##0.00");

    int userId = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_delivery_in);

        etPoNumber = findViewById(R.id.etPoNumber);
        etBarcode = findViewById(R.id.etBarcode);
        cbAutoPoNumber = findViewById(R.id.cbAutoPoNumber);
        spinnerWarehouse = findViewById(R.id.spinnerWarehouse);
        listDeliveryItems = findViewById(R.id.listDeliveryItems);
        tvItemCount = findViewById(R.id.tvItemCount);
        tvTotalAmount = findViewById(R.id.tvTotalAmount);
        btnSave = findViewById(R.id.btnSave);

        userId = getIntent().getIntExtra("user_id", -1);

        adapter = new DeliveryItemAdapter(this, deliveryItems, this::updateSummary);
        listDeliveryItems.setAdapter(adapter);

        actSupplier = findViewById(R.id.actSupplier);
        etInvoiceNo = findViewById(R.id.etInvoiceNo);
        etDrNo = findViewById(R.id.etDrNo);

        loadSuppliers();

        loadWarehouses();

        cbAutoPoNumber.setOnCheckedChangeListener((btn, checked) -> {
            if (checked) {
                etPoNumber.setEnabled(false);
                generatePoNumber();
            } else {
                etPoNumber.setEnabled(true);
                etPoNumber.setText("");
            }
        });

        etBarcode.requestFocus();

        etBarcode.setOnEditorActionListener((v, actionId, event) -> {
            handleScan();
            return true;
        });

        etBarcode.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_UP && keyCode == KeyEvent.KEYCODE_ENTER) {
                handleScan();
                return true;
            }
            return false;
        });

        btnSave.setOnClickListener(v -> saveDelivery());

        findViewById(R.id.btnBack).setOnClickListener(v -> {
            if (!deliveryItems.isEmpty()) {
                showStyledDialog(
                        "Discard Delivery",
                        "You have unsaved changes.",
                        "You have unsaved items. Are you sure you want to go back?",
                        "Items:",
                        String.valueOf(deliveryItems.size()),
                        "Discard",
                        "Stay",
                        this::finish,
                        null
                );
            } else {
                finish();
            }
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

        if (!raw.isEmpty()) {
            String code = resolveProductCode(raw);
            lookupProduct(code);
        }
    }

    private void showItemDialog(String code, String desc, JSONArray units) {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_delivery_item, null);

        TextView tvDialogCode = view.findViewById(R.id.tvDialogCode);
        TextView tvDialogDescription = view.findViewById(R.id.tvDialogDescription);
        EditText etDialogQty = view.findViewById(R.id.etDialogQty);
        EditText etDialogTotalCost = view.findViewById(R.id.etDialogTotalCost);
        TextView tvDialogUnitCost = view.findViewById(R.id.tvDialogUnitCost);
        Spinner spinnerDialogUom = view.findViewById(R.id.spinnerDialogUom);
        TextView tvDialogConvertedQty = view.findViewById(R.id.tvDialogConvertedQty);

        tvDialogCode.setText(code);
        tvDialogDescription.setText(cleanItemName(desc));

        List<String> uomLabels = new ArrayList<>();
        Map<String, Double> conversionMap = new LinkedHashMap<>();

        try {
            for (int i = 0; i < units.length(); i++) {

                JSONObject u = units.getJSONObject(i);

                String unitName = u.getString("unit_name");
                double conversionQty = u.optDouble("conversion_qty", 1);

                uomLabels.add(unitName);
                conversionMap.put(unitName, conversionQty);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (uomLabels.isEmpty()) {
            uomLabels.add("PCS");
            conversionMap.put("PCS", 1.0);
        }

        ArrayAdapter<String> uomAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                uomLabels
        );

        uomAdapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item
        );

        spinnerDialogUom.setAdapter(uomAdapter);

        DeliveryItem existingItem = null;
        for (DeliveryItem item : deliveryItems) {
            if (item.getProductCode().equals(code)) {
                existingItem = item;
                break;
            }
        }

        if (existingItem != null) {

            etDialogQty.setText(
                    fmtNoComma(existingItem.getOriginalQty())
            );

            etDialogTotalCost.setText(
                    fmtNoComma(existingItem.getTotalCost())
            );

            tvDialogUnitCost.setText(
                    "Cost/Unit: ₱" +
                            fmt.format(existingItem.getComputedUnitCost())
            );

            // restore selected UOM
            for (int i = 0; i < uomLabels.size(); i++) {

                if (uomLabels.get(i)
                        .equalsIgnoreCase(existingItem.getUnitName())) {

                    spinnerDialogUom.setSelection(i);
                    break;
                }
            }

        } else {
            etDialogQty.setText("1");
            etDialogTotalCost.setText("");
            tvDialogUnitCost.setText("Cost/Unit: ₱0.00");
        }

        Runnable recompute = () -> {
            try {

                double qty = parseDouble(etDialogQty.getText().toString());
                double total = parseDouble(etDialogTotalCost.getText().toString());

                String selectedUom =
                        spinnerDialogUom.getSelectedItem().toString();

                double conversionQty =
                        conversionMap.get(selectedUom);

                double convertedQty = qty * conversionQty;

                double unit = convertedQty > 0
                        ? total / convertedQty
                        : 0;

                tvDialogConvertedQty.setText(
                        "Converted Qty: " +
                                fmtNoComma(convertedQty) +
                                " PCS"
                );

                tvDialogUnitCost.setText(
                        "Cost/Unit: ₱" + fmt.format(unit)
                );

            } catch (Exception ignored) {

                tvDialogConvertedQty.setText(
                        "Converted Qty: 0 PCS"
                );

                tvDialogUnitCost.setText(
                        "Cost/Unit: ₱0.00"
                );
            }
        };

        etDialogQty.addTextChangedListener(new SimpleWatcher(recompute));
        etDialogTotalCost.addTextChangedListener(new SimpleWatcher(recompute));

        spinnerDialogUom.setOnItemSelectedListener(
                new android.widget.AdapterView.OnItemSelectedListener() {

                    @Override
                    public void onItemSelected(
                            android.widget.AdapterView<?> parent,
                            View view,
                            int position,
                            long id
                    ) {
                        recompute.run();
                    }

                    @Override
                    public void onNothingSelected(
                            android.widget.AdapterView<?> parent
                    ) {

                    }
                }
        );

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Delivery Item")
                .setView(view)
                .setCancelable(false)
                .setPositiveButton("Add", null)
                .setNegativeButton("Cancel", (d, w) -> etBarcode.requestFocus())
                .create();

        dialog.setOnShowListener(d -> {
            Button btnAdd = dialog.getButton(AlertDialog.BUTTON_POSITIVE);

            btnAdd.setOnClickListener(v -> {
                double inputQty = parseDouble(etDialogQty.getText().toString());

                String selectedUom =
                        spinnerDialogUom.getSelectedItem().toString();

                double conversionQty =
                        conversionMap.get(selectedUom);

                double qty = inputQty * conversionQty;
                double total = parseDouble(etDialogTotalCost.getText().toString());

                if (qty <= 0) {
                    etDialogQty.setError("Required");
                    etDialogQty.requestFocus();
                    return;
                }

                if (total < 0) {
                    etDialogTotalCost.setError("Invalid");
                    etDialogTotalCost.requestFocus();
                    return;
                }

                DeliveryItem found = null;
                for (DeliveryItem item : deliveryItems) {
                    if (item.getProductCode().equals(code)) {
                        found = item;
                        break;
                    }
                }

                if (found != null) {

                    found.setQuantity(qty);
                    found.setTotalCost(total);

                    found.setUnitName(selectedUom);
                    found.setConversionQty(conversionQty);
                    found.setOriginalQty(inputQty);

                } else {
                    deliveryItems.add(
                            new DeliveryItem(
                                    code,
                                    desc,
                                    qty,               // converted PCS qty
                                    total,
                                    selectedUom,
                                    conversionQty,
                                    inputQty           // original qty entered
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
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        }

        etDialogQty.requestFocus();
        etDialogQty.selectAll();
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
        View dialogView = LayoutInflater.from(this)
                .inflate(R.layout.dialog_action_confirmation, null);

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
    private double parseDouble(String s) {
        if (s == null || s.trim().isEmpty()) return 0;
        return Double.parseDouble(s.trim());
    }

    private String fmtNoComma(double value) {
        if (value == (long) value) {
            return String.valueOf((long) value);
        }
        return String.valueOf(value);
    }

    private void loadSuppliers() {
        new Thread(() -> {
            HttpURLConnection connection = null;

            try {
                URL url = new URL(ENV.SUPPLIERS_URL);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("Accept", "application/json");
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(15000);

                InputStream is = connection.getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(is));
                StringBuilder response = new StringBuilder();
                String line;

                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }

                reader.close();

                JSONObject json = new JSONObject(response.toString());
                JSONArray arr = json.getJSONArray("suppliers");

                supplierMap.clear();
                supplierNameToId.clear();

                List<String> names = new ArrayList<>();

                for (int i = 0; i < arr.length(); i++) {
                    JSONObject s = arr.getJSONObject(i);

                    int id = s.getInt("supplier_id");
                    String name = s.getString("supplier_name");
                    String code = s.optString("supplier_code", "");

                    String label = code.isEmpty() ? name : name + " (" + code + ")";

                    supplierMap.put(id, label);
                    supplierNameToId.put(label, id);
                    supplierNameToId.put(name, id);
                    names.add(label);
                }

                runOnUiThread(() -> {
                    ArrayAdapter<String> adapter = new ArrayAdapter<>(
                            this,
                            android.R.layout.simple_dropdown_item_1line,
                            names
                    );

                    actSupplier.setAdapter(adapter);
                    actSupplier.setThreshold(1);

                    actSupplier.setOnItemClickListener((parent, view, position, id) -> {
                        String selected = parent.getItemAtPosition(position).toString();
                        selectedSupplierId = supplierNameToId.get(selected);
                    });

                    actSupplier.addTextChangedListener(new android.text.TextWatcher() {
                        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                        @Override
                        public void onTextChanged(CharSequence s, int start, int before, int count) {
                            String text = s.toString().trim();
                            selectedSupplierId = supplierNameToId.get(text);
                        }

                        @Override public void afterTextChanged(android.text.Editable s) {}
                    });

                    actSupplier.setOnClickListener(v -> actSupplier.showDropDown());
                    actSupplier.setOnFocusChangeListener((v, hasFocus) -> {
                        if (hasFocus) actSupplier.showDropDown();
                    });
                });

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() ->
                        Toast.makeText(this, "Failed to load suppliers.", Toast.LENGTH_SHORT).show()
                );
            } finally {
                if (connection != null) connection.disconnect();
            }
        }).start();
    }
    private void loadWarehouses() {
        new Thread(() -> {
            HttpURLConnection connection = null;

            try {
                URL url = new URL(ENV.WAREHOUSES_URL);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("Accept", "application/json");
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(15000);

                InputStream is = connection.getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(is));
                StringBuilder response = new StringBuilder();
                String line;

                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }

                reader.close();

                JSONObject json = new JSONObject(response.toString());
                JSONArray arr = json.getJSONArray("warehouses");

                warehouseMap.clear();
                warehouseIds.clear();

                List<String> names = new ArrayList<>();

                for (int i = 0; i < arr.length(); i++) {
                    JSONObject w = arr.getJSONObject(i);
                    int id = w.getInt("warehouse_id");
                    String name = w.getString("warehouse_name");

                    warehouseMap.put(id, name);
                    warehouseIds.add(id);
                    names.add(name);
                }

                runOnUiThread(() -> {
                    ArrayAdapter<String> sa = new ArrayAdapter<>(
                            this,
                            android.R.layout.simple_spinner_item,
                            names
                    );
                    sa.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                    spinnerWarehouse.setAdapter(sa);
                });

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() ->
                        Toast.makeText(this, "Failed to load warehouses.", Toast.LENGTH_SHORT).show()
                );
            } finally {
                if (connection != null) connection.disconnect();
            }
        }).start();
    }

    private void generatePoNumber() {
        new Thread(() -> {
            HttpURLConnection connection = null;

            try {
                URL url = new URL(ENV.GENERATE_PO_URL);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("Accept", "application/json");
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(15000);

                InputStream is = connection.getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(is));
                StringBuilder res = new StringBuilder();
                String line;

                while ((line = reader.readLine()) != null) {
                    res.append(line);
                }

                reader.close();

                JSONObject json = new JSONObject(res.toString());
                String po = json.getString("po_number");

                runOnUiThread(() -> etPoNumber.setText(po));

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() ->
                        Toast.makeText(this, "Failed to generate PO number.", Toast.LENGTH_SHORT).show()
                );
            } finally {
                if (connection != null) connection.disconnect();
            }
        }).start();
    }

    private void lookupProduct(String productCode) {
        new Thread(() -> {
            HttpURLConnection connection = null;

            try {
                String encoded = URLEncoder.encode(productCode, "UTF-8");
                URL url = new URL(ENV.PRODUCT_LOOKUP_URL + "?code=" + encoded);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("Accept", "application/json");
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(15000);

                InputStream is = connection.getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(is));
                StringBuilder res = new StringBuilder();
                String line;

                while ((line = reader.readLine()) != null) {
                    res.append(line);
                }

                reader.close();

                JSONObject json = new JSONObject(res.toString());

                if (!json.optBoolean("success")) {
                    runOnUiThread(() -> {
                        showStyledDialog(
                                "Product Not Found",
                                "This scanned code is not registered yet.",
                                "Product code \"" + productCode + "\" is not registered. Register it now?",
                                "Code:",
                                productCode,
                                "Register",
                                "Cancel",
                                () -> {
                                    Intent intent = new Intent(this, RegisterProductActivity.class);
                                    intent.putExtra("product_code", productCode);
                                    startActivityForResult(intent, 100);
                                },
                                null
                        );
                    });
                    return;
                }

                JSONObject p = json.getJSONObject("product");
                String code = p.getString("product_code");
                String desc = cleanItemName(
                        p.getString("item_description")
                );

                JSONArray units = p.optJSONArray("units");

                if (units == null) {
                    units = new JSONArray();
                }

                JSONArray finalUnits = units;

                runOnUiThread(() ->
                        showItemDialog(code, desc, finalUnits)
                );

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() ->
                        Toast.makeText(this, "Error looking up product.", Toast.LENGTH_SHORT).show()
                );
            } finally {
                if (connection != null) connection.disconnect();
            }
        }).start();
    }

    private String resolveProductCode(String scanned) {
        if (scanned == null) return "";

        return scanned.trim()
                .replaceAll("[^0-9A-Za-z]", "");
    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == 100 && resultCode == RESULT_OK && data != null) {
            String newCode = data.getStringExtra("product_code");
            if (newCode != null) {
                etBarcode.setText(newCode);
            }
        }
    }

    private String cleanItemName(String value) {

        if (value == null) return "";

        String text = value;

        text = text
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

    private double getTotalDeliveryAmount() {
        double total = 0;
        for (DeliveryItem item : deliveryItems) {
            total += item.getTotalCost();
        }
        return total;
    }
    private void saveDelivery() {
        String poNumber = etPoNumber.getText().toString().trim();
        String invoiceNo = etInvoiceNo.getText().toString().trim();
        String drNo = etDrNo.getText().toString().trim();
        String supplierText = actSupplier.getText().toString().trim();

        if (!supplierText.isEmpty() && selectedSupplierId == null) {
            Toast.makeText(this, "Please select a valid supplier from the list.", Toast.LENGTH_SHORT).show();
            return;
        }

        final Integer finalSupplierId = selectedSupplierId;

        if (deliveryItems.isEmpty()) {
            Toast.makeText(this, "Please add at least one product.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (warehouseIds.isEmpty()) {
            Toast.makeText(this, "No warehouse selected.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (userId == -1) {
            Toast.makeText(this, "Session error. Please login again.", Toast.LENGTH_SHORT).show();
            return;
        }

        int warehouseId = warehouseIds.get(spinnerWarehouse.getSelectedItemPosition());

        showStyledDialog(
                "Confirm Delivery",
                "Please review before saving.",
                "Save delivery with " + deliveryItems.size() + " item(s)?",
                "Total Cost:",
                "₱" + fmt.format(getTotalDeliveryAmount()),
                "Confirm",
                "Cancel",
                () -> {
                    btnSave.setEnabled(false);

                    new Thread(() -> {
                        HttpURLConnection conn = null;

                        try {
                            URL url = new URL(ENV.SAVE_DELIVERY_URL);
                            conn = (HttpURLConnection) url.openConnection();
                            conn.setRequestMethod("POST");
                            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                            conn.setRequestProperty("Accept", "application/json");
                            conn.setDoOutput(true);
                            conn.setConnectTimeout(15000);
                            conn.setReadTimeout(15000);

                            JSONObject body = new JSONObject();
                            body.put("po_number", poNumber);
                            body.put("warehouse_id", warehouseId);

                            if (finalSupplierId == null) {
                                body.put("supplier_id", JSONObject.NULL);
                            } else {
                                body.put("supplier_id", finalSupplierId);
                            }

                            body.put("dr_no", drNo);
                            body.put("invoice_no", invoiceNo);
                            body.put("user_id", userId);

                            JSONArray itemsArr = new JSONArray();

                            for (DeliveryItem item : deliveryItems) {
                                JSONObject obj = new JSONObject();
                                obj.put("product_code", item.getProductCode());
                                obj.put("quantity", item.getQuantity());
                                obj.put("original_qty", item.getOriginalQty());
                                obj.put("unit_name", item.getUnitName());
                                obj.put("conversion_qty", item.getConversionQty());
                                obj.put("total_cost", item.getTotalCost());
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
                            String savedPo = json.optString("po_number", poNumber);

                            runOnUiThread(() -> {
                                btnSave.setEnabled(true);

                                if (success) {
                                    Toast.makeText(this,
                                            "Delivery " + savedPo + " saved successfully!",
                                            Toast.LENGTH_LONG).show();
                                    finish();
                                } else {
                                    Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                                }
                            });

                        } catch (Exception e) {
                            e.printStackTrace();

                            runOnUiThread(() -> {
                                btnSave.setEnabled(true);
                                Toast.makeText(this,
                                        "Save failed: " + e.getMessage(),
                                        Toast.LENGTH_LONG).show();
                            });
                        } finally {
                            if (conn != null) conn.disconnect();
                        }
                    }).start();
                },
                null
        );
    }
    private void updateSummary() {

        double total = 0;
        double totalQty = 0;

        for (DeliveryItem item : deliveryItems) {
            total += item.getTotalCost();
            totalQty += item.getQuantity();
        }

        tvItemCount.setText(
                "Items: " +
                        deliveryItems.size() +
                        " | PCS: " +
                        fmtNoComma(totalQty)
        );

        tvTotalAmount.setText(
                "₱" + fmt.format(total)
        );
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