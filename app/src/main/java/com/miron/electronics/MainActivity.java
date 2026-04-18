package com.miron.electronics;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

public class MainActivity extends Activity {

    private static final String APP_URL = "https://me-nine-pearl.vercel.app/";
    private static final int FILE_CHOOSER_REQUEST = 100;

    private WebView webView;
    private LinearLayout offlineLayout;
    private ValueCallback<Uri[]> fileChooserCallback;
    private AlertDialog loadingDialog;

    // ═══════════════════════════════════════════════════
    //  JS Bridge
    // ═══════════════════════════════════════════════════
    public class SlipBridge {

        // Web app calls this directly if window.open() is blocked
        // html = full slip HTML string
        @JavascriptInterface
        public void receiveSlip(final String html, final String ignored) {
            runOnUiThread(() -> {
                showLoading("স্লিপ তৈরি হচ্ছে...");
                renderHtmlToBitmap(html);
            });
        }

        // Called after html2canvas captures any page/popup
        @JavascriptInterface
        public void receiveImageBase64(final String base64) {
            runOnUiThread(() -> {
                dismissLoading();
                if (base64 == null || base64.isEmpty()) {
                    toast("ছবি তৈরি ব্যর্থ হয়েছে");
                    return;
                }
                try {
                    byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
                    Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                    if (bmp != null) showImagePreview(bmp);
                    else toast("ছবি তৈরি ব্যর্থ হয়েছে");
                } catch (Exception e) {
                    toast("সমস্যা: " + e.getMessage());
                }
            });
        }

        @JavascriptInterface
        public void showLoadingDialog() {
            runOnUiThread(() -> showLoading("তৈরি হচ্ছে..."));
        }
    }

