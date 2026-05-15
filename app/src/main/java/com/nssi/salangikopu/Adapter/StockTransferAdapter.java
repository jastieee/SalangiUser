package com.nssi.salangikopu.Adapter;

import android.content.Context;
import android.graphics.Color;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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

    public interface OnTotalChangedListener {
        void onTotalChanged();
    }

    public StockTransferAdapter(Context context, List<StockTransferItem> items,
                                OnTotalChangedListener listener) {
        super(context, 0, items);
        this.listener = listener;
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
            holder.etTransferQty = convertView.findViewById(R.id.etTransferQty);
            holder.etUnitPrice = convertView.findViewById(R.id.etUnitPrice);
            holder.tvSubtotal = convertView.findViewById(R.id.tvSubtotal);
            holder.btnRemove = convertView.findViewById(R.id.btnRemove);
            holder.tvStockWarning = convertView.findViewById(R.id.tvStockWarning);

            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        StockTransferItem item = getItem(position);
        if (item == null) return convertView;

        if (holder.transferQtyWatcher != null) {
            holder.etTransferQty.removeTextChangedListener(holder.transferQtyWatcher);
        }
        if (holder.unitPriceWatcher != null) {
            holder.etUnitPrice.removeTextChangedListener(holder.unitPriceWatcher);
        }

        holder.tvProductCode.setText(item.getProductCode());
        holder.tvDescription.setText(item.getDescription());
        holder.tvWarehouseQty.setText(fmt.format(item.getWarehouseQty()));
        holder.etTransferQty.setText(fmt.format(item.getTransferQty()));
        holder.etUnitPrice.setText(costFmt.format(item.getUnitPrice()));
        holder.tvSubtotal.setText("₱" + costFmt.format(item.getSubtotal()));

        holder.transferQtyWatcher = new SimpleTextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                try {
                    String val = s.toString().trim();
                    double qty = val.isEmpty() ? 0 : Double.parseDouble(val);

                    if (qty < 0) qty = 0;

                    item.setTransferQty(qty);
                    holder.tvSubtotal.setText("₱" + costFmt.format(item.getSubtotal()));
                    updateWarning(holder, item);

                    if (listener != null) {
                        listener.onTotalChanged();
                    }

                } catch (Exception ignored) {
                }
            }
        };

        holder.unitPriceWatcher = new SimpleTextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                try {
                    String val = s.toString().trim();
                    double price = val.isEmpty() ? 0 : Double.parseDouble(val);

                    if (price < 0) price = 0;

                    item.setUnitPrice(price);
                    holder.tvSubtotal.setText("₱" + costFmt.format(item.getSubtotal()));
                    updateWarning(holder, item);

                    if (listener != null) {
                        listener.onTotalChanged();
                    }

                } catch (Exception ignored) {
                }
            }
        };

        holder.etTransferQty.addTextChangedListener(holder.transferQtyWatcher);
        holder.etUnitPrice.addTextChangedListener(holder.unitPriceWatcher);

        holder.btnRemove.setOnClickListener(v -> {
            remove(item);
            notifyDataSetChanged();

            if (listener != null) {
                listener.onTotalChanged();
            }
        });

        updateWarning(holder, item);

        convertView.setBackgroundColor(
                position % 2 == 0 ? Color.WHITE : Color.parseColor("#F5F5F5")
        );

        return convertView;
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
            holder.etTransferQty.setBackgroundColor(Color.parseColor("#FFEBEE"));
        } else {
            holder.tvStockWarning.setVisibility(View.GONE);
            holder.etTransferQty.setBackgroundResource(R.drawable.input_border);
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
        TextView tvSubtotal;
        TextView tvStockWarning;
        EditText etTransferQty;
        EditText etUnitPrice;
        Button btnRemove;

        TextWatcher transferQtyWatcher;
        TextWatcher unitPriceWatcher;
    }

    private abstract static class SimpleTextWatcher implements TextWatcher {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void afterTextChanged(Editable s) {
        }
    }
}