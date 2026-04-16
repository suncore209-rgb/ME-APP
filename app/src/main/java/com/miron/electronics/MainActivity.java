package com.miron.electronics;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.JsResult;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

public class MainActivity extends Activity {

    // ══════════════════════════════════════════════
    //  👇 আপনার Vercel App URL এখানে দিন
    // ══════════════════════════════════════════════
    private static final String APP_URL = "https://YOUR-APP.vercel.app";

    private WebView webView;
    private SwipeRefreshLayout swipeRefresh;
    private LinearLayout offlineLayout;

    // File chooser for image upload
    private ValueCallback<Uri[]> fileChooserCallback;
    private static final int FILE_CHOOSER_REQUEST = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        );

        // ── Root layout ──────────────────────────────
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.MATCH_PARENT
        ));

        // ── SwipeRefreshLayout ───────────────────────
        swipeRefresh = new SwipeRefreshLayout(this);
        swipeRefresh.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.MATCH_PARENT, 1f
        ));
        swipeRefresh.setColorSchemeColors(Color.parseColor("#0D47A1"));

        // ── WebView ──────────────────────────────────
        webView = new WebView(this);
        webView.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.MATCH_PARENT
        ));

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setSupportMultipleWindows(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);

        // ── WebViewClient ────────────────────────────
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                view.loadUrl(request.getUrl().toString());
                return true;
            }
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                swipeRefresh.setRefreshing(false);
                offlineLayout.setVisibility(View.GONE);
                swipeRefresh.setVisibility(View.VISIBLE);
            }
            @Override
            public void onReceivedError(WebView view,
                    android.webkit.WebResourceRequest request,
                    android.webkit.WebResourceError error) {
                swipeRefresh.setRefreshing(false);
                if (request.isForMainFrame()) {
                    swipeRefresh.setVisibility(View.GONE);
                    offlineLayout.setVisibility(View.VISIBLE);
                }
            }
        });

        // ── WebChromeClient — confirm, alert, file ───
        webView.setWebChromeClient(new WebChromeClient() {

            // ✅ confirm() dialog — delete এর জন্য
            @Override
            public boolean onJsConfirm(WebView view, String url,
                    String message, final JsResult result) {
                new AlertDialog.Builder(MainActivity.this)
                    .setMessage(message)
                    .setPositiveButton("হ্যাঁ", (d, w) -> result.confirm())
                    .setNegativeButton("না", (d, w) -> result.cancel())
                    .setOnCancelListener(d -> result.cancel())
                    .create().show();
                return true;
            }

            // ✅ alert() dialog
            @Override
            public boolean onJsAlert(WebView view, String url,
                    String message, final JsResult result) {
                new AlertDialog.Builder(MainActivity.this)
                    .setMessage(message)
                    .setPositiveButton("ঠিক আছে", (d, w) -> result.confirm())
                    .setOnCancelListener(d -> result.confirm())
                    .create().show();
                return true;
            }

            // ✅ Image upload — file picker
            @Override
            public boolean onShowFileChooser(WebView webView,
                    ValueCallback<Uri[]> filePathCallback,
                    FileChooserParams fileChooserParams) {
                if (fileChooserCallback != null) {
                    fileChooserCallback.onReceiveValue(null);
                }
                fileChooserCallback = filePathCallback;
                Intent intent = fileChooserParams.createIntent();
                try {
                    startActivityForResult(intent, FILE_CHOOSER_REQUEST);
                } catch (Exception e) {
                    fileChooserCallback = null;
                    return false;
                }
                return true;
            }

            // ✅ window.open() — slip print এর জন্য
            @Override
            public boolean onCreateWindow(WebView view, boolean isDialog,
                    boolean isUserGesture, android.os.Message resultMsg) {
                WebView popup = new WebView(MainActivity.this);
                popup.getSettings().setJavaScriptEnabled(true);
                popup.setWebViewClient(new WebViewClient());
                WebView.WebViewTransport transport =
                    (WebView.WebViewTransport) resultMsg.obj;
                transport.setWebView(popup);
                resultMsg.sendToTarget();
                return true;
            }
        });

        // Swipe to refresh
        swipeRefresh.setOnRefreshListener(() -> {
            if (isOnline()) webView.reload();
            else {
                swipeRefresh.setRefreshing(false);
                swipeRefresh.setVisibility(View.GONE);
                offlineLayout.setVisibility(View.VISIBLE);
            }
        });

        swipeRefresh.addView(webView);

        // ── Offline layout ───────────────────────────
        offlineLayout = new LinearLayout(this);
        offlineLayout.setOrientation(LinearLayout.VERTICAL);
        offlineLayout.setGravity(android.view.Gravity.CENTER);
        offlineLayout.setBackgroundColor(Color.parseColor("#EEF2FF"));
        offlineLayout.setVisibility(View.GONE);
        offlineLayout.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.MATCH_PARENT
        ));

        TextView offlineIcon = new TextView(this);
        offlineIcon.setText("📡");
        offlineIcon.setTextSize(48);
        offlineIcon.setGravity(android.view.Gravity.CENTER);

        TextView offlineText = new TextView(this);
        offlineText.setText("ইন্টারনেট সংযোগ নেই\nঅনুগ্রহ করে সংযোগ দিন");
        offlineText.setTextSize(16);
        offlineText.setTextColor(Color.parseColor("#546E7A"));
        offlineText.setGravity(android.view.Gravity.CENTER);
        offlineText.setPadding(0, 16, 0, 32);

        Button retryBtn = new Button(this);
        retryBtn.setText("🔄  আবার চেষ্টা করুন");
        retryBtn.setTextSize(14);
        retryBtn.setBackgroundColor(Color.parseColor("#0D47A1"));
        retryBtn.setTextColor(Color.WHITE);
        retryBtn.setPadding(40, 20, 40, 20);
        retryBtn.setOnClickListener(v -> {
            if (isOnline()) {
                offlineLayout.setVisibility(View.GONE);
                swipeRefresh.setVisibility(View.VISIBLE);
                webView.loadUrl(APP_URL);
            }
        });

        offlineLayout.addView(offlineIcon);
        offlineLayout.addView(offlineText);
        offlineLayout.addView(retryBtn);

        root.addView(swipeRefresh);
        root.addView(offlineLayout);
        setContentView(root);

        if (isOnline()) webView.loadUrl(APP_URL);
        else {
            swipeRefresh.setVisibility(View.GONE);
            offlineLayout.setVisibility(View.VISIBLE);
        }
    }

    // ✅ File chooser result
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == FILE_CHOOSER_REQUEST) {
            if (fileChooserCallback != null) {
                Uri[] results = null;
                if (resultCode == Activity.RESULT_OK && data != null) {
                    String dataString = data.getDataString();
                    if (dataString != null) {
                        results = new Uri[]{Uri.parse(dataString)};
                    }
                }
                fileChooserCallback.onReceiveValue(results);
                fileChooserCallback = null;
            }
        }
    }

    private boolean isOnline() {
        ConnectivityManager cm =
            (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo info = cm.getActiveNetworkInfo();
        return info != null && info.isConnected();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && webView.canGoBack()) {
            webView.goBack();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override protected void onResume()  { super.onResume();  webView.onResume();  }
    @Override protected void onPause()   { super.onPause();   webView.onPause();   }
    @Override protected void onDestroy() { webView.destroy(); super.onDestroy();   }
}
