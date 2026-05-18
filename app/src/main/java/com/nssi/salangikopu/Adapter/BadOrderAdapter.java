package com.nssi.salangikopu.Adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.nssi.salangikopu.Model.BadOrderItem;
import com.nssi.salangikopu.R;

import java.text.DecimalFormat;
import java.util.List;

public class BadOrderAdapter extends ArrayAdapter<BadOrderItem> {

    private final DecimalFormat qtyFmt = new DecimalFormat("#,##0.##");
    private final DecimalFormat pesoFmt = new DecimalFormat("#,##0.00");
    private final OnItemRemovedListener listener;

    public interface OnItemRemovedListener {
        void onItemRemoved();
    }

    public BadOrderAdapter(Context context, List<BadOrderItem> items,
                           OnItemRemovedListener listener) {
        super(context, 0, items);
        this.listener = listener;
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView,
                        @NonNull ViewGroup parent) {
        ViewHolder h;

        if (convertView == null) {
            convertView = LayoutInflater.from(getContext())
                    .inflate(R.layout.item_bad_order, parent, false);

            h = new ViewHolder();
            h.tvProductCode = convertView.findViewById(R.id.tvProductCode);
            h.tvDescription = convertView.findViewById(R.id.tvDescription);
            h.tvQty = convertView.findViewById(R.id.tvQty);
            h.tvReason = convertView.findViewById(R.id.tvReason);
            h.tvSubtotal = convertView.findViewById(R.id.tvSubtotal);
            h.btnRemove = convertView.findViewById(R.id.btnRemove);

            convertView.setTag(h);
        } else {
            h = (ViewHolder) convertView.getTag();
        }

        BadOrderItem item = getItem(position);
        if (item == null) return convertView;

        h.tvProductCode.setText(item.getProductCode());
        h.tvDescription.setText(cleanItemName(item.getDescription()));
        h.tvQty.setText("Qty: " + qtyFmt.format(item.getQuantity()));
        h.tvReason.setText("Reason: " + safeText(item.getRemarks()));
        h.tvSubtotal.setText("₱" + pesoFmt.format(item.getSubtotal()));

        h.btnRemove.setOnClickListener(v -> {
            remove(item);
            notifyDataSetChanged();
            if (listener != null) listener.onItemRemoved();
        });

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
        TextView tvProductCode, tvDescription, tvQty, tvReason, tvSubtotal;
        Button btnRemove;
    }
}