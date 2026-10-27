package com.markdave.tagotagoan;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.webkit.WebViewAssetLoader;

public class MainActivity extends Activity {
    private static final int REQ_CAMERA = 1;
    private static final int REQ_FILE = 2;
    private WebView web;
    private PermissionRequest pendingPermission;
    private ValueCallback<Uri[]> fileCallback;
    private RelayServer relay;
    private LanBridge lan;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        web = new WebView(this);
        setContentView(web);
        hideSystemBars();

        final WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);

        lan = new LanBridge(this, web);
        web.addJavascriptInterface(new Bridge(), "Android");

        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                // Serve the 3D engine from the app itself so 3D works offline (e.g. on a hotspot with no internet)
                if (u != null && "cdnjs.cloudflare.com".equals(u.getHost()) && u.getPath() != null && u.getPath().endsWith("/three.min.js")) {
                    try {
                        return new WebResourceResponse("application/javascript", "utf-8", getAssets().open("lib/three.min.js"));
                    } catch (Exception ignored) { }
                }
                return loader.shouldInterceptRequest(u);
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                runOnUiThread(() -> {
                    java.util.List<String> need = new java.util.ArrayList<>();
                    for (String r : request.getResources()) {
                        if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(r)
                                && checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                            need.add(Manifest.permission.CAMERA);
                        }
                        if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(r)
                                && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                            need.add(Manifest.permission.RECORD_AUDIO);
                        }
                    }
                    if (need.isEmpty()) {
                        request.grant(request.getResources());
                    } else {
                        pendingPermission = request;
                        requestPermissions(need.toArray(new String[0]), REQ_CAMERA);
                    }
                });
            }

            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                String type = "image/*";
                String[] accept = params.getAcceptTypes();
                if (accept != null && accept.length > 0 && accept[0] != null && !accept[0].trim().isEmpty()) {
                    type = accept[0].trim();
                }
                intent.setType(type);
                try {
                    startActivityForResult(Intent.createChooser(intent, type.startsWith("audio") ? "Pick an audio file" : "Pick a face photo"), REQ_FILE);
                } catch (Exception e) {
                    fileCallback = null;
                    return false;
                }
                return true;
            }
        });

        web.loadUrl("https://appassets.androidplatform.net/assets/index.html");
    }

    /** Called from the game's JavaScript as window.Android.* */
    public class Bridge {
        @JavascriptInterface
        public String startHost(String name) {
            try {
                stopRelay();
                relay = new RelayServer(47730);
                relay.start();
                lan.stopListen();
                String n = name == null ? "Player" : name;
                if (n.length() > 12) n = n.substring(0, 12);
                lan.startBroadcast(n);
                return "ok";
            } catch (Exception e) {
                return "error";
            }
        }

        @JavascriptInterface
        public void stopHost() {
            lan.stopBroadcast();
            stopRelay();
        }

        @JavascriptInterface
        public String getIp() { return LanBridge.localIp(); }

        @JavascriptInterface
        public void startDiscovery() { lan.startListen(); }

        @JavascriptInterface
        public void stopDiscovery() { lan.stopListen(); }

        @JavascriptInterface
        public void copyText(final String text) {
            runOnUiThread(() -> {
                try {
                    ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("Tago Tagoan prompt", text));
                } catch (Exception ignored) { }
            });
        }
    }

    private void stopRelay() {
        RelayServer r = relay;
        relay = null;
        if (r != null) {
            try { r.stop(500); } catch (Exception ignored) { }
        }
    }

    @Override
    protected void onDestroy() {
        if (lan != null) { lan.stopBroadcast(); lan.stopListen(); }
        stopRelay();
        super.onDestroy();
    }

    private void hideSystemBars() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemBars();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == REQ_CAMERA && pendingPermission != null) {
            boolean all = results.length > 0;
            for (int r : results) if (r != PackageManager.PERMISSION_GRANTED) all = false;
            if (all) {
                pendingPermission.grant(pendingPermission.getResources());
            } else {
                pendingPermission.deny();
            }
            pendingPermission = null;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_FILE && fileCallback != null) {
            Uri[] result = null;
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                result = new Uri[]{data.getData()};
            }
            fileCallback.onReceiveValue(result);
            fileCallback = null;
        }
    }

    @Override
    public void onBackPressed() {
        web.evaluateJavascript(
                "(function(){try{if(typeof S!=='undefined'&&S&&!S.over&&!userPaused){setPause(true);return 'paused';}" +
                "if(!document.getElementById('menu').hidden){return 'menu';}showScreen('menu');return 'nav';}catch(e){return 'menu';}})()",
                value -> {
                    if (value != null && value.contains("menu")) finish();
                });
    }

    @Override
    protected void onPause() {
        super.onPause();
        web.evaluateJavascript("try{if(typeof S!=='undefined'&&S&&!S.over)setPause(true)}catch(e){}", null);
        web.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        web.onResume();
        hideSystemBars();
    }
}
