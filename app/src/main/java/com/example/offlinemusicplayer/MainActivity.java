package com.example.offlinemusicplayer;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Build;
import android.view.View;
import android.widget.*;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;

public class MainActivity extends Activity {
    private EditText urlInput;
    private Button downloadButton, playPauseButton;
    private TextView statusText, nowPlaying;
    private ListView libraryList;
    private ArrayAdapter<String> adapter;
    private final ArrayList<File> tracks = new ArrayList<>();
    private MediaPlayer player;
    private File selectedTrack;
    private long lastDownloadId = -1;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        urlInput = findViewById(R.id.urlInput);
        downloadButton = findViewById(R.id.downloadButton);
        playPauseButton = findViewById(R.id.playPauseButton);
        statusText = findViewById(R.id.statusText);
        nowPlaying = findViewById(R.id.nowPlaying);
        libraryList = findViewById(R.id.libraryList);
        adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, new ArrayList<String>()) {
            @Override public View getView(int position, View convertView, android.view.ViewGroup parent) {
                TextView view = (TextView) super.getView(position, convertView, parent);
                view.setTextColor(0xFFF4F5F7); view.setTextSize(15); view.setPadding(8, 18, 8, 18); return view;
            }
        };
        libraryList.setAdapter(adapter);
        downloadButton.setOnClickListener(v -> startDownload());
        playPauseButton.setOnClickListener(v -> togglePlayback());
        libraryList.setOnItemClickListener((p, v, position, id) -> selectTrack(tracks.get(position)));
        IntentFilter downloadFilter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(downloadReceiver, downloadFilter, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(downloadReceiver, downloadFilter);
        refreshLibrary();
    }

    private File musicDir() { File dir = new File(getExternalFilesDir(Environment.DIRECTORY_MUSIC), "OfflineMusic"); if (!dir.exists()) dir.mkdirs(); return dir; }

    private void startDownload() {
        String raw = urlInput.getText().toString().trim();
        if (raw.isEmpty()) { statusText.setText("Paste a URL first."); return; }
        Uri uri = Uri.parse(raw);
        if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) { statusText.setText("Use an http or https URL."); return; }
        String name = uri.getLastPathSegment();
        if (name == null || name.trim().isEmpty()) name = "download-" + System.currentTimeMillis() + ".mp3";
        if (!name.contains(".")) name += ".mp3";
        name = name.replaceAll("[^a-zA-Z0-9._-]", "_");
        DownloadManager.Request request = new DownloadManager.Request(uri)
                .setTitle(name).setDescription("Offline Music download")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setAllowedOverMetered(true).setAllowedOverRoaming(false)
                .setDestinationUri(Uri.fromFile(new File(musicDir(), name)));
        lastDownloadId = ((DownloadManager) getSystemService(DOWNLOAD_SERVICE)).enqueue(request);
        statusText.setText("Downloading…"); downloadButton.setEnabled(false); urlInput.setText("");
    }

    private final BroadcastReceiver downloadReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(intent.getAction())) {
                long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                if (id != lastDownloadId) return;
                DownloadManager.Query query = new DownloadManager.Query().setFilterById(id);
                Cursor c = ((DownloadManager) getSystemService(DOWNLOAD_SERVICE)).query(query);
                boolean ok = c != null && c.moveToFirst() && c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)) == DownloadManager.STATUS_SUCCESSFUL;
                if (c != null) c.close();
                statusText.setText(ok ? "Saved to your offline library." : "Download failed. Check the URL and your connection.");
                downloadButton.setEnabled(true); refreshLibrary();
            }
        }
    };

    private void refreshLibrary() {
        tracks.clear(); File[] files = musicDir().listFiles();
        if (files != null) { Arrays.sort(files, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER)); for (File f : files) if (f.isFile()) tracks.add(f); }
        ArrayList<String> names = new ArrayList<>(); for (File f : tracks) names.add(f.getName()); adapter.clear(); adapter.addAll(names); adapter.notifyDataSetChanged();
    }

    private void selectTrack(File file) { selectedTrack = file; nowPlaying.setText(file.getName()); nowPlaying.setSelected(true); playPauseButton.setText("PLAY"); }

    private void togglePlayback() {
        if (selectedTrack == null) { if (!tracks.isEmpty()) selectTrack(tracks.get(0)); else { statusText.setText("Download a track first."); return; } }
        try {
            if (player != null && player.isPlaying()) { player.pause(); playPauseButton.setText("RESUME"); return; }
            if (player == null) { player = new MediaPlayer(); player.setDataSource(selectedTrack.getAbsolutePath()); player.setOnCompletionListener(mp -> playPauseButton.setText("PLAY")); player.prepare(); }
            player.start(); playPauseButton.setText("PAUSE");
        } catch (Exception e) { statusText.setText("This file could not be played: " + e.getMessage()); }
    }

    @Override protected void onDestroy() { unregisterReceiver(downloadReceiver); if (player != null) { player.release(); player = null; } super.onDestroy(); }
}
