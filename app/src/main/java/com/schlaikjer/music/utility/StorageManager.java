package com.schlaikjer.music.utility;

import android.content.Context;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

public class StorageManager {

    private static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();

    static String bytesToHex(byte[] data) {
        char[] hexChars = new char[data.length * 2];
        for (int j = 0; j < data.length; j++) {
            int v = data[j] & 0xFF;
            hexChars[j * 2] = HEX_ARRAY[v >>> 4];
            hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        return new String(hexChars);
    }

    public static void saveContentFile(Context context, byte[] checksum, byte[] data) {
        // Convert the checksum to hex for use as a filename
        String filename = bytesToHex(checksum);

        // Write out the data
        try (FileOutputStream fos = context.openFileOutput(filename, Context.MODE_PRIVATE)) {
            fos.write(data);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static boolean hasContentFile(Context context, byte[] checksum) {
        String filename = bytesToHex(checksum);
        File file = new File(context.getFilesDir(), filename);
        return file.exists();
    }

    public static String getContentFilePath(Context context, byte[] checksum) {
        String filename = bytesToHex(checksum);
        File file = new File(context.getFilesDir(), filename);
        return file.getAbsolutePath();
    }

}
