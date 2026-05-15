package com.nssi.salangikopu.Model;

public class TransferConfirmationItem {

    private int    transferId;
    private String transferNo;
    private String warehouseName;
    private String storeName;
    private String transferDate;
    private int    itemCount;
    private String status;
    private int    warehouseId;
    private int    storeId;

    public TransferConfirmationItem(int transferId, String transferNo,
                                    String warehouseName, String storeName,
                                    String transferDate, int itemCount,
                                    String status, int warehouseId, int storeId) {
        this.transferId    = transferId;
        this.transferNo    = transferNo;
        this.warehouseName = warehouseName;
        this.storeName     = storeName;
        this.transferDate  = transferDate;
        this.itemCount     = itemCount;
        this.status        = status;
        this.warehouseId   = warehouseId;
        this.storeId       = storeId;
    }

    public int    getTransferId()    { return transferId; }
    public String getTransferNo()    { return transferNo; }
    public String getWarehouseName() { return warehouseName; }
    public String getStoreName()     { return storeName; }
    public String getTransferDate()  { return transferDate; }
    public int    getItemCount()     { return itemCount; }
    public String getStatus()        { return status; }
    public int    getWarehouseId()   { return warehouseId; }
    public int    getStoreId()       { return storeId; }
    public void   setStatus(String status) { this.status = status; }
}