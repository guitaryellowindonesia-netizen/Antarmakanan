package com.antarmakan.app.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Penyimpanan offline di SQLite (mode demo). */
public class LocalStore extends SQLiteOpenHelper implements Store {

    public LocalStore(Context c) {
        super(c.getApplicationContext(), "antarmakan.db", null, 1);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE docs (col TEXT NOT NULL, id TEXT NOT NULL, json TEXT NOT NULL, "
                + "PRIMARY KEY (col, id))");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldV, int newV) {
    }

    @Override
    public synchronized JSONObject get(String col, String id) throws Exception {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT json FROM docs WHERE col=? AND id=?", new String[]{col, id});
        try {
            if (c.moveToFirst()) return new JSONObject(c.getString(0));
            return null;
        } finally {
            c.close();
        }
    }

    @Override
    public synchronized List<JSONObject> list(String col) throws Exception {
        List<JSONObject> out = new ArrayList<JSONObject>();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT json FROM docs WHERE col=? ORDER BY id", new String[]{col});
        try {
            while (c.moveToNext()) out.add(new JSONObject(c.getString(0)));
        } finally {
            c.close();
        }
        return out;
    }

    @Override
    public synchronized List<JSONObject> query(String col, String field, Object value) throws Exception {
        List<JSONObject> out = new ArrayList<JSONObject>();
        String want = String.valueOf(value);
        for (JSONObject o : list(col)) {
            Object v = o.opt(field);
            if (v != null && want.equals(String.valueOf(v))) out.add(o);
        }
        return out;
    }

    @Override
    public synchronized void put(String col, String id, JSONObject obj) throws Exception {
        ContentValues cv = new ContentValues();
        cv.put("col", col);
        cv.put("id", id);
        cv.put("json", obj.toString());
        getWritableDatabase().insertWithOnConflict("docs", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
    }

    @Override
    public synchronized void delete(String col, String id) throws Exception {
        getWritableDatabase().delete("docs", "col=? AND id=?", new String[]{col, id});
    }

    @Override
    public synchronized JSONObject transact(String col, String id, Updater updater) throws Exception {
        JSONObject cur = get(col, id);
        JSONObject next = updater.update(cur);
        if (next == null) {
            delete(col, id);
        } else {
            put(col, id, next);
        }
        return next;
    }
}
