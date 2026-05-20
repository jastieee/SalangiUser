package com.nssi.salangikopu.Activity;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.text.method.ScrollingMovementMethod;

import com.nssi.salangikopu.Adapter.TransferConfirmationAdapter;
import com.nssi.salangikopu.Connection.ENV;
import com.nssi.salangikopu.Model.TransferConfirmationItem;
import com.nssi.salangikopu.R;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TransferConfirmationActivity extends AppCompatActivity {

    ListView listTransfers;
    ProgressBar progressBar;
    EditText etSearch;
    TextView tvPendingCount;
    Spinner spinnerStatus;

    TransferConfirmationAdapter adapter;
    List<TransferConfirmationItem> allItems = new ArrayList<>();
    List<TransferConfirmationItem> filteredItems = new ArrayList<>();

    int userId = -1;
    int storeId = -1;

    String selectedStatus = "PENDING";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_transfer_confirmation);

        listTransfers = findViewById(R.id.listTransfers);
        progressBar = findViewById(R.id.progressBar);
        etSearch = findViewById(R.id.etSearch);
        tvPendingCount = findViewById(R.id.tvPendingCount);
        spinnerStatus = findViewById(R.id.spinnerStatus);

        userId = getIntent().getIntExtra("user_id", -1);
        storeId = getIntent().getIntExtra("store_id", -1);

        adapter = new TransferConfirmationAdapter(
                this,
                filteredItems,
                this::onConfirmClicked,
                this::onDenyClicked
        );
        listTransfers.setAdapter(adapter);

        listTransfers.setOnItemClickListener((parent, view, position, id) -> {
            TransferConfirmationItem item = filteredItems.get(position);
            openTransferDetails(item);
        });

        List<String> statuses = Arrays.asList("PENDING", "TRANSFER COMPLETE", "DENIED", "ALL");
        ArrayAdapter<String> sa = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, statuses
        );
        sa.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerStatus.setAdapter(sa);
        spinnerStatus.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                selectedStatus = statuses.get(pos);
                filterList(etSearch.getText().toString());
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int i, int c, int a) {}
            @Override public void afterTextChanged(Editable s) {}
            @Override public void onTextChanged(CharSequence s, int i, int b, int c) {
                filterList(s.toString());
            }
        });

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        loadTransfers();
    }

    private void openTransferDetails(TransferConfirmationItem item) {
        progressBar.setVisibility(View.VISIBLE);

        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(ENV.TRANSFER_DETAILS_URL + "?transfer_id=" + item.getTransferId());
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

                String raw = res.toString().trim();
                if (!raw.startsWith("{")) {
                    throw new Exception("Server did not return JSON:\n" + raw);
                }

                JSONObject json = new JSONObject(raw);

                if (!json.optBoolean("success", false)) {
                    throw new Exception(json.optString("message", "Failed to load transfer details"));
                }

                JSONArray arr = json.getJSONArray("items");
                StringBuilder details = new StringBuilder();

                for (int i = 0; i < arr.length(); i++) {
                    JSONObject obj = arr.getJSONObject(i);
                    int qty = (int) obj.optDouble("quantity", 0);
                    double unitPrice = obj.optDouble("unit_price", 0);

                    details.append(i + 1).append(". ")
                            .append(cleanItemName(obj.optString("description", "No description")))
                            .append("\nQty: ").append(qty)
                            .append("\nUnit Price: ₱").append(String.format("%.2f", unitPrice))
                            .append("\n\n");
                }

                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);

                    showStyledDialog(
                            "Transfer Items",
                            item.getTransferNo(),
                            details.length() == 0 ? "No items found." : details.toString(),
                            "Total Items:",
                            String.valueOf(item.getItemCount()),
                            "OK",
                            "Close",
                            null,
                            null
                    );
                });

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    showStyledDialog(
                            "Load Failed",
                            "Could not load transfer items.",
                            e.getMessage(),
                            "Transfer No:",
                            item.getTransferNo(),
                            "OK",
                            "Close",
                            null,
                            null
                    );
                });
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

        tvDialogMessage.setMovementMethod(new ScrollingMovementMethod());
        tvDialogMessage.setVerticalScrollBarEnabled(true);
        tvDialogMessage.setMaxLines(12);

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

    private void loadTransfers() {
        progressBar.setVisibility(View.VISIBLE);

        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(ENV.TRANSFER_LIST_URL + "?store_id=" + storeId);
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
                    throw new Exception(json.optString("message", "Failed to load transfers"));
                }

                JSONArray arr = json.getJSONArray("items");
                allItems.clear();

                for (int i = 0; i < arr.length(); i++) {
                    JSONObject row = arr.getJSONObject(i);
                    allItems.add(new TransferConfirmationItem(
                            row.getInt("transfer_id"),
                            row.getString("transfer_no"),
                            row.getString("warehouse_name"),
                            row.getString("store_name"),
                            row.getString("transfer_date"),
                            row.getInt("item_count"),
                            row.getString("status"),
                            row.getInt("warehouse_id"),
                            row.getInt("store_id")
                    ));
                }

                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    filterList(etSearch.getText().toString());
                    updatePendingCount();
                });

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    showStyledDialog(
                            "Load Failed",
                            "Could not load transfer records.",
                            e.getMessage(),
                            "Store ID:",
                            String.valueOf(storeId),
                            "Retry",
                            "Close",
                            this::loadTransfers,
                            this::finish
                    );
                });
            } finally {
                if (conn != null) conn.disconnect();
            }
        }).start();
    }

    private void filterList(String query) {
        filteredItems.clear();
        String q = query.toLowerCase().trim();

        for (TransferConfirmationItem item : allItems) {
            boolean matchStatus = selectedStatus.equals("ALL") ||
                    item.getStatus().equals(selectedStatus);

            boolean matchSearch = q.isEmpty()
                    || item.getTransferNo().toLowerCase().contains(q)
                    || item.getWarehouseName().toLowerCase().contains(q)
                    || item.getStoreName().toLowerCase().contains(q);

            if (matchStatus && matchSearch) {
                filteredItems.add(item);
            }
        }

        adapter.notifyDataSetChanged();
    }

    private void updatePendingCount() {
        int count = 0;
        for (TransferConfirmationItem item : allItems) {
            if ("PENDING".equals(item.getStatus())) count++;
        }
        tvPendingCount.setText("Pending: " + count);
    }

    private void onConfirmClicked(TransferConfirmationItem item) {
        showStyledDialog(
                "Confirm Transfer",
                "Please review before confirming.",
                "Confirm transfer " + item.getTransferNo() + "?\n\n" +
                        "From: " + item.getWarehouseName() + "\n" +
                        "To: " + item.getStoreName() + "\n" +
                        "Items: " + item.getItemCount() + "\n\n" +
                        "This will deduct stock from source and add stock to destination.",
                "Transfer No:",
                item.getTransferNo(),
                "Confirm",
                "Cancel",
                () -> processConfirmation(item),
                null
        );
    }

    private void onDenyClicked(TransferConfirmationItem item) {
        showStyledDialog(
                "Deny Transfer",
                "This will close the transfer.",
                "Deny transfer " + item.getTransferNo() + "?\n\n" +
                        "From: " + item.getWarehouseName() + "\n" +
                        "To: " + item.getStoreName() + "\n" +
                        "Items: " + item.getItemCount() + "\n\n" +
                        "No stock will be moved. The transfer will be closed and you can scan the items into a new transfer if needed.",
                "Transfer No:",
                item.getTransferNo(),
                "Deny",
                "Cancel",
                () -> processDenial(item),
                null
        );
    }

    private void processConfirmation(TransferConfirmationItem transfer) {
        progressBar.setVisibility(View.VISIBLE);

        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(ENV.TRANSFER_CONFIRM_URL);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                conn.setRequestProperty("Accept", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);

                JSONObject body = new JSONObject();
                body.put("transfer_id", transfer.getTransferId());
                body.put("warehouse_id", transfer.getWarehouseId());
                body.put("store_id", transfer.getStoreId());

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
                String message = json.optString("message", "Confirmation failed");

                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);

                    if (success) {
                        transfer.setStatus("TRANSFER COMPLETE");
                        adapter.notifyDataSetChanged();
                        updatePendingCount();

                        showStyledDialog(
                                "Transfer Confirmed",
                                "Stock movement completed successfully.",
                                transfer.getTransferNo() + " confirmed.\n\n" +
                                        "✓ Source stock deducted\n" +
                                        "✓ Destination inventory updated",
                                "Transfer No:",
                                transfer.getTransferNo(),
                                "OK",
                                "Close",
                                null,
                                null
                        );
                    } else {
                        showStyledDialog(
                                "Confirmation Failed",
                                "The transfer could not be confirmed.",
                                message,
                                "Transfer No:",
                                transfer.getTransferNo(),
                                "OK",
                                "Close",
                                null,
                                null
                        );
                    }
                });

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    showStyledDialog(
                            "Confirmation Failed",
                            "An error occurred while confirming.",
                            e.getMessage(),
                            "Transfer No:",
                            transfer.getTransferNo(),
                            "Retry",
                            "Close",
                            () -> processConfirmation(transfer),
                            null
                    );
                });
            } finally {
                if (conn != null) conn.disconnect();
            }
        }).start();
    }

    private void processDenial(TransferConfirmationItem transfer) {
        progressBar.setVisibility(View.VISIBLE);

        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(ENV.TRANSFER_DENY_URL);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                conn.setRequestProperty("Accept", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);

                JSONObject body = new JSONObject();
                body.put("transfer_id", transfer.getTransferId());

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
                String message = json.optString("message", "Denial failed");

                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);

                    if (success) {
                        transfer.setStatus("DENIED");
                        adapter.notifyDataSetChanged();
                        updatePendingCount();

                        showStyledDialog(
                                "Transfer Denied",
                                "The transfer was closed.",
                                message + "\n\nNo inventory was moved.",
                                "Transfer No:",
                                transfer.getTransferNo(),
                                "OK",
                                "Close",
                                null,
                                null
                        );
                    } else {
                        showStyledDialog(
                                "Denial Failed",
                                "The transfer could not be denied.",
                                message,
                                "Transfer No:",
                                transfer.getTransferNo(),
                                "OK",
                                "Close",
                                null,
                                null
                        );
                    }
                });

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    showStyledDialog(
                            "Denial Failed",
                            "An error occurred while denying.",
                            e.getMessage(),
                            "Transfer No:",
                            transfer.getTransferNo(),
                            "Retry",
                            "Close",
                            () -> processDenial(transfer),
                            null
                    );
                });
            } finally {
                if (conn != null) conn.disconnect();
            }
        }).start();
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

    @Override
    protected void onResume() {
        super.onResume();
        loadTransfers();
    }
}