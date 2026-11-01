package com.example.offlinemusicplayer;

import android.app.*;
import android.content.*;
import android.graphics.drawable.Icon;
import android.media.MediaMetadata;
import android.media.MediaPlayer;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.*;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;

public class PlaybackService extends Service {
    public static final String ACTION_PLAY="com.example.offlinemusicplayer.PLAY";
    public static final String ACTION_PAUSE="com.example.offlinemusicplayer.PAUSE";
    public static final String ACTION_NEXT="com.example.offlinemusicplayer.NEXT";
    public static final String ACTION_PREVIOUS="com.example.offlinemusicplayer.PREVIOUS";
    public static final String ACTION_HANDOFF_TO_APP="com.example.offlinemusicplayer.HANDOFF_TO_APP";
    public static final String ACTION_STOP="com.example.offlinemusicplayer.STOP";
    public static final String EXTRA_PATH="path";
    public static final String EXTRA_QUEUE="queue";
    public static final String EXTRA_INDEX="index";
    public static final String EXTRA_POSITION="position";
    public static final String EXTRA_SHOULD_PLAY="should_play";
    private static final int NOTIFICATION_ID=42;
    private static final String CHANNEL_ID="wfmusic_playback";

    private MediaPlayer player;
    private MediaSession mediaSession;
    private String currentPath;
    private final ArrayList<String> queue=new ArrayList<>();
    private int queueIndex=0;
    private int pendingPositionMs=0;
    private boolean pendingShouldPlay=true;

