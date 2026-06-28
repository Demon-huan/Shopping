package com.example.shopping.activity;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.shopping.R;
import com.example.shopping.db.DatabaseHelper;
import com.example.shopping.model.User;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;

public class RegisterActivity extends AppCompatActivity {

    private TextInputEditText etUsername;
    private TextInputEditText etPassword;
    private TextInputEditText etConfirmPassword;
    private TextInputEditText etEmail;
    private MaterialButton btnRegister;
    private TextView tvGoLogin;
    private LinearProgressIndicator progressBar;

    private DatabaseHelper dbHelper;

    // 初始化界面，绑定注册按钮和登录跳转
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

    // 后台检查用户名是否重复，不重复则插入新用户
    private void performRegister(String username, String password, String email) {
        new Thread(() -> {
            if (dbHelper.queryUserByUsername(username) != null) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "用户名已存在", Toast.LENGTH_SHORT).show();
                });
                return;
            }
            User user = new User(username, password, email);
            long result = dbHelper.insertUser(user);
            runOnUiThread(() -> {
                progressBar.setVisibility(View.GONE);
                if (result != -1) {
                    Toast.makeText(this, "注册成功，请登录", Toast.LENGTH_SHORT).show();
                    finish();
                } else {
                    Toast.makeText(this, "注册失败，请重试", Toast.LENGTH_SHORT).show();
                }
            });
        }).start();
    }
}
