package com.schlaikjer.music.model;

public class NetworkOpcode {
    // Trigger update of remote database
    // No data arguments
    // Zero-len response comes after update is complete
    public static final int UPDATE_REMOTE_DB = 0;

    // Fetch serialized database information
    // No data arguments
    // Response is protobuf-serialized db info
    public static final int FETCH_DB = 1;

    // Fetch track with specified checksum
    // Data argument is checksum (20 bytes)
    // Response is raw track data (variable size)
    public static final int FETCH_TRACK = 2;

    // Fetch image with specified checksum
    // Data argument is checksum (20 bytes)
    // Response is raw image data (variable size)
    public static final int FETCH_IMAGE = 3;
}

