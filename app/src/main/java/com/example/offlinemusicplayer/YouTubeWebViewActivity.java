package com.example.offlinemusicplayer;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.TextView;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

public class YouTubeWebViewActivity extends Activity {
    private WebView webView;
    private TextView status;
    private boolean saved;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state); setContentView(R.layout.activity_youtube_webview);
        status=findViewById(R.id.webStatus); webView=findViewById(R.id.youtubeWebView);
        Button use=findViewById(R.id.useSessionButton); use.setOnClickListener(v -> finishWithSession());
        CookieManager cookies=CookieManager.getInstance(); cookies.setAcceptCookie(true); cookies.setAcceptThirdPartyCookies(webView,true);
        WebSettings settings=webView.getSettings(); settings.setJavaScriptEnabled(true); settings.setDomStorageEnabled(true); settings.setMediaPlaybackRequiresUserGesture(false);
        webView.setWebViewClient(new WebViewClient(){
            @Override public void onPageFinished(WebView view,String url){ super.onPageFinished(view,url); saveYouTubeCookies(); }
        });
        String start=getIntent().getStringExtra("url"); webView.loadUrl(start==null||start.isEmpty()?"https://www.youtube.com/":start);
    }

    private void saveYouTubeCookies() {
        String header=CookieManager.getInstance().getCookie("https://www.youtube.com/");
        if(header==null||header.trim().isEmpty()) { status.setText("YouTube session: sign in or complete verification, then tap USE SESSION"); return; }
        try(FileOutputStream out=openFileOutput("youtube-cookies.txt",MODE_PRIVATE)){
            out.write(header.getBytes(StandardCharsets.UTF_8)); CookieManager.getInstance().flush(); saved=true;
            status.setText("YouTube session saved privately on this phone");
        }catch(Exception e){ status.setText("Could not save the private session: "+e.getMessage()); }
    }

    private void finishWithSession(){ saveYouTubeCookies(); setResult(saved?RESULT_OK:RESULT_CANCELED); finish(); }
    @Override public void onBackPressed(){ setResult(saved?RESULT_OK:RESULT_CANCELED); super.onBackPressed(); }
}
