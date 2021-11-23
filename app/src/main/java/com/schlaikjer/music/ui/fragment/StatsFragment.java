package com.schlaikjer.music.ui.fragment;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.google.android.material.slider.Slider;
import com.schlaikjer.music.R;
import com.schlaikjer.music.db.TrackDatabase;
import com.schlaikjer.music.utility.NetworkManager;
import com.schlaikjer.music.utility.PreferencesManager;
import com.schlaikjer.music.utility.StorageManager;
import com.schlaikjer.music.utility.ThreadManager;

import java.lang.ref.WeakReference;
import java.text.CharacterIterator;
import java.text.StringCharacterIterator;
import java.util.Locale;

public class StatsFragment extends Fragment {

    private static final String TAG = StatsFragment.class.getSimpleName();

    static final long MEGABYTE = 1024 * 1024;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_stats, container, false);
        TextView cacheSizeText = root.findViewById(R.id.fragment_stats_cache_size);
        CheckBox limitCacheSize = root.findViewById(R.id.fragment_stats_limit_cache_size);
        Slider cacheSizeSlider = root.findViewById(R.id.fragment_stats_max_cache_size);
        Button rescanRemoteDb = root.findViewById(R.id.fragment_stats_rescan_remote_db);
        Button gcCache = root.findViewById(R.id.fragment_stats_gc_cache);

        // Load prev settings for cache
        long maxCacheSize = PreferencesManager.getSharedPreferences(getContext()).getLong(PreferencesManager.Keys.MAX_CACHE_SIZE_BYTES, StorageManager.DEFAULT_MAX_CACHE_SIZE_BYTES);
        limitCacheSize.setChecked(PreferencesManager.getSharedPreferences(getContext()).getBoolean(PreferencesManager.Keys.LIMIT_CACHE_SIZE, false));
        cacheSizeSlider.setValue((float) maxCacheSize / MEGABYTE);

        // Handle max cache size value set
        limitCacheSize.setText(getString(R.string.limit_cache_size, formatByteSize(((long) cacheSizeSlider.getValue()) * MEGABYTE)));
        limitCacheSize.setOnClickListener(v -> PreferencesManager.getSharedPreferences(getContext()).edit().putBoolean(PreferencesManager.Keys.LIMIT_CACHE_SIZE, limitCacheSize.isChecked()).apply());
        cacheSizeSlider.setOnTouchListener((v, event) -> {
            long cacheSizeBytes = ((long) cacheSizeSlider.getValue()) * MEGABYTE;
            limitCacheSize.setText(getString(R.string.limit_cache_size, formatByteSize(cacheSizeBytes)));
            PreferencesManager.getSharedPreferences(getContext()).edit().putLong(PreferencesManager.Keys.MAX_CACHE_SIZE_BYTES, cacheSizeBytes).apply();
            return false;
        });


        // Set cache byte size text
        long cacheSize = TrackDatabase.getInstance(getContext()).getCacheSize();
        cacheSizeText.setText(getResources().getString(R.string.cache_size_s, formatByteSize(cacheSize)));

        // Rescan DB button
        rescanRemoteDb.setOnClickListener(v -> {
            NetworkManager.rescanDatabase(new NetworkManager.DatabaseRescanCallback() {
                @Override
                public void onSuccess() {
                    Log.d(TAG, "Rescanning DB");
                }

                @Override
                public void onAbort() {
                    Log.w(TAG, "Failed to request DB scan");
                }
            });
        });

        // GC cache button
        final Context appContext = getContext().getApplicationContext();
        final WeakReference<TextView> cacheSizeTextRef = new WeakReference<>(cacheSizeText);
        gcCache.setOnClickListener(v -> ThreadManager.runOnBgThread(() -> {
            StorageManager.gcContentCache(appContext);
            ThreadManager.runOnUIThread(() -> {
                TextView referent = cacheSizeTextRef.get();
                if (referent != null) {
                    referent.setText(getString(R.string.limit_cache_size, formatByteSize(((long) cacheSizeSlider.getValue()) * MEGABYTE)));
                }
            });
        }));

        return root;
    }


    public static String formatByteSize(long bytes) {
        long absB = bytes == Long.MIN_VALUE ? Long.MAX_VALUE : Math.abs(bytes);
        if (absB < 1024) {
            return bytes + " B";
        }
        long value = absB;
        CharacterIterator ci = new StringCharacterIterator("KMGTPE");
        for (int i = 40; i >= 0 && absB > 0xfffccccccccccccL >> i; i -= 10) {
            value >>= 10;
            ci.next();
        }
        value *= Long.signum(bytes);
        return String.format(Locale.US, "%.1f %ciB", value / 1024.0, ci.current());
    }

}