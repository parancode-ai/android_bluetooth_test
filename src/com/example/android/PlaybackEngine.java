package com.example.android;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.SystemClock;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Generates simple sine wave audio so the player can drive A2DP devices without bundling assets.
 */
class PlaybackEngine {
    static class TrackInfo {
        final String title;
        final String artist;
        final String album;
        final String fileName;
        final long durationMs;

        TrackInfo(String title, String artist, String album, String fileName, long durationMs) {
            this.title = title;
            this.artist = artist;
            this.album = album;
            this.fileName = fileName;
            this.durationMs = durationMs;
        }
    }

    interface Listener {
        void onTrackChanged(TrackInfo trackInfo);
    }

    private static final int SAMPLE_RATE = 44_100;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_OUT_STEREO;
    private static final int ENCODING = AudioFormat.ENCODING_PCM_16BIT;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final List<TrackInfo> playlist = new ArrayList<>();
    private final Listener listener;

    private int trackIndex = 0;
    private boolean playing;
    private long playbackStartRealtime;
    private long playbackStartPosition;
    private Future<?> playbackFuture;

    PlaybackEngine(Listener listener) {
        this.listener = listener;
        playlist.add(new TrackInfo("Sample Track One", "Example Artist", "Demo Album", "sample_one.wav", 180_000));
        playlist.add(new TrackInfo("Sample Track Two", "Example Artist", "Demo Album", "sample_two.wav", 200_000));
        playlist.add(new TrackInfo("Extended Mix", "Example Artist", "Live Session", "extended_mix.wav", 240_000));
    }

    TrackInfo currentTrack() {
        return playlist.get(trackIndex);
    }

    void play() {
        if (playing) {
            return;
        }
        playing = true;
        playbackStartRealtime = SystemClock.elapsedRealtime();
        startTone();
    }

    void pause() {
        playing = false;
        stopTone();
        playbackStartPosition = getPositionInternal();
    }

    void stop() {
        playing = false;
        stopTone();
        playbackStartPosition = 0;
    }

    void skipToNext() {
        trackIndex = (trackIndex + 1) % playlist.size();
        resetPosition();
    }

    void skipToPrevious() {
        trackIndex = (trackIndex - 1 + playlist.size()) % playlist.size();
        resetPosition();
    }

    void fastForward() {
        seekTo(Math.min(currentTrack().durationMs, getPosition() + 10_000));
    }

    void rewind() {
        seekTo(Math.max(0, getPosition() - 10_000));
    }

    void seekTo(long positionMs) {
        playbackStartPosition = positionMs;
        playbackStartRealtime = SystemClock.elapsedRealtime();
    }

    long getPosition() {
        return getPositionInternal();
    }

    boolean isPlaying() {
        return playing;
    }

    private void resetPosition() {
        playbackStartPosition = 0;
        playbackStartRealtime = SystemClock.elapsedRealtime();
        if (listener != null) {
            listener.onTrackChanged(currentTrack());
        }
        if (playing) {
            restartTone();
        }
    }

    private long getPositionInternal() {
        if (!playing) {
            return playbackStartPosition;
        }
        long elapsed = SystemClock.elapsedRealtime() - playbackStartRealtime;
        long position = playbackStartPosition + elapsed;
        return Math.min(position, currentTrack().durationMs);
    }

    private void restartTone() {
        stopTone();
        startTone();
    }

    private void startTone() {
        playbackFuture = executor.submit(() -> {
            int bufferSize = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, ENCODING);
            if (bufferSize <= 0) {
                bufferSize = SAMPLE_RATE; // Fallback to a second of audio data.
            }
            AudioTrack track = new AudioTrack(
                    new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build(),
                    new AudioFormat.Builder()
                            .setEncoding(ENCODING)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(CHANNEL_CONFIG)
                            .build(),
                    bufferSize,
                    AudioTrack.MODE_STREAM,
                    AudioManager.AUDIO_SESSION_ID_GENERATE);
            track.play();

            short[] buffer = new short[bufferSize];
            double frequency = 880.0; // Audible tone suitable for debugging A2DP paths.
            double increment = 2 * Math.PI * frequency / SAMPLE_RATE;
            int idx = 0;
            while (playing && !Thread.currentThread().isInterrupted()) {
                for (int i = 0; i < buffer.length; i += 2) {
                    short sample = (short) (Math.sin(idx * increment) * Short.MAX_VALUE * 0.2);
                    buffer[i] = sample;
                    buffer[i + 1] = sample;
                    idx++;
                }
                track.write(buffer, 0, buffer.length);
                if (getPositionInternal() >= currentTrack().durationMs) {
                    skipToNext();
                }
            }
            track.stop();
            track.release();
        });
    }

    private void stopTone() {
        if (playbackFuture != null) {
            playbackFuture.cancel(true);
            playbackFuture = null;
        }
    }

    void release() {
        stop();
        executor.shutdownNow();
    }
}
