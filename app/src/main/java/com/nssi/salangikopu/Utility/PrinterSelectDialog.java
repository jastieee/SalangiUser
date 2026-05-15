package com.nssi.salangikopu.Utility;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.nssi.salangikopu.R;

import java.util.ArrayList;
import java.util.List;

public class PrinterSelectDialog {

    public interface OnPrinterSelectedListener {
        void onSelected(BluetoothDevice device);
        void onCancelled();
    }

    @SuppressLint("MissingPermission")
    public static void show(Context context,
                            OnPrinterSelectedListener listener) {
        List<BluetoothDevice> printers =
                BluetoothPrinterManager.getPairedBPPrinters();

        View view = LayoutInflater.from(context)
                .inflate(R.layout.activity_select_printer, null);

        ListView listPrinters     = view.findViewById(R.id.listPrinters);
        TextView tvNoPrinters     = view.findViewById(R.id.tvNoPrinters);
        TextView tvCurrentPrinter = view.findViewById(R.id.tvCurrentPrinter);

        String currentName =
                BluetoothPrinterManager.getDefaultPrinterName(context);
        tvCurrentPrinter.setText("Current: " +
                (currentName != null ? currentName : "None"));

        AlertDialog.Builder builder = new AlertDialog.Builder(context)
                .setView(view)
                .setNegativeButton("Cancel", (d, w) -> listener.onCancelled());

        AlertDialog dialog = builder.create();

        if (printers.isEmpty()) {
            listPrinters.setVisibility(View.GONE);
            tvNoPrinters.setVisibility(View.VISIBLE);
        } else {
            List<String> names = new ArrayList<>();
            for (BluetoothDevice d : printers) {
                try {
                    // getName() and getAddress() require BLUETOOTH_CONNECT on API 31+
                    String name    = d.getName();
                    String address = d.getAddress();
                    names.add((name != null ? name : "Unknown") + "\n" + address);
                } catch (SecurityException e) {
                    names.add("Unknown Device");
                }
            }

            ArrayAdapter<String> adapter = new ArrayAdapter<>(
                    context,
                    android.R.layout.simple_list_item_1,
                    names
            );
            listPrinters.setAdapter(adapter);

            listPrinters.setOnItemClickListener((parent, v, pos, id) -> {
                BluetoothDevice selected = printers.get(pos);
                try {
                    BluetoothPrinterManager.saveDefaultPrinter(
                            context,
                            selected.getAddress(),
                            selected.getName() != null ? selected.getName() : "BP Printer"
                    );
                    dialog.dismiss();
                    listener.onSelected(selected);
                } catch (SecurityException e) {
                    e.printStackTrace();
                    dialog.dismiss();
                    listener.onCancelled();
                }
            });
        }

        dialog.show();
    }
}