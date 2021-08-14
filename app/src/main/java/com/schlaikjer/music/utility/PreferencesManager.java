package com.schlaikjer.music.utility;

import android.content.Context;
import android.content.SharedPreferences;

import com.schlaikjer.music.BuildConfig;

public class PreferencesManager {

    public static class Keys {
        public static final String LIMIT_CACHE_SIZE = "LIMIT_CACHE_SIZE";
        public static final String MAX_CACHE_SIZE_BYTES = "MAX_CACHE_SIZE_BYTES";
    }

    public static SharedPreferences getSharedPreferences(Context context) {
        return context.getSharedPreferences(BuildConfig.APPLICATION_ID, Context.MODE_PRIVATE);
    }

}
