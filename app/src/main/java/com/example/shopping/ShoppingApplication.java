package com.example.shopping;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.util.Log;

import com.example.shopping.db.DatabaseHelper;
import com.example.shopping.model.CartItem;
import com.example.shopping.model.PendingOrder;
import com.example.shopping.network.ApiConfig;
import com.example.shopping.network.ApiContract;
import com.example.shopping.network.HttpUtils;
import com.example.shopping.util.NetworkUtil;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class ShoppingApplication extends Application {

    private static final String TAG = "ShoppingApplication";

    private DatabaseHelper dbHelper;
    private volatile boolean isSyncing = false;

    @Override
    public void onCreate() {
        super.onCreate();
        dbHelper = new DatabaseHelper(this);
        registerNetworkReceiver();
    }

    public DatabaseHelper getDbHelper() {
        return dbHelper;
    }

    private void registerNetworkReceiver() {
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (NetworkUtil.isNetworkAvailable(context)) {
                    syncPendingOrders();
                }
            }
        };
        IntentFilter filter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
        registerReceiver(receiver, filter);
    }

    public void syncPendingOrders() {
        if (isSyncing) return;
        isSyncing = true;

        new Thread(() -> {
            try {
                List<PendingOrder> pendingOrders = dbHelper.getAllPendingOrders();
                for (PendingOrder po : pendingOrders) {
                    try {
                        uploadAndFinalizePendingOrder(po);
                    } catch (Exception e) {
                        Log.e(TAG, "同步待处理订单失败, id=" + po.getId(), e);
                    }
                }
            } finally {
                isSyncing = false;
            }
        }).start();
    }

    private void uploadAndFinalizePendingOrder(PendingOrder po) throws Exception {
        JSONObject json = new JSONObject(po.getOrderJson());
        int userId = json.getInt("userId");
        double totalPrice = json.getDouble("totalPrice");
        String createdAt = json.getString("createdAt");
        JSONArray itemsArray = json.getJSONArray("items");

        // 上传每个商品项到 MockAPI
        List<CartItem> cartItems = new ArrayList<>();
        for (int i = 0; i < itemsArray.length(); i++) {
            JSONObject itemJson = itemsArray.getJSONObject(i);

            JSONObject postJson = new JSONObject();
            postJson.put(ApiContract.KEY_ORDER_USER_ID, userId);
            postJson.put(ApiContract.KEY_TOTAL_PRICE, totalPrice);
            postJson.put(ApiContract.KEY_STATUS, "已下单");
            postJson.put(ApiContract.KEY_CREATED_AT, createdAt);
            postJson.put(ApiContract.KEY_PRODUCT_ID, itemJson.getInt(ApiContract.KEY_PRODUCT_ID));
            postJson.put(ApiContract.KEY_NAME, itemJson.getString(ApiContract.KEY_NAME));
            postJson.put(ApiContract.KEY_PRICE, itemJson.getDouble(ApiContract.KEY_PRICE));
            postJson.put(ApiContract.KEY_QUANTITY, itemJson.getInt(ApiContract.KEY_QUANTITY));

            String url = ApiConfig.BASE_URL + ApiContract.ORDERS;
            HttpUtils.doPost(url, postJson.toString());

            CartItem ci = new CartItem();
            ci.setProductId(itemJson.getInt(ApiContract.KEY_PRODUCT_ID));
            ci.setName(itemJson.getString(ApiContract.KEY_NAME));
            ci.setPrice(itemJson.getDouble(ApiContract.KEY_PRICE));
            ci.setQuantity(itemJson.getInt(ApiContract.KEY_QUANTITY));
            cartItems.add(ci);
        }

        // 上传成功 → 创建本地订单 + 清空购物车 + 删除待处理记录
        dbHelper.createOrderOnly(userId, totalPrice, cartItems, createdAt);
        dbHelper.clearCart(userId);
        dbHelper.deletePendingOrder(po.getId());
        Log.d(TAG, "待处理订单同步成功, id=" + po.getId());
    }
}
