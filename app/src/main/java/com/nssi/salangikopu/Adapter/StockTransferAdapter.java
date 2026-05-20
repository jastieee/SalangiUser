package com.nssi.salangikopu.Adapter;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.nssi.salangikopu.Model.StockTransferItem;
import com.nssi.salangikopu.R;

import java.text.DecimalFormat;
import java.util.List;

public class StockTransferAdapter extends ArrayAdapter<StockTransferItem> {

    private final DecimalFormat fmt = new DecimalFormat("#,##0.##");
    private final DecimalFormat costFmt = new DecimalFormat("#,##0.00");
    private final OnTotalChangedListener listener;
    private final OnDialogDismissedListener dismissListener;

    public interface OnTotalChangedListener {
        void onTotalChanged();
    }

    public interface OnDialogDismissedListener {
        void onDialogDismissed();
    }

    public StockTransferAdapter(Context context, List<StockTransferItem> items,
                                OnTotalChangedListener listener) {
        this(context, items, listener, null);
    }

    public StockTransferAdapter(Context context, List<StockTransferItem> items,
                                OnTotalChangedListener listener,
                                OnDialogDismissedListener dismissListener) {
        super(context, 0, items);
        this.listener = listener;
        this.dismissListener = dismissListener;
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        ViewHolder holder;

        if (convertView == null) {
            convertView = LayoutInflater.from(getContext())
                    .inflate(R.layout.item_stock_transfer, parent, false);

            holder = new ViewHolder();
            holder.tvProductCode = convertView.findViewById(R.id.tvProductCode);
            holder.tvDescription = convertView.findViewById(R.id.tvDescription);
            holder.tvWarehouseQty = convertView.findViewById(R.id.tvWarehouseQty);
            holder.tvTransferQty = convertView.findViewById(R.id.tvTransferQty);
            holder.tvUnitPrice = convertView.findViewById(R.id.tvUnitPrice);
            holder.tvSubtotal = convertView.findViewById(R.id.tvSubtotal);
            holder.btnRemove = convertView.findViewById(R.id.btnRemove);
            holder.tvStockWarning = convertView.findViewById(R.id.tvStockWarning);

            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        final StockTransferItem item = getItem(position);
        if (item == null) return convertView;

        holder.tvProductCode.setText(item.getProductCode());
        holder.tvDescription.setText(item.getDescription());
        holder.tvWarehouseQty.setText(fmt.format(item.getWarehouseQty()));
        holder.tvTransferQty.setText(fmt.format(item.getTransferQty()));
        holder.tvUnitPrice.setText(costFmt.format(item.getUnitPrice()));
        holder.tvSubtotal.setText("₱" + costFmt.format(item.getSubtotal()));

        // Tap qty -> modal
        holder.tvTransferQty.setOnClickListener(v ->
                showNumericInputDialog(
                        "Enter Quantity",
                        item.getProductCode() + " — " + item.getDescription(),
                        item.getTransferQty(),
                        fmt,
                        newValue -> {
                            item.setTransferQty(newValue);
                            notifyDataSetChanged();
                            if (listener != null) listener.onTotalChanged();
                        }
                )
        );

//        // Tap price -> modal
//        holder.tvUnitPrice.setOnClickListener(v ->
//                showNumericInputDialog(
//                        "Enter Unit Price",
//                        item.getProductCode() + " — " + item.getDescription(),
//                        item.getUnitPrice(),
//                        costFmt,
//                        newValue -> {
//                            item.setUnitPrice(newValue);
//                            notifyDataSetChanged();
//                            if (listener != null) listener.onTotalChanged();
//                        }
//                )
//        );

        holder.btnRemove.setOnClickListener(v -> {
            remove(item);
            notifyDataSetChanged();
            if (listener != null) listener.onTotalChanged();
        });

        updateWarning(holder, item);

        convertView.setBackgroundColor(
                position % 2 == 0 ? Color.WHITE : Color.parseColor("#F5F5F5")
        );

        return convertView;
    }

    private interface OnValueEntered {
        void onValue(double value);
    }

    private void showNumericInputDialog(String title, String subtitle, double currentValue,
                                        DecimalFormat displayFmt, OnValueEntered callback) {
        View dialogView = LayoutInflater.from(getContext())
                .inflate(R.layout.dialog_qty_input, null);

        TextView tvTitle = dialogView.findViewById(R.id.tvQtyDialogTitle);
        TextView tvSubtitle = dialogView.findViewById(R.id.tvQtyDialogSubtitle);
        EditText etValue = dialogView.findViewById(R.id.etQtyDialogValue);
        Button btnCancel = dialogView.findViewById(R.id.btnQtyDialogCancel);
        Button btnConfirm = dialogView.findViewById(R.id.btnQtyDialogConfirm);

        tvTitle.setText(title);
        tvSubtitle.setText(subtitle);

        // Plain numeric string (no thousand separators) so user can edit easily
        DecimalFormat plain = new DecimalFormat("0.##");
        etValue.setText(plain.format(currentValue));
        etValue.setSelection(etValue.getText().length());

        AlertDialog dialog = new AlertDialog.Builder(getContext())
                .setView(dialogView)
                .setCancelable(false)
                .create();

        // Make sure soft keyboard pops up automatically
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            dialog.getWindow().setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
            );
        }

        dialog.show();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(
                    (int) (getContext().getResources().getDisplayMetrics().widthPixels * 0.88),
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
        }

        etValue.requestFocus();

        btnConfirm.setOnClickListener(v -> {
            String raw = etValue.getText().toString().trim();
            double value = 0;
            try {
                if (!raw.isEmpty()) value = Double.parseDouble(raw);
            } catch (NumberFormatException ignored) {
            }
            if (value < 0) value = 0;

            dialog.dismiss();
            callback.onValue(value);

            // Return focus to hidden scanner input
            if (dismissListener != null) dismissListener.onDialogDismissed();
        });

        btnCancel.setOnClickListener(v -> {
            dialog.dismiss();
            if (dismissListener != null) dismissListener.onDialogDismissed();
        });
    }

    private void updateWarning(ViewHolder holder, StockTransferItem item) {
        if (item.isOverStock()) {
            holder.tvStockWarning.setVisibility(View.VISIBLE);
            holder.tvStockWarning.setText(
                    "⚠ Transfer qty (" + fmt.format(item.getTransferQty()) +
                            ") exceeds warehouse stock (" +
                            fmt.format(item.getWarehouseQty()) +
                            "). Please reduce the quantity to match what the store confirmed."
            );
            holder.tvTransferQty.setBackgroundColor(Color.parseColor("#FFEBEE"));
        } else {
            holder.tvStockWarning.setVisibility(View.GONE);
            holder.tvTransferQty.setBackgroundResource(R.drawable.input_border);
        }
    }

    public boolean hasStockViolation() {
        for (int i = 0; i < getCount(); i++) {
            StockTransferItem item = getItem(i);
            if (item != null && item.isOverStock()) {
                return true;
            }
        }
        return false;
    }

    private static class ViewHolder {
        TextView tvProductCode;
        TextView tvDescription;
        TextView tvWarehouseQty;
        TextView tvTransferQty;   // was EditText
        TextView tvUnitPrice;     // was EditText
        TextView tvSubtotal;
        TextView tvStockWarning;
        Button btnRemove;
    }
}