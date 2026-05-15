package com.nssi.salangikopu.Activity;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.nssi.salangikopu.Adapter.RefundDecisionAdapter;
import com.nssi.salangikopu.Connection.ENV;
import com.nssi.salangikopu.Model.RefundDecisionItem;
import com.nssi.salangikopu.R;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

public class RefundDecisionActivity extends AppCompatActivity {

    Button btnBack, btnRefresh;
    TextView tvPendingCount;
    ListView listRefundDecisions;
    ProgressBar progressBar;

    RefundDecisionAdapter adapter;
    List<RefundDecisionItem> items = new ArrayList<>();

    int userId = -1;
    int storeId = -1;

    final DecimalFormat peso = new DecimalFormat("#,##0.00");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_refund_decision);

        btnBack = findViewById(R.id.btnBack);
        btnRefresh = findViewById(R.id.btnRefresh);
        tvPendingCount = findViewById(R.id.tvPendingCount);
        listRefundDecisions = findViewById(R.id.listRefundDecisions);
        progressBar = findViewById(R.id.progressBar);

        userId = getIntent().getIntExtra("user_id", -1);
        storeId = getIntent().getIntExtra("store_id", -1);

        adapter = new RefundDecisionAdapter(this, items, new RefundDecisionAdapter.OnDecisionClickListener() {
            @Override
            public void onReturnToStore(RefundDecisionItem item) {
                confirmReturnToStore(item);
            }

            @Override
            public void onDispose(RefundDecisionItem item) {
                showDisposeReasonDialog(item);
            }
        });

        listRefundDecisions.setAdapter(adapter);

        btnBack.setOnClickListener(v -> finish());
        btnRefresh.setOnClickListener(v -> loadPendingRefundItems());

        loadPendingRefundItems();
    }

    private void loadPendingRefundItems() {
        progressBar.setVisibility(View.VISIBLE);

        new Thread(() -> {
            HttpURLConnection conn = null;

            try {
                String urlStr = ENV.REFUND_PENDING_ITEMS_URL;

                if (storeId > 0) {
                    urlStr += "?store_id=" + storeId;
                }

                URL url = new URL(urlStr);
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
                    throw new Exception(json.optString("message", "Failed to load pending refund items"));
                }

                JSONArray arr = json.getJSONArray("items");
                List<RefundDecisionItem> temp = new ArrayList<>();

                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.getJSONObject(i);

                    temp.add(new RefundDecisionItem(
                            o.optInt("refund_item_id"),
                            o.optInt("refund_id"),
                            o.optInt("transaction_id"),
                            o.optString("transaction_no", "-"),
                            o.optString("product_code", ""),
                            o.optString("item_name", ""),
                            o.optInt("quantity", 0),
                            o.optDouble("unit_price", 0),
                            o.optDouble("subtotal", 0),
                            o.optString("status", "pending"),
                            o.optString("refund_type", "partial"),
                            o.optString("refund_reason", ""),
                            o.optString("refund_date", ""),
                            o.optInt("store_id", 0),
                            o.optString("store_name", "-"),
                            o.optString("processed_by", "-"),
                            o.optString("original_cashier", "-")
                    ));
                }

                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    items.clear();
                    items.addAll(temp);
                    adapter.notifyDataSetChanged();
                    tvPendingCount.setText("Pending: " + items.size());
                });

            } catch (Exception e) {
                e.printStackTrace();

                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(
                            this,
                            "Load failed: " + e.getMessage(),
                            Toast.LENGTH_LONG
                    ).show();
                });

            } finally {
                if (conn != null) conn.disconnect();
            }
        }).start();
    }

    private void confirmReturnToStore(RefundDecisionItem item) {
        new AlertDialog.Builder(this)
                .setTitle("Return to Store")
                .setMessage("Return this item to store inventory?\n\n"
                        + cleanItemName(item.getItemName())
                        + "\nQty: " + item.getQuantity()
                        + "\nStore: " + item.getStoreName())
                .setPositiveButton("Return", (d, w) -> processDecision(
                        ENV.REFUND_RETURN_TO_STORE_URL,
                        item,
                        "Returned to store"
                ))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showDisposeReasonDialog(RefundDecisionItem item) {
        EditText input = new EditText(this);
        input.setHint("Enter disposal reason...");
        input.setMinLines(2);
        input.setPadding(24, 16, 24, 16);

        new AlertDialog.Builder(this)
                .setTitle("Dispose Item")
                .setMessage(cleanItemName(item.getItemName())
                        + "\nQty: " + item.getQuantity())
                .setView(input)
                .setPositiveButton("Dispose", (d, w) -> {
                    String reason = input.getText().toString().trim();

                    if (reason.isEmpty()) {
                        Toast.makeText(this, "Disposal reason is required.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    processDecision(
                            ENV.REFUND_DISPOSE_ITEM_URL,
                            item,
                            reason
                    );
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void processDecision(String apiUrl, RefundDecisionItem item, String reason) {
        progressBar.setVisibility(View.VISIBLE);

        new Thread(() -> {
            HttpURLConnection conn = null;

            try {
                URL url = new URL(apiUrl);
                conn = (HttpURLConnection) url.openConnection();

                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                conn.setRequestProperty("Accept", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);

                JSONObject body = new JSONObject();
                body.put("refund_item_id", item.getRefundItemId());
                body.put("user_id", userId);
                body.put("reason", reason);

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

                String raw = res.toString().trim();

                if (!raw.startsWith("{")) {
                    throw new Exception("Server did not return JSON:\n" + raw);
                }

                JSONObject json = new JSONObject(raw);

                boolean success = json.optBoolean("success", false);
                String message = json.optString("message", "Action failed.");

                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show();

                    if (success) {
                        loadPendingRefundItems();
                    }
                });

            } catch (Exception e) {
                e.printStackTrace();

                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(
                            this,
                            "Action failed: " + e.getMessage(),
                            Toast.LENGTH_LONG
                    ).show();
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
}