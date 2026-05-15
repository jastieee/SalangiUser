package com.nssi.salangikopu.Adapter;

import android.content.Context;
import android.graphics.Color;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.TextView;

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
            holder.etRefundQty = convertView.findViewById(R.id.etRefundQty);
            holder.tvRefundAmount = convertView.findViewById(R.id.tvRefundAmount);

            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        RefundItem item = getItem(position);
        if (item == null) return convertView;

        if (holder.refundQtyWatcher != null) {
            holder.etRefundQty.removeTextChangedListener(holder.refundQtyWatcher);
        }

        holder.tvItemName.setText(item.getItemName());
        holder.tvProductCode.setText(item.getProductCode());
        holder.tvUnitPrice.setText("₱" + fmt.format(item.getUnitPrice()));
        holder.tvOrigQty.setText(fmt.format(item.getOriginalQty()));
        holder.etRefundQty.setText(fmt.format(item.getRefundQty()));
        holder.tvRefundAmount.setText("₱" + fmt.format(item.getRefundAmount()));

        holder.etRefundQty.setEnabled(isPartial);
        holder.etRefundQty.setFocusable(isPartial);
        holder.etRefundQty.setFocusableInTouchMode(isPartial);
        holder.etRefundQty.setCursorVisible(isPartial);

        holder.etRefundQty.setBackgroundResource(
                isPartial ? R.drawable.input_border : android.R.color.transparent
        );

        holder.etRefundQty.setOnClickListener(v -> {
            if (!isPartial) return;

            holder.etRefundQty.requestFocus();
            holder.etRefundQty.selectAll();

            InputMethodManager imm = (InputMethodManager)
                    getContext().getSystemService(Context.INPUT_METHOD_SERVICE);

            if (imm != null) {
                imm.showSoftInput(holder.etRefundQty, InputMethodManager.SHOW_IMPLICIT);
            }
        });

        holder.refundQtyWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                try {
                    String val = s.toString().trim().replace(",", "");
                    double qty = val.isEmpty() ? 0 : Double.parseDouble(val);

                    if (qty < 0) qty = 0;

                    if (qty > item.getOriginalQty()) {
                        qty = item.getOriginalQty();

                        holder.etRefundQty.removeTextChangedListener(this);
                        holder.etRefundQty.setText(fmt.format(qty));
                        holder.etRefundQty.setSelection(holder.etRefundQty.getText().length());
                        holder.etRefundQty.addTextChangedListener(this);
                    }

                    item.setRefundQty(qty);
                    holder.tvRefundAmount.setText("₱" + fmt.format(item.getRefundAmount()));

                    if (listener != null) {
                        listener.onRefundChanged();
                    }

                } catch (Exception ignored) {
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        };

        holder.etRefundQty.addTextChangedListener(holder.refundQtyWatcher);

        convertView.setBackgroundColor(
                position % 2 == 0 ? Color.WHITE : Color.parseColor("#F5F5F5")
        );

        return convertView;
    }

    private static class ViewHolder {
        TextView tvItemName;
        TextView tvProductCode;
        TextView tvUnitPrice;
        TextView tvOrigQty;
        TextView tvRefundAmount;
        EditText etRefundQty;
        TextWatcher refundQtyWatcher;
    }
}