package com.example.shopping.activity;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;

import com.example.shopping.R;
import com.example.shopping.db.DatabaseHelper;
import com.example.shopping.model.Order;
import com.example.shopping.model.OrderItem;
import com.example.shopping.network.ApiConfig;
import com.example.shopping.network.ApiContract;
import com.example.shopping.network.HttpUtils;
import com.example.shopping.util.NetworkUtil;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class OrderDetailActivity extends AppCompatActivity {

    private static final int MSG_SUCCESS = 1;

    private TextView tvOrderId, tvOrderTime, tvOrderStatus, tvTotal;
    private RecyclerView recyclerView;
    private DatabaseHelper dbHelper;
    private int userId;
    private double orderTotal;
    private String orderTime;
    private Order order;
    private List<OrderItem> orderItems = new ArrayList<>();
    private OrderItemAdapter adapter;

    private final Handler handler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            if (msg.what == MSG_SUCCESS) {
                if (order != null) {
                    tvOrderId.setText(String.valueOf(order.getId()));
                    tvOrderTime.setText(order.getCreatedAt());
                    tvOrderStatus.setText(order.getStatus());
                    tvTotal.setText("¥" + String.format("%.2f", order.getTotalPrice()));
                }
                adapter.setItems(orderItems);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_order_detail);

        dbHelper = new DatabaseHelper(this);
        userId = getIntent().getIntExtra("user_id", -1);
        orderTotal = getIntent().getDoubleExtra("order_total", 0);
        orderTime = getIntent().getStringExtra("order_time");

        tvOrderId = findViewById(R.id.tv_detail_order_id);
        tvOrderTime = findViewById(R.id.tv_detail_order_time);
        tvOrderStatus = findViewById(R.id.tv_detail_order_status);
        tvTotal = findViewById(R.id.tv_detail_total);
        recyclerView = findViewById(R.id.recycler_order_items);

        com.google.android.material.appbar.MaterialToolbar toolbar = findViewById(R.id.toolbar_order_detail);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        adapter = new OrderItemAdapter(orderItems);
        recyclerView.setAdapter(adapter);

        loadOrderDetail();
    }

    private void loadOrderDetail() {
        new Thread(() -> {
            try {
                if (!NetworkUtil.isNetworkAvailable(OrderDetailActivity.this)) {
                    throw new Exception("无网络");
                }
                // 同一订单的商品共享 totalPrice + createdAt
                String response = HttpUtils.doGet(ApiConfig.BASE_URL + ApiContract.ORDERS
                        + "?" + ApiContract.KEY_ORDER_USER_ID + "=" + userId);
                JSONArray arr = new JSONArray(response);
                orderItems = new ArrayList<>();
                JSONObject first = null;
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject obj = arr.getJSONObject(i);
                    if (obj.getInt(ApiContract.KEY_ORDER_USER_ID) != userId) continue;
                    if (obj.getDouble(ApiContract.KEY_TOTAL_PRICE) == orderTotal
                            && obj.optString(ApiContract.KEY_CREATED_AT, "").equals(orderTime)) {
                        if (first == null) first = obj;
                        OrderItem item = new OrderItem();
                        item.setProductId(obj.getInt(ApiContract.KEY_PRODUCT_ID));
                        item.setName(obj.getString(ApiContract.KEY_NAME));
                        item.setPrice(obj.getDouble(ApiContract.KEY_PRICE));
                        item.setQuantity(obj.getInt(ApiContract.KEY_QUANTITY));
                        orderItems.add(item);
                    }
                }
                if (first != null) {
                    order = new Order();
                    order.setUserId(first.getInt(ApiContract.KEY_ORDER_USER_ID));
                    order.setTotalPrice(first.getDouble(ApiContract.KEY_TOTAL_PRICE));
                    order.setStatus(first.optString(ApiContract.KEY_STATUS, "已下单"));
                    order.setCreatedAt(first.optString(ApiContract.KEY_CREATED_AT, ""));
                }
            } catch (Exception e) {
                List<Order> localOrders = dbHelper.getOrdersByUserId(userId);
                for (Order o : localOrders) {
                    if (o.getTotalPrice() == orderTotal) {
                        order = o;
                        orderItems = dbHelper.getOrderItemsByOrderId(o.getId());
                        break;
                    }
                }
            }
            handler.sendEmptyMessage(MSG_SUCCESS);
        }).start();
    }

    // ========== 适配器 ==========

    static class OrderItemAdapter extends RecyclerView.Adapter<OrderItemAdapter.ViewHolder> {

        private List<OrderItem> items;

        OrderItemAdapter(List<OrderItem> items) {
            this.items = items;
        }

        void setItems(List<OrderItem> items) {
            this.items = items;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_order_item, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            OrderItem item = items.get(position);
            holder.tvName.setText(item.getName());
            holder.tvPrice.setText("¥" + String.format("%.2f", item.getPrice()));
            holder.tvQty.setText("×" + item.getQuantity());
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvName, tvPrice, tvQty;

            ViewHolder(View itemView) {
                super(itemView);
                tvName = itemView.findViewById(R.id.tv_order_item_name);
                tvPrice = itemView.findViewById(R.id.tv_order_item_price);
                tvQty = itemView.findViewById(R.id.tv_order_item_qty);
            }
        }
    }
}
