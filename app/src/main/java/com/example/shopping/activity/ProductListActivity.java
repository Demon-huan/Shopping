package com.example.shopping.activity;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;

import com.example.shopping.R;
import com.example.shopping.db.DatabaseHelper;
import com.example.shopping.model.Product;
import com.example.shopping.network.ApiConfig;
import com.example.shopping.network.ApiContract;
import com.example.shopping.network.HttpUtils;
import com.example.shopping.util.SessionManager;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class ProductListActivity extends AppCompatActivity {

    private static final int MSG_SUCCESS = 1;
    private static final int MSG_FAIL = 2;

    private RecyclerView recyclerView;
    private LinearProgressIndicator progressBar;
    private ProductAdapter adapter;
    private List<Product> productList = new ArrayList<>();

    private SessionManager sessionManager;
    private DatabaseHelper dbHelper;
    private int userId;

    private final Handler handler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            progressBar.setVisibility(View.GONE);

            if (msg.what == MSG_SUCCESS) {
                adapter.setProducts(productList);
            } else if (msg.what == MSG_FAIL) {
                String error = (String) msg.obj;
                Toast.makeText(ProductListActivity.this, error, Toast.LENGTH_SHORT).show();
                // 即使报错也显示本地数据
                adapter.setProducts(productList);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_product_list);

        sessionManager = new SessionManager(this);
        dbHelper = new DatabaseHelper(this);
        userId = sessionManager.getUserId();

        recyclerView = findViewById(R.id.recycler_products);
        progressBar = findViewById(R.id.progress_products);

        adapter = new ProductAdapter(productList, product -> {
            Intent intent = new Intent(ProductListActivity.this, ProductDetailActivity.class);
            intent.putExtra("product_id", product.getId());
            startActivity(intent);
        });
        recyclerView.setAdapter(adapter);

        // 设置 Toolbar
        com.google.android.material.appbar.MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // 加载商品数据
        progressBar.setVisibility(View.VISIBLE);
        loadProducts();
    }

    private void loadProducts() {
        new Thread(() -> {
            try {
                // 尝试网络获取
                String response = HttpUtils.doGet(ApiConfig.BASE_URL + ApiContract.PRODUCTS);
                List<Product> fetched = new ArrayList<>();

                // 兼容两种响应格式：MockAPI直接返回数组，自建服务器返回 {"code":200,"data":[...]}
                if (response.trim().startsWith("[")) {
                    // MockAPI 格式：直接是 JSONArray
                    JSONArray dataArray = new JSONArray(response);
                    for (int i = 0; i < dataArray.length(); i++) {
                        JSONObject obj = dataArray.getJSONObject(i);
                        Product p = parseProduct(obj);
                        fetched.add(p);
                        dbHelper.insertOrUpdateProduct(p);
                    }
                } else {
                    // 自建服务器格式：{"code":200,"data":[...]}
                    JSONObject root = new JSONObject(response);
                    JSONArray dataArray = root.getJSONArray(ApiContract.KEY_DATA);
                    for (int i = 0; i < dataArray.length(); i++) {
                        JSONObject obj = dataArray.getJSONObject(i);
                        Product p = parseProduct(obj);
                        fetched.add(p);
                        dbHelper.insertOrUpdateProduct(p);
                    }
                }
                productList = fetched;
            } catch (Exception e) {
                // 网络不可用，从本地加载
                productList = dbHelper.getAllProducts();
            }

            if (productList.isEmpty()) {
                Message msg = handler.obtainMessage(MSG_FAIL, "暂无商品数据，请连接网络后重试");
                handler.sendMessage(msg);
            } else {
                Message msg = handler.obtainMessage(MSG_SUCCESS);
                handler.sendMessage(msg);
            }
        }).start();
    }

    private Product parseProduct(JSONObject obj) throws Exception {
        Product p = new Product();
        // MockAPI 的 id 可能是字符串，兼容处理
        String idStr = obj.optString(ApiContract.KEY_ID, "");
        if (idStr.isEmpty()) {
            p.setId(obj.getInt(ApiContract.KEY_ID));
        } else {
            p.setId(Integer.parseInt(idStr));
        }
        p.setName(obj.getString(ApiContract.KEY_NAME));
        p.setDescription(obj.optString(ApiContract.KEY_DESCRIPTION, ""));
        p.setPrice(obj.getDouble(ApiContract.KEY_PRICE));
        p.setImageUrl(obj.optString(ApiContract.KEY_IMAGE_URL, ""));
        p.setCategory(obj.optString(ApiContract.KEY_CATEGORY, ""));
        p.setStock(obj.optInt(ApiContract.KEY_STOCK, 0));
        return p;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.toolbar_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_cart) {
            Intent intent = new Intent(this, CartActivity.class);
            intent.putExtra("user_id", userId);
            startActivity(intent);
            return true;
        }

        if (id == R.id.action_logout) {
            sessionManager.logout();
            Intent intent = new Intent(this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    // ========== RecyclerView 适配器 (内部类) ==========

    public interface OnProductClickListener {
        void onProductClick(Product product);
    }

    static class ProductAdapter extends RecyclerView.Adapter<ProductAdapter.ViewHolder> {

        private List<Product> products;
        private final OnProductClickListener listener;

        ProductAdapter(List<Product> products, OnProductClickListener listener) {
            this.products = products;
            this.listener = listener;
        }

        void setProducts(List<Product> products) {
            this.products = products;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_product, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Product product = products.get(position);
            holder.tvName.setText(product.getName());
            holder.tvCategory.setText(product.getCategory());
            holder.tvPrice.setText("¥" + String.format("%.2f", product.getPrice()));
            holder.itemView.setOnClickListener(v -> listener.onProductClick(product));
        }

        @Override
        public int getItemCount() {
            return products.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            ImageView ivProduct;
            TextView tvName, tvCategory, tvPrice;

            ViewHolder(View itemView) {
                super(itemView);
                ivProduct = itemView.findViewById(R.id.iv_product);
                tvName = itemView.findViewById(R.id.tv_product_name);
                tvCategory = itemView.findViewById(R.id.tv_product_category);
                tvPrice = itemView.findViewById(R.id.tv_product_price);
            }
        }
    }
}
