package com.example.shopping.network;

// MockAPI 接口字段名，和远端schema保持一致
public class ApiContract {

    // 接口路径
    public static final String PRODUCTS = "/products";
    public static final String CART = "/cart";

    // 通用
    public static final String KEY_CODE = "code";
    public static final String KEY_DATA = "data";
    public static final String KEY_USER_ID = "user_id";

    // 商品相关
    public static final String KEY_ID = "id";
    public static final String KEY_NAME = "name";
    public static final String KEY_DESCRIPTION = "description";
    public static final String KEY_PRICE = "price";
    public static final String KEY_IMAGE_URL = "image_url";
    public static final String KEY_CATEGORY = "category";
    public static final String KEY_STOCK = "stock";

    // 购物车
    public static final String KEY_PRODUCT_ID = "product_id";
    public static final String KEY_QUANTITY = "quantity";

    // 订单相关
    public static final String ORDERS = "/orders";
    public static final String KEY_ORDER_USER_ID = "userId";
    public static final String KEY_TOTAL_PRICE = "totalPrice";
    public static final String KEY_CREATED_AT = "createdAt";
    public static final String KEY_STATUS = "status";
}
