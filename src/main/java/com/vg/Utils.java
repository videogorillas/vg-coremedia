package com.vg;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class Utils {
    private static final Gson GSON_TOSTRING = new GsonBuilder().create();

    public static void rethrow(Throwable e) {
        if (e instanceof RuntimeException) {
            throw (RuntimeException) e;
        } else {
            throw new RuntimeException(e);
        }
    }

    public static <T> T gsonClone(T t) {
        String json = GSON_TOSTRING.toJson(t);
        T fromJson = (T) GSON_TOSTRING.fromJson(json, t.getClass());
        return fromJson;
    }
}
