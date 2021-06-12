package com.schlaikjer.music.ui.home;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.schlaikjer.msgs.TrackOuterClass;
import com.schlaikjer.music.R;
import com.schlaikjer.music.db.TrackDatabase;
import com.schlaikjer.music.ui.AlbumRecyclerAdapter;
import com.schlaikjer.music.utility.NetworkManager;
import com.schlaikjer.music.utility.ThreadManager;

public class HomeFragment extends Fragment {

    private HomeViewModel homeViewModel;


    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        homeViewModel =
                new ViewModelProvider(this).get(HomeViewModel.class);
        View root = inflater.inflate(R.layout.fragment_home, container, false);

        final RecyclerView recyclerView = root.findViewById(R.id.fragment_home_recycler);
        recyclerView.setLayoutManager(new GridLayoutManager(getContext(), 2, RecyclerView.VERTICAL, false));
        recyclerView.setAdapter(new AlbumRecyclerAdapter(getContext(), TrackDatabase.getInstance(getContext()).getAlbums()));

        final SwipeRefreshLayout swipeRefresh = root.findViewById(R.id.fragment_home_swipe_refresh);
        final Context appContext = root.getContext().getApplicationContext();
        swipeRefresh.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                NetworkManager.fetchDatabase(new NetworkManager.DatabaseFetchCallback() {
                    @Override
                    public void onDatabaseFetched(TrackOuterClass.MusicDatabase db) {
                        TrackDatabase.getInstance(appContext).setDatabase(db);
                        ThreadManager.runOnUIThread(() -> {
                            recyclerView.setAdapter(new AlbumRecyclerAdapter(appContext, TrackDatabase.getInstance(appContext).getAlbums()));
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
            }
        });

        return root;
    }
}