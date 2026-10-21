package com.antarmakan.app.data;

/** Sumber token login untuk akses database. */
public interface TokenSource {
    String idToken() throws Exception;
}
