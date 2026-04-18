package com.miron.electronics;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
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
import android.os.Environment;
import android.provider.MediaStore;
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
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class MainActivity extends Activity {

    private static final String APP_URL = "https://me-nine-pearl.vercel.app/";
    private static final int FILE_CHOOSER_REQUEST = 100;

    private WebView webView;
    private LinearLayout offlineLayout;
    private ValueCallback<Uri[]> fileChooserCallback;

    // JS Bridge
    public class SlipBridge {
        @JavascriptInterface
        public void receiveSlip(final String html, final String slipText) {
            runOnUiThread(() -> renderSlipToPdf(html));
        }
    }

    /**
     * KEY FIX: Inject JS that overrides window.print().
     * WebView does NOT support window.print() — the screen goes white.
     * We intercept it, grab the slip HTML, and send to our PDF renderer.
     */
    private void injectPrintOverride(WebView v) {
        String js = "(function() {\n" +
            "  window.print = function() {\n" +
            "    try {\n" +
            "      var selectors = ['#slip','.slip','[id*=\"slip\"]','[class*=\"slip\"]',\n" +
            "                       '#printable','.printable','.invoice','#invoice',\n" +
            "                       '[id*=\"challan\"]','[class*=\"challan\"]',\n" +
            "                       '[id*=\"receipt\"]','[class*=\"receipt\"]'];\n" +
            "      var el = null;\n" +
            "      for (var s = 0; s < selectors.length; s++) {\n" +
            "        el = document.querySelector(selectors[s]);\n" +
            "        if (el) break;\n" +
            "      }\n" +
            "      var styles = '';\n" +
            "      try {\n" +
            "        var sheets = document.styleSheets;\n" +
            "        for (var i = 0; i < sheets.length; i++) {\n" +
            "          var rules;\n" +
            "          try { rules = sheets[i].cssRules || sheets[i].rules; } catch(e) { continue; }\n" +
            "          if (rules) { for (var j = 0; j < rules.length; j++) { styles += rules[j].cssText + '\\n'; } }\n" +
            "        }\n" +
            "      } catch(e) {}\n" +
            "      var body = el ? el.outerHTML : document.body.innerHTML;\n" +
            "      var fullHtml = '<!DOCTYPE html><html><head><meta charset=\"utf-8\">'\n" +
            "        + '<meta name=\"viewport\" content=\"width=device-width\">'\n" +
            "        + '<style>body{margin:0;padding:12px;background:#fff;font-family:sans-serif;}' + styles + '</style>'\n" +
            "        + '</head><body>' + body + '</body></html>';\n" +
            "      window.AndroidSlip.receiveSlip(fullHtml, '');\n" +
            "    } catch(err) {\n" +
            "      window.AndroidSlip.receiveSlip(document.documentElement.outerHTML, '');\n" +
            "    }\n" +
            "  };\n" +
            "})();";
        v.evaluateJavascript(js, null);
    }

    private void renderSlipToPdf(String html) {
        AlertDialog loading = new AlertDialog.Builder(this)
            .setMessage("স্লিপ তৈরি হচ্ছে...")
            .setCancelable(false).create();
        loading.show();

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
                view.postDelayed(() -> {
                    try {
                        float density = getResources().getDisplayMetrics().density;
                        int contentH = (int)(view.getContentHeight() * density);
                        if (contentH <= 0) contentH = 800;
                        view.layout(0, 0, w, contentH);

                        Bitmap bmp = Bitmap.createBitmap(w, contentH, Bitmap.Config.ARGB_8888);
                        bmp.eraseColor(Color.WHITE);
                        view.draw(new Canvas(bmp));

                        PdfDocument pdf = new PdfDocument();
                        float scaleX = 419f / w;
                        int pdfH = (int)(contentH * scaleX);
                        PdfDocument.PageInfo pi = new PdfDocument.PageInfo
                            .Builder(419, Math.max(pdfH, 100), 1).create();
                        PdfDocument.Page page = pdf.startPage(pi);
                        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
                        paint.setFilterBitmap(true);
                        android.graphics.Matrix mx = new android.graphics.Matrix();
                        mx.setScale(scaleX, scaleX);
                        page.getCanvas().drawBitmap(bmp, mx, paint);
                        pdf.finishPage(page);

                        String fileName = "miron_slip_" + System.currentTimeMillis() + ".pdf";
                        File pdfFile = new File(getCacheDir(), fileName);
                        FileOutputStream fos = new FileOutputStream(pdfFile);
                        pdf.writeTo(fos); fos.flush(); fos.close();
                        pdf.close(); bmp.recycle();

                        Uri pdfUri = FileProvider.getUriForFile(
                            MainActivity.this, getPackageName() + ".provider", pdfFile);

                        loading.dismiss();
                        renderer.destroy();
                        showSlipOptions(pdfUri, pdfFile, fileName);

                    } catch (Exception e) {
                        loading.dismiss(); renderer.destroy();
                        new AlertDialog.Builder(MainActivity.this)
                            .setMessage("স্লিপ তৈরিতে সমস্যা: " + e.getMessage())
                            .setPositiveButton("ঠিক আছে", null).show();
                    }
                }, 1400);
            }
        });

        renderer.loadDataWithBaseURL(APP_URL, html, "text/html", "UTF-8", null);
    }

    private void showSlipOptions(Uri pdfUri, File pdfFile, String fileName) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(28, 28, 28, 20);
        layout.setBackgroundColor(Color.WHITE);

        TextView title = new TextView(this);
        title.setText("✅ স্লিপ PDF তৈরি হয়েছে");
        title.setTextSize(17);
        title.setTextColor(Color.parseColor("#0D47A1"));
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, 22);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        layout.addView(title);

        Button waBtn    = makeBtn("📲  WhatsApp এ শেয়ার",         "#25D366");
        Button waBizBtn = makeBtn("💼  WhatsApp Business শেয়ার",  "#075E54");
        Button shareBtn = makeBtn("📤  অন্য App এ শেয়ার",          "#0D47A1");
        Button saveBtn  = makeBtn("💾  ডাউনলোড ফোল্ডারে সেভ",     "#37474F");
        Button openBtn  = makeBtn("👁   PDF দেখুন",                 "#546E7A");

        layout.addView(waBtn);
        layout.addView(waBizBtn);
        layout.addView(shareBtn);
        layout.addView(saveBtn);
        layout.addView(openBtn);

        AlertDialog dialog = new AlertDialog.Builder(this).setView(layout).create();

        waBtn.setOnClickListener(v    -> { shareViaApp(pdfUri, "com.whatsapp");     dialog.dismiss(); });
        waBizBtn.setOnClickListener(v -> { shareViaApp(pdfUri, "com.whatsapp.w4b"); dialog.dismiss(); });
        shareBtn.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("application/pdf");
            i.putExtra(Intent.EXTRA_STREAM, pdfUri);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(i, "স্লিপ শেয়ার করুন"));
            dialog.dismiss();
        });
        saveBtn.setOnClickListener(v  -> { savePdfToDownloads(pdfFile, fileName); dialog.dismiss(); });
        openBtn.setOnClickListener(v  -> {
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(pdfUri, "application/pdf");
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            try { startActivity(i); }
            catch (Exception e) {
                new AlertDialog.Builder(this)
                    .setMessage("PDF viewer নেই। Google Drive বা Files app ইনস্টল করুন।")
                    .setPositiveButton("ঠিক আছে", null).show();
            }
            dialog.dismiss();
        });

        dialog.show();
        Window w2 = dialog.getWindow();
        if (w2 != null) w2.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT);
    }

    private void shareViaApp(Uri pdfUri, String pkg) {
        try {
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("application/pdf"); i.setPackage(pkg);
            i.putExtra(Intent.EXTRA_STREAM, pdfUri);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(i);
        } catch (Exception e) {
            // WhatsApp not installed — fall back to generic share chooser
            Intent fallback = new Intent(Intent.ACTION_SEND);
            fallback.setType("application/pdf");
            fallback.putExtra(Intent.EXTRA_STREAM, pdfUri);
            fallback.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(fallback, "শেয়ার করুন"));
        }
    }

    private void savePdfToDownloads(File srcFile, String fileName) {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
                values.put(MediaStore.Downloads.MIME_TYPE, "application/pdf");
                values.put(MediaStore.Downloads.IS_PENDING, 1);
                Uri dest = getContentResolver().insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (dest == null) throw new Exception("Storage error");
                try (InputStream in  = new FileInputStream(srcFile);
                     OutputStream out = getContentResolver().openOutputStream(dest)) {
                    byte[] buf = new byte[4096]; int n;
                    while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                }
                values.clear();
                values.put(MediaStore.Downloads.IS_PENDING, 0);
                getContentResolver().update(dest, values, null, null);
            } else {
                File downloads = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS);
                if (!downloads.exists()) downloads.mkdirs();
                File out = new File(downloads, fileName);
                try (InputStream in  = new FileInputStream(srcFile);
                     OutputStream os = new FileOutputStream(out)) {
                    byte[] buf = new byte[4096]; int n;
                    while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
                }
            }
            new AlertDialog.Builder(this)
                .setMessage("✅ '" + fileName + "'\nডাউনলোড ফোল্ডারে সেভ হয়েছে!")
                .setPositiveButton("ঠিক আছে", null).show();
        } catch (Exception e) {
            new AlertDialog.Builder(this)
                .setMessage("সেভ করতে সমস্যা: " + e.getMessage())
                .setPositiveButton("ঠিক আছে", null).show();
        }
    }

    private Button makeBtn(String text, String color) {
        Button b = new Button(this);
        b.setText(text); b.setTextSize(14);
        b.setBackgroundColor(Color.parseColor(color));
        b.setTextColor(Color.WHITE);
        b.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        b.setPadding(28, 20, 28, 20);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        p.setMargins(0, 0, 0, 12);
        b.setLayoutParams(p);
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
                // Inject window.print() override on every page load
                injectPrintOverride(v);
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

        Button retry = makeBtn("🔄  আবার চেষ্টা করুন", "#0D47A1");
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
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
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
