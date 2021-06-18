package com.schlaikjer.music.ui.home;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
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
import com.schlaikjer.music.utility.NetworkManager;
import com.schlaikjer.music.utility.ThreadManager;

import java.util.List;
import java.util.Stack;

public class HomeFragment extends Fragment implements AlbumSelectedListener, TrackSelectedListener {

    private HomeViewModel homeViewModel;

    Stack<String> baseDirBackStack = new Stack<>();
    String baseDir = "";

    List<Album> fullAlbumList;

    private RecyclerView recyclerView;
    private AlbumRecyclerAdapter recyclerAdapter;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        homeViewModel =
                new ViewModelProvider(this).get(HomeViewModel.class);
        View root = inflater.inflate(R.layout.fragment_home, container, false);

        recyclerView = root.findViewById(R.id.fragment_home_recycler);
        recyclerView.setLayoutManager(new GridLayoutManager(getContext(), 2, RecyclerView.VERTICAL, false));
        recyclerAdapter = new AlbumRecyclerAdapter(getContext(), this, this);
        recyclerView.setAdapter(recyclerAdapter);


        final SwipeRefreshLayout swipeRefresh = root.findViewById(R.id.fragment_home_swipe_refresh);
        final Context appContext = root.getContext().getApplicationContext();
        swipeRefresh.setOnRefreshListener(() -> NetworkManager.fetchDatabase(new NetworkManager.DatabaseFetchCallback() {
            @Override
            public void onDatabaseFetched(TrackOuterClass.MusicDatabase db) {
                TrackDatabase.getInstance(appContext).setDatabase(db);
                final List<Album> albums = TrackDatabase.getInstance(appContext).getDirectoryAlbums(baseDir);
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
            final List<Album> albums = TrackDatabase.getInstance(getContext()).getDirectoryAlbums("");
            ThreadManager.runOnUIThread(() -> {
                fullAlbumList = albums;
                HomeFragment.this.recyclerAdapter.setAlbumList(albums);
                swipeRefresh.setRefreshing(false);
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
                    getActivity().onBackPressed();
                }
            }
        });

        return root;
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
            ThreadManager.runOnUIThread(() -> HomeFragment.this.recyclerAdapter.setAlbumList(albums));
        });
    }

}