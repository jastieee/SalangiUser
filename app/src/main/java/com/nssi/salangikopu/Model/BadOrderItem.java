package com.nssi.salangikopu.Model;

public class BadOrderItem {

    private String productCode;
    private String description;
    private double quantity;
    private double unitPrice;
    private double stockQty;
    private String remarks;

    public BadOrderItem(String productCode, String description,
                        double quantity, double unitPrice,
                        double stockQty, String remarks) {
        this.productCode = productCode;
        this.description = description;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.stockQty = stockQty;
        this.remarks = remarks;
    }

    public String getProductCode() {
        return productCode;
    }

    public String getDescription() {
        return description;
    }

    public double getQuantity() {
        return quantity;
    }

    public double getUnitPrice() {
        return unitPrice;
    }

    public double getStockQty() {
        return stockQty;
    }

    public String getRemarks() {
        return remarks;
    }

    public double getSubtotal() {
        return quantity * unitPrice;
    }

    public void setQuantity(double quantity) {
        this.quantity = quantity;
    }

    public void setUnitPrice(double unitPrice) {
        this.unitPrice = unitPrice;
    }

    public void setRemarks(String remarks) {
        this.remarks = remarks;
    }
}