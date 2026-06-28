package com.example.shopping.util;

import android.content.Context;
import android.content.SharedPreferences;

public class SessionManager {

    private static final String PREF_NAME = "shopping_prefs";
    private static final String KEY_IS_LOGGED_IN = "is_logged_in";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_TOKEN = "token";

    private final SharedPreferences prefs;

    // 读取本地保存的登录信息
    public SessionManager(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    // 登录成功后把用户ID和用户名写入SharedPreferences
    public void setLoggedIn(int userId, String username, String token) {
        prefs.edit()
                .putBoolean(KEY_IS_LOGGED_IN, true)
                .putInt(KEY_USER_ID, userId)
                .putString(KEY_USERNAME, username)
                .putString(KEY_TOKEN, token)
                .apply();
    }

    // 判断当前是否已登录
    public boolean isLoggedIn() {
        return prefs.getBoolean(KEY_IS_LOGGED_IN, false);
    }

    // 获取当前登录用户的ID，未登录返回-1
    public int getUserId() {
        return prefs.getInt(KEY_USER_ID, -1);
    }

    // 清空所有登录信息，退出登录
    public void logout() {
        prefs.edit().clear().apply();
    }
}
