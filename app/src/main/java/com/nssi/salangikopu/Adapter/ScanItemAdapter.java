package com.nssi.salangikopu.Adapter;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.TextView;

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

    public interface OnTotalChangedListener {
        void onTotalChanged();
    }

    public ScanItemAdapter(Context context, List<ScannedItem> items,
                           OnTotalChangedListener listener) {
        super(context, 0, items);
        this.listener = listener;
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

        ScannedItem item = getItem(position);
        if (item == null) {
            return convertView;
        }

        holder.tvProductCode.setText(item.getProductCode());
        holder.tvDescription.setText(cleanItemName(item.getDescription()));
        holder.tvUnitPrice.setText("₱" + moneyFmt.format(item.getUnitPrice()));
        holder.tvQty.setText("x" + qtyFmt.format(item.getQuantity()));
        holder.tvSubtotal.setText("₱" + moneyFmt.format(item.getSubtotal()));

        holder.btnRemove.setOnClickListener(null);
        holder.btnRemove.setOnClickListener(v -> {
            remove(item);
            notifyDataSetChanged();

            if (listener != null) {
                listener.onTotalChanged();
            }
        });

        convertView.setBackgroundColor(
                position % 2 == 0 ? Color.WHITE : Color.parseColor("#F5F5F5")
        );

        return convertView;
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