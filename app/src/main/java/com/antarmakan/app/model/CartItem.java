package com.antarmakan.app.model;

public class CartItem {
    public String menuId = "";
    public String name = "";
    public String emoji = "🍽️";
    public long price;
    public int qty;
    public String note = "";
    public String options = "";   // pilihan varian "Grup=Pilihan;Grup=Pilihan"

    /** Kunci baris keranjang: menu yang sama dengan varian berbeda jadi baris terpisah. */
    public String key() {
        return menuId + "|" + options;
    }

    public long total() {
        return price * qty;
    }
}
