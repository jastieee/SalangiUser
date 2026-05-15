package com.nssi.salangikopu.Model;

public class DeliveryItem {
    private String productCode;
    private String description;

    // converted/base qty, always PCS
    private double quantity;

    private double totalCost;

    // original input
    private String unitName;
    private double conversionQty;
    private double originalQty;

    public DeliveryItem(
            String productCode,
            String description,
            double quantity,
            double totalCost,
            String unitName,
            double conversionQty,
            double originalQty
    ) {
        this.productCode = productCode;
        this.description = description;
        this.quantity = quantity;
        this.totalCost = totalCost;
        this.unitName = unitName;
        this.conversionQty = conversionQty;
        this.originalQty = originalQty;
    }

    // old constructor fallback
    public DeliveryItem(String productCode, String description, double quantity, double totalCost) {
        this(productCode, description, quantity, totalCost, "PCS", 1.0, quantity);
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

    public double getTotalCost() {
        return totalCost;
    }

    public String getUnitName() {
        return unitName;
    }

    public double getConversionQty() {
        return conversionQty;
    }

    public double getOriginalQty() {
        return originalQty;
    }

    public void setQuantity(double quantity) {
        this.quantity = quantity;
    }

    public void setTotalCost(double totalCost) {
        this.totalCost = totalCost;
    }

    public void setUnitName(String unitName) {
        this.unitName = unitName;
    }

    public void setConversionQty(double conversionQty) {
        this.conversionQty = conversionQty;
    }

    public void setOriginalQty(double originalQty) {
        this.originalQty = originalQty;
    }

    public double getComputedUnitCost() {
        return quantity > 0 ? totalCost / quantity : 0;
    }

    public String getDisplayQty() {
        return originalQty + " " + unitName + " = " + quantity + " PCS";
    }
}