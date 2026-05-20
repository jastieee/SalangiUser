package com.nssi.salangikopu.Connection;

public class ENV {

    public static final String HOST = "app.3ecandy.com";
    public static final String PATH = "mobile_api";

    public static final String API_BASE_URL = "https://" + HOST + "/" + PATH;

    public static final String LOGIN_URL = API_BASE_URL + "/login.php";
    public static final String MODULES_URL = API_BASE_URL + "/modules.php";
    public static final String WAREHOUSES_URL = API_BASE_URL + "/warehouses.php";
    public static final String PRODUCT_LOOKUP_URL = API_BASE_URL + "/product_lookup.php";
    public static final String SAVE_DELIVERY_URL = API_BASE_URL + "/save_delivery.php";
    public static final String GENERATE_PO_URL = API_BASE_URL + "/generate_po.php";
    public static final String REFUND_SEARCH_URL  = API_BASE_URL + "/refund_search.php";
    public static final String REFUND_PROCESS_URL = API_BASE_URL + "/refund_process.php";
    public static final String REGISTER_PRODUCT_URL = API_BASE_URL + "/register_product.php";
    public static final String GENERATE_TRX_URL = API_BASE_URL + "/generate_trx.php";
    public static final String SCAN_LOOKUP_URL  = API_BASE_URL + "/scan_lookup.php";
    public static final String SAVE_SALE_URL    = API_BASE_URL + "/save_sale.php";
    public static final String GENERATE_TRANSFER_URL = API_BASE_URL + "/generate_transfer.php";
    public static final String TRANSFER_LOOKUP_URL   = API_BASE_URL + "/transfer_lookup.php";
    public static final String SAVE_TRANSFER_URL     = API_BASE_URL + "/save_transfer.php";
    public static final String STORE_SNAPSHOTS_URL = API_BASE_URL + "/store_snapshots.php";
    public static final String STORE_INVENTORY_URL = API_BASE_URL + "/store_inventory.php";
    public static final String TRANSFER_LIST_URL    = API_BASE_URL + "/transfer_list.php";
    public static final String TRANSFER_CONFIRM_URL = API_BASE_URL + "/transfer_confirm.php";
    public static final String TRANSFER_DETAILS_URL = API_BASE_URL + "/transfer_details.php";
    public static final String WAREHOUSE_INVENTORY_URL = API_BASE_URL + "/warehouse_inventory.php";
    public static final String ALLOWED_WAREHOUSES_URL = API_BASE_URL + "/allowed_warehouses.php";
    public static final String ALLOWED_STORES_URL  = API_BASE_URL + "/allowed_stores.php";
    public static final String REFUND_PENDING_ITEMS_URL = API_BASE_URL + "/refund_pending_items.php";
    public static final String REFUND_RETURN_TO_STORE_URL = API_BASE_URL + "/refund_return_to_store.php";
    public static final String REFUND_DISPOSE_ITEM_URL = API_BASE_URL + "/refund_dispose_item.php";
    public static final String GENERATE_BO_URL = API_BASE_URL + "/generate_bo.php";
    public static final String BAD_ORDER_LOOKUP_URL = API_BASE_URL + "/bad_order_lookup.php";
    public static final String SAVE_BAD_ORDER_URL = API_BASE_URL + "/save_bad_order.php";
    public static final String SUPPLIERS_URL = API_BASE_URL + "/suppliers.php";
    //    public static final String SUPPLIERS_LIST_URL     = API_BASE_URL + "suppliers/suppliers_list.php";
    public static final String TRANSFER_DENY_URL = API_BASE_URL + "/transfer_deny.php";

    public static final String MOD_BAD_ORDER = "Bad Order - Mobile";
    // module names if still needed in app logic
    public static final String MOD_WAREHOUSE_INVENTORY = "Warehouse Inventory - Mobile";
    public static final String MOD_STORE_INVENTORY     = "Store Inventory - Mobile";
    public static final String MOD_SCAN_ITEMS          = "Scan Items - Mobile";
    public static final String MOD_STOCK_TRANSFERS     = "Stock Transfers - Mobile";
    public static final String MOD_DELIVERY_IN         = "Delivery In - Mobile";
    public static final String MOD_REFUND_ITEMS        = "Refund Items - Mobile";
    public static final String MOD_BO                  = "Bar Order - Mobile";
}