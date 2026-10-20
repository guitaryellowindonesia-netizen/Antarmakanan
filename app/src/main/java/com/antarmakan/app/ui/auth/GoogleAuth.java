package com.antarmakan.app.ui.auth;

import android.app.Activity;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;

import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.GetCredentialCancellationException;
import androidx.credentials.exceptions.GetCredentialException;

import com.antarmakan.app.Config;
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;

import java.util.concurrent.Executor;

/** Menampilkan pilihan akun Google dan mengambil ID token-nya. */
public final class GoogleAuth {
    private GoogleAuth() {}

    public interface Listener {
        void onToken(String idToken);

        void onError(String message);
    }

    public static void signIn(Activity a, final Listener l) {
        final Handler main = new Handler(Looper.getMainLooper());
        Executor executor = new Executor() {
            @Override
            public void execute(Runnable r) {
                main.post(r);
            }
        };
        GetSignInWithGoogleOption option =
                new GetSignInWithGoogleOption.Builder(Config.GOOGLE_WEB_CLIENT_ID).build();
        GetCredentialRequest request = new GetCredentialRequest.Builder()
                .addCredentialOption(option)
                .build();
        CredentialManager cm = CredentialManager.create(a);
        cm.getCredentialAsync(a, request, new CancellationSignal(), executor,
                new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                    @Override
                    public void onResult(GetCredentialResponse response) {
                        Credential c = response.getCredential();
                        if (c instanceof CustomCredential
                                && GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL.equals(c.getType())) {
                            try {
                                GoogleIdTokenCredential g = GoogleIdTokenCredential.createFrom(c.getData());
                                l.onToken(g.getIdToken());
                            } catch (Exception e) {
                                l.onError("Gagal membaca akun Google");
                            }
                        } else {
                            l.onError("Jenis akun tidak didukung");
                        }
                    }

                    @Override
                    public void onError(GetCredentialException e) {
                        if (e instanceof GetCredentialCancellationException) {
                            l.onError("Login Google dibatalkan");
                        } else {
                            l.onError("Login Google gagal: " + e.getMessage());
                        }
                    }
                });
    }
}
