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
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
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
    private static final String APP_URL = "https://mev5.vercel.app/";

    private WebView webView;
    private SwipeRefreshLayout swipeRefresh;
    private LinearLayout offlineLayout;
    private ValueCallback<Uri[]> fileChooserCallback;
    private static final int FILE_CHOOSER_REQUEST = 100;

    // ── JavaScript Bridge for slip ────────────────
    public class SlipBridge {
        private String lastSlipHtml = "";
        private String lastSlipText = "";

        @JavascriptInterface
        public void receiveSlip(String html, String text) {
            lastSlipHtml = html;
            lastSlipText = text;
            runOnUiThread(() -> showSlipDialog(html, text));
        }
    }

    private final SlipBridge slipBridge = new SlipBridge();

    // ── Show slip in native dialog ────────────────
    private void showSlipDialog(String html, String slipText) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundColor(Color.parseColor("#F5F5F5"));

        // Slip WebView
        WebView slipView = new WebView(this);
        slipView.getSettings().setJavaScriptEnabled(true);
        slipView.getSettings().setDomStorageEnabled(true);
        slipView.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        slipView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null);

        // Button row
        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setPadding(12, 12, 12, 12);
        btnRow.setGravity(Gravity.CENTER);
        btnRow.setBackgroundColor(Color.WHITE);

        // WhatsApp share button
        Button waBtn = new Button(this);
        waBtn.setText("📲 WhatsApp শেয়ার");
        waBtn.setTextSize(13);
        waBtn.setBackgroundColor(Color.parseColor("#25D366"));
        waBtn.setTextColor(Color.WHITE);
        LinearLayout.LayoutParams waParams = new LinearLayout.LayoutParams(0,
            LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        waParams.setMargins(0, 0, 8, 0);
        waBtn.setLayoutParams(waParams);
        waBtn.setPadding(8, 16, 8, 16);

        // General share button
        Button shareBtn = new Button(this);
        shareBtn.setText("📤 শেয়ার");
        shareBtn.setTextSize(13);
        shareBtn.setBackgroundColor(Color.parseColor("#0D47A1"));
        shareBtn.setTextColor(Color.WHITE);
        LinearLayout.LayoutParams shareParams = new LinearLayout.LayoutParams(0,
            LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        shareParams.setMargins(0, 0, 8, 0);
        shareBtn.setLayoutParams(shareParams);
        shareBtn.setPadding(8, 16, 8, 16);

        // Close button
        Button closeBtn = new Button(this);
        closeBtn.setText("✕ বন্ধ");
        closeBtn.setTextSize(13);
        closeBtn.setBackgroundColor(Color.parseColor("#ECEFF1"));
        closeBtn.setTextColor(Color.parseColor("#333333"));
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(0,
            LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        closeBtn.setLayoutParams(closeParams);
        closeBtn.setPadding(8, 16, 8, 16);

        btnRow.addView(waBtn);
        btnRow.addView(shareBtn);
        btnRow.addView(closeBtn);

        layout.addView(slipView);
        layout.addView(btnRow);

        AlertDialog dialog = builder.setView(layout).create();

        // WhatsApp share
        waBtn.setOnClickListener(v -> {
            try {
                Intent intent = new Intent(Intent.ACTION_SEND);
                intent.setType("text/plain");
                intent.setPackage("com.whatsapp");
                intent.putExtra(Intent.EXTRA_TEXT, slipText);
                startActivity(intent);
            } catch (Exception e) {
                // WhatsApp not installed — general share
                Intent intent = new Intent(Intent.ACTION_SEND);
                intent.setType("text/plain");
                intent.putExtra(Intent.EXTRA_TEXT, slipText);
                startActivity(Intent.createChooser(intent, "শেয়ার করুন"));
            }
        });

        // General share
        shareBtn.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_TEXT, slipText);
            startActivity(Intent.createChooser(intent, "শেয়ার করুন"));
        });

        closeBtn.setOnClickListener(v -> dialog.dismiss());

        // Full screen dialog
        dialog.show();
        Window w = dialog.getWindow();
        if (w != null) {
            w.setLayout(WindowManager.LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.MATCH_PARENT);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                             WindowManager.LayoutParams.FLAG_FULLSCREEN);

        // ── Root layout ──────────────────────────────
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.MATCH_PARENT));

        // ── SwipeRefreshLayout ───────────────────────
        swipeRefresh = new SwipeRefreshLayout(this);
        swipeRefresh.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.MATCH_PARENT, 1f));
        swipeRefresh.setColorSchemeColors(Color.parseColor("#0D47A1"));

        // ✅ Fix: only refresh when WebView is at very top
        swipeRefresh.setOnChildScrollUpCallback((parent, child) -> {
            if (webView != null) return webView.getScrollY() > 0;
            return false;
        });

        // ── WebView ──────────────────────────────────
        webView = new WebView(this);
        webView.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.MATCH_PARENT));

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

        // ✅ Slip bridge — intercepts window.open()
        webView.addJavascriptInterface(slipBridge, "AndroidSlip");

        // ── WebViewClient ────────────────────────────
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                view.loadUrl(req.getUrl().toString());
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                swipeRefresh.setRefreshing(false);
                offlineLayout.setVisibility(View.GONE);
                swipeRefresh.setVisibility(View.VISIBLE);

                // ✅ Inject JS: override window.open() to send slip to Android
                String js =
                    "window.open = function(url, target, features) {" +
                    "  var fakeWin = {" +
                    "    document: {" +
                    "      _html: ''," +
                    "      write: function(h) { this._html += h; }," +
                    "      close: function() {" +
                    "        var html = this._html;" +
                    "        var text = '';" +
                    "        try {" +
                    "          var tmp = document.createElement('div');" +
                    "          tmp.innerHTML = html;" +
                    "          var rows = tmp.querySelectorAll('tr');" +
                    "          var hdr = tmp.querySelector('.co-name');" +
                    "          if(hdr) text += hdr.innerText + '\\n';" +
                    "          var slipT = tmp.querySelector('.slip-t');" +
                    "          if(slipT) text += slipT.innerText + '\\n';" +
                    "          var metas = tmp.querySelectorAll('.meta td');" +
                    "          for(var i=0;i<metas.length;i+=2){" +
                    "            if(metas[i+1]) text += metas[i].innerText+': '+metas[i+1].innerText+'\\n';" +
                    "          }" +
                    "          text += '\\n';" +
                    "          rows.forEach(function(r){" +
                    "            var cells=r.querySelectorAll('td,th');" +
                    "            var line=[];" +
                    "            cells.forEach(function(c){line.push(c.innerText.trim());});" +
                    "            if(line.length) text+=line.join(' | ')+'\\n';" +
                    "          });" +
                    "        } catch(e) { text = 'স্লিপ'; }" +
                    "        if(window.AndroidSlip) AndroidSlip.receiveSlip(html, text);" +
                    "      }" +
                    "    }," +
                    "    close: function(){}" +
                    "  };" +
                    "  return fakeWin;" +
                    "};";
                view.evaluateJavascript(js, null);
            }

            @Override
            public void onReceivedError(WebView view,
                    WebResourceRequest request,
                    android.webkit.WebResourceError error) {
                swipeRefresh.setRefreshing(false);
                if (request.isForMainFrame()) {
                    swipeRefresh.setVisibility(View.GONE);
                    offlineLayout.setVisibility(View.VISIBLE);
                }
            }
        });

        // ── WebChromeClient ──────────────────────────
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onJsConfirm(WebView view, String url,
                    String message, final JsResult result) {
                new AlertDialog.Builder(MainActivity.this)
                    .setMessage(message)
                    .setPositiveButton("হ্যাঁ", (d, w) -> result.confirm())
                    .setNegativeButton("না",    (d, w) -> result.cancel())
                    .setOnCancelListener(d -> result.cancel())
                    .create().show();
                return true;
            }

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

            @Override
            public boolean onShowFileChooser(WebView webView,
                    ValueCallback<Uri[]> filePathCallback,
                    FileChooserParams fileChooserParams) {
                if (fileChooserCallback != null)
                    fileChooserCallback.onReceiveValue(null);
                fileChooserCallback = filePathCallback;
                try {
                    startActivityForResult(
                        fileChooserParams.createIntent(), FILE_CHOOSER_REQUEST);
                } catch (Exception e) {
                    fileChooserCallback = null;
                    return false;
                }
                return true;
            }
        });

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
        offlineLayout.setGravity(Gravity.CENTER);
        offlineLayout.setBackgroundColor(Color.parseColor("#EEF2FF"));
        offlineLayout.setVisibility(View.GONE);
        offlineLayout.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.MATCH_PARENT));

        TextView offlineIcon = new TextView(this);
        offlineIcon.setText("📡");
        offlineIcon.setTextSize(48);
        offlineIcon.setGravity(Gravity.CENTER);

        TextView offlineText = new TextView(this);
        offlineText.setText("ইন্টারনেট সংযোগ নেই\nঅনুগ্রহ করে সংযোগ দিন");
        offlineText.setTextSize(16);
        offlineText.setTextColor(Color.parseColor("#546E7A"));
        offlineText.setGravity(Gravity.CENTER);
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

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == FILE_CHOOSER_REQUEST && fileChooserCallback != null) {
            Uri[] results = null;
            if (resultCode == Activity.RESULT_OK && data != null) {
                String s = data.getDataString();
                if (s != null) results = new Uri[]{Uri.parse(s)};
            }
            fileChooserCallback.onReceiveValue(results);
            fileChooserCallback = null;
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
            webView.goBack(); return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override protected void onResume()  { super.onResume();  webView.onResume();  }
    @Override protected void onPause()   { super.onPause();   webView.onPause();   }
    @Override protected void onDestroy() { webView.destroy(); super.onDestroy();   }
}
