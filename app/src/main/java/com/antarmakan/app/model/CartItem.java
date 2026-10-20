package com.antarmakan.app.model;

public class CartItem {
    public String menuId = "";
    public String name = "";
    public String emoji = "🍽️";
    public long price;
    public int qty;
    public String note = "";

    public long total() {
        return price * qty;
    }
}
