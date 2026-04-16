package com.miron.electronics;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.print.PrintAttributes;
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
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.core.content.FileProvider;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import java.io.File;
import java.io.FileOutputStream;

public class MainActivity extends Activity {

    private static final String APP_URL = "https://YOUR-APP.vercel.app";
    private static final int FILE_CHOOSER_REQUEST = 100;

    private ScrollAwareWebView webView;
    private SwipeRefreshLayout swipeRefresh;
    private LinearLayout offlineLayout;
    private ValueCallback<Uri[]> fileChooserCallback;

    // ══════════════════════════════════════════════
    //  ScrollAwareWebView — fixes over-refresh bug
    // ══════════════════════════════════════════════
    private class ScrollAwareWebView extends WebView {
        public ScrollAwareWebView(Context ctx) { super(ctx); }

        @Override
        protected void onScrollChanged(int l, int t, int oldl, int oldt) {
            super.onScrollChanged(l, t, oldl, oldt);
            // Only allow pull-to-refresh when truly at the very top
            if (swipeRefresh != null) swipeRefresh.setEnabled(t == 0);
        }
    }

    // ══════════════════════════════════════════════
    //  JS Bridge — receives slip from JS
    // ══════════════════════════════════════════════
    public class SlipBridge {
        @JavascriptInterface
        public void receiveSlip(final String html, final String slipText) {
            runOnUiThread(() -> showSlipDialog(html, slipText));
        }
    }

