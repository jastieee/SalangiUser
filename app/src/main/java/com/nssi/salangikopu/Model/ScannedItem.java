package com.nssi.salangikopu.Model;

public class ScannedItem {

    private String productCode;
    private String description;
    private double unitPrice;
    private double quantity;
    private double subtotal;

    private double quantityInStock;
    private double promoBatchRemaining;
    private boolean hasPromo;
    private int promoId;
    private int promoItemId;
    private double normalPrice;
    private String calculationCode;

    public ScannedItem(String productCode, String description, double unitPrice) {
        this.productCode       = productCode;
        this.description       = description;
        this.unitPrice         = unitPrice;
        this.quantity          = 1;
        this.subtotal          = unitPrice;
        this.normalPrice       = unitPrice;
        this.calculationCode   = "";
        this.hasPromo          = false;
        this.promoId           = 0;
        this.promoItemId       = 0;
        this.quantityInStock   = 0;
        this.promoBatchRemaining = 0;
    }

    // ── Original fields ───────────────────────────────────────────────

    public String getProductCode() { return productCode; }
    public String getDescription() { return description; }
    public double getUnitPrice()   { return unitPrice; }
    public double getQuantity()    { return quantity; }
    public double getSubtotal()    { return subtotal; }

    public void addQty() {
        this.quantity++;
        this.subtotal = this.quantity * this.unitPrice;
    }

    public void setQuantity(double qty) {
        this.quantity = qty;
        this.subtotal = qty * this.unitPrice;
    }

    // ── New fields ────────────────────────────────────────────────────

    public double getQuantityInStock() { return quantityInStock; }
    public void setQuantityInStock(double quantityInStock) {
        this.quantityInStock = quantityInStock;
    }

    public double getPromoBatchRemaining() { return promoBatchRemaining; }
    public void setPromoBatchRemaining(double promoBatchRemaining) {
        this.promoBatchRemaining = promoBatchRemaining;
    }

    public boolean hasPromo() { return hasPromo; }
    public void setHasPromo(boolean hasPromo) { this.hasPromo = hasPromo; }

    public int getPromoId() { return promoId; }
    public void setPromoId(int promoId) { this.promoId = promoId; }

    public int getPromoItemId() { return promoItemId; }
    public void setPromoItemId(int promoItemId) { this.promoItemId = promoItemId; }

    public double getNormalPrice() { return normalPrice; }
    public void setNormalPrice(double normalPrice) { this.normalPrice = normalPrice; }

    public String getCalculationCode() { return calculationCode; }
    public void setCalculationCode(String calculationCode) {
        this.calculationCode = calculationCode != null ? calculationCode : "";
    }
    public void setUnitPriceOverride(double newPrice) {
        this.unitPrice = newPrice;
        this.subtotal  = this.quantity * newPrice;
    }
}