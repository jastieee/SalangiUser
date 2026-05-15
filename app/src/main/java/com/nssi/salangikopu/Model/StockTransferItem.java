package com.nssi.salangikopu.Model;

public class StockTransferItem {

    private String productCode;
    private String description;
    private int    warehouseId;
    private String warehouseName;
    private double warehouseQty;   // current stock in warehouse
    private double transferQty;
    private double unitPrice;
    private double subtotal;

    public StockTransferItem(String productCode, String description,
                             int warehouseId, String warehouseName,
                             double warehouseQty, double unitPrice) {
        this.productCode   = productCode;
        this.description   = description;
        this.warehouseId   = warehouseId;
        this.warehouseName = warehouseName;
        this.warehouseQty  = warehouseQty;
        this.unitPrice     = unitPrice;
        this.transferQty   = 1;
        this.subtotal      = unitPrice;
    }

    public String getProductCode()   { return productCode; }
    public String getDescription()   { return description; }
    public int    getWarehouseId()   { return warehouseId; }
    public String getWarehouseName() { return warehouseName; }
    public double getWarehouseQty()  { return warehouseQty; }
    public double getTransferQty()   { return transferQty; }
    public double getUnitPrice()     { return unitPrice; }
    public double getSubtotal()      { return subtotal; }

    public boolean isOverStock() { return transferQty > warehouseQty; }

    public void setTransferQty(double qty) {
        this.transferQty = qty;
        this.subtotal    = qty * unitPrice;
    }
    public void setUnitPrice(double price) {
        this.unitPrice = price;
        this.subtotal  = transferQty * price;
    }
    public void setProductCode(String v)   { this.productCode = v; }
    public void setDescription(String v)   { this.description = v; }
    public void setWarehouseQty(double v)  { this.warehouseQty = v; }
}