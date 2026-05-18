package com.nssi.salangikopu.Activity;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.nssi.salangikopu.Connection.ENV;
import com.nssi.salangikopu.R;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    TextView tvWelcome, tvDateTime;
    LinearLayout cardWarehouseInventory, cardStoreInventory, cardScanItems,
            cardStockTransfers, cardDeliveryIn, cardRefundItems, cardTransferConfirmations, cardPriceVerifier;

    Handler clockHandler = new Handler();
    Runnable clockRunnable;

    int    userId       = -1;
    int    roleId       = -1;
    String username;
    int    storeId      = -1;
    String storeName    = "";
    String fullName     = "";

    // ── Warehouse fields — read from Intent, passed from LoginActivity ────────
    int    warehouseId   = 0;   // 0 = NULL in DB = no warehouse access
    String warehouseName = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvWelcome              = findViewById(R.id.tvWelcome);
        tvDateTime             = findViewById(R.id.tvDateTime);
        cardWarehouseInventory = findViewById(R.id.cardWarehouseInventory);
        cardStoreInventory     = findViewById(R.id.cardStoreInventory);
        cardScanItems          = findViewById(R.id.cardScanItems);
        cardStockTransfers     = findViewById(R.id.cardStockTransfers);
        cardDeliveryIn         = findViewById(R.id.cardDeliveryIn);
        cardRefundItems        = findViewById(R.id.cardRefundItems);
        cardTransferConfirmations = findViewById(R.id.cardTransferConfirmations);
        cardPriceVerifier       = findViewById(R.id.cardPriceVerifier);

        userId        = getIntent().getIntExtra("user_id", -1);
        roleId        = getIntent().getIntExtra("role_id", -1);
        storeId       = getIntent().getIntExtra("store_id", -1);
        storeName     = getIntent().getStringExtra("store_name");
        username      = getIntent().getStringExtra("username");
        warehouseId   = getIntent().getIntExtra("warehouse_id", 0);
        warehouseName = getIntent().getStringExtra("warehouse_name");

        if (storeName     == null) storeName     = "";
        if (warehouseName == null) warehouseName = "";

        fullName = getIntent().getStringExtra("full_name");
        if (fullName == null || fullName.isEmpty()) fullName = username;

        tvWelcome.setText("Welcome, " + (username != null ? username : "User"));

        // Live clock
        clockRunnable = new Runnable() {
            @Override
            public void run() {
                String now = new SimpleDateFormat(
                        "EEEE, MMM dd yyyy  hh:mm:ss a",
                        Locale.getDefault()
                ).format(new Date());
                tvDateTime.setText(now);
                clockHandler.postDelayed(this, 1000);
            }
        };
        clockHandler.post(clockRunnable);

        hideAll();
        loadPermissions(roleId);

        // ── Card click listeners ──────────────────────────────────────────────

        cardWarehouseInventory.setOnClickListener(v -> {
            Intent intent = new Intent(this, WarehouseInventoryActivity.class);
            intent.putExtra(WarehouseInventoryActivity.EXTRA_WAREHOUSE_ID,   warehouseId);
            intent.putExtra(WarehouseInventoryActivity.EXTRA_WAREHOUSE_NAME, warehouseName);
            startActivity(intent);
        });

        cardStoreInventory.setOnClickListener(v -> {
            Intent intent = new Intent(this, StoreInventoryActivity.class);
            intent.putExtra("store_id", storeId);
            startActivity(intent);
        });

        cardScanItems.setOnClickListener(v -> {
            Intent intent = new Intent(this, ScanItemsActivity.class);
            intent.putExtra("user_id",    userId);
            intent.putExtra("store_id",   storeId);
            intent.putExtra("store_name", storeName);
            intent.putExtra("username",   username);
            intent.putExtra("full_name",  fullName);
            startActivity(intent);
        });

        cardStockTransfers.setOnClickListener(v -> {
            Intent intent = new Intent(this, StockTransferActivity.class);
            intent.putExtra("user_id",    userId);
            intent.putExtra("store_id",   storeId);
            intent.putExtra("store_name", storeName);
            startActivity(intent);
        });

        cardDeliveryIn.setOnClickListener(v -> {
            Intent intent = new Intent(this, DeliveryInActivity.class);
            intent.putExtra("user_id", userId);
            startActivity(intent);
        });

        cardRefundItems.setOnClickListener(v -> {
            Intent intent = new Intent(this, RefundItemsActivity.class);
            intent.putExtra("user_id", userId);
            startActivity(intent);
        });

        cardTransferConfirmations.setOnClickListener(v -> {
            Intent intent = new Intent(this, TransferConfirmationActivity.class);
            intent.putExtra("user_id",  userId);
            intent.putExtra("store_id", storeId);
            startActivity(intent);
        });

        findViewById(R.id.cardRefundDecisions).setOnClickListener(v -> {
            Intent intent = new Intent(this, RefundDecisionActivity.class);
            intent.putExtra("user_id", userId);
            intent.putExtra("store_id", storeId);
            startActivity(intent);
        });

        findViewById(R.id.cardBadOrder).setOnClickListener(v -> {
            Intent intent = new Intent(this, BadOrderActivity.class);
            intent.putExtra("user_id", userId);
            intent.putExtra("store_id", storeId);
            startActivity(intent);
        });

        findViewById(R.id.cardPriceVerifier).setOnClickListener(v -> {
            Intent intent = new Intent(this, PriceVerifierActivity.class);
            intent.putExtra("user_id", userId);
            intent.putExtra("store_id", storeId);
            startActivity(intent);
        });

        // Logout
        findViewById(R.id.btnLogout).setOnClickListener(v ->
                new AlertDialog.Builder(this)
                        .setTitle("Logout")
                        .setMessage("Are you sure you want to logout?")
                        .setPositiveButton("Yes", (dialog, which) -> {
                            startActivity(new Intent(this, LoginActivity.class));
                            finish();
                        })
                        .setNegativeButton("Cancel", null)
                        .show()
        );
    }

    private void loadPermissions(int roleId) {
        new Thread(() -> {
            HttpURLConnection connection = null;

            try {
                URL url = new URL(ENV.MODULES_URL + "?role_id=" + roleId);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("Accept", "application/json");

                int statusCode = connection.getResponseCode();

                InputStream is = (statusCode >= 200 && statusCode < 300)
                        ? connection.getInputStream()
                        : connection.getErrorStream();

                if (is == null) return;

                BufferedReader reader = new BufferedReader(new InputStreamReader(is));
                StringBuilder response = new StringBuilder();
                String line;

                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }

                reader.close();

                JSONObject json = new JSONObject(response.toString());

                if (!json.optBoolean("success")) return;

                JSONArray arr = json.getJSONArray("modules");

                Set<String> allowed = new HashSet<>();
                for (int i = 0; i < arr.length(); i++) {
                    allowed.add(arr.getString(i));
                }

                runOnUiThread(() -> applyPermissions(allowed));

            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (connection != null) connection.disconnect();
            }
        }).start();
    }

    private void applyPermissions(Set<String> allowed) {
        setCard(cardWarehouseInventory,    allowed.contains(ENV.MOD_WAREHOUSE_INVENTORY));
        setCard(cardStoreInventory,        allowed.contains(ENV.MOD_STORE_INVENTORY));
        setCard(cardScanItems,             allowed.contains(ENV.MOD_SCAN_ITEMS));
        setCard(cardStockTransfers,        allowed.contains(ENV.MOD_STOCK_TRANSFERS));
        setCard(cardDeliveryIn,            allowed.contains(ENV.MOD_DELIVERY_IN));
        setCard(cardRefundItems,           allowed.contains(ENV.MOD_REFUND_ITEMS));

        setCard(cardTransferConfirmations, allowed.contains("Transfer Confirmations - Mobile"));

        View badOrder = findViewById(R.id.cardBadOrder);
        setCard(badOrder, allowed.contains(ENV.MOD_BAD_ORDER));
    }

    private void setCard(View v, boolean visible) {
        v.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private void hideAll() {
        cardWarehouseInventory.setVisibility(View.GONE);
        cardStoreInventory.setVisibility(View.GONE);
        cardScanItems.setVisibility(View.GONE);
        cardStockTransfers.setVisibility(View.GONE);
        cardDeliveryIn.setVisibility(View.GONE);
        cardRefundItems.setVisibility(View.GONE);
        cardTransferConfirmations.setVisibility(View.GONE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        clockHandler.removeCallbacks(clockRunnable);
    }
}