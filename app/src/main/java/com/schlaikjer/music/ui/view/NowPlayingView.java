package com.schlaikjer.music.ui.view;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.ViewSwitcher;

import androidx.annotation.NonNull;
import androidx.customview.widget.ViewDragHelper;

import com.schlaikjer.music.R;

public class NowPlayingView extends RelativeLayout {

    private final ViewDragHelper mDragHelper;

    // Draggable view
    private View mHeaderView;

    // Main content view
    private View mMainView;


    private LinearLayout mDraggedUpButtons;
    private LinearLayout mDraggedDownButtons;


    /**
     * Top buttons in the draggable header part.
     */
    private ImageButton mTopPlayPauseButton;
    private ImageButton mTopPlaylistButton;
    private ImageButton mTopMenuButton;

    /**
     * Buttons in the bottom part of the view
     */
    private ImageButton mBottomRepeatButton;
    private ImageButton mBottomPreviousButton;
    private ImageButton mBottomPlayPauseButton;
    private ImageButton mBottomStopButton;
    private ImageButton mBottomNextButton;
    private ImageButton mBottomRandomButton;

    /**
     * Seekbar used for seeking and informing the user of the current playback position.
     */
    private SeekBar mPositionSeekbar;

    /**
     * Seekbar used for volume control of host
     */
    private SeekBar mVolumeSeekbar;
    private ImageView mVolumeIcon;
    private ImageView mVolumeIconButtons;

    private TextView mVolumeText;

    private ImageButton mVolumeMinus;
    private ImageButton mVolumePlus;

    private LinearLayout mHeaderTextLayout;

    private LinearLayout mVolumeSeekbarLayout;
    private LinearLayout mVolumeButtonLayout;

    private int mVolumeStepSize;

    /**
     * Main cover imageview
     */
    private AlbumArtistView mCoverImage;

    /**
     * Small cover image, part of the draggable header
     */
    private ImageView mTopCoverImage;

    /**
     * View that contains the playlist ListVIew
     */
    private CurrentPlaylistView mPlaylistView;

    /**
     * ViewSwitcher used for switching between the main cover image and the playlist
     */
    private ViewSwitcher mViewSwitcher;


    /**
     * Various textviews for track information
     */
    private TextView mTrackName;
    private TextView mTrackAdditionalInfo;
    private TextView mElapsedTime;
    private TextView mDuration;

    private TextView mTrackNo;
    private TextView mPlaylistNo;
    private TextView mBitrate;
    private TextView mAudioProperties;
    private TextView mTrackURI;

    /**
     * Absolute pixel position of upper layout bound
     */
    private int mTopPosition;

    /**
     * relative dragposition
     */
    private float mDragOffset;

    /**
     * Height of non-draggable part.
     * (Layout height - draggable part)
     */
    private int mDragRange;

    public NowPlayingView(Context context) {
        this(context, null, 0);
    }

