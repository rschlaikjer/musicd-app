package com.schlaikjer.music.ui.fragment;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.schlaikjer.msgs.TrackOuterClass;
import com.schlaikjer.music.R;
import com.schlaikjer.music.db.TrackDatabase;
import com.schlaikjer.music.listener.AlbumSelectedListener;
import com.schlaikjer.music.listener.TrackSelectedListener;
import com.schlaikjer.music.model.Album;
import com.schlaikjer.music.model.Track;
import com.schlaikjer.music.ui.AlbumRecyclerAdapter;
import com.schlaikjer.music.ui.activity.MainActivity;
import com.schlaikjer.music.utility.NetworkManager;
import com.schlaikjer.music.utility.PlaylistManager;
import com.schlaikjer.music.utility.StorageManager;
import com.schlaikjer.music.utility.ThreadManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

public class LibraryFragment extends Fragment implements AlbumSelectedListener, TrackSelectedListener {

    Stack<String> baseDirBackStack = new Stack<>();
    String baseDir = "";

    List<Album> fullAlbumList;

    private RecyclerView recyclerView;
    private AlbumRecyclerAdapter recyclerAdapter;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_library, container, false);

        recyclerView = root.findViewById(R.id.fragment_home_recycler);
        recyclerView.setLayoutManager(new GridLayoutManager(getContext(), 2, RecyclerView.VERTICAL, false));
        recyclerAdapter = new AlbumRecyclerAdapter(getContext(), this, this);
        recyclerView.setAdapter(recyclerAdapter);

        registerForContextMenu(recyclerView);

        final SwipeRefreshLayout swipeRefresh = root.findViewById(R.id.fragment_home_swipe_refresh);
        final Context appContext = root.getContext().getApplicationContext();
        swipeRefresh.setOnRefreshListener(() -> NetworkManager.fetchDatabase(new NetworkManager.DatabaseFetchCallback() {
            @Override
            public void onDatabaseFetched(TrackOuterClass.MusicDatabase db) {
                TrackDatabase.getInstance(appContext).setDatabase(db);
                final List<Album> albums = TrackDatabase.getInstance(appContext).getDirectoryAlbums(baseDir);

                // If we are on wifi, prefetch album art
                if (NetworkManager.isOnWifi(appContext)) {
                    StorageManager.prefetchArt(appContext);
                }

                ThreadManager.runOnUIThread(() -> {
                    recyclerAdapter.setAlbumList(albums);
                    swipeRefresh.setRefreshing(false);
                });
            }

            @Override
            public void onAbort() {
                ThreadManager.runOnUIThread(() -> {
                    swipeRefresh.setRefreshing(false);
                });
            }
        }));

        // Populate recyclerview async
        swipeRefresh.setRefreshing(true);
        ThreadManager.runOnBgThread(() -> {
            // Fetch album list from DB
            final List<Album> albums = TrackDatabase.getInstance(getContext()).getDirectoryAlbums("");

            // If there exist albums, post them to the DB
            if (albums.size() > 0) {
                ThreadManager.runOnUIThread(() -> {
                    fullAlbumList = albums;
                    LibraryFragment.this.recyclerAdapter.setAlbumList(albums);
                    swipeRefresh.setRefreshing(false);
                });
                return;
            }

            // If the album list is empty, this might be first boot - try and fetch albums from the network
            NetworkManager.fetchDatabase(new NetworkManager.DatabaseFetchCallback() {
                @Override
                public void onDatabaseFetched(TrackOuterClass.MusicDatabase db) {
                    TrackDatabase.getInstance(appContext).setDatabase(db);
                    final List<Album> albums = TrackDatabase.getInstance(appContext).getDirectoryAlbums(baseDir);

                    // If we are on wifi, prefetch album art
                    if (NetworkManager.isOnWifi(appContext)) {
                        StorageManager.prefetchArt(appContext);
                    }

                    // Post the DB to the UI thread
                    ThreadManager.runOnUIThread(() -> {
                        recyclerAdapter.setAlbumList(albums);
                        swipeRefresh.setRefreshing(false);
                    });
                }

                @Override
                public void onAbort() {
                    ThreadManager.runOnUIThread(() -> {
                        swipeRefresh.setRefreshing(false);
                    });
                }
            });
        });

        getActivity().getOnBackPressedDispatcher().addCallback(new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (!baseDirBackStack.empty()) {
                    baseDir = baseDirBackStack.pop();
                    reloadAlbumsAsync();
                } else {
                    setEnabled(false);
                    Activity activity = getActivity();
                    if (activity != null) {
                        activity.onBackPressed();
                    }
                }
            }
        });

        return root;
    }

    private MainActivity getMainActivity() {
        return (MainActivity) getActivity();
    }

    @Override
    public boolean onContextItemSelected(@NonNull MenuItem item) {
        Album album = recyclerAdapter.getSelectedAlbum();
        if (album == null) {
            return super.onContextItemSelected(item);
        }

        // Get all the tracks below this album
        List<Track> tracks = TrackDatabase.getInstance(getContext()).getTracksForParentPathRecursive(album.parent_path);
        List<byte[]> trackIds = new ArrayList<>();
        for (Track track : tracks) {
            trackIds.add(track.checksum);
        }

        if (item.getItemId() == R.id.action_play_next) {
            PlaylistManager.insert(getContext(), trackIds, getMainActivity().getMediaService().getPlayIndex() +1);
            return true;
        } else if (item.getItemId() == R.id.action_add_to_playlist) {
            PlaylistManager.append(getContext(), trackIds);
            return true;
        } else {
            return super.onContextItemSelected(item);
        }
    }

    @Override
    public void onCreateContextMenu(@NonNull ContextMenu menu, @NonNull View v, @Nullable ContextMenu.ContextMenuInfo menuInfo) {
        // If the menu request isn't for the recycler view, do nothing
        if (v != recyclerView) {
            super.onCreateContextMenu(menu, v, menuInfo);
            return;
        }

        MenuInflater inflater = getActivity().getMenuInflater();
        inflater.inflate(R.menu.album_long_press, menu);
    }

    @Override
    public void onAlbumSelected(Album album) {
        baseDirBackStack.push(baseDir);
        baseDir = album.parent_path;
        reloadAlbumsAsync();
    }

    @Override
    public void onTrackSelected(Track Track) {

    }

    private void reloadAlbumsAsync() {
        // If the basedir is empty, and we have the full album list cached, use the cached version
        if (baseDir.equals("") && fullAlbumList != null) {
            recyclerAdapter.setAlbumList(fullAlbumList);
            return;
        }

        ThreadManager.runOnBgThread(() -> {
            final List<Album> albums = TrackDatabase.getInstance(getContext()).getDirectoryAlbums(baseDir);
            ThreadManager.runOnUIThread(() -> LibraryFragment.this.recyclerAdapter.setAlbumList(albums));
        });
    }

}