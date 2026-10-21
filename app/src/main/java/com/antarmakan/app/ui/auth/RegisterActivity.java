package com.antarmakan.app.ui.auth;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;

import com.antarmakan.app.Config;
import com.antarmakan.app.model.Restaurant;
import com.antarmakan.app.model.User;
import com.antarmakan.app.ui.common.BaseActivity;
import com.antarmakan.app.ui.common.Nav;
import com.antarmakan.app.util.Async;
import com.antarmakan.app.util.Ui;

import java.util.Arrays;

public class RegisterActivity extends BaseActivity {

    private RadioGroup roleGroup;
    private EditText name, phone, pass, emailIn, address, vehicle, restoName, restoAddress;
    private Spinner kec, restoCat;
    private LinearLayout driverBox, merchantBox;
    private String googleUid, googleEmail;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        googleUid = getIntent().getStringExtra("googleUid");
        googleEmail = getIntent().getStringExtra("googleEmail");
        boolean google = googleUid != null;
        enableBack(google ? "Lengkapi Profil" : "Daftar Akun");
        LinearLayout page = scrollPage();
        LinearLayout card = formCard(page);
        if (google) {
            card.addView(Ui.text(this, "Masuk sebagai " + googleEmail, 14, Ui.PRIMARY_DARK, true));
            card.addView(Ui.muted(this, "Lengkapi data berikut untuk pertama kali."));
            card.addView(Ui.divider(this));
        }

        card.addView(Ui.label(this, "Daftar sebagai"));
        roleGroup = new RadioGroup(this);
        roleGroup.addView(radio(1, "🙋 Pelanggan (pesan makanan)"));
        roleGroup.addView(radio(2, "🏪 Mitra Resto / Warung"));
        roleGroup.addView(radio(3, "🛵 Driver Pengantar"));
        roleGroup.check(1);
        card.addView(roleGroup);
        card.addView(Ui.divider(this));

        card.addView(Ui.label(this, "Nama lengkap"));
        name = Ui.edit(this, "Nama Anda");
        if (google) name.setText(getIntent().getStringExtra("googleName"));
        card.addView(name);
        card.addView(Ui.label(this, "Nomor HP (WhatsApp aktif)"));
        phone = Ui.phone(this, "08xxxxxxxxxx");
        card.addView(phone);
        pass = Ui.password(this, "Minimal 6 karakter");
        emailIn = Ui.edit(this, "nama@gmail.com", android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        if (!google) {
            card.addView(Ui.label(this, "Kata sandi"));
            card.addView(pass);
            card.addView(Ui.label(this, "Email (disarankan, untuk lupa kata sandi)"));
            card.addView(emailIn);
        }
        card.addView(Ui.label(this, "Kecamatan"));
        kec = Ui.spinner(this, Config.namaKecamatan(), 0);
        card.addView(kec);
        card.addView(Ui.label(this, "Alamat lengkap"));
        address = Ui.multiline(this, "Nama jalan, RT/RW, desa, patokan");
        card.addView(address);

        driverBox = Ui.vbox(this);
        driverBox.addView(Ui.label(this, "Kendaraan & plat nomor"));
        vehicle = Ui.edit(this, "Contoh: Honda Beat - B 1234 XY");
        driverBox.addView(vehicle);
        driverBox.addView(Ui.muted(this, "Akun driver aktif setelah disetujui admin."));
        card.addView(driverBox);

        merchantBox = Ui.vbox(this);
        merchantBox.addView(Ui.label(this, "Nama resto / warung"));
        restoName = Ui.edit(this, "Contoh: Warung Bu Siti");
        merchantBox.addView(restoName);
        merchantBox.addView(Ui.label(this, "Kategori"));
        String[] cats = Arrays.copyOfRange(Config.KATEGORI, 1, Config.KATEGORI.length);
        restoCat = Ui.spinner(this, cats, 0);
        merchantBox.addView(restoCat);
        merchantBox.addView(Ui.label(this, "Alamat resto (kecamatan sama dengan di atas)"));
        restoAddress = Ui.multiline(this, "Alamat resto");
        merchantBox.addView(restoAddress);
        merchantBox.addView(Ui.muted(this, "Resto tampil di aplikasi setelah disetujui admin."));
        card.addView(merchantBox);

        roleGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup g, int id) {
                updateRoleBoxes();
            }
        });
        updateRoleBoxes();

        Button btn = Ui.primary(this, google ? "Simpan & Mulai" : "Daftar");
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                submit();
            }
        });
        card.addView(btn);
    }

    private RadioButton radio(int id, String text) {
        RadioButton rb = new RadioButton(this);
        rb.setId(id);
        rb.setText(text);
        rb.setTextColor(Ui.TEXT);
        return rb;
    }

    private String role() {
        int id = roleGroup.getCheckedRadioButtonId();
        if (id == 2) return User.ROLE_MERCHANT;
        if (id == 3) return User.ROLE_DRIVER;
        return User.ROLE_CUSTOMER;
    }

    private void updateRoleBoxes() {
        String r = role();
        driverBox.setVisibility(User.ROLE_DRIVER.equals(r) ? View.VISIBLE : View.GONE);
        merchantBox.setVisibility(User.ROLE_MERCHANT.equals(r) ? View.VISIBLE : View.GONE);
    }

    private void submit() {
        final String role = role();
        final String n = name.getText().toString();
        final String ph = phone.getText().toString();
        final String pw = pass.getText().toString();
        final String em = emailIn.getText().toString().trim();
        final String k = (String) kec.getSelectedItem();
        final String addr = address.getText().toString().trim();
        final String veh = vehicle.getText().toString().trim();
        if (addr.length() < 5) {
            Ui.toast(this, "Alamat wajib diisi");
            return;
        }
        Restaurant draft = null;
        if (User.ROLE_DRIVER.equals(role) && veh.length() < 3) {
            Ui.toast(this, "Isi data kendaraan");
            return;
        }
        if (User.ROLE_MERCHANT.equals(role)) {
            draft = new Restaurant();
            draft.name = restoName.getText().toString().trim();
            draft.category = (String) restoCat.getSelectedItem();
            draft.emoji = Config.emojiKategori(draft.category);
            draft.kecamatan = k;
            draft.address = restoAddress.getText().toString().trim();
            if (draft.name.length() < 2 || draft.address.length() < 5) {
                Ui.toast(this, "Isi nama & alamat resto");
                return;
            }
        }
        final Restaurant fd = draft;
        action("Mendaftar...", new Async.Task<User>() {
            @Override
            public User run() throws Exception {
                if (googleUid != null) {
                    return repo.completeGoogleProfile(googleUid, googleEmail, n, ph, role, k, addr, veh, fd);
                }
                return repo.register(n, ph, pw, em, role, k, addr, veh, fd);
            }
        }, new Async.Callback<User>() {
            @Override
            public void onResult(User u) {
                Ui.toast(RegisterActivity.this, "Pendaftaran berhasil");
                Nav.home(RegisterActivity.this, u.role);
            }
        });
    }
}
