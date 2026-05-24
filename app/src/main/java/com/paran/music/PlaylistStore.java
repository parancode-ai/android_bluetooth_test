package com.paran.music;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PlaylistStore {
    private static final String PREFS = "playlists";
    private static final String FAVORITES = "favorites";
    private static final String NAMES = "names";

    private final SharedPreferences prefs;

    public PlaylistStore(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public boolean isFavorite(long id) {
        return ids(FAVORITES).contains(id);
    }

    public void toggleFavorite(long id) {
        List<Long> ids = ids(FAVORITES);
        if (ids.contains(id)) {
            ids.remove(id);
        } else {
            ids.add(id);
        }
        saveIds(FAVORITES, ids);
    }

    public List<Long> favorites() {
        return ids(FAVORITES);
    }

    public List<String> playlistNames() {
        List<String> names = strings(NAMES);
        Collections.sort(names, String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    public void createPlaylist(String name) {
        String cleaned = cleanName(name);
        if (cleaned.isEmpty()) {
            return;
        }
        List<String> names = strings(NAMES);
        if (!names.contains(cleaned)) {
            names.add(cleaned);
            saveStrings(NAMES, names);
            saveIds(key(cleaned), new ArrayList<Long>());
        }
    }

    public void addToPlaylist(String name, long id) {
        createPlaylist(name);
        List<Long> ids = ids(key(name));
        if (!ids.contains(id)) {
            ids.add(id);
            saveIds(key(name), ids);
        }
    }

    public List<Long> playlistIds(String name) {
        return ids(key(name));
    }

    private String key(String name) {
        return "playlist_" + cleanName(name);
    }

    private String cleanName(String name) {
        return name == null ? "" : name.trim();
    }

    private List<Long> ids(String key) {
        List<Long> result = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(prefs.getString(key, "[]"));
            for (int i = 0; i < array.length(); i++) {
                result.add(array.getLong(i));
            }
        } catch (JSONException ignored) {
        }
        return result;
    }

    private void saveIds(String key, List<Long> ids) {
        JSONArray array = new JSONArray();
        for (Long id : ids) {
            array.put(id);
        }
        prefs.edit().putString(key, array.toString()).apply();
    }

    private List<String> strings(String key) {
        Set<String> set = prefs.getStringSet(key, new HashSet<String>());
        return new ArrayList<>(set);
    }

    private void saveStrings(String key, List<String> values) {
        prefs.edit().putStringSet(key, new HashSet<>(values)).apply();
    }
}