    public NowPlayingView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public NowPlayingView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mDragHelper = ViewDragHelper.create(this, 1f, new BottomDragCallbackHelper());
    }


    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        // Get both main views (header and bottom part)
        mHeaderView = findViewById(R.id.now_playing_headerLayout);
        mMainView = findViewById(R.id.now_playing_bodyLayout);


        // header buttons
        mTopPlayPauseButton = findViewById(R.id.now_playing_topPlayPauseButton);
        mTopPlaylistButton = findViewById(R.id.now_playing_topPlaylistButton);
        mTopMenuButton = findViewById(R.id.now_playing_topMenuButton);

        // bottom buttons
        mBottomRepeatButton = findViewById(R.id.now_playing_bottomRepeatButton);
        mBottomPreviousButton = findViewById(R.id.now_playing_bottomPreviousButton);
        mBottomPlayPauseButton = findViewById(R.id.now_playing_bottomPlayPauseButton);
        mBottomStopButton = findViewById(R.id.now_playing_bottomStopButton);
        mBottomNextButton = findViewById(R.id.now_playing_bottomNextButton);
        mBottomRandomButton = findViewById(R.id.now_playing_bottomRandomButton);

        // Main cover image
        // mCoverImage = findViewById(R.id.now_playing_cover);
        // Small header cover image
        mTopCoverImage = findViewById(R.id.now_playing_topCover);

        // View with the ListView of the playlist
        mPlaylistView = findViewById(R.id.now_playing_playlist);

        // view switcher for cover and playlist view
        mViewSwitcher = findViewById(R.id.now_playing_view_switcher);

        // Button container for the buttons shown if dragged up
        mDraggedUpButtons = findViewById(R.id.now_playing_layout_dragged_up);
        // Button container for the buttons shown if dragged down
        mDraggedDownButtons = findViewById(R.id.now_playing_layout_dragged_down);

        // textviews
        mTrackName = findViewById(R.id.now_playing_trackName);
        // For marquee scrolling the TextView need selected == true
        mTrackName.setSelected(true);
        mTrackAdditionalInfo = findViewById(R.id.now_playing_track_additional_info);
        // For marquee scrolling the TextView need selected == true
        mTrackAdditionalInfo.setSelected(true);

        mTrackNo = findViewById(R.id.now_playing_text_track_no);
        mPlaylistNo = findViewById(R.id.now_playing_text_playlist_no);
        mBitrate = findViewById(R.id.now_playing_text_bitrate);
        mAudioProperties = findViewById(R.id.now_playing_text_audio_properties);
        mTrackURI = findViewById(R.id.now_playing_text_track_uri);

        // Textviews directly under the seekbar
        mElapsedTime = findViewById(R.id.now_playing_elapsedTime);
        mDuration = findViewById(R.id.now_playing_duration);

        mHeaderTextLayout = findViewById(R.id.now_playing_header_textLayout);

        // seekbar (position)
        mPositionSeekbar = findViewById(R.id.now_playing_seekBar);


        mVolumeSeekbar = findViewById(R.id.volume_seekbar);
        mVolumeIcon = findViewById(R.id.volume_icon);


        mVolumeSeekbar.setMax(100);


        /* Volume control buttons */
        mVolumeIconButtons = findViewById(R.id.volume_icon_buttons);

        mVolumeText = findViewById(R.id.volume_button_text);

        mVolumeMinus = findViewById(R.id.volume_button_minus);


        mVolumePlus = findViewById(R.id.volume_button_plus);


        mVolumeSeekbarLayout = findViewById(R.id.volume_seekbar_layout);
        mVolumeButtonLayout = findViewById(R.id.volume_button_layout);

        mDraggedUpButtons.setVisibility(INVISIBLE);
        mDraggedDownButtons.setVisibility(VISIBLE);
        mDraggedUpButtons.setAlpha(0.0f);

    }

    /**
     * Observer class for changes of the drag status.
     */
    private class BottomDragCallbackHelper extends ViewDragHelper.Callback {

        /**
         * Checks if a given child view should act as part of the drag. This is only true for the header
         * element of this View-class.
         *
         * @param child     Child that was touched by the user
         * @param pointerId Id of the pointer used for touching the view.
         * @return True if the view should be allowed to be used as dragging part, false otheriwse.
         */
        @Override
        public boolean tryCaptureView(@NonNull View child, int pointerId) {
            return child == mHeaderView;
        }

        /**
         * Called if the position of the draggable view is changed. This rerequests the layout of the view.
         *
         * @param changedView The view that was changed.
         * @param left        Left position of the view (should stay constant in this case)
         * @param top         Top position of the view
         * @param dx          Dimension of the width
         * @param dy          Dimension of the height
         */
        @Override
        public void onViewPositionChanged(@NonNull View changedView, int left, int top, int dx, int dy) {
            // Save the heighest top position of this view.
            mTopPosition = top;

            // Calculate the new drag offset
            mDragOffset = (float) top / mDragRange;

            // Relayout this view
            requestLayout();

            // Set inverse alpha values for smooth layout transition.
            // Visibility still needs to be set otherwise parts of the buttons
            // are not clickable.
            mDraggedDownButtons.setAlpha(mDragOffset);
            mDraggedUpButtons.setAlpha(1.0f - mDragOffset);

            // Calculate the margin to smoothly resize text field
            LayoutParams layoutParams = (LayoutParams) mHeaderTextLayout.getLayoutParams();
            layoutParams.setMarginEnd((int) (mTopPlaylistButton.getWidth() * (1.0 - mDragOffset)));
            mHeaderTextLayout.setLayoutParams(layoutParams);
        }

        /**
         * Called if the user lifts the finger(release the view) with a velocity
         *
         * @param releasedChild View that was released
         * @param xvel          x position of the view
         * @param yvel          y position of the view
         */
        @Override
        public void onViewReleased(@NonNull View releasedChild, float xvel, float yvel) {
            int top = getPaddingTop();
            if (yvel > 0 || (yvel == 0 && mDragOffset > 0.5f)) {
                top += mDragRange;
            }
            // Snap the view to top/bottom position
            mDragHelper.settleCapturedViewAt(releasedChild.getLeft(), top);
            invalidate();
        }

        /**
         * Returns the range within a view is allowed to be dragged.
         *
         * @param child Child to get the dragrange for
         * @return Dragging range
         */
        @Override
        public int getViewVerticalDragRange(@NonNull View child) {
            return mDragRange;
        }


        /**
         * Clamps (limits) the view during dragging to the top or bottom(plus header height)
         *
         * @param child Child that is being dragged
         * @param top   Top position of the dragged view
         * @param dy    Delta value of the height
         * @return The limited height value (or valid position inside the clamped range).
         */
        @Override
        public int clampViewPositionVertical(@NonNull View child, int top, int dy) {
            final int topBound = getPaddingTop();
            int bottomBound = getHeight() - mHeaderView.getHeight() - mHeaderView.getPaddingBottom();

            final int newTop = Math.min(Math.max(top, topBound), bottomBound);

            return newTop;
        }

        /**
         * Called when the drag state changed. Informs observers that it is either dragged up or down.
         * Also sets the visibility of button groups in the header
         *
         * @param state New drag state
         */
        @Override
        public void onViewDragStateChanged(int state) {
            super.onViewDragStateChanged(state);

            // Check if the new state is the idle state. If then notify the observer (if one is registered)
            if (state == ViewDragHelper.STATE_IDLE) {
                // Enable scrolling of the text views
                mTrackName.setSelected(true);
                mTrackAdditionalInfo.setSelected(true);

                if (mDragOffset == 0.0f) {
                    // Called when dragged up
                    mDraggedDownButtons.setVisibility(INVISIBLE);
                    mDraggedUpButtons.setVisibility(VISIBLE);

                } else {
                    // Called when dragged down
                    mDraggedDownButtons.setVisibility(VISIBLE);
                    mDraggedUpButtons.setVisibility(INVISIBLE);
                    mCoverImage.setVisibility(INVISIBLE);


                }
            } else if (state == ViewDragHelper.STATE_DRAGGING) {
                /*
                 * Show both layouts to enable a smooth transition via
                 * alpha values of the layouts.
                 */
                mDraggedDownButtons.setVisibility(VISIBLE);
                mDraggedUpButtons.setVisibility(VISIBLE);
                mCoverImage.setVisibility(VISIBLE);
                // report the change of the view
            }
        }
    }


}
