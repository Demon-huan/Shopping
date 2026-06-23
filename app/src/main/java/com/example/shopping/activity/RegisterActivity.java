package com.example.shopping.activity;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.shopping.R;
import com.example.shopping.db.DatabaseHelper;
import com.example.shopping.model.User;
import com.example.shopping.network.ApiConfig;
import com.example.shopping.network.ApiContract;
import com.example.shopping.network.HttpUtils;
import com.example.shopping.util.NetworkUtil;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textview.MaterialTextView;

import org.json.JSONArray;
import org.json.JSONObject;

public class RegisterActivity extends AppCompatActivity {

    private static final int MSG_SUCCESS = 1;
    private static final int MSG_FAIL = 2;

    private TextInputEditText etUsername;
    private TextInputEditText etPassword;
    private TextInputEditText etConfirmPassword;
    private TextInputEditText etEmail;
    private MaterialButton btnRegister;
    private MaterialTextView tvGoLogin;
    private LinearProgressIndicator progressBar;

    private DatabaseHelper dbHelper;

    private final Handler handler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            progressBar.setVisibility(View.GONE);

            if (msg.what == MSG_SUCCESS) {
                Toast.makeText(RegisterActivity.this, "注册成功，请登录", Toast.LENGTH_SHORT).show();
                finish();
            } else if (msg.what == MSG_FAIL) {
                String error = (String) msg.obj;
                Toast.makeText(RegisterActivity.this, error, Toast.LENGTH_SHORT).show();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        dbHelper = new DatabaseHelper(this);

        etUsername = findViewById(R.id.et_username);
        etPassword = findViewById(R.id.et_password);
        etConfirmPassword = findViewById(R.id.et_confirm_password);
        etEmail = findViewById(R.id.et_email);
        btnRegister = findViewById(R.id.btn_register);
        tvGoLogin = findViewById(R.id.tv_go_login);
        progressBar = findViewById(R.id.progress_register);

        btnRegister.setOnClickListener(v -> {
            String username = etUsername.getText().toString().trim();
            String password = etPassword.getText().toString().trim();
            String confirmPassword = etConfirmPassword.getText().toString().trim();
            String email = etEmail.getText().toString().trim();

            if (username.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
                Toast.makeText(this, "请填写必填项", Toast.LENGTH_SHORT).show();
                return;
            }

            if (!password.equals(confirmPassword)) {
                Toast.makeText(this, "两次输入的密码不一致", Toast.LENGTH_SHORT).show();
                return;
            }

            progressBar.setVisibility(View.VISIBLE);
            performRegister(username, password, email);
        });

        tvGoLogin.setOnClickListener(v -> finish());
    }

    private void performRegister(String username, String password, String email) {
        new Thread(() -> {
            try {
                // 无网络时直接跳过，走 SQLite 回退
                if (!NetworkUtil.isNetworkAvailable(RegisterActivity.this)) {
                    throw new Exception("无网络连接");
                }
                // 先查重：GET /users?username=xxx
                String checkResp = HttpUtils.doGet(ApiConfig.BASE_URL + ApiContract.REGISTER
                        + "?" + ApiContract.KEY_USERNAME + "=" + username);
                JSONArray existing = new JSONArray(checkResp);

                if (existing.length() > 0) {
                    Message msg = handler.obtainMessage(MSG_FAIL, "用户名已存在");
                    handler.sendMessage(msg);
                    return;
                }

                // 创建用户：POST /users
                JSONObject body = new JSONObject();
                body.put(ApiContract.KEY_USERNAME, username);
                body.put(ApiContract.KEY_PASSWORD, password);
                body.put(ApiContract.KEY_EMAIL, email);

                String response = HttpUtils.doPost(ApiConfig.BASE_URL + ApiContract.REGISTER, body.toString());
                Message msg = handler.obtainMessage(MSG_SUCCESS, response);
                handler.sendMessage(msg);

            } catch (Exception e) {
                // 网络不可用，回退到本地 SQLite
                User existingUser = dbHelper.queryUserByUsername(username);
                if (existingUser != null) {
                    Message msg = handler.obtainMessage(MSG_FAIL, "用户名已存在");
                    handler.sendMessage(msg);
                    return;
                }

                User user = new User(username, password, email);
                long result = dbHelper.insertUser(user);

                if (result != -1) {
                    Message msg = handler.obtainMessage(MSG_SUCCESS, "LOCAL");
                    handler.sendMessage(msg);
                } else {
                    Message msg = handler.obtainMessage(MSG_FAIL, "注册失败，请重试");
                    handler.sendMessage(msg);
                }
            }
        }).start();
    }
}
