package com.nssi.salangikopu.Activity;

import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.nssi.salangikopu.Adapter.ScanItemAdapter;
import com.nssi.salangikopu.Connection.ENV;
import com.nssi.salangikopu.Model.ScannedItem;
import com.nssi.salangikopu.R;
import com.nssi.salangikopu.Utility.BluetoothPrinterManager;
import com.nssi.salangikopu.Utility.PrinterSelectDialog;
import com.nssi.salangikopu.Utility.ReceiptData;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ScanItemsActivity extends AppCompatActivity {

    EditText etBarcode;
    Button btnSave, btnBack;
    ListView listScannedItems;
    TextView tvTrxNo, tvStoreName, tvScanStatus, tvItemCount, tvTotal;
    TextView tvFocusIndicator;

    ScanItemAdapter adapter;
    List<ScannedItem> scannedItems = new ArrayList<>();

    int userId = -1;
    int storeId = -1;
    String storeName = "";
    String trxNo = "";
    String cashierName = "";

    private long lastScanTime = 0;
    private static final long DEBOUNCE_MS = 100;
    private boolean isScanning = false;



    DecimalFormat fmt = new DecimalFormat("#,##0.00");

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        Log.d("SCAN_DEBUG", "dispatchKeyEvent keyCode=" + event.getKeyCode()
                + " action=" + event.getAction()
                + " unicode=" + event.getUnicodeChar());
        return super.dispatchKeyEvent(event);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan_items);

        etBarcode = findViewById(R.id.etBarcode);
        btnSave = findViewById(R.id.btnSave);
        btnBack = findViewById(R.id.btnBack);
        listScannedItems = findViewById(R.id.listScannedItems);
        tvTrxNo = findViewById(R.id.tvTrxNo);
        tvStoreName = findViewById(R.id.tvStoreName);
        tvScanStatus = findViewById(R.id.tvScanStatus);
        tvItemCount = findViewById(R.id.tvItemCount);
        tvTotal = findViewById(R.id.tvTotal);
        tvFocusIndicator = findViewById(R.id.tvFocusIndicator);

        userId = getIntent().getIntExtra("user_id", -1);
        storeId = getIntent().getIntExtra("store_id", -1);
        storeName = getIntent().getStringExtra("store_name");
        if (storeName == null) storeName = "—";

        cashierName = getIntent().getStringExtra("full_name");
        if (cashierName == null || cashierName.isEmpty()) {
            cashierName = getIntent().getStringExtra("username");
        }
        if (cashierName == null) {
            cashierName = "Cashier";
        }

        tvStoreName.setText(storeName);

        adapter = new ScanItemAdapter(this, scannedItems, this::updateSummary);
        listScannedItems.setAdapter(adapter);

        listScannedItems.setDescendantFocusability(ListView.FOCUS_BLOCK_DESCENDANTS);
        listScannedItems.setItemsCanFocus(false);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{
                        android.Manifest.permission.BLUETOOTH_CONNECT,
                        android.Manifest.permission.BLUETOOTH_SCAN
                }, 101);
            }
        }

        generateTrxNo();

        etBarcode.setOnFocusChangeListener((v, hasFocus) -> {
            tvFocusIndicator.setText("⬤");
            tvFocusIndicator.setTextColor(
                    hasFocus ? 0xFF4CAF50 : 0xFFF44336
            );
            Log.d("SCAN_DEBUG", "etBarcode focus: " + hasFocus);
        });

        etBarcode.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void afterTextChanged(android.text.Editable s) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                Log.d("SCAN_DEBUG", "Text changed: [" + s + "]");
            }
        });

        etBarcode.setOnEditorActionListener((v, actionId, event) -> {
            Log.d("SCAN_DEBUG", "EditorAction fired, actionId=" + actionId);
            handleScan();
            return true;
        });

        etBarcode.setOnKeyListener((v, keyCode, event) -> {
            Log.d("SCAN_DEBUG", "KeyEvent: keyCode=" + keyCode
                    + " action=" + event.getAction()
                    + " text=[" + etBarcode.getText().toString() + "]");
            if (event.getAction() == KeyEvent.ACTION_UP
                    && keyCode == KeyEvent.KEYCODE_ENTER) {
                handleScan();
                return true;
            }
            return false;
        });

        listScannedItems.setOnTouchListener((v, event) -> {
            etBarcode.requestFocus();
            return false;
        });

        findViewById(android.R.id.content).setOnClickListener(v -> etBarcode.requestFocus());

        findViewById(R.id.btnPrinter).setOnClickListener(v ->
                PrinterSelectDialog.show(this,
                        new PrinterSelectDialog.OnPrinterSelectedListener() {
                            @SuppressLint("MissingPermission")
                            @Override
                            public void onSelected(android.bluetooth.BluetoothDevice device) {
                                try {
                                    String name = device.getName();
                                    Toast.makeText(
                                            ScanItemsActivity.this,
                                            "Printer set: " + (name != null ? name : "BP Printer"),
                                            Toast.LENGTH_SHORT
                                    ).show();
                                } catch (SecurityException e) {
                                    Toast.makeText(
                                            ScanItemsActivity.this,
                                            "Printer set successfully.",
                                            Toast.LENGTH_SHORT
                                    ).show();
                                }
                                etBarcode.requestFocus();
                            }

                            @Override
                            public void onCancelled() {
                                etBarcode.requestFocus();
                            }
                        }
                )
        );

        btnBack.setOnClickListener(v -> {
            if (!scannedItems.isEmpty()) {
                showStyledDialog(
                        "Discard Transaction",
                        "You have unsaved changes.",
                        "You have unsaved items. Go back anyway?",
                        "Items:",
                        String.valueOf(scannedItems.size()),
                        "Discard",
                        "Stay",
                        this::finish,
                        () -> etBarcode.requestFocus()
                );
            } else {
                finish();
            }
        });

        btnSave.setOnClickListener(v -> saveTransaction());

        etBarcode.post(() -> {
            etBarcode.requestFocus();
            Log.d("SCAN_DEBUG", "post() focus requested, hasFocus=" + etBarcode.hasFocus());
        });
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        Log.d("SCAN_DEBUG", "onWindowFocusChanged: " + hasFocus);
        if (hasFocus) etBarcode.requestFocus();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String[] permissions,
                                           int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 101) {
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this,
                            "Bluetooth permission required for printing.",
                            Toast.LENGTH_LONG).show();
                    return;
                }
            }
        }
    }

    private void handleScan() {
        if (isScanning) {
            Log.d("SCAN_DEBUG", "handleScan() skipped — still scanning");
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastScanTime < DEBOUNCE_MS) {
            Log.d("SCAN_DEBUG", "handleScan() debounced");
            etBarcode.setText("");
            return;
        }
        lastScanTime = now;

        String raw = etBarcode.getText().toString().trim();
        etBarcode.setText("");
        etBarcode.requestFocus();

        Log.d("SCAN_DEBUG", "handleScan() raw=[" + raw + "]");

        if (!raw.isEmpty()) {
            String resolvedCode = resolveProductCode(raw);

            Log.d("SCAN_DEBUG", "Resolved code=[" + resolvedCode + "] from raw=[" + raw + "]");
            tvScanStatus.setText("Scanned: " + raw + " → " + resolvedCode);

            scanProduct(resolvedCode);
        } else {
            tvScanStatus.setText("⚠ No barcode received");
        }
    }

    private void scanProduct(String productCode) {
        isScanning = true;

        for (ScannedItem existing : scannedItems) {
            String code = existing.getProductCode();
            if (code.equals(productCode) || productCode.endsWith(code)) {

                // 1. Check store stock limit
                if (existing.getQuantity() + 1 > existing.getQuantityInStock()) {
                    runOnUiThread(() -> showStyledDialog(
                            "Out of Stock",
                            "Cannot add more of this item.",
                            "Only " + (int) existing.getQuantityInStock() + " unit(s) available in store.",
                            "In Stock:", String.valueOf((int) existing.getQuantityInStock()),
                            "OK", "Close",
                            () -> etBarcode.requestFocus(),
                            () -> etBarcode.requestFocus()
                    ));
                    isScanning = false;
                    return;
                }

// 2. Check delivery batch limit (promo qty)
                if (existing.hasPromo() && existing.getPromoBatchRemaining() > 0
                        && existing.getQuantity() + 1 > existing.getPromoBatchRemaining()) {

//                    String code = existing.getProductCode();
                    String desc = existing.getDescription();
                    double normalPrice = existing.getNormalPrice();
                    double stockLimit = existing.getQuantityInStock();

                    // Count total qty already in cart for this product (promo + normal lines)
                    double totalInCart = 0;
                    for (ScannedItem s : scannedItems) {
                        if (s.getProductCode().equals(code)) {
                            totalInCart += s.getQuantity();
                        }
                    }

                    // Check total cart qty against store stock
                    if (totalInCart + 1 > stockLimit) {
                        tvScanStatus.setText("⚠ No more stock for " + code);
                        showStyledDialog(
                                "Out of Stock",
                                "Cannot add more of this item.",
                                "Only " + (int) stockLimit + " unit(s) available in store.",
                                "In Stock:", String.valueOf((int) stockLimit),
                                "OK", "Close",
                                () -> etBarcode.requestFocus(),
                                () -> etBarcode.requestFocus()
                        );
                        isScanning = false;
                        return;
                    }

                    // Find existing normal-price line or create new one
                    boolean found = false;
                    for (ScannedItem s : scannedItems) {
                        if (s.getProductCode().equals(code) && !s.hasPromo()) {
                            s.addQty();
                            found = true;
                            break;
                        }
                    }

                    if (!found) {
                        ScannedItem normalItem = new ScannedItem(code, desc, normalPrice);
                        normalItem.setNormalPrice(normalPrice);
                        normalItem.setQuantityInStock(stockLimit);
                        normalItem.setHasPromo(false);
                        scannedItems.add(normalItem);
                    }

                    adapter.notifyDataSetChanged();
                    updateSummary();
                    tvScanStatus.setText("+" + code + " (normal price)");
                    isScanning = false;
                    etBarcode.requestFocus();
                    return;
                }

                existing.addQty();
                adapter.notifyDataSetChanged();
                updateSummary();
                tvScanStatus.setText("+" + code + " (x" + (int) existing.getQuantity() + ")");
                isScanning = false;
                etBarcode.requestFocus();
                return;
            }
        }

        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                String urlStr = ENV.SCAN_LOOKUP_URL
                        + "?store_id=" + storeId
                        + "&code=" + java.net.URLEncoder.encode(productCode, "UTF-8");
                Log.d("SCAN_DEBUG", "Calling: " + urlStr);

                URL url = new URL(urlStr);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Accept", "application/json");
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);

                int statusCode = conn.getResponseCode();
                Log.d("SCAN_DEBUG", "HTTP status: " + statusCode);

                InputStream is = (statusCode >= 200 && statusCode < 300)
                        ? conn.getInputStream()
                        : conn.getErrorStream();

                if (is == null) throw new Exception("Empty server response");

                BufferedReader reader = new BufferedReader(new InputStreamReader(is));
                StringBuilder res = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) res.append(line);
                reader.close();

                Log.d("SCAN_DEBUG", "Response: " + res);

                JSONObject json = new JSONObject(res.toString());
                boolean success = json.optBoolean("success", false);

                if (!success) {
                    String message = json.optString("message", "Item not found.");
                    runOnUiThread(() -> {
                        isScanning = false;
                        showStyledDialog(
                                statusCode == 409 ? "Out of Stock" : "Product Not Found",
                                statusCode == 409
                                        ? "The item exists but is unavailable."
                                        : "The scanned code could not be used.",
                                message,
                                "Code:",
                                productCode,
                                "OK",
                                "Close",
                                () -> etBarcode.requestFocus(),
                                () -> etBarcode.requestFocus()
                        );
                    });
                    return;
                }

                JSONObject item = json.getJSONObject("item"); // ← ADD THIS LINE

                final String resolvedCode   = item.getString("product_code");
                final String desc           = item.getString("item_description");
                final double price          = item.getDouble("unit_price");
                final double finalNormal    = item.optDouble("normal_price", price);
                final double qtyInStock     = item.optDouble("quantity_in_stock", 0);
                final boolean finalHasPromo = item.optBoolean("has_promo", false);

                double batchRem = 0;
                int pId = 0, pItemId = 0;
                String calcCode = "";

                if (finalHasPromo && !item.isNull("promo")) {
                    JSONObject promo = item.getJSONObject("promo");
                    pId      = promo.optInt("promo_id", 0);
                    pItemId  = promo.optInt("promo_item_id", 0);
                    calcCode = promo.optString("calculation_code", "");
                    if (!promo.isNull("delivery_batch_remaining_qty")) {
                        batchRem = promo.getDouble("delivery_batch_remaining_qty");
                    }
                }

                final double finalBatchRem  = batchRem;
                final int finalPromoId      = pId;
                final int finalPromoItemId  = pItemId;
                final String finalCalcCode  = calcCode;

                runOnUiThread(() -> {
                    // Check if added while network was running
                    for (ScannedItem existing : scannedItems) {
                        if (existing.getProductCode().equals(resolvedCode)) {
                            existing.addQty();
                            adapter.notifyDataSetChanged();
                            updateSummary();
                            isScanning = false;
                            etBarcode.requestFocus();
                            return;
                        }
                    }

                    ScannedItem newItem = new ScannedItem(resolvedCode, desc, price);
                    newItem.setNormalPrice(finalNormal);
                    newItem.setQuantityInStock(qtyInStock);
                    newItem.setHasPromo(finalHasPromo);
                    newItem.setPromoId(finalPromoId);
                    newItem.setPromoItemId(finalPromoItemId);
                    newItem.setCalculationCode(finalCalcCode);
                    newItem.setPromoBatchRemaining(finalBatchRem);

                    scannedItems.add(newItem);
                    adapter.notifyDataSetChanged();
                    updateSummary();
                    tvScanStatus.setText("✓ " + desc + (finalHasPromo ? " 🏷 PROMO" : ""));
                    isScanning = false;
                    etBarcode.requestFocus();
                });
            } catch (Exception e) {
                e.printStackTrace();
                Log.e("SCAN_DEBUG", "scanProduct error: " + e.getMessage());
                runOnUiThread(() -> {
                    isScanning = false;
                    etBarcode.requestFocus();
                    Toast.makeText(this, "Scan error: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                });
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
    private void generateTrxNo() {
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(ENV.GENERATE_TRX_URL);
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
                trxNo = json.getString("trx_no");
                runOnUiThread(() -> tvTrxNo.setText(trxNo));

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() ->
                        Toast.makeText(this,
                                "Failed to generate transaction number.",
                                Toast.LENGTH_SHORT).show()
                );
            } finally {
                if (conn != null) conn.disconnect();
            }
        }).start();
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

    private void saveTransaction() {
        if (scannedItems.isEmpty()) {
            Toast.makeText(this, "No items scanned yet.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (userId == -1) {
            Toast.makeText(this, "Session error. Please logout and login again.",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        if (storeId == -1) {
            Toast.makeText(this, "No store assigned to your account.",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        double total = 0;
        for (ScannedItem item : scannedItems) total += item.getSubtotal();
        final double finalTotal = total;

        View dialogView = getLayoutInflater().inflate(R.layout.dialog_confirm_transaction, null);

        TextView tvConfirmTrxNo = dialogView.findViewById(R.id.tvConfirmTrxNo);
        TextView tvConfirmStore = dialogView.findViewById(R.id.tvConfirmStore);
        TextView tvConfirmItems = dialogView.findViewById(R.id.tvConfirmItems);
        TextView tvConfirmTotal = dialogView.findViewById(R.id.tvConfirmTotal);
        Button btnDialogCancel = dialogView.findViewById(R.id.btnDialogCancel);
        Button btnDialogConfirm = dialogView.findViewById(R.id.btnDialogConfirm);

        tvConfirmTrxNo.setText(trxNo);
        tvConfirmStore.setText(storeName);
        tvConfirmItems.setText(String.valueOf(scannedItems.size()));
        tvConfirmTotal.setText("₱" + fmt.format(finalTotal));

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

        btnDialogCancel.setOnClickListener(v -> {
            dialog.dismiss();
            btnSave.setEnabled(true);
            etBarcode.requestFocus();
        });

        btnDialogConfirm.setOnClickListener(v -> {
            dialog.dismiss();
            btnSave.setEnabled(false);

            new Thread(() -> {
                HttpURLConnection conn = null;
                try {
                    URL url = new URL(ENV.SAVE_SALE_URL);
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("POST");
                    conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                    conn.setRequestProperty("Accept", "application/json");
                    conn.setDoOutput(true);
                    conn.setConnectTimeout(15000);
                    conn.setReadTimeout(15000);

                    JSONObject body = new JSONObject();
                    body.put("user_id", userId);
                    body.put("store_id", storeId);
                    body.put("trx_no", trxNo);

                    JSONArray itemsArr = new JSONArray();
                    for (ScannedItem item : scannedItems) {
                        JSONObject obj = new JSONObject();
                        obj.put("product_code",     item.getProductCode());
                        obj.put("item_name",        item.getDescription());
                        obj.put("unit_price",       item.getUnitPrice());
                        obj.put("normal_price",     item.getNormalPrice());
                        obj.put("quantity",         item.getQuantity());
                        obj.put("subtotal",         item.getSubtotal());
                        obj.put("promo_id",         item.getPromoId());
                        obj.put("promo_item_id",    item.getPromoItemId());
                        obj.put("calculation_code", item.getCalculationCode());
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
                    while ((line = reader.readLine()) != null) res.append(line);
                    reader.close();

                    JSONObject json = new JSONObject(res.toString());
                    boolean success = json.optBoolean("success", false);
                    String message = json.optString("message", "Save failed.");

                    if (!success) {
                        runOnUiThread(() -> {
                            btnSave.setEnabled(true);
                            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                        });
                        return;
                    }

                    List<ReceiptData.ReceiptItem> receiptItems = new ArrayList<>();
                    for (ScannedItem item : scannedItems) {
                        receiptItems.add(new ReceiptData.ReceiptItem(
                                item.getDescription(),
                                item.getUnitPrice(),
                                item.getQuantity(),
                                item.getSubtotal()
                        ));
                    }

                    String dateTime = new SimpleDateFormat(
                            "MM/dd/yyyy hh:mm a", Locale.getDefault()
                    ).format(new Date());

                    ReceiptData receipt = new ReceiptData(
                            storeName, trxNo, dateTime,
                            cashierName, finalTotal, receiptItems
                    );

                    runOnUiThread(() -> {
                        Toast.makeText(this,
                                "Transaction " + trxNo + " saved!",
                                Toast.LENGTH_SHORT).show();

                        if (!BluetoothPrinterManager.hasDefaultPrinter(this)) {
                            PrinterSelectDialog.show(this,
                                    new PrinterSelectDialog.OnPrinterSelectedListener() {
                                        @Override
                                        public void onSelected(android.bluetooth.BluetoothDevice device) {
                                            printAndFinish(receipt);
                                        }

                                        @Override
                                        public void onCancelled() {
                                            finish();
                                        }
                                    }
                            );
                        } else {
                            printAndFinish(receipt);
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
        });
    }

    private void printAndFinish(ReceiptData receipt) {
        BluetoothPrinterManager.printReceipt(this, receipt,
                new BluetoothPrinterManager.PrintCallback() {
                    @Override
                    public void onSuccess() {
                        runOnUiThread(() -> {
                            Toast.makeText(ScanItemsActivity.this,
                                    "Receipt printed!", Toast.LENGTH_SHORT).show();
                            finish();
                        });
                    }

                    @Override
                    public void onError(String message) {
                        runOnUiThread(() ->
                                showStyledDialog(
                                        "Print Failed",
                                        "Transaction saved successfully.",
                                        "Could not print receipt.\n\n" + message,
                                        "Status:",
                                        "Saved",
                                        "Retry",
                                        "Close",
                                        () -> printAndFinish(receipt),
                                        () -> ScanItemsActivity.this.finish()
                                )
                        );
                    }
                }
        );
    }

    private void updateSummary() {
        double total = 0;
        for (ScannedItem item : scannedItems) total += item.getSubtotal();
        tvItemCount.setText("Items: " + scannedItems.size());
        tvTotal.setText("₱" + fmt.format(total));
    }
}