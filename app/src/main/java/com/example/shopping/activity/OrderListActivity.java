package com.example.shopping.activity;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;

import com.example.shopping.R;
import com.example.shopping.ShoppingApplication;
import com.example.shopping.db.DatabaseHelper;
import com.example.shopping.model.Order;

import com.example.shopping.network.ApiConfig;
import com.example.shopping.network.ApiContract;
import com.example.shopping.network.HttpUtils;
import com.example.shopping.util.NetworkUtil;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

public class OrderListActivity extends AppCompatActivity {

    private static final int MSG_SUCCESS = 1;

    private RecyclerView recyclerView;
    private TextView tvEmpty;
    private DatabaseHelper dbHelper;
    private int userId;
    private List<Order> orderList = new ArrayList<>();
    private OrderAdapter adapter;

    // 加载完成后刷新订单列表，无订单时显示空状态
    private final Handler handler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            if (msg.what == MSG_SUCCESS) {
                adapter.setOrders(orderList);
                if (orderList.isEmpty()) {
                    recyclerView.setVisibility(View.GONE);
                    tvEmpty.setVisibility(View.VISIBLE);
                } else {
                    recyclerView.setVisibility(View.VISIBLE);
                    tvEmpty.setVisibility(View.GONE);
                }
            }
        }
    };

    // 初始化界面，绑定订单列表和Toolbar返回
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_order_list);

        dbHelper = new DatabaseHelper(this);
        userId = getIntent().getIntExtra("user_id", -1);

        recyclerView = findViewById(R.id.recycler_orders);
        tvEmpty = findViewById(R.id.tv_empty_orders);

        com.google.android.material.appbar.MaterialToolbar toolbar = findViewById(R.id.toolbar_order_list);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        adapter = new OrderAdapter(orderList, new OnOrderClickListener() {
            @Override
            public void onOrderClick(Order order) {
                Intent intent = new Intent(OrderListActivity.this, OrderDetailActivity.class);
                intent.putExtra("user_id", userId);
                intent.putExtra("order_total", order.getTotalPrice());
                intent.putExtra("order_time", order.getCreatedAt());
                startActivity(intent);
            }

            @Override
            public void onOrderDelete(Order order) {
                deleteOrder(order);
            }
        });
        recyclerView.setAdapter(adapter);

        loadOrders();
    }

    // 每次回到页面时重新加载订单并尝试同步待处理订单
    @Override
    protected void onResume() {
        super.onResume();
        loadOrders();
        ((ShoppingApplication) getApplication()).syncPendingOrders();
    }

    // 在线：从MockAPI拉取 → 同步到本地SQLite；离线：读本地缓存
    private void loadOrders() {
        new Thread(() -> {
            try {
                if (!NetworkUtil.isNetworkAvailable(OrderListActivity.this)) {
                    throw new Exception("无网络");
                }
                // MockAPI 每条记录是一个商品项，含订单元数据
                // 同一订单的商品共享 totalPrice + createdAt，以此分组
                String response = HttpUtils.doGet(ApiConfig.BASE_URL + ApiContract.ORDERS
                        + "?" + ApiContract.KEY_ORDER_USER_ID + "=" + userId);
                JSONArray arr = new JSONArray(response);
                LinkedHashMap<String, Order> orderMap = new LinkedHashMap<>();
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject obj = arr.getJSONObject(i);
                    if (obj.getInt(ApiContract.KEY_ORDER_USER_ID) != userId) continue;
                    double tp = obj.getDouble(ApiContract.KEY_TOTAL_PRICE);
                    String ct = obj.optString(ApiContract.KEY_CREATED_AT, "");
                    String key = tp + "|" + ct;
                    if (!orderMap.containsKey(key)) {
                        Order o = new Order();
                        String idStr = obj.optString(ApiContract.KEY_ID, "0");
                        o.setId(Integer.parseInt(idStr));
                        o.setUserId(obj.getInt(ApiContract.KEY_ORDER_USER_ID));
                        o.setTotalPrice(tp);
                        o.setStatus(obj.optString(ApiContract.KEY_STATUS, "已下单"));
                        o.setCreatedAt(ct);
                        orderMap.put(key, o);
                    }
                }
                orderList = new ArrayList<>(orderMap.values());

                // 在线时同步到本地 SQLite
                dbHelper.syncOrdersFromRemote(userId, orderList);
            } catch (Exception e) {
                // 离线或失败：从本地 SQLite 读取（上次同步的缓存）
                orderList = dbHelper.getOrdersByUserId(userId);
            }
            handler.sendEmptyMessage(MSG_SUCCESS);
        }).start();
    }

    // 同时删远端和本地，保持一致
    private void deleteOrder(Order order) {
        new Thread(() -> {
            try {
                if (NetworkUtil.isNetworkAvailable(OrderListActivity.this)) {
                    // 从MockAPI获取所有订单记录，删除匹配的
                    String response = HttpUtils.doGet(ApiConfig.BASE_URL + ApiContract.ORDERS
                            + "?" + ApiContract.KEY_ORDER_USER_ID + "=" + userId);
                    JSONArray arr = new JSONArray(response);
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject obj = arr.getJSONObject(i);
                        if (obj.getInt(ApiContract.KEY_ORDER_USER_ID) != userId) continue;
                        double tp = obj.getDouble(ApiContract.KEY_TOTAL_PRICE);
                        String ct = obj.optString(ApiContract.KEY_CREATED_AT, "");
                        if (tp == order.getTotalPrice() && ct.equals(order.getCreatedAt())) {
                            String recordId = obj.optString(ApiContract.KEY_ID, "");
                            if (!recordId.isEmpty()) {
                                HttpUtils.doDelete(ApiConfig.BASE_URL + ApiContract.ORDERS + "/" + recordId);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                android.util.Log.e("OrderListActivity", "删除MockAPI订单失败", e);
            }

            // 删除本地订单
            dbHelper.deleteOrder(order.getId());

            runOnUiThread(() -> {
                orderList.remove(order);
                adapter.setOrders(orderList);
                if (orderList.isEmpty()) {
                    recyclerView.setVisibility(View.GONE);
                    tvEmpty.setVisibility(View.VISIBLE);
                }
                Toast.makeText(OrderListActivity.this, "订单已删除", Toast.LENGTH_SHORT).show();
            });
        }).start();
    }

    // ========== 适配器 ==========

    interface OnOrderClickListener {
        void onOrderClick(Order order);
        void onOrderDelete(Order order);
    }

    static class OrderAdapter extends RecyclerView.Adapter<OrderAdapter.ViewHolder> {

        private List<Order> orders;
        private final OnOrderClickListener listener;

        OrderAdapter(List<Order> orders, OnOrderClickListener listener) {
            this.orders = orders;
            this.listener = listener;
        }

        void setOrders(List<Order> orders) {
            this.orders = orders;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_order, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Order order = orders.get(position);
            holder.tvOrderId.setText("订单号：" + order.getId());
            holder.tvStatus.setText(order.getStatus());
            holder.tvTime.setText(order.getCreatedAt());
            holder.tvTotal.setText("¥" + String.format("%.2f", order.getTotalPrice()));
            holder.itemView.setOnClickListener(v -> listener.onOrderClick(order));
            holder.btnDelete.setOnClickListener(v -> listener.onOrderDelete(order));
        }

        @Override
        public int getItemCount() {
            return orders.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvOrderId, tvStatus, tvTime, tvTotal;
            ImageButton btnDelete;

            ViewHolder(View itemView) {
                super(itemView);
                tvOrderId = itemView.findViewById(R.id.tv_order_id);
                tvStatus = itemView.findViewById(R.id.tv_order_status);
                tvTime = itemView.findViewById(R.id.tv_order_time);
                tvTotal = itemView.findViewById(R.id.tv_order_total);
                btnDelete = itemView.findViewById(R.id.btn_delete_order);
            }
        }
    }
}
