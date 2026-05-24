package com.paran.music;

import android.content.Context;
import android.content.SharedPreferences;

public class AudioEffectSettings {
    public static final String PREFS = "audio_effects";
    public static final String KEY_ENABLED = "enabled";
    public static final String KEY_PRESET = "preset";
    public static final String KEY_BASS = "bass";
    public static final String KEY_VIRTUALIZER = "virtualizer";
    public static final String KEY_LOUDNESS = "loudness";
    public static final String KEY_SPEED = "speed";
    public static final String KEY_CROSSFADE = "crossfade";
    public static final String KEY_SKIP_SILENCE = "skip_silence";
    public static final String KEY_LOCKSCREEN = "lockscreen";
    public static final String KEY_EXTERNAL = "external_device";
    public static final String KEY_SLEEP_TIMER = "sleep_timer";

    private final SharedPreferences prefs;

    public AudioEffectSettings(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public boolean enabled() {
        return prefs.getBoolean(KEY_ENABLED, false);
    }

    public int preset() {
        return prefs.getInt(KEY_PRESET, -1);
    }

    public int bass() {
        return prefs.getInt(KEY_BASS, 0);
    }

    public int virtualizer() {
        return prefs.getInt(KEY_VIRTUALIZER, 0);
    }

    public int loudness() {
        return prefs.getInt(KEY_LOUDNESS, 0);
    }

    public float speed() {
        return prefs.getFloat(KEY_SPEED, 1.0f);
    }

    public int crossfadeSeconds() {
        return prefs.getInt(KEY_CROSSFADE, 0);
    }

    public boolean skipSilence() {
        return prefs.getBoolean(KEY_SKIP_SILENCE, false);
    }

    public boolean lockscreenControls() {
        return prefs.getBoolean(KEY_LOCKSCREEN, true);
    }

    public boolean externalDevicePlayback() {
        return prefs.getBoolean(KEY_EXTERNAL, true);
    }

    public int sleepTimerMinutes() {
        return prefs.getInt(KEY_SLEEP_TIMER, 0);
    }

    public void putBoolean(String key, boolean value) {
        prefs.edit().putBoolean(key, value).apply();
    }

    public void putInt(String key, int value) {
        prefs.edit().putInt(key, value).apply();
    }

    public void putFloat(String key, float value) {
        prefs.edit().putFloat(key, value).apply();
    }
}
