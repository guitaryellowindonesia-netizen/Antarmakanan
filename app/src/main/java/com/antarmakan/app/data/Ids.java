package com.antarmakan.app.data;

import java.security.MessageDigest;
import java.security.SecureRandom;

public final class Ids {
    private Ids() {}

    private static final String CHARS = "0123456789abcdefghijklmnopqrstuvwxyz";
    private static final SecureRandom RND = new SecureRandom();

    /** Id unik berurutan waktu, aman dipakai sebagai key Firebase. */
    public static String newId() {
        StringBuilder sb = new StringBuilder(Long.toString(System.currentTimeMillis(), 36));
        for (int i = 0; i < 8; i++) sb.append(CHARS.charAt(RND.nextInt(CHARS.length())));
        return sb.toString();
    }

    /** Kode pesanan pendek, mis. AM-K3F9Q2 */
    public static String orderCode() {
        String c = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder sb = new StringBuilder("AM-");
        for (int i = 0; i < 6; i++) sb.append(c.charAt(RND.nextInt(c.length())));
        return sb.toString();
    }

    public static String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] b = md.digest(s.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte x : b) sb.append(String.format("%02x", x));
            return sb.toString();
        } catch (Exception e) {
            return s;
        }
    }
}
