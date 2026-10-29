package com.example.offlinemusicplayer;

import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.*;
import android.view.View;
import android.widget.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {
    private EditText urlInput, serviceUrlInput;
    private Button downloadButton, playPauseButton, previousButton, nextButton, shuffleButton, repeatButton;
    private TextView statusText, nowPlaying, elapsedText, durationText, libraryEmpty, downloadTab, libraryTab;
    private ListView libraryList;
    private SeekBar progressBar;
    private ArrayAdapter<String> adapter;
    private final ArrayList<File> tracks = new ArrayList<>();
    private MediaPlayer player;
    private File selectedTrack;
    private long lastDownloadId = -1;
    private boolean shuffle = false, repeat = false, userSeeking = false;
    private final Random random = new Random();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState); setContentView(R.layout.activity_main);
        bindViews(); bindActions();
        IntentFilter filter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(downloadReceiver, filter, Context.RECEIVER_NOT_EXPORTED); else registerReceiver(downloadReceiver, filter);
        refreshLibrary(); showDownloadTab();
    }

    private void bindViews() {
        urlInput=findViewById(R.id.urlInput); serviceUrlInput=findViewById(R.id.serviceUrlInput); downloadButton=findViewById(R.id.downloadButton);
        playPauseButton=findViewById(R.id.playPauseButton); previousButton=findViewById(R.id.previousButton); nextButton=findViewById(R.id.nextButton);
        shuffleButton=findViewById(R.id.shuffleButton); repeatButton=findViewById(R.id.repeatButton); statusText=findViewById(R.id.statusText);
        nowPlaying=findViewById(R.id.nowPlaying); elapsedText=findViewById(R.id.elapsedText); durationText=findViewById(R.id.durationText);
        libraryEmpty=findViewById(R.id.libraryEmpty); progressBar=findViewById(R.id.progressBar); libraryList=findViewById(R.id.libraryList);
        downloadTab=findViewById(R.id.downloadTab); libraryTab=findViewById(R.id.libraryTab);
        serviceUrlInput.setText(getPreferences(MODE_PRIVATE).getString("service_url", "http://10.0.2.2:8000"));
        adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, new ArrayList<String>()) {
            @Override public View getView(int position, View convertView, android.view.ViewGroup parent) { TextView v=(TextView)super.getView(position,convertView,parent); v.setTextColor(0xFFF4F5F7); v.setTextSize(15); v.setPadding(8,18,8,18); return v; }
        }; libraryList.setAdapter(adapter);
    }

    private void bindActions() {
        downloadButton.setOnClickListener(v -> beginDownload());
        downloadTab.setOnClickListener(v -> showDownloadTab()); libraryTab.setOnClickListener(v -> showLibraryTab());
        libraryList.setOnItemClickListener((p,v,position,id) -> selectTrack(tracks.get(position)));
        playPauseButton.setOnClickListener(v -> togglePlayback()); previousButton.setOnClickListener(v -> playRelative(-1)); nextButton.setOnClickListener(v -> playRelative(1));
        shuffleButton.setOnClickListener(v -> { shuffle=!shuffle; shuffleButton.setText(shuffle?"SHUFFLE ON":"SHUFFLE"); });
        repeatButton.setOnClickListener(v -> { repeat=!repeat; repeatButton.setText(repeat?"REPEAT ON":"REPEAT OFF"); });
        progressBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onStartTrackingTouch(SeekBar b){ userSeeking=true; }
            public void onStopTrackingTouch(SeekBar b){ if(player!=null) player.seekTo(b.getProgress()); userSeeking=false; }
            public void onProgressChanged(SeekBar b,int p,boolean fromUser){ if(fromUser) elapsedText.setText(formatMs(p)); }
        });
    }

    private File musicDir() { File d=new File(getExternalFilesDir(Environment.DIRECTORY_MUSIC),"OfflineMusic"); if(!d.exists()) d.mkdirs(); return d; }

    private void beginDownload() {
        String raw=urlInput.getText().toString().trim(); if(raw.isEmpty()){statusText.setText("Paste a URL first.");return;}
        Uri uri=Uri.parse(raw); String scheme=uri.getScheme(); if(!"http".equalsIgnoreCase(scheme)&&!"https".equalsIgnoreCase(scheme)){statusText.setText("Use an http or https URL.");return;}
        getPreferences(MODE_PRIVATE).edit().putString("service_url",serviceUrlInput.getText().toString().trim()).apply();
        if (isServiceUrl(raw)) extractThroughService(raw); else enqueueDownload(raw, safeName(uri.getLastPathSegment()));
    }

    private boolean isServiceUrl(String raw) { String l=raw.toLowerCase(Locale.US); return l.contains("youtube.com/") || l.contains("youtu.be/") || l.contains("vimeo.com/"); }

    private void extractThroughService(String mediaUrl) {
        String serviceBase=serviceUrlInput.getText().toString().trim(); if(serviceBase.endsWith("/")) serviceBase=serviceBase.substring(0,serviceBase.length()-1);
        if(serviceBase.isEmpty()){statusText.setText("Enter the extractor service URL first.");return;}
        final String base=serviceBase; downloadButton.setEnabled(false); statusText.setText("Converting with yt-dlp + FFmpeg…"); final String endpoint=base+"/extract";
        executor.execute(() -> {
            HttpURLConnection c=null;
            try {
                c=(HttpURLConnection)new URL(endpoint).openConnection(); c.setRequestMethod("POST"); c.setConnectTimeout(8000); c.setReadTimeout(600000); c.setDoOutput(true); c.setRequestProperty("Content-Type","application/json");
                String body="{\"url\":\""+jsonEscape(mediaUrl)+"\"}"; try(OutputStream out=c.getOutputStream()){out.write(body.getBytes("UTF-8"));}
                int code=c.getResponseCode(); InputStream in=code>=200&&code<300?c.getInputStream():c.getErrorStream(); String response=readAll(in);
                if(code<200||code>=300) throw new IOException("Service returned "+code+": "+response);
                String downloadUrl=jsonValue(response,"download_url"); String title=jsonValue(response,"title"); if(downloadUrl==null) throw new IOException("Service did not return download_url");
                final String outputUrl=downloadUrl.startsWith("/")?base+downloadUrl:downloadUrl; final String outputName=safeName(title==null?"youtube-audio.mp3":title+".mp3");
                runOnUiThread(() -> enqueueDownload(outputUrl, outputName));
            } catch(Exception e) { runOnUiThread(() -> { downloadButton.setEnabled(true); statusText.setText("Conversion failed: "+e.getMessage()); }); }
            finally { if(c!=null)c.disconnect(); }
        });
    }

    private void enqueueDownload(String rawUrl,String name) {
        try {
            DownloadManager.Request r=new DownloadManager.Request(Uri.parse(rawUrl)).setTitle(name).setDescription("Offline Music download").setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED).setAllowedOverMetered(true).setAllowedOverRoaming(false).setDestinationUri(Uri.fromFile(new File(musicDir(),name)));
            lastDownloadId=((DownloadManager)getSystemService(DOWNLOAD_SERVICE)).enqueue(r); statusText.setText("Downloading "+name+"…"); downloadButton.setEnabled(false); urlInput.setText("");
        } catch(Exception e) { downloadButton.setEnabled(true); statusText.setText("Download could not start: "+e.getMessage()); }
    }

    private String safeName(String name) { if(name==null||name.trim().isEmpty())name="audio-"+System.currentTimeMillis()+".mp3"; if(!name.contains("."))name += ".mp3"; return name.replaceAll("[^a-zA-Z0-9._-]","_"); }
    private String jsonEscape(String s){return s.replace("\\","\\\\").replace("\"","\\\"");}
    private String readAll(InputStream in)throws IOException{if(in==null)return "";StringBuilder b=new StringBuilder();try(BufferedReader r=new BufferedReader(new InputStreamReader(in))){String l;while((l=r.readLine())!=null)b.append(l);}return b.toString();}
    private String jsonValue(String json,String key){String needle="\""+key+"\"";int i=json.indexOf(needle);if(i<0)return null;i=json.indexOf(':',i+needle.length());if(i<0)return null;i++;while(i<json.length()&&Character.isWhitespace(json.charAt(i)))i++;if(i>=json.length()||json.charAt(i)!='\"')return null;i++;StringBuilder b=new StringBuilder();boolean esc=false;for(;i<json.length();i++){char ch=json.charAt(i);if(esc){b.append(ch);esc=false;}else if(ch=='\\')esc=true;else if(ch=='\"')break;else b.append(ch);}return b.toString();}

    private final BroadcastReceiver downloadReceiver=new BroadcastReceiver(){@Override public void onReceive(Context context,Intent intent){long id=intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID,-1);if(id!=lastDownloadId)return;DownloadManager.Query q=new DownloadManager.Query().setFilterById(id);Cursor c=((DownloadManager)getSystemService(DOWNLOAD_SERVICE)).query(q);boolean ok=c!=null&&c.moveToFirst()&&c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))==DownloadManager.STATUS_SUCCESSFUL;if(c!=null)c.close();downloadButton.setEnabled(true);statusText.setText(ok?"Saved to your downloaded music.":"Download failed. Check the service and URL.");refreshLibrary();showLibraryTab();}};

    private void refreshLibrary(){tracks.clear();File[] fs=musicDir().listFiles();if(fs!=null){Arrays.sort(fs,Comparator.comparing(File::getName,String.CASE_INSENSITIVE_ORDER));for(File f:fs)if(f.isFile())tracks.add(f);}ArrayList<String> names=new ArrayList<>();for(File f:tracks)names.add(f.getName());adapter.clear();adapter.addAll(names);adapter.notifyDataSetChanged();libraryEmpty.setVisibility(tracks.isEmpty()?View.VISIBLE:View.GONE);}
    private void selectTrack(File f){selectedTrack=f;nowPlaying.setText(f.getName());playPauseButton.setText("PLAY");if(player!=null){player.release();player=null;}progressBar.setProgress(0);elapsedText.setText("0:00");durationText.setText("0:00");}
    private void togglePlayback(){if(selectedTrack==null){if(tracks.isEmpty()){statusText.setText("Download a track first.");return;}selectTrack(tracks.get(0));}try{if(player!=null&&player.isPlaying()){player.pause();playPauseButton.setText("RESUME");return;}if(player==null){player=new MediaPlayer();player.setDataSource(selectedTrack.getAbsolutePath());player.setOnPreparedListener(mp->{progressBar.setMax(mp.getDuration());durationText.setText(formatMs(mp.getDuration()));mp.start();playPauseButton.setText("PAUSE");updateProgress();});player.setOnCompletionListener(mp->{if(repeat){mp.seekTo(0);mp.start();}else playRelative(1);});player.prepareAsync();}else{player.start();playPauseButton.setText("PAUSE");}}catch(Exception e){statusText.setText("This file could not be played: "+e.getMessage());}}
    private void playRelative(int delta){if(tracks.isEmpty())return;int index=selectedTrack==null?0:tracks.indexOf(selectedTrack);if(shuffle)index=random.nextInt(tracks.size());else index=(index+delta+tracks.size())%tracks.size();selectTrack(tracks.get(index));togglePlayback();}
    private void updateProgress(){handler.postDelayed(()->{if(player!=null&&player.isPlaying()&&!userSeeking){progressBar.setProgress(player.getCurrentPosition());elapsedText.setText(formatMs(player.getCurrentPosition()));}if(player!=null)updateProgress();},500);}
    private String formatMs(int ms){int s=Math.max(0,ms/1000);return String.format(Locale.US,"%d:%02d",s/60,s%60);}
    private void showDownloadTab(){findViewById(R.id.downloadPanel).setVisibility(View.VISIBLE);findViewById(R.id.libraryPanel).setVisibility(View.GONE);downloadTab.setTextColor(0xFFA7F36B);libraryTab.setTextColor(0xFFAEB4C0);}
    private void showLibraryTab(){findViewById(R.id.downloadPanel).setVisibility(View.GONE);findViewById(R.id.libraryPanel).setVisibility(View.VISIBLE);downloadTab.setTextColor(0xFFAEB4C0);libraryTab.setTextColor(0xFFA7F36B);refreshLibrary();}
    @Override protected void onStop(){
        super.onStop();
        if(player!=null && player.isPlaying() && selectedTrack!=null){
            Intent intent=new Intent(this,PlaybackService.class).setAction(PlaybackService.ACTION_PLAY).putExtra(PlaybackService.EXTRA_PATH,selectedTrack.getAbsolutePath());
            if(Build.VERSION.SDK_INT>=26) startForegroundService(intent); else startService(intent);
            player.release(); player=null;
        }
    }
    @Override protected void onDestroy(){unregisterReceiver(downloadReceiver);executor.shutdownNow();handler.removeCallbacksAndMessages(null);if(player!=null){player.release();player=null;}super.onDestroy();}
}
