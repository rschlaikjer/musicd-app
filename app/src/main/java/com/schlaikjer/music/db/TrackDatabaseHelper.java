package com.schlaikjer.music.db;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.provider.BaseColumns;
import android.util.SparseArray;

import com.schlaikjer.music.exception.MissingMigrationException;

public class TrackDatabaseHelper extends SQLiteOpenHelper {

    private static final String DB_NAME = "tracks";
    private static int DB_VERSION = 3;

    private static SparseArray<Migration> migrations = new SparseArray<>();

    static {
        migrations.put(1, new Migration() {
            @Override
            public void apply(SQLiteDatabase database) {
                // Create the main tracks table
                database.execSQL("CREATE TABLE " + TracksTable.TABLE_NAME + " (" +
                        TracksTable.COLUMN_RAW_PATH + " TEXT," +
                        TracksTable.COLUMN_PARENT_PATH + " TEXT," +
                        TracksTable.COLUMN_CHECKSUM + " BLOB PRIMARY KEY," +
                        TracksTable.COLUMN_TAG_TITLE + " TEXT," +
                        TracksTable.COLUMN_TAG_ARTIST + " TEXT," +
                        TracksTable.COLUMN_TAG_ALBUM + " TEXT," +
                        TracksTable.COLUMN_TAG_YEAR + " INTEGER," +
                        TracksTable.COLUMN_TAG_COMMENT + " TEXT," +
                        TracksTable.COLUMN_TAG_TRACK + " INTEGER," +
                        TracksTable.COLUMN_TAG_GENRE + " TEXT," +
                        "CONSTRAINT " + TracksTable.COLUMN_CHECKSUM + "_unique UNIQUE (" + TracksTable.COLUMN_CHECKSUM + ") ON CONFLICT REPLACE " +
                        " ) ");

                // Add indexes on artist / album since we'll be grouping on these a lot
                database.execSQL("CREATE INDEX " + TracksTable.COLUMN_TAG_ARTIST + "_index ON " + TracksTable.TABLE_NAME + "(" + TracksTable.COLUMN_TAG_ARTIST + ")");
                database.execSQL("CREATE INDEX " + TracksTable.COLUMN_TAG_ALBUM + "_index ON " + TracksTable.TABLE_NAME + "(" + TracksTable.COLUMN_TAG_ALBUM + ")");
            }
        });

        migrations.put(2, new Migration() {
            @Override
            public void apply(SQLiteDatabase database) {
                // Create the main images table
                database.execSQL("CREATE TABLE " + ImagesTable.TABLE_NAME + " (" +
                        ImagesTable.COLUMN_RAW_PATH + " TEXT," +
                        ImagesTable.COLUMN_PARENT_PATH + " TEXT," +
                        ImagesTable.COLUMN_CHECKSUM + " BLOB PRIMARY KEY," +
                        "CONSTRAINT " + ImagesTable.COLUMN_CHECKSUM + "_unique UNIQUE (" + ImagesTable.COLUMN_CHECKSUM + ") ON CONFLICT REPLACE " +
                        " ) ");
            }
        });

        migrations.put(3, new Migration() {
            @Override
            public void apply(SQLiteDatabase database) {
                database.execSQL("CREATE TABLE " + PlaylistTable.TABLE_NAME + " (" +
                        PlaylistTable.COLUMN_INDEX + " INT," +
                        PlaylistTable.COLUMN_CHECKSUM + " BLOB" +
                        " ) ");
            }
        });
    }


    public TrackDatabaseHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        onUpgrade(db, 0, DB_VERSION);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        for (int i = oldVersion + 1; i <= newVersion; i++) {
            Migration migration;
            if ((migration = migrations.get(i)) != null) {
                migration.apply(db);
            } else {
                throw new MissingMigrationException(DB_NAME, i);
            }
        }
    }

    public static class TracksTable implements BaseColumns {

        public static final String TABLE_NAME = "track";

        public static final String COLUMN_RAW_PATH = "raw_path";
        public static final String COLUMN_PARENT_PATH = "parent_path";
        public static final String COLUMN_CHECKSUM = "checksum";
        public static final String COLUMN_TAG_TITLE = "tag_title";
        public static final String COLUMN_TAG_ARTIST = "tag_artist";
        public static final String COLUMN_TAG_ALBUM = "tag_album";
        public static final String COLUMN_TAG_YEAR = "tag_year";
        public static final String COLUMN_TAG_COMMENT = "tag_comment";
        public static final String COLUMN_TAG_TRACK = "tag_track";
        public static final String COLUMN_TAG_GENRE = "tag_genre";

        public static String[] projection() {
            return new String[]{
                    COLUMN_RAW_PATH, COLUMN_PARENT_PATH, COLUMN_CHECKSUM, COLUMN_TAG_TITLE, COLUMN_TAG_ARTIST, COLUMN_TAG_ALBUM, COLUMN_TAG_YEAR, COLUMN_TAG_COMMENT, COLUMN_TAG_TRACK, COLUMN_TAG_GENRE
            };
        }

    }


    public static class ImagesTable implements BaseColumns {

        public static final String TABLE_NAME = "image";

        public static final String COLUMN_RAW_PATH = "raw_path";
        public static final String COLUMN_PARENT_PATH = "parent_path";
        public static final String COLUMN_CHECKSUM = "checksum";

        public static String[] projection() {
            return new String[]{
                    COLUMN_RAW_PATH, COLUMN_PARENT_PATH, COLUMN_CHECKSUM,
            };
        }

    }

    public static class PlaylistTable implements BaseColumns {

        public static final String TABLE_NAME = "playlist";

        public static final String COLUMN_INDEX = "idx";
        public static final String COLUMN_CHECKSUM = "checksum";

        public static String[] projection() {
            return new String[]{
                    COLUMN_INDEX, COLUMN_CHECKSUM,
            };
        }

    }


}
