package com.antarmakan.app.data;

/** Server menolak perubahan (Rules Firebase). */
public class PermissionDeniedException extends AppException {
    public PermissionDeniedException(String message) {
        super(message);
    }
}
