package com.schlaikjer.music.service;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;

import com.schlaikjer.music.db.TrackDatabase;
import com.schlaikjer.music.model.Track;
import com.schlaikjer.music.utility.NetworkManager;
import com.schlaikjer.music.utility.PlaylistManager;
import com.schlaikjer.music.utility.StorageManager;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.List;

public class MediaService extends Service implements MediaPlayer.OnErrorListener, MediaPlayer.OnCompletionListener, MediaPlayer.OnPreparedListener, PlaylistManager.PlaylistChangedListener {

    private static final String TAG = MediaService.class.getSimpleName();

    public class MediaServiceBinder extends Binder {

        public final MediaService service;

        private MediaServiceBinder(MediaService service) {
            this.service = service;
        }

    }

    MediaPlayer player;
    boolean playerPrepared = false;
    boolean isPlaying = false;
    int playIndex = 0;
    byte[] currentTrack;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return new MediaServiceBinder(this);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        player = new MediaPlayer();
        player.setOnErrorListener(this);
        player.setOnCompletionListener(this);
        player.setOnPreparedListener(this);

        PlaylistManager.addOnPlaylistChangedListener(this);
    }

    @Override
    public void onDestroy() {
        // Clean up media player
        player.release();

        super.onDestroy();
    }

    @Override
    public void onPlaylistChanged() {
        // If we weren't playing before, no need to react
        if (!isPlaying) {
            return;
        }

        // If we were playing, rescan the playlist for the currently playing content ID and update our play index
        List<byte[]> playlist = PlaylistManager.getPlaylist(this);
        boolean trackFound = false;
        for (int i = 0; i < playlist.size(); i++) {
            if (playlist.get(i).equals(currentTrack)) {
                playIndex = i;
                trackFound = true;
                break;
            }
        }

        // If we couldn't find the track we were playing, it must have been removed - stop.
        if (!trackFound) {
            stop();
        }
    }

    public boolean next() {
        PlaylistManager.popFront(this);
        return play();
    }

    public boolean prev() {
        return true;
    }

    public boolean toggle() {
        if (playerPrepared) {
            if (player.isPlaying()) {
                player.pause();
            } else {
                player.start();
            }
        } else {
            return play();
        }
        return true;
    }

    public boolean stop() {
        isPlaying = false;
        playerPrepared = false;

        if (!player.isPlaying()) {
            return true;
        }
        player.stop();

        return true;
    }

    public boolean play() {
        return play(0);
    }

    public boolean play(int index) {
        // Fetch current playlist
        List<byte[]> playlist = PlaylistManager.getPlaylist(this);

        // If it's empty, or the index is OOB, nothing to play
        if (playlist.size() == 0 || index >= playlist.size()) {
            Log.w(TAG, "Failed to play index " + index + " with playlist of size " + playlist.size());
            return false;
        }

        // If we were previously playing something, stop
        if (isPlaying) {
            stop();
        }


        // Get the track to play
        isPlaying = true;
        playIndex = index;
        byte[] trackChecksum = playlist.get(playIndex);
        currentTrack = trackChecksum;

        // Do we have this file cached?
        if (StorageManager.hasContentFile(this, trackChecksum)) {
            // We do - get the content URI and start up the mediaplayer
            String trackPath = StorageManager.getContentFilePath(this, trackChecksum);
            Track track = TrackDatabase.getInstance(this).getTrack(trackChecksum);
            if (track != null) {
                Log.d(TAG, "Playing track index " + index + " " + track.raw_path + " local path: " + trackPath);
            }
            player.reset();
            playerPrepared = false;

            try {
                player.setDataSource(trackPath);
            } catch (IOException e) {
                e.printStackTrace();
                isPlaying = false;
                return false;
            }

            // Prepare
            player.prepareAsync();
            return true;
        }

        // If we don't have the file downloaded, need to make a network request
        final Context appContext = getApplicationContext();
        final WeakReference<MediaService> serviceRef = new WeakReference<>(this);
        Log.d(TAG, "Fetching track index " + index + " with ID " + StorageManager.bytesToHex(trackChecksum));
        NetworkManager.fetchTrack(trackChecksum, new NetworkManager.ContentFetchCallback() {
            @Override
            public void onContentReceived(byte[] data) {
                StorageManager.saveContentFile(appContext, trackChecksum, data);

                // If the service still exists, ask it to play
                MediaService service = serviceRef.get();
                if (service != null) {
                    service.play(index);
                }
            }

            @Override
            public void onAbort() {
                isPlaying = false;
            }
        });

        // Network req will process in bg, call this a success
        return true;
    }

    /**
     * Called to indicate an error.
     *
     * @param mp    the MediaPlayer the error pertains to
     * @param what  the type of error that has occurred:
     * @param extra an extra code, specific to the error. Typically
     *              implementation dependent.
     * @return True if the method handled the error, false if it didn't.
     * Returning false, or not having an OnErrorListener at all, will
     * cause the OnCompletionListener to be called.
     */
    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        Log.w(TAG, "Media player error: " + what + ", extra: " + extra);
        playerPrepared = false;
        isPlaying = false;
        return false;
    }

    /**
     * Called when the end of a media source is reached during playback.
     *
     * @param mp the MediaPlayer that reached the end of the file
     */
    @Override
    public void onCompletion(MediaPlayer mp) {
        Log.d(TAG, "MediaPlayer onCompletion");

        // No longer playing
        isPlaying = false;
        playerPrepared = false;

        // Pop the front of the playlist
        PlaylistManager.removeIndex(this, playIndex, true);

        // Try and play the next track
        play(playIndex);
    }

    /**
     * Called when the media file is ready for playback.
     *
     * @param mp the MediaPlayer that is ready for playback
     */
    @Override
    public void onPrepared(MediaPlayer mp) {
        playerPrepared = true;
        player.start();
    }

}
