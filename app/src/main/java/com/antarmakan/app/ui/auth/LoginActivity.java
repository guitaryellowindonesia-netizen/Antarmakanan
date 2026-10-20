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
