package com.schlaikjer.music.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.schlaikjer.music.model.Track;

import java.util.ArrayList;
import java.util.List;

public class TrackDatabase {

    private static volatile TrackDatabase instance;

    private TrackDatabaseHelper helper;

    public static TrackDatabase getInstance(Context context) {
        if (instance == null) {
            synchronized (TrackDatabase.class) {
                if (instance == null) {
                    instance = new TrackDatabase(context);
                }
            }
        }
        return instance;
    }

    private TrackDatabase(Context context) {
        this.helper = new TrackDatabaseHelper(context);
    }

    public void addTrack(Track track) {
        addTrack(helper.getWritableDatabase(), track);
    }

    public void addTrack(Iterable<Track> records) {
        SQLiteDatabase database = helper.getWritableDatabase();
        for (Track record : records) {
            addTrack(database, record);
        }
    }

    private void addTrack(SQLiteDatabase database, Track record) {
        ContentValues contentValues = new ContentValues();

        contentValues.put(TrackDatabaseHelper.TracksTable.COLUMN_RAW_PATH, record.raw_path);
        contentValues.put(TrackDatabaseHelper.TracksTable.COLUMN_PARENT_PATH, record.parent_path);
        contentValues.put(TrackDatabaseHelper.TracksTable.COLUMN_CHECKSUM, record.checksum);
        contentValues.put(TrackDatabaseHelper.TracksTable.COLUMN_TAG_TITLE, record.tag_title);
        contentValues.put(TrackDatabaseHelper.TracksTable.COLUMN_TAG_ARTIST, record.tag_artist);
        contentValues.put(TrackDatabaseHelper.TracksTable.COLUMN_TAG_ALBUM, record.tag_album);
        contentValues.put(TrackDatabaseHelper.TracksTable.COLUMN_TAG_YEAR, record.tag_year);
        contentValues.put(TrackDatabaseHelper.TracksTable.COLUMN_TAG_COMMENT, record.tag_comment);
        contentValues.put(TrackDatabaseHelper.TracksTable.COLUMN_TAG_TRACK, record.tag_track);
        contentValues.put(TrackDatabaseHelper.TracksTable.COLUMN_TAG_GENRE, record.tag_genre);

        database.insert(TrackDatabaseHelper.TracksTable.TABLE_NAME, null, contentValues);
    }

    public List<Track> getAllTracks() {
        SQLiteDatabase database = helper.getReadableDatabase();
        Cursor c = database.query(
                TrackDatabaseHelper.TracksTable.TABLE_NAME,
                TrackDatabaseHelper.TracksTable.projection(), null, // No select
                null, // No select args
                null, // Group
                null, // Having
                null // Order
        );

        List<Track> transactionList = new ArrayList<>();
        c.moveToFirst();
        while (!c.isAfterLast()) {
            Track track = new Track();
            track.raw_path = c.getString(c.getColumnIndex(TrackDatabaseHelper.TracksTable.COLUMN_RAW_PATH));
            track.parent_path = c.getString(c.getColumnIndex(TrackDatabaseHelper.TracksTable.COLUMN_PARENT_PATH));
            track.checksum = c.getString(c.getColumnIndex(TrackDatabaseHelper.TracksTable.COLUMN_CHECKSUM));
            track.tag_title = c.getString(c.getColumnIndex(TrackDatabaseHelper.TracksTable.COLUMN_TAG_TITLE));
            track.tag_artist = c.getString(c.getColumnIndex(TrackDatabaseHelper.TracksTable.COLUMN_TAG_ARTIST));
            track.tag_album = c.getString(c.getColumnIndex(TrackDatabaseHelper.TracksTable.COLUMN_TAG_ALBUM));
            track.tag_year = c.getInt(c.getColumnIndex(TrackDatabaseHelper.TracksTable.COLUMN_TAG_YEAR));
            track.tag_comment = c.getString(c.getColumnIndex(TrackDatabaseHelper.TracksTable.COLUMN_TAG_COMMENT));
            track.tag_track = c.getInt(c.getColumnIndex(TrackDatabaseHelper.TracksTable.COLUMN_TAG_TRACK));
            track.tag_genre = c.getString(c.getColumnIndex(TrackDatabaseHelper.TracksTable.COLUMN_TAG_GENRE));
            transactionList.add(track);
            c.moveToNext();
        }
        return transactionList;
    }
}