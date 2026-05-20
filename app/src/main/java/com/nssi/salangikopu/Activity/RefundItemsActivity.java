package com.nssi.salangikopu.Activity;

import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.nssi.salangikopu.Adapter.RefundItemAdapter;
import com.nssi.salangikopu.Connection.ENV;
import com.nssi.salangikopu.Model.RefundItem;
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

public class RefundItemsActivity extends AppCompatActivity {

    String currentTransactionNo = "";
    String currentStoreName = "";
    String currentCashierName = "";

    EditText etTransactionNo;
    Button btnSearch, btnProcessRefund, btnBack;
    TextView tvTransactionInfo, tvRefundTotal;
    LinearLayout layoutRefundType, layoutTableHeader;
    RadioGroup rgRefundType;
    ListView listRefundItems;
    ProgressBar progressBar;

    RefundItemAdapter adapter;
    List<RefundItem> refundItems = new ArrayList<>();

    int transactionId = -1;
    int userId = -1;
    double originalTransactionTotal = 0;

    final DecimalFormat fmt = new DecimalFormat("#,##0.00");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_refund_items);

        etTransactionNo = findViewById(R.id.etTransactionNo);
        btnSearch = findViewById(R.id.btnSearch);
        tvTransactionInfo = findViewById(R.id.tvTransactionInfo);

        layoutRefundType = findViewById(R.id.layoutRefundType);
        rgRefundType = findViewById(R.id.rgRefundType);
        tvRefundTotal = findViewById(R.id.tvRefundTotal);

        layoutTableHeader = findViewById(R.id.layoutTableHeader);
        listRefundItems = findViewById(R.id.listRefundItems);
        progressBar = findViewById(R.id.progressBar);

        btnProcessRefund = findViewById(R.id.btnProcessRefund);
        btnBack = findViewById(R.id.btnBack);

        userId = getIntent().getIntExtra("user_id", -1);

        adapter = new RefundItemAdapter(this, refundItems, this::onRefundChanged);
        listRefundItems.setAdapter(adapter);

        rgRefundType.setOnCheckedChangeListener((group, checkedId) -> {
            hideKeyboard();

            if (checkedId == R.id.rbFullRefund) {
                adapter.setPartialMode(false);
                setFullRefundQty();
            } else if (checkedId == R.id.rbPartialRefund) {
                adapter.setPartialMode(true);

                for (RefundItem item : refundItems) {
                    item.setRefundQty(0);
                }

                adapter.notifyDataSetChanged();
                updateRefundTotal();

                Toast.makeText(
                        this,
                        "Select item quantity, then tap Process.",
                        Toast.LENGTH_SHORT
                ).show();
            }
        });

        btnBack.setOnClickListener(v -> finish());

        btnSearch.setOnClickListener(v -> {
            String txnNo = etTransactionNo.getText().toString().trim();
            if (txnNo.isEmpty()) {
                Toast.makeText(this, "Please enter a transaction number.", Toast.LENGTH_SHORT).show();
                return;
            }
            hideKeyboard();
            searchTransaction(txnNo);
        });

        etTransactionNo.setOnEditorActionListener((v, actionId, event) -> {
            btnSearch.performClick();
            return true;
        });

        btnProcessRefund.setOnClickListener(v -> {
            hideKeyboard();

            if (transactionId <= 0 || refundItems.isEmpty()) {
                Toast.makeText(this, "Please search a transaction first.", Toast.LENGTH_SHORT).show();
                return;
            }

            boolean isFull = rgRefundType.getCheckedRadioButtonId() == R.id.rbFullRefund;

            if (isFull) {
                adapter.setPartialMode(false);
                setFullRefundQty();
            } else {
                boolean hasQty = false;
                for (RefundItem item : refundItems) {
                    if (item.getRefundQty() > 0) {
                        hasQty = true;
                        break;
                    }
                }
                if (!hasQty) {
                    Toast.makeText(this, "Please choose item and enter refund quantity.", Toast.LENGTH_SHORT).show();
                    return;
                }
            }

            showRefundActionModal();
        });
    }

    private void setFullRefundQty() {
        for (RefundItem item : refundItems) {
            item.setRefundQty(item.getOriginalQty());
        }
        adapter.notifyDataSetChanged();
        updateRefundTotal();
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        View focus = getCurrentFocus();

        if (imm != null && focus != null) {
            imm.hideSoftInputFromWindow(focus.getWindowToken(), 0);
        }
    }

    /**
     * Determines whether the current refund quantities represent a full refund
     * (every item's refund qty equals its original qty) or a partial refund.
     */
    private boolean isActuallyFullRefund() {
        if (refundItems.isEmpty()) return false;

        for (RefundItem item : refundItems) {
            if (item.getRefundQty() < item.getOriginalQty()) {
                return false;
            }
        }
        return true;
    }

    private void showRefundActionModal() {
        View modalView = getLayoutInflater().inflate(R.layout.dialog_refund_action, null);

        TextView mChipExpired = modalView.findViewById(R.id.modalChipExpired);
        TextView mChipDefective = modalView.findViewById(R.id.modalChipDefective);
        TextView mChipWrongItem = modalView.findViewById(R.id.modalChipWrongItem);
        EditText mEtReason = modalView.findViewById(R.id.modalEtReason);
        TextView mTvType = modalView.findViewById(R.id.modalTvType);
        TextView mTvTotal = modalView.findViewById(R.id.modalTvTotal);
        Button mBtnCancel = modalView.findViewById(R.id.modalBtnCancel);
        Button mBtnProcess = modalView.findViewById(R.id.modalBtnProcess);

        final String[] modalChipReason = {""};

        Runnable resetModalChips = () -> {
            for (TextView chip : new TextView[]{mChipExpired, mChipDefective, mChipWrongItem}) {
                chip.setBackgroundResource(R.drawable.chip_unselected);
                chip.setTextColor(0xFF2E3192);
            }
        };

        View.OnClickListener chipClick = v -> {
            String label = (v == mChipExpired) ? "Expired"
                    : (v == mChipDefective) ? "Defective"
                    : "Wrong Item";

            if (modalChipReason[0].equals(label)) {
                modalChipReason[0] = "";
                resetModalChips.run();
            } else {
                modalChipReason[0] = label;
                resetModalChips.run();
                ((TextView) v).setBackgroundResource(R.drawable.chip_selected);
                ((TextView) v).setTextColor(0xFFFFFFFF);
                mEtReason.setText("");
            }
        };

        mChipExpired.setOnClickListener(chipClick);
        mChipDefective.setOnClickListener(chipClick);
        mChipWrongItem.setOnClickListener(chipClick);

        boolean isFull = isActuallyFullRefund();

        mTvType.setText(isFull ? "Full Refund" : "Partial Refund");
        mTvTotal.setText("₱" + fmt.format(getRefundTotal()));

        AlertDialog modal = new AlertDialog.Builder(this)
                .setView(modalView)
                .setCancelable(true)
                .create();

        modal.show();

        if (modal.getWindow() != null) {
            modal.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            modal.getWindow().setSoftInputMode(
                    android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
            modal.getWindow().setLayout(
                    (int) (getResources().getDisplayMetrics().widthPixels * 0.92),
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        mBtnCancel.setOnClickListener(v -> modal.dismiss());

        mBtnProcess.setOnClickListener(v -> {
            // Prevent double-tap
            mBtnProcess.setEnabled(false);

            String reason = !modalChipReason[0].isEmpty()
                    ? modalChipReason[0]
                    : mEtReason.getText().toString().trim();

            if (reason.isEmpty()) {
                mBtnProcess.setEnabled(true);
                Toast.makeText(this, "Please select or enter a refund reason.", Toast.LENGTH_SHORT).show();
                return;
            }

            List<RefundItem> selectedItems = new ArrayList<>();

            for (RefundItem item : refundItems) {
                double refundQty = item.getRefundQty();
                if (refundQty <= 0) continue;

                if (refundQty > item.getOriginalQty()) {
                    mBtnProcess.setEnabled(true);
                    Toast.makeText(this,
                            "Refund qty exceeds sold qty for " + item.getItemName(),
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                selectedItems.add(item);
            }

            if (selectedItems.isEmpty()) {
                mBtnProcess.setEnabled(true);
                Toast.makeText(this, "Please enter at least one refund quantity.", Toast.LENGTH_SHORT).show();
                return;
            }

            double refundTotal = getRefundTotal();

            if (refundTotal > originalTransactionTotal) {
                mBtnProcess.setEnabled(true);
                Toast.makeText(this, "Refund total cannot exceed transaction total.", Toast.LENGTH_SHORT).show();
                return;
            }

            modal.dismiss();
            processRefund(reason, selectedItems);
        });
    }

    private void searchTransaction(String txnNo) {

        progressBar.setVisibility(View.VISIBLE);

        refundItems.clear();
        adapter.notifyDataSetChanged();

        transactionId = -1;
        originalTransactionTotal = 0;

        layoutRefundType.setVisibility(View.GONE);
        layoutTableHeader.setVisibility(View.GONE);
        btnProcessRefund.setEnabled(false);
        tvTransactionInfo.setVisibility(View.GONE);

        new Thread(() -> {
            HttpURLConnection conn = null;

            try {
                URL url = new URL(ENV.REFUND_SEARCH_URL + "?transaction_no=" + txnNo);
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

                while ((line = reader.readLine()) != null) {
                    res.append(line);
                }

                reader.close();

                JSONObject json = new JSONObject(res.toString());
                boolean success = json.optBoolean("success", false);

                if (!success) {
                    String message = json.optString("message", "Transaction not found.");
                    runOnUiThread(() -> {
                        progressBar.setVisibility(View.GONE);
                        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
                    });
                    return;
                }

                JSONObject txn = json.getJSONObject("transaction");

                transactionId = txn.getInt("transaction_id");
                originalTransactionTotal = txn.optDouble("total_amount", 0);

                String customer = txn.optString("customer_name", "WALK-IN");
                currentTransactionNo = txn.optString("transaction_no", etTransactionNo.getText().toString().trim());
                currentCashierName = txn.optString("cashier_name", "-");
                currentStoreName = txn.optString("store_name", "-");

                String cashier = currentCashierName;
                String store = currentStoreName;
                String createdAt = txn.optString("created_at", "");

                String info = "Customer: " + customer
                        + "  |  Cashier: " + cashier
                        + "  |  Store: " + store
                        + "  |  Total: ₱" + fmt.format(originalTransactionTotal)
                        + "  |  Date: " + createdAt;

                JSONArray items = json.getJSONArray("items");

                refundItems.clear();

                for (int i = 0; i < items.length(); i++) {
                    JSONObject item = items.getJSONObject(i);

                    refundItems.add(new RefundItem(
                            item.getInt("item_id"),
                            item.getString("product_code"),
                            item.getString("item_name"),
                            item.getDouble("unit_price"),
                            item.getDouble("quantity")
                    ));
                }

                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);

                    tvTransactionInfo.setText(info);
                    tvTransactionInfo.setVisibility(View.VISIBLE);

                    layoutRefundType.setVisibility(View.VISIBLE);
                    layoutTableHeader.setVisibility(View.VISIBLE);
                    btnProcessRefund.setEnabled(true);

                    rgRefundType.check(R.id.rbFullRefund);

                    adapter.setPartialMode(false);
                    setFullRefundQty();

                    adapter.notifyDataSetChanged();
                    updateRefundTotal();
                });

            } catch (Exception e) {
                e.printStackTrace();

                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(
                            this,
                            "Search failed: " + e.getMessage(),
                            Toast.LENGTH_SHORT
                    ).show();
                });

            } finally {
                if (conn != null) conn.disconnect();
            }
        }).start();
    }

    private void onRefundChanged() {
        updateRefundTotal();
    }

    private void updateRefundTotal() {
        tvRefundTotal.setText("Refund Amount: ₱" + fmt.format(getRefundTotal()));
    }

    private double getRefundTotal() {
        double total = 0;

        for (RefundItem item : refundItems) {
            total += item.getRefundAmount();
        }

        return total;
    }

    private void processRefund(String reason, List<RefundItem> selectedItems) {
        btnProcessRefund.setEnabled(false);

        // Compute the actual refund type from quantities, not radio buttons.
        final boolean isFull = isActuallyFullRefund();

        new Thread(() -> {
            HttpURLConnection conn = null;

            try {
                URL url = new URL(ENV.REFUND_PROCESS_URL);
                conn = (HttpURLConnection) url.openConnection();

                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                conn.setRequestProperty("Accept", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);

                JSONObject body = new JSONObject();

                body.put("transaction_id", transactionId);
                body.put("user_id", userId);
                body.put("reason", reason);
                body.put("refund_type", isFull ? "full" : "partial");

                JSONArray itemsArr = new JSONArray();

                for (RefundItem item : selectedItems) {
                    JSONObject obj = new JSONObject();
                    obj.put("item_id", item.getTransactionItemId());
                    obj.put("refund_qty", item.getRefundQty());
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
                String message = json.optString("message", "Refund failed.");

                runOnUiThread(() -> {
                    btnProcessRefund.setEnabled(true);
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show();

                    if (success) {
                        printReplacementReceipt(selectedItems);
                    }
                });

            } catch (Exception e) {
                e.printStackTrace();

                runOnUiThread(() -> {
                    btnProcessRefund.setEnabled(true);
                    Toast.makeText(
                            this,
                            "Refund failed: " + e.getMessage(),
                            Toast.LENGTH_LONG
                    ).show();
                });

            } finally {
                if (conn != null) conn.disconnect();
            }
        }).start();
    }

    private void printReplacementReceipt(List<RefundItem> refundedItems) {
        List<ReceiptData.ReceiptItem> receiptItems = new ArrayList<>();
        double newTotal = 0;

        for (RefundItem item : refundItems) {
            double refundedQty = 0;

            for (RefundItem refunded : refundedItems) {
                if (refunded.getTransactionItemId() == item.getTransactionItemId()) {
                    refundedQty = refunded.getRefundQty();
                    break;
                }
            }

            double remainingQty = item.getOriginalQty() - refundedQty;

            if (remainingQty <= 0) continue;

            double subtotal = remainingQty * item.getUnitPrice();
            newTotal += subtotal;

            receiptItems.add(new ReceiptData.ReceiptItem(
                    item.getItemName(),
                    item.getUnitPrice(),
                    remainingQty,
                    subtotal
            ));
        }

        if (receiptItems.isEmpty()) {
            Toast.makeText(this, "Refund processed. No remaining item to print.", Toast.LENGTH_LONG).show();

            String txnNo = etTransactionNo.getText().toString().trim();
            if (!txnNo.isEmpty()) searchTransaction(txnNo);
            return;
        }

        String dateTime = new SimpleDateFormat(
                "MM/dd/yyyy hh:mm a",
                Locale.getDefault()
        ).format(new Date());

        ReceiptData receipt = new ReceiptData(
                currentStoreName,
                currentTransactionNo + "-UPDATED",
                dateTime,
                currentCashierName,
                newTotal,
                receiptItems
        );

        if (!BluetoothPrinterManager.hasDefaultPrinter(this)) {
            PrinterSelectDialog.show(this, new PrinterSelectDialog.OnPrinterSelectedListener() {
                @Override
                public void onSelected(android.bluetooth.BluetoothDevice device) {
                    printRefundReceipt(receipt);
                }

                @Override
                public void onCancelled() {
                    String txnNo = etTransactionNo.getText().toString().trim();
                    if (!txnNo.isEmpty()) searchTransaction(txnNo);
                }
            });
        } else {
            printRefundReceipt(receipt);
        }
    }

    private void printRefundReceipt(ReceiptData receipt) {
        BluetoothPrinterManager.printReceipt(this, receipt, new BluetoothPrinterManager.PrintCallback() {
            @Override
            public void onSuccess() {
                runOnUiThread(() -> {
                    Toast.makeText(
                            RefundItemsActivity.this,
                            "Refund processed and updated receipt printed.",
                            Toast.LENGTH_LONG
                    ).show();

                    String txnNo = etTransactionNo.getText().toString().trim();
                    if (!txnNo.isEmpty()) searchTransaction(txnNo);
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    new AlertDialog.Builder(RefundItemsActivity.this)
                            .setTitle("Print Failed")
                            .setMessage("Refund was processed, but updated receipt failed to print:\n\n" + message)
                            .setPositiveButton("OK", (d, w) -> {
                                String txnNo = etTransactionNo.getText().toString().trim();
                                if (!txnNo.isEmpty()) searchTransaction(txnNo);
                            })
                            .setNegativeButton("Retry", (d, w) -> printRefundReceipt(receipt))
                            .show();
                });
            }
        });
    }
}