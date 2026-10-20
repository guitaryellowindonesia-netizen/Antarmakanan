package com.antarmakan.app.ui.common;

import android.app.Activity;
import android.content.Intent;

import com.antarmakan.app.model.User;
import com.antarmakan.app.ui.admin.AdminMainActivity;
import com.antarmakan.app.ui.auth.LoginActivity;
import com.antarmakan.app.ui.customer.CustomerMainActivity;
import com.antarmakan.app.ui.driver.DriverMainActivity;
import com.antarmakan.app.ui.merchant.MerchantMainActivity;

public final class Nav {
    private Nav() {}

    /** Buka halaman utama sesuai peran, hapus riwayat layar sebelumnya. */
    public static void home(Activity a, String role) {
        Class<?> target;
        if (User.ROLE_MERCHANT.equals(role)) target = MerchantMainActivity.class;
        else if (User.ROLE_DRIVER.equals(role)) target = DriverMainActivity.class;
        else if (User.ROLE_ADMIN.equals(role)) target = AdminMainActivity.class;
        else target = CustomerMainActivity.class;
        Intent i = new Intent(a, target);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        a.startActivity(i);
        a.finish();
    }

    public static void login(Activity a) {
        Intent i = new Intent(a, LoginActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        a.startActivity(i);
        a.finish();
    }
}
