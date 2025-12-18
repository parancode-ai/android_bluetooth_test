package com.example.android.player;

import android.graphics.Bitmap;

/**
 * Simple holder for track information. Audio and artwork files are assumed to exist on the device
 * already; this class only carries their locations.
 */
public class Track {
    private final String title;
    private final String artist;
    private final String album;
    private final String audioPath;
    private final String artworkPath;
    private final long durationMs;
    private Bitmap artwork;

    public Track(String title, String artist, String album, String audioPath, String artworkPath, long durationMs) {
        this.title = title;
        this.artist = artist;
        this.album = album;
        this.audioPath = audioPath;
        this.artworkPath = artworkPath;
        this.durationMs = durationMs;
    }

    public String getTitle() {
        return title;
    }

    public String getArtist() {
        return artist;
    }

    public String getAlbum() {
        return album;
    }

    public String getAudioPath() {
        return audioPath;
    }

    public String getArtworkPath() {
        return artworkPath;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public Bitmap getArtwork() {
        return artwork;
    }

    public void setArtwork(Bitmap artwork) {
        this.artwork = artwork;
    }
}
