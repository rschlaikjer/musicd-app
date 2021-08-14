package com.schlaikjer.music.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.schlaikjer.music.R;
import com.schlaikjer.music.db.TrackDatabase;
import com.schlaikjer.music.model.Track;
import com.schlaikjer.music.utility.NetworkManager;
import com.schlaikjer.music.utility.StorageManager;
import com.schlaikjer.music.utility.ThreadManager;

import java.util.ArrayList;
import java.util.List;

public class PlaylistRecyclerAdapter extends RecyclerView.Adapter<PlaylistRecyclerAdapter.ViewHolder> {

    private static final String TAG = PlaylistRecyclerAdapter.class.getSimpleName();

    private final Context _appContext;
    private List<Track> _tracks;

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public final View rootView;
        public final ImageView imageView;
        public final TextView titleText;
        public final TextView subtitleText;

        public Track track;

        public ViewHolder(View view) {
            super(view);
            rootView = view;
            imageView = view.findViewById(R.id.recycler_folder_image);
            titleText = view.findViewById(R.id.recycler_folder_top_text);
            subtitleText = view.findViewById(R.id.recycler_folder_bottom_text);
        }
    }

    public interface TrackSelectedListener {
        void onTrackSelected(int position, byte[] track);
    }

    TrackSelectedListener trackSelectedListener;

    public PlaylistRecyclerAdapter(Context context, TrackSelectedListener listener) {
        this._appContext = context.getApplicationContext();
        this.trackSelectedListener = listener;
        setPlaylist(new ArrayList<>());
    }

    public void setPlaylist(List<Track> tracks) {
        _tracks = tracks;
        notifyDataSetChanged();
    }

    public void removeItem(int position) {
        _tracks.remove(position);
        notifyItemRemoved(position);
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
        // Create a new view, which defines the UI of the list item
        View view = LayoutInflater.from(viewGroup.getContext())
                .inflate(R.layout.recycler_playlist, viewGroup, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder viewHolder, final int position) {
        final Track track = _tracks.get(position);
        viewHolder.track = track;

        // Try and load an image
        viewHolder.imageView.setImageDrawable(_appContext.getDrawable(R.drawable.ic_baseline_library_music_48));

        ThreadManager.runOnBgThread(() -> {
            // Attempt to find an already loaded image for this album
            TrackDatabase db = TrackDatabase.getInstance(_appContext);
            List<byte[]> imageCandidates = db.getImageChecksumsForParentPath(track.parent_path);
            for (byte[] checksum : db.getImageChecksumsForParentPath(track.parent_path)) {
                if (StorageManager.hasContentFile(_appContext, checksum)) {
                    // Try and load the image
                    Bitmap bitmap = BitmapFactory.decodeFile(StorageManager.getContentFilePath(_appContext, checksum));
                    ThreadManager.runOnUIThread(() -> {
                        if (viewHolder.track == track) {
                            viewHolder.imageView.setImageBitmap(bitmap);
                        }
                    });
                    return;
                }
            }

            // If no images exist on disk, try and fetch them
            for (byte[] checksum : imageCandidates) {
                NetworkManager.fetchImage(checksum, new NetworkManager.ContentFetchCallback() {
                    @Override
                    public void onContentReceived(byte[] data) {
                        // Save the image to local storage
                        StorageManager.saveContentFile(_appContext, checksum, data);
                        // Also load the data as a bitmap
                        Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
                        // Load to image view
                        ThreadManager.runOnUIThread(() -> {
                            if (viewHolder.track == track) {
                                viewHolder.imageView.setImageBitmap(bitmap);
                            }
                        });
                    }

                    @Override
                    public void onAbort() {
                    }
                });
            }
        });

        viewHolder.titleText.setText(viewHolder.track.tag_title);
        viewHolder.subtitleText.setText(viewHolder.track.tag_artist + " / " + viewHolder.track.tag_album);
        viewHolder.rootView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                trackSelectedListener.onTrackSelected(position, track.checksum);
            }
        });
    }

    @Override
    public int getItemCount() {
        return _tracks.size();
    }
}


