package com.paran.music;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public class MainActivity extends Activity {
    private static final int REQUEST_PERMISSIONS = 20;
    private static final int REQUEST_FOLDER = 21;
    private static final String PREFS_APP = "app_prefs";
    private static final String KEY_BT_GUIDE_SEEN = "bt_guide_seen";
    private static final int COPPER = Color.rgb(183, 96, 52);
    private static final int BLUE = Color.rgb(46, 115, 216);
    private static final int BG = Color.rgb(244, 244, 244);
    private static final int TEXT = Color.rgb(17, 17, 17);
    private static final int MUTED = Color.rgb(120, 120, 120);

    private MusicLibrary library;
    private PlaylistStore playlists;
    private LinearLayout root;
    private FrameLayout content;
    private LinearLayout miniPlayer;
    private TextView miniTitle;
    private TextView miniArtist;
    private ImageView miniArt;
    private ImageButton miniPlay;
    private String selectedTab = "곡";
    private MusicTrack currentTrack;
    private boolean playing;
    private long positionMs;
    private long durationMs;
    private SeekBar playerSeek;
    private TextView playerTime;
    private boolean playerVisible;
    private boolean shuffle;
    private int repeatMode;
    private boolean scanRequestedOnce;
    private final Handler handler = new Handler();
    private final List<MusicTrack> currentQueue = new ArrayList<>();
    private Runnable holdSeekRunnable;
    private Runnable sleepTimerRunnable;
    private boolean receiverRegistered;

    private final BroadcastReceiver stateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            long id = intent.getLongExtra(PlayerService.EXTRA_TRACK_ID, -1);
            long oldId = currentTrack == null ? -1 : currentTrack.id;
            currentTrack = library == null ? null : library.find(id);
            playing = intent.getBooleanExtra(PlayerService.EXTRA_PLAYING, false);
            positionMs = intent.getLongExtra(PlayerService.EXTRA_POSITION, 0);
            durationMs = intent.getLongExtra(PlayerService.EXTRA_DURATION, currentTrack == null ? 0 : currentTrack.durationMs);
            shuffle = intent.getBooleanExtra(PlayerService.EXTRA_SHUFFLE, false);
            repeatMode = intent.getIntExtra(PlayerService.EXTRA_REPEAT_MODE, 0);
            updateMiniPlayer();
            if (playerVisible && currentTrack != null && oldId != currentTrack.id) {
                showPlayerScreen();
            } else {
                updatePlayerProgress();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window window = getWindow();
        window.setStatusBarColor(BG);
        window.setNavigationBarColor(BG);
        if (Build.VERSION.SDK_INT >= 23) {
            window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }
        playlists = new PlaylistStore(this);
        if (!getSharedPreferences(PREFS_APP, MODE_PRIVATE).getBoolean(KEY_BT_GUIDE_SEEN, false)) {
            showBluetoothGuideScreen();
            return;
        }
        continueAfterGuide();
    }

    private void continueAfterGuide() {
        if (hasAudioPermission()) {
            loadAndShow();
        } else {
            showPermissionScreen();
            requestNeededPermissions();
        }
    }

    private void showBluetoothGuideScreen() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);

        LinearLayout screen = new LinearLayout(this);
        screen.setOrientation(LinearLayout.VERTICAL);
        screen.setGravity(Gravity.CENTER_VERTICAL);
        screen.setPadding(dp(26), dp(34), dp(26), dp(34));
        scroll.addView(screen, new ScrollView.LayoutParams(-1, -2));

        TextView title = text("BluetoothMusicPlayer", 28, TEXT, true);
        TextView subtitle = text("Bluetooth AVRCP 사용 전 확인", 22, TEXT, true);
        TextView body = text(
                "차량이나 블루투스 기기에서 곡 목록을 보려면 폰의 AVRCP Browser 지원이 필요합니다.\n\n"
                        + "이 앱은 Songs, Albums, Artists, Folders, Playlists, Favorites 목록을 Android MediaBrowserService로 제공합니다.\n\n"
                        + "AVRCP 버전은 앱에서 직접 바꿀 수 없습니다. 삼성폰은 보통 개발자 옵션의 Bluetooth AVRCP 버전에서 확인할 수 있고, 목록 탐색은 AVRCP 1.4 이상을 권장합니다.\n\n"
                        + "음악이 보이지 않으면 설정 화면에서 음악 폴더 스캔 또는 음악 폴더 선택을 사용하세요.",
                16,
                MUTED,
                false);
        body.setLineSpacing(dp(2), 1.0f);

        Button start = new Button(this);
        start.setText("시작하기");
        start.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getSharedPreferences(PREFS_APP, MODE_PRIVATE).edit().putBoolean(KEY_BT_GUIDE_SEEN, true).apply();
                continueAfterGuide();
            }
        });

        Button developerSettings = new Button(this);
        developerSettings.setText("개발자 옵션 열기");
        developerSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    startActivity(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));
                } catch (RuntimeException ignored) {
                    Toast.makeText(MainActivity.this, "폰 설정에서 개발자 옵션을 열어주세요", Toast.LENGTH_SHORT).show();
                }
            }
        });

        screen.addView(title, lp(-1, -2, 0, 0, 0, dp(18)));
        screen.addView(subtitle, lp(-1, -2, 0, 0, 0, dp(16)));
        screen.addView(body, lp(-1, -2, 0, 0, 0, dp(24)));
        screen.addView(start, lp(-1, dp(52), 0, 0, 0, dp(10)));
        screen.addView(developerSettings, lp(-1, dp(52)));
        setContentView(scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(stateReceiver, new IntentFilter(PlayerService.ACTION_STATE_CHANGED), Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(stateReceiver, new IntentFilter(PlayerService.ACTION_STATE_CHANGED));
        }
        receiverRegistered = true;
        handler.post(progressTicker);
    }

    @Override
    protected void onPause() {
        if (receiverRegistered) {
            unregisterReceiver(stateReceiver);
            receiverRegistered = false;
        }
        handler.removeCallbacks(progressTicker);
        stopHoldSeek();
        super.onPause();
    }

    private final Runnable progressTicker = new Runnable() {
        @Override
        public void run() {
            if (playing) {
                positionMs += 1000;
                updatePlayerProgress();
            }
            handler.postDelayed(this, 1000);
        }
    };

    private void loadAndShow() {
        library = MusicLibrary.load(this);
        buildLibraryScreen();
        if (library.allTracks().isEmpty() && !scanRequestedOnce) {
            scanRequestedOnce = true;
            requestMusicScan(false);
        }
    }

    private boolean hasAudioPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            return checkSelfPermission(Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED;
        }
        return checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestNeededPermissions() {
        List<String> permissions = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= 33) {
            permissions.add(Manifest.permission.READ_MEDIA_AUDIO);
            permissions.add(Manifest.permission.POST_NOTIFICATIONS);
        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        }
        requestPermissions(permissions.toArray(new String[0]), REQUEST_PERMISSIONS);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSIONS && hasAudioPermission()) {
            loadAndShow();
        } else if (requestCode == REQUEST_PERMISSIONS) {
            showPermissionScreen();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_FOLDER || resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        Uri treeUri = data.getData();
        try {
            getContentResolver().takePersistableUriPermission(treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (RuntimeException ignored) {
        }
        SharedPreferences prefs = getSharedPreferences(MusicLibrary.PREFS_FOLDERS, MODE_PRIVATE);
        Set<String> saved = new LinkedHashSet<>(prefs.getStringSet(MusicLibrary.KEY_TREE_URIS, new LinkedHashSet<String>()));
        saved.add(treeUri.toString());
        prefs.edit().putStringSet(MusicLibrary.KEY_TREE_URIS, saved).apply();
        Toast.makeText(this, "선택한 폴더를 음악 목록에 추가했습니다", Toast.LENGTH_SHORT).show();
        loadAndShow();
    }

    private void showPermissionScreen() {
        LinearLayout screen = new LinearLayout(this);
        screen.setOrientation(LinearLayout.VERTICAL);
        screen.setGravity(Gravity.CENTER);
        screen.setPadding(dp(28), dp(28), dp(28), dp(28));
        screen.setBackgroundColor(BG);

        TextView title = text("BluetoothMusicPlayer", 26, TEXT, true);
        TextView message = text("폰의 음악 파일을 읽기 위해 오디오 권한이 필요합니다.", 17, MUTED, false);
        message.setGravity(Gravity.CENTER);
        Button allow = new Button(this);
        allow.setText("권한 허용");
        allow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                requestNeededPermissions();
            }
        });
        Button settings = new Button(this);
        settings.setText("앱 설정 열기");
        settings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            }
        });
        screen.addView(title, lp(-1, -2, 0, 0, 0, dp(16)));
        screen.addView(message, lp(-1, -2, 0, 0, 0, dp(22)));
        screen.addView(allow, lp(-1, dp(48), 0, 0, 0, dp(10)));
        screen.addView(settings, lp(-1, dp(48)));
        setContentView(screen);
    }

    private void buildLibraryScreen() {
        playerVisible = false;
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(22), dp(22), dp(16), dp(8));
        TextView title = text("BluetoothMusicPlayer", 25, TEXT, true);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        TextView add = text("+", 34, TEXT, false);
        add.setGravity(Gravity.CENTER);
        add.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                promptCreatePlaylist();
            }
        });
        header.addView(add, new LinearLayout.LayoutParams(dp(56), dp(56)));
        root.addView(header);

        root.addView(tabBar(), lp(-1, dp(54)));
        content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));
        root.addView(buildMiniPlayer(), lp(-1, dp(76), dp(12), 0, dp(12), dp(8)));
        root.addView(bottomNav(), lp(-1, dp(68)));
        setContentView(root);
        renderSelectedTab();
        updateMiniPlayer();
    }

    private View tabBar() {
        HorizontalScroll tabs = new HorizontalScroll(this);
        String[] labels = {"좋아요", "플레이리스트", "곡", "앨범", "아티스트", "폴더", "검색", "설정"};
        for (String label : labels) {
            TextView tab = text(label, label.equals(selectedTab) ? 22 : 17, label.equals(selectedTab) ? TEXT : MUTED, label.equals(selectedTab));
            tab.setGravity(Gravity.CENTER);
            tab.setPadding(dp(10), 0, dp(10), 0);
            tab.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    selectedTab = ((TextView) v).getText().toString();
                    buildLibraryScreen();
                }
            });
            tabs.row.addView(tab, new LinearLayout.LayoutParams(-2, -1));
        }
        return tabs;
    }

    private View bottomNav() {
        LinearLayout nav = new LinearLayout(this);
        nav.setGravity(Gravity.CENTER);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setBackgroundColor(BG);
        String[] labels = {"마이 뮤직", "검색", "설정"};
        for (String label : labels) {
            TextView item = text(label, 15, "마이 뮤직".equals(label) ? BLUE : TEXT, false);
            item.setGravity(Gravity.CENTER);
            item.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    String value = ((TextView) v).getText().toString();
                    selectedTab = "마이 뮤직".equals(value) ? "곡" : value;
                    buildLibraryScreen();
                }
            });
            nav.addView(item, new LinearLayout.LayoutParams(0, -1, 1));
        }
        return nav;
    }

    private View buildMiniPlayer() {
        miniPlayer = new LinearLayout(this);
        miniPlayer.setOrientation(LinearLayout.HORIZONTAL);
        miniPlayer.setGravity(Gravity.CENTER_VERTICAL);
        miniPlayer.setPadding(dp(12), dp(8), dp(8), dp(8));
        miniPlayer.setBackground(round(COPPER, dp(30)));
        miniPlayer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (currentTrack != null) {
                    showPlayerScreen();
                }
            }
        });
        miniArt = new ImageView(this);
        miniArt.setScaleType(ImageView.ScaleType.CENTER_CROP);
        miniArt.setBackground(round(Color.rgb(220, 220, 220), dp(18)));
        miniPlayer.addView(miniArt, lp(dp(56), dp(56), 0, 0, dp(10), 0));
        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        miniTitle = text("재생 중인 곡 없음", 17, Color.WHITE, true);
        miniArtist = text("", 14, Color.WHITE, false);
        labels.addView(miniTitle);
        labels.addView(miniArtist);
        miniPlayer.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
        miniPlayer.addView(control("⏮", PlayerService.ACTION_PREVIOUS), lp(dp(44), dp(52)));
        miniPlay = control("▶", PlayerService.ACTION_TOGGLE);
        miniPlayer.addView(miniPlay, lp(dp(44), dp(52)));
        miniPlayer.addView(control("⏭", PlayerService.ACTION_NEXT), lp(dp(44), dp(52)));
        ImageButton queue = iconButton("≡");
        queue.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showQueueScreen();
            }
        });
        miniPlayer.addView(queue, lp(dp(44), dp(52)));
        return miniPlayer;
    }

    private void renderSelectedTab() {
        content.removeAllViews();
        if ("검색".equals(selectedTab)) {
            renderSearch();
        } else if ("플레이리스트".equals(selectedTab)) {
            renderPlaylists();
        } else if ("앨범".equals(selectedTab)) {
            renderGroups("앨범", library.groupByAlbum());
        } else if ("아티스트".equals(selectedTab)) {
            renderGroups("아티스트", library.groupByArtist());
        } else if ("폴더".equals(selectedTab)) {
            renderGroups("폴더", library.groupByFolder());
        } else if ("좋아요".equals(selectedTab)) {
            renderTracks("좋아요", tracksFromIds(playlists.favorites()));
        } else if ("설정".equals(selectedTab)) {
            renderSettings();
        } else {
            renderTracks("곡", library.allTracks());
        }
    }

    private void renderTracks(String title, List<MusicTrack> tracks) {
        if (tracks.isEmpty()) {
            renderEmptyLibrary();
            return;
        }
        currentQueue.clear();
        currentQueue.addAll(tracks);
        ListView list = new ListView(this);
        list.setDividerHeight(1);
        list.setBackgroundColor(Color.WHITE);
        list.setAdapter(new TrackAdapter(tracks));
        content.addView(list);
    }

    private void renderGroups(String title, Map<String, List<MusicTrack>> groups) {
        List<GroupRow> rows = new ArrayList<>();
        for (Map.Entry<String, List<MusicTrack>> entry : groups.entrySet()) {
            rows.add(new GroupRow(entry.getKey(), entry.getValue()));
        }
        GridView grid = new GridView(this);
        grid.setNumColumns("앨범".equals(title) ? 2 : 1);
        grid.setVerticalSpacing(dp(8));
        grid.setHorizontalSpacing(dp(8));
        grid.setPadding(dp(12), dp(12), dp(12), dp(90));
        grid.setClipToPadding(false);
        grid.setBackgroundColor(Color.WHITE);
        grid.setAdapter(new GroupAdapter(rows, "앨범".equals(title)));
        content.addView(grid);
    }

    private void renderPlaylists() {
        List<PlaylistRow> rows = new ArrayList<>();
        rows.add(new PlaylistRow("플레이리스트 추가", "새 목록 만들기", 0, true));
        rows.add(new PlaylistRow("현재 재생목록", currentQueue.size() + "곡", 0, false));
        rows.add(new PlaylistRow("좋아요 한 곡", playlists.favorites().size() + "곡", 0, false));
        for (String name : playlists.playlistNames()) {
            rows.add(new PlaylistRow(name, playlists.playlistIds(name).size() + "곡", 0, false));
        }
        ListView list = new ListView(this);
        list.setBackgroundColor(Color.WHITE);
        list.setAdapter(new PlaylistAdapter(rows));
        content.addView(list);
    }

    private void renderSearch() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(12), dp(16), 0);
        box.setBackgroundColor(Color.WHITE);
        EditText search = new EditText(this);
        search.setHint("곡, 앨범, 아티스트, 폴더 검색");
        search.setSingleLine(true);
        search.setTextSize(18);
        ListView results = new ListView(this);
        TrackAdapter adapter = new TrackAdapter(library.allTracks());
        results.setAdapter(adapter);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                adapter.setTracks(library.search(s.toString()));
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        box.addView(search, lp(-1, dp(56)));
        box.addView(results, new LinearLayout.LayoutParams(-1, 0, 1));
        content.addView(box);
    }

    private void renderSettings() {
        AudioEffectSettings effects = new AudioEffectSettings(this);
        ScrollView scroll = new ScrollView(this);
        LinearLayout settings = new LinearLayout(this);
        settings.setOrientation(LinearLayout.VERTICAL);
        settings.setPadding(dp(22), dp(22), dp(22), dp(22));
        settings.setBackgroundColor(Color.WHITE);
        settings.addView(text("설정", 26, TEXT, true), lp(-1, -2, 0, 0, 0, dp(18)));
        settings.addView(text("세로 화면 고정: 켜짐", 18, TEXT, false), lp(-1, -2, 0, 0, 0, dp(12)));
        settings.addView(text("AVRCP Browser: Songs, Albums, Artists, Folders, Playlists, Favorites 제공", 16, MUTED, false), lp(-1, -2, 0, 0, 0, dp(12)));
        settings.addView(text("음악 파일 수: " + library.allTracks().size(), 16, MUTED, false), lp(-1, -2, 0, 0, 0, dp(12)));
        Button reload = new Button(this);
        reload.setText("음악 폴더 스캔 후 새로고침");
        reload.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                requestMusicScan(true);
            }
        });
        settings.addView(reload, lp(-1, dp(52)));
        Button pickFolder = new Button(this);
        pickFolder.setText("음악 폴더 선택");
        pickFolder.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openFolderPicker();
            }
        });
        settings.addView(pickFolder, lp(-1, dp(52), 0, dp(10), 0, 0));
        Button bluetoothGuide = new Button(this);
        bluetoothGuide.setText("Bluetooth AVRCP 안내 보기");
        bluetoothGuide.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showBluetoothGuideScreen();
            }
        });
        settings.addView(bluetoothGuide, lp(-1, dp(52), 0, dp(10), 0, 0));

        settings.addView(text("음질 및 음향 효과", 22, TEXT, true), lp(-1, -2, 0, dp(26), 0, dp(10)));
        Button systemSound = new Button(this);
        systemSound.setText("시스템 음질 설정 열기");
        systemSound.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openSystemSoundSettings();
            }
        });
        settings.addView(systemSound, lp(-1, dp(52), 0, 0, 0, dp(10)));

        Button eqEnabled = new Button(this);
        eqEnabled.setText("앱 음장: " + (effects.enabled() ? "켜짐" : "꺼짐"));
        eqEnabled.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AudioEffectSettings current = new AudioEffectSettings(MainActivity.this);
                current.putBoolean(AudioEffectSettings.KEY_ENABLED, !current.enabled());
                applyAudioSettings();
                renderSettings();
            }
        });
        settings.addView(eqEnabled, lp(-1, dp(52), 0, 0, 0, dp(10)));

        settings.addView(cycleButton("EQ 프리셋", presetLabel(effects.preset()), new String[]{"사용 안 함", "기본", "클래식", "댄스", "플랫", "포크", "헤비메탈", "힙합", "재즈", "팝", "락"}, new Runnable() {
            @Override
            public void run() {
                AudioEffectSettings current = new AudioEffectSettings(MainActivity.this);
                int next = current.preset() + 1;
                if (next > 9) {
                    next = -1;
                }
                current.putInt(AudioEffectSettings.KEY_PRESET, next);
                current.putBoolean(AudioEffectSettings.KEY_ENABLED, true);
                applyAudioSettings();
                renderSettings();
            }
        }), lp(-1, dp(52), 0, 0, 0, dp(10)));

        settings.addView(effectSlider("Bass Boost", AudioEffectSettings.KEY_BASS, effects.bass(), 1000));
        settings.addView(effectSlider("Virtualizer", AudioEffectSettings.KEY_VIRTUALIZER, effects.virtualizer(), 1000));
        settings.addView(effectSlider("Loudness", AudioEffectSettings.KEY_LOUDNESS, effects.loudness(), 1500));

        settings.addView(text("재생", 22, TEXT, true), lp(-1, -2, 0, dp(26), 0, dp(10)));
        settings.addView(cycleButton("자동 끄기 타이머", effects.sleepTimerMinutes() == 0 ? "사용 안 함" : effects.sleepTimerMinutes() + "분", null, new Runnable() {
            @Override
            public void run() {
                AudioEffectSettings settings = new AudioEffectSettings(MainActivity.this);
                int current = settings.sleepTimerMinutes();
                int next = current == 0 ? 15 : current == 15 ? 30 : current == 30 ? 60 : 0;
                settings.putInt(AudioEffectSettings.KEY_SLEEP_TIMER, next);
                scheduleSleepTimer(next);
                renderSettings();
            }
        }), lp(-1, dp(52), 0, 0, 0, dp(10)));
        settings.addView(cycleSpeedButton(effects), lp(-1, dp(52), 0, 0, 0, dp(10)));
        settings.addView(cycleCrossfadeButton(effects), lp(-1, dp(52), 0, 0, 0, dp(10)));
        settings.addView(toggleSettingButton("곡 사이의 무음 건너뛰기", AudioEffectSettings.KEY_SKIP_SILENCE, effects.skipSilence()), lp(-1, dp(52), 0, 0, 0, dp(10)));
        settings.addView(toggleSettingButton("잠금화면에서 음악 재생 제어", AudioEffectSettings.KEY_LOCKSCREEN, effects.lockscreenControls()), lp(-1, dp(52), 0, 0, 0, dp(10)));
        settings.addView(toggleSettingButton("외부 기기에서 재생하도록 허용", AudioEffectSettings.KEY_EXTERNAL, effects.externalDevicePlayback()), lp(-1, dp(52), 0, 0, 0, dp(10)));
        settings.addView(cycleButton("현재 재생목록 설정", "모든 곡 재생", null, new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainActivity.this, "현재 큐 기준 재생은 이미 적용되어 있습니다", Toast.LENGTH_SHORT).show();
            }
        }), lp(-1, dp(52), 0, 0, 0, dp(10)));
        settings.addView(cycleButton("중복 곡 제외", "켜짐", null, new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainActivity.this, "재생목록 중복 제외 옵션을 유지합니다", Toast.LENGTH_SHORT).show();
            }
        }), lp(-1, dp(52), 0, 0, 0, dp(20)));

        scroll.addView(settings);
        content.addView(scroll);
    }

    private View effectSlider(String label, String key, int value, int max) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, dp(8), 0, dp(8));
        TextView caption = text(label + ": " + value, 16, MUTED, false);
        SeekBar seek = new SeekBar(this);
        seek.setMax(max);
        seek.setProgress(value);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                caption.setText(label + ": " + progress);
                if (fromUser) {
                    AudioEffectSettings settings = new AudioEffectSettings(MainActivity.this);
                    settings.putInt(key, progress);
                    settings.putBoolean(AudioEffectSettings.KEY_ENABLED, true);
                    applyAudioSettings();
                }
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        box.addView(caption, lp(-1, -2));
        box.addView(seek, lp(-1, dp(44)));
        return box;
    }

    private Button toggleSettingButton(String label, String key, boolean value) {
        Button button = new Button(this);
        button.setText(label + ": " + (value ? "켜짐" : "꺼짐"));
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AudioEffectSettings settings = new AudioEffectSettings(MainActivity.this);
                settings.putBoolean(key, !value);
                applyAudioSettings();
                renderSettings();
            }
        });
        return button;
    }

    private Button cycleSpeedButton(AudioEffectSettings effects) {
        Button button = new Button(this);
        button.setText("재생 속도: " + speedLabel(effects.speed()));
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AudioEffectSettings settings = new AudioEffectSettings(MainActivity.this);
                float current = settings.speed();
                float next = current < 1.0f ? 1.0f : current < 1.25f ? 1.25f : current < 1.5f ? 1.5f : current < 2.0f ? 2.0f : 0.75f;
                settings.putFloat(AudioEffectSettings.KEY_SPEED, next);
                applyAudioSettings();
                renderSettings();
            }
        });
        return button;
    }

    private Button cycleCrossfadeButton(AudioEffectSettings effects) {
        Button button = new Button(this);
        button.setText("곡 전환 시 겹쳐 재생: " + (effects.crossfadeSeconds() == 0 ? "사용 안 함" : effects.crossfadeSeconds() + "초"));
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AudioEffectSettings settings = new AudioEffectSettings(MainActivity.this);
                int current = settings.crossfadeSeconds();
                int next = current == 0 ? 3 : current == 3 ? 5 : current == 5 ? 10 : 0;
                settings.putInt(AudioEffectSettings.KEY_CROSSFADE, next);
                Toast.makeText(MainActivity.this, "겹쳐 재생 값이 저장되었습니다", Toast.LENGTH_SHORT).show();
                renderSettings();
            }
        });
        return button;
    }

    private Button cycleButton(String label, String value, String[] ignored, Runnable action) {
        Button button = new Button(this);
        button.setText(label + ": " + value);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                action.run();
            }
        });
        return button;
    }

    private String presetLabel(int preset) {
        String[] labels = {"기본", "클래식", "댄스", "플랫", "포크", "헤비메탈", "힙합", "재즈", "팝", "락"};
        if (preset < 0 || preset >= labels.length) {
            return "사용 안 함";
        }
        return labels[preset];
    }

    private String speedLabel(float speed) {
        return String.format("%.2f배속", speed);
    }

    private void applyAudioSettings() {
        startService(new Intent(this, PlayerService.class).setAction(PlayerService.ACTION_APPLY_AUDIO_SETTINGS));
    }

    private void scheduleSleepTimer(int minutes) {
        if (sleepTimerRunnable != null) {
            handler.removeCallbacks(sleepTimerRunnable);
            sleepTimerRunnable = null;
        }
        if (minutes <= 0) {
            Toast.makeText(this, "자동 끄기 타이머를 해제했습니다", Toast.LENGTH_SHORT).show();
            return;
        }
        sleepTimerRunnable = new Runnable() {
            @Override
            public void run() {
                startService(new Intent(MainActivity.this, PlayerService.class).setAction(PlayerService.ACTION_STOP));
                Toast.makeText(MainActivity.this, "자동 끄기 타이머로 재생을 멈췄습니다", Toast.LENGTH_SHORT).show();
            }
        };
        handler.postDelayed(sleepTimerRunnable, minutes * 60L * 1000L);
        Toast.makeText(this, minutes + "분 후 재생을 멈춥니다", Toast.LENGTH_SHORT).show();
    }

    private void openSystemSoundSettings() {
        try {
            startActivity(new Intent("com.samsung.settings.SOUND_QUALITY_SETTINGS"));
            return;
        } catch (RuntimeException ignored) {
        }
        try {
            startActivity(new Intent(Settings.ACTION_SOUND_SETTINGS));
        } catch (RuntimeException ignored) {
            Toast.makeText(this, "시스템 사운드 설정을 열 수 없습니다", Toast.LENGTH_SHORT).show();
        }
    }

    private void renderEmptyLibrary() {
        LinearLayout empty = new LinearLayout(this);
        empty.setOrientation(LinearLayout.VERTICAL);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(dp(24), dp(24), dp(24), dp(24));
        empty.setBackgroundColor(Color.WHITE);
        TextView title = text("음악 파일을 찾는 중입니다", 22, TEXT, true);
        title.setGravity(Gravity.CENTER);
        TextView message = text("Android가 새로 추가된 음악을 아직 MediaStore에 등록하지 않았을 수 있습니다. /sdcard/Music 폴더를 스캔한 뒤 목록을 다시 읽습니다.", 15, MUTED, false);
        message.setGravity(Gravity.CENTER);
        Button scan = new Button(this);
        scan.setText("음악 폴더 스캔");
        scan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                requestMusicScan(true);
            }
        });
        empty.addView(title, lp(-1, -2, 0, 0, 0, dp(12)));
        empty.addView(message, lp(-1, -2, 0, 0, 0, dp(18)));
        empty.addView(scan, lp(-1, dp(52)));
        Button pickFolder = new Button(this);
        pickFolder.setText("폴더 직접 선택");
        pickFolder.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openFolderPicker();
            }
        });
        empty.addView(pickFolder, lp(-1, dp(52), 0, dp(10), 0, 0));
        content.addView(empty);
    }

    private void openFolderPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        startActivityForResult(intent, REQUEST_FOLDER);
    }

    private void requestMusicScan(boolean showToast) {
        File musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC);
        List<String> paths = new ArrayList<>();
        collectAudioPaths(musicDir, paths);
        if (paths.isEmpty()) {
            if (showToast) {
                Toast.makeText(this, "Music 폴더에 오디오 파일이 없습니다", Toast.LENGTH_SHORT).show();
            }
            library = MusicLibrary.load(this);
            renderSelectedTab();
            return;
        }
        if (showToast) {
            Toast.makeText(this, "음악 파일 스캔 중: " + paths.size() + "개", Toast.LENGTH_SHORT).show();
        }
        AtomicInteger remaining = new AtomicInteger(paths.size());
        MediaScannerConnection.scanFile(this, paths.toArray(new String[0]), null, new MediaScannerConnection.OnScanCompletedListener() {
            @Override
            public void onScanCompleted(String path, Uri uri) {
                if (remaining.decrementAndGet() == 0) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            library = MusicLibrary.load(MainActivity.this);
                            renderSelectedTab();
                            Toast.makeText(MainActivity.this, "음악 목록 갱신 완료: " + library.allTracks().size() + "곡", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        });
    }

    private void collectAudioPaths(File dir, List<String> out) {
        if (dir == null || !dir.exists()) {
            return;
        }
        File[] children = dir.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            if (child.isDirectory()) {
                collectAudioPaths(child, out);
            } else if (isAudioFile(child.getName())) {
                out.add(child.getAbsolutePath());
            }
        }
    }

    private boolean isAudioFile(String name) {
        String lower = name.toLowerCase();
        return lower.endsWith(".mp3")
                || lower.endsWith(".m4a")
                || lower.endsWith(".aac")
                || lower.endsWith(".flac")
                || lower.endsWith(".wav")
                || lower.endsWith(".ogg")
                || lower.endsWith(".opus");
    }

    private void showPlayerScreen() {
        if (currentTrack == null) {
            return;
        }
        playerVisible = true;
        LinearLayout screen = new LinearLayout(this);
        screen.setOrientation(LinearLayout.VERTICAL);
        screen.setPadding(dp(18), dp(18), dp(18), dp(12));
        screen.setBackgroundColor(BG);

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView down = text("⌄", 38, TEXT, false);
        down.setGravity(Gravity.CENTER);
        down.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                buildLibraryScreen();
            }
        });
        top.addView(down, lp(dp(56), dp(56)));
        TextView spacer = text("", 1, TEXT, false);
        top.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1));
        TextView add = text("+", 34, TEXT, false);
        add.setGravity(Gravity.CENTER);
        add.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showAddToPlaylistDialog(currentTrack);
            }
        });
        top.addView(add, lp(dp(56), dp(56)));
        screen.addView(top);

        ImageView art = new ImageView(this);
        art.setScaleType(ImageView.ScaleType.CENTER_CROP);
        art.setImageBitmap(loadArtOrNull(currentTrack.albumArtUri));
        art.setBackground(round(Color.rgb(225, 225, 225), dp(26)));
        screen.addView(art, lp(-1, 0, 0, dp(18), 0, dp(18), 1));
        TextView title = text(currentTrack.title, 28, TEXT, true);
        title.setGravity(Gravity.CENTER);
        TextView artist = text(currentTrack.artist, 20, TEXT, false);
        artist.setGravity(Gravity.CENTER);
        screen.addView(title, lp(-1, -2));
        screen.addView(artist, lp(-1, -2, 0, dp(6), 0, dp(18)));

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER);
        TextView queue = text("≡", 34, TEXT, false);
        TextView favorite = text(playlists.isFavorite(currentTrack.id) ? "♥" : "♡", 34, TEXT, false);
        TextView plus = text("+", 34, TEXT, false);
        queue.setGravity(Gravity.CENTER);
        favorite.setGravity(Gravity.CENTER);
        plus.setGravity(Gravity.CENTER);
        queue.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showQueueScreen(); }
        });
        favorite.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                playlists.toggleFavorite(currentTrack.id);
                ((TextView) v).setText(playlists.isFavorite(currentTrack.id) ? "♥" : "♡");
            }
        });
        plus.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showAddToPlaylistDialog(currentTrack); }
        });
        actions.addView(queue, new LinearLayout.LayoutParams(0, dp(56), 1));
        actions.addView(favorite, new LinearLayout.LayoutParams(0, dp(56), 1));
        actions.addView(plus, new LinearLayout.LayoutParams(0, dp(56), 1));
        screen.addView(actions);

        playerSeek = new SeekBar(this);
        playerSeek.setMax((int) Math.max(1, currentTrack.durationMs));
        playerSeek.setProgress((int) positionMs);
        playerSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {}
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                Intent intent = new Intent(MainActivity.this, PlayerService.class)
                        .setAction(PlayerService.ACTION_SEEK)
                        .putExtra(PlayerService.EXTRA_SEEK_TO, seekBar.getProgress());
                startPlaybackService(intent);
            }
        });
        playerTime = text(time(positionMs) + "        " + time(currentTrack.durationMs), 14, Color.WHITE, false);
        playerTime.setTextColor(MUTED);
        screen.addView(playerSeek, lp(-1, dp(42)));
        screen.addView(playerTime, lp(-1, -2));

        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER);
        controls.addView(textControl(shuffle ? "SHUF" : "shuf", PlayerService.ACTION_TOGGLE_SHUFFLE), lp(dp(64), dp(64)));
        controls.addView(skipControl(android.R.drawable.ic_media_previous, PlayerService.ACTION_PREVIOUS, PlayerService.ACTION_REWIND), lp(dp(58), dp(64)));
        controls.addView(control(playing ? "⏸" : "▶", PlayerService.ACTION_TOGGLE), lp(dp(76), dp(76)));
        controls.addView(skipControl(android.R.drawable.ic_media_next, PlayerService.ACTION_NEXT, PlayerService.ACTION_FAST_FORWARD), lp(dp(58), dp(64)));
        controls.addView(textControl(repeatLabel(), PlayerService.ACTION_TOGGLE_REPEAT), lp(dp(64), dp(64)));
        screen.addView(controls, lp(-1, dp(86), 0, dp(10), 0, dp(12)));
        setContentView(screen);
    }

    private void showQueueScreen() {
        playerVisible = false;
        LinearLayout screen = new LinearLayout(this);
        screen.setOrientation(LinearLayout.VERTICAL);
        screen.setBackgroundColor(BG);
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(14), dp(16), dp(14), dp(8));
        TextView back = text("‹", 40, TEXT, false);
        back.setGravity(Gravity.CENTER);
        back.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showPlayerScreen(); }
        });
        top.addView(back, lp(dp(52), dp(52)));
        top.addView(text("현재 재생목록", 26, TEXT, true), new LinearLayout.LayoutParams(0, -2, 1));
        screen.addView(top);
        List<MusicTrack> queue = currentQueue.isEmpty() ? library.allTracks() : currentQueue;
        ListView list = new ListView(this);
        list.setBackgroundColor(Color.WHITE);
        list.setAdapter(new TrackAdapter(queue));
        screen.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(screen);
    }

    @Override
    public void onBackPressed() {
        playerVisible = false;
        buildLibraryScreen();
    }

    private void playTrack(MusicTrack track, List<MusicTrack> queue) {
        currentTrack = track;
        currentQueue.clear();
        currentQueue.addAll(queue);
        long[] ids = new long[queue.size()];
        for (int i = 0; i < queue.size(); i++) {
            ids[i] = queue.get(i).id;
        }
        Intent intent = new Intent(this, PlayerService.class)
                .setAction(PlayerService.ACTION_PLAY_ID)
                .putExtra(PlayerService.EXTRA_TRACK_ID, track.id)
                .putExtra(PlayerService.EXTRA_TRACK_IDS, ids);
        startPlaybackService(intent);
        updateMiniPlayer();
    }

    private void startPlaybackService(Intent intent) {
        String action = intent.getAction();
        if (Build.VERSION.SDK_INT >= 26
                && (PlayerService.ACTION_PLAY_ID.equals(action) || PlayerService.ACTION_PLAY_QUEUE.equals(action))) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    private void updateMiniPlayer() {
        if (miniPlayer == null) {
            return;
        }
        if (currentTrack == null) {
            miniTitle.setText("재생 중인 곡 없음");
            miniArtist.setText("");
            miniPlay.setImageResource(android.R.drawable.ic_media_play);
            miniArt.setImageResource(android.R.drawable.ic_media_play);
            return;
        }
        miniTitle.setText(currentTrack.title);
        miniArtist.setText(currentTrack.artist);
        miniPlay.setImageResource(playing ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play);
        Bitmap art = loadArtOrNull(currentTrack.albumArtUri);
        if (art != null) {
            miniArt.setImageBitmap(art);
        } else {
            miniArt.setImageResource(android.R.drawable.ic_media_play);
        }
    }

    private void updatePlayerProgress() {
        if (playerSeek != null && currentTrack != null) {
            playerSeek.setMax((int) Math.max(1, durationMs > 0 ? durationMs : currentTrack.durationMs));
            playerSeek.setProgress((int) Math.min(playerSeek.getMax(), positionMs));
        }
        if (playerTime != null && currentTrack != null) {
            long total = durationMs > 0 ? durationMs : currentTrack.durationMs;
            playerTime.setText(time(positionMs) + "        " + time(total));
        }
    }

    private List<MusicTrack> tracksFromIds(List<Long> ids) {
        List<MusicTrack> tracks = new ArrayList<>();
        for (Long id : ids) {
            MusicTrack track = library.find(id);
            if (track != null) {
                tracks.add(track);
            }
        }
        return tracks;
    }

    private void promptCreatePlaylist() {
        final EditText input = new EditText(this);
        input.setHint("플레이리스트 이름");
        new AlertDialog.Builder(this)
                .setTitle("플레이리스트 추가")
                .setView(input)
                .setPositiveButton("추가", (dialog, which) -> {
                    playlists.createPlaylist(input.getText().toString());
                    selectedTab = "플레이리스트";
                    buildLibraryScreen();
                })
                .setNegativeButton("취소", null)
                .show();
    }

    private void showAddToPlaylistDialog(MusicTrack track) {
        List<String> names = playlists.playlistNames();
        List<String> choices = new ArrayList<>();
        choices.add("새 플레이리스트");
        choices.addAll(names);
        new AlertDialog.Builder(this)
                .setTitle("추가")
                .setItems(choices.toArray(new String[0]), (dialog, which) -> {
                    if (which == 0) {
                        final EditText input = new EditText(this);
                        input.setHint("플레이리스트 이름");
                        new AlertDialog.Builder(this)
                                .setTitle("플레이리스트 추가")
                                .setView(input)
                                .setPositiveButton("추가", (d, w) -> {
                                    playlists.addToPlaylist(input.getText().toString(), track.id);
                                    Toast.makeText(this, "추가되었습니다", Toast.LENGTH_SHORT).show();
                                })
                                .setNegativeButton("취소", null)
                                .show();
                    } else {
                        playlists.addToPlaylist(choices.get(which), track.id);
                        Toast.makeText(this, "추가되었습니다", Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }

    private ImageButton control(String label, String action) {
        ImageButton button = iconButton(label);
        if ("▶".equals(label)) {
            button.setImageResource(android.R.drawable.ic_media_play);
        } else if ("⏸".equals(label)) {
            button.setImageResource(android.R.drawable.ic_media_pause);
        } else if ("⏮".equals(label)) {
            button.setImageResource(android.R.drawable.ic_media_previous);
        } else if ("⏭".equals(label)) {
            button.setImageResource(android.R.drawable.ic_media_next);
        }
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startPlaybackService(new Intent(MainActivity.this, PlayerService.class).setAction(action));
            }
        });
        return button;
    }

    private ImageButton skipControl(int icon, String clickAction, String longClickAction) {
        ImageButton button = iconButton(clickAction);
        button.setImageResource(icon);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startPlaybackService(new Intent(MainActivity.this, PlayerService.class).setAction(clickAction));
            }
        });
        button.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                startHoldSeek(longClickAction);
                return true;
            }
        });
        button.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_UP
                        || event.getAction() == MotionEvent.ACTION_CANCEL
                        || event.getAction() == MotionEvent.ACTION_OUTSIDE) {
                    stopHoldSeek();
                }
                return false;
            }
        });
        return button;
    }

    private void startHoldSeek(String action) {
        stopHoldSeek();
        holdSeekRunnable = new Runnable() {
            @Override
            public void run() {
                startPlaybackService(new Intent(MainActivity.this, PlayerService.class).setAction(action));
                handler.postDelayed(this, 500);
            }
        };
        holdSeekRunnable.run();
    }

    private void stopHoldSeek() {
        if (holdSeekRunnable != null) {
            handler.removeCallbacks(holdSeekRunnable);
            holdSeekRunnable = null;
        }
    }

    private TextView textControl(String label, String action) {
        TextView button = text(label, 15, TEXT, true);
        button.setGravity(Gravity.CENTER);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startPlaybackService(new Intent(MainActivity.this, PlayerService.class).setAction(action));
            }
        });
        return button;
    }

    private String repeatLabel() {
        if (repeatMode == 1) {
            return "ONE";
        }
        if (repeatMode == 2) {
            return "ALL";
        }
        return "rep";
    }

    private ImageButton iconButton(String label) {
        ImageButton button = new ImageButton(this);
        button.setBackgroundColor(Color.TRANSPARENT);
        button.setColorFilter(TEXT);
        button.setContentDescription(label);
        button.setImageResource(android.R.drawable.ic_media_play);
        return button;
    }

    private Bitmap loadArtOrNull(Uri uri) {
        try (InputStream stream = getContentResolver().openInputStream(uri)) {
            return stream == null ? null : BitmapFactory.decodeStream(stream);
        } catch (Exception ignored) {
            return null;
        }
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setIncludeFontPadding(true);
        view.setSingleLine(false);
        if (bold) {
            view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        }
        return view;
    }

    private GradientDrawable round(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private LinearLayout.LayoutParams lp(int w, int h) {
        return new LinearLayout.LayoutParams(w, h);
    }

    private LinearLayout.LayoutParams lp(int w, int h, int l, int t, int r, int b) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(w, h);
        params.setMargins(l, t, r, b);
        return params;
    }

    private LinearLayout.LayoutParams lp(int w, int h, int l, int t, int r, int b, float weight) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(w, h, weight);
        params.setMargins(l, t, r, b);
        return params;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private String time(long ms) {
        long total = Math.max(0, ms) / 1000;
        return (total / 60) + ":" + String.format("%02d", total % 60);
    }

    private class TrackAdapter extends BaseAdapter {
        private List<MusicTrack> tracks;

        TrackAdapter(List<MusicTrack> tracks) {
            this.tracks = new ArrayList<>(tracks);
        }

        void setTracks(List<MusicTrack> tracks) {
            this.tracks = new ArrayList<>(tracks);
            currentQueue.clear();
            currentQueue.addAll(this.tracks);
            notifyDataSetChanged();
        }

        @Override public int getCount() { return tracks.size(); }
        @Override public Object getItem(int position) { return tracks.get(position); }
        @Override public long getItemId(int position) { return tracks.get(position).id; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            MusicTrack track = tracks.get(position);
            LinearLayout row = new LinearLayout(MainActivity.this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(14), dp(8), dp(10), dp(8));
            row.setBackgroundColor(Color.WHITE);
            ImageView art = new ImageView(MainActivity.this);
            art.setScaleType(ImageView.ScaleType.CENTER_CROP);
            Bitmap bitmap = loadArtOrNull(track.albumArtUri);
            if (bitmap != null) {
                art.setImageBitmap(bitmap);
            } else {
                art.setImageResource(android.R.drawable.ic_media_play);
            }
            row.addView(art, lp(dp(64), dp(64), 0, 0, dp(14), 0));
            LinearLayout labels = new LinearLayout(MainActivity.this);
            labels.setOrientation(LinearLayout.VERTICAL);
            TextView title = text(track.title, 20, track.id == (currentTrack == null ? -1 : currentTrack.id) ? BLUE : TEXT, false);
            title.setSingleLine(true);
            TextView sub = text(track.artist, 15, MUTED, false);
            sub.setSingleLine(true);
            labels.addView(title);
            labels.addView(sub);
            row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
            TextView menu = text("⋮", 28, MUTED, false);
            menu.setGravity(Gravity.CENTER);
            menu.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { showAddToPlaylistDialog(track); }
            });
            row.addView(menu, lp(dp(42), dp(64)));
            row.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { playTrack(track, tracks); }
            });
            return row;
        }
    }

    private class GroupAdapter extends BaseAdapter {
        private final List<GroupRow> rows;
        private final boolean grid;

        GroupAdapter(List<GroupRow> rows, boolean grid) {
            this.rows = rows;
            this.grid = grid;
        }

        @Override public int getCount() { return rows.size(); }
        @Override public Object getItem(int position) { return rows.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            GroupRow item = rows.get(position);
            LinearLayout box = new LinearLayout(MainActivity.this);
            box.setOrientation(grid ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
            box.setGravity(grid ? Gravity.CENTER : Gravity.CENTER_VERTICAL);
            box.setPadding(dp(10), dp(10), dp(10), dp(10));
            box.setBackgroundColor(Color.WHITE);
            ImageView art = new ImageView(MainActivity.this);
            art.setScaleType(ImageView.ScaleType.CENTER_CROP);
            Bitmap bitmap = item.tracks.isEmpty() ? null : loadArtOrNull(item.tracks.get(0).albumArtUri);
            if (bitmap != null) {
                art.setImageBitmap(bitmap);
            } else {
                art.setImageResource(android.R.drawable.ic_menu_gallery);
            }
            int size = grid ? dp(160) : dp(64);
            box.addView(art, lp(size, size, 0, 0, grid ? 0 : dp(14), grid ? dp(8) : 0));
            LinearLayout labels = new LinearLayout(MainActivity.this);
            labels.setOrientation(LinearLayout.VERTICAL);
            TextView title = text(item.name, grid ? 17 : 20, TEXT, false);
            title.setGravity(grid ? Gravity.CENTER : Gravity.NO_GRAVITY);
            TextView sub = text(item.tracks.size() + "곡", 15, MUTED, false);
            sub.setGravity(grid ? Gravity.CENTER : Gravity.NO_GRAVITY);
            labels.addView(title);
            labels.addView(sub);
            box.addView(labels, grid ? lp(-1, -2) : new LinearLayout.LayoutParams(0, -2, 1));
            box.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { renderTracks(item.name, item.tracks); }
            });
            box.setLayoutParams(new AbsListView.LayoutParams(-1, grid ? dp(240) : dp(86)));
            return box;
        }
    }

    private class PlaylistAdapter extends BaseAdapter {
        private final List<PlaylistRow> rows;

        PlaylistAdapter(List<PlaylistRow> rows) {
            this.rows = rows;
        }

        @Override public int getCount() { return rows.size(); }
        @Override public Object getItem(int position) { return rows.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            PlaylistRow item = rows.get(position);
            LinearLayout row = new LinearLayout(MainActivity.this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(18), dp(12), dp(18), dp(12));
            row.setBackgroundColor(Color.WHITE);
            TextView icon = text(item.add ? "+" : "♫", 28, MUTED, false);
            icon.setGravity(Gravity.CENTER);
            icon.setBackground(round(Color.rgb(238, 238, 238), dp(14)));
            row.addView(icon, lp(dp(64), dp(64), 0, 0, dp(16), 0));
            LinearLayout labels = new LinearLayout(MainActivity.this);
            labels.setOrientation(LinearLayout.VERTICAL);
            labels.addView(text(item.title, 22, TEXT, true));
            labels.addView(text(item.sub, 15, MUTED, false));
            row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
            row.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    if (item.add) {
                        promptCreatePlaylist();
                    } else if ("현재 재생목록".equals(item.title)) {
                        showQueueScreen();
                    } else if ("좋아요 한 곡".equals(item.title)) {
                        renderTracks(item.title, tracksFromIds(playlists.favorites()));
                    } else {
                        renderTracks(item.title, tracksFromIds(playlists.playlistIds(item.title)));
                    }
                }
            });
            return row;
        }
    }

    private static class GroupRow {
        final String name;
        final List<MusicTrack> tracks;
        GroupRow(String name, List<MusicTrack> tracks) {
            this.name = name;
            this.tracks = tracks;
        }
    }

    private static class PlaylistRow {
        final String title;
        final String sub;
        final long id;
        final boolean add;
        PlaylistRow(String title, String sub, long id, boolean add) {
            this.title = title;
            this.sub = sub;
            this.id = id;
            this.add = add;
        }
    }

    private class HorizontalScroll extends android.widget.HorizontalScrollView {
        final LinearLayout row;
        HorizontalScroll(Context context) {
            super(context);
            setHorizontalScrollBarEnabled(false);
            row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            addView(row, new ViewGroup.LayoutParams(-2, -1));
        }
    }
}
