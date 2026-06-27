package com.example.shopping.model;

public class PendingOrder {
    private int id;
    private int userId;
    private String orderJson;
    private String createdAt;

    public PendingOrder() {}

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }

    public String getOrderJson() { return orderJson; }
    public void setOrderJson(String orderJson) { this.orderJson = orderJson; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
