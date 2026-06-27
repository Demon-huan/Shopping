package com.example.shopping.model;

public class Order {
    private int id;
    private int userId;
    private double totalPrice;
    private String status;
    private String createdAt;

    public Order() {}

    public Order(int userId, double totalPrice) {
        this.userId = userId;
        this.totalPrice = totalPrice;
        this.status = "已下单";
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }

    public double getTotalPrice() { return totalPrice; }
    public void setTotalPrice(double totalPrice) { this.totalPrice = totalPrice; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
