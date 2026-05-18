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

import com.nssi.salangikopu.Model.StoreInventoryItem;
import com.nssi.salangikopu.R;

import java.text.DecimalFormat;
import java.util.List;

public class StoreInventoryAdapter extends ArrayAdapter<StoreInventoryItem> {

    private final DecimalFormat qtyFmt = new DecimalFormat("#,##0.####");
    private final DecimalFormat costFmt = new DecimalFormat("#,##0.00");

    public StoreInventoryAdapter(Context context, List<StoreInventoryItem> items) {
        super(context, 0, items);
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        ViewHolder holder;

        if (convertView == null) {
            convertView = LayoutInflater.from(getContext())
                    .inflate(R.layout.item_store_inventory, parent, false);

            holder = new ViewHolder();
            holder.tvProductCode = convertView.findViewById(R.id.tvProductCode);
            holder.tvDescription = convertView.findViewById(R.id.tvDescription);
            holder.tvQuantity = convertView.findViewById(R.id.tvQuantity);


            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        StoreInventoryItem item = getItem(position);
        if (item == null) {
            return convertView;
        }

        holder.tvProductCode.setText(safeText(item.getProductCode()));
        holder.tvDescription.setText(cleanItemName(item.getDescription()));
        holder.tvQuantity.setText(qtyFmt.format(item.getQuantityInStock()));


        String warehouseName = safeText(item.getWarehouseName());
        String transferNo = safeText(item.getTransferNo());


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
                .replace("\uFE0F", "")   // variation selector (the invisible ️ char)
                .replace("–", "-")
                .replace("—", "-")
                .replaceAll("\\s+", " ")
                .trim();

        // Cut off everything after a tagline delimiter.
        // Pipe is the most common in your data (e.g. "Product | Marketing tagline").
        text = text.replaceFirst("\\s*\\|.*$", "").trim();

        // Colon-based taglines: "Product: best snack ever"
        text = text.replaceFirst("\\s*:\\s+.*$", "").trim();

        // Bullet / middot separators
        text = text.replaceFirst("\\s*[•·●].*$", "").trim();

        // Tilde separators sometimes used in PH e-commerce listings
        text = text.replaceFirst("\\s*~\\s+.*$", "").trim();

        // Dash-based tagline (e.g. "Brand - The original since 1995")
        text = text.replaceFirst("\\s*-\\s+.*$", "").trim();

        // Exclamation tagline
        text = text.replaceFirst("\\s+!\\s*.*$", "").trim();

        // Marketing-phrase tails
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

    }
}