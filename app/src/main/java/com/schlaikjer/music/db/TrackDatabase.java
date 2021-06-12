package com.schlaikjer.music.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.schlaikjer.msgs.TrackOuterClass;
import com.schlaikjer.music.model.Album;
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

    public void addTracks(Iterable<Track> records) {
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

    public void addPbTracks(Iterable<TrackOuterClass.Track> records) {
        SQLiteDatabase database = helper.getWritableDatabase();
        for (TrackOuterClass.Track record : records) {
            addTrack(database, record);
        }
    }

    private void addTrack(SQLiteDatabase database, TrackOuterClass.Track record) {
        ContentValues contentValues = new ContentValues();

        contentValues.put(TrackDatabaseHelper.TracksTable.COLUMN_RAW_PATH, record.getRawPath());
        contentValues.put(TrackDatabaseHelper.TracksTable.COLUMN_PARENT_PATH, record.getParentPath());
        contentValues.put(TrackDatabaseHelper.TracksTable.COLUMN_CHECKSUM, record.getChecksum().toByteArray());
        contentValues.put(TrackDatabaseHelper.TracksTable.COLUMN_TAG_TITLE, record.getTagTitle());
        contentValues.put(TrackDatabaseHelper.TracksTable.COLUMN_TAG_ARTIST, record.getTagArtist());
        contentValues.put(TrackDatabaseHelper.TracksTable.COLUMN_TAG_ALBUM, record.getTagAlbum());
        contentValues.put(TrackDatabaseHelper.TracksTable.COLUMN_TAG_YEAR, record.getTagYear());
        contentValues.put(TrackDatabaseHelper.TracksTable.COLUMN_TAG_COMMENT, record.getTagComment());
        contentValues.put(TrackDatabaseHelper.TracksTable.COLUMN_TAG_TRACK, record.getTagTrack());
        contentValues.put(TrackDatabaseHelper.TracksTable.COLUMN_TAG_GENRE, record.getTagGenre());

        database.insertWithOnConflict(TrackDatabaseHelper.TracksTable.TABLE_NAME, null, contentValues, SQLiteDatabase.CONFLICT_REPLACE);
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
            track.checksum = c.getBlob(c.getColumnIndex(TrackDatabaseHelper.TracksTable.COLUMN_CHECKSUM));
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

    public void addPbImages(Iterable<TrackOuterClass.Image> records) {
        SQLiteDatabase database = helper.getWritableDatabase();
        for (TrackOuterClass.Image record : records) {
            addImage(database, record);
        }
    }

    private void addImage(SQLiteDatabase database, TrackOuterClass.Image record) {
        ContentValues contentValues = new ContentValues();

        contentValues.put(TrackDatabaseHelper.ImagesTable.COLUMN_RAW_PATH, record.getRawPath());
        contentValues.put(TrackDatabaseHelper.ImagesTable.COLUMN_PARENT_PATH, record.getParentPath());
        contentValues.put(TrackDatabaseHelper.ImagesTable.COLUMN_CHECKSUM, record.getChecksum().toByteArray());

        database.insertWithOnConflict(TrackDatabaseHelper.ImagesTable.TABLE_NAME, null, contentValues, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public List<byte[]> getImageChecksumsForParentPath(String path) {
        SQLiteDatabase database = helper.getReadableDatabase();
        Cursor c = database.query(
                TrackDatabaseHelper.ImagesTable.TABLE_NAME,
                new String[]{TrackDatabaseHelper.ImagesTable.COLUMN_CHECKSUM}, TrackDatabaseHelper.ImagesTable.COLUMN_PARENT_PATH + " = $1", // Select
                new String[]{path}, // No select args
                null, // Group
                null, // Having
                null // Order
        );

        List<byte[]> checksums = new ArrayList<>();
        c.moveToFirst();
        while (!c.isAfterLast()) {
            checksums.add(c.getBlob(c.getColumnIndex(TrackDatabaseHelper.ImagesTable.COLUMN_CHECKSUM)));
            c.moveToNext();
        }

        return checksums;
    }

    public List<Album> getAlbums() {
        // Select distinct album names
        SQLiteDatabase database = helper.getReadableDatabase();
        Cursor c = database.query(
                true, // distinct
                TrackDatabaseHelper.TracksTable.TABLE_NAME,
                new String[]{
                        TrackDatabaseHelper.TracksTable.COLUMN_PARENT_PATH,
                        TrackDatabaseHelper.TracksTable.COLUMN_TAG_ALBUM,
                        TrackDatabaseHelper.TracksTable.COLUMN_TAG_ARTIST,
                        TrackDatabaseHelper.TracksTable.COLUMN_TAG_YEAR,
                },
                null, // No select
                null, // No select args
                TrackDatabaseHelper.TracksTable.COLUMN_TAG_ALBUM + ", " +
                        TrackDatabaseHelper.TracksTable.COLUMN_TAG_ARTIST, // Group
                null, // Having
                null, // Order
                null // Limit
        );

        // Pull intom models
        List<Album> albums = new ArrayList<>();
        c.moveToFirst();
        while (!c.isAfterLast()) {
            Album album = new Album();
            album.parent_path = c.getString(c.getColumnIndex(TrackDatabaseHelper.TracksTable.COLUMN_PARENT_PATH));
            album.artist = c.getString(c.getColumnIndex(TrackDatabaseHelper.TracksTable.COLUMN_TAG_ARTIST));
            album.name = c.getString(c.getColumnIndex(TrackDatabaseHelper.TracksTable.COLUMN_TAG_ALBUM));
            album.artist = c.getString(c.getColumnIndex(TrackDatabaseHelper.TracksTable.COLUMN_TAG_ARTIST));

            // For each album, fetch the set of content addresses for images that could be used as covers
            album.coverImageChecksums = getImageChecksumsForParentPath(album.parent_path);

            albums.add(album);
            c.moveToNext();
        }

        return albums;
    }

    public List<Album> getDirectoryAlbums(String basedir) {
        // Select distinct album names
        SQLiteDatabase database = helper.getReadableDatabase();
        Cursor c = database.query(
                true, // distinct
                TrackDatabaseHelper.TracksTable.TABLE_NAME,
                new String[]{
                        TrackDatabaseHelper.TracksTable.COLUMN_PARENT_PATH,
                        TrackDatabaseHelper.TracksTable.COLUMN_TAG_ALBUM,
                        TrackDatabaseHelper.TracksTable.COLUMN_TAG_ARTIST,
                        TrackDatabaseHelper.TracksTable.COLUMN_TAG_YEAR,
                },
                null, // No select
                null, // No select args
                TrackDatabaseHelper.TracksTable.COLUMN_PARENT_PATH + ", " +
                        TrackDatabaseHelper.TracksTable.COLUMN_TAG_ARTIST, // Group
                null, // Having
                null, // Order
                null // Limit
        );

        List<Album> albums = new ArrayList<>();
        c.moveToFirst();
        while (!c.isAfterLast()) {
            Album album = new Album();
            album.parent_path = c.getString(c.getColumnIndex(TrackDatabaseHelper.TracksTable.COLUMN_PARENT_PATH));
            album.artist = c.getString(c.getColumnIndex(TrackDatabaseHelper.TracksTable.COLUMN_TAG_ARTIST));
            album.name = c.getString(c.getColumnIndex(TrackDatabaseHelper.TracksTable.COLUMN_TAG_ALBUM));
            album.artist = c.getString(c.getColumnIndex(TrackDatabaseHelper.TracksTable.COLUMN_TAG_ARTIST));

            // For each album, fetch the set of content addresses for images that could be used as covers
            album.coverImageChecksums = getImageChecksumsForParentPath(album.parent_path);

            albums.add(album);
            c.moveToNext();
        }

        return albums;
    }

    public List<byte[]> getPlaylist() {
        // Select distinct album names
        SQLiteDatabase database = helper.getReadableDatabase();
        Cursor c = database.query(
                true, // distinct
                TrackDatabaseHelper.PlaylistTable.TABLE_NAME,
                TrackDatabaseHelper.PlaylistTable.projection(),
                null, // No select
                null, // No select args
                null, // Group
                null, // Having
                TrackDatabaseHelper.PlaylistTable.COLUMN_INDEX + " ASC", // Order
                null // Limit
        );

        List<byte[]> tracks = new ArrayList<>();
        c.moveToFirst();
        while (!c.isAfterLast()) {
            tracks.add(c.getBlob(c.getColumnIndex(TrackDatabaseHelper.TracksTable.COLUMN_CHECKSUM)));
            c.moveToNext();
        }

        return tracks;
    }

    public void setPlaylist(List<byte[]> playlist) {
        SQLiteDatabase database = helper.getWritableDatabase();
        database.beginTransaction();
        database.delete(TrackDatabaseHelper.PlaylistTable.TABLE_NAME, null, null);
        for (int i = 0; i < playlist.size(); i++) {
            ContentValues contentValues = new ContentValues();
            contentValues.put(TrackDatabaseHelper.PlaylistTable.COLUMN_INDEX, i);
            contentValues.put(TrackDatabaseHelper.PlaylistTable.COLUMN_CHECKSUM, playlist.get(i));
            database.insert(TrackDatabaseHelper.PlaylistTable.TABLE_NAME, null, contentValues);
        }
        database.setTransactionSuccessful();
        database.endTransaction();
    }

}