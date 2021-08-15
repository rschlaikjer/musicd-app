package com.schlaikjer.music.ui;

import android.content.Context;
import android.util.Log;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.SectionIndexer;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.schlaikjer.music.R;
import com.schlaikjer.music.listener.AlbumSelectedListener;
import com.schlaikjer.music.listener.TrackSelectedListener;
import com.schlaikjer.music.model.Album;
import com.schlaikjer.music.utility.NetworkManager;
import com.schlaikjer.music.utility.StorageManager;
import com.squareup.picasso.Picasso;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class AlbumRecyclerAdapter extends RecyclerView.Adapter<AlbumRecyclerAdapter.ViewHolder> implements SectionIndexer, View.OnCreateContextMenuListener {

    private static final String TAG = AlbumRecyclerAdapter.class.getSimpleName();

    private final Context _appContext;
    private List<Album> _albums;
    private String[] _albumSections;
    private Integer[] _albumSectionOffsets;

    private Album selectedAlbum;

    AlbumSelectedListener albumSelectedListener;
    TrackSelectedListener trackSelectedListener;

    @Override
    public Object[] getSections() {
        return _albumSections;
    }

    @Override
    public int getPositionForSection(int sectionIndex) {
        return _albumSectionOffsets[sectionIndex];
    }

    @Override
    public int getSectionForPosition(int position) {
        int section = 0;
        for (int offset : _albumSectionOffsets) {
            if (position > offset) {
                break;
            }
            section = offset;
        }
        return section;
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {

    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public final View rootView;
        public final ImageView imageView;
        public final TextView albumText;

        public Album album;

        public ViewHolder(View view) {
            super(view);
            rootView = view;
            imageView = view.findViewById(R.id.recycler_folder_image);
            albumText = view.findViewById(R.id.recycler_folder_top_text);
        }
    }


    public AlbumRecyclerAdapter(Context context, AlbumSelectedListener albumSelectedListener, TrackSelectedListener trackSelectedListener) {
        this._appContext = context.getApplicationContext();
        this.albumSelectedListener = albumSelectedListener;
        this.trackSelectedListener = trackSelectedListener;
        setAlbumList(new ArrayList<>());
    }

    public void setAlbumList(List<Album> albums) {
        this._albums = albums;
        Collections.sort(_albums, (o1, o2) -> o1.name.compareTo(o2.name));

        // Regenerate album sections
        // Albums are guaranteed in-order at this point
        List<String> sections = new ArrayList<>();
        List<Integer> sectionOffsets = new ArrayList<>();
        char currentSection = '\0';
        int offset = -1;
        for (Album album : _albums) {
            offset++;

            // Ignore zero-length album names
            if (album.name.length() == 0) {
                continue;
            }

            // Get the first character of this album
            char albumSection = album.name.charAt(0);

            // If it's different to the prev section, add it to the set and update the section offsets
            if (albumSection != currentSection) {
                currentSection = albumSection;
                sections.add(Character.toString(albumSection).toUpperCase(Locale.getDefault()));
                sectionOffsets.add(offset);
            }
        }

        _albumSections = sections.toArray(new String[0]);
        _albumSectionOffsets = sectionOffsets.toArray(new Integer[0]);

        notifyDataSetChanged();
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
        // Create a new view, which defines the UI of the list item
        View view = LayoutInflater.from(viewGroup.getContext())
                .inflate(R.layout.recycler_folder, viewGroup, false);
        return new ViewHolder(view);
    }

    public Album getSelectedAlbum() {
        return selectedAlbum;
    }

    @Override
    public void onBindViewHolder(ViewHolder viewHolder, final int position) {
        final Album album = _albums.get(position);
        viewHolder.album = album;

        // Invalidate previous picasso requests
        Picasso.get().cancelRequest(viewHolder.imageView);

        View.OnClickListener listener = v -> albumSelectedListener.onAlbumSelected(album);
        viewHolder.rootView.setOnClickListener(listener);
        viewHolder.rootView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                selectedAlbum = album;
                return false;
            }
        });
        viewHolder.rootView.setOnCreateContextMenuListener(this);

        // Attempt to find an already loaded image for this album
        Log.d(TAG, "Trying to load cached album art for album '" + album.name + "'");
        boolean didLoadImage = false;
        for (byte[] checksum : album.coverImageChecksums) {
            Log.d(TAG, "Checking for cached content hash " + StorageManager.bytesToHex(checksum));
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
        Log.d(TAG, "Trying to download album art for album '" + album.name + "'");
        if (!didLoadImage) {
            imageFetchContinuation(viewHolder, album.parent_path, new ArrayList<>(album.coverImageChecksums));
        }

        viewHolder.albumText.setText(viewHolder.album.name);
    }

    void imageFetchContinuation(ViewHolder holder, String parent_path, List<byte[]> coverImageChecksums) {
        // No checksums, nothing to do
        if (coverImageChecksums.size() == 0) {
            return;
        }

        // Pop the first checksum
        byte[] checksum = coverImageChecksums.get(0);
        coverImageChecksums.remove(0);

        // If we already have it, we're done
        if (StorageManager.hasContentFile(_appContext, checksum)) {
            return;
        }

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
                imageFetchContinuation(holder, parent_path, coverImageChecksums);
            }
        });
    }

    @Override
    public int getItemCount() {
        return _albums.size();
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public int getItemViewType(int position) {
        return position;
    }
}


