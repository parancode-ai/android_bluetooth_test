package com.paran.music;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioAttributes;
import android.media.MediaMetadata;
import android.media.MediaPlayer;
import android.media.PlaybackParams;
import android.media.audiofx.BassBoost;
import android.media.audiofx.Equalizer;
import android.media.audiofx.LoudnessEnhancer;
import android.media.audiofx.Virtualizer;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class PlayerService extends Service {
    public static final String ACTION_PLAY_ID = "com.paran.music.PLAY_ID";
    public static final String ACTION_PLAY_QUEUE = "com.paran.music.PLAY_QUEUE";
    public static final String ACTION_TOGGLE = "com.paran.music.TOGGLE";
    public static final String ACTION_NEXT = "com.paran.music.NEXT";
    public static final String ACTION_PREVIOUS = "com.paran.music.PREVIOUS";
    public static final String ACTION_SEEK = "com.paran.music.SEEK";
    public static final String ACTION_FAST_FORWARD = "com.paran.music.FAST_FORWARD";
    public static final String ACTION_REWIND = "com.paran.music.REWIND";
    public static final String ACTION_TOGGLE_SHUFFLE = "com.paran.music.TOGGLE_SHUFFLE";
    public static final String ACTION_TOGGLE_REPEAT = "com.paran.music.TOGGLE_REPEAT";
    public static final String ACTION_APPLY_AUDIO_SETTINGS = "com.paran.music.APPLY_AUDIO_SETTINGS";
    public static final String ACTION_STOP = "com.paran.music.STOP";
    public static final String ACTION_STATE_CHANGED = "com.paran.music.STATE_CHANGED";
    public static final String EXTRA_TRACK_ID = "track_id";
    public static final String EXTRA_TRACK_IDS = "track_ids";
    public static final String EXTRA_SEEK_TO = "seek_to";
    public static final String EXTRA_PLAYING = "playing";
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_ARTIST = "artist";
    public static final String EXTRA_POSITION = "position";
    public static final String EXTRA_DURATION = "duration";
    public static final String EXTRA_SHUFFLE = "shuffle";
    public static final String EXTRA_REPEAT_MODE = "repeat_mode";

    private static final String CHANNEL_ID = "playback";
    private static final int NOTIFICATION_ID = 10;

    private MediaPlayer mediaPlayer;
    private MediaSession mediaSession;
    private MusicLibrary library;
    private AudioEffectSettings effectSettings;
    private Equalizer equalizer;
    private BassBoost bassBoost;
    private Virtualizer virtualizer;
    private LoudnessEnhancer loudnessEnhancer;
    private final List<Long> queue = new ArrayList<>();
    private int queueIndex = -1;
    private MusicTrack currentTrack;
    private boolean prepared;
    private boolean shuffle;
    private int repeatMode;
    private final Random random = new Random();
    private final Handler progressHandler = new Handler();
    private final Runnable progressTicker = new Runnable() {
        @Override
        public void run() {
            if (isPlaying()) {
                updatePlaybackState(PlaybackState.STATE_PLAYING);
                broadcastState();
                progressHandler.postDelayed(this, 1000);
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        library = MusicLibrary.load(this);
        effectSettings = new AudioEffectSettings(this);
        createChannel();
        mediaSession = new MediaSession(this, "BluetoothMusicPlayer");
        mediaSession.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
        mediaSession.setCallback(new MediaSession.Callback() {
            @Override
            public void onPlay() {
                play();
            }

            @Override
            public void onPause() {
                pause();
            }

            @Override
            public void onSkipToNext() {
                next();
            }

            @Override
            public void onSkipToPrevious() {
                previous();
            }

            @Override
            public void onSeekTo(long pos) {
                seekTo((int) pos);
            }

            @Override
            public void onPlayFromMediaId(String mediaId, android.os.Bundle extras) {
                long id = parsePlayableId(mediaId);
                if (id > 0) {
                    playId(id, null);
                }
            }
        });
        mediaSession.setActive(true);
        updatePlaybackState(PlaybackState.STATE_NONE);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            if (ACTION_PLAY_ID.equals(action)) {
                playId(intent.getLongExtra(EXTRA_TRACK_ID, -1), intent.getLongArrayExtra(EXTRA_TRACK_IDS));
            } else if (ACTION_PLAY_QUEUE.equals(action)) {
                long[] ids = intent.getLongArrayExtra(EXTRA_TRACK_IDS);
                if (ids != null && ids.length > 0) {
                    playId(ids[0], ids);
                }
            } else if (ACTION_TOGGLE.equals(action)) {
                toggle();
            } else if (ACTION_NEXT.equals(action)) {
                next();
            } else if (ACTION_PREVIOUS.equals(action)) {
                previous();
            } else if (ACTION_SEEK.equals(action)) {
                seekTo(intent.getIntExtra(EXTRA_SEEK_TO, 0));
            } else if (ACTION_FAST_FORWARD.equals(action)) {
                seekRelative(10000);
            } else if (ACTION_REWIND.equals(action)) {
                seekRelative(-10000);
            } else if (ACTION_TOGGLE_SHUFFLE.equals(action)) {
                shuffle = !shuffle;
                updateAll();
            } else if (ACTION_TOGGLE_REPEAT.equals(action)) {
                repeatMode = (repeatMode + 1) % 3;
                updateAll();
            } else if (ACTION_APPLY_AUDIO_SETTINGS.equals(action)) {
                effectSettings = new AudioEffectSettings(this);
                updateAll();
            } else if (ACTION_STOP.equals(action)) {
                stopPlayback();
            }
        }
        return START_STICKY;
    }

    private void playId(long id, long[] newQueue) {
        MusicTrack track = library.find(id);
        if (track == null) {
            library = MusicLibrary.load(this);
            track = library.find(id);
        }
        if (track == null) {
            return;
        }
        if (newQueue != null && newQueue.length > 0) {
            queue.clear();
            for (long item : newQueue) {
                queue.add(item);
            }
            queueIndex = queue.indexOf(id);
        } else if (queue.isEmpty()) {
            queue.clear();
            for (MusicTrack item : library.allTracks()) {
                queue.add(item.id);
            }
            queueIndex = queue.indexOf(id);
        }
        currentTrack = track;
        prepared = false;
        stopProgressTicker();
        releasePlayer();
        mediaPlayer = new MediaPlayer();
        try {
            mediaPlayer.setAudioAttributes(new AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build());
            mediaPlayer.setDataSource(this, track.contentUri);
            mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mp) {
                    prepared = true;
                    attachAudioEffects();
                    applyPlaybackSpeed();
                    mp.start();
                    updateAll();
                }
            });
            mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    if (repeatMode == 1) {
                        seekTo(0);
                        play();
                    } else {
                        next();
                    }
                }
            });
            mediaPlayer.prepareAsync();
            updateMetadata();
            updatePlaybackState(PlaybackState.STATE_BUFFERING);
            broadcastState();
            showNotification();
        } catch (Exception ignored) {
            releasePlayer();
        }
    }

    private void toggle() {
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            pause();
        } else {
            play();
        }
    }

    private void play() {
        if (mediaPlayer != null && prepared) {
            applyPlaybackSpeed();
            mediaPlayer.start();
            updateAll();
        } else if (currentTrack != null) {
            playId(currentTrack.id, toArray(queue));
        }
    }

    private void pause() {
        if (mediaPlayer != null && prepared && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
        }
        updateAll();
    }

    private void stopPlayback() {
        pause();
        stopProgressTicker();
        stopForeground(false);
        broadcastState();
    }

    private void next() {
        if (queue.isEmpty()) {
            return;
        }
        if (shuffle && queue.size() > 1) {
            int nextIndex = queueIndex;
            while (nextIndex == queueIndex) {
                nextIndex = random.nextInt(queue.size());
            }
            queueIndex = nextIndex;
        } else if (queueIndex >= queue.size() - 1 && repeatMode == 2) {
            queueIndex = 0;
        } else if (queueIndex >= queue.size() - 1) {
            pause();
            seekTo(0);
            return;
        } else {
            queueIndex = queueIndex < 0 ? 0 : queueIndex + 1;
        }
        playId(queue.get(queueIndex), toArray(queue));
    }

    private void previous() {
        if (queue.isEmpty()) {
            return;
        }
        queueIndex = queueIndex < 0 ? 0 : (queueIndex - 1 + queue.size()) % queue.size();
        playId(queue.get(queueIndex), toArray(queue));
    }

    private void seekTo(int positionMs) {
        if (mediaPlayer != null && prepared) {
            mediaPlayer.seekTo(Math.max(0, positionMs));
            updateAll();
        }
    }

    private void seekRelative(int deltaMs) {
        if (mediaPlayer != null && prepared) {
            int duration = mediaPlayer.getDuration();
            int target = Math.max(0, Math.min(duration, mediaPlayer.getCurrentPosition() + deltaMs));
            mediaPlayer.seekTo(target);
            updateAll();
        }
    }

    private void updateAll() {
        if (prepared) {
            applyPlaybackSpeed();
            applyAudioEffects();
        }
        updateMetadata();
        updatePlaybackState(isPlaying() ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED);
        showNotification();
        broadcastState();
        if (isPlaying()) {
            startProgressTicker();
        } else {
            stopProgressTicker();
        }
    }

    private void updateMetadata() {
        if (currentTrack == null || mediaSession == null) {
            return;
        }
        MediaMetadata.Builder builder = new MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_MEDIA_ID, "song:" + currentTrack.id)
                .putString(MediaMetadata.METADATA_KEY_TITLE, currentTrack.title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, currentTrack.artist)
                .putString(MediaMetadata.METADATA_KEY_ALBUM, currentTrack.album)
                .putLong(MediaMetadata.METADATA_KEY_DURATION, currentTrack.durationMs);
        Bitmap art = loadArt(currentTrack.albumArtUri);
        if (art != null) {
            builder.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, art);
        }
        mediaSession.setMetadata(builder.build());
    }

    private void updatePlaybackState(int state) {
        if (mediaSession == null) {
            return;
        }
        long actions = PlaybackState.ACTION_PLAY
                | PlaybackState.ACTION_PAUSE
                | PlaybackState.ACTION_PLAY_PAUSE
                | PlaybackState.ACTION_SKIP_TO_NEXT
                | PlaybackState.ACTION_SKIP_TO_PREVIOUS
                | PlaybackState.ACTION_SEEK_TO
                | PlaybackState.ACTION_PLAY_FROM_MEDIA_ID;
        long position = currentPosition();
        mediaSession.setPlaybackState(new PlaybackState.Builder()
                .setActions(actions)
                .setState(state, position, state == PlaybackState.STATE_PLAYING ? 1f : 0f)
                .build());
    }

    private void showNotification() {
        if (currentTrack == null) {
            return;
        }
        Intent openIntent = new Intent(this, MainActivity.class);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 1, openIntent, pendingFlags());

        Notification.Action previous = action(android.R.drawable.ic_media_previous, "Previous", ACTION_PREVIOUS, 2);
        Notification.Action playPause = action(isPlaying() ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play,
                isPlaying() ? "Pause" : "Play", ACTION_TOGGLE, 3);
        Notification.Action next = action(android.R.drawable.ic_media_next, "Next", ACTION_NEXT, 4);

        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        builder.setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle(currentTrack.title)
                .setContentText(currentTrack.artist)
                .setContentIntent(contentIntent)
                .setShowWhen(false)
                .setOngoing(isPlaying())
                .addAction(previous)
                .addAction(playPause)
                .addAction(next)
                .setStyle(new Notification.MediaStyle()
                        .setMediaSession(mediaSession.getSessionToken())
                        .setShowActionsInCompactView(0, 1, 2));
        Bitmap art = loadArt(currentTrack.albumArtUri);
        if (art != null) {
            builder.setLargeIcon(art);
        }
        startForeground(NOTIFICATION_ID, builder.build());
    }

    private Notification.Action action(int icon, String title, String serviceAction, int requestCode) {
        Intent intent = new Intent(this, PlayerService.class).setAction(serviceAction);
        PendingIntent pendingIntent = PendingIntent.getService(this, requestCode, intent, pendingFlags());
        return new Notification.Action.Builder(icon, title, pendingIntent).build();
    }

    private int pendingFlags() {
        return Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT : PendingIntent.FLAG_UPDATE_CURRENT;
    }

    private void broadcastState() {
        Intent intent = new Intent(ACTION_STATE_CHANGED);
        intent.setPackage(getPackageName());
        if (currentTrack != null) {
            intent.putExtra(EXTRA_TRACK_ID, currentTrack.id);
            intent.putExtra(EXTRA_TITLE, currentTrack.title);
            intent.putExtra(EXTRA_ARTIST, currentTrack.artist);
            intent.putExtra(EXTRA_DURATION, currentTrack.durationMs);
        }
        intent.putExtra(EXTRA_PLAYING, isPlaying());
        intent.putExtra(EXTRA_POSITION, currentPosition());
        intent.putExtra(EXTRA_SHUFFLE, shuffle);
        intent.putExtra(EXTRA_REPEAT_MODE, repeatMode);
        sendBroadcast(intent);
    }

    private void startProgressTicker() {
        progressHandler.removeCallbacks(progressTicker);
        progressHandler.postDelayed(progressTicker, 1000);
    }

    private void stopProgressTicker() {
        progressHandler.removeCallbacks(progressTicker);
    }

    private boolean isPlaying() {
        return mediaPlayer != null && prepared && mediaPlayer.isPlaying();
    }

    private long currentPosition() {
        if (mediaPlayer != null && prepared) {
            return mediaPlayer.getCurrentPosition();
        }
        return 0;
    }

    private Bitmap loadArt(Uri uri) {
        try (InputStream stream = getContentResolver().openInputStream(uri)) {
            return stream == null ? null : BitmapFactory.decodeStream(stream);
        } catch (Exception ignored) {
            return null;
        }
    }

    private long parsePlayableId(String mediaId) {
        if (mediaId == null || !mediaId.startsWith("song:")) {
            return -1;
        }
        try {
            return Long.parseLong(mediaId.substring("song:".length()));
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private long[] toArray(List<Long> ids) {
        long[] array = new long[ids.size()];
        for (int i = 0; i < ids.size(); i++) {
            array[i] = ids.get(i);
        }
        return array;
    }

    private void releasePlayer() {
        releaseAudioEffects();
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
        prepared = false;
    }

    private void attachAudioEffects() {
        releaseAudioEffects();
        if (mediaPlayer == null) {
            return;
        }
        int sessionId = mediaPlayer.getAudioSessionId();
        try {
            equalizer = new Equalizer(0, sessionId);
        } catch (RuntimeException ignored) {
            equalizer = null;
        }
        try {
            bassBoost = new BassBoost(0, sessionId);
        } catch (RuntimeException ignored) {
            bassBoost = null;
        }
        try {
            virtualizer = new Virtualizer(0, sessionId);
        } catch (RuntimeException ignored) {
            virtualizer = null;
        }
        if (Build.VERSION.SDK_INT >= 19) {
            try {
                loudnessEnhancer = new LoudnessEnhancer(sessionId);
            } catch (RuntimeException ignored) {
                loudnessEnhancer = null;
            }
        }
        applyAudioEffects();
    }

    private void applyAudioEffects() {
        if (effectSettings == null) {
            return;
        }
        boolean enabled = effectSettings.enabled();
        if (equalizer != null) {
            try {
                equalizer.setEnabled(enabled);
                int preset = effectSettings.preset();
                if (enabled && preset >= 0 && preset < equalizer.getNumberOfPresets()) {
                    equalizer.usePreset((short) preset);
                }
            } catch (RuntimeException ignored) {
            }
        }
        if (bassBoost != null) {
            try {
                bassBoost.setEnabled(enabled && effectSettings.bass() > 0);
                bassBoost.setStrength((short) Math.max(0, Math.min(1000, effectSettings.bass())));
            } catch (RuntimeException ignored) {
            }
        }
        if (virtualizer != null) {
            try {
                virtualizer.setEnabled(enabled && effectSettings.virtualizer() > 0);
                virtualizer.setStrength((short) Math.max(0, Math.min(1000, effectSettings.virtualizer())));
            } catch (RuntimeException ignored) {
            }
        }
        if (loudnessEnhancer != null && Build.VERSION.SDK_INT >= 19) {
            try {
                loudnessEnhancer.setEnabled(enabled && effectSettings.loudness() > 0);
                loudnessEnhancer.setTargetGain(Math.max(0, Math.min(1500, effectSettings.loudness())));
            } catch (RuntimeException ignored) {
            }
        }
    }

    private void applyPlaybackSpeed() {
        if (mediaPlayer == null || !prepared || Build.VERSION.SDK_INT < 23 || effectSettings == null) {
            return;
        }
        try {
            PlaybackParams params = mediaPlayer.getPlaybackParams();
            params.setSpeed(effectSettings.speed());
            mediaPlayer.setPlaybackParams(params);
        } catch (RuntimeException ignored) {
        }
    }

    private void releaseAudioEffects() {
        if (equalizer != null) {
            equalizer.release();
            equalizer = null;
        }
        if (bassBoost != null) {
            bassBoost.release();
            bassBoost = null;
        }
        if (virtualizer != null) {
            virtualizer.release();
            virtualizer = null;
        }
        if (loudnessEnhancer != null) {
            loudnessEnhancer.release();
            loudnessEnhancer = null;
        }
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, getString(R.string.notification_channel_playback), NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Music playback controls");
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    @Override
    public void onDestroy() {
        stopProgressTicker();
        releasePlayer();
        if (mediaSession != null) {
            mediaSession.release();
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
