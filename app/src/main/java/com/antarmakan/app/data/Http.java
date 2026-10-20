package com.antarmakan.app.data;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;

/** Klien HTTP sederhana tanpa library tambahan. */
public final class Http {
    private Http() {}

    public static class Response {
        public int code;
        public String body;
        public String etag;
    }

    public static Response request(String method, String url, String body,
                                   Map<String, String> headers) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        try {
            con.setConnectTimeout(15000);
            con.setReadTimeout(20000);
            if ("PATCH".equals(method)) {
                con.setRequestMethod("POST");
                con.setRequestProperty("X-HTTP-Method-Override", "PATCH");
            } else {
                con.setRequestMethod(method);
            }
            if (headers != null) {
                for (Map.Entry<String, String> h : headers.entrySet()) {
                    con.setRequestProperty(h.getKey(), h.getValue());
                }
            }
            if (body != null) {
                con.setDoOutput(true);
                if (con.getRequestProperty("Content-Type") == null) {
                    con.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                }
                OutputStream os = con.getOutputStream();
                os.write(body.getBytes("UTF-8"));
                os.close();
            }
            Response r = new Response();
            r.code = con.getResponseCode();
            r.etag = con.getHeaderField("ETag");
            InputStream is = r.code >= 400 ? con.getErrorStream() : con.getInputStream();
            r.body = is == null ? "" : readAll(is);
            return r;
        } catch (java.net.UnknownHostException e) {
            throw new AppException("Tidak ada koneksi internet");
        } catch (java.net.SocketTimeoutException e) {
            throw new AppException("Koneksi lambat, coba lagi");
        } finally {
            con.disconnect();
        }
    }

    private static String readAll(InputStream is) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
        is.close();
        return bos.toString("UTF-8");
    }
}
