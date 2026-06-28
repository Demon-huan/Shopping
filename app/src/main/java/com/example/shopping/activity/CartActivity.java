package com.example.shopping.activity;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;

import com.example.shopping.R;
import com.example.shopping.db.DatabaseHelper;
import com.example.shopping.model.CartItem;
import com.example.shopping.network.ApiConfig;
import com.example.shopping.network.ApiContract;
import com.example.shopping.network.HttpUtils;
import com.example.shopping.util.NetworkUtil;
import com.google.android.material.button.MaterialButton;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class CartActivity extends AppCompatActivity {

    private static final int MSG_LOAD_SUCCESS = 1;

    private RecyclerView recyclerView;
    private TextView tvEmpty, tvTotalPrice;
    private MaterialButton btnClear, btnPurchase;
    private CheckBox cbSelectAll;

    private DatabaseHelper dbHelper;
    private CartAdapter adapter;
    private List<CartItem> cartItems = new ArrayList<>();
    private int userId;
    private volatile boolean isUploading = false;

    private final CheckBox.OnCheckedChangeListener selectAllListener = (buttonView, isChecked) -> {
        adapter.setSelectAll(isChecked);
        updateTotalPrice();
    };

    // 加载完成后刷新列表和合计
    private final Handler handler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            if (msg.what == MSG_LOAD_SUCCESS) {
                adapter.setItems(cartItems);
                updateTotalPrice();
                updateEmptyState();
            }
        }
    };

    // 初始化界面：购物车列表、全选、清空、购买
    @Override
    protected void onCreate(Bundle savedInstanceState) {

        dbHelper = new DatabaseHelper(this);
        userId = getIntent().getIntExtra("user_id", -1);

        recyclerView = findViewById(R.id.recycler_cart);
        tvEmpty = findViewById(R.id.tv_empty_cart);
        tvTotalPrice = findViewById(R.id.tv_total_price);
        btnClear = findViewById(R.id.btn_clear_cart);
        btnPurchase = findViewById(R.id.btn_purchase);
        cbSelectAll = findViewById(R.id.cb_select_all);

        // Toolbar 返回
        com.google.android.material.appbar.MaterialToolbar toolbar = findViewById(R.id.toolbar_cart);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        adapter = new CartAdapter(cartItems, new OnCartActionListener() {
            @Override
            public void onQuantityChanged(CartItem item, int newQuantity) {
                CartActivity.this.onQuantityChanged(item, newQuantity);
            }

            @Override
            public void onItemDeleted(CartItem item) {
                CartActivity.this.onItemDeleted(item);
            }

            @Override
            public void onSelectionChanged() {
                updateTotalPrice();
                cbSelectAll.setOnCheckedChangeListener(null);
                cbSelectAll.setChecked(adapter.isAllSelected());
                cbSelectAll.setOnCheckedChangeListener(selectAllListener);
            }
        });
        recyclerView.setAdapter(adapter);

        cbSelectAll.setOnCheckedChangeListener(selectAllListener);

        btnClear.setOnClickListener(v -> {
            dbHelper.clearCart(userId);
            cartItems.clear();
            adapter.setItems(cartItems);
            updateTotalPrice();
            updateEmptyState();
            Toast.makeText(this, "已清空购物车", Toast.LENGTH_SHORT).show();
        });

        btnPurchase.setOnClickListener(v -> {
            if (cartItems.isEmpty()) {
                Toast.makeText(this, "购物车为空", Toast.LENGTH_SHORT).show();
                return;
            }
            List<CartItem> selectedItems = adapter.getSelectedItems(cartItems);
            if (selectedItems.isEmpty()) {
                Toast.makeText(this, "请先选择商品", Toast.LENGTH_SHORT).show();
                return;
            }
            if (isUploading) {
                Toast.makeText(this, "正在处理中...", Toast.LENGTH_SHORT).show();
                return;
            }

            double total = 0;
            for (CartItem item : selectedItems) {
                total += item.getPrice() * item.getQuantity();
            }
            final double finalTotal = total;
            final List<CartItem> snapshot = new ArrayList<>(selectedItems);
            final String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());

            isUploading = true;
            btnPurchase.setEnabled(false);
            btnPurchase.setText("处理中...");

            // 先建本地订单 → 再尝试上传MockAPI → 失败则缓存待重试
            new Thread(() -> {
                try {
                    // 先创建本地订单（状态"待同步"），获取本地 orderId
                    final long localOrderId = dbHelper.createOrderOnly(
                            userId, finalTotal, snapshot, timestamp, "待同步");

                    // 删除已购商品从购物车
                    for (CartItem item : snapshot) {
                        dbHelper.deleteCartItem(item.getId());
                    }

                    if (NetworkUtil.isNetworkAvailable(CartActivity.this)) {
                        try {
                            // 上传到 MockAPI
                            for (CartItem item : snapshot) {
                                JSONObject json = new JSONObject();
                                json.put(ApiContract.KEY_ORDER_USER_ID, userId);
                                json.put(ApiContract.KEY_TOTAL_PRICE, finalTotal);
                                json.put(ApiContract.KEY_STATUS, "已下单");
                                json.put(ApiContract.KEY_CREATED_AT, timestamp);
                                json.put(ApiContract.KEY_PRODUCT_ID, item.getProductId());
                                json.put(ApiContract.KEY_NAME, item.getName());
                                json.put(ApiContract.KEY_PRICE, item.getPrice());
                                json.put(ApiContract.KEY_QUANTITY, item.getQuantity());
                                String url = ApiConfig.BASE_URL + ApiContract.ORDERS;
                                HttpUtils.doPost(url, json.toString());
                            }
                            // 上传成功 → 更新订单状态
                            dbHelper.updateOrderStatus((int) localOrderId, "已下单");
                            runOnUiThread(() -> {
                                cartItems.removeAll(snapshot);
                                adapter.clearSelection();
                                adapter.setItems(cartItems);
                                updateTotalPrice();
                                updateEmptyState();
                                Toast.makeText(CartActivity.this, "下单成功", Toast.LENGTH_SHORT).show();
                            });
                        } catch (Exception e) {
                            // 上传失败 → 缓存到 pending_orders，本地订单保持"待同步"
                            android.util.Log.e("CartActivity", "MockAPI上传失败", e);
                            String orderJson = buildPendingOrderJson(userId, finalTotal, snapshot, timestamp, localOrderId);
                            dbHelper.insertPendingOrder(userId, orderJson);
                            runOnUiThread(() -> {
                                cartItems.removeAll(snapshot);
                                adapter.clearSelection();
                                adapter.setItems(cartItems);
                                updateTotalPrice();
                                updateEmptyState();
                                Toast.makeText(CartActivity.this,
                                        "网络异常，订单将在联网后自动同步", Toast.LENGTH_LONG).show();
                            });
                        }
                    } else {
                        // 无网络 → 缓存到 pending_orders，本地订单保持"待同步"
                        String orderJson = buildPendingOrderJson(userId, finalTotal, snapshot, timestamp, localOrderId);
                        dbHelper.insertPendingOrder(userId, orderJson);
                        runOnUiThread(() -> {
                            cartItems.removeAll(snapshot);
                            adapter.clearSelection();
                            adapter.setItems(cartItems);
                            updateTotalPrice();
                            updateEmptyState();
                            Toast.makeText(CartActivity.this,
                                    "无网络，订单将在联网后自动同步", Toast.LENGTH_LONG).show();
                        });
                    }
                } finally {
                    runOnUiThread(() -> {
                        isUploading = false;
                        btnPurchase.setEnabled(true);
                        btnPurchase.setText("购买");
                    });
                }
            }).start();
        });

        loadCartItems();
    }

    // 把订单信息序列化成JSON，等联网后重传
    private String buildPendingOrderJson(int userId, double totalPrice,
                                          List<CartItem> items, String createdAt, long orderId) {
        try {
            JSONObject json = new JSONObject();
            json.put("userId", userId);
            json.put("totalPrice", totalPrice);
            json.put("createdAt", createdAt);
            json.put("orderId", orderId);
            JSONArray itemsArray = new JSONArray();
            for (CartItem item : items) {
                JSONObject itemJson = new JSONObject();
                itemJson.put(ApiContract.KEY_PRODUCT_ID, item.getProductId());
                itemJson.put(ApiContract.KEY_NAME, item.getName());
                itemJson.put(ApiContract.KEY_PRICE, item.getPrice());
                itemJson.put(ApiContract.KEY_QUANTITY, item.getQuantity());
                itemsArray.put(itemJson);
            }
            json.put("items", itemsArray);
            return json.toString();
        } catch (Exception e) {
            android.util.Log.e("CartActivity", "构建待同步订单JSON失败", e);
            return null;
        }
    }

    // 从SQLite加载当前用户的购物车商品
    private void loadCartItems() {
        new Thread(() -> {
            cartItems = dbHelper.getCartItemsByUserId(userId);
            handler.sendEmptyMessage(MSG_LOAD_SUCCESS);
        }).start();
    }

    // 更新商品数量，最少为1
    private void onQuantityChanged(CartItem item, int newQuantity) {
        if (newQuantity < 1) newQuantity = 1;
        dbHelper.updateCartItemQuantity(item.getId(), newQuantity);
        item.setQuantity(newQuantity);
        adapter.notifyDataSetChanged();
        updateTotalPrice();
    }

    // 删除单个购物车商品
    private void onItemDeleted(CartItem item) {
        dbHelper.deleteCartItem(item.getId());
        cartItems.remove(item);
        adapter.setItems(cartItems);
        updateTotalPrice();
        updateEmptyState();
        Toast.makeText(this, "已删除", Toast.LENGTH_SHORT).show();
    }

    // 只计算选中商品的总金额
    private void updateTotalPrice() {
        double total = 0;
        for (CartItem item : cartItems) {
            if (adapter.isSelected(item.getId())) {
                total += item.getPrice() * item.getQuantity();
            }
        }
        tvTotalPrice.setText("¥" + String.format("%.2f", total));
    }

    // 购物车为空时显示空状态提示
    private void updateEmptyState() {
        if (cartItems.isEmpty()) {
            recyclerView.setVisibility(View.GONE);
            tvEmpty.setVisibility(View.VISIBLE);
        } else {
            recyclerView.setVisibility(View.VISIBLE);
            tvEmpty.setVisibility(View.GONE);
        }
    }

    // ========== 接口 ==========

    interface OnCartActionListener {
        void onQuantityChanged(CartItem item, int newQuantity);
        void onItemDeleted(CartItem item);
        void onSelectionChanged();
    }

    // ========== RecyclerView 适配器 (内部类) ==========

    static class CartAdapter extends RecyclerView.Adapter<CartAdapter.ViewHolder> {

        private List<CartItem> items;
        private final OnCartActionListener listener;
        private final Set<Integer> selectedIds = new HashSet<>();

        CartAdapter(List<CartItem> items, OnCartActionListener listener) {
            this.items = items;
            this.listener = listener;
            // 默认全选
            for (CartItem item : items) {
                selectedIds.add(item.getId());
            }
        }

        void setItems(List<CartItem> items) {
            this.items = items;
            selectedIds.clear();
            for (CartItem item : items) {
                selectedIds.add(item.getId());
            }
            notifyDataSetChanged();
        }

        boolean isSelected(int itemId) {
            return selectedIds.contains(itemId);
        }

        boolean isAllSelected() {
            return !items.isEmpty() && selectedIds.size() == items.size();
        }

        void setSelectAll(boolean selectAll) {
            selectedIds.clear();
            if (selectAll) {
                for (CartItem item : items) {
                    selectedIds.add(item.getId());
                }
            }
            notifyDataSetChanged();
        }

        void clearSelection() {
            selectedIds.clear();
        }

        List<CartItem> getSelectedItems(List<CartItem> allItems) {
            List<CartItem> selected = new ArrayList<>();
            for (CartItem item : allItems) {
                if (selectedIds.contains(item.getId())) {
                    selected.add(item);
                }
            }
            return selected;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_cart, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            CartItem item = items.get(position);
            holder.tvName.setText(item.getName());
            holder.tvPrice.setText("¥" + String.format("%.2f", item.getPrice()));
            holder.tvQuantity.setText(String.valueOf(item.getQuantity()));

            holder.cbSelect.setOnCheckedChangeListener(null);
            holder.cbSelect.setChecked(selectedIds.contains(item.getId()));
            holder.cbSelect.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    selectedIds.add(item.getId());
                } else {
                    selectedIds.remove(item.getId());
                }
                listener.onSelectionChanged();
            });

            holder.btnMinus.setOnClickListener(v -> {
                int qty = item.getQuantity() - 1;
                if (qty >= 1) {
                    listener.onQuantityChanged(item, qty);
                }
            });

            holder.btnPlus.setOnClickListener(v -> {
                int qty = item.getQuantity() + 1;
                listener.onQuantityChanged(item, qty);
            });

            holder.btnDelete.setOnClickListener(v -> listener.onItemDeleted(item));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            CheckBox cbSelect;
            TextView tvName, tvPrice, tvQuantity;
            ImageButton btnMinus, btnPlus, btnDelete;

            ViewHolder(View itemView) {
                super(itemView);
                cbSelect = itemView.findViewById(R.id.cb_select);
                tvName = itemView.findViewById(R.id.tv_cart_name);
                tvPrice = itemView.findViewById(R.id.tv_cart_price);
                tvQuantity = itemView.findViewById(R.id.tv_quantity);
                btnMinus = itemView.findViewById(R.id.btn_minus);
                btnPlus = itemView.findViewById(R.id.btn_plus);
                btnDelete = itemView.findViewById(R.id.btn_delete);
            }
        }
    }
}
