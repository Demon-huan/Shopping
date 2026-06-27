package com.example.shopping.activity;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.example.shopping.R;
import com.example.shopping.db.DatabaseHelper;
import com.example.shopping.model.CartItem;
import com.example.shopping.model.Product;
import com.example.shopping.network.ApiConfig;
import com.example.shopping.network.ApiContract;
import com.example.shopping.network.HttpUtils;
import com.example.shopping.util.NetworkUtil;
import com.example.shopping.util.SessionManager;
import com.google.android.material.button.MaterialButton;

import org.json.JSONObject;

public class ProductDetailActivity extends AppCompatActivity {

    private static final int MSG_SUCCESS = 1;
    private static final int MSG_FAIL = 2;

    private ImageView ivImage;
    private TextView tvCategory, tvName, tvPrice, tvDescription;
    private MaterialButton btnAddToCart;

    private DatabaseHelper dbHelper;
    private SessionManager sessionManager;
    private int userId;
    private int productId;
    private Product currentProduct;

    private final Handler handler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            if (msg.what == MSG_SUCCESS) {
                displayProduct(currentProduct);
            } else if (msg.what == MSG_FAIL) {
                String error = (String) msg.obj;
                Toast.makeText(ProductDetailActivity.this, error, Toast.LENGTH_SHORT).show();
                // 有本地数据就显示本地数据
                if (currentProduct != null) {
                    displayProduct(currentProduct);
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_product_detail);

        com.google.android.material.appbar.MaterialToolbar toolbar = findViewById(R.id.toolbar_detail);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        dbHelper = new DatabaseHelper(this);
        sessionManager = new SessionManager(this);
        userId = sessionManager.getUserId();

        productId = getIntent().getIntExtra("product_id", -1);
        if (productId == -1) {
            Toast.makeText(this, "商品不存在", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        ivImage = findViewById(R.id.iv_detail_image);
        tvCategory = findViewById(R.id.tv_detail_category);
        tvName = findViewById(R.id.tv_detail_name);
        tvPrice = findViewById(R.id.tv_detail_price);
        tvDescription = findViewById(R.id.tv_detail_description);
        btnAddToCart = findViewById(R.id.btn_add_to_cart);

        // 先显示本地缓存
        currentProduct = dbHelper.getProductById(productId);
        if (currentProduct != null) {
            displayProduct(currentProduct);
        }

        // 再从网络获取最新信息
        loadProductDetail();

        btnAddToCart.setOnClickListener(v -> addToCart());
    }

    private void loadProductDetail() {
        new Thread(() -> {
            try {
                // 无网络时直接跳过，走 SQLite 回退
                if (!NetworkUtil.isNetworkAvailable(ProductDetailActivity.this)) {
                    throw new Exception("无网络连接");
                }
                String response = HttpUtils.doGet(ApiConfig.BASE_URL + ApiContract.PRODUCTS + "/" + productId);
                JSONObject data;

                // 兼容两种响应格式
                if (response.trim().startsWith("{")) {
                    JSONObject root = new JSONObject(response);
                    // 判断是 {"code":200,"data":{...}} 还是直接 {...}
                    if (root.has(ApiContract.KEY_CODE)) {
                        data = root.getJSONObject(ApiContract.KEY_DATA);
                    } else {
                        data = root;
                    }
                } else {
                    throw new Exception("无效的响应格式");
                }

                Product p = new Product();
                String idStr = data.optString(ApiContract.KEY_ID, "");
                if (idStr.isEmpty()) {
                    p.setId(data.getInt(ApiContract.KEY_ID));
                } else {
                    p.setId(Integer.parseInt(idStr));
                }
                p.setName(data.getString(ApiContract.KEY_NAME));
                p.setDescription(data.optString(ApiContract.KEY_DESCRIPTION, ""));
                p.setPrice(data.getDouble(ApiContract.KEY_PRICE));
                p.setImageUrl(data.optString(ApiContract.KEY_IMAGE_URL, ""));
                p.setCategory(data.optString(ApiContract.KEY_CATEGORY, ""));
                p.setStock(data.optInt(ApiContract.KEY_STOCK, 0));

                currentProduct = p;
                dbHelper.insertOrUpdateProduct(p);
            } catch (Exception e) {
                // 网络不可用，使用之前设置的本地数据
            }

            if (currentProduct != null) {
                handler.sendEmptyMessage(MSG_SUCCESS);
            } else {
                Message msg = handler.obtainMessage(MSG_FAIL, "加载商品详情失败");
                handler.sendMessage(msg);
            }
        }).start();
    }

    private void displayProduct(Product product) {
        tvName.setText(product.getName());
        tvCategory.setText(product.getCategory());
        tvPrice.setText("¥" + String.format("%.2f", product.getPrice()));
        tvDescription.setText(product.getDescription());
        Glide.with(this)
                .load(product.getImageUrl())
                .placeholder(R.drawable.ic_product_placeholder)
                .into(ivImage);
    }

    private void addToCart() {
        if (currentProduct == null) {
            Toast.makeText(this, "商品信息加载中，请稍后再试", Toast.LENGTH_SHORT).show();
            return;
        }

        new Thread(() -> {
            try {
                // 查重：同一用户同一商品是否已在购物车中
                CartItem existing = dbHelper.getCartItemByProductId(userId, currentProduct.getId());

                if (existing != null) {
                    // 已有该商品，数量+1
                    dbHelper.updateCartItemQuantity(existing.getId(), existing.getQuantity() + 1);
                    runOnUiThread(() ->
                            Toast.makeText(ProductDetailActivity.this, "已更新购物车数量", Toast.LENGTH_SHORT).show());
                } else {
                    // 新商品，插入购物车
                    CartItem item = new CartItem(
                            userId,
                            currentProduct.getId(),
                            currentProduct.getName(),
                            currentProduct.getPrice(),
                            1,
                            currentProduct.getImageUrl()
                    );
                    long result = dbHelper.insertCartItem(item);

                    // 尝试同步到服务器（可选）
                    try {
                        JSONObject body = new JSONObject();
                        body.put(ApiContract.KEY_USER_ID, userId);
                        body.put(ApiContract.KEY_PRODUCT_ID, currentProduct.getId());
                        body.put(ApiContract.KEY_QUANTITY, 1);
                        HttpUtils.doPost(ApiConfig.BASE_URL + ApiContract.CART, body.toString());
                    } catch (Exception ignored) {
                    }

                    long finalResult = result;
                    runOnUiThread(() -> {
                        if (finalResult != -1) {
                            Toast.makeText(ProductDetailActivity.this, "已加入购物车", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(ProductDetailActivity.this, "加入购物车失败", Toast.LENGTH_SHORT).show();
                        }
                    });
                }

            } catch (Exception e) {
                runOnUiThread(() -> {
                    Toast.makeText(ProductDetailActivity.this, "操作失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }
}
