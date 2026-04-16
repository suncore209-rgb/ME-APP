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
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintManager;
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
    private static final String APP_URL = "https://YOUR-APP.vercel.app";

    private WebView webView;
    private SwipeRefreshLayout swipeRefresh;
    private LinearLayout offlineLayout;
    private ValueCallback<Uri[]> fileChooserCallback;
    private static final int FILE_CHOOSER_REQUEST = 100;

    // ── Android bridge: receives slip from JS ─────
    public class SlipBridge {
        @JavascriptInterface
        public void receiveSlip(final String html, final String slipText) {
            runOnUiThread(() -> showSlipDialog(html, slipText));
        }
    }

    // ── Native slip dialog ────────────────────────
    private void showSlipDialog(String html, String slipText) {
        // Full-screen dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#F5F5F5"));

        // WebView showing the slip
        WebView slipView = new WebView(this);
        slipView.getSettings().setJavaScriptEnabled(true);
        slipView.getSettings().setDomStorageEnabled(true);
        slipView.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        slipView.loadDataWithBaseURL(
            "https://fonts.googleapis.com", html, "text/html", "UTF-8", null);

        // Button row
        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setPadding(10, 10, 10, 10);
        btnRow.setGravity(Gravity.CENTER);
        btnRow.setBackgroundColor(Color.WHITE);

        // WhatsApp
        Button waBtn = makeBtn("📲 WhatsApp", "#25D366");
        // PDF/Print
        Button pdfBtn = makeBtn("📄 PDF সেভ", "#0D47A1");
        // Share
        Button shareBtn = makeBtn("📤 শেয়ার", "#546E7A");
        // Close
        Button closeBtn = makeBtn("✕ বন্ধ", "#ECEFF1");
        closeBtn.setTextColor(Color.parseColor("#333333"));

        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(0,
            LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        bp.setMargins(4,0,4,0);
        waBtn.setLayoutParams(bp);
        pdfBtn.setLayoutParams(new LinearLayout.LayoutParams(0,
            LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        shareBtn.setLayoutParams(new LinearLayout.LayoutParams(0,
            LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        closeBtn.setLayoutParams(new LinearLayout.LayoutParams(0,
            LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        btnRow.addView(waBtn);
        btnRow.addView(pdfBtn);
        btnRow.addView(shareBtn);
        btnRow.addView(closeBtn);

        root.addView(slipView);
        root.addView(btnRow);

        AlertDialog dialog = builder.setView(root).create();

        // WhatsApp share
        waBtn.setOnClickListener(v -> {
            try {
                Intent i = new Intent(Intent.ACTION_SEND);
                i.setType("text/plain");
                i.setPackage("com.whatsapp");
                i.putExtra(Intent.EXTRA_TEXT, slipText);
                startActivity(i);
            } catch (Exception e) {
                // WhatsApp নেই — general share
                Intent i = new Intent(Intent.ACTION_SEND);
                i.setType("text/plain");
                i.putExtra(Intent.EXTRA_TEXT, slipText);
                startActivity(Intent.createChooser(i, "শেয়ার করুন"));
            }
        });

        // PDF save via Android PrintManager
        pdfBtn.setOnClickListener(v -> {
            PrintManager pm = (PrintManager) getSystemService(Context.PRINT_SERVICE);
            PrintDocumentAdapter adapter =
                slipView.createPrintDocumentAdapter("মিরন-স্লিপ");
            PrintAttributes attrs = new PrintAttributes.Builder()
                .setMediaSize(PrintAttributes.MediaSize.ISO_A5)
                .setResolution(new PrintAttributes.Resolution("pdf","pdf",300,300))
                .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                .build();
            pm.print("Miron Slip", adapter, attrs);
        });

        // General share (text)
        shareBtn.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("text/plain");
            i.putExtra(Intent.EXTRA_TEXT, slipText);
            startActivity(Intent.createChooser(i, "শেয়ার করুন"));
        });

        closeBtn.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
        Window w = dialog.getWindow();
        if (w != null) {
            w.setLayout(WindowManager.LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.MATCH_PARENT);
        }
    }

    private Button makeBtn(String text, String color) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(11);
        b.setBackgroundColor(Color.parseColor(color));
        b.setTextColor(Color.WHITE);
        b.setPadding(6, 14, 6, 14);
        return b;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                             WindowManager.LayoutParams.FLAG_FULLSCREEN);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.MATCH_PARENT));

        swipeRefresh = new SwipeRefreshLayout(this);
        swipeRefresh.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.MATCH_PARENT, 1f));
        swipeRefresh.setColorSchemeColors(Color.parseColor("#0D47A1"));

        // ✅ Only refresh when truly at top
        swipeRefresh.setOnChildScrollUpCallback((parent, child) ->
            webView != null && webView.getScrollY() > 0);

        webView = new WebView(this);
        webView.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.MATCH_PARENT));

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);

        // ✅ Register slip bridge — JS calls AndroidSlip.receiveSlip()
        webView.addJavascriptInterface(new SlipBridge(), "AndroidSlip");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                view.loadUrl(req.getUrl().toString()); return true;
            }
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                swipeRefresh.setRefreshing(false);
                offlineLayout.setVisibility(View.GONE);
                swipeRefresh.setVisibility(View.VISIBLE);
            }
            @Override
            public void onReceivedError(WebView view, WebResourceRequest req,
                    android.webkit.WebResourceError err) {
                swipeRefresh.setRefreshing(false);
                if (req.isForMainFrame()) {
                    swipeRefresh.setVisibility(View.GONE);
                    offlineLayout.setVisibility(View.VISIBLE);
                }
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onJsConfirm(WebView v, String url, String msg, JsResult r) {
                new AlertDialog.Builder(MainActivity.this)
                    .setMessage(msg)
                    .setPositiveButton("হ্যাঁ", (d,w)->r.confirm())
                    .setNegativeButton("না",    (d,w)->r.cancel())
                    .setOnCancelListener(d->r.cancel()).create().show();
                return true;
            }
            @Override
            public boolean onJsAlert(WebView v, String url, String msg, JsResult r) {
                new AlertDialog.Builder(MainActivity.this)
                    .setMessage(msg)
                    .setPositiveButton("ঠিক আছে",(d,w)->r.confirm())
                    .setOnCancelListener(d->r.confirm()).create().show();
                return true;
            }
            @Override
            public boolean onShowFileChooser(WebView wv,
                    ValueCallback<Uri[]> cb, FileChooserParams params) {
                if (fileChooserCallback != null) fileChooserCallback.onReceiveValue(null);
                fileChooserCallback = cb;
                try { startActivityForResult(params.createIntent(), FILE_CHOOSER_REQUEST); }
                catch (Exception e) { fileChooserCallback = null; return false; }
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

        // Offline view
        offlineLayout = new LinearLayout(this);
        offlineLayout.setOrientation(LinearLayout.VERTICAL);
        offlineLayout.setGravity(Gravity.CENTER);
        offlineLayout.setBackgroundColor(Color.parseColor("#EEF2FF"));
        offlineLayout.setVisibility(View.GONE);
        offlineLayout.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.MATCH_PARENT));

        TextView icon = new TextView(this);
        icon.setText("📡"); icon.setTextSize(48);
        icon.setGravity(Gravity.CENTER);

        TextView msg = new TextView(this);
        msg.setText("ইন্টারনেট সংযোগ নেই\nঅনুগ্রহ করে সংযোগ দিন");
        msg.setTextSize(16); msg.setTextColor(Color.parseColor("#546E7A"));
        msg.setGravity(Gravity.CENTER); msg.setPadding(0,16,0,32);

        Button retry = new Button(this);
        retry.setText("🔄  আবার চেষ্টা করুন");
        retry.setBackgroundColor(Color.parseColor("#0D47A1"));
        retry.setTextColor(Color.WHITE); retry.setPadding(40,20,40,20);
        retry.setOnClickListener(v -> {
            if (isOnline()) {
                offlineLayout.setVisibility(View.GONE);
                swipeRefresh.setVisibility(View.VISIBLE);
                webView.loadUrl(APP_URL);
            }
        });

        offlineLayout.addView(icon);
        offlineLayout.addView(msg);
        offlineLayout.addView(retry);

        root.addView(swipeRefresh);
        root.addView(offlineLayout);
        setContentView(root);

        if (isOnline()) webView.loadUrl(APP_URL);
        else { swipeRefresh.setVisibility(View.GONE); offlineLayout.setVisibility(View.VISIBLE); }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        if (req == FILE_CHOOSER_REQUEST && fileChooserCallback != null) {
            Uri[] results = null;
            if (res == Activity.RESULT_OK && data != null) {
                String s = data.getDataString();
                if (s != null) results = new Uri[]{Uri.parse(s)};
            }
            fileChooserCallback.onReceiveValue(results);
            fileChooserCallback = null;
        }
    }

    private boolean isOnline() {
        ConnectivityManager cm = (ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);
        NetworkInfo ni = cm.getActiveNetworkInfo();
        return ni != null && ni.isConnected();
    }

    @Override
    public boolean onKeyDown(int kc, KeyEvent e) {
        if (kc == KeyEvent.KEYCODE_BACK && webView.canGoBack()) { webView.goBack(); return true; }
        return super.onKeyDown(kc, e);
    }
    @Override protected void onResume()  { super.onResume();  webView.onResume();  }
    @Override protected void onPause()   { super.onPause();   webView.onPause();   }
    @Override protected void onDestroy() { webView.destroy(); super.onDestroy();   }
}
