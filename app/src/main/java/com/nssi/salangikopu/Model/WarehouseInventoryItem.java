package com.nssi.salangikopu.Model;

public class WarehouseInventoryItem {

    private String productCode;
    private String description;
    private double quantity;
    private double unitPrice;
    private double totalCost;

    public WarehouseInventoryItem(String productCode, String description,
                                  double quantity, double unitPrice, double totalCost) {
        this.productCode = productCode;
        this.description = description;
        this.quantity    = quantity;
        this.unitPrice   = unitPrice;
        this.totalCost   = totalCost;
    }

    // Getters
    public String getProductCode() { return productCode; }
    public String getDescription()  { return description; }
    public double getQuantity()     { return quantity; }
    public double getUnitPrice()    { return unitPrice; }
    public double getTotalCost()    { return totalCost; }

    // Setters
    public void setProductCode(String productCode) { this.productCode = productCode; }
    public void setDescription(String description)  { this.description = description; }
    public void setQuantity(double quantity)          { this.quantity = quantity; }
    public void setUnitPrice(double unitPrice)        { this.unitPrice = unitPrice; }
    public void setTotalCost(double totalCost)        { this.totalCost = totalCost; }
}