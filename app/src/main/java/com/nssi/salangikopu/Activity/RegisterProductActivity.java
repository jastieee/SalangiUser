package com.nssi.salangikopu.Activity;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.nssi.salangikopu.R;
import com.nssi.salangikopu.Connection.ENV;

import org.json.JSONObject;

import java.io.OutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.net.HttpURLConnection;
import java.net.URL;


public class RegisterProductActivity extends AppCompatActivity {

    EditText etProductCode, etDescription, etUom, etCategory;
    Button btnRegister, btnCancel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register_product);

        etProductCode = findViewById(R.id.etProductCode);
        etDescription = findViewById(R.id.etDescription);
        etUom         = findViewById(R.id.etUom);
        etCategory    = findViewById(R.id.etCategory);
        btnRegister   = findViewById(R.id.btnRegister);
        btnCancel     = findViewById(R.id.btnCancel);

        // Pre-fill product code from scan
        String scannedCode = getIntent().getStringExtra("product_code");
        if (scannedCode != null) etProductCode.setText(scannedCode);

        btnCancel.setOnClickListener(v -> finish());

        btnRegister.setOnClickListener(v -> {

            String code = etProductCode.getText().toString().trim();
            String desc = etDescription.getText().toString().trim();
            String uom  = etUom.getText().toString().trim();
            String cat  = etCategory.getText().toString().trim();

            if (code.isEmpty() || desc.isEmpty()) {
                Toast.makeText(this, "Product code and description are required.", Toast.LENGTH_SHORT).show();
                return;
            }

            new Thread(() -> {
                HttpURLConnection conn = null;

                try {
                    URL url = new URL(ENV.REGISTER_PRODUCT_URL);
                    conn = (HttpURLConnection) url.openConnection();

                    conn.setRequestMethod("POST");
                    conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                    conn.setRequestProperty("Accept", "application/json");
                    conn.setDoOutput(true);
                    conn.setConnectTimeout(15000);
                    conn.setReadTimeout(15000);

                    JSONObject body = new JSONObject();
                    body.put("product_code", code);
                    body.put("description", desc);
                    body.put("uom", uom);
                    body.put("category", cat);

                    OutputStream os = conn.getOutputStream();
                    os.write(body.toString().getBytes("UTF-8"));
                    os.flush();
                    os.close();

                    int statusCode = conn.getResponseCode();

                    InputStream is = (statusCode >= 200 && statusCode < 300)
                            ? conn.getInputStream()
                            : conn.getErrorStream();

                    if (is == null) {
                        throw new Exception("Empty response from server");
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
                    String message  = json.optString("message", "Failed");

                    runOnUiThread(() -> {
                        if (success) {
                            Toast.makeText(this, "Product registered!", Toast.LENGTH_SHORT).show();

                            Intent result = new Intent();
                            result.putExtra("product_code", code);
                            setResult(RESULT_OK, result);
                            finish();
                        } else {
                            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                        }
                    });

                } catch (Exception e) {
                    e.printStackTrace();
                    runOnUiThread(() ->
                            Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_LONG).show()
                    );
                } finally {
                    if (conn != null) conn.disconnect();
                }
            }).start();
        });
    }
}