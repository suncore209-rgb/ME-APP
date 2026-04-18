package com.miron.electronics;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Base64;
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
import android.widget.Toast;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

public class MainActivity extends Activity {

    // ── Change this to your Vercel URL ──────────────────
    private static final String APP_URL = "https://me-nine-pearl.vercel.app/";
    private static final int FILE_CHOOSER_REQUEST = 100;

    private WebView webView;
    private LinearLayout offlineLayout;
    private ValueCallback<Uri[]> fileChooserCallback;

    // ════════════════════════════════════════════════════
    //  JS Bridge — receives slip HTML from web app
    // ════════════════════════════════════════════════════
    public class SlipBridge {

        // Called by V4.2 web app: AndroidSlip.receiveSlipHtml(html)
        @JavascriptInterface
        public void receiveSlipHtml(final String html) {
            runOnUiThread(() -> showSlipDialog(html));
        }

        // Legacy fallback (old calls)
        @JavascriptInterface
        public void receiveSlip(final String html, final String text) {
            runOnUiThread(() -> showSlipDialog(html));
        }

        // Image base64 fallback (from old html2canvas bridge)
        @JavascriptInterface
        public void receiveImageBase64(final String base64) {
            runOnUiThread(() -> {
                if (base64 == null || base64.isEmpty()) return;
                try {
                    byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
                    Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                    if (bmp != null) showImageShareDialog(bmp, null);
                } catch (Exception e) {
                    toast("সমস্যা: " + e.getMessage());
                }
            });
        }

        @JavascriptInterface
        public void showLoadingDialog() { /* no-op */ }
    }

    // ════════════════════════════════════════════════════
    //  Slip Dialog — WebView preview + share options
    // ════════════════════════════════════════════════════
    private void showSlipDialog(String html) {
        // Root layout
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);

        // Header
        TextView hdr = new TextView(this);
        hdr.setText("📄 স্টক সরবরাহ স্লিপ");
        hdr.setTextSize(15);
        hdr.setTypeface(null, android.graphics.Typeface.BOLD);
        hdr.setTextColor(Color.parseColor("#0D47A1"));
        hdr.setGravity(Gravity.CENTER);
        hdr.setBackgroundColor(Color.parseColor("#E3F2FD"));
        hdr.setPadding(16, 16, 16, 16);
        root.addView(hdr);

