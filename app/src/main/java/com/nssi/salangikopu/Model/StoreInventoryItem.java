package com.nssi.salangikopu.Model;

public class StoreInventoryItem {

    private String productCode;
    private String description;
    private double quantityInStock;
    private double unitPrice;
    private double totalCost;
    private String warehouseName;
    private String transferNo;

    public StoreInventoryItem(String productCode, String description,
                              double quantityInStock, double unitPrice,
                              double totalCost, String warehouseName,
                              String transferNo) {
        this.productCode     = productCode;
        this.description     = description;
        this.quantityInStock = quantityInStock;
        this.unitPrice       = unitPrice;
        this.totalCost       = totalCost;
        this.warehouseName   = warehouseName;
        this.transferNo      = transferNo;
    }

    public String getProductCode()     { return productCode; }
    public String getDescription()     { return description; }
    public double getQuantityInStock() { return quantityInStock; }
    public double getUnitPrice()       { return unitPrice; }
    public double getTotalCost()       { return totalCost; }
    public String getWarehouseName()   { return warehouseName; }
    public String getTransferNo()      { return transferNo; }

    public void setProductCode(String productCode)         { this.productCode = productCode; }
    public void setDescription(String description)         { this.description = description; }
    public void setQuantityInStock(double quantityInStock) { this.quantityInStock = quantityInStock; }
    public void setUnitPrice(double unitPrice)             { this.unitPrice = unitPrice; }
    public void setTotalCost(double totalCost)             { this.totalCost = totalCost; }
    public void setWarehouseName(String warehouseName)     { this.warehouseName = warehouseName; }
    public void setTransferNo(String transferNo)           { this.transferNo = transferNo; }
}