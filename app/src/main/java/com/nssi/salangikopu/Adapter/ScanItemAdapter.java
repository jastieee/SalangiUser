package com.nssi.salangikopu.Adapter;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.os.IBinder;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.nssi.salangikopu.Model.ScannedItem;
import com.nssi.salangikopu.R;

import java.text.DecimalFormat;
import java.util.List;

public class ScanItemAdapter extends ArrayAdapter<ScannedItem> {

    private final DecimalFormat qtyFmt = new DecimalFormat("#,##0.##");
    private final DecimalFormat moneyFmt = new DecimalFormat("#,##0.00");
    private final OnTotalChangedListener listener;
    private final OnDialogDismissedListener dismissListener;

    public interface OnTotalChangedListener {
        void onTotalChanged();
    }

    public interface OnDialogDismissedListener {
        void onDialogDismissed();
    }

    public ScanItemAdapter(Context context, List<ScannedItem> items,
                           OnTotalChangedListener listener) {
        this(context, items, listener, null);
    }

    public ScanItemAdapter(Context context, List<ScannedItem> items,
                           OnTotalChangedListener listener,
                           OnDialogDismissedListener dismissListener) {
        super(context, 0, items);
        this.listener = listener;
        this.dismissListener = dismissListener;
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView,
                        @NonNull ViewGroup parent) {

        ViewHolder holder;

        if (convertView == null) {
            convertView = LayoutInflater.from(getContext())
                    .inflate(R.layout.item_scan, parent, false);

            holder = new ViewHolder();
            holder.tvProductCode = convertView.findViewById(R.id.tvProductCode);
            holder.tvDescription = convertView.findViewById(R.id.tvDescription);
            holder.tvUnitPrice = convertView.findViewById(R.id.tvUnitPrice);
            holder.tvQty = convertView.findViewById(R.id.tvQty);
            holder.tvSubtotal = convertView.findViewById(R.id.tvSubtotal);
            holder.btnRemove = convertView.findViewById(R.id.btnRemove);

            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        final ScannedItem item = getItem(position);
        if (item == null) return convertView;

        holder.tvProductCode.setText(item.getProductCode());
        holder.tvDescription.setText(cleanItemName(item.getDescription()));
        holder.tvUnitPrice.setText("₱" + moneyFmt.format(item.getUnitPrice()));
        holder.tvQty.setText(qtyFmt.format(item.getQuantity()));
        holder.tvSubtotal.setText("₱" + moneyFmt.format(item.getSubtotal()));

        // Tap qty -> modal
        holder.tvQty.setOnClickListener(v -> showQtyDialog(item, holder));

        holder.btnRemove.setOnClickListener(v -> {
            remove(item);
            notifyDataSetChanged();
            if (listener != null) listener.onTotalChanged();
        });

        convertView.setBackgroundColor(
                position % 2 == 0 ? Color.WHITE : Color.parseColor("#F5F5F5")
        );

        return convertView;
    }

    private void hideKeyboard(View view) {
        if (view == null) return;
        InputMethodManager imm = (InputMethodManager)
                getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm == null) return;

        IBinder token = view.getWindowToken();
        if (token != null) {
            imm.hideSoftInputFromWindow(token, 0);
        }
    }

    private void showQtyDialog(ScannedItem item, ViewHolder holder) {
        View dialogView = LayoutInflater.from(getContext())
                .inflate(R.layout.dialog_qty_input, null);

        TextView tvTitle = dialogView.findViewById(R.id.tvQtyDialogTitle);
        TextView tvSubtitle = dialogView.findViewById(R.id.tvQtyDialogSubtitle);
        EditText etValue = dialogView.findViewById(R.id.etQtyDialogValue);
        Button btnCancel = dialogView.findViewById(R.id.btnQtyDialogCancel);
        Button btnConfirm = dialogView.findViewById(R.id.btnQtyDialogConfirm);

        tvTitle.setText("Enter Quantity");
        tvSubtitle.setText(item.getProductCode() + " — " + cleanItemName(item.getDescription()));

        DecimalFormat plain = new DecimalFormat("0.##");
        etValue.setText(plain.format(item.getQuantity()));
        etValue.setSelection(etValue.getText().length());

        AlertDialog dialog = new AlertDialog.Builder(getContext())
                .setView(dialogView)
                .setCancelable(false)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            dialog.getWindow().setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        }

