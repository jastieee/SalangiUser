package com.nssi.salangikopu.Adapter;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.nssi.salangikopu.Model.WarehouseInventoryItem;
import com.nssi.salangikopu.R;

import java.text.DecimalFormat;
import java.util.List;

public class WarehouseInventoryAdapter extends ArrayAdapter<WarehouseInventoryItem> {

    private final DecimalFormat qtyFmt = new DecimalFormat("#,##0.####");
    private final DecimalFormat costFmt = new DecimalFormat("#,##0.00");

    public WarehouseInventoryAdapter(Context context, List<WarehouseInventoryItem> items) {
        super(context, 0, items);
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        ViewHolder holder;

        if (convertView == null) {
            convertView = LayoutInflater.from(getContext())
                    .inflate(R.layout.item_warehouse_inventory, parent, false);

            holder = new ViewHolder();
            holder.tvProductCode = convertView.findViewById(R.id.tvProductCode);
            holder.tvDescription = convertView.findViewById(R.id.tvDescription);
            holder.tvQuantity = convertView.findViewById(R.id.tvQuantity);
//            holder.tvUnitPrice = convertView.findViewById(R.id.tvUnitPrice);
//            holder.tvTotalCost = convertView.findViewById(R.id.tvTotalCost);

            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        WarehouseInventoryItem item = getItem(position);
        if (item == null) {
            return convertView;
        }

        holder.tvProductCode.setText(safeText(item.getProductCode()));
        holder.tvDescription.setText(cleanItemName(item.getDescription()));
        holder.tvQuantity.setText(qtyFmt.format(item.getQuantity()));
//        holder.tvUnitPrice.setText("₱" + costFmt.format(item.getUnitPrice()));
//        holder.tvTotalCost.setText("₱" + costFmt.format(item.getTotalCost()));

        convertView.setBackgroundColor(
                position % 2 == 0 ? Color.WHITE : Color.parseColor("#F5F5F5")
        );

        return convertView;
    }

    private String safeText(String value) {
        return value == null ? "" : value.trim();
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
        TextView tvQuantity;
//        TextView tvUnitPrice;
//        TextView tvTotalCost;
    }
}