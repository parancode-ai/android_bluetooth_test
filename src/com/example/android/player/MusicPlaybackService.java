package com.example.android.player;

import android.annotation.Nullable;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.MediaDescription;
import android.media.MediaPlayer;
import android.media.browse.MediaBrowser;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.SystemClock;
import android.service.media.MediaBrowserService;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * MediaBrowserService implementation that exposes transport controls and metadata to Bluetooth A2DP
 * clients via MediaSession.
 */
public class MusicPlaybackService extends MediaBrowserService {

    private static final String CHANNEL_ID = "playback_channel";
    private static final int NOTIFICATION_ID = 1001;
    private static final long SEEK_DELTA_MS = 10_000L;

    private static final String ACTION_PLAY = "com.example.android.player.PLAY";
    private static final String ACTION_PAUSE = "com.example.android.player.PAUSE";
    private static final String ACTION_NEXT = "com.example.android.player.NEXT";
    private static final String ACTION_PREVIOUS = "com.example.android.player.PREVIOUS";
    private static final String ACTION_FAST_FORWARD = "com.example.android.player.FAST_FORWARD";
    private static final String ACTION_REWIND = "com.example.android.player.REWIND";

    private final List<Track> queue = new ArrayList<>();
    private MediaSession mediaSession;
    private MediaPlayer mediaPlayer;
    private int currentIndex = 0;
    private boolean hasAudioFocus = false;
    private AudioManager audioManager;

    @Override
    public void onCreate() {
        super.onCreate();
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        populateQueue();
        initMediaSession();
        createNotificationChannel();
    }

    @Nullable
    @Override
    public BrowserRoot onGetRoot(String clientPackageName, int clientUid, Bundle rootHints) {
        return new BrowserRoot("root", null);
    }

    @Override
    public void onLoadChildren(String parentId, Result<List<MediaBrowser.MediaItem>> result) {
        List<MediaBrowser.MediaItem> items = new ArrayList<>();
        for (int i = 0; i < queue.size(); i++) {
            Track track = queue.get(i);
            MediaDescription desc = new MediaDescription.Builder()
                    .setMediaId(String.valueOf(i))
                    .setTitle(track.getTitle())
                    .setSubtitle(track.getArtist())
                    .build();
            items.add(new MediaBrowser.MediaItem(desc, MediaBrowser.MediaItem.FLAG_PLAYABLE));
        }
        result.sendResult(items);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            handleIntentAction(intent.getAction());
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (mediaPlayer != null) {
            mediaPlayer.release();
        }
        if (mediaSession != null) {
            mediaSession.release();
        }
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return super.onBind(intent);
    }

    private void populateQueue() {
        queue.add(new Track(
                getString(R.string.media_title),
                getString(R.string.media_artist),
                getString(R.string.media_album),
                "/sdcard/Music/sample_track.wav",
                "/sdcard/Music/sample_cover.png",
                180_000L));
        queue.add(new Track(
                "Second Track",
                "Example Artist",
                "Example Album",
                "/sdcard/Music/second_track.wav",
                "/sdcard/Music/second_cover.png",
                200_000L));
    }

    private void initMediaSession() {
        mediaSession = new MediaSession(this, "BluetoothMusicPlayerSession");
        mediaSession.setCallback(new SessionCallback());
        mediaSession.setActive(true);
        mediaSession.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
        setSessionToken(mediaSession.getSessionToken());
        updateMetadata(queue.get(currentIndex));
        updatePlaybackState(PlaybackState.STATE_PAUSED);
    }

    private void updateMetadata(Track track) {
        Bitmap art = loadArtwork(track);
        MediaMetadata metadata = new MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, track.getTitle())
                .putString(MediaMetadata.METADATA_KEY_ARTIST, track.getArtist())
                .putString(MediaMetadata.METADATA_KEY_ALBUM, track.getAlbum())
                .putLong(MediaMetadata.METADATA_KEY_DURATION, track.getDurationMs())
                .putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, art)
                .build();
        mediaSession.setMetadata(metadata);
    }

    private void updatePlaybackState(int state) {
        long position = PlaybackState.PLAYBACK_POSITION_UNKNOWN;
        if (mediaPlayer != null) {
            position = mediaPlayer.getCurrentPosition();
        }
        PlaybackState playbackState = new PlaybackState.Builder()
                .setActions(
                        PlaybackState.ACTION_PLAY |
                                PlaybackState.ACTION_PLAY_PAUSE |
                                PlaybackState.ACTION_PAUSE |
                                PlaybackState.ACTION_SKIP_TO_NEXT |
                                PlaybackState.ACTION_SKIP_TO_PREVIOUS |
                                PlaybackState.ACTION_FAST_FORWARD |
                                PlaybackState.ACTION_REWIND |
                                PlaybackState.ACTION_STOP)
                .setState(state, position, 1.0f, SystemClock.elapsedRealtime())
                .build();
        mediaSession.setPlaybackState(playbackState);
        if (state == PlaybackState.STATE_PLAYING) {
            startForeground(NOTIFICATION_ID, buildNotification(playbackState));
        } else {
            stopForeground(false);
            Notification notification = buildNotification(playbackState);
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            manager.notify(NOTIFICATION_ID, notification);
        }
    }

    private Notification buildNotification(PlaybackState state) {
        MediaController controller = mediaSession.getController();
        MediaMetadata metadata = controller.getMetadata();
        MediaDescription description = metadata != null ? metadata.getDescription() : null;

        Notification.Builder builder = new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle(description != null ? description.getTitle() : getString(R.string.media_title))
                .setContentText(description != null ? description.getSubtitle() : getString(R.string.media_artist))
                .setSmallIcon(R.drawable.ic_album_placeholder)
                .setLargeIcon(description != null ? description.getIconBitmap() : null)
                .setContentIntent(controller.getSessionActivity())
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setStyle(new Notification.MediaStyle().setMediaSession(mediaSession.getSessionToken()).setShowActionsInCompactView(1, 2, 3));

        builder.addAction(new Notification.Action(
                android.R.drawable.ic_media_previous,
                getString(R.string.previous),
                buildServicePendingIntent(ACTION_PREVIOUS)));

        if (state.getState() == PlaybackState.STATE_PLAYING) {
            builder.addAction(new Notification.Action(
                    android.R.drawable.ic_media_pause,
                    getString(R.string.pause),
                    buildServicePendingIntent(ACTION_PAUSE)));
        } else {
            builder.addAction(new Notification.Action(
                    android.R.drawable.ic_media_play,
                    getString(R.string.play),
                    buildServicePendingIntent(ACTION_PLAY)));
        }

        builder.addAction(new Notification.Action(
                android.R.drawable.ic_media_next,
                getString(R.string.next),
                buildServicePendingIntent(ACTION_NEXT)));

        builder.addAction(new Notification.Action(
                android.R.drawable.ic_media_ff,
                getString(R.string.fast_forward),
                buildServicePendingIntent(ACTION_FAST_FORWARD)));

        builder.addAction(new Notification.Action(
                android.R.drawable.ic_media_rew,
                getString(R.string.rewind),
                buildServicePendingIntent(ACTION_REWIND)));

        return builder.build();
    }

    private PendingIntent buildServicePendingIntent(String action) {
        Intent intent = new Intent(this, MusicPlaybackService.class);
        intent.setAction(action);
        return PendingIntent.getService(this, action.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.notification_channel_name),
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription(getString(R.string.notification_channel_description));
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            manager.createNotificationChannel(channel);
        }
    }

    private boolean requestAudioFocus() {
        if (hasAudioFocus) {
            return true;
        }
        AudioAttributes attrs = new AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .build();
        AudioFocusRequest focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attrs)
                .setOnAudioFocusChangeListener(focusChange -> {
                    if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
                        pause();
                    }
                })
                .build();
        int result = audioManager.requestAudioFocus(focusRequest);
        hasAudioFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        return hasAudioFocus;
    }

    private void abandonAudioFocus() {
        if (!hasAudioFocus) {
            return;
        }
        audioManager.abandonAudioFocus(null);
        hasAudioFocus = false;
    }

    private void play() {
        if (!requestAudioFocus()) {
            return;
        }
        Track track = queue.get(currentIndex);
        if (mediaPlayer == null) {
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setOnCompletionListener(mp -> skipToNext());
        } else {
            mediaPlayer.reset();
        }
        try {
            mediaPlayer.setAudioAttributes(new AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build());
            mediaPlayer.setDataSource(track.getAudioPath());
            mediaPlayer.prepare();
            mediaPlayer.start();
            updateMetadata(track);
            updatePlaybackState(PlaybackState.STATE_PLAYING);
        } catch (IOException e) {
            updatePlaybackState(PlaybackState.STATE_ERROR);
        }
    }

    private void pause() {
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
        }
        updatePlaybackState(PlaybackState.STATE_PAUSED);
    }

    private void skipToPrevious() {
        currentIndex = (currentIndex - 1 + queue.size()) % queue.size();
        play();
    }

    private void skipToNext() {
        currentIndex = (currentIndex + 1) % queue.size();
        play();
    }

    private void fastForward() {
        if (mediaPlayer != null) {
            int newPosition = (int) Math.min(mediaPlayer.getCurrentPosition() + SEEK_DELTA_MS, mediaPlayer.getDuration());
            mediaPlayer.seekTo(newPosition);
        }
    }

    private void rewind() {
        if (mediaPlayer != null) {
            int newPosition = (int) Math.max(mediaPlayer.getCurrentPosition() - SEEK_DELTA_MS, 0);
            mediaPlayer.seekTo(newPosition);
        }
    }

    private Bitmap loadArtwork(Track track) {
        if (track.getArtwork() != null) {
            return track.getArtwork();
        }
        Bitmap bitmap = BitmapFactory.decodeFile(track.getArtworkPath());
        if (bitmap == null) {
            bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            canvas.drawColor(Color.DKGRAY);
            Paint paint = new Paint();
            paint.setColor(Color.CYAN);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(72f);
            canvas.drawText("BT", 256f, 276f, paint);
        }
        track.setArtwork(bitmap);
        return bitmap;
    }

    private void handleIntentAction(String action) {
        switch (action) {
            case ACTION_PLAY:
                mediaSession.getController().getTransportControls().play();
                break;
            case ACTION_PAUSE:
                mediaSession.getController().getTransportControls().pause();
                break;
            case ACTION_NEXT:
                mediaSession.getController().getTransportControls().skipToNext();
                break;
            case ACTION_PREVIOUS:
                mediaSession.getController().getTransportControls().skipToPrevious();
                break;
            case ACTION_FAST_FORWARD:
                mediaSession.getController().getTransportControls().fastForward();
                break;
            case ACTION_REWIND:
                mediaSession.getController().getTransportControls().rewind();
                break;
            default:
                break;
        }
    }

    private class SessionCallback extends MediaSession.Callback {
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
            skipToNext();
        }

        @Override
        public void onSkipToPrevious() {
            skipToPrevious();
        }

        @Override
        public void onFastForward() {
            fastForward();
        }

        @Override
        public void onRewind() {
            rewind();
        }

        @Override
        public void onStop() {
            if (mediaPlayer != null) {
                mediaPlayer.stop();
            }
            abandonAudioFocus();
            updatePlaybackState(PlaybackState.STATE_STOPPED);
        }

        @Override
        public void onPlayFromMediaId(String mediaId, Bundle extras) {
            int index = Integer.parseInt(mediaId);
            if (index >= 0 && index < queue.size()) {
                currentIndex = index;
                play();
            }
        }
    }
}
