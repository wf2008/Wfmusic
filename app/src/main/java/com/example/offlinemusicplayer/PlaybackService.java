package com.example.offlinemusicplayer;

import android.app.*;
import android.content.*;
import android.media.MediaPlayer;
import android.os.*;

public class PlaybackService extends Service {
    public static final String ACTION_PLAY="com.example.offlinemusicplayer.PLAY";
    public static final String ACTION_PAUSE="com.example.offlinemusicplayer.PAUSE";
    public static final String ACTION_STOP="com.example.offlinemusicplayer.STOP";
    public static final String EXTRA_PATH="path";
    private static final int NOTIFICATION_ID=42;
    private MediaPlayer player;
    private String currentPath;

    @Override public void onCreate(){ super.onCreate(); createChannel(); }
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        String action=intent==null?null:intent.getAction();
        if(ACTION_PLAY.equals(action)){ String path=intent.getStringExtra(EXTRA_PATH); if(path!=null) play(path); }
        else if(ACTION_PAUSE.equals(action)&&player!=null&&player.isPlaying()){ player.pause(); updateNotification("Paused"); }
        else if(ACTION_STOP.equals(action)){ stopPlayback(); }
        return START_STICKY;
    }
    private void play(String path){
        try{
            if(player!=null&&path.equals(currentPath)){ if(!player.isPlaying()){player.start();updateNotification("Playing");} return; }
            releasePlayer(); currentPath=path; player=new MediaPlayer(); player.setDataSource(path);
            player.setOnPreparedListener(mp->{mp.start();updateNotification("Playing");});
            player.setOnCompletionListener(mp->stopPlayback()); player.prepareAsync();
            startForeground(NOTIFICATION_ID, notification("Loading"));
        }catch(Exception e){stopPlayback();}
    }
    private void stopPlayback(){releasePlayer();stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();}
    private void releasePlayer(){if(player!=null){player.release();player=null;}}
    private void createChannel(){if(Build.VERSION.SDK_INT>=26){NotificationChannel c=new NotificationChannel("wfmusic_playback","Wfmusic playback",NotificationManager.IMPORTANCE_LOW);c.setDescription("Offline music playback");getSystemService(NotificationManager.class).createNotificationChannel(c);}}
    private Notification notification(String state){Intent open=new Intent(this,MainActivity.class);PendingIntent content=PendingIntent.getActivity(this,1,open,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);Intent pause=new Intent(this,PlaybackService.class).setAction(player!=null&&player.isPlaying()?ACTION_PAUSE:ACTION_PLAY).putExtra(EXTRA_PATH,currentPath);PendingIntent pausePi=PendingIntent.getService(this,2,pause,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);return new Notification.Builder(this,"wfmusic_playback").setContentTitle("Wfmusic").setContentText(state).setSmallIcon(com.example.offlinemusicplayer.R.drawable.ic_wf_logo).setContentIntent(content).setOngoing(true).addAction(new Notification.Action.Builder(null,player!=null&&player.isPlaying()?"Pause":"Resume",pausePi).build()).build();}
    private void updateNotification(String state){if(Build.VERSION.SDK_INT>=26)getSystemService(NotificationManager.class).notify(NOTIFICATION_ID,notification(state));}
    @Override public void onDestroy(){releasePlayer();super.onDestroy();}
    @Override public android.os.IBinder onBind(Intent intent){return null;}
}
