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

import com.nssi.salangikopu.Model.RefundDecisionItem;
import com.nssi.salangikopu.R;

import java.text.DecimalFormat;
import java.util.List;

public class RefundDecisionAdapter extends ArrayAdapter<RefundDecisionItem> {

    private final DecimalFormat peso = new DecimalFormat("#,##0.00");
    private final OnDecisionClickListener listener;

    public interface OnDecisionClickListener {
        void onReturnToStore(RefundDecisionItem item);
        void onDispose(RefundDecisionItem item);
    }

    public RefundDecisionAdapter(Context context,
                                 List<RefundDecisionItem> items,
                                 OnDecisionClickListener listener) {
        super(context, 0, items);
        this.listener = listener;
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        ViewHolder h;

        if (convertView == null) {
            convertView = LayoutInflater.from(getContext())
                    .inflate(R.layout.item_refund_decision, parent, false);

            h = new ViewHolder();
            h.tvItemName = convertView.findViewById(R.id.tvItemName);
            h.tvInfo = convertView.findViewById(R.id.tvInfo);
            h.tvReason = convertView.findViewById(R.id.tvReason);
            h.tvQty = convertView.findViewById(R.id.tvQty);
            h.tvAmount = convertView.findViewById(R.id.tvAmount);
            h.btnReturnToStore = convertView.findViewById(R.id.btnReturnToStore);
            h.btnDispose = convertView.findViewById(R.id.btnDispose);

            convertView.setTag(h);
        } else {
            h = (ViewHolder) convertView.getTag();
        }

        RefundDecisionItem item = getItem(position);
        if (item == null) return convertView;

        h.tvItemName.setText(cleanItemName(item.getItemName()));
        h.tvInfo.setText("TRX: " + item.getTransactionNo()
                + " | Store: " + item.getStoreName()
                + " | Cashier: " + item.getOriginalCashier());
        h.tvReason.setText("Reason: " + item.getRefundReason()
                + " | Processed by: " + item.getProcessedBy());

        h.tvQty.setText("Qty: " + item.getQuantity());
        h.tvAmount.setText("₱" + peso.format(item.getSubtotal()));

        h.btnReturnToStore.setOnClickListener(v -> {
            if (listener != null) listener.onReturnToStore(item);
        });

        h.btnDispose.setOnClickListener(v -> {
            if (listener != null) listener.onDispose(item);
        });

        return convertView;
    }

    private static class ViewHolder {
        TextView tvItemName, tvInfo, tvReason, tvQty, tvAmount;
        Button btnReturnToStore, btnDispose;
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

        text = text.replaceFirst("(?i)\\s+!\\s*.*$", "").trim();
        text = text.replaceFirst("(?i)\\s+is the\\s+.*$", "").trim();
        text = text.replaceFirst("(?i)\\s+perfect for\\s+.*$", "").trim();
        text = text.replaceFirst("(?i)\\s+great for\\s+.*$", "").trim();
        text = text.replaceFirst("(?i)\\s+ideal for\\s+.*$", "").trim();
        text = text.replaceFirst("(?i)\\s+by\\s+.*$", "").trim();

        return text.replaceAll("\\s+", " ").trim();
    }
}