package com.paran.music;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MusicLibrary {
    public static final String PREFS_FOLDERS = "music_folders";
    public static final String KEY_TREE_URIS = "tree_uris";

    private final List<MusicTrack> tracks;
    private final Map<Long, MusicTrack> byId;

    public MusicLibrary(List<MusicTrack> tracks) {
        this.tracks = tracks;
        this.byId = new LinkedHashMap<>();
        for (MusicTrack track : tracks) {
            byId.put(track.id, track);
        }
    }

    public static MusicLibrary load(Context context) {
        List<MusicTrack> result = new ArrayList<>();
        ContentResolver resolver = context.getContentResolver();
        String[] projection;
        if (Build.VERSION.SDK_INT >= 29) {
            projection = new String[]{
                    MediaStore.Audio.Media._ID,
                    MediaStore.Audio.Media.ALBUM_ID,
                    MediaStore.Audio.Media.TITLE,
                    MediaStore.Audio.Media.ARTIST,
                    MediaStore.Audio.Media.ALBUM,
                    MediaStore.Audio.Media.DURATION,
                    MediaStore.Audio.Media.RELATIVE_PATH
            };
        } else {
            projection = new String[]{
                    MediaStore.Audio.Media._ID,
                    MediaStore.Audio.Media.ALBUM_ID,
                    MediaStore.Audio.Media.TITLE,
                    MediaStore.Audio.Media.ARTIST,
                    MediaStore.Audio.Media.ALBUM,
                    MediaStore.Audio.Media.DURATION,
                    MediaStore.Audio.Media.DATA
            };
        }

        String selection = MediaStore.Audio.Media.IS_MUSIC + "!=0";
        String sort = MediaStore.Audio.Media.TITLE + " COLLATE NOCASE ASC";
        try (Cursor cursor = resolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection, selection, null, sort)) {
            if (cursor == null) {
                appendFileSystemTracks(result);
                appendDocumentTreeTracks(context, result);
                return new MusicLibrary(result);
            }
            int idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
            int albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID);
            int titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE);
            int artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST);
            int albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM);
            int durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION);
            int folderCol = cursor.getColumnIndexOrThrow(Build.VERSION.SDK_INT >= 29 ? MediaStore.Audio.Media.RELATIVE_PATH : MediaStore.Audio.Media.DATA);

            while (cursor.moveToNext()) {
                long duration = cursor.getLong(durationCol);
                if (duration <= 0) {
                    continue;
                }
                String folder = folderName(cursor.getString(folderCol));
                result.add(new MusicTrack(
                        cursor.getLong(idCol),
                        cursor.getLong(albumIdCol),
                        cursor.getString(titleCol),
                        cursor.getString(artistCol),
                        cursor.getString(albumCol),
                        folder,
                        duration
                ));
            }
        } catch (RuntimeException ignored) {
            appendFileSystemTracks(result);
            appendDocumentTreeTracks(context, result);
            return new MusicLibrary(result);
        }
        if (result.isEmpty()) {
            appendFileSystemTracks(result);
        }
        appendDocumentTreeTracks(context, result);
        return new MusicLibrary(result);
    }

    private static void appendFileSystemTracks(List<MusicTrack> result) {
        File musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC);
        if (musicDir == null || !musicDir.exists()) {
            return;
        }
        List<File> files = new ArrayList<>();
        collectAudioFiles(musicDir, files);
        for (File file : files) {
            String path = file.getAbsolutePath();
            if (containsPath(result, path)) {
                continue;
            }
            MusicTrack track = fromFile(file);
            if (track != null) {
                result.add(track);
            }
        }
        Collections.sort(result, new Comparator<MusicTrack>() {
            @Override
            public int compare(MusicTrack left, MusicTrack right) {
                return left.title.compareToIgnoreCase(right.title);
            }
        });
    }

    private static boolean containsPath(List<MusicTrack> tracks, String path) {
        for (MusicTrack track : tracks) {
            if (path.equals(track.filePath)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsUri(List<MusicTrack> tracks, Uri uri) {
        String value = uri.toString();
        for (MusicTrack track : tracks) {
            if (value.equals(track.contentUri.toString())) {
                return true;
            }
        }
        return false;
    }

    private static void appendDocumentTreeTracks(Context context, List<MusicTrack> result) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_FOLDERS, Context.MODE_PRIVATE);
        for (String value : prefs.getStringSet(KEY_TREE_URIS, Collections.<String>emptySet())) {
            Uri treeUri = Uri.parse(value);
            String folder = treeTitle(context, treeUri);
            try {
                collectDocumentTreeTracks(context, treeUri, DocumentsContract.getTreeDocumentId(treeUri), folder, result);
            } catch (RuntimeException ignored) {
            }
        }
        Collections.sort(result, new Comparator<MusicTrack>() {
            @Override
            public int compare(MusicTrack left, MusicTrack right) {
                return left.title.compareToIgnoreCase(right.title);
            }
        });
    }

    private static void collectDocumentTreeTracks(Context context, Uri treeUri, String documentId, String folder, List<MusicTrack> result) {
        Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId);
        String[] projection = new String[]{
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE
        };
        try (Cursor cursor = context.getContentResolver().query(childrenUri, projection, null, null, null)) {
            if (cursor == null) {
                return;
            }
            int idCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID);
            int nameCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME);
            int mimeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE);
            while (cursor.moveToNext()) {
                String childId = cursor.getString(idCol);
                String name = cursor.getString(nameCol);
                String mime = cursor.getString(mimeCol);
                if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) {
                    collectDocumentTreeTracks(context, treeUri, childId, name == null ? folder : name, result);
                    continue;
                }
                if (!isAudioMime(mime) && !isAudioFile(name == null ? "" : name)) {
                    continue;
                }
                Uri documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childId);
                if (containsUri(result, documentUri)) {
                    continue;
                }
                MusicTrack track = fromDocument(context, documentUri, name, folder);
                if (track != null) {
                    result.add(track);
                }
            }
        }
    }

    private static boolean isAudioMime(String mime) {
        return mime != null && mime.toLowerCase(Locale.US).startsWith("audio/");
    }

    private static void collectAudioFiles(File dir, List<File> out) {
        File[] children = dir.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            if (child.isDirectory()) {
                collectAudioFiles(child, out);
            } else if (isAudioFile(child.getName())) {
                out.add(child);
            }
        }
    }

    private static boolean isAudioFile(String name) {
        String lower = name.toLowerCase(Locale.US);
        return lower.endsWith(".mp3")
                || lower.endsWith(".m4a")
                || lower.endsWith(".aac")
                || lower.endsWith(".flac")
                || lower.endsWith(".wav")
                || lower.endsWith(".ogg")
                || lower.endsWith(".opus");
    }

    private static MusicTrack fromFile(File file) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(file.getAbsolutePath());
            String title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
            String artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
            String album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM);
            String duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            long durationMs = duration == null ? 0 : Long.parseLong(duration);
            if (title == null || title.trim().isEmpty()) {
                String name = file.getName();
                int dot = name.lastIndexOf('.');
                title = dot > 0 ? name.substring(0, dot) : name;
            }
            long id = -Math.abs((long) file.getAbsolutePath().hashCode());
            return new MusicTrack(id, title, artist, album, file.getParentFile().getName(), durationMs, file.getAbsolutePath());
        } catch (Exception ignored) {
            return null;
        } finally {
            try {
                retriever.release();
            } catch (Exception ignored) {
            }
        }
    }

    private static MusicTrack fromDocument(Context context, Uri uri, String displayName, String folder) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(context, uri);
            String title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
            String artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
            String album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM);
            String duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            long durationMs = duration == null ? 0 : Long.parseLong(duration);
            if (title == null || title.trim().isEmpty()) {
                String name = displayName == null ? "Unknown title" : displayName;
                int dot = name.lastIndexOf('.');
                title = dot > 0 ? name.substring(0, dot) : name;
            }
            return new MusicTrack(stableNegativeId(uri.toString()), title, artist, album, folder, durationMs, uri);
        } catch (Exception ignored) {
            return null;
        } finally {
            try {
                retriever.release();
            } catch (Exception ignored) {
            }
        }
    }

    private static String treeTitle(Context context, Uri treeUri) {
        Uri documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri));
        try (Cursor cursor = context.getContentResolver().query(
                documentUri,
                new String[]{DocumentsContract.Document.COLUMN_DISPLAY_NAME},
                null,
                null,
                null)) {
            if (cursor != null && cursor.moveToFirst()) {
                String title = cursor.getString(0);
                if (title != null && !title.trim().isEmpty()) {
                    return title;
                }
            }
        } catch (RuntimeException ignored) {
        }
        return "Selected folder";
    }

    private static long stableNegativeId(String value) {
        long hash = value == null ? 1 : value.hashCode();
        if (hash == Integer.MIN_VALUE) {
            hash = 1;
        }
        return -Math.abs(hash);
    }

    private static String folderName(String path) {
        if (path == null || path.trim().isEmpty()) {
            return "Music";
        }
        String normalized = path.replace("\\", "/");
        if (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        int slash = normalized.lastIndexOf('/');
        return slash >= 0 ? normalized.substring(slash + 1) : normalized;
    }

    public List<MusicTrack> allTracks() {
        return new ArrayList<>(tracks);
    }

    public MusicTrack find(long id) {
        return byId.get(id);
    }

    public List<MusicTrack> search(String query) {
        String needle = query == null ? "" : query.toLowerCase(Locale.getDefault());
        if (needle.isEmpty()) {
            return allTracks();
        }
        List<MusicTrack> matches = new ArrayList<>();
        for (MusicTrack track : tracks) {
            if (track.title.toLowerCase(Locale.getDefault()).contains(needle)
                    || track.artist.toLowerCase(Locale.getDefault()).contains(needle)
                    || track.album.toLowerCase(Locale.getDefault()).contains(needle)
                    || track.folder.toLowerCase(Locale.getDefault()).contains(needle)) {
                matches.add(track);
            }
        }
        return matches;
    }

    public Map<String, List<MusicTrack>> groupByAlbum() {
        return groupBy("album");
    }

    public Map<String, List<MusicTrack>> groupByArtist() {
        return groupBy("artist");
    }

    public Map<String, List<MusicTrack>> groupByFolder() {
        return groupBy("folder");
    }

    private Map<String, List<MusicTrack>> groupBy(String type) {
        Map<String, List<MusicTrack>> groups = new LinkedHashMap<>();
        List<MusicTrack> sorted = allTracks();
        Collections.sort(sorted, new Comparator<MusicTrack>() {
            @Override
            public int compare(MusicTrack left, MusicTrack right) {
                return key(left, type).compareToIgnoreCase(key(right, type));
            }
        });
        for (MusicTrack track : sorted) {
            String key = key(track, type);
            List<MusicTrack> list = groups.get(key);
            if (list == null) {
                list = new ArrayList<>();
                groups.put(key, list);
            }
            list.add(track);
        }
        return groups;
    }

    private String key(MusicTrack track, String type) {
        if ("album".equals(type)) {
            return track.album;
        }
        if ("artist".equals(type)) {
            return track.artist;
        }
        return track.folder;
    }
}