    // ═══════════════════════════════════════════════════
    //  Render raw HTML string → Bitmap (receiveSlip fallback path)
    // ═══════════════════════════════════════════════════
    private void renderHtmlToBitmap(String html) {
        WebView renderer = new WebView(this);
        WebSettings rs = renderer.getSettings();
        rs.setJavaScriptEnabled(true);
        rs.setDomStorageEnabled(true);
        rs.setLoadWithOverviewMode(true);
        rs.setUseWideViewPort(true);
        renderer.addJavascriptInterface(new SlipBridge(), "AndroidSlip");

        renderer.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView v, String url) {
                String js =
                    "(function() {" +
                    "  var btns = document.querySelector('.btns');" +
                    "  if (btns) btns.style.display = 'none';" +
                    "  var sc = document.createElement('script');" +
                    "  sc.src = 'https://cdnjs.cloudflare.com/ajax/libs/html2canvas/1.4.1/html2canvas.min.js';" +
                    "  sc.onload = function() {" +
                    "    html2canvas(document.body, {" +
                    "      scale: 2, useCORS: true, allowTaint: true," +
                    "      backgroundColor: '#ffffff', logging: false" +
                    "    }).then(function(canvas) {" +
                    "      window.AndroidSlip.receiveImageBase64(" +
                    "        canvas.toDataURL('image/jpeg', 0.93).split(',')[1]);" +
                    "    }).catch(function() {" +
                    "      window.AndroidSlip.receiveImageBase64('');" +
                    "    });" +
                    "  };" +
                    "  sc.onerror = function() { window.AndroidSlip.receiveImageBase64(''); };" +
                    "  document.head.appendChild(sc);" +
                    "})();";
                v.postDelayed(() -> v.evaluateJavascript(js, null), 600);
            }
        });

        // Load slip HTML with the app origin so fonts/resources load
        renderer.loadDataWithBaseURL(
            "https://me-nine-pearl.vercel.app/",
            html, "text/html", "UTF-8", null);
    }

    // ═══════════════════════════════════════════════════
    //  JS injection into every main page load
    //  Intercepts window.print() (report page)
    // ═══════════════════════════════════════════════════
    private void injectBridgeOverrides(WebView v) {
        String js =
        "(function() {" +
        "  if (window._meBridgeReady) return;" +
        "  window._meBridgeReady = true;" +

        // ── Block window.open so web app falls into receiveSlip branch ──
        // The web app does:
        //   var win = window.open(...);
        //   if (win) { win.document.write(html); }
        //   else if (window.AndroidSlip) { window.AndroidSlip.receiveSlip(html, ''); }
        // By returning null, receiveSlip() gets called with the full HTML.
        "  window.open = function() { return null; };" +

        // ── Capture current page for Report's window.print() button ──
        "  function captureCurrentPage() {" +
        "    if (typeof window.AndroidSlip === 'undefined') return;" +
        "    window.AndroidSlip.showLoadingDialog();" +
        "    function doCapture() {" +
        "      html2canvas(document.body, {" +
        "        scale: 2, useCORS: true, allowTaint: true," +
        "        backgroundColor: '#ffffff', logging: false" +
        "      }).then(function(canvas) {" +
        "        window.AndroidSlip.receiveImageBase64(" +
        "          canvas.toDataURL('image/jpeg', 0.93).split(',')[1]);" +
        "      }).catch(function() {" +
        "        window.AndroidSlip.receiveImageBase64('');" +
        "      });" +
        "    }" +
        "    if (window.html2canvas) {" +
        "      doCapture();" +
        "    } else {" +
        "      var sc = document.createElement('script');" +
        "      sc.src = 'https://cdnjs.cloudflare.com/ajax/libs/html2canvas/1.4.1/html2canvas.min.js';" +
        "      sc.onload = doCapture;" +
        "      sc.onerror = function() { window.AndroidSlip.receiveImageBase64(''); };" +
        "      document.head.appendChild(sc);" +
        "    }" +
        "  }" +

        // ── Override window.print → capture (Report page PDF button) ──
        "  window.print = function() { captureCurrentPage(); };" +

        "})();";

        v.evaluateJavascript(js, null);
    }

    // ═══════════════════════════════════════════════════
    //  Capture popup WebView (slip popup path)
    // ═══════════════════════════════════════════════════
    private void capturePopupWebView(WebView popup) {
        String js =
        "(function() {" +
        // Hide the sticky button bar so it doesn't appear in image
        "  var btns = document.querySelector('.btns');" +
        "  if (btns) btns.style.display = 'none';" +
        "  function doCapture() {" +
        "    html2canvas(document.body, {" +
        "      scale: 2, useCORS: true, allowTaint: true," +
        "      backgroundColor: '#ffffff', logging: false" +
        "    }).then(function(canvas) {" +
        "      window.AndroidSlip.receiveImageBase64(" +
        "        canvas.toDataURL('image/jpeg', 0.93).split(',')[1]);" +
        "    }).catch(function() {" +
        "      window.AndroidSlip.receiveImageBase64('');" +
        "    });" +
        "  }" +
        "  if (window.html2canvas) {" +
        "    doCapture();" +
        "  } else {" +
        "    var sc = document.createElement('script');" +
        "    sc.src = 'https://cdnjs.cloudflare.com/ajax/libs/html2canvas/1.4.1/html2canvas.min.js';" +
        "    sc.onload = doCapture;" +
        "    sc.onerror = function() { window.AndroidSlip.receiveImageBase64(''); };" +
        "    document.head.appendChild(sc);" +
        "  }" +
        "})();";

        showLoading("স্লিপ ক্যাপচার হচ্ছে...");
        popup.postDelayed(() -> popup.evaluateJavascript(js, null), 800);
    }

    // ═══════════════════════════════════════════════════
    //  Image Preview + Share Dialog
    // ═══════════════════════════════════════════════════
    private void showImagePreview(Bitmap bmp) {
        String fn = "miron_slip_" + System.currentTimeMillis() + ".jpg";
        File imgFile = new File(getCacheDir(), fn);
        try {
            FileOutputStream fos = new FileOutputStream(imgFile);
            bmp.compress(Bitmap.CompressFormat.JPEG, 93, fos);
            fos.flush(); fos.close();
        } catch (Exception e) {
            toast("ছবি সেভ ব্যর্থ: " + e.getMessage()); return;
        }
        Uri imgUri = FileProvider.getUriForFile(
            this, getPackageName() + ".provider", imgFile);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);

        TextView header = new TextView(this);
        header.setText("✅ স্লিপ / রিপোর্ট তৈরি হয়েছে");
        header.setTextSize(16);
        header.setTypeface(null, android.graphics.Typeface.BOLD);
        header.setTextColor(Color.parseColor("#0D47A1"));
        header.setGravity(Gravity.CENTER);
        header.setPadding(20, 20, 20, 12);
        root.addView(header);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.parseColor("#F5F5F5"));
        scroll.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(300)));
        ImageView img = new ImageView(this);
        img.setImageBitmap(bmp);
        img.setAdjustViewBounds(true);
        img.setScaleType(ImageView.ScaleType.FIT_CENTER);
        img.setPadding(8, 8, 8, 8);
        scroll.addView(img);
        root.addView(scroll);

        View divider = new View(this);
        divider.setBackgroundColor(Color.parseColor("#E0E0E0"));
        divider.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 1));
        root.addView(divider);

        LinearLayout btns = new LinearLayout(this);
        btns.setOrientation(LinearLayout.VERTICAL);
        btns.setPadding(20, 16, 20, 20);
        btns.setBackgroundColor(Color.WHITE);

        Button waBtn    = makeBtn("📲  WhatsApp এ পাঠান",       "#25D366");
        Button waBizBtn = makeBtn("💼  WA Business এ পাঠান",    "#075E54");
        Button galBtn   = makeBtn("🖼   গ্যালারিতে সেভ করুন",    "#1565C0");
        Button shareBtn = makeBtn("📤  অন্য App এ শেয়ার করুন",  "#37474F");
        Button closeBtn = makeBtn("✖   বন্ধ করুন",              "#9E9E9E");

        btns.addView(waBtn); btns.addView(waBizBtn);
        btns.addView(galBtn); btns.addView(shareBtn); btns.addView(closeBtn);
        root.addView(btns);

        AlertDialog dialog = new AlertDialog.Builder(this).setView(root).create();

        final Uri finalUri = imgUri;
        final Bitmap finalBmp = bmp;

        waBtn.setOnClickListener(v    -> { shareImageViaApp(finalUri, "com.whatsapp");     dialog.dismiss(); });
        waBizBtn.setOnClickListener(v -> { shareImageViaApp(finalUri, "com.whatsapp.w4b"); dialog.dismiss(); });
        galBtn.setOnClickListener(v   -> { saveImageToGallery(finalBmp, fn);               dialog.dismiss(); });
        shareBtn.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("image/jpeg");
            i.putExtra(Intent.EXTRA_STREAM, finalUri);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(i, "শেয়ার করুন"));
            dialog.dismiss();
        });
        closeBtn.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
        Window w = dialog.getWindow();
        if (w != null)
            w.setLayout(WindowManager.LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.WRAP_CONTENT);
    }

    // ═══════════════════════════════════════════════════
    //  Helpers
    // ═══════════════════════════════════════════════════
    private void shareImageViaApp(Uri uri, String pkg) {
        try {
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("image/jpeg"); i.setPackage(pkg);
            i.putExtra(Intent.EXTRA_STREAM, uri);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(i);
        } catch (Exception e) {
            Intent fb = new Intent(Intent.ACTION_SEND);
            fb.setType("image/jpeg");
            fb.putExtra(Intent.EXTRA_STREAM, uri);
            fb.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(fb, "শেয়ার করুন"));
        }
    }

    private void saveImageToGallery(Bitmap bmp, String fileName) {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                ContentValues cv = new ContentValues();
                cv.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
                cv.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
                cv.put(MediaStore.Images.Media.IS_PENDING, 1);
                Uri dest = getContentResolver().insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv);
                if (dest == null) throw new Exception("Gallery insert failed");
                try (OutputStream out = getContentResolver().openOutputStream(dest)) {
                    bmp.compress(Bitmap.CompressFormat.JPEG, 93, out);
                }
                cv.clear();
                cv.put(MediaStore.Images.Media.IS_PENDING, 0);
                getContentResolver().update(dest, cv, null, null);
            } else {
                File pics = android.os.Environment.getExternalStoragePublicDirectory(
                    android.os.Environment.DIRECTORY_PICTURES);
                if (!pics.exists()) pics.mkdirs();
                File out = new File(pics, fileName);
                FileOutputStream fos = new FileOutputStream(out);
                bmp.compress(Bitmap.CompressFormat.JPEG, 93, fos);
                fos.flush(); fos.close();
                sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE,
                    Uri.fromFile(out)));
            }
            toast("✅ গ্যালারিতে সেভ হয়েছে!");
        } catch (Exception e) {
            toast("সেভ ব্যর্থ: " + e.getMessage());
        }
    }

    private void showLoading(String msg) {
        if (loadingDialog != null && loadingDialog.isShowing()) return;
        loadingDialog = new AlertDialog.Builder(this)
            .setMessage(msg).setCancelable(false).create();
        loadingDialog.show();
    }

    private void dismissLoading() {
        if (loadingDialog != null && loadingDialog.isShowing())
            loadingDialog.dismiss();
        loadingDialog = null;
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }

    private int dpToPx(int dp) {
        return (int)(dp * getResources().getDisplayMetrics().density);
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
        p.setMargins(0, 0, 0, 10);
        b.setLayoutParams(p);
        return b;
    }

    // ═══════════════════════════════════════════════════
    //  onCreate
    // ═══════════════════════════════════════════════════
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
        s.setSupportMultipleWindows(true);
        s.setJavaScriptCanOpenWindowsAutomatically(true);

        webView.addJavascriptInterface(new SlipBridge(), "AndroidSlip");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                String url = r.getUrl().toString();
                if (url.startsWith("blob:")) {
                    showLoading("তৈরি হচ্ছে...");
                    String js = "javascript:(function(){" +
                        "fetch('" + url + "')" +
                        ".then(function(r){return r.blob();})" +
                        ".then(function(b){" +
                        "  var rd=new FileReader();" +
                        "  rd.onloadend=function(){" +
                        "    window.AndroidSlip.receiveImageBase64(rd.result.split(',')[1]);" +
                        "  };" +
                        "  rd.readAsDataURL(b);" +
                        "}).catch(function(){});})();";
                    v.loadUrl(js);
                    return true;
                }
                v.loadUrl(url);
                return true;
            }

            @Override
            public void onPageFinished(WebView v, String url) {
                super.onPageFinished(v, url);
                offlineLayout.setVisibility(View.GONE);
                webView.setVisibility(View.VISIBLE);
                injectBridgeOverrides(v);
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
            public boolean onJsAlert(WebView v, String u, String m, JsResult r) {
                new AlertDialog.Builder(MainActivity.this).setMessage(m)
                    .setPositiveButton("ঠিক আছে", (d, w) -> r.confirm())
                    .setOnCancelListener(d -> r.confirm()).create().show();
                return true;
            }

            @Override
            public boolean onJsConfirm(WebView v, String u, String m, JsResult r) {
                new AlertDialog.Builder(MainActivity.this).setMessage(m)
                    .setPositiveButton("হ্যাঁ", (d, w) -> r.confirm())
                    .setNegativeButton("না",    (d, w) -> r.cancel())
                    .setOnCancelListener(d -> r.cancel()).create().show();
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

            // ── Intercept slip popup window ──
            // Web app: window.open('','_blank','width=480,height=740,...')
            // then writes slip HTML and the user clicks "PDF ডাউনলোড / প্রিন্ট"
            // We capture it automatically with html2canvas.
            @Override
            public boolean onCreateWindow(WebView view, boolean isDialog,
                    boolean isUserGesture, android.os.Message resultMsg) {

                WebView popup = new WebView(MainActivity.this);
                WebSettings ps = popup.getSettings();
                ps.setJavaScriptEnabled(true);
                ps.setDomStorageEnabled(true);
                ps.setLoadWithOverviewMode(true);
                ps.setUseWideViewPort(true);
                popup.addJavascriptInterface(new SlipBridge(), "AndroidSlip");

                // Only inject print override — do NOT auto-capture.
                // User sees the slip popup, taps "PDF ডাউনলোড / প্রিন্ট"
                // which calls window.print() → our override captures it.
                popup.setWebViewClient(new WebViewClient() {
                    @Override
                    public void onPageFinished(WebView pv, String url) {
                        String printOverride =
                            "(function(){" +
                            "  window.print = function() {" +
                            "    window.AndroidSlip.showLoadingDialog();" +
                            "    var btns=document.querySelector('.btns');" +
                            "    if(btns)btns.style.display='none';" +
                            "    var sc=document.createElement('script');" +
                            "    sc.src='https://cdnjs.cloudflare.com/ajax/libs/html2canvas/1.4.1/html2canvas.min.js';" +
                            "    sc.onload=function(){" +
                            "      html2canvas(document.body,{scale:2,useCORS:true,allowTaint:true,backgroundColor:'#ffffff',logging:false})" +
                            "      .then(function(c){" +
                            "        window.AndroidSlip.receiveImageBase64(c.toDataURL('image/jpeg',0.93).split(',')[1]);" +
                            "      }).catch(function(){window.AndroidSlip.receiveImageBase64('');});" +
                            "    };" +
                            "    if(window.html2canvas){sc.onload();}else{document.head.appendChild(sc);}" +
                            "  };" +
                            "})();";
                        pv.postDelayed(() -> pv.evaluateJavascript(printOverride, null), 400);
                    }
                });

                WebView.WebViewTransport t = (WebView.WebViewTransport) resultMsg.obj;
                t.setWebView(popup);
                resultMsg.sendToTarget();
                return true;
            }
        });

        root.addView(webView);

        // ── Offline screen ──
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
