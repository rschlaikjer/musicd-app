package com.schlaikjer.music.utility;

import android.content.Context;
import android.util.Log;

import com.schlaikjer.music.db.TrackDatabase;
import com.schlaikjer.music.model.CacheEntry;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

public class StorageManager {

    public static final String TAG = StorageManager.class.getSimpleName();

    private static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();

    private static final long GIGABYTE = 1024 * 1024 * 1024;
    public static final long DEFAULT_MAX_CACHE_SIZE_BYTES = 4 * GIGABYTE;

    public static String bytesToHex(byte[] data) {
        char[] hexChars = new char[data.length * 2];
        for (int j = 0; j < data.length; j++) {
            int v = data[j] & 0xFF;
            hexChars[j * 2] = HEX_ARRAY[v >>> 4];
            hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        return new String(hexChars);
    }

    private static File getContentFile(Context context, byte[] checksum) {
        // Convert the checksum to hex for use as a filename
        String filename = bytesToHex(checksum);

        // Bucket filename after first byte for FS pressure
        String dirPrefix = filename.substring(0, 2);
        String dirPostfix = filename.substring(2);

        // Ensure filepath exists
        File dir = new File(context.getFilesDir(), dirPrefix);
        if (!dir.exists() && !dir.mkdirs()) {
            Log.e(TAG, "Failed to mkdirs for " + dir.getAbsolutePath());
            return null;
        }

        // Return file handle
        return new File(dir, dirPostfix);
    }

    public static void saveContentFile(Context context, byte[] checksum, byte[] data) {
        // Get file handle
        File outputFile = getContentFile(context, checksum);
        if (outputFile == null) {
            return;
        }

        // Write out the data
        try (FileOutputStream fos = new FileOutputStream(outputFile)) {
            fos.write(data);

            // Add a cache entry to the DB on success
            CacheEntry entry = new CacheEntry();
            entry.checksum = checksum;
            entry.path = getContentFilePath(context, checksum);
            entry.sizeBytes = data.length;
            TrackDatabase.getInstance(context).addCacheEntry(entry);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static boolean hasContentFile(Context context, byte[] checksum) {
        if (checksum == null) {
            return false;
        }

        // Get file handle
        File contentFile = getContentFile(context, checksum);
        if (contentFile == null) {
            return false;
        }

        return contentFile.exists();
    }

    public static String getContentFilePath(Context context, byte[] checksum) {
        if (checksum == null) {
            throw new RuntimeException();
        }

        // Increment access counter / time for this blob
        TrackDatabase.getInstance(context).accessCacheEntry(checksum);

        File file = getContentFile(context, checksum);
        if (file == null) {
            throw new RuntimeException();
        }

        return file.getAbsolutePath();
    }

    public static boolean deleteContentFile(Context context, byte[] checksum) {
        String path = getContentFilePath(context, checksum);
        File file = new File(path);
        return file.delete();
    }

    public static void gcContentCache(Context context) {
        // Iterate the files in the cache directory, order by age, and sum the total byte size.
        // If the total file size exceeds the max cache allowance, delete files starting with the
        // oldest until we are under budget.
        List<CacheEntry> entries = TrackDatabase.getInstance(context).getCacheEntries();

        // Get the total size
        long cacheSizeTotal = 0;
        for (CacheEntry entry : entries) {
            cacheSizeTotal += entry.sizeBytes;
        }
        Log.d(TAG, "Starting GC of content cache - initial size: " + cacheSizeTotal);

        // Remove cache entries until we are under the limit
        long maxCacheSize = PreferencesManager.getSharedPreferences(context).getLong(PreferencesManager.Keys.MAX_CACHE_SIZE_BYTES, DEFAULT_MAX_CACHE_SIZE_BYTES);
        while (cacheSizeTotal > maxCacheSize && entries.size() > 0) {
            // Get the next oldest entry
            CacheEntry entry = entries.remove(0);

            Log.d(TAG, "Deleting cache entry " + entry.path);
            if (entry.path == null) {
                Log.w(TAG, "Cache entry missing path");
                continue;
            }

            // Unlink it
            File file = new File(entry.path);
            if (!file.delete()) {
                Log.w(TAG, "Failed to delete cache entry " + entry.path);
            } else {
                Log.i(TAG, "Gc'd cache entry " + entry.path + ", atime: " + entry.lastAccessTime + ", access count: " + entry.accessCount + ", size: " + entry.sizeBytes);
            }

            // Decrement cache size
            cacheSizeTotal -= entry.sizeBytes;
        }

        Log.d(TAG, "Final cache size: " + cacheSizeTotal);
    }


}
