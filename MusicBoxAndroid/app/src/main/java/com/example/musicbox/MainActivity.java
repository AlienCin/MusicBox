package com.example.musicbox;

import android.Manifest;
import android.app.Activity;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends Activity {
    private static final int REQUEST_AUDIO = 100;
    private final ArrayList<Track> allTracks = new ArrayList<>();
    private final ArrayList<Track> shownTracks = new ArrayList<>();
    private final Set<String> favorites = new HashSet<>();

    private LinearLayout listContainer;
    private TextView nowPlaying;
    private TextView playPauseButton;
    private EditText searchBox;
    private TextView filterButton;
    private boolean favoritesOnly = false;
    private MediaPlayer player;
    private int currentIndex = -1;
    private String currentTrackId = "";

    private final int bg = Color.rgb(15, 18, 30);
    private final int panel = Color.rgb(28, 33, 50);
    private final int accent = Color.rgb(126, 87, 255);
    private final int white = Color.rgb(245, 245, 250);
    private final int muted = Color.rgb(165, 170, 190);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        favorites.addAll(getPreferences(MODE_PRIVATE).getStringSet("favorites", new HashSet<>()));
        buildScreen();
        if (hasAudioPermission()) {
            loadTracks();
        } else {
            requestAudioPermission();
        }
    }

    private void buildScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(24), dp(18), dp(12));
        root.setBackgroundColor(bg);

        TextView title = new TextView(this);
        title.setText("MusicBox  ♪");
        title.setTextColor(white);
        title.setTextSize(29);
        title.setTypeface(null, 1);
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("موسیقی‌های داخل گوشی‌ات، همه در یک جا");
        subtitle.setTextColor(muted);
        subtitle.setTextSize(14);
        LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(-1, -2);
        subParams.bottomMargin = dp(18);
        root.addView(subtitle, subParams);

        searchBox = new EditText(this);
        searchBox.setSingleLine(true);
        searchBox.setHint("جستجوی نام آهنگ یا خواننده...");
        searchBox.setHintTextColor(muted);
        searchBox.setTextColor(white);
        searchBox.setTextSize(14);
        searchBox.setPadding(dp(14), dp(8), dp(14), dp(8));
        searchBox.setBackgroundColor(panel);
        root.addView(searchBox, new LinearLayout.LayoutParams(-1, dp(48)));

        filterButton = makeText("♫  همه آهنگ‌ها  •  نمایش علاقه‌مندی‌ها", 13, accent, true);
        filterButton.setPadding(0, dp(14), 0, dp(10));
        root.addView(filterButton);
        filterButton.setOnClickListener(v -> {
            favoritesOnly = !favoritesOnly;
            filterButton.setText(favoritesOnly ? "★  فقط علاقه‌مندی‌ها  •  نمایش همه" : "♫  همه آهنگ‌ها  •  نمایش علاقه‌مندی‌ها");
            renderTracks();
        });

        ScrollView scroll = new ScrollView(this);
        listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(listContainer);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout playerCard = new LinearLayout(this);
        playerCard.setOrientation(LinearLayout.VERTICAL);
        playerCard.setPadding(dp(14), dp(12), dp(14), dp(12));
        playerCard.setBackgroundColor(panel);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(-1, -2);
        cardParams.topMargin = dp(12);
        root.addView(playerCard, cardParams);

        TextView smallLabel = makeText("در حال پخش", 12, muted, false);
        playerCard.addView(smallLabel);
        nowPlaying = makeText("هنوز آهنگی انتخاب نشده", 15, white, true);
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(-1, -2);
        np.topMargin = dp(4);
        playerCard.addView(nowPlaying, np);

        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER);
        controls.setPadding(0, dp(8), 0, 0);
        playerCard.addView(controls);

        TextView previous = control("|◀");
        playPauseButton = control("▶");
        TextView next = control("▶|");
        controls.addView(previous);
        controls.addView(playPauseButton);
        controls.addView(next);

        previous.setOnClickListener(v -> playRelative(-1));
        next.setOnClickListener(v -> playRelative(1));
        playPauseButton.setOnClickListener(v -> togglePlayback());

        setContentView(root);

        searchBox.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int st, int before, int count) { renderTracks(); }
            @Override public void afterTextChanged(Editable e) {}
        });
    }

    private TextView makeText(String text, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(null, 1);
        return t;
    }

    private TextView control(String text) {
        TextView t = makeText(text, 22, white, true);
        t.setGravity(Gravity.CENTER);
        t.setBackgroundColor(accent);
        t.setPadding(dp(18), dp(8), dp(18), dp(8));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, -2);
        p.setMargins(dp(7), 0, dp(7), 0);
        t.setLayoutParams(p);
        return t;
    }

    private boolean hasAudioPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            return checkSelfPermission(Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED;
        }
        return checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestAudioPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(new String[]{Manifest.permission.READ_MEDIA_AUDIO}, REQUEST_AUDIO);
        } else {
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQUEST_AUDIO);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_AUDIO) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                loadTracks();
            } else {
                Toast.makeText(this, "برای نمایش آهنگ‌ها، اجازه دسترسی به فایل‌های صوتی لازم است.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void loadTracks() {
        allTracks.clear();
        Uri collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        String[] projection = {
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.DURATION
        };
        String selection = MediaStore.Audio.Media.IS_MUSIC + " != 0";
        String sort = MediaStore.Audio.Media.TITLE + " COLLATE NOCASE ASC";

        try (Cursor cursor = getContentResolver().query(collection, projection, selection, null, sort)) {
            if (cursor != null) {
                int idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
                int titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE);
                int artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST);
                int durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION);
                while (cursor.moveToNext()) {
                    long id = cursor.getLong(idCol);
                    String title = cursor.getString(titleCol);
                    String artist = cursor.getString(artistCol);
                    long duration = cursor.getLong(durationCol);
                    Uri uri = ContentUris.withAppendedId(collection, id);
                    allTracks.add(new Track(String.valueOf(id), title, artist, duration, uri));
                }
            }
        } catch (Exception e) {
            Toast.makeText(this, "خواندن فایل‌های صوتی با مشکل روبه‌رو شد.", Toast.LENGTH_SHORT).show();
        }
        renderTracks();
    }

    private void renderTracks() {
        if (listContainer == null) return;
        listContainer.removeAllViews();
        shownTracks.clear();
        String query = searchBox == null ? "" : searchBox.getText().toString().toLowerCase(Locale.ROOT).trim();

        for (Track track : allTracks) {
            boolean matches = track.title.toLowerCase(Locale.ROOT).contains(query)
                    || track.artist.toLowerCase(Locale.ROOT).contains(query);
            if (matches && (!favoritesOnly || favorites.contains(track.id))) shownTracks.add(track);
        }

        if (shownTracks.isEmpty()) {
            TextView empty = makeText(allTracks.isEmpty()
                    ? "هنوز آهنگی در حافظه گوشی پیدا نشد."
                    : "موردی با این جستجو پیدا نشد.", 14, muted, false);
            empty.setPadding(dp(4), dp(24), dp(4), dp(24));
            listContainer.addView(empty);
            return;
        }

        for (Track track : shownTracks) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(12), dp(12), dp(8), dp(12));
            row.setBackgroundColor(panel);
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
            rowParams.bottomMargin = dp(8);
            listContainer.addView(row, rowParams);

            LinearLayout info = new LinearLayout(this);
            info.setOrientation(LinearLayout.VERTICAL);
            row.addView(info, new LinearLayout.LayoutParams(0, -2, 1));

            TextView name = makeText(track.title, 15, white, true);
            info.addView(name);
            TextView details = makeText(track.artist + "  •  " + formatDuration(track.duration), 12, muted, false);
            LinearLayout.LayoutParams detailsParams = new LinearLayout.LayoutParams(-1, -2);
            detailsParams.topMargin = dp(3);
            info.addView(details, detailsParams);

            TextView fav = makeText(favorites.contains(track.id) ? "★" : "☆", 24, accent, true);
            fav.setPadding(dp(10), 0, dp(8), 0);
            row.addView(fav);
            fav.setOnClickListener(v -> {
                if (favorites.contains(track.id)) favorites.remove(track.id);
                else favorites.add(track.id);
                getPreferences(MODE_PRIVATE).edit().putStringSet("favorites", new HashSet<>(favorites)).apply();
                renderTracks();
            });
            row.setOnClickListener(v -> playTrack(track));
        }
    }

    private void playTrack(Track track) {
        releasePlayer();
        nowPlaying.setText(track.title + " — " + track.artist);
        playPauseButton.setText("…");
        try {
            player = new MediaPlayer();
            player.setDataSource(this, track.uri);
            player.setOnPreparedListener(mp -> {
                mp.start();
                playPauseButton.setText("Ⅱ");
            });
            player.setOnCompletionListener(mp -> playRelative(1));
            player.setOnErrorListener((mp, what, extra) -> {
                Toast.makeText(this, "پخش این فایل ممکن نیست.", Toast.LENGTH_SHORT).show();
                playPauseButton.setText("▶");
                return true;
            });
            player.prepareAsync();
            currentTrackId = track.id;
        } catch (IOException | IllegalStateException e) {
            Toast.makeText(this, "امکان پخش این آهنگ وجود ندارد.", Toast.LENGTH_SHORT).show();
            playPauseButton.setText("▶");
        }
    }

    private void togglePlayback() {
        if (player == null) {
            if (!shownTracks.isEmpty()) playTrack(shownTracks.get(0));
            return;
        }
        try {
            if (player.isPlaying()) {
                player.pause();
                playPauseButton.setText("▶");
            } else {
                player.start();
                playPauseButton.setText("Ⅱ");
            }
        } catch (IllegalStateException ignored) {}
    }

    private void playRelative(int step) {
        if (allTracks.isEmpty()) return;
        int index = -1;
        for (int i = 0; i < allTracks.size(); i++) {
            if (allTracks.get(i).id.equals(currentTrackId)) { index = i; break; }
        }
        if (index == -1) index = 0;
        index = (index + step + allTracks.size()) % allTracks.size();
        playTrack(allTracks.get(index));
    }

    private void releasePlayer() {
        if (player != null) {
            try { if (player.isPlaying()) player.stop(); } catch (Exception ignored) {}
            player.release();
            player = null;
        }
    }

    private String formatDuration(long millis) {
        long total = millis / 1000;
        return String.format(Locale.getDefault(), "%d:%02d", total / 60, total % 60);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    protected void onDestroy() {
        releasePlayer();
        super.onDestroy();
    }

    private static class Track {
        final String id;
        final String title;
        final String artist;
        final long duration;
        final Uri uri;

        Track(String id, String title, String artist, long duration, Uri uri) {
            this.id = id;
            this.title = title == null ? "بدون عنوان" : title;
            this.artist = artist == null || artist.equals("<unknown>") ? "خواننده ناشناس" : artist;
            this.duration = duration;
            this.uri = uri;
        }
    }
}
