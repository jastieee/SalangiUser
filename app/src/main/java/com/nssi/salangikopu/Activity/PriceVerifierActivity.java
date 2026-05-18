package com.nssi.salangikopu.Activity;

import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.nssi.salangikopu.Connection.ENV;
import com.nssi.salangikopu.R;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.DecimalFormat;

public class PriceVerifierActivity extends AppCompatActivity {

    EditText etBarcode;
    TextView tvSku, tvItemName, tvPrice, tvScanStatus, tvFocusIndicator;

    private long lastScanTime = 0;
    private static final long DEBOUNCE_MS = 100;
    private boolean isLooking = false;

    DecimalFormat fmt = new DecimalFormat("#,##0.00");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_price_verifier);

        etBarcode        = findViewById(R.id.etBarcode);
        tvSku            = findViewById(R.id.tvSku);
        tvItemName       = findViewById(R.id.tvItemName);
        tvPrice          = findViewById(R.id.tvPrice);
        tvScanStatus     = findViewById(R.id.tvScanStatus);
        tvFocusIndicator = findViewById(R.id.tvFocusIndicator);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        etBarcode.setOnFocusChangeListener((v, hasFocus) -> {
            tvFocusIndicator.setTextColor(hasFocus ? 0xFF4CAF50 : 0xFFF44336);
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

        // Keep focus on the hidden EditText so the scanner keeps feeding it
        findViewById(android.R.id.content).setOnClickListener(v -> etBarcode.requestFocus());

        etBarcode.post(() -> etBarcode.requestFocus());
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) etBarcode.requestFocus();
    }

    private void handleScan() {
        if (isLooking) {
            etBarcode.setText("");
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastScanTime < DEBOUNCE_MS) {
            etBarcode.setText("");
            return;
        }
        lastScanTime = now;

        String raw = etBarcode.getText().toString().trim();
        etBarcode.setText("");
        etBarcode.requestFocus();

        if (raw.isEmpty()) {
            tvScanStatus.setText("⚠ No barcode received");
            return;
        }

        String resolvedCode = resolveProductCode(raw);
        tvScanStatus.setText("Scanned: " + raw + " → " + resolvedCode);
        lookupProduct(resolvedCode);
    }

    private String resolveProductCode(String scanned) {
        if (scanned == null) return "";
        return scanned.trim().replaceAll("[^0-9A-Za-z]", "");
    }

    private void lookupProduct(String productCode) {
        isLooking = true;

        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                String urlStr = ENV.PRODUCT_LOOKUP_URL
                        + "?code=" + java.net.URLEncoder.encode(productCode, "UTF-8");
                Log.d("PRICE_VERIFY", "Calling: " + urlStr);

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

                JSONObject json = new JSONObject(res.toString());
                boolean success = json.optBoolean("success", false);

                if (!success) {
                    String message = json.optString("message", "Item not found.");
                    runOnUiThread(() -> {
                        tvSku.setText("—");
                        tvItemName.setText("Product not found");
                        tvPrice.setText("₱0.00");
                        tvScanStatus.setText("✗ " + message);
                        isLooking = false;
                        etBarcode.requestFocus();
                    });
                    return;
                }

                JSONObject product = json.getJSONObject("product");
                final String code  = product.getString("product_code");
                final String desc = cleanItemName(
                        product.getString("item_description")
                );
                final double price = product.getDouble("unit_price");

                runOnUiThread(() -> {
                    tvSku.setText(code);
                    tvItemName.setText(desc);
                    tvPrice.setText("₱" + fmt.format(price));
                    tvScanStatus.setText("✓ " + desc);
                    isLooking = false;
                    etBarcode.requestFocus();
                });

            } catch (Exception e) {
                e.printStackTrace();
                Log.e("PRICE_VERIFY", "lookup error: " + e.getMessage());
                runOnUiThread(() -> {
                    isLooking = false;
                    etBarcode.requestFocus();
                    Toast.makeText(this, "Lookup error: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                });
            } finally {
                if (conn != null) conn.disconnect();
            }
        }).start();
    }

    private String cleanItemName(String value) {
        if (value == null) return "";

        String text = value
                .replace("\uFE0F", "")   // variation selector (the invisible ️ char)
                .replace("–", "-")
                .replace("—", "-")
                .replaceAll("\\s+", " ")
                .trim();

        // Cut off everything after a tagline delimiter.
        // Pipe is the most common in your data (e.g. "Product | Marketing tagline").
        text = text.replaceFirst("\\s*\\|.*$", "").trim();

        // Colon-based taglines: "Product: best snack ever"
        text = text.replaceFirst("\\s*:\\s+.*$", "").trim();

        // Bullet / middot separators
        text = text.replaceFirst("\\s*[•·●].*$", "").trim();

        // Tilde separators sometimes used in PH e-commerce listings
        text = text.replaceFirst("\\s*~\\s+.*$", "").trim();

        // Dash-based tagline (e.g. "Brand - The original since 1995")
        text = text.replaceFirst("\\s*-\\s+.*$", "").trim();

        // Exclamation tagline
        text = text.replaceFirst("\\s+!\\s*.*$", "").trim();

        // Marketing-phrase tails
        text = text.replaceFirst("(?i)\\s+is the\\s+.*$", "").trim();
        text = text.replaceFirst("(?i)\\s+perfect for\\s+.*$", "").trim();
        text = text.replaceFirst("(?i)\\s+great for\\s+.*$", "").trim();
        text = text.replaceFirst("(?i)\\s+ideal for\\s+.*$", "").trim();
        text = text.replaceFirst("(?i)\\s+by\\s+.*$", "").trim();

        return text;
    }   }