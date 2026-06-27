package com.example.shopping.model;

public class OrderItem {
    private int id;
    private int orderId;
    private int productId;
    private String name;
    private double price;
    private int quantity;

    public OrderItem() {}

    public OrderItem(int orderId, int productId, String name, double price, int quantity) {
        this.orderId = orderId;
        this.productId = productId;
        this.name = name;
        this.price = price;
        this.quantity = quantity;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getOrderId() { return orderId; }
    public void setOrderId(int orderId) { this.orderId = orderId; }

    public int getProductId() { return productId; }
    public void setProductId(int productId) { this.productId = productId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
}
