package com.schlaikjer.music.service;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;

import com.schlaikjer.music.MainActivity;
import com.schlaikjer.music.utility.NetworkManager;
import com.schlaikjer.music.utility.PlaylistManager;
import com.schlaikjer.music.utility.StorageManager;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.List;

public class MediaService extends Service implements MediaPlayer.OnErrorListener, MediaPlayer.OnCompletionListener, MediaPlayer.OnPreparedListener {

    private static final String TAG = MainActivity.class.getSimpleName();


    public class MediaServiceBinder extends Binder {

        public final MediaService service;

        private MediaServiceBinder(MediaService service) {
            this.service = service;
        }

    }

    MediaPlayer player;

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
    }

    @Override
    public void onDestroy() {
        // Clean up media player
        player.release();

        super.onDestroy();
    }

    public boolean play() {
        // Fetch current playlist
        List<byte[]> playlist = PlaylistManager.getPlaylist(this);

        // If it's empty, nothing to play
        if (playlist.size() == 0) {
            return false;
        }

        // Get the first track
        byte[] trackChecksum = playlist.get(0);

        // Do we have this file cached?
        if (StorageManager.hasContentFile(this, trackChecksum)) {
            // We do - get the content URI and start up the mediaplayer
            String trackPath = StorageManager.getContentFilePath(this, trackChecksum);
            player.reset();
            try {
                player.setDataSource(trackPath);
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }

            // Prepare
            player.prepareAsync();
            return true;
        }

        // If we don't have the file downloaded, need to make a network request
        final Context appContext = getApplicationContext();
        final WeakReference<MediaService> serviceRef = new WeakReference(this);
        NetworkManager.fetchTrack(trackChecksum, new NetworkManager.ContentFetchCallback() {
            @Override
            public void onContentReceived(byte[] data) {
                StorageManager.saveContentFile(appContext, trackChecksum, data);

                // If the service still exists, ask it to play
                MediaService service = serviceRef.get();
                if (service != null) {
                    service.play();
                }
            }

            @Override
            public void onAbort() {
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
        return false;
    }

    /**
     * Called when the end of a media source is reached during playback.
     *
     * @param mp the MediaPlayer that reached the end of the file
     */
    @Override
    public void onCompletion(MediaPlayer mp) {
        // Pop the front of the playlist
        PlaylistManager.popFront(this);

        // Try and play the next track
        play();
    }

    /**
     * Called when the media file is ready for playback.
     *
     * @param mp the MediaPlayer that is ready for playback
     */
    @Override
    public void onPrepared(MediaPlayer mp) {
        player.start();
    }

}
