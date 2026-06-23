package com.example.shopping.activity;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.shopping.R;
import com.example.shopping.db.DatabaseHelper;
import com.example.shopping.model.User;
import com.example.shopping.network.ApiConfig;
import com.example.shopping.network.ApiContract;
import com.example.shopping.network.HttpUtils;
import com.example.shopping.util.NetworkUtil;
import com.example.shopping.util.SessionManager;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textview.MaterialTextView;

import org.json.JSONArray;
import org.json.JSONObject;

public class LoginActivity extends AppCompatActivity {

    private static final int MSG_SUCCESS = 1;
    private static final int MSG_FAIL = 2;

    private TextInputEditText etUsername;
    private TextInputEditText etPassword;
    private MaterialButton btnLogin;
    private MaterialTextView tvGoRegister;
    private LinearProgressIndicator progressBar;

    private SessionManager sessionManager;
    private DatabaseHelper dbHelper;

    private final Handler handler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            progressBar.setVisibility(View.GONE);

            if (msg.what == MSG_SUCCESS) {
                String json = (String) msg.obj;
                handleLoginSuccess(json);
            } else if (msg.what == MSG_FAIL) {
                String error = (String) msg.obj;
                Toast.makeText(LoginActivity.this, error, Toast.LENGTH_SHORT).show();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        sessionManager = new SessionManager(this);
        dbHelper = new DatabaseHelper(this);

        etUsername = findViewById(R.id.et_username);
        etPassword = findViewById(R.id.et_password);
        btnLogin = findViewById(R.id.btn_login);
        tvGoRegister = findViewById(R.id.tv_go_register);
        progressBar = findViewById(R.id.progress_login);

        btnLogin.setOnClickListener(v -> {
            String username = etUsername.getText().toString().trim();
            String password = etPassword.getText().toString().trim();

            if (username.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "请输入用户名和密码", Toast.LENGTH_SHORT).show();
                return;
            }

            progressBar.setVisibility(View.VISIBLE);
            performLogin(username, password);
        });

        tvGoRegister.setOnClickListener(v -> {
            startActivity(new Intent(LoginActivity.this, RegisterActivity.class));
        });
    }

    private void performLogin(String username, String password) {
        new Thread(() -> {
            try {
                // 无网络时直接跳过，走 SQLite 回退
                if (!NetworkUtil.isNetworkAvailable(LoginActivity.this)) {
                    throw new Exception("无网络连接");
                }
                // 通过 MockAPI 查用户：GET /users?username=xxx
                String response = HttpUtils.doGet(ApiConfig.BASE_URL + ApiContract.LOGIN
                        + "?" + ApiContract.KEY_USERNAME + "=" + username);
                JSONArray users = new JSONArray(response);

                if (users.length() == 0) {
                    Message msg = handler.obtainMessage(MSG_FAIL, "用户名或密码错误");
                    handler.sendMessage(msg);
                    return;
                }

                JSONObject user = users.getJSONObject(0);
                if (password.equals(user.getString(ApiContract.KEY_PASSWORD))) {
                    Message msg = handler.obtainMessage(MSG_SUCCESS, user.toString());
                    handler.sendMessage(msg);
                } else {
                    Message msg = handler.obtainMessage(MSG_FAIL, "用户名或密码错误");
                    handler.sendMessage(msg);
                }
            } catch (Exception e) {
                // 网络不可用，回退到本地 SQLite
                User user = dbHelper.queryUserByUsernameAndPassword(username, password);
                if (user != null) {
                    Message msg = handler.obtainMessage(MSG_SUCCESS, "LOCAL");
                    handler.sendMessage(msg);
                } else {
                    Message msg = handler.obtainMessage(MSG_FAIL, "登录失败：" + e.getMessage());
                    handler.sendMessage(msg);
                }
            }
        }).start();
    }

    private void handleLoginSuccess(String json) {
        try {
            int userId;
            String username;
            String token;

            if (json.equals("LOCAL")) {
                User user = dbHelper.queryUserByUsername(etUsername.getText().toString().trim());
                userId = user.getId();
                username = user.getUsername();
                token = "";
            } else {
                // MockAPI 返回的用户对象：{"username":"xxx","password":"xxx","id":"1",...}
                JSONObject userObj = new JSONObject(json);
                userId = Integer.parseInt(userObj.getString(ApiContract.KEY_ID));
                username = userObj.getString(ApiContract.KEY_USERNAME);
                token = "";
            }

            // 保存登录状态
            sessionManager.setLoggedIn(userId, username, token);

            // 跳转商品列表页
            Intent intent = new Intent(LoginActivity.this, ProductListActivity.class);
            intent.putExtra("user_id", userId);
            startActivity(intent);
            finish();

        } catch (Exception e) {
            Toast.makeText(this, "数据解析错误：" + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
}
