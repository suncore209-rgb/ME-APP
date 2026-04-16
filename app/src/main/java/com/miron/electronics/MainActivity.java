package com.miron.electronics;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.CookieManager;
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
    private static final String APP_URL = "https://mev5.vercel.app/";

    private WebView webView;
    private SwipeRefreshLayout swipeRefresh;
    private LinearLayout offlineLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Full screen — status bar থাকবে, action bar থাকবে না
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

        // ── SwipeRefreshLayout (নিচে টানলে refresh) ─
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
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setSupportZoom(false);

        // Cookies enable
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                // সব link একই WebView এ খুলবে
                view.loadUrl(request.getUrl().toString());
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                swipeRefresh.setRefreshing(false);
                // Page load হলে offline message লুকাও
                offlineLayout.setVisibility(View.GONE);
                swipeRefresh.setVisibility(View.VISIBLE);
            }

            @Override
            public void onReceivedError(android.webkit.WebView view,
                    android.webkit.WebResourceRequest request,
                    android.webkit.WebResourceError error) {
                swipeRefresh.setRefreshing(false);
                if (request.isForMainFrame()) {
                    swipeRefresh.setVisibility(View.GONE);
                    offlineLayout.setVisibility(View.VISIBLE);
                }
            }
        });

        swipeRefresh.setOnRefreshListener(() -> {
            if (isOnline()) {
                webView.reload();
            } else {
                swipeRefresh.setRefreshing(false);
                swipeRefresh.setVisibility(View.GONE);
                offlineLayout.setVisibility(View.VISIBLE);
            }
        });

        swipeRefresh.addView(webView);

        // ── Offline message layout ───────────────────
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

        // ── Assemble ─────────────────────────────────
        root.addView(swipeRefresh);
        root.addView(offlineLayout);
        setContentView(root);

        // ── Load app ─────────────────────────────────
        if (isOnline()) {
            webView.loadUrl(APP_URL);
        } else {
            swipeRefresh.setVisibility(View.GONE);
            offlineLayout.setVisibility(View.VISIBLE);
        }
    }

    // Internet connection check
    private boolean isOnline() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo info = cm.getActiveNetworkInfo();
        return info != null && info.isConnected();
    }

    // Back button — WebView এ back যাবে
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && webView.canGoBack()) {
            webView.goBack();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onResume() {
        super.onResume();
        webView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        webView.onPause();
    }

    @Override
    protected void onDestroy() {
        webView.destroy();
        super.onDestroy();
    }
}
