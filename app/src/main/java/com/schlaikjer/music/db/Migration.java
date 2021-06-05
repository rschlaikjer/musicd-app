package com.schlaikjer.music.db;

import android.database.sqlite.SQLiteDatabase;

public interface Migration {

    void apply(SQLiteDatabase database);

}
