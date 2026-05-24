package com.paran.music;

import android.net.Uri;
import android.provider.MediaStore;

import java.io.File;

public class MusicTrack {
    public final long id;
    public final long albumId;
    public final String title;
    public final String artist;
    public final String album;
    public final String folder;
    public final long durationMs;
    public final Uri contentUri;
    public final Uri albumArtUri;
    public final String filePath;

    public MusicTrack(long id, long albumId, String title, String artist, String album, String folder, long durationMs) {
        this.id = id;
        this.albumId = albumId;
        this.title = clean(title, "Unknown title");
        this.artist = clean(artist, "Unknown artist");
        this.album = clean(album, "Unknown album");
        this.folder = clean(folder, "Music");
        this.durationMs = durationMs;
        this.contentUri = Uri.withAppendedPath(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, String.valueOf(id));
        this.albumArtUri = Uri.parse("content://media/external/audio/albumart/" + albumId);
        this.filePath = null;
    }

    public MusicTrack(long id, String title, String artist, String album, String folder, long durationMs, String filePath) {
        this.id = id;
        this.albumId = -1;
        this.title = clean(title, "Unknown title");
        this.artist = clean(artist, "Unknown artist");
        this.album = clean(album, "Unknown album");
        this.folder = clean(folder, "Music");
        this.durationMs = durationMs;
        this.filePath = filePath;
        this.contentUri = Uri.fromFile(new File(filePath));
        this.albumArtUri = Uri.EMPTY;
    }

    public MusicTrack(long id, String title, String artist, String album, String folder, long durationMs, Uri contentUri) {
        this.id = id;
        this.albumId = -1;
        this.title = clean(title, "Unknown title");
        this.artist = clean(artist, "Unknown artist");
        this.album = clean(album, "Unknown album");
        this.folder = clean(folder, "Music");
        this.durationMs = durationMs;
        this.contentUri = contentUri;
        this.albumArtUri = Uri.EMPTY;
        this.filePath = null;
    }

    private static String clean(String value, String fallback) {
        if (value == null || value.trim().isEmpty() || "<unknown>".equalsIgnoreCase(value.trim())) {
            return fallback;
        }
        return value.trim();
    }
}