        // Loading indicator (shown while WebView renders)
        ProgressBar progress = new ProgressBar(this);
        progress.setIndeterminate(true);
        LinearLayout.LayoutParams pb = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(48));
        pb.setMargins(0, 8, 0, 8);
        progress.setLayoutParams(pb);
        root.addView(progress);

        // Slip WebView
        WebView slipView = new WebView(this);
        slipView.setBackgroundColor(Color.WHITE);
        LinearLayout.LayoutParams wvp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        slipView.setLayoutParams(wvp);
        slipView.setVisibility(View.GONE);

        WebSettings ws = slipView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setLoadWithOverviewMode(true);
        ws.setUseWideViewPort(true);
        ws.setBuiltInZoomControls(true);
        ws.setDisplayZoomControls(false);

        root.addView(slipView);

        // Divider
        View div = new View(this);
        div.setBackgroundColor(Color.parseColor("#E0E0E0"));
        div.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 1));
        root.addView(div);

        // Button row
        LinearLayout btns = new LinearLayout(this);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        btns.setPadding(10, 10, 10, 10);
        btns.setBackgroundColor(Color.WHITE);
        btns.setGravity(Gravity.CENTER);

        Button waBtn    = makeBtnH("📲 WhatsApp",     "#25D366");
        Button dlBtn    = makeBtnH("💾 গ্যালারি",     "#1565C0");
        Button shareBtn = makeBtnH("📤 শেয়ার",        "#546E7A");
        Button closeBtn = makeBtnH("✕ বন্ধ",          "#9E9E9E");

        // disable until loaded
        waBtn.setEnabled(false);
        dlBtn.setEnabled(false);
        shareBtn.setEnabled(false);

        setWeight(waBtn, 1.2f); setWeight(dlBtn, 1f);
        setWeight(shareBtn, 1f); setWeight(closeBtn, 0.7f);

        btns.addView(waBtn);
        btns.addView(dlBtn);
        btns.addView(shareBtn);
        btns.addView(closeBtn);
        root.addView(btns);

        // Build dialog
        AlertDialog dialog = new AlertDialog.Builder(this)
            .setView(root).create();

        // Load slip into WebView
        slipView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                // Give fonts time to render
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    progress.setVisibility(View.GONE);
                    slipView.setVisibility(View.VISIBLE);
                    waBtn.setEnabled(true);
                    dlBtn.setEnabled(true);
                    shareBtn.setEnabled(true);
                }, 800);
            }
        });

        // Use fonts.gstatic.com as base so Google Fonts loads
        slipView.loadDataWithBaseURL(
            "https://fonts.gstatic.com", html, "text/html", "UTF-8", null);

        // Button actions — capture WebView as bitmap then share
        waBtn.setOnClickListener(v -> {
            toast("স্লিপ তৈরি হচ্ছে...");
            captureAndShare(slipView, dialog, "com.whatsapp");
        });

        dlBtn.setOnClickListener(v -> {
            toast("সেভ হচ্ছে...");
            captureAndSave(slipView, dialog);
        });

        shareBtn.setOnClickListener(v -> {
            toast("শেয়ার হচ্ছে...");
            captureAndShare(slipView, dialog, null);
        });

        closeBtn.setOnClickListener(v -> dialog.dismiss());

        // Show full-screen dialog
        dialog.show();
        Window w = dialog.getWindow();
        if (w != null) {
            w.setLayout(WindowManager.LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.MATCH_PARENT);
        }
    }

    // ════════════════════════════════════════════════════
    //  Capture WebView → Bitmap → Share/Save
    // ════════════════════════════════════════════════════
    private Bitmap captureWebView(WebView wv) {
        try {
            float d = getResources().getDisplayMetrics().density;
            int h = Math.max((int)(wv.getContentHeight() * d), 200);
            int w = Math.max(wv.getWidth(), 100);
            Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            bmp.eraseColor(Color.WHITE);
            Canvas canvas = new Canvas(bmp);
            wv.draw(canvas);
            return bmp;
        } catch (Exception e) {
            return null;
        }
    }

    private Uri saveBitmapToCache(Bitmap bmp) {
        try {
            String fn = "miron_slip_" + System.currentTimeMillis() + ".jpg";
            File f = new File(getCacheDir(), fn);
            FileOutputStream fos = new FileOutputStream(f);
            bmp.compress(Bitmap.CompressFormat.JPEG, 95, fos);
            fos.flush(); fos.close();
            return FileProvider.getUriForFile(
                this, getPackageName() + ".provider", f);
        } catch (Exception e) { return null; }
    }

    private void captureAndShare(WebView slipView, AlertDialog dialog, String pkg) {
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Bitmap bmp = captureWebView(slipView);
            if (bmp == null) { toast("স্লিপ ক্যাপচার ব্যর্থ"); return; }
            Uri uri = saveBitmapToCache(bmp);
            if (uri == null) { toast("ফাইল সেভ ব্যর্থ"); return; }
            dialog.dismiss();
            if (pkg != null) {
                // Direct WhatsApp share
                try {
                    Intent i = new Intent(Intent.ACTION_SEND);
                    i.setType("image/jpeg");
                    i.setPackage(pkg);
                    i.putExtra(Intent.EXTRA_STREAM, uri);
                    i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(i);
                } catch (Exception e) {
                    // WhatsApp not installed — open chooser
                    Intent i = new Intent(Intent.ACTION_SEND);
                    i.setType("image/jpeg");
                    i.putExtra(Intent.EXTRA_STREAM, uri);
                    i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(Intent.createChooser(i, "শেয়ার করুন"));
                }
            } else {
                // General share chooser
                Intent i = new Intent(Intent.ACTION_SEND);
                i.setType("image/jpeg");
                i.putExtra(Intent.EXTRA_STREAM, uri);
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(Intent.createChooser(i, "স্লিপ শেয়ার করুন"));
            }
        }, 300);
    }

    private void captureAndSave(WebView slipView, AlertDialog dialog) {
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Bitmap bmp = captureWebView(slipView);
            if (bmp == null) { toast("ক্যাপচার ব্যর্থ"); return; }
            try {
                String fn = "miron_slip_" + System.currentTimeMillis() + ".jpg";
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    ContentValues cv = new ContentValues();
                    cv.put(MediaStore.Images.Media.DISPLAY_NAME, fn);
                    cv.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
                    cv.put(MediaStore.Images.Media.IS_PENDING, 1);
                    Uri dest = getContentResolver().insert(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv);
                    if (dest != null) {
                        try (OutputStream out = getContentResolver().openOutputStream(dest)) {
                            bmp.compress(Bitmap.CompressFormat.JPEG, 95, out);
                        }
                        cv.clear();
                        cv.put(MediaStore.Images.Media.IS_PENDING, 0);
                        getContentResolver().update(dest, cv, null, null);
                    }
                } else {
                    File pics = android.os.Environment.getExternalStoragePublicDirectory(
                        android.os.Environment.DIRECTORY_PICTURES);
                    if (!pics.exists()) pics.mkdirs();
                    File out = new File(pics, fn);
                    FileOutputStream fos = new FileOutputStream(out);
                    bmp.compress(Bitmap.CompressFormat.JPEG, 95, fos);
                    fos.flush(); fos.close();
                    sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE,
                        Uri.fromFile(out)));
                }
                dialog.dismiss();
                toast("✅ গ্যালারিতে সেভ হয়েছে!");
            } catch (Exception e) {
                toast("সেভ ব্যর্থ: " + e.getMessage());
            }
        }, 300);
    }

    // Show image directly (legacy path)
    private void showImageShareDialog(Bitmap bmp, AlertDialog prev) {
        if (prev != null) prev.dismiss();
        Uri uri = saveBitmapToCache(bmp);
        if (uri == null) { toast("ফাইল সেভ ব্যর্থ"); return; }
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("image/jpeg");
        i.putExtra(Intent.EXTRA_STREAM, uri);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(i, "স্লিপ শেয়ার করুন"));
    }

    // ════════════════════════════════════════════════════
    //  onCreate
    // ════════════════════════════════════════════════════
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

        webView = new WebView(this);
        webView.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

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
        s.setMediaPlaybackRequiresUserGesture(false);

        // Register slip bridge
        webView.addJavascriptInterface(new SlipBridge(), "AndroidSlip");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                v.loadUrl(r.getUrl().toString());
                return true;
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
                    .setPositiveButton("হ্যাঁ", (d, w) -> r.confirm())
                    .setNegativeButton("না",    (d, w) -> r.cancel())
                    .setOnCancelListener(d -> r.cancel()).create().show();
                return true;
            }
            @Override
            public boolean onJsAlert(WebView v, String u, String m, JsResult r) {
                new AlertDialog.Builder(MainActivity.this).setMessage(m)
                    .setPositiveButton("ঠিক আছে", (d, w) -> r.confirm())
                    .setOnCancelListener(d -> r.confirm()).create().show();
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

        // ── Offline screen ───────────────────────────────
        offlineLayout = new LinearLayout(this);
        offlineLayout.setOrientation(LinearLayout.VERTICAL);
        offlineLayout.setGravity(Gravity.CENTER);
        offlineLayout.setBackgroundColor(Color.parseColor("#EEF2FF"));
        offlineLayout.setVisibility(View.GONE);
        offlineLayout.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.MATCH_PARENT));

        TextView ico = new TextView(this);
        ico.setText("📡"); ico.setTextSize(52); ico.setGravity(Gravity.CENTER);

        TextView msg = new TextView(this);
        msg.setText("ইন্টারনেট সংযোগ নেই\nঅনুগ্রহ করে সংযোগ দিন");
        msg.setTextSize(16); msg.setTextColor(Color.parseColor("#546E7A"));
        msg.setGravity(Gravity.CENTER); msg.setPadding(0, 16, 0, 32);

        Button retry = makeBtnV("🔄  আবার চেষ্টা করুন", "#0D47A1");
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

        if (isOnline()) webView.loadUrl(APP_URL);
        else {
            webView.setVisibility(View.GONE);
            offlineLayout.setVisibility(View.VISIBLE);
        }
    }

    // ════════════════════════════════════════════════════
    //  Helpers
    // ════════════════════════════════════════════════════
    private Button makeBtnH(String text, String color) {
        Button b = new Button(this);
        b.setText(text); b.setTextSize(11);
        b.setBackgroundColor(Color.parseColor(color));
        b.setTextColor(Color.WHITE);
        b.setPadding(6, 14, 6, 14);
        return b;
    }

    private Button makeBtnV(String text, String color) {
        Button b = new Button(this);
        b.setText(text); b.setTextSize(14);
        b.setBackgroundColor(Color.parseColor(color));
        b.setTextColor(Color.WHITE);
        b.setGravity(Gravity.CENTER);
        b.setPadding(28, 20, 28, 20);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        p.setMargins(0, 0, 0, 10);
        b.setLayoutParams(p);
        return b;
    }

    private void setWeight(Button b, float w) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, w);
        p.setMargins(4, 0, 4, 0);
        b.setLayoutParams(p);
    }

    private int dpToPx(int dp) {
        return (int)(dp * getResources().getDisplayMetrics().density);
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    private boolean isOnline() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        NetworkInfo ni = cm.getActiveNetworkInfo();
        return ni != null && ni.isConnected();
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
