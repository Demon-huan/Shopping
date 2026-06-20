package com.example.shopping.activity;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.example.shopping.util.SessionManager;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SessionManager sessionManager = new SessionManager(this);

        if (sessionManager.isLoggedIn()) {
            // 已登录，直接进入商品列表
            startActivity(new Intent(this, ProductListActivity.class));
        } else {
            // 未登录，跳转登录页
            startActivity(new Intent(this, LoginActivity.class));
        }

        finish();
    }
}
