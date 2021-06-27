package com.schlaikjer.music.model;

public class CacheEntry {

    public byte[] checksum;
    public String path;
    public long sizeBytes = 0;
    public long accessCount = 0;
    public long lastAccessTime = 0;

}
