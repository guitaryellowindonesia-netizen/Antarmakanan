package com.antarmakan.app.ui.auth;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.antarmakan.app.Config;
import com.antarmakan.app.data.Repo;
import com.antarmakan.app.model.User;
import com.antarmakan.app.ui.common.BaseActivity;
import com.antarmakan.app.ui.common.Nav;
import com.antarmakan.app.util.Async;
import com.antarmakan.app.util.Ui;

public class LoginActivity extends BaseActivity {

    private EditText phone, pass;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getActionBar() != null) getActionBar().hide();
        LinearLayout page = scrollPage();

        LinearLayout hero = Ui.vbox(this);
        hero.setGravity(Gravity.CENTER);
        hero.setBackgroundColor(Ui.PRIMARY);
        hero.setPadding(0, Ui.dp(this, 40), 0, Ui.dp(this, 32));
        TextView logo = Ui.text(this, "🛵", 48, Color.WHITE, false);
        TextView name = Ui.text(this, Config.NAMA_APLIKASI, 28, Color.WHITE, true);
        TextView sub = Ui.text(this, "Antar makanan se-" + Config.NAMA_KABUPATEN, 14, Color.WHITE, false);
        hero.addView(logo);
        hero.addView(name);
        hero.addView(sub);
        page.addView(hero, Ui.matchWrap());

        LinearLayout card = formCard(page);
        card.addView(Ui.title(this, "Masuk"));
        card.addView(Ui.spacer(this, 8));
        card.addView(Ui.label(this, "Nomor HP"));
        phone = Ui.phone(this, "08xxxxxxxxxx");
        card.addView(phone);
        card.addView(Ui.label(this, "Kata sandi"));
        pass = Ui.password(this, "Minimal 6 karakter");
        card.addView(pass);
        Button login = Ui.primary(this, "Masuk");
        login.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                doLogin();
            }
        });
        card.addView(login);
        Button reg = Ui.outlineButton(this, "Belum punya akun? Daftar", Ui.PRIMARY);
        reg.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(LoginActivity.this, RegisterActivity.class));
            }
        });
        card.addView(reg);
        TextView forgot = Ui.text(this, "Lupa kata sandi?", 14, Ui.PRIMARY, true);
        forgot.setGravity(Gravity.CENTER);
        forgot.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 4));
        forgot.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                forgotPassword();
            }
        });
        card.addView(forgot, Ui.matchWrap());

        if (Config.googleLoginEnabled()) {
            TextView or = Ui.muted(this, "atau");
            or.setGravity(Gravity.CENTER);
            card.addView(or, Ui.matchWrap());
            Button google = Ui.outlineButton(this, "G   Masuk dengan Google", Ui.BLUE);
            google.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    googleLogin();
                }
            });
            card.addView(google);
        }
    }

    private void googleLogin() {
        GoogleAuth.signIn(this, new GoogleAuth.Listener() {
            @Override
            public void onToken(final String idToken) {
                action("Masuk dengan Google...", new Async.Task<Repo.GoogleLogin>() {
                    @Override
                    public Repo.GoogleLogin run() throws Exception {
                        return repo.loginWithGoogle(idToken);
                    }
                }, new Async.Callback<Repo.GoogleLogin>() {
                    @Override
                    public void onResult(Repo.GoogleLogin g) {
                        if (g.user != null) {
                            Ui.toast(LoginActivity.this, "Selamat datang, " + g.user.name);
                            Nav.home(LoginActivity.this, g.user.role);
                        } else {
                            // akun Google baru: lengkapi profil
                            Intent i = new Intent(LoginActivity.this, RegisterActivity.class);
                            i.putExtra("googleUid", g.uid);
                            i.putExtra("googleName", g.name);
                            i.putExtra("googleEmail", g.email);
                            startActivity(i);
                        }
                    }
                });
            }

            @Override
            public void onError(String message) {
                Ui.toast(LoginActivity.this, message);
            }
        });
    }

    private void forgotPassword() {
        Ui.prompt(this, "Lupa kata sandi", "Nomor HP akun Anda", phone.getText().toString(),
                android.text.InputType.TYPE_CLASS_PHONE, new Ui.TextCallback() {
                    @Override
                    public void onText(final String ph) {
                        if (ph.length() < 9) return;
                        action("Memeriksa...", new Async.Task<String>() {
                            @Override
                            public String run() throws Exception {
                                String masked = repo.forgotPassword(ph);
                                return masked == null ? "" : masked;
                            }
                        }, new Async.Callback<String>() {
                            @Override
                            public void onResult(String masked) {
                                if (masked.length() > 0) {
                                    new android.app.AlertDialog.Builder(LoginActivity.this)
                                            .setTitle("Cek email Anda")
                                            .setMessage("Link untuk membuat kata sandi baru sudah dikirim ke "
                                                    + masked + ". Buka emailnya, buat sandi baru, lalu masuk lagi.")
                                            .setPositiveButton("OK", null).show();
                                } else {
                                    recoveryDialog(ph);
                                }
                            }
                        });
                    }
                });
    }

    /** Akun tanpa email: ajukan pemulihan ke admin. */
    private void recoveryDialog(final String ph) {
        LinearLayout box = Ui.vbox(this);
        box.setPadding(Ui.dp(this, 20), Ui.dp(this, 8), Ui.dp(this, 20), 0);
        box.addView(Ui.body(this, "Akun ini belum punya email pemulihan. Ajukan pemulihan ke admin: "
                + "buat kata sandi baru, lalu admin akan memverifikasi Anda lewat WhatsApp."));
        box.addView(Ui.label(this, "Nama sesuai akun"));
        final EditText nm = Ui.edit(this, "Nama Anda");
        box.addView(nm);
        box.addView(Ui.label(this, "Kata sandi baru"));
        final EditText pw = Ui.password(this, "Minimal 6 karakter");
        box.addView(pw);
        new android.app.AlertDialog.Builder(this)
                .setTitle("Pulihkan akun")
                .setView(box)
                .setPositiveButton("Ajukan", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int w) {
                        final String n = nm.getText().toString();
                        final String p = pw.getText().toString();
                        action("Mengirim...", new Async.Task<Boolean>() {
                            @Override
                            public Boolean run() throws Exception {
                                repo.requestRecovery(ph, n, p);
                                return true;
                            }
                        }, new Async.Callback<Boolean>() {
                            @Override
                            public void onResult(Boolean r) {
                                new android.app.AlertDialog.Builder(LoginActivity.this)
                                        .setTitle("Permintaan terkirim")
                                        .setMessage("Hubungi admin lewat WhatsApp untuk verifikasi. Setelah "
                                                + "disetujui, masuk dengan nomor HP & kata sandi baru.")
                                        .setPositiveButton("Chat Admin", new android.content.DialogInterface.OnClickListener() {
                                            @Override
                                            public void onClick(android.content.DialogInterface d, int w) {
                                                Ui.whatsapp(LoginActivity.this, Config.CS_PHONE,
                                                        "Halo admin, saya " + n + " (" + ph
                                                                + ") mengajukan pemulihan akun AntarMakan.");
                                            }
                                        })
                                        .setNegativeButton("Tutup", null).show();
                            }
                        });
                    }
                })
                .setNegativeButton("Batal", null)
                .show();
    }

    private void doLogin() {
        final String ph = phone.getText().toString();
        final String pw = pass.getText().toString();
        if (ph.trim().length() == 0 || pw.length() == 0) {
            Ui.toast(this, "Isi nomor HP dan kata sandi");
            return;
        }
        action("Masuk...", new Async.Task<User>() {
            @Override
            public User run() throws Exception {
                return repo.login(ph, pw);
            }
        }, new Async.Callback<User>() {
            @Override
            public void onResult(User u) {
                Ui.toast(LoginActivity.this, "Selamat datang, " + u.name);
                Nav.home(LoginActivity.this, u.role);
            }
        });
    }
}
