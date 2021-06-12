package com.schlaikjer.music;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.Menu;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.navigation.NavigationView;
import com.schlaikjer.music.db.TrackDatabase;
import com.schlaikjer.music.model.Track;
import com.schlaikjer.music.service.MediaService;
import com.schlaikjer.music.utility.PlaylistManager;

import java.util.List;
import java.util.Random;

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (serviceBinder == null) {
                    Log.w(TAG, "Service binder null - cannot play");
                    return;
                }

                serviceBinder.service.play();
            }
        });

        DrawerLayout drawer = findViewById(R.id.drawer_layout);

        NavigationView navigationView = findViewById(R.id.nav_view);

        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        mAppBarConfiguration = new AppBarConfiguration.Builder(
                R.id.nav_home, R.id.nav_gallery, R.id.nav_slideshow)
                .setDrawerLayout(drawer)
                .build();

        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment);
        NavigationUI.setupActionBarWithNavController(this, navController, mAppBarConfiguration);
        NavigationUI.setupWithNavController(navigationView, navController);

        List<Track> allTracks = TrackDatabase.getInstance(this).getAllTracks();
        if (allTracks.size() > 0) {
            Random rand = new Random();
            for (int i = 0; i < 10; i++) {
                PlaylistManager.append(this, allTracks.get(rand.nextInt(allTracks.size())).checksum);
            }
            Log.d(TAG, "Playlist init'd");
        }

        bindService(new Intent(this, MediaService.class), mediaServiceConnection, BIND_AUTO_CREATE);
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