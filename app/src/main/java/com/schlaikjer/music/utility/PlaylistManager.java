package com.schlaikjer.music.utility;

import android.content.Context;

import com.schlaikjer.music.db.TrackDatabase;
import com.schlaikjer.music.model.Track;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

public class PlaylistManager {

    private static List<byte[]> currentPlaylist = null;

    private static final int TRACK_PREFETCH_LEN = 4;

    private static List<WeakReference<PlaylistChangedListener>> playlistChangedListeners = new ArrayList<>();

    public interface PlaylistChangedListener {
        void onPlaylistChanged();
    }

    public static void addOnPlaylistChangedListener(PlaylistChangedListener listener) {
        playlistChangedListeners.add(new WeakReference<>(listener));
    }

    private static void notifyPlaylistChanged() {
        ThreadManager.runOnUIThread(new Runnable() {
            @Override
            public void run() {
                for (WeakReference<PlaylistChangedListener> listener : playlistChangedListeners) {
                    if (listener.get() == null) {
                        playlistChangedListeners.remove(listener);
                    } else {
                        listener.get().onPlaylistChanged();
                    }
                }
            }
        });
    }

    public static List<byte[]> getPlaylist(Context context) {
        // If the cached playlist is null, fetch from DB
        if (currentPlaylist == null) {
            currentPlaylist = TrackDatabase.getInstance(context).getPlaylist();
        }

        return currentPlaylist;
    }

    public static List<Track> getPlaylistTracks(Context context) {
        // If the cached playlist is null, fetch from DB
        if (currentPlaylist == null) {
            currentPlaylist = TrackDatabase.getInstance(context).getPlaylist();
        }

        List<Track> tracks = new ArrayList<>();
        for (byte[] hash : currentPlaylist) {
            tracks.add(TrackDatabase.getInstance(context).getTrack(hash));
        }

        return tracks;
    }

    public static List<byte[]> setPlaylist(Context context, List<byte[]> playlist) {
        currentPlaylist = playlist;
        cachePlaylist(context);
        prefetchTracks(context);
        notifyPlaylistChanged();
        return currentPlaylist;
    }

    public static List<byte[]> append(Context context, byte[] track) {
        if (currentPlaylist == null) {
            getPlaylist(context);
        }

        currentPlaylist.add(track);
        cachePlaylist(context);
        prefetchTracks(context);
        notifyPlaylistChanged();
        return currentPlaylist;
    }

    public static List<byte[]> popFront(Context context) {
        if (currentPlaylist == null) {
            getPlaylist(context);
        }


        if (currentPlaylist.size() > 0) {
            currentPlaylist.remove(0);
            prefetchTracks(context);
        }

        cachePlaylist(context);
        notifyPlaylistChanged();

        return currentPlaylist;
    }

    private static void cachePlaylist(Context context) {
        // Clone the current playlist & send to bg thread for serialization
        final List<byte[]> clonedPlaylist = new ArrayList<>(currentPlaylist);
        ThreadManager.runOnBgThread(() -> TrackDatabase.getInstance(context).setPlaylist(clonedPlaylist));
    }

    private static void prefetchTracks(Context context) {
        for (int i = 0; i < currentPlaylist.size() && i < TRACK_PREFETCH_LEN; i++) {
            final byte[] checksum = currentPlaylist.get(i);
            if (!StorageManager.hasContentFile(context, checksum)) {
                NetworkManager.fetchTrack(checksum, new NetworkManager.ContentFetchCallback() {
                    @Override
                    public void onContentReceived(byte[] data) {
                        StorageManager.saveContentFile(context, checksum, data);
                    }

                    @Override
                    public void onAbort() {
                        // Don't care
                    }
                });
            }
        }
    }

}
