package com.schlaikjer.music.ui.activity;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.RadioButton;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.slider.Slider;
import com.schlaikjer.music.R;
import com.schlaikjer.music.db.TrackDatabase;
import com.schlaikjer.music.model.Album;
import com.schlaikjer.music.model.Track;
import com.schlaikjer.music.service.MediaService;
import com.schlaikjer.music.utility.NetworkManager;
import com.schlaikjer.music.utility.PlaylistManager;
import com.schlaikjer.music.utility.StorageManager;
import com.schlaikjer.music.utility.ThreadManager;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = MainActivity.class.getSimpleName();

    private AppBarConfiguration mAppBarConfiguration;

    MediaService.MediaServiceBinder serviceBinder;

    ServiceConnection mediaServiceConnection = new ServiceConnection() {


        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            serviceBinder = (MediaService.MediaServiceBinder) binder;
            Log.d(TAG, "Bound media service");
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            serviceBinder = null;
            Log.d(TAG, "Media service disconnected");
        }
    };

    public MediaService getMediaService() {
        if (serviceBinder == null) {
            return null;
        }

        return serviceBinder.service;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(v -> NetworkManager.rescanDatabase(new NetworkManager.DatabaseRescanCallback() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "Rescanning DB");
            }

            @Override
            public void onAbort() {
                Log.w(TAG, "Failed to request DB scan");
            }
        }));

        DrawerLayout drawer = findViewById(R.id.drawer_layout);

        NavigationView navigationView = findViewById(R.id.nav_view);

        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        mAppBarConfiguration = new AppBarConfiguration.Builder(
                R.id.nav_library, R.id.nav_playlist, R.id.nav_stats)
                .setDrawerLayout(drawer)
                .build();

        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment);
        NavigationUI.setupActionBarWithNavController(this, navController, mAppBarConfiguration);
        NavigationUI.setupWithNavController(navigationView, navController);

        final Context appContext = getApplicationContext();
        ThreadManager.runOnBgThread(() -> StorageManager.gcContentCache(appContext));

        bindService(new Intent(this, MediaService.class), mediaServiceConnection, BIND_AUTO_CREATE);
    }

    @Override
    protected void onDestroy() {
        unbindService(mediaServiceConnection);
        super.onDestroy();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_clear_playlist) {
            PlaylistManager.clearPlaylist(this);
            serviceBinder.service.stop();
            return true;
        }

        if (item.getItemId() == R.id.ation_prefetch_queue) {
            ThreadManager.runOnBgThread(() -> PlaylistManager.prefetchTracks(getApplicationContext(), PlaylistManager.getPlaylist(getApplicationContext()).size()));
            return true;
        }

        if (item.getItemId() == R.id.action_add_random) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setCancelable(true).setTitle(R.string.add_random).setNegativeButton(R.string.cancel, (dialog, which) -> {
                // Do nothing.
            });

            // Set root layout
            View dialogLayout = getLayoutInflater().inflate(R.layout.dialog_add_random, null);
            builder.setView(dialogLayout);
            builder.setPositiveButton(R.string.ok, (dialog, which) -> {
                // What class of thing are we adding?
                RadioButton artists = dialogLayout.findViewById(R.id.dialog_add_random_artists);
                RadioButton albums = dialogLayout.findViewById(R.id.dialog_add_random_albums);
                RadioButton tracks = dialogLayout.findViewById(R.id.dialog_add_random_tracks);
                Slider count = dialogLayout.findViewById(R.id.dialog_add_random_count);
                int countToAdd = (int) count.getValue();
                List<byte[]> tracksToAdd = new ArrayList<>();
                TrackDatabase db = TrackDatabase.getInstance(this);

                // Random artists
                if (artists.isChecked()) {
                    Toast.makeText(this, R.string.unimplemented, Toast.LENGTH_LONG).show();
                }

                // Random albums
                if (albums.isChecked()) {
                    // Pick N random albums
                    List<Album> albumList = db.getRandomAlbums(countToAdd);

                    // Add all the tracks in those albums to the ingest list
                    for (Album a : albumList) {
                        List<Track> albumTracks = db.getTracksForParentPath(a.parent_path);
                        for (Track t : albumTracks) {
                            tracksToAdd.add(t.checksum);
                        }
                    }
                }

                // Random tracks
                if (tracks.isChecked()) {
                    List<Track> randomTracks = db.getRandomTracks(countToAdd);
                    for (Track t : randomTracks) {
                        tracksToAdd.add(t.checksum);
                    }
                }

                PlaylistManager.append(this, tracksToAdd);
            });

            builder.create().show();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onSupportNavigateUp() {
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment);
        return NavigationUI.navigateUp(navController, mAppBarConfiguration)
                || super.onSupportNavigateUp();
    }
}