    // ══════════════════════════════════════════════
    //  Slip dialog — WebView preview + share options
    // ══════════════════════════════════════════════
    private void showSlipDialog(String html, String slipText) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);

        // Loading spinner shown while WebView renders
        ProgressBar progress = new ProgressBar(this);
        progress.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 200));
        progress.setIndeterminate(true);

        // Slip WebView
        WebView slipView = new WebView(this);
        slipView.setBackgroundColor(Color.WHITE);
        slipView.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        slipView.setVisibility(View.GONE); // hidden until loaded

        WebSettings ws = slipView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setLoadWithOverviewMode(true);
        ws.setUseWideViewPort(true);
        ws.setBuiltInZoomControls(true);
        ws.setDisplayZoomControls(false);

        // Button row (disabled until slip loads)
        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setPadding(8, 10, 8, 10);
        btnRow.setGravity(Gravity.CENTER);
        btnRow.setBackgroundColor(Color.parseColor("#F5F5F5"));

        Button waBtn    = makeBtn("📲 WhatsApp", "#25D366", true);
        Button imgBtn   = makeBtn("🖼 Image শেয়ার", "#E65100", true);
        Button pdfBtn   = makeBtn("📄 PDF", "#0D47A1", true);
        Button closeBtn = makeBtn("✕", "#ECEFF1", true);
        closeBtn.setTextColor(Color.parseColor("#333333"));

        // Disable buttons until loaded
        waBtn.setEnabled(false); imgBtn.setEnabled(false); pdfBtn.setEnabled(false);

        setWeight(waBtn,  1f); setWeight(imgBtn, 1f);
        setWeight(pdfBtn, 1f); setWeight(closeBtn, 0.6f);
        btnRow.addView(waBtn); btnRow.addView(imgBtn);
        btnRow.addView(pdfBtn); btnRow.addView(closeBtn);

        root.addView(progress);
        root.addView(slipView);
        root.addView(btnRow);

        AlertDialog dialog = builder.setView(root).create();

        // Show WebView only after fully rendered
        slipView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                progress.setVisibility(View.GONE);
                slipView.setVisibility(View.VISIBLE);
                // Enable buttons now
                waBtn.setEnabled(true);
                imgBtn.setEnabled(true);
                pdfBtn.setEnabled(true);
            }
        });

        // Load slip HTML — use null base URL for inline content
        slipView.loadDataWithBaseURL(
            "https://fonts.gstatic.com", html, "text/html", "UTF-8", null);

        // ── WhatsApp — share as image ──────────────
        waBtn.setOnClickListener(v -> {
            Uri imgUri = captureAndSave(slipView, "slip_wa");
            if (imgUri != null) {
                try {
                    Intent i = new Intent(Intent.ACTION_SEND);
                    i.setType("image/png");
                    i.setPackage("com.whatsapp");
                    i.putExtra(Intent.EXTRA_STREAM, imgUri);
                    i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(i);
                } catch (Exception e) {
                    // WhatsApp নেই — text fallback
                    Intent i = new Intent(Intent.ACTION_SEND);
                    i.setType("text/plain");
                    i.putExtra(Intent.EXTRA_TEXT, slipText);
                    startActivity(Intent.createChooser(i, "WhatsApp"));
                }
            }
        });

        // ── Image share — any app ──────────────────
        imgBtn.setOnClickListener(v -> {
            Uri imgUri = captureAndSave(slipView, "slip_share");
            if (imgUri != null) {
                Intent i = new Intent(Intent.ACTION_SEND);
                i.setType("image/png");
                i.putExtra(Intent.EXTRA_STREAM, imgUri);
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(Intent.createChooser(i, "স্লিপ শেয়ার করুন"));
            }
        });

        // ── PDF via Android PrintManager ───────────
        pdfBtn.setOnClickListener(v -> {
            PrintManager pm = (PrintManager) getSystemService(PRINT_SERVICE);
            pm.print("মিরন-স্লিপ",
                slipView.createPrintDocumentAdapter("মিরন-স্লিপ"),
                new PrintAttributes.Builder()
                    .setMediaSize(PrintAttributes.MediaSize.ISO_A5)
                    .setResolution(new PrintAttributes.Resolution("p","p",300,300))
                    .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                    .build());
        });

        closeBtn.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
        Window w = dialog.getWindow();
        if (w != null) w.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT);
    }

    // Capture WebView as PNG and return FileProvider Uri
    private Uri captureAndSave(WebView wv, String name) {
        try {
            float d = getResources().getDisplayMetrics().density;
            int h = Math.max((int)(wv.getContentHeight() * d), 100);
            int w = Math.max(wv.getWidth(), 100);

            Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            bmp.eraseColor(Color.WHITE);
            Canvas c = new Canvas(bmp);
            wv.draw(c);

            File out = new File(getCacheDir(), name + ".png");
            FileOutputStream fos = new FileOutputStream(out);
            bmp.compress(Bitmap.CompressFormat.PNG, 95, fos);
            fos.flush(); fos.close();

            return FileProvider.getUriForFile(this,
                getPackageName() + ".provider", out);
        } catch (Exception e) {
            return null;
        }
    }

    private Button makeBtn(String text, String color, boolean small) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(small ? 11 : 13);
        b.setBackgroundColor(Color.parseColor(color));
        b.setTextColor(Color.WHITE);
        b.setPadding(4, 12, 4, 12);
        return b;
    }

    private void setWeight(Button b, float w) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, w);
        p.setMargins(3, 0, 3, 0);
        b.setLayoutParams(p);
    }

    // ══════════════════════════════════════════════
    //  onCreate
    // ══════════════════════════════════════════════
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
        swipeRefresh.setEnabled(true); // start enabled (at top)

        // ── ScrollAwareWebView ─────────────────────
        webView = new ScrollAwareWebView(this);
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

        webView.addJavascriptInterface(new SlipBridge(), "AndroidSlip");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                v.loadUrl(r.getUrl().toString()); return true;
            }
            @Override
            public void onPageFinished(WebView v, String url) {
                super.onPageFinished(v, url);
                swipeRefresh.setRefreshing(false);
                offlineLayout.setVisibility(View.GONE);
                swipeRefresh.setVisibility(View.VISIBLE);
            }
            @Override
            public void onReceivedError(WebView v, WebResourceRequest r,
                    android.webkit.WebResourceError e) {
                swipeRefresh.setRefreshing(false);
                if (r.isForMainFrame()) {
                    swipeRefresh.setVisibility(View.GONE);
                    offlineLayout.setVisibility(View.VISIBLE);
                }
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onJsConfirm(WebView v, String u, String m, JsResult r) {
                new AlertDialog.Builder(MainActivity.this).setMessage(m)
                    .setPositiveButton("হ্যাঁ", (d,w)->r.confirm())
                    .setNegativeButton("না",    (d,w)->r.cancel())
                    .setOnCancelListener(d->r.cancel()).create().show();
                return true;
            }
            @Override
            public boolean onJsAlert(WebView v, String u, String m, JsResult r) {
                new AlertDialog.Builder(MainActivity.this).setMessage(m)
                    .setPositiveButton("ঠিক আছে",(d,w)->r.confirm())
                    .setOnCancelListener(d->r.confirm()).create().show();
                return true;
            }
            @Override
            public boolean onShowFileChooser(WebView wv, ValueCallback<Uri[]> cb,
                    FileChooserParams p) {
                if (fileChooserCallback != null) fileChooserCallback.onReceiveValue(null);
                fileChooserCallback = cb;
                try { startActivityForResult(p.createIntent(), FILE_CHOOSER_REQUEST); }
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

        // ── Offline view ───────────────────────────
        offlineLayout = new LinearLayout(this);
        offlineLayout.setOrientation(LinearLayout.VERTICAL);
        offlineLayout.setGravity(Gravity.CENTER);
        offlineLayout.setBackgroundColor(Color.parseColor("#EEF2FF"));
        offlineLayout.setVisibility(View.GONE);
        offlineLayout.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.MATCH_PARENT));

        TextView ico = new TextView(this);
        ico.setText("📡"); ico.setTextSize(48); ico.setGravity(Gravity.CENTER);

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
        offlineLayout.addView(ico); offlineLayout.addView(msg); offlineLayout.addView(retry);

        root.addView(swipeRefresh); root.addView(offlineLayout);
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
        if (kc == KeyEvent.KEYCODE_BACK && webView.canGoBack()) {
            webView.goBack(); return true;
        }
        return super.onKeyDown(kc, e);
    }
    @Override protected void onResume()  { super.onResume();  webView.onResume();  }
    @Override protected void onPause()   { super.onPause();   webView.onPause();   }
    @Override protected void onDestroy() { webView.destroy(); super.onDestroy();   }
}