        dialog.show();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(
                    (int) (getContext().getResources().getDisplayMetrics().widthPixels * 0.88),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        etValue.requestFocus();

        // Hide keyboard + return scanner focus on any dismiss path
        dialog.setOnDismissListener(d -> {
            hideKeyboard(etValue);
            if (dismissListener != null) dismissListener.onDialogDismissed();
        });

        btnConfirm.setOnClickListener(v -> {
            String raw = etValue.getText().toString().trim();
            double value = 0;
            try {
                if (!raw.isEmpty()) value = Double.parseDouble(raw);
            } catch (NumberFormatException ignored) {
            }

            if (value <= 0) {
                Toast.makeText(getContext(),
                        "Quantity must be greater than 0. Use the X button to remove.",
                        Toast.LENGTH_SHORT).show();
                return;
            }

            // Don't exceed store stock
            if (item.getQuantityInStock() > 0 && value > item.getQuantityInStock()) {
                Toast.makeText(getContext(),
                        "Only " + qtyFmt.format(item.getQuantityInStock())
                                + " unit(s) available in store.",
                        Toast.LENGTH_SHORT).show();
                return;
            }

            // Don't exceed promo batch remaining (if item has a promo)
            if (item.hasPromo() && item.getPromoBatchRemaining() > 0
                    && value > item.getPromoBatchRemaining()) {
                Toast.makeText(getContext(),
                        "Promo limit reached. Only "
                                + qtyFmt.format(item.getPromoBatchRemaining())
                                + " unit(s) at promo price.",
                        Toast.LENGTH_SHORT).show();
                return;
            }

            item.setQuantity(value);
            holder.tvQty.setText(qtyFmt.format(value));
            holder.tvSubtotal.setText("₱" + moneyFmt.format(item.getSubtotal()));

            hideKeyboard(etValue);
            dialog.dismiss();

            if (listener != null) listener.onTotalChanged();
        });

        btnCancel.setOnClickListener(v -> {
            hideKeyboard(etValue);
            dialog.dismiss();
        });
    }

    private String cleanItemName(String value) {
        if (value == null) return "";

        String text = value
                .replace("\uFE0F", "")
                .replace("–", "-")
                .replace("—", "-")
                .replaceAll("\\s+", " ")
                .trim();

        text = text.replaceFirst("\\s*\\|.*$", "").trim();
        text = text.replaceFirst("\\s*:\\s+.*$", "").trim();
        text = text.replaceFirst("\\s*[•·●].*$", "").trim();
        text = text.replaceFirst("\\s*~\\s+.*$", "").trim();
        text = text.replaceFirst("\\s*-\\s+.*$", "").trim();
        text = text.replaceFirst("\\s+!\\s*.*$", "").trim();
        text = text.replaceFirst("(?i)\\s+is the\\s+.*$", "").trim();
        text = text.replaceFirst("(?i)\\s+perfect for\\s+.*$", "").trim();
        text = text.replaceFirst("(?i)\\s+great for\\s+.*$", "").trim();
        text = text.replaceFirst("(?i)\\s+ideal for\\s+.*$", "").trim();
        text = text.replaceFirst("(?i)\\s+by\\s+.*$", "").trim();

        return text;
    }

    private static class ViewHolder {
        TextView tvProductCode;
        TextView tvDescription;
        TextView tvUnitPrice;
        TextView tvQty;
        TextView tvSubtotal;
        Button btnRemove;
    }
}