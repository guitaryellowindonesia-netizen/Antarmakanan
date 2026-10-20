package com.antarmakan.app.data;

/** Error dengan pesan yang aman ditampilkan ke pengguna. */
public class AppException extends RuntimeException {
    public AppException(String message) {
        super(message);
    }
}
