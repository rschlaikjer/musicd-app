package com.schlaikjer.music.ui.gallery;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.SeekBar;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.schlaikjer.music.MainActivity;
import com.schlaikjer.music.R;
import com.schlaikjer.music.ui.PlaylistRecyclerAdapter;
import com.schlaikjer.music.utility.PlaylistManager;

public class GalleryFragment extends Fragment implements PlaylistManager.PlaylistChangedListener {

    @Override
    public void onPlaylistChanged() {
        recyclerAdapter.setPlaylist(PlaylistManager.getPlaylistTracks(getContext()));
    }

    class ControlViews {
        SeekBar volume_seekbar;
        ImageButton now_playing_bottomPreviousButton;
        ImageButton now_playing_bottomPlayPauseButton;
        ImageButton now_playing_bottomStopButton;
        ImageButton now_playing_bottomNextButton;
    }

    private ControlViews controls;

    private RecyclerView playlistRecycler;
    private PlaylistRecyclerAdapter recyclerAdapter;

    private MainActivity getMainActivity() {
        return (MainActivity) getActivity();
    }


    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_gallery, container, false);

        playlistRecycler = root.findViewById(R.id.fragment_gallery_recycler);
        playlistRecycler.setLayoutManager(new GridLayoutManager(getContext(), 1, RecyclerView.VERTICAL, false));
        recyclerAdapter = new PlaylistRecyclerAdapter(getContext());
        recyclerAdapter.setPlaylist(PlaylistManager.getPlaylistTracks(getContext()));
        playlistRecycler.setAdapter(recyclerAdapter);

        // Find controls views
        controls = new ControlViews();
        controls.volume_seekbar = root.findViewById(R.id.volume_seekbar);
        controls.now_playing_bottomPreviousButton = root.findViewById(R.id.now_playing_bottomPreviousButton);
        controls.now_playing_bottomPlayPauseButton = root.findViewById(R.id.now_playing_bottomPlayPauseButton);
        controls.now_playing_bottomStopButton = root.findViewById(R.id.now_playing_bottomStopButton);
        controls.now_playing_bottomNextButton = root.findViewById(R.id.now_playing_bottomNextButton);

        // Set up event handlers
        controls.now_playing_bottomPreviousButton.setOnClickListener(v -> getMainActivity().getMediaService().prev());
        controls.now_playing_bottomPlayPauseButton.setOnClickListener(v -> getMainActivity().getMediaService().toggle());
        controls.now_playing_bottomStopButton.setOnClickListener(v -> getMainActivity().getMediaService().stop());
        controls.now_playing_bottomNextButton.setOnClickListener(v -> getMainActivity().getMediaService().next());

        PlaylistManager.addOnPlaylistChangedListener(this);

        return root;
    }
}