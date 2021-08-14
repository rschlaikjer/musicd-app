package com.schlaikjer.music.ui;

import android.content.Context;
import android.util.Log;
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
import com.squareup.picasso.Picasso;

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


        // Attempt to find an already loaded image for this album
        TrackDatabase db = TrackDatabase.getInstance(_appContext);
        List<byte[]> imageCandidates = db.getImageChecksumsForParentPath(track.parent_path);
        boolean didLoadImage = false;
        for (byte[] checksum : imageCandidates) {
            if (StorageManager.hasContentFile(_appContext, checksum)) {
                // Load into UI
                didLoadImage = true;
                Picasso.get()
                        .load(StorageManager.getContentFile(_appContext, checksum))
                        .placeholder(R.drawable.ic_baseline_image_48)
                        .error(R.drawable.ic_baseline_image_48)
                        .into(viewHolder.imageView);
                break;
            }
        }
        if (!didLoadImage) {
            viewHolder.imageView.setImageDrawable(_appContext.getDrawable(R.drawable.ic_baseline_image_48));
        }

        // If no images exist on disk, try and fetch them serially
        imageFetchContinuation(viewHolder, track.checksum, imageCandidates);

        viewHolder.titleText.setText(viewHolder.track.tag_title);
        viewHolder.subtitleText.setText(viewHolder.track.tag_artist + " / " + viewHolder.track.tag_album);
        viewHolder.rootView.setOnClickListener(v -> trackSelectedListener.onTrackSelected(position, track.checksum));
    }

    void imageFetchContinuation(ViewHolder holder, byte[] trackChecksum, List<byte[]> coverImageChecksums) {
        // No checksums, nothing to do
        if (coverImageChecksums.size() == 0) {
            return;
        }

        // If the holder is now owned by another item, bail
        if (holder.track.checksum != trackChecksum) {
            return;
        }

        // Pop the first checksum
        byte[] checksum = coverImageChecksums.get(0);
        Log.d(TAG, "Fetching image with content ID " + StorageManager.bytesToHex(checksum));
        NetworkManager.fetchImage(checksum, new NetworkManager.ContentFetchCallback() {
            @Override
            public void onContentReceived(byte[] data) {
                // Save the image to local storage for next time
                StorageManager.saveContentFile(_appContext, checksum, data);
            }

            @Override
            public void onAbort() {
                Log.w(TAG, "Failed to fetch art with id " + StorageManager.bytesToHex(checksum));

                // Try again with next one
                coverImageChecksums.remove(0);
                if (coverImageChecksums.size() > 0) {
                    imageFetchContinuation(holder, trackChecksum, coverImageChecksums);
                }
            }
        });
    }

    @Override
    public int getItemCount() {
        return _tracks.size();
    }
}


