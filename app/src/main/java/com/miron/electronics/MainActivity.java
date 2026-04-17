package com.miron.electronics;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
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
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;

public class MainActivity extends Activity {

    private static final String APP_URL = "https://mev5.vercel.app/";
    private static final int FILE_CHOOSER_REQUEST = 100;

    private WebView webView;
    private LinearLayout offlineLayout;
    private ValueCallback<Uri[]> fileChooserCallback;

    // ══════════════════════════════════════════════
    //  JS Bridge — slip এলে PDF বানাই
    // ══════════════════════════════════════════════
    public class SlipBridge {
        @JavascriptInterface
        public void receiveSlip(final String html, final String slipText) {
            runOnUiThread(() -> renderSlipToPdf(html));
        }
    }

    // ══════════════════════════════════════════════
    //  Slip → PDF render (offscreen WebView)
    // ══════════════════════════════════════════════
    private void renderSlipToPdf(String html) {
        // Loading dialog
        AlertDialog loading = new AlertDialog.Builder(this)
            .setMessage("স্লিপ তৈরি হচ্ছে...")
            .setCancelable(false)
            .create();
        loading.show();

        // Offscreen WebView to render slip
        WebView renderer = new WebView(this);
        renderer.setBackgroundColor(Color.WHITE);
        int w = getResources().getDisplayMetrics().widthPixels;
        renderer.layout(0, 0, w, 10);

        WebSettings ws = renderer.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setLoadWithOverviewMode(false);
        ws.setUseWideViewPort(false);

        renderer.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                // Wait for fonts/layout to settle
                view.postDelayed(() -> {
                    try {
                        // Measure full content height
                        float density = getResources().getDisplayMetrics().density;
                        int contentH = (int)(view.getContentHeight() * density);
                        int contentW = w;

                        // Re-layout to full height
                        view.layout(0, 0, contentW, contentH);

                        // Capture to Bitmap
                        Bitmap bmp = Bitmap.createBitmap(contentW, contentH, Bitmap.Config.ARGB_8888);
                        bmp.eraseColor(Color.WHITE);
                        Canvas canvas = new Canvas(bmp);
                        view.draw(canvas);

                        // Bitmap → PDF
                        PdfDocument pdf = new PdfDocument();
                        // A5 size in points (72dpi): 419 x 595 pt
                        // Scale bitmap to fit A5 width
                        float scaleX = 419f / contentW;
                        int pdfH = (int)(contentH * scaleX);

                        PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo
                            .Builder(419, Math.max(pdfH, 100), 1).create();
                        PdfDocument.Page page = pdf.startPage(pageInfo);
                        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
                        paint.setFilterBitmap(true);
                        android.graphics.Matrix matrix = new android.graphics.Matrix();
                        matrix.setScale(scaleX, scaleX);
                        page.getCanvas().drawBitmap(bmp, matrix, paint);
                        pdf.finishPage(page);

                        // Save PDF to cache
                        File pdfFile = new File(getCacheDir(), "miron_slip.pdf");
                        FileOutputStream fos = new FileOutputStream(pdfFile);
                        pdf.writeTo(fos);
                        fos.flush(); fos.close();
                        pdf.close();
                        bmp.recycle();

                        Uri pdfUri = FileProvider.getUriForFile(
                            MainActivity.this,
                            getPackageName() + ".provider",
                            pdfFile);

                        loading.dismiss();
                        renderer.destroy();
                        showSlipOptions(pdfUri);

                    } catch (Exception e) {
                        loading.dismiss();
                        renderer.destroy();
                        new AlertDialog.Builder(MainActivity.this)
                            .setMessage("স্লিপ তৈরিতে সমস্যা: " + e.getMessage())
                            .setPositiveButton("ঠিক আছে", null).show();
                    }
                }, 1200); // wait 1.2s for full render
            }
        });

        renderer.loadDataWithBaseURL(
            "https://fonts.gstatic.com", html, "text/html", "UTF-8", null);
    }

    // ══════════════════════════════════════════════
    //  Show PDF options dialog
    // ══════════════════════════════════════════════
    private void showSlipOptions(Uri pdfUri) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(24, 24, 24, 24);
        layout.setBackgroundColor(Color.WHITE);
        layout.setGravity(Gravity.CENTER);

        TextView title = new TextView(this);
        title.setText("✅ স্লিপ PDF তৈরি হয়েছে");
        title.setTextSize(16);
        title.setTextColor(Color.parseColor("#0D47A1"));
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, 24);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        layout.addView(title);

        // WhatsApp
        Button waBtn = makeOptionBtn("📲  WhatsApp এ PDF শেয়ার", "#25D366");
        // Share (all apps)
        Button shareBtn = makeOptionBtn("📤  অন্য App এ শেয়ার করুন", "#0D47A1");
        // Open PDF
        Button openBtn = makeOptionBtn("👁  PDF দেখুন", "#546E7A");

        layout.addView(waBtn);
        layout.addView(shareBtn);
        layout.addView(openBtn);

        AlertDialog dialog = new AlertDialog.Builder(this)
            .setView(layout)
            .create();

        waBtn.setOnClickListener(v -> {
            try {
                Intent i = new Intent(Intent.ACTION_SEND);
                i.setType("application/pdf");
                i.setPackage("com.whatsapp");
                i.putExtra(Intent.EXTRA_STREAM, pdfUri);
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(i);
                dialog.dismiss();
            } catch (Exception e) {
                // WhatsApp নেই
                new AlertDialog.Builder(this)
                    .setMessage("WhatsApp পাওয়া যায়নি। অন্য App ব্যবহার করুন।")
                    .setPositiveButton("ঠিক আছে", null).show();
            }
        });

        shareBtn.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("application/pdf");
            i.putExtra(Intent.EXTRA_STREAM, pdfUri);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(i, "স্লিপ শেয়ার করুন"));
            dialog.dismiss();
        });

        openBtn.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(pdfUri, "application/pdf");
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            try { startActivity(i); }
            catch (Exception e) {
                new AlertDialog.Builder(this)
                    .setMessage("PDF viewer app নেই। Google Drive বা Files app ইনস্টল করুন।")
                    .setPositiveButton("ঠিক আছে", null).show();
            }
            dialog.dismiss();
        });

        dialog.show();
        Window w = dialog.getWindow();
        if (w != null) w.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT);
    }

    private Button makeOptionBtn(String text, String color) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(14);
        b.setBackgroundColor(Color.parseColor(color));
        b.setTextColor(Color.WHITE);
        b.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        b.setPadding(24, 20, 24, 20);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        p.setMargins(0, 0, 0, 14);
        b.setLayoutParams(p);
        return b;
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

        // ── Root: full vertical layout ─────────────
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.MATCH_PARENT));

        // ── Top bar: app name + refresh button ─────
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setBackgroundColor(Color.parseColor("#0D47A1"));
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(16, 10, 8, 10);
        LinearLayout.LayoutParams tbParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        topBar.setLayoutParams(tbParams);

        // App title
        TextView appTitle = new TextView(this);
        appTitle.setText("📦 মিরন ইলেকট্রনিক্স");
        appTitle.setTextColor(Color.WHITE);
        appTitle.setTextSize(15);
        appTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        appTitle.setLayoutParams(titleParams);
        topBar.addView(appTitle);

        // 🔄 Refresh button
        Button refreshBtn = new Button(this);
        refreshBtn.setText("🔄");
        refreshBtn.setTextSize(18);
        refreshBtn.setBackgroundColor(Color.TRANSPARENT);
        refreshBtn.setTextColor(Color.WHITE);
        refreshBtn.setPadding(16, 8, 16, 8);
        LinearLayout.LayoutParams rParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        refreshBtn.setLayoutParams(rParams);
        topBar.addView(refreshBtn);

        root.addView(topBar);

        // ── WebView ────────────────────────────────
        webView = new WebView(this);
        webView.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.MATCH_PARENT, 1f));

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
                offlineLayout.setVisibility(View.GONE);
                webView.setVisibility(View.VISIBLE);
            }
            @Override
            public void onReceivedError(WebView v, WebResourceRequest r,
                    android.webkit.WebResourceError e) {
                if (r.isForMainFrame()) {
                    webView.setVisibility(View.GONE);
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

        root.addView(webView);

        // ── Offline layout ─────────────────────────
        offlineLayout = new LinearLayout(this);
        offlineLayout.setOrientation(LinearLayout.VERTICAL);
        offlineLayout.setGravity(Gravity.CENTER);
        offlineLayout.setBackgroundColor(Color.parseColor("#EEF2FF"));
        offlineLayout.setVisibility(View.GONE);
        LinearLayout.LayoutParams olp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.MATCH_PARENT);
        offlineLayout.setLayoutParams(olp);

        TextView ico = new TextView(this);
        ico.setText("📡"); ico.setTextSize(48); ico.setGravity(Gravity.CENTER);

        TextView msg = new TextView(this);
        msg.setText("ইন্টারনেট সংযোগ নেই\nঅনুগ্রহ করে সংযোগ দিন");
        msg.setTextSize(16); msg.setTextColor(Color.parseColor("#546E7A"));
        msg.setGravity(Gravity.CENTER); msg.setPadding(0,16,0,32);

        Button retry = makeOptionBtn("🔄  আবার চেষ্টা করুন", "#0D47A1");
        retry.setOnClickListener(v -> {
            if (isOnline()) {
                offlineLayout.setVisibility(View.GONE);
                webView.setVisibility(View.VISIBLE);
                webView.loadUrl(APP_URL);
            }
        });

        offlineLayout.addView(ico);
        offlineLayout.addView(msg);
        offlineLayout.addView(retry);
        root.addView(offlineLayout);

        setContentView(root);

        // Refresh button action
        refreshBtn.setOnClickListener(v -> {
            if (isOnline()) webView.reload();
            else {
                webView.setVisibility(View.GONE);
                offlineLayout.setVisibility(View.VISIBLE);
            }
        });

        // Load app
        if (isOnline()) webView.loadUrl(APP_URL);
        else {
            webView.setVisibility(View.GONE);
            offlineLayout.setVisibility(View.VISIBLE);
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        if (req == FILE_CHOOSER_REQUEST && fileChooserCallback != null) {
            Uri[] results = null;
            if (res == Activity.RESULT_OK && data != null) {
                String str = data.getDataString();
                if (str != null) results = new Uri[]{Uri.parse(str)};
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
