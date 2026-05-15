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

import com.nssi.salangikopu.Model.TransferConfirmationItem;
import com.nssi.salangikopu.R;

import java.util.List;

public class TransferConfirmationAdapter extends ArrayAdapter<TransferConfirmationItem> {

    public interface OnConfirmClickListener {
        void onConfirm(TransferConfirmationItem item);
    }

    private final OnConfirmClickListener listener;

    public TransferConfirmationAdapter(Context context,
                                       List<TransferConfirmationItem> items,
                                       OnConfirmClickListener listener) {
        super(context, 0, items);
        this.listener = listener;
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        ViewHolder holder;

        if (convertView == null) {
            convertView = LayoutInflater.from(getContext())
                    .inflate(R.layout.item_transfer_confirmation, parent, false);

            holder = new ViewHolder();
            holder.tvTransferNo = convertView.findViewById(R.id.tvTransferNo);
            holder.tvWarehouse = convertView.findViewById(R.id.tvWarehouse);
            holder.tvStore = convertView.findViewById(R.id.tvStore);
            holder.tvDate = convertView.findViewById(R.id.tvDate);
            holder.tvItemCount = convertView.findViewById(R.id.tvItemCount);
            holder.tvStatus = convertView.findViewById(R.id.tvStatus);
            holder.btnConfirm = convertView.findViewById(R.id.btnConfirm);

            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        TransferConfirmationItem item = getItem(position);
        if (item == null) {
            return convertView;
        }

        String transferNo = safeText(item.getTransferNo());
        String warehouseName = safeText(item.getWarehouseName());
        String storeName = safeText(item.getStoreName());
        String transferDate = safeText(item.getTransferDate());
        String status = safeText(item.getStatus());

        holder.tvTransferNo.setText(transferNo.isEmpty() ? "—" : transferNo);
        holder.tvWarehouse.setText(warehouseName.isEmpty() ? "—" : warehouseName);
        holder.tvStore.setText(storeName.isEmpty() ? "—" : storeName);
        holder.tvDate.setText(transferDate.isEmpty() ? "—" : transferDate);
        holder.tvItemCount.setText(String.valueOf(item.getItemCount()));
        holder.tvStatus.setText(status.isEmpty() ? "—" : status);

        switch (status) {
            case "PENDING":
                holder.tvStatus.setTextColor(Color.parseColor("#FFA000"));
                break;
            case "TRANSFER COMPLETE":
                holder.tvStatus.setTextColor(Color.parseColor("#2E7D32"));
                break;
            default:
                holder.tvStatus.setTextColor(Color.parseColor("#555555"));
                break;
        }

        holder.btnConfirm.setOnClickListener(null);

        if ("PENDING".equals(status)) {
            holder.btnConfirm.setVisibility(View.VISIBLE);
            holder.btnConfirm.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onConfirm(item);
                }
            });
        } else {
            holder.btnConfirm.setVisibility(View.GONE);
        }

        convertView.setBackgroundColor(
                position % 2 == 0 ? Color.WHITE : Color.parseColor("#F5F5F5")
        );

        return convertView;
    }

    private String safeText(String value) {
        return value == null ? "" : value.trim();
    }

    private static class ViewHolder {
        TextView tvTransferNo;
        TextView tvWarehouse;
        TextView tvStore;
        TextView tvDate;
        TextView tvItemCount;
        TextView tvStatus;
        Button btnConfirm;
    }
}