package com.schlaikjer.music.ui.fragment;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.SeekBar;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.schlaikjer.music.R;
import com.schlaikjer.music.ui.PlaylistRecyclerAdapter;
import com.schlaikjer.music.ui.activity.MainActivity;
import com.schlaikjer.music.utility.PlaylistManager;

public class PlaylistFragment extends Fragment implements PlaylistManager.PlaylistChangedListener {

    @Override
    public void onPlaylistChanged() {
        recyclerAdapter.setPlaylist(PlaylistManager.getPlaylistTracks(getContext()));
        recyclerAdapter.notifyDataSetChanged();
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

    private Paint paint = new Paint();

    private MainActivity getMainActivity() {
        return (MainActivity) getActivity();
    }


    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_playlist, container, false);

        playlistRecycler = root.findViewById(R.id.fragment_gallery_recycler);
        playlistRecycler.setLayoutManager(new GridLayoutManager(getContext(), 1, RecyclerView.VERTICAL, false));
        recyclerAdapter = new PlaylistRecyclerAdapter(getContext(), (position, track) -> getMainActivity().getMediaService().play(position));
        recyclerAdapter.setPlaylist(PlaylistManager.getPlaylistTracks(getContext()));
        playlistRecycler.setHasFixedSize(true);
        playlistRecycler.setAdapter(recyclerAdapter);

        ItemTouchHelper.SimpleCallback simpleItemTouchCallback = new ItemTouchHelper.SimpleCallback(ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {

            @Override
            public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(RecyclerView.ViewHolder viewHolder, int swipeDir) {
                //Remove swiped item from list and notify the RecyclerView
                int position = viewHolder.getAdapterPosition();
                PlaylistManager.removeIndex(getContext(), position, true);
            }

            @Override
            public void onChildDraw(Canvas c, RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, float dX, float dY, int actionState, boolean isCurrentlyActive) {
                if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
                    View itemView = viewHolder.itemView;
                    RectF background;
                    if (dX > 0) {
                        background = new RectF((float) itemView.getLeft(), (float) itemView.getTop(), dX, (float) itemView.getBottom());
                    } else {
                        background = new RectF((float) itemView.getRight() + dX, (float) itemView.getTop(), (float) itemView.getRight(), (float) itemView.getBottom());
                    }
                    paint.setColor(ContextCompat.getColor(getContext(), R.color.swipe_remove));
                    c.drawRect(background, paint);
                }
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
            }
        };
        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(simpleItemTouchCallback);
        itemTouchHelper.attachToRecyclerView(playlistRecycler);

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