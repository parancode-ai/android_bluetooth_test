package com.example.android.mediasession.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.SeekBar;

/**
 * Very small extension of {@link SeekBar}. In the original sample this kept the progress in sync
 * with the media session. Here it's just a stand‑in to allow the project to compile.
 */
public class MediaSeekBar extends SeekBar {
    public MediaSeekBar(Context context, AttributeSet attrs) {
        super(context, attrs);
    }
}
