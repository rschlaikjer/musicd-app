package com.schlaikjer.music.utility;

import android.content.Context;
import android.content.SharedPreferences;

import com.schlaikjer.music.BuildConfig;

public class PreferencesManager {

    public static SharedPreferences getSharedPreferences(Context context) {
        return context.getSharedPreferences(BuildConfig.APPLICATION_ID, Context.MODE_PRIVATE);
    }

}
