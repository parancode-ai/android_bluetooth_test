package com.paran.music;

import android.content.Intent;
import android.media.MediaDescription;
import android.media.browse.MediaBrowser;
import android.media.session.MediaSession;
import android.os.Bundle;
import android.service.media.MediaBrowserService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class BluetoothMusicBrowserService extends MediaBrowserService {
    private static final String ROOT = "root";
    private static final String SONGS = "songs";
    private static final String ALBUMS = "albums";
    private static final String ARTISTS = "artists";
    private static final String FOLDERS = "folders";
    private static final String PLAYLISTS = "playlists";
    private static final String FAVORITES = "favorites";

    private MediaSession session;
    private MusicLibrary library;
    private PlaylistStore playlists;

    @Override
    public void onCreate() {
        super.onCreate();
        library = MusicLibrary.load(this);
        playlists = new PlaylistStore(this);
        session = new MediaSession(this, "BluetoothMusicBrowser");
        session.setCallback(new MediaSession.Callback() {
            @Override
            public void onPlayFromMediaId(String mediaId, Bundle extras) {
                playMediaId(mediaId);
            }

            @Override
            public void onSkipToNext() {
                startService(new Intent(BluetoothMusicBrowserService.this, PlayerService.class).setAction(PlayerService.ACTION_NEXT));
            }

            @Override
            public void onSkipToPrevious() {
                startService(new Intent(BluetoothMusicBrowserService.this, PlayerService.class).setAction(PlayerService.ACTION_PREVIOUS));
            }

            @Override
            public void onPlay() {
                startService(new Intent(BluetoothMusicBrowserService.this, PlayerService.class).setAction(PlayerService.ACTION_TOGGLE));
            }

            @Override
            public void onPause() {
                startService(new Intent(BluetoothMusicBrowserService.this, PlayerService.class).setAction(PlayerService.ACTION_TOGGLE));
            }
        });
        session.setActive(true);
        setSessionToken(session.getSessionToken());
    }

    @Override
    public BrowserRoot onGetRoot(String clientPackageName, int clientUid, Bundle rootHints) {
        return new BrowserRoot(ROOT, null);
    }

    @Override
    public void onLoadChildren(String parentId, Result<List<MediaBrowser.MediaItem>> result) {
        library = MusicLibrary.load(this);
        playlists = new PlaylistStore(this);
        result.sendResult(children(parentId));
    }

    private List<MediaBrowser.MediaItem> children(String parentId) {
        List<MediaBrowser.MediaItem> items = new ArrayList<>();
        if (ROOT.equals(parentId)) {
            items.add(category(SONGS, "Songs", "All local songs"));
            items.add(category(ALBUMS, "Albums", "Browse by album"));
            items.add(category(ARTISTS, "Artists", "Browse by artist"));
            items.add(category(FOLDERS, "Folders", "Browse by storage folder"));
            items.add(category(PLAYLISTS, "Playlists", "User playlists"));
            items.add(category(FAVORITES, "Favorites", "Liked songs"));
            return items;
        }
        if (SONGS.equals(parentId)) {
            addTracks(items, library.allTracks());
            return items;
        }
        if (FAVORITES.equals(parentId)) {
            addIds(items, playlists.favorites());
            return items;
        }
        if (ALBUMS.equals(parentId)) {
            addGroups(items, "album:", library.groupByAlbum());
            return items;
        }
        if (ARTISTS.equals(parentId)) {
            addGroups(items, "artist:", library.groupByArtist());
            return items;
        }
        if (FOLDERS.equals(parentId)) {
            addGroups(items, "folder:", library.groupByFolder());
            return items;
        }
        if (PLAYLISTS.equals(parentId)) {
            for (String name : playlists.playlistNames()) {
                items.add(category("playlist:" + name, name, playlists.playlistIds(name).size() + " songs"));
            }
            return items;
        }
        if (parentId.startsWith("album:")) {
            addTracks(items, library.groupByAlbum().get(parentId.substring(6)));
        } else if (parentId.startsWith("artist:")) {
            addTracks(items, library.groupByArtist().get(parentId.substring(7)));
        } else if (parentId.startsWith("folder:")) {
            addTracks(items, library.groupByFolder().get(parentId.substring(7)));
        } else if (parentId.startsWith("playlist:")) {
            addIds(items, playlists.playlistIds(parentId.substring(9)));
        }
        return items;
    }

    private void addGroups(List<MediaBrowser.MediaItem> items, String prefix, Map<String, List<MusicTrack>> groups) {
        for (Map.Entry<String, List<MusicTrack>> entry : groups.entrySet()) {
            items.add(category(prefix + entry.getKey(), entry.getKey(), entry.getValue().size() + " songs"));
        }
    }

    private void addIds(List<MediaBrowser.MediaItem> items, List<Long> ids) {
        List<MusicTrack> tracks = new ArrayList<>();
        for (Long id : ids) {
            MusicTrack track = library.find(id);
            if (track != null) {
                tracks.add(track);
            }
        }
        addTracks(items, tracks);
    }

    private void addTracks(List<MediaBrowser.MediaItem> items, List<MusicTrack> tracks) {
        if (tracks == null) {
            return;
        }
        for (MusicTrack track : tracks) {
            items.add(trackItem(track));
        }
    }

    private MediaBrowser.MediaItem category(String id, String title, String subtitle) {
        MediaDescription description = new MediaDescription.Builder()
                .setMediaId(id)
                .setTitle(title)
                .setSubtitle(subtitle)
                .build();
        return new MediaBrowser.MediaItem(description, MediaBrowser.MediaItem.FLAG_BROWSABLE);
    }

    private MediaBrowser.MediaItem trackItem(MusicTrack track) {
        MediaDescription description = new MediaDescription.Builder()
                .setMediaId("song:" + track.id)
                .setTitle(track.title)
                .setSubtitle(track.artist)
                .setIconUri(track.albumArtUri)
                .setMediaUri(track.contentUri)
                .build();
        return new MediaBrowser.MediaItem(description, MediaBrowser.MediaItem.FLAG_PLAYABLE);
    }

    private void playMediaId(String mediaId) {
        if (mediaId == null || !mediaId.startsWith("song:")) {
            return;
        }
        try {
            long id = Long.parseLong(mediaId.substring(5));
            Intent intent = new Intent(this, PlayerService.class)
                    .setAction(PlayerService.ACTION_PLAY_ID)
                    .putExtra(PlayerService.EXTRA_TRACK_ID, id);
            startService(intent);
        } catch (NumberFormatException ignored) {
        }
    }

    @Override
    public void onDestroy() {
        if (session != null) {
            session.release();
        }
        super.onDestroy();
    }
}