    @Override public void onCreate(){
        super.onCreate();
        createChannel();
        mediaSession=new MediaSession(this,"Wfmusic");
        mediaSession.setCallback(new MediaSession.Callback(){
            @Override public void onPlay(){ resumePlayback(); }
            @Override public void onPause(){ pausePlayback(); }
            @Override public void onSkipToNext(){ playRelative(1); }
            @Override public void onSkipToPrevious(){ playRelative(-1); }
            @Override public void onStop(){ stopPlayback(); }
        });
        mediaSession.setActive(true);
    }

    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent!=null){
            String[] incomingQueue=intent.getStringArrayExtra(EXTRA_QUEUE);
            if(incomingQueue!=null && incomingQueue.length>0){
                queue.clear(); queue.addAll(Arrays.asList(incomingQueue));
                queueIndex=Math.max(0,Math.min(intent.getIntExtra(EXTRA_INDEX,0),queue.size()-1));
            }
            String action=intent.getAction();
            if(ACTION_HANDOFF_TO_APP.equals(action)){
                releaseForApp();
            } else if(ACTION_PLAY.equals(action)){
                String path=intent.getStringExtra(EXTRA_PATH);
                pendingPositionMs=Math.max(0,intent.getIntExtra(EXTRA_POSITION,0));
                pendingShouldPlay=intent.getBooleanExtra(EXTRA_SHOULD_PLAY,true);
                if(path!=null){ ensureQueueContains(path); play(path); }
                else resumePlayback();
            } else if(ACTION_PAUSE.equals(action)) pausePlayback();
            else if(ACTION_NEXT.equals(action)) playRelative(1);
            else if(ACTION_PREVIOUS.equals(action)) playRelative(-1);
            else if(ACTION_STOP.equals(action)) stopPlayback();
        }
        return START_STICKY;
    }

    private void ensureQueueContains(String path){
        int found=queue.indexOf(path);
        if(found>=0){queueIndex=found;return;}
        queue.clear(); queue.add(path); queueIndex=0;
    }

    private void play(String path){
        try{
            if(player!=null && path.equals(currentPath)){
                if(pendingPositionMs>0)player.seekTo(Math.min(pendingPositionMs,Math.max(0,player.getDuration()-500)));
                if(pendingShouldPlay)resumePlayback();else pausePlayback();
                pendingPositionMs=0; pendingShouldPlay=true; return;
            }
            releasePlayer(); currentPath=path;
            updateMetadata();
            player=new MediaPlayer(); player.setDataSource(path);
            player.setOnPreparedListener(mp->{
                if(pendingPositionMs>0)mp.seekTo(Math.min(pendingPositionMs,Math.max(0,mp.getDuration()-500)));
                if(pendingShouldPlay){ updatePlaybackState(PlaybackState.STATE_PLAYING); mp.start(); }
                else updatePlaybackState(PlaybackState.STATE_PAUSED);
                pendingPositionMs=0; pendingShouldPlay=true; updateNotification();
            });
            player.setOnCompletionListener(mp->playRelative(1));
            startForeground(NOTIFICATION_ID,notification());
            updatePlaybackState(PlaybackState.STATE_BUFFERING);
            player.prepareAsync();
        }catch(Exception e){ stopPlayback(); }
    }

    private void resumePlayback(){
        if(player==null){ if(currentPath!=null) play(currentPath); return; }
        if(!player.isPlaying()){ player.start(); updatePlaybackState(PlaybackState.STATE_PLAYING); updateNotification(); }
    }

    private void pausePlayback(){
        if(player!=null && player.isPlaying()){
            player.pause(); updatePlaybackState(PlaybackState.STATE_PAUSED); updateNotification();
        }
    }

    private void playRelative(int delta){
        if(queue.isEmpty()) return;
        queueIndex=(queueIndex+delta+queue.size())%queue.size();
        play(queue.get(queueIndex));
    }

    private void stopPlayback(){
        releasePlayer(); currentPath=null; clearSavedState();
        if(mediaSession!=null){ updatePlaybackState(PlaybackState.STATE_NONE); mediaSession.setActive(false); }
        stopForeground(STOP_FOREGROUND_REMOVE); stopSelf();
    }

    private void releaseForApp(){
        persistState(); releasePlayer();
        if(mediaSession!=null){mediaSession.setActive(false);}
        getSharedPreferences("wfmusic_playback",MODE_PRIVATE).edit().putBoolean("active",false).apply();
        stopForeground(STOP_FOREGROUND_REMOVE); stopSelf();
    }

    private void releasePlayer(){ if(player!=null){ player.release(); player=null; } }

    private void createChannel(){
        if(Build.VERSION.SDK_INT>=26){
            NotificationChannel c=new NotificationChannel(CHANNEL_ID,"Wfmusic playback",NotificationManager.IMPORTANCE_LOW);
            c.setDescription("Playback controls for offline music");
            c.setShowBadge(false);
            getSystemService(NotificationManager.class).createNotificationChannel(c);
        }
    }

    private PendingIntent serviceIntent(String action,int requestCode){
        Intent i=new Intent(this,PlaybackService.class).setAction(action).putExtra(EXTRA_PATH,currentPath);
        i.putExtra(EXTRA_QUEUE,queue.toArray(new String[0])).putExtra(EXTRA_INDEX,queueIndex);
        return PendingIntent.getService(this,requestCode,i,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private Notification notification(){
        Intent open=new Intent(this,MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent content=PendingIntent.getActivity(this,1,open,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        boolean playing=player!=null && player.isPlaying();
        Notification.Builder builder=new Notification.Builder(this,CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_wf_logo)
            .setContentTitle(displayTitle(currentPath))
            .setContentText(playing?"Wfmusic • Playing":"Wfmusic • Paused")
            .setContentIntent(content)
            .setOngoing(true)
            .setShowWhen(false)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setDeleteIntent(serviceIntent(ACTION_STOP,5))
            .addAction(new Notification.Action.Builder(Icon.createWithResource(this,R.drawable.ic_wf_logo),"Previous",serviceIntent(ACTION_PREVIOUS,3)).build())
            .addAction(new Notification.Action.Builder(Icon.createWithResource(this,R.drawable.ic_wf_logo),playing?"Pause":"Play",serviceIntent(playing?ACTION_PAUSE:ACTION_PLAY,2)).build())
            .addAction(new Notification.Action.Builder(Icon.createWithResource(this,R.drawable.ic_wf_logo),"Next",serviceIntent(ACTION_NEXT,4)).build())
            .addAction(new Notification.Action.Builder(Icon.createWithResource(this,R.drawable.ic_wf_logo),"Stop",serviceIntent(ACTION_STOP,5)).build());
        if(mediaSession!=null){
            builder.setStyle(new Notification.MediaStyle().setMediaSession(mediaSession.getSessionToken()).setShowActionsInCompactView(0,1,2));
        }
        return builder.build();
    }

    private void updateNotification(){
        persistState();
        if(Build.VERSION.SDK_INT>=26) getSystemService(NotificationManager.class).notify(NOTIFICATION_ID,notification());
    }

    private void persistState(){
        if(currentPath==null)return;
        StringBuilder paths=new StringBuilder();for(String p:queue){if(paths.length()>0)paths.append('\n');paths.append(p);}
        getSharedPreferences("wfmusic_playback",MODE_PRIVATE).edit().putBoolean("active",true).putString("path",currentPath)
            .putInt("position",player==null?0:player.getCurrentPosition()).putBoolean("playing",player!=null&&player.isPlaying())
            .putString("queue",paths.toString()).putInt("index",queueIndex).apply();
    }
    private void clearSavedState(){getSharedPreferences("wfmusic_playback",MODE_PRIVATE).edit().clear().apply();}

    private void updateMetadata(){
        if(mediaSession==null||currentPath==null)return;
        String title=displayTitle(currentPath);
        mediaSession.setMetadata(new MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE,title)
            .putString(MediaMetadata.METADATA_KEY_ARTIST,"Wfmusic library")
            .build());
    }

    private void updatePlaybackState(int state){
        if(mediaSession==null)return;
        long actions=PlaybackState.ACTION_PLAY|PlaybackState.ACTION_PAUSE|PlaybackState.ACTION_PLAY_PAUSE|PlaybackState.ACTION_SKIP_TO_NEXT|PlaybackState.ACTION_SKIP_TO_PREVIOUS|PlaybackState.ACTION_STOP;
        mediaSession.setPlaybackState(new PlaybackState.Builder().setActions(actions).setState(state,player==null?0:player.getCurrentPosition(),1f).build());
    }

    private String displayTitle(String path){
        if(path==null)return "Wfmusic";
        String n=new File(path).getName();
        int dot=n.lastIndexOf('.'); if(dot>0)n=n.substring(0,dot);
        return n.replace('_',' ');
    }

    @Override public void onDestroy(){
        releasePlayer(); if(mediaSession!=null){mediaSession.setActive(false);mediaSession.release();mediaSession=null;} super.onDestroy();
    }

    @Override public android.os.IBinder onBind(Intent intent){return null;}
}
