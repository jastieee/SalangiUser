package com.nssi.salangikopu.Model;

public class RefundItem {

    private int transactionItemId;
    private String productCode;
    private String itemName;
    private double unitPrice;
    private double originalQty;
    private double refundQty;

    public RefundItem(int transactionItemId, String productCode,
                      String itemName, double unitPrice, double originalQty) {
        this.transactionItemId = transactionItemId;
        this.productCode = productCode;
        this.itemName = itemName;
        this.unitPrice = unitPrice;
        this.originalQty = originalQty;
        this.refundQty = 0;
    }

    public int getTransactionItemId() {
        return transactionItemId;
    }

    public String getProductCode() {
        return productCode;
    }

    public String getItemName() {
        return itemName;
    }

    public double getUnitPrice() {
        return unitPrice;
    }

    public double getOriginalQty() {
        return originalQty;
    }

    public double getRefundQty() {
        return refundQty;
    }

    public double getRefundAmount() {
        return refundQty * unitPrice;
    }

    public void setRefundQty(double refundQty) {
        if (refundQty < 0) refundQty = 0;
        this.refundQty = Math.min(refundQty, originalQty);
    }
}