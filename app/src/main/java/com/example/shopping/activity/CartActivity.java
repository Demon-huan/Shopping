package com.example.shopping.activity;

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
import com.example.shopping.db.DatabaseHelper;
import com.example.shopping.model.CartItem;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

public class CartActivity extends AppCompatActivity {

    private static final int MSG_LOAD_SUCCESS = 1;

    private RecyclerView recyclerView;
    private TextView tvEmpty, tvTotalPrice;
    private MaterialButton btnClear;

    private DatabaseHelper dbHelper;
    private CartAdapter adapter;
    private List<CartItem> cartItems = new ArrayList<>();
    private int userId;

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cart);

        dbHelper = new DatabaseHelper(this);
        userId = getIntent().getIntExtra("user_id", -1);

        recyclerView = findViewById(R.id.recycler_cart);
        tvEmpty = findViewById(R.id.tv_empty_cart);
        tvTotalPrice = findViewById(R.id.tv_total_price);
        btnClear = findViewById(R.id.btn_clear_cart);

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
        });
        recyclerView.setAdapter(adapter);

        btnClear.setOnClickListener(v -> {
            dbHelper.clearCart(userId);
            cartItems.clear();
            adapter.setItems(cartItems);
            updateTotalPrice();
            updateEmptyState();
            Toast.makeText(this, "已清空购物车", Toast.LENGTH_SHORT).show();
        });

        loadCartItems();
    }

    private void loadCartItems() {
        new Thread(() -> {
            cartItems = dbHelper.getCartItemsByUserId(userId);
            handler.sendEmptyMessage(MSG_LOAD_SUCCESS);
        }).start();
    }

    private void onQuantityChanged(CartItem item, int newQuantity) {
        if (newQuantity < 1) newQuantity = 1;
        dbHelper.updateCartItemQuantity(item.getId(), newQuantity);
        item.setQuantity(newQuantity);
        adapter.notifyDataSetChanged();
        updateTotalPrice();
    }

    private void onItemDeleted(CartItem item) {
        dbHelper.deleteCartItem(item.getId());
        cartItems.remove(item);
        adapter.setItems(cartItems);
        updateTotalPrice();
        updateEmptyState();
        Toast.makeText(this, "已删除", Toast.LENGTH_SHORT).show();
    }

    private void updateTotalPrice() {
        double total = 0;
        for (CartItem item : cartItems) {
            total += item.getPrice() * item.getQuantity();
        }
        tvTotalPrice.setText("¥" + String.format("%.2f", total));
    }

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
    }

    // ========== RecyclerView 适配器 (内部类) ==========

    static class CartAdapter extends RecyclerView.Adapter<CartAdapter.ViewHolder> {

        private List<CartItem> items;
        private final OnCartActionListener listener;

        CartAdapter(List<CartItem> items, OnCartActionListener listener) {
            this.items = items;
            this.listener = listener;
        }

        void setItems(List<CartItem> items) {
            this.items = items;
            notifyDataSetChanged();
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
            TextView tvName, tvPrice, tvQuantity;
            ImageButton btnMinus, btnPlus, btnDelete;

            ViewHolder(View itemView) {
                super(itemView);
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
