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

import com.nssi.salangikopu.Model.RefundItem;
import com.nssi.salangikopu.R;

import java.text.DecimalFormat;
import java.util.List;

public class RefundItemAdapter extends ArrayAdapter<RefundItem> {

    private final DecimalFormat fmt = new DecimalFormat("#,##0.##");
    private boolean isPartial = false;
    private final OnRefundChangedListener listener;

    public interface OnRefundChangedListener {
        void onRefundChanged();
    }

    public RefundItemAdapter(Context context, List<RefundItem> items,
                             OnRefundChangedListener listener) {
        super(context, 0, items);
        this.listener = listener;
    }

    public void setPartialMode(boolean isPartial) {
        this.isPartial = isPartial;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        ViewHolder holder;

        if (convertView == null) {
            convertView = LayoutInflater.from(getContext())
                    .inflate(R.layout.item_refund, parent, false);

            holder = new ViewHolder();
            holder.tvItemName = convertView.findViewById(R.id.tvItemName);
            holder.tvProductCode = convertView.findViewById(R.id.tvProductCode);
            holder.tvUnitPrice = convertView.findViewById(R.id.tvUnitPrice);
            holder.tvOrigQty = convertView.findViewById(R.id.tvOrigQty);
            holder.tvRefundQty = convertView.findViewById(R.id.tvRefundQty);
            holder.tvRefundAmount = convertView.findViewById(R.id.tvRefundAmount);

            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        final RefundItem item = getItem(position);
        if (item == null) return convertView;

        holder.tvItemName.setText(item.getItemName());
        holder.tvProductCode.setText(item.getProductCode());
        holder.tvUnitPrice.setText(fmt.format(item.getUnitPrice()));
        holder.tvOrigQty.setText(fmt.format(item.getOriginalQty()));
        holder.tvRefundQty.setText(fmt.format(item.getRefundQty()));
        holder.tvRefundAmount.setText("₱" + fmt.format(item.getRefundAmount()));

        if (isPartial) {
            holder.tvRefundQty.setBackgroundResource(R.drawable.input_border);
            holder.tvRefundQty.setTextColor(Color.parseColor("#000000"));
            holder.tvRefundQty.setClickable(true);

            holder.tvRefundQty.setOnClickListener(v ->
                    showRefundQtyDialog(item, holder));
        } else {
            holder.tvRefundQty.setBackgroundColor(Color.TRANSPARENT);
            holder.tvRefundQty.setTextColor(Color.parseColor("#888888"));
            holder.tvRefundQty.setClickable(false);
            holder.tvRefundQty.setOnClickListener(null);
        }

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

    private void showRefundQtyDialog(RefundItem item, ViewHolder holder) {
        View dialogView = LayoutInflater.from(getContext())
                .inflate(R.layout.dialog_refund_qty, null);

        TextView tvTitle = dialogView.findViewById(R.id.tvRefundQtyTitle);
        TextView tvSubtitle = dialogView.findViewById(R.id.tvRefundQtySubtitle);
        TextView tvSold = dialogView.findViewById(R.id.tvRefundQtySold);
        EditText etValue = dialogView.findViewById(R.id.etRefundQtyValue);
        Button btnCancel = dialogView.findViewById(R.id.btnRefundQtyCancel);
        Button btnConfirm = dialogView.findViewById(R.id.btnRefundQtyConfirm);

        tvTitle.setText("Refund Quantity");
        tvSubtitle.setText(item.getProductCode() + " — " + item.getItemName());
        tvSold.setText("Sold: " + fmt.format(item.getOriginalQty()));

        DecimalFormat plain = new DecimalFormat("0.##");
        etValue.setText(plain.format(item.getRefundQty()));
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

        // Hide keyboard whenever the dialog is dismissed (back press, outside tap, etc.)
        dialog.setOnDismissListener(d -> {
            hideKeyboard(etValue);
            holder.tvRefundQty.clearFocus();
        });

        btnConfirm.setOnClickListener(v -> {
            String raw = etValue.getText().toString().trim();
            double value = 0;
            try {
                if (!raw.isEmpty()) value = Double.parseDouble(raw);
            } catch (NumberFormatException ignored) {
            }

            if (value < 0) value = 0;

            if (value > item.getOriginalQty()) {
                Toast.makeText(getContext(),
                        "Refund qty cannot exceed sold qty (" + fmt.format(item.getOriginalQty()) + ").",
                        Toast.LENGTH_SHORT).show();
                return;
            }

            item.setRefundQty(value);
            holder.tvRefundQty.setText(fmt.format(value));
            holder.tvRefundAmount.setText("₱" + fmt.format(item.getRefundAmount()));

            // Explicitly hide keyboard before dismissing
            hideKeyboard(etValue);
            dialog.dismiss();

            if (listener != null) listener.onRefundChanged();
        });

        btnCancel.setOnClickListener(v -> {
            hideKeyboard(etValue);
            dialog.dismiss();
        });
    }

    private static class ViewHolder {
        TextView tvItemName;
        TextView tvProductCode;
        TextView tvUnitPrice;
        TextView tvOrigQty;
        TextView tvRefundQty;
        TextView tvRefundAmount;
    }
}