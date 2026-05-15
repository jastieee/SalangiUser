package com.nssi.salangikopu.Model;

public class RefundDecisionItem {

    private int refundItemId;
    private int refundId;
    private int transactionId;
    private String transactionNo;
    private String productCode;
    private String itemName;
    private int quantity;
    private double unitPrice;
    private double subtotal;
    private String status;
    private String refundType;
    private String refundReason;
    private String refundDate;
    private int storeId;
    private String storeName;
    private String processedBy;
    private String originalCashier;

    public RefundDecisionItem(
            int refundItemId,
            int refundId,
            int transactionId,
            String transactionNo,
            String productCode,
            String itemName,
            int quantity,
            double unitPrice,
            double subtotal,
            String status,
            String refundType,
            String refundReason,
            String refundDate,
            int storeId,
            String storeName,
            String processedBy,
            String originalCashier
    ) {
        this.refundItemId = refundItemId;
        this.refundId = refundId;
        this.transactionId = transactionId;
        this.transactionNo = transactionNo;
        this.productCode = productCode;
        this.itemName = itemName;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.subtotal = subtotal;
        this.status = status;
        this.refundType = refundType;
        this.refundReason = refundReason;
        this.refundDate = refundDate;
        this.storeId = storeId;
        this.storeName = storeName;
        this.processedBy = processedBy;
        this.originalCashier = originalCashier;
    }

    public int getRefundItemId() { return refundItemId; }
    public int getRefundId() { return refundId; }
    public int getTransactionId() { return transactionId; }
    public String getTransactionNo() { return transactionNo; }
    public String getProductCode() { return productCode; }
    public String getItemName() { return itemName; }
    public int getQuantity() { return quantity; }
    public double getUnitPrice() { return unitPrice; }
    public double getSubtotal() { return subtotal; }
    public String getStatus() { return status; }
    public String getRefundType() { return refundType; }
    public String getRefundReason() { return refundReason; }
    public String getRefundDate() { return refundDate; }
    public int getStoreId() { return storeId; }
    public String getStoreName() { return storeName; }
    public String getProcessedBy() { return processedBy; }
    public String getOriginalCashier() { return originalCashier; }
}