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

import com.nssi.salangikopu.Model.DeliveryItem;
import com.nssi.salangikopu.R;

import java.text.DecimalFormat;
import java.util.List;

public class DeliveryItemAdapter extends ArrayAdapter<DeliveryItem> {

    private final DecimalFormat qtyFmt = new DecimalFormat("#,##0.##");
    private final DecimalFormat moneyFmt = new DecimalFormat("#,##0.00");
    private final OnItemRemovedListener listener;

    public interface OnItemRemovedListener {
        void onItemRemoved();
    }

    public DeliveryItemAdapter(Context context, List<DeliveryItem> items,
                               OnItemRemovedListener listener) {
        super(context, 0, items);
        this.listener = listener;
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        ViewHolder holder;

        if (convertView == null) {
            convertView = LayoutInflater.from(getContext())
                    .inflate(R.layout.item_delivery_in, parent, false);

            holder = new ViewHolder();
            holder.tvProductCode = convertView.findViewById(R.id.tvProductCode);
            holder.tvDescription = convertView.findViewById(R.id.tvDescription);
            holder.tvQty = convertView.findViewById(R.id.tvQty);
            holder.tvTotalCost = convertView.findViewById(R.id.tvTotalCost);
            holder.tvUnitCost = convertView.findViewById(R.id.tvUnitCost);
            holder.btnRemove = convertView.findViewById(R.id.btnRemove);

            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        DeliveryItem item = getItem(position);
        if (item == null) return convertView;

        holder.tvProductCode.setText(item.getProductCode());
        holder.tvDescription.setText(item.getDescription());

        holder.tvQty.setText(
                "Qty: " + qtyFmt.format(item.getOriginalQty()) + " " + item.getUnitName()
                        + " = " + qtyFmt.format(item.getQuantity()) + " PCS"
        );

        holder.tvTotalCost.setText("Total: ₱" + moneyFmt.format(item.getTotalCost()));
        holder.tvUnitCost.setText("Cost/Unit: ₱" + moneyFmt.format(item.getComputedUnitCost()));

        holder.btnRemove.setOnClickListener(v -> {
            remove(item);
            notifyDataSetChanged();
            if (listener != null) listener.onItemRemoved();
        });

        return convertView;
    }

    private static class ViewHolder {
        TextView tvProductCode, tvDescription, tvQty, tvTotalCost, tvUnitCost;
        Button btnRemove;
    }
}