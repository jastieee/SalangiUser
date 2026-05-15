package com.nssi.salangikopu.Utility;

import java.util.List;

public class ReceiptData {

    public String storeName;
    public String transactionNo;
    public String dateTime;
    public String cashierName;
    public double totalAmount;
    public List<ReceiptItem> items;

    public ReceiptData(String storeName, String transactionNo,
                       String dateTime, String cashierName,
                       double totalAmount, List<ReceiptItem> items) {
        this.storeName     = storeName;
        this.transactionNo = transactionNo;
        this.dateTime      = dateTime;
        this.cashierName   = cashierName;
        this.totalAmount   = totalAmount;
        this.items         = items;
    }

    public static class ReceiptItem {
        public String description;
        public double unitPrice;
        public double quantity;
        public double subtotal;

        public ReceiptItem(String description, double unitPrice,
                           double quantity, double subtotal) {
            this.description = description;
            this.unitPrice   = unitPrice;
            this.quantity    = quantity;
            this.subtotal    = subtotal;
        }
    }
}