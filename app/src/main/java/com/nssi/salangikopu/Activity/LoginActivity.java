package com.nssi.salangikopu.Activity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.nssi.salangikopu.Connection.ENV;
import com.nssi.salangikopu.R;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class LoginActivity extends AppCompatActivity {

    EditText etUsername, etPassword;
    Button btnSignIn;
    TextView tvError;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        etUsername = findViewById(R.id.etUsername);
        etPassword = findViewById(R.id.etPassword);
        btnSignIn  = findViewById(R.id.btnSignIn);
        tvError    = findViewById(R.id.tvError);

        btnSignIn.setOnClickListener(v -> attemptLogin());
    }

    private void attemptLogin() {
        String username = etUsername.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (username.isEmpty() || password.isEmpty()) {
            showError("Please enter username and password.");
            return;
        }

        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(ENV.LOGIN_URL);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                connection.setRequestProperty("Accept", "application/json");
                connection.setDoOutput(true);
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(15000);

                JSONObject requestBody = new JSONObject();
                requestBody.put("username", username);
                requestBody.put("password", password);

                OutputStream os = connection.getOutputStream();
                os.write(requestBody.toString().getBytes("UTF-8"));
                os.flush();
                os.close();

                int statusCode = connection.getResponseCode();

                InputStream is = (statusCode >= 200 && statusCode < 300)
                        ? connection.getInputStream()
                        : connection.getErrorStream();

                if (is == null) {
                    showError("Empty server response.");
                    return;
                }

                BufferedReader reader = new BufferedReader(new InputStreamReader(is));
                StringBuilder response = new StringBuilder();
                String line;

                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }

                reader.close();

                JSONObject json = new JSONObject(response.toString());
                boolean success = json.optBoolean("success", false);
                String message = json.optString("message", "Login failed.");

                if (!success) {
                    showError(message);
                    return;
                }

                JSONObject user = json.getJSONObject("user");

                int userId = user.optInt("user_id", 0);
                int roleId = user.optInt("role_id", 0);
                int storeId = user.optInt("store_id", 0);
                int warehouseId = user.optInt("warehouse_id", 0);

                String uname = user.optString("username", "");
                String fullName = user.optString("full_name", uname);
                String roleName = user.optString("role_name", "");
                String storeName = user.optString("store_name", "");
                String warehouseName = user.optString("warehouse_name", "");

                runOnUiThread(() -> {
                    Intent intent = new Intent(LoginActivity.this, MainActivity.class);
                    intent.putExtra("user_id", userId);
                    intent.putExtra("role_id", roleId);
                    intent.putExtra("store_id", storeId);
                    intent.putExtra("username", uname);
                    intent.putExtra("full_name", fullName);
                    intent.putExtra("role_name", roleName);
                    intent.putExtra("store_name", storeName);
                    intent.putExtra("warehouse_id", warehouseId);
                    intent.putExtra("warehouse_name", warehouseName);
                    startActivity(intent);
                    finish();
                });

            } catch (Exception e) {
                e.printStackTrace();
                showError("Login failed. Check your connection.");
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }).start();
    }

    private void showError(String message) {
        runOnUiThread(() -> {
            tvError.setText(message);
            tvError.setVisibility(View.VISIBLE);
        });
    }
}