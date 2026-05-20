package com.nssi.salangikopu.Utility;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public class BluetoothPrinterManager {

    private static final String TAG             = "BTPrinter";
    private static final String PREF_NAME       = "threeetoys_prefs";
    private static final String PREF_BT_ADDRESS = "bt_printer_address";
    private static final String PREF_BT_NAME    = "bt_printer_name";

    private static final String TIKTOK_URL =
            "https://www.tiktok.com/@threeestoysandcandystore?_r=1&_t=ZS-951afOU22fE";

    private static final String FACEBOOK_URL =
            "https://www.facebook.com/share/14cDAAu4nY3/";

    private static final String CONTACT_URL =
            "https://www.3ecandy.com/contact/";

    private static final UUID SPP_UUID =
            UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private static final String TYPE_ESC = "ESC";
    private static final String TYPE_ZPL = "ZPL";

    private static final byte[] ESC_INIT       = {0x1B, 0x40};
    private static final byte[] ALIGN_LEFT     = {0x1B, 0x61, 0x00};
    private static final byte[] ALIGN_CENTER   = {0x1B, 0x61, 0x01};
    private static final byte[] ALIGN_RIGHT    = {0x1B, 0x61, 0x02};
    private static final byte[] BOLD_ON        = {0x1B, 0x45, 0x01};
    private static final byte[] BOLD_OFF       = {0x1B, 0x45, 0x00};
    private static final byte[] FONT_NORMAL    = {0x1B, 0x21, 0x00};
    // Condensed font: smaller width (font B)
    private static final byte[] FONT_SMALL     = {0x1B, 0x4D, 0x01};  // ESC M 1 = Font B (smaller)
    private static final byte[] FONT_STANDARD  = {0x1B, 0x4D, 0x00};  // ESC M 0 = Font A (normal)
    private static final byte[] FONT_DOUBLE_HT = {0x1B, 0x21, 0x10};
    private static final byte[] FONT_DOUBLE_WH = {0x1B, 0x21, 0x30};
    private static final byte[] LINE_FEED      = {0x0A};
    private static final byte[] CUT            = {0x1D, 0x56, 0x41, 0x03};

    private static final byte[] STATUS_PAPER   = {0x10, 0x04, 0x04};

    private static final int BIT_PAPER_NEAR_END = 0x04;
    private static final int BIT_PAPER_OUT      = 0x20;

    public enum PaperStatus {
        OK,
        NEAR_END,
        OUT,
        UNKNOWN
    }

    @SuppressLint("MissingPermission")
    public static List<BluetoothDevice> getPairedBPPrinters() {
        List<BluetoothDevice> list = new ArrayList<>();
        try {
            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            if (adapter == null || !adapter.isEnabled()) return list;

            Set<BluetoothDevice> paired = adapter.getBondedDevices();
            for (BluetoothDevice d : paired) {
                String name = d.getName();
                if (name != null) {
                    String upper = name.toUpperCase();
                    if (upper.contains("BP") || upper.contains("ZQ") || upper.contains("MHT") || upper.contains("TP")) {
                        list.add(d);
                    }
                }
            }
        } catch (SecurityException e) {
            Log.e(TAG, "getPairedBPPrinters: " + e.getMessage());
        }
        return list;
    }

    private static String detectPrinterType(Context ctx) {
        String name = getDefaultPrinterName(ctx);
        if (name != null && name.toUpperCase().contains("ZQ")) {
            return TYPE_ZPL;
        }
        return TYPE_ESC;
    }

    public static void saveDefaultPrinter(Context ctx, String address, String name) {
        ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
                .putString(PREF_BT_ADDRESS, address)
                .putString(PREF_BT_NAME, name)
                .apply();
    }

    public static String getDefaultPrinterAddress(Context ctx) {
        return ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .getString(PREF_BT_ADDRESS, null);
    }

    public static String getDefaultPrinterName(Context ctx) {
        return ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .getString(PREF_BT_NAME, null);
    }

    public static boolean hasDefaultPrinter(Context ctx) {
        return getDefaultPrinterAddress(ctx) != null;
    }

    @SuppressLint("MissingPermission")
    public static void checkPaperStatus(Context ctx, PaperStatusCallback callback) {
        String address = getDefaultPrinterAddress(ctx);
        if (address == null) {
            callback.onResult(PaperStatus.UNKNOWN);
            return;
        }

        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null || !adapter.isEnabled()) {
            callback.onResult(PaperStatus.UNKNOWN);
            return;
        }

        new Thread(() -> {
            BluetoothSocket socket = null;
            try {
                BluetoothDevice device = adapter.getRemoteDevice(address);
                try { adapter.cancelDiscovery(); } catch (Exception ignored) {}

                socket = device.createRfcommSocketToServiceRecord(SPP_UUID);
                socket.connect();
                Thread.sleep(500);

                OutputStream out = socket.getOutputStream();
                InputStream in = socket.getInputStream();

                while (in.available() > 0) in.read();

                out.write(STATUS_PAPER);
                out.flush();

                long deadline = System.currentTimeMillis() + 1500;
                while (in.available() == 0 && System.currentTimeMillis() < deadline) {
                    Thread.sleep(50);
                }

                if (in.available() == 0) {
                    Log.w(TAG, "Paper status: no response from printer");
                    closeSocket(socket);
                    callback.onResult(PaperStatus.UNKNOWN);
                    return;
                }

                int statusByte = in.read();
                Log.d(TAG, "Paper status byte: 0x" + Integer.toHexString(statusByte));

                closeSocket(socket);

                if ((statusByte & BIT_PAPER_OUT) != 0) {
                    callback.onResult(PaperStatus.OUT);
                } else if ((statusByte & BIT_PAPER_NEAR_END) != 0) {
                    callback.onResult(PaperStatus.NEAR_END);
                } else {
                    callback.onResult(PaperStatus.OK);
                }

            } catch (SecurityException e) {
                Log.e(TAG, "checkPaperStatus security: " + e.getMessage());
                closeSocket(socket);
                callback.onResult(PaperStatus.UNKNOWN);
            } catch (Exception e) {
                Log.e(TAG, "checkPaperStatus error: " + e.getMessage());
                closeSocket(socket);
                callback.onResult(PaperStatus.UNKNOWN);
            }
        }).start();
    }

    @SuppressLint("MissingPermission")
    public static void printReceipt(Context ctx, ReceiptData data, PrintCallback callback) {
        String address = getDefaultPrinterAddress(ctx);
        if (address == null) {
            callback.onError("No printer selected.");
            return;
        }

        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null) {
            callback.onError("Bluetooth not supported.");
            return;
        }
        if (!adapter.isEnabled()) {
            callback.onError("Bluetooth is off.");
            return;
        }

        String printerType = detectPrinterType(ctx);
        Log.d(TAG, "Printer type detected: " + printerType);

        new Thread(() -> {
            BluetoothSocket socket = null;
            try {
                BluetoothDevice device = adapter.getRemoteDevice(address);
                try { adapter.cancelDiscovery(); } catch (Exception ignored) {}

                socket = device.createRfcommSocketToServiceRecord(SPP_UUID);
                Log.d(TAG, "Connecting to: " + address);
                socket.connect();
                Log.d(TAG, "Connected!");

                Thread.sleep(500);

                OutputStream out = socket.getOutputStream();
                InputStream in = socket.getInputStream();

                if (printerType.equals(TYPE_ESC)) {
                    PaperStatus paperStatus = readPaperStatus(in, out);
                    Log.d(TAG, "Pre-print paper check: " + paperStatus);

                    if (paperStatus == PaperStatus.OUT) {
                        closeSocket(socket);
                        callback.onPaperError(PaperStatus.OUT,
                                "No paper in printer. Please load paper and try again.");
                        return;
                    }

                    if (paperStatus == PaperStatus.NEAR_END) {
                        closeSocket(socket);
                        callback.onPaperError(PaperStatus.NEAR_END,
                                "Paper is running low. Please replace paper roll before printing.");
                        return;
                    }
                }

                byte[] bytes = printerType.equals(TYPE_ZPL)
                        ? buildZplReceipt(data)
                        : buildEscReceipt(data);

                Log.d(TAG, "Sending " + bytes.length + " bytes [" + printerType + "]...");

                int offset = 0;
                while (offset < bytes.length) {
                    int end = Math.min(offset + 1024, bytes.length);
                    out.write(bytes, offset, end - offset);
                    out.flush();
                    Thread.sleep(30);
                    offset = end;
                }

                Log.d(TAG, "All sent. Waiting for printer...");
                Thread.sleep(3000);
                socket.close();
                Log.d(TAG, "Print complete.");
                callback.onSuccess();

            } catch (SecurityException e) {
                Log.e(TAG, "Security: " + e.getMessage());
                closeSocket(socket);
                callback.onError("Permission denied: " + e.getMessage());
            } catch (Exception e) {
                Log.e(TAG, "Error: " + e.getMessage());
                closeSocket(socket);
                callback.onError(e.getMessage());
            }
        }).start();
    }

    private static PaperStatus readPaperStatus(InputStream in, OutputStream out) {
        try {
            while (in.available() > 0) in.read();

            out.write(STATUS_PAPER);
            out.flush();

            long deadline = System.currentTimeMillis() + 1500;
            while (in.available() == 0 && System.currentTimeMillis() < deadline) {
                Thread.sleep(50);
            }

            if (in.available() == 0) return PaperStatus.UNKNOWN;

            int statusByte = in.read();
            Log.d(TAG, "Paper status byte: 0x" + Integer.toHexString(statusByte));

            if ((statusByte & BIT_PAPER_OUT) != 0) return PaperStatus.OUT;
            if ((statusByte & BIT_PAPER_NEAR_END) != 0) return PaperStatus.NEAR_END;
            return PaperStatus.OK;

        } catch (Exception e) {
            Log.e(TAG, "readPaperStatus: " + e.getMessage());
            return PaperStatus.UNKNOWN;
        }
    }

    private static void closeSocket(BluetoothSocket s) {
        try {
            if (s != null) s.close();
        } catch (Exception ignored) {}
    }

    private static boolean[][] encodeQr(String text) throws Exception {
        com.google.zxing.qrcode.QRCodeWriter writer =
                new com.google.zxing.qrcode.QRCodeWriter();
        java.util.Map<com.google.zxing.EncodeHintType, Object> hints =
                new java.util.EnumMap<>(com.google.zxing.EncodeHintType.class);
        hints.put(com.google.zxing.EncodeHintType.ERROR_CORRECTION,
                com.google.zxing.qrcode.decoder.ErrorCorrectionLevel.M);
        hints.put(com.google.zxing.EncodeHintType.MARGIN, 1);

        com.google.zxing.common.BitMatrix matrix = writer.encode(
                text, com.google.zxing.BarcodeFormat.QR_CODE, 0, 0, hints);

        int size = matrix.getWidth();
        boolean[][] bits = new boolean[size][size];
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                bits[y][x] = matrix.get(x, y);
            }
        }
        return bits;
    }

    private static void writeSideBySideQrCodesCentered(ByteArrayOutputStream b,
                                                       String leftUrl, String leftLabel,
                                                       String rightUrl, String rightLabel) throws Exception {

        final int PAPER_DOTS = 384;
        final int GAP = 50;
        final int TARGET_SIZE = 120;

        boolean[][] leftBits = encodeQr(leftUrl);
        boolean[][] rightBits = encodeQr(rightUrl);

        int leftMod = leftBits.length;
        int rightMod = rightBits.length;

        int leftScale = Math.max(1, TARGET_SIZE / leftMod);
        int rightScale = Math.max(1, TARGET_SIZE / rightMod);

        int leftSize = leftMod * leftScale;
        int rightSize = rightMod * rightScale;

        int qrHeight = Math.max(leftSize, rightSize);
        int groupWidth = leftSize + GAP + rightSize;
        int leftPadding = Math.max(0, (PAPER_DOTS - groupWidth) / 2);

        int canvasW = PAPER_DOTS;
        int canvasWPad = ((canvasW + 7) / 8) * 8;
        int bytesPerRow = canvasWPad / 8;

        byte[] raster = new byte[qrHeight * bytesPerRow];

        int leftStartX = leftPadding;
        int leftOffsetY = (qrHeight - leftSize) / 2;

        for (int y = 0; y < leftMod; y++) {
            for (int x = 0; x < leftMod; x++) {
                if (!leftBits[y][x]) continue;
                for (int sy = 0; sy < leftScale; sy++) {
                    for (int sx = 0; sx < leftScale; sx++) {
                        int px = leftStartX + (x * leftScale) + sx;
                        int py = leftOffsetY + (y * leftScale) + sy;
                        if (px < canvasWPad && py < qrHeight) {
                            raster[py * bytesPerRow + px / 8] |= (byte) (0x80 >> (px % 8));
                        }
                    }
                }
            }
        }

        int rightStartX = leftStartX + leftSize + GAP;
        int rightOffsetY = (qrHeight - rightSize) / 2;

        for (int y = 0; y < rightMod; y++) {
            for (int x = 0; x < rightMod; x++) {
                if (!rightBits[y][x]) continue;
                for (int sy = 0; sy < rightScale; sy++) {
                    for (int sx = 0; sx < rightScale; sx++) {
                        int px = rightStartX + (x * rightScale) + sx;
                        int py = rightOffsetY + (y * rightScale) + sy;
                        if (px < canvasWPad && py < qrHeight) {
                            raster[py * bytesPerRow + px / 8] |= (byte) (0x80 >> (px % 8));
                        }
                    }
                }
            }
        }

        b.write(ALIGN_CENTER);
        writeLine(b, centerText(leftLabel + "      " + rightLabel, 32));

        b.write(new byte[]{
                0x1D, 0x76, 0x30, 0x00,
                (byte) (bytesPerRow & 0xFF), (byte) ((bytesPerRow >> 8) & 0xFF),
                (byte) (qrHeight & 0xFF), (byte) ((qrHeight >> 8) & 0xFF)
        });
        b.write(raster);
        b.write(LINE_FEED);
    }

    private static void writeSingleQrCode(ByteArrayOutputStream b, String text) throws Exception {
        final int PAPER_DOTS = 384;
        final int TARGET_SIZE = 170;

        boolean[][] bits = encodeQr(text);

        int mod = bits.length;
        int scale = Math.max(1, TARGET_SIZE / mod);
        int size = mod * scale;
        int leftPadding = Math.max(0, (PAPER_DOTS - size) / 2);

        int canvasW = PAPER_DOTS;
        int canvasWPad = ((canvasW + 7) / 8) * 8;
        int bytesPerRow = canvasWPad / 8;

        byte[] raster = new byte[size * bytesPerRow];

        for (int y = 0; y < mod; y++) {
            for (int x = 0; x < mod; x++) {
                if (!bits[y][x]) continue;

                for (int sy = 0; sy < scale; sy++) {
                    for (int sx = 0; sx < scale; sx++) {
                        int px = leftPadding + (x * scale) + sx;
                        int py = (y * scale) + sy;

                        if (px < canvasWPad && py < size) {
                            raster[py * bytesPerRow + px / 8] |= (byte) (0x80 >> (px % 8));
                        }
                    }
                }
            }
        }

        b.write(new byte[]{
                0x1D, 0x76, 0x30, 0x00,
                (byte) (bytesPerRow & 0xFF), (byte) ((bytesPerRow >> 8) & 0xFF),
                (byte) (size & 0xFF), (byte) ((size >> 8) & 0xFF)
        });

        b.write(raster);
        b.write(LINE_FEED);
    }

    private static String formatReceiptItemLine(int qty, String unitPrice, String subtotal, int width) {
        String left = qty + " x " + unitPrice;
        int spaces = width - left.length() - subtotal.length();
        if (spaces < 1) spaces = 1;

        StringBuilder sb = new StringBuilder();
        sb.append(left);
        for (int i = 0; i < spaces; i++) sb.append(' ');
        sb.append(subtotal);
        return sb.toString();
    }

    private static String formatTotalAmountLine(String label, String value, int width) {
        int valueWidth = 10;
        int spaces = width - label.length() - valueWidth;
        if (spaces < 1) spaces = 1;

        StringBuilder sb = new StringBuilder();
        sb.append(label);
        for (int i = 0; i < spaces; i++) sb.append(' ');
        sb.append(padLeft(value, valueWidth));

        return sb.toString();
    }

    private static byte[] buildEscReceipt(ReceiptData data) throws Exception {

        ByteArrayOutputStream b = new ByteArrayOutputStream();
        DecimalFormat df = new DecimalFormat("#,##0.00");

        final int WIDTH = 48;

        b.write(ESC_INIT);
        b.write(FONT_NORMAL);

        // =========================
        // LOGO
        // =========================
        b.write(ALIGN_CENTER);
        b.write(LogoEscPos.getHeader());
        b.write(LogoEscPos.getData());

        // =========================
        // HEADER
        // =========================
        b.write(ALIGN_CENTER);

        b.write(BOLD_ON);
        writeLine(b, "THREE E'S TOYS &");
        writeLine(b, "CANDY STORE");
        b.write(BOLD_OFF);

        writeLine(b, "\"All Your Daily Needs, All in One Place.\"");

        // no extra feed — go straight to date/cashier/trx
        writeLine(b, "Date: " + data.dateTime);
        writeLine(b, "Cashier: " + data.cashierName);
        writeLine(b, "TRX#: " + data.transactionNo);

        // =========================
        // DIVIDER
        // =========================
        b.write(ALIGN_LEFT);
        writeLine(b, repeat("-", WIDTH));

        // =========================
        // ITEMS
        // =========================
        for (ReceiptData.ReceiptItem item : data.items) {

            String name = toProperCase(
                    cleanItemName(item.description)
            ).trim();

            // wrap to full WIDTH — no more cut-off at 28
            List<String> nameLines = wrapText(name, WIDTH);
            for (String line : nameLines) {
                writeLine(b, line);
            }

            // qty x price on left, subtotal flush right, spans full WIDTH
            writeLine(
                    b,
                    formatReceiptItemLine(
                            (int) item.quantity,
                            df.format(item.unitPrice),
                            df.format(item.subtotal),
                            WIDTH
                    )
            );
            // no extra feed between items
        }

        // =========================
        // DIVIDER
        // =========================
        writeLine(b, repeat("-", WIDTH));

        // =========================
        // TOTAL
        // =========================
        b.write(BOLD_ON);
        writeLine(
                b,
                formatTotalAmountLine(
                        "TOTAL AMOUNT:",
                        df.format(data.totalAmount),
                        WIDTH
                )
        );
        b.write(BOLD_OFF);

        // =========================
        // DIVIDER
        // =========================
        writeLine(b, repeat("-", WIDTH));

        // =========================
        // FOOTER
        // =========================
        b.write(ALIGN_CENTER);
        writeLine(b, "Thank you for supporting our local jobs!");
        writeLine(b, "See you again!");

        writeLine(b, "NOTE");
        writeLine(b, "This is not an official receipt");
        writeLine(b, "and not a valid source for");
        writeLine(b, "claiming input VAT.");
        writeLine(b, "Please request the official");
        writeLine(b, "invoice from the merchant.");

        writeLine(b, "FOLLOW US ON SOCIAL MEDIA");

        // =========================
        // QR CODES
        // =========================
        writeSideBySideQrCodesCentered(
                b,
                TIKTOK_URL, "TikTok",
                FACEBOOK_URL, "Facebook"
        );

        // =========================
        // CONTACT LINE (below QR codes)
        // =========================
        b.write(ALIGN_CENTER);
        writeLine(b, "Need assistance or have a question?");
        writeLine(b, "Visit " + CONTACT_URL);

        // =========================
        // CUT
        // =========================
        b.write(CUT);

        return b.toByteArray();
    }
    private static String formatCenteredItemLine(int qty, String unitPrice, String subtotal, int contentWidth) {
        String left = qty + " x " + unitPrice;
        int subtotalWidth = 8;
        int gap = 2;
        int leftWidth = contentWidth - subtotalWidth - gap;

        if (leftWidth < 1) leftWidth = 1;

        if (left.length() > leftWidth) {
            left = left.substring(0, leftWidth);
        }

        return padRight(left, leftWidth) + spaces(gap) + padLeft(subtotal, subtotalWidth);
    }

    private static String formatTotalLine(String label, String value, int contentWidth) {
        int valueWidth = 8;
        int gap = 2;
        int labelWidth = contentWidth - valueWidth - gap;

        if (labelWidth < 1) labelWidth = 1;

        if (label.length() > labelWidth) {
            label = label.substring(0, labelWidth);
        }

        return padRight(label, labelWidth) + spaces(gap) + padLeft(value, valueWidth);
    }

    private static byte[] buildZplReceipt(ReceiptData data) throws Exception {
        DecimalFormat df = new DecimalFormat("#,##0.00");

        final int PW  = 609;
        final int LM  = 10;
        final int RM  = 599;
        final int UW  = 589;

        StringBuilder zpl = new StringBuilder();

        zpl.append("^XA\n");
        zpl.append("^PW").append(PW).append("\n");
        zpl.append("^LL1200\n");
        zpl.append("^PON\n");
        zpl.append("^MNN\n");

        int y = 25;

        String storeName = data.storeName.toUpperCase();

        int snFontW, snFontH;
        if (storeName.length() <= 14) {
            snFontW = 36;
            snFontH = 48;
        } else if (storeName.length() <= 18) {
            snFontW = 28;
            snFontH = 38;
        } else {
            snFontW = Math.max(20, UW / storeName.length());
            snFontH = (int) (snFontW * 1.33);
        }

        zpl.append("^FO").append(LM).append(",").append(y)
                .append("^A0N,").append(snFontH).append(",").append(snFontW)
                .append("^FB").append(UW).append(",1,0,C,0")
                .append("^FD").append(storeName).append("^FS\n");
        y += snFontH + 18;

        zpl.append("^FO").append(LM).append(",").append(y)
                .append("^GB").append(UW).append(",3,3^FS\n");
        y += 12;

        final int infoFH = 26;
        final int infoFW = 20;

        String trxLine = "TRX#: " + data.transactionNo;
        String dateLine = data.dateTime;
        String cashierLine = "Cashier: " + data.cashierName;

        zpl.append("^FO").append(LM).append(",").append(y)
                .append("^A0N,").append(infoFH).append(",").append(infoFW)
                .append("^FB").append(UW).append(",1,0,C,0")
                .append("^FD").append(trxLine).append("^FS\n");
        y += infoFH + 8;

        zpl.append("^FO").append(LM).append(",").append(y)
                .append("^A0N,").append(infoFH).append(",").append(infoFW)
                .append("^FB").append(UW).append(",1,0,C,0")
                .append("^FD").append(dateLine).append("^FS\n");
        y += infoFH + 8;

        zpl.append("^FO").append(LM).append(",").append(y)
                .append("^A0N,").append(infoFH).append(",").append(infoFW)
                .append("^FB").append(UW).append(",1,0,C,0")
                .append("^FD").append(cashierLine).append("^FS\n");
        y += infoFH + 15;

        final int COL_ITEM_X   = LM;
        final int COL_QTY_X    = 320;
        final int COL_QTY_END  = 449;
        final int COL_AMT_X    = 452;
        final int COL_AMT_END  = RM;

        final int colFH = 26;
        final int colFW = 20;

        zpl.append("^FO").append(COL_ITEM_X).append(",").append(y)
                .append("^A0N,").append(colFH).append(",").append(colFW)
                .append("^FDITEM^FS\n");

        String qtyHeader = "QTY";
        int qtyHdrX = COL_QTY_END - qtyHeader.length() * colFW;
        zpl.append("^FO").append(Math.max(COL_QTY_X, qtyHdrX)).append(",").append(y)
                .append("^A0N,").append(colFH).append(",").append(colFW)
                .append("^FD").append(qtyHeader).append("^FS\n");

        String amtHeader = "AMOUNT";
        int amtHdrX = COL_AMT_END - amtHeader.length() * colFW;
        zpl.append("^FO").append(Math.max(COL_AMT_X, amtHdrX)).append(",").append(y)
                .append("^A0N,").append(colFH).append(",").append(colFW)
                .append("^FD").append(amtHeader).append("^FS\n");
        y += colFH + 8;

        zpl.append("^FO").append(LM).append(",").append(y)
                .append("^GB").append(UW).append(",3,3^FS\n");
        y += 12;

        for (ReceiptData.ReceiptItem item : data.items) {
            final int itemFH = 26;
            final int itemFW = 20;
            final int itemCharEst = 14;

            String fullName = toProperCase(item.description);
            int maxItemChars = (COL_QTY_X - COL_ITEM_X - 15) / itemCharEst;

            String nameLine1;
            String nameLine2 = null;
            if (fullName.length() <= maxItemChars) {
                nameLine1 = fullName;
            } else {
                int breakAt = fullName.lastIndexOf(' ', maxItemChars);
                if (breakAt <= 0) breakAt = maxItemChars;
                nameLine1 = fullName.substring(0, breakAt).trim();
                String rest = fullName.substring(breakAt).trim();
                nameLine2 = rest.length() > maxItemChars ? rest.substring(0, maxItemChars) : rest;
            }

            String qtyStr = "x" + (int) item.quantity;
            int qtyStrX = Math.max(COL_QTY_X, COL_QTY_END - qtyStr.length() * itemFW);

            String amtStr = df.format(item.subtotal);
            int amtStrX = Math.max(COL_AMT_X, COL_AMT_END - amtStr.length() * itemFW);

            zpl.append("^FO").append(COL_ITEM_X).append(",").append(y)
                    .append("^A0N,").append(itemFH).append(",").append(itemFW)
                    .append("^FD").append(nameLine1).append("^FS\n");
            zpl.append("^FO").append(qtyStrX).append(",").append(y)
                    .append("^A0N,").append(itemFH).append(",").append(itemFW)
                    .append("^FD").append(qtyStr).append("^FS\n");
            zpl.append("^FO").append(amtStrX).append(",").append(y)
                    .append("^A0N,").append(itemFH).append(",").append(itemFW)
                    .append("^FD").append(amtStr).append("^FS\n");
            y += itemFH + 6;

            if (nameLine2 != null) {
                zpl.append("^FO").append(COL_ITEM_X).append(",").append(y)
                        .append("^A0N,").append(itemFH).append(",").append(itemFW)
                        .append("^FD").append(nameLine2).append("^FS\n");
                y += itemFH + 6;
            }

            String priceLabel = "@ P" + df.format(item.unitPrice);
            zpl.append("^FO").append(COL_ITEM_X + 15).append(",").append(y)
                    .append("^A0N,22,17^FD").append(priceLabel).append("^FS\n");
            y += 22 + 12;
        }

        zpl.append("^FO").append(LM).append(",").append(y)
                .append("^GB").append(UW).append(",3,3^FS\n");
        y += 15;

        final int totFH = 38;
        final int totFW = 28;
        String totalLabel = "TOTAL:";
        String totalAmt = "P" + df.format(data.totalAmount);

        zpl.append("^FO").append(LM).append(",").append(y)
                .append("^A0N,").append(totFH).append(",").append(totFW)
                .append("^FD").append(totalLabel).append("^FS\n");

        int totalAmtW = totalAmt.length() * totFW;
        int totalAmtX = Math.max(LM + totalLabel.length() * totFW + 10, RM - totalAmtW);
        zpl.append("^FO").append(totalAmtX).append(",").append(y)
                .append("^A0N,").append(totFH).append(",").append(totFW)
                .append("^FD").append(totalAmt).append("^FS\n");
        y += totFH + 18;

        zpl.append("^FO").append(LM).append(",").append(y)
                .append("^GB").append(UW).append(",3,3^FS\n");
        y += 15;

        final int ftFH = 26;
        final int ftFW = 20;
        String thanks = "Thank you for shopping!";
        String comeback = "Please come again.";

        zpl.append("^FO").append(LM).append(",").append(y)
                .append("^A0N,").append(ftFH).append(",").append(ftFW)
                .append("^FB").append(UW).append(",1,0,C,0")
                .append("^FD").append(thanks).append("^FS\n");
        y += ftFH + 8;

        zpl.append("^FO").append(LM).append(",").append(y)
                .append("^A0N,").append(ftFH).append(",").append(ftFW)
                .append("^FB").append(UW).append(",1,0,C,0")
                .append("^FD").append(comeback).append("^FS\n");
        y += ftFH + 15;

        zpl.append("^FO").append(LM).append(",").append(y)
                .append("^GB").append(UW).append(",3,3^FS\n");
        y += 80;

        String zplStr = zpl.toString()
                .replace("^LL1200\n", "^LL" + (y + 10) + "\n");
        zplStr += "^XZ\n";

        return zplStr.getBytes("UTF-8");
    }

    private static String cleanItemName(String value) {
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

    private static String toProperCase(String text) {
        if (text == null || text.isEmpty()) return "";

        java.util.Set<String> lowercase = new java.util.HashSet<>(java.util.Arrays.asList(
                "a", "an", "the", "and", "but", "or", "for",
                "nor", "on", "at", "to", "by", "in", "of", "up"
        ));

        String[] words = text.trim().toLowerCase().split("\\s+");
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < words.length; i++) {
            String word = words[i];
            if (word.isEmpty()) continue;

            if (i == 0 || i == words.length - 1 || !lowercase.contains(word)) {
                sb.append(Character.toUpperCase(word.charAt(0)))
                        .append(word.substring(1));
            } else {
                sb.append(word);
            }

            if (i < words.length - 1) sb.append(" ");
        }

        return sb.toString();
    }

    private static List<String> wrapText(String text, int width) {
        List<String> lines = new ArrayList<>();
        if (text == null) {
            lines.add("");
            return lines;
        }

        text = text.trim();
        while (text.length() > width) {
            int breakAt = text.lastIndexOf(' ', width);
            if (breakAt <= 0) breakAt = width;
            lines.add(text.substring(0, breakAt).trim());
            text = text.substring(breakAt).trim();
        }

        if (!text.isEmpty()) {
            lines.add(text);
        }

        return lines;
    }

    private static String centerText(String text, int width) {
        if (text == null) text = "";
        if (text.length() >= width) return text.substring(0, width);

        int totalPadding = width - text.length();
        int leftPadding = totalPadding / 2;
        int rightPadding = totalPadding - leftPadding;

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < leftPadding; i++) sb.append(' ');
        sb.append(text);
        for (int i = 0; i < rightPadding; i++) sb.append(' ');
        return sb.toString();
    }

    private static String padLeft(String text, int width) {
        if (text == null) text = "";
        if (text.length() >= width) return text.substring(0, width);

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < width - text.length(); i++) sb.append(' ');
        sb.append(text);
        return sb.toString();
    }

    private static String padRight(String text, int width) {
        if (text == null) text = "";
        if (text.length() >= width) return text.substring(0, width);

        StringBuilder sb = new StringBuilder(text);
        while (sb.length() < width) sb.append(' ');
        return sb.toString();
    }

    private static String spaces(int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) sb.append(' ');
        return sb.toString();
    }

    private static String repeat(String s, int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) sb.append(s);
        return sb.toString();
    }

    private static void writeCenteredLine(ByteArrayOutputStream b, String text, int width) throws Exception {
        writeLine(b, centerText(text, width));
    }

    private static void writeLine(ByteArrayOutputStream b, String text) throws Exception {
        b.write(text.getBytes("UTF-8"));
        b.write(0x0D);
        b.write(0x0A);
    }

    public interface PrintCallback {
        void onSuccess();
        void onError(String message);

        default void onPaperError(PaperStatus status, String message) {
            onError(message);
        }
    }

    public interface PaperStatusCallback {
        void onResult(PaperStatus status);
    }
}