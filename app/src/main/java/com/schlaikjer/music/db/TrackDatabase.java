package com.schlaikjer.music.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteCursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import com.schlaikjer.msgs.TrackOuterClass;
import com.schlaikjer.music.model.Album;
import com.schlaikjer.music.model.CacheEntry;
import com.schlaikjer.music.model.Track;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TrackDatabase {

    private static final String TAG = TrackDatabase.class.getSimpleName();

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

    public void setDatabase(TrackOuterClass.MusicDatabase db) {
        SQLiteDatabase database = helper.getWritableDatabase();
        database.beginTransaction();

        // Delete old track/image info
        database.delete(TrackDatabaseHelper.TracksTable.TABLE_NAME, null, null);
        database.delete(TrackDatabaseHelper.ImagesTable.TABLE_NAME, null, null);

        // Insert the new data set
        for (TrackOuterClass.Track track : db.getTracksList()) {
            addTrack(database, track);
        }
        for (TrackOuterClass.Image image : db.getImagesList()) {
            addImage(database, image);
        }

        database.setTransactionSuccessful();
        database.endTransaction();

        Log.d(TAG, "Database update completed with " + db.getTracksCount() + " tracks, " + db.getImagesCount() + " images.");
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

    private Track parseTrack(Cursor c) {
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

        return track;
    }

    private List<Track> parseTracks(Cursor c) {
        List<Track> trackList = new ArrayList<>();
        c.moveToFirst();
        while (!c.isAfterLast()) {
            trackList.add(parseTrack(c));
            c.moveToNext();
        }
        c.close();
        return trackList;
    }

    public Track getTrack(byte[] checksum) {
        SQLiteDatabase database = helper.getReadableDatabase();
        Cursor c = database.rawQueryWithFactory((db, masterQuery, editTable, query) -> {
            query.bindBlob(1, checksum);
            return new SQLiteCursor(masterQuery, editTable, query);
        }, "SELECT * FROM " + TrackDatabaseHelper.TracksTable.TABLE_NAME + " WHERE " + TrackDatabaseHelper.TracksTable.COLUMN_CHECKSUM + " = $1", null, TrackDatabaseHelper.TracksTable.TABLE_NAME);

        c.moveToFirst();
        while (!c.isAfterLast()) {
            Track track = parseTrack(c);
            c.close();
            return track;
        }

        c.close();
        return null;
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

        return parseTracks(c);
    }

    public List<Track> getRandomTracks(int count) {
        SQLiteDatabase database = helper.getReadableDatabase();
        Cursor c = database.query(
                TrackDatabaseHelper.TracksTable.TABLE_NAME,
                TrackDatabaseHelper.TracksTable.projection(), null, // No select
                null, // No select args
                null, // Group
                null, // Having
                "RANDOM()", // Order
                String.valueOf(count)
        );

        return parseTracks(c);
    }

    public List<Track> getTracksForParentPath(String parent_path) {
        SQLiteDatabase database = helper.getReadableDatabase();
        Cursor c = database.query(
                TrackDatabaseHelper.TracksTable.TABLE_NAME,
                TrackDatabaseHelper.TracksTable.projection(), TrackDatabaseHelper.TracksTable.COLUMN_PARENT_PATH + " = $1", // No select
                new String[]{parent_path}, // No select args
                null, // Group
                null, // Having
                TrackDatabaseHelper.TracksTable.COLUMN_TAG_ALBUM + ", " + TrackDatabaseHelper.TracksTable.COLUMN_TAG_TRACK // Order
        );

        return parseTracks(c);
    }

    public List<Track> getTracksForParentPathRecursive(String parent_path) {
        SQLiteDatabase database = helper.getReadableDatabase();
        Cursor c = database.query(
                TrackDatabaseHelper.TracksTable.TABLE_NAME,
                TrackDatabaseHelper.TracksTable.projection(), TrackDatabaseHelper.TracksTable.COLUMN_PARENT_PATH + " LIKE $1", // No select
                new String[]{parent_path + '%'}, // No select args
                null, // Group
                null, // Having
                TrackDatabaseHelper.TracksTable.COLUMN_TAG_ALBUM + ", " + TrackDatabaseHelper.TracksTable.COLUMN_TAG_TRACK // Order
        );

        return parseTracks(c);
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
                new String[]{TrackDatabaseHelper.ImagesTable.COLUMN_CHECKSUM}, TrackDatabaseHelper.ImagesTable.COLUMN_PARENT_PATH + " LIKE $1", // Select
                new String[]{path + "%"}, // No select args
                null, // Group
                null, // Having
                "LENGTH(" + TrackDatabaseHelper.ImagesTable.COLUMN_RAW_PATH + ") - LENGTH(REPLACE(" + TrackDatabaseHelper.ImagesTable.COLUMN_RAW_PATH + ", '/', '')) ASC" // Order
        );

        List<byte[]> checksums = new ArrayList<>();
        c.moveToFirst();
        while (!c.isAfterLast()) {
            checksums.add(c.getBlob(c.getColumnIndex(TrackDatabaseHelper.ImagesTable.COLUMN_CHECKSUM)));
            c.moveToNext();
        }
        c.close();

        return checksums;
    }

    private List<Album> parseAlbumList(Cursor c) {
        // Pull intom models
        List<Album> albums = new ArrayList<>();
        c.moveToFirst();
        while (!c.isAfterLast()) {
            Album album = new Album();
            album.parent_path = c.getString(c.getColumnIndex(TrackDatabaseHelper.TracksTable.COLUMN_PARENT_PATH));
            album.artist = c.getString(c.getColumnIndex(TrackDatabaseHelper.TracksTable.COLUMN_TAG_ARTIST));
            album.name = c.getString(c.getColumnIndex(TrackDatabaseHelper.TracksTable.COLUMN_TAG_ALBUM));

            // For each album, fetch the set of content addresses for images that could be used as covers
            album.coverImageChecksums = getImageChecksumsForParentPath(album.parent_path);

            albums.add(album);
            c.moveToNext();
        }
        c.close();
        return albums;
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

        return parseAlbumList(c);
    }

    public List<Album> getRandomAlbums(int count) {
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
                "RANDOM()", // Order
                String.valueOf(count) // Limit
        );

        return parseAlbumList(c);
    }

    public List<Album> getDirectoryAlbums(String basedir) {
        // Select distinct directories from the common basedir
        SQLiteDatabase database = helper.getReadableDatabase();
        Cursor c = database.query(
                true, // distinct
                TrackDatabaseHelper.TracksTable.TABLE_NAME,
                new String[]{
                        TrackDatabaseHelper.TracksTable.COLUMN_PARENT_PATH,
                },
                // Select tracks that are in subdirectories of the basedir but not the basedir itself
                TrackDatabaseHelper.TracksTable.COLUMN_PARENT_PATH + " LIKE $1 AND " + TrackDatabaseHelper.TracksTable.COLUMN_PARENT_PATH + " != $2",
                new String[]{basedir + "%", basedir},
                TrackDatabaseHelper.TracksTable.COLUMN_PARENT_PATH, // Group
                null, // Having
                null, // Order
                null // Limit
        );

        Set<String> prefixSet = new HashSet<>();
        List<Album> albums = new ArrayList<>();
        c.moveToFirst();
        while (!c.isAfterLast()) {
            // Split the parent path on /
            String parentPath = c.getString(c.getColumnIndex(TrackDatabaseHelper.TracksTable.COLUMN_PARENT_PATH));
            String childPath = parentPath.substring(basedir.length());
            String[] childPathFragments = childPath.split("/");
            // baseChild is first non-empty path fragment
            String baseChild = "";
            for (int i = 0; i < childPathFragments.length; i++) {
                if (childPathFragments[i].length() > 0) {
                    baseChild = childPathFragments[i];
                    break;
                }
            }
            if (prefixSet.contains(baseChild)) {
                c.moveToNext();
                continue;
            }

            prefixSet.add(baseChild);
            Album album = new Album();
            if (basedir.length() > 0) {
                album.parent_path = basedir + "/" + baseChild;
            } else {
                album.parent_path = baseChild;
            }
            album.artist = baseChild;
            album.name = baseChild;

            // For each album, fetch the set of content addresses for images that could be used as covers
            album.coverImageChecksums = getImageChecksumsForParentPath(album.parent_path);

            albums.add(album);
            c.moveToNext();
        }
        c.close();

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
        c.close();

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

    public void addCacheEntry(CacheEntry entry) {
        SQLiteDatabase database = helper.getWritableDatabase();
        ContentValues contentValues = new ContentValues();
        contentValues.put(TrackDatabaseHelper.CacheTable.COLUMN_CHECKSUM, entry.checksum);
        contentValues.put(TrackDatabaseHelper.CacheTable.COLUMN_PATH, entry.path);
        contentValues.put(TrackDatabaseHelper.CacheTable.COLUMN_SIZE_BYTES, entry.sizeBytes);
        contentValues.put(TrackDatabaseHelper.CacheTable.COLUMN_ACCESS_COUNT, entry.accessCount);
        contentValues.put(TrackDatabaseHelper.CacheTable.COLUMN_LAST_ACCESS_TIME, entry.lastAccessTime);
        database.insert(TrackDatabaseHelper.CacheTable.TABLE_NAME, null, contentValues);
    }

    public void accessCacheEntry(byte[] hash) {
        SQLiteDatabase database = helper.getWritableDatabase();
        database.execSQL("UPDATE " + TrackDatabaseHelper.CacheTable.TABLE_NAME + " SET " + TrackDatabaseHelper.CacheTable.COLUMN_ACCESS_COUNT + " = " + TrackDatabaseHelper.CacheTable.COLUMN_ACCESS_COUNT + " + 1, " + TrackDatabaseHelper.CacheTable.COLUMN_LAST_ACCESS_TIME + " = $1 WHERE " + TrackDatabaseHelper.CacheTable.COLUMN_CHECKSUM + " = $2", new Object[]{System.currentTimeMillis(), hash});
    }

    public long getCacheSize() {
        // Select all cache entries, order by least recently accessed
        SQLiteDatabase database = helper.getReadableDatabase();
        Cursor c = database.rawQuery("SELECT SUM(" + TrackDatabaseHelper.CacheTable.COLUMN_SIZE_BYTES + ") FROM " + TrackDatabaseHelper.CacheTable.TABLE_NAME, new String[]{});
        c.moveToFirst();
        long size = c.getLong(0);
        c.close();
        return size;
    }

    public void deleteCacheEntry(byte[] checksum) {
        SQLiteDatabase database = helper.getWritableDatabase();
        database.execSQL("DELETE FROM " +
                TrackDatabaseHelper.CacheTable.TABLE_NAME + " WHERE " + TrackDatabaseHelper.CacheTable.COLUMN_CHECKSUM + " = $1", new Object[]{checksum});
    }

    public List<CacheEntry> getCacheEntries() {
        // Select all cache entries, order by least recently accessed
        SQLiteDatabase database = helper.getReadableDatabase();
        Cursor c = database.query(
                false, // distinct
                TrackDatabaseHelper.CacheTable.TABLE_NAME,
                TrackDatabaseHelper.CacheTable.projection(),
                null, // No select
                null, // No select args
                null, // Group
                null, // Having
                TrackDatabaseHelper.CacheTable.COLUMN_LAST_ACCESS_TIME + " ASC", // Order
                null // Limit
        );

        List<CacheEntry> entries = new ArrayList<>();
        c.moveToFirst();
        while (!c.isAfterLast()) {
            CacheEntry entry = new CacheEntry();
            entry.checksum = c.getBlob(c.getColumnIndex(TrackDatabaseHelper.CacheTable.COLUMN_CHECKSUM));
            entry.path = c.getString(c.getColumnIndex(TrackDatabaseHelper.CacheTable.COLUMN_PATH));
            entry.sizeBytes = c.getLong(c.getColumnIndex(TrackDatabaseHelper.CacheTable.COLUMN_SIZE_BYTES));
            entry.accessCount = c.getLong(c.getColumnIndex(TrackDatabaseHelper.CacheTable.COLUMN_ACCESS_COUNT));
            entry.lastAccessTime = c.getLong(c.getColumnIndex(TrackDatabaseHelper.CacheTable.COLUMN_LAST_ACCESS_TIME));
            entries.add(entry);
            c.moveToNext();
        }
        c.close();

        return entries;
    }


}