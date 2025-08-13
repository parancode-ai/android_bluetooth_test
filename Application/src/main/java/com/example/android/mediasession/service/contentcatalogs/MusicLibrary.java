package com.example.android.mediasession.service.contentcatalogs;

import android.content.Context;
import android.media.MediaMetadata;
import android.media.browse.MediaBrowser;
import android.net.Uri;

import java.util.ArrayList;
import java.util.List;

/**
 * Very small in-memory library of music loaded from the assets folder.
 */
public class MusicLibrary {
    private static final String[] SONGS = {
            "jazz_in_paris.mp3",
            "the_coldest_shoulder.mp3"
    };

    public static List<MediaBrowser.MediaItem> getMediaItems(Context context) {
        List<MediaBrowser.MediaItem> items = new ArrayList<>();
        for (String song : SONGS) {
            MediaMetadata metadata = new MediaMetadata.Builder()
                    .putString(MediaMetadata.METADATA_KEY_MEDIA_ID, song)
                    .putString(MediaMetadata.METADATA_KEY_TITLE, song)
                    .putString(MediaMetadata.METADATA_KEY_MEDIA_URI,
                            Uri.parse("asset:///" + song).toString())
                    .build();
            MediaBrowser.MediaItem item = new MediaBrowser.MediaItem(
                    metadata.getDescription(), MediaBrowser.MediaItem.FLAG_PLAYABLE);
            items.add(item);
        }
        return items;
    }
}
