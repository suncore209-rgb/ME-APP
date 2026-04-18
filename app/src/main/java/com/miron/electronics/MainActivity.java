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
import android.webkit.DownloadListener;
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

import java.io.ByteArrayOutputStream;
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
    private AlertDialog loadingDialog;

    // ═══════════════════════════════════════════════════
    //  JS Bridge
    // ═══════════════════════════════════════════════════
    public class SlipBridge {

        // Image (JPEG base64) received from html2canvas
        @JavascriptInterface
        public void receiveImageBase64(final String base64) {
            runOnUiThread(() -> {
                dismissLoading();
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

        // PDF base64 received (for report downloads)
        @JavascriptInterface
        public void receivePdfBase64(final String base64) {
            runOnUiThread(() -> {
                dismissLoading();
                try {
                    byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
                    String fn = "miron_report_" + System.currentTimeMillis() + ".pdf";
                    File f = new File(getCacheDir(), fn);
                    FileOutputStream fos = new FileOutputStream(f);
                    fos.write(bytes); fos.flush(); fos.close();
                    Uri uri = FileProvider.getUriForFile(
                        MainActivity.this, getPackageName() + ".provider", f);
                    showPdfOptions(uri, f, fn);
                } catch (Exception e) {
                    toast("PDF সমস্যা: " + e.getMessage());
                }
            });
        }

        // Show loading spinner (called from JS before html2canvas runs)
        @JavascriptInterface
        public void showLoadingDialog() {
            runOnUiThread(() -> showLoading("স্লিপ/রিপোর্ট তৈরি হচ্ছে..."));
        }
    }

    // ═══════════════════════════════════════════════════
    //  JS Injection — runs on every page load
    //  Covers: window.print(), window.open(), <a download>, blob URLs
    // ═══════════════════════════════════════════════════
    private void injectBridgeOverrides(WebView v) {
        String js =
        "(function() {" +
        "  window._androidBridgeReady = true;" +

        // ── Shared capture function using html2canvas ──
        "  function captureAndSend() {" +
        "    window.AndroidSlip.showLoadingDialog();" +
        "    var selectors = ['#slip','.slip','#printArea','.printArea'," +
        "      '#printable','.printable','.invoice','#invoice'," +
        "      '[id*=\"slip\"]','[class*=\"slip\"]'," +
        "      '[id*=\"report\"]','[class*=\"report\"]'," +
        "      '[id*=\"challan\"]','[id*=\"receipt\"]'," +
        "      'table','main','#main','#content','.content'," +
        "      'article','.container','.wrapper'];" +
        "    var el = null;" +
        "    for(var s=0;s<selectors.length;s++){var f=document.querySelector(selectors[s]);if(f&&f.offsetHeight>50){el=f;break;}}" +
        "    if (!el) el = document.body;" +
        "    function doCapture() {" +
        "      html2canvas(el, {" +
        "        scale: 2," +
        "        useCORS: true," +
        "        allowTaint: true," +
        "        backgroundColor: '#ffffff'," +
        "        logging: false" +
        "      }).then(function(canvas) {" +
        "        var jpeg = canvas.toDataURL('image/jpeg', 0.93);" +
        "        var b64 = jpeg.split(',')[1];" +
        "        window.AndroidSlip.receiveImageBase64(b64);" +
        "      }).catch(function(e) {" +
        "        window.AndroidSlip.receiveImageBase64('');" +
        "      });" +
        "    }" +
        // Load html2canvas from CDN if not present
        "    if (window.html2canvas) {" +
        "      doCapture();" +
        "    } else {" +
        "      var sc = document.createElement('script');" +
        "      sc.src = 'https://cdnjs.cloudflare.com/ajax/libs/html2canvas/1.4.1/html2canvas.min.js';" +
        "      sc.onload = doCapture;" +
        "      sc.onerror = function() {" +
        // Fallback: send full page HTML to render server-side
        "        window.AndroidSlip.receiveImageBase64('');" +
        "      };" +
        "      document.head.appendChild(sc);" +
        "    }" +
        "  }" +

        // ── 1. Override window.print() ──
        "  window.print = function() { captureAndSend(); };" +

        // ── 2. Override window.open() (print popup pattern) ──
        "  var _origOpen = window.open.bind(window);" +
        "  window.open = function(url, target, features) {" +
        "    if (!url || url===''||url==='about:blank') {" +
        "      var _html='';" +
        "      var fakeWin = {" +
        "        document: {" +
        "          write:function(s){_html+=s;}," +
        "          writeln:function(s){_html+=s;}," +
        "          close:function(){}" +
        "        }," +
        "        print:function(){" +
        "          window.AndroidSlip.showLoadingDialog();" +
        "          var sc=document.createElement('script');" +
        "          sc.src='https://cdnjs.cloudflare.com/ajax/libs/html2canvas/1.4.1/html2canvas.min.js';" +
        "          sc.onload=function(){" +
        "            var d=document.createElement('div');" +
        "            d.style.cssText='position:fixed;left:0;top:0;width:100%;background:#fff;z-index:99999;';" +
        "            d.innerHTML=_html;" +
        "            document.body.appendChild(d);" +
        "            html2canvas(d,{scale:2,backgroundColor:'#ffffff',useCORS:true}).then(function(c){" +
        "              document.body.removeChild(d);" +
        "              window.AndroidSlip.receiveImageBase64(c.toDataURL('image/jpeg',0.93).split(',')[1]);" +
        "            }).catch(function(){document.body.removeChild(d);captureAndSend();});" +
        "          };" +
        "          if(window.html2canvas){sc.onload();}else{document.head.appendChild(sc);}" +
        "        }," +
        "        focus:function(){}," +
        "        close:function(){}" +
        "      };" +
        "      return fakeWin;" +
        "    }" +
        "    if (url&&(url.startsWith('blob:')||url.startsWith('data:application/pdf'))) {" +
        "      window._interceptBlob(url); return null;" +
        "    }" +
        "    // For real URLs opened in new window: let onCreateWindow handle it" +
        "    return _origOpen(url,target,features);" +
        "  };" +

        // ── 3. Blob/data URL interceptor → send PDF as base64 ──
        "  window._interceptBlob = function(url) {" +
        "    window.AndroidSlip.showLoadingDialog();" +
        "    if (url.startsWith('data:application/pdf;base64,')) {" +
        "      window.AndroidSlip.receivePdfBase64(url.substring(28)); return;" +
        "    }" +
        "    fetch(url)" +
        "      .then(function(r){return r.blob();})" +
        "      .then(function(blob){" +
        "        var rd=new FileReader();" +
        "        rd.onloadend=function(){window.AndroidSlip.receivePdfBase64(rd.result.split(',')[1]);};" +
        "        rd.readAsDataURL(blob);" +
        "      }).catch(function(){});" +
        "  };" +

        // ── 4. Intercept <a download> clicks (jsPDF anchor trick) ──
        "  document.addEventListener('click',function(e){" +
        "    var a=e.target.closest?e.target.closest('a[download]'):null;" +
        "    if (!a) return;" +
        "    var href=a.href||'';" +
        "    if (href.startsWith('blob:')||href.startsWith('data:')) {" +
        "      e.preventDefault(); e.stopPropagation();" +
        "      window._interceptBlob(href);" +
        "    }" +
        "  },true);" +

        "  console.log('AndroidSlip bridge v4 ready');" +
        "})();";

        v.evaluateJavascript(js, null);
    }

    // ═══════════════════════════════════════════════════
    //  Image Preview Dialog  (main share UI)
    // ═══════════════════════════════════════════════════
    private void showImagePreview(Bitmap bmp) {
        // Save JPEG to cache for sharing
        String fn  = "miron_slip_" + System.currentTimeMillis() + ".jpg";
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

        // ── Build dialog layout ──
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);

        // Header
        TextView header = new TextView(this);
        header.setText("✅ স্লিপ/রিপোর্ট তৈরি হয়েছে");
        header.setTextSize(16); header.setTypeface(null, android.graphics.Typeface.BOLD);
        header.setTextColor(Color.parseColor("#0D47A1"));
        header.setGravity(Gravity.CENTER);
        header.setPadding(20, 20, 20, 12);
        root.addView(header);

        // Image preview (scrollable for tall slips)
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.parseColor("#F5F5F5"));
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(320));
        scroll.setLayoutParams(sp);

        ImageView img = new ImageView(this);
        img.setImageBitmap(bmp);
        img.setAdjustViewBounds(true);
        img.setScaleType(ImageView.ScaleType.FIT_CENTER);
        img.setPadding(8, 8, 8, 8);
        scroll.addView(img);
        root.addView(scroll);

        // Divider
        View divider = new View(this);
        divider.setBackgroundColor(Color.parseColor("#E0E0E0"));
        divider.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 1));
        root.addView(divider);

        // Buttons
        LinearLayout btns = new LinearLayout(this);
        btns.setOrientation(LinearLayout.VERTICAL);
        btns.setPadding(20, 16, 20, 20);
        btns.setBackgroundColor(Color.WHITE);

        Button waBtn    = makeBtn("📲  WhatsApp এ পাঠান",         "#25D366");
        Button waBizBtn = makeBtn("💼  WA Business এ পাঠান",      "#075E54");
        Button galBtn   = makeBtn("🖼   গ্যালারিতে সেভ করুন",      "#1565C0");
        Button shareBtn = makeBtn("📤  অন্য App এ শেয়ার করুন",    "#37474F");
        Button closeBtn = makeBtn("✖   বন্ধ করুন",                "#9E9E9E");

        btns.addView(waBtn);
        btns.addView(waBizBtn);
        btns.addView(galBtn);
        btns.addView(shareBtn);
        btns.addView(closeBtn);
        root.addView(btns);

        AlertDialog dialog = new AlertDialog.Builder(this)
            .setView(root).create();

        final File finalImgFile = imgFile;
        final Uri finalImgUri = imgUri;
        final Bitmap finalBmp = bmp;

        waBtn.setOnClickListener(v    -> { shareImageViaApp(finalImgUri, "com.whatsapp");    dialog.dismiss(); });
        waBizBtn.setOnClickListener(v -> { shareImageViaApp(finalImgUri, "com.whatsapp.w4b"); dialog.dismiss(); });
        galBtn.setOnClickListener(v   -> { saveImageToGallery(finalBmp, fn); dialog.dismiss(); });
        shareBtn.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("image/jpeg");
            i.putExtra(Intent.EXTRA_STREAM, finalImgUri);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(i, "শেয়ার করুন"));
            dialog.dismiss();
        });
        closeBtn.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
        Window w = dialog.getWindow();
        if (w != null) {
            w.setLayout(WindowManager.LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.WRAP_CONTENT);
        }
    }

    // ═══════════════════════════════════════════════════
    //  PDF options dialog (for report downloads)
    // ═══════════════════════════════════════════════════
    private void showPdfOptions(Uri pdfUri, File pdfFile, String fileName) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(24, 24, 24, 20);
        layout.setBackgroundColor(Color.WHITE);

        TextView title = new TextView(this);
        title.setText("📊 রিপোর্ট PDF তৈরি হয়েছে");
        title.setTextSize(16); title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setTextColor(Color.parseColor("#0D47A1"));
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, 20);
        layout.addView(title);

        Button waBtn    = makeBtn("📲  WhatsApp এ পাঠান",         "#25D366");
        Button waBizBtn = makeBtn("💼  WA Business এ পাঠান",      "#075E54");
        Button saveBtn  = makeBtn("💾  ডাউনলোড ফোল্ডারে সেভ",     "#1565C0");
        Button shareBtn = makeBtn("📤  অন্য App এ শেয়ার",          "#37474F");
        Button openBtn  = makeBtn("👁   PDF দেখুন",                 "#546E7A");

        layout.addView(waBtn); layout.addView(waBizBtn);
        layout.addView(saveBtn); layout.addView(shareBtn); layout.addView(openBtn);

        AlertDialog dialog = new AlertDialog.Builder(this).setView(layout).create();

        waBtn.setOnClickListener(v    -> { sharePdfViaApp(pdfUri, "com.whatsapp");     dialog.dismiss(); });
        waBizBtn.setOnClickListener(v -> { sharePdfViaApp(pdfUri, "com.whatsapp.w4b"); dialog.dismiss(); });
        saveBtn.setOnClickListener(v  -> { savePdfToDownloads(pdfFile, fileName);       dialog.dismiss(); });
        shareBtn.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("application/pdf");
            i.putExtra(Intent.EXTRA_STREAM, pdfUri);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(i, "শেয়ার করুন"));
            dialog.dismiss();
        });
        openBtn.setOnClickListener(v  -> {
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(pdfUri, "application/pdf");
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            try { startActivity(i); }
            catch (Exception e) { toast("PDF viewer নেই। Files app ইনস্টল করুন।"); }
            dialog.dismiss();
        });

        dialog.show();
        Window w = dialog.getWindow();
        if (w != null) w.setLayout(WindowManager.LayoutParams.MATCH_PARENT,
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

    private void sharePdfViaApp(Uri uri, String pkg) {
        try {
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("application/pdf"); i.setPackage(pkg);
            i.putExtra(Intent.EXTRA_STREAM, uri);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(i);
        } catch (Exception e) {
            Intent fb = new Intent(Intent.ACTION_SEND);
            fb.setType("application/pdf");
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
                if (dest == null) throw new Exception("Gallery error");
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
                // Notify gallery
                sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE,
                    Uri.fromFile(out)));
            }
            toast("✅ গ্যালারিতে সেভ হয়েছে!");
        } catch (Exception e) {
            toast("সেভ ব্যর্থ: " + e.getMessage());
        }
    }

    private void savePdfToDownloads(File srcFile, String fileName) {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                ContentValues cv = new ContentValues();
                cv.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
                cv.put(MediaStore.Downloads.MIME_TYPE, "application/pdf");
                cv.put(MediaStore.Downloads.IS_PENDING, 1);
                Uri dest = getContentResolver().insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
                if (dest == null) throw new Exception("Storage error");
                try (InputStream in  = new FileInputStream(srcFile);
                     OutputStream out = getContentResolver().openOutputStream(dest)) {
                    byte[] buf = new byte[4096]; int n;
                    while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                }
                cv.clear();
                cv.put(MediaStore.Downloads.IS_PENDING, 0);
                getContentResolver().update(dest, cv, null, null);
            } else {
                File dl = android.os.Environment.getExternalStoragePublicDirectory(
                    android.os.Environment.DIRECTORY_DOWNLOADS);
                if (!dl.exists()) dl.mkdirs();
                File out = new File(dl, fileName);
                try (InputStream in  = new FileInputStream(srcFile);
                     OutputStream os = new FileOutputStream(out)) {
                    byte[] buf = new byte[4096]; int n;
                    while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
                }
            }
            toast("✅ ডাউনলোড ফোল্ডারে সেভ হয়েছে!");
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

        // ── Intercept blob/data downloads (report PDFs, jsPDF output) ──
        webView.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) -> {
            showLoading("ডাউনলোড হচ্ছে...");
            if (url.startsWith("blob:")) {
                // Fetch blob via JS and send base64 to bridge
                String js = "javascript:(function(){" +
                    "fetch('" + url + "')" +
                    ".then(function(r){return r.blob();})" +
                    ".then(function(b){" +
                    "  var rd=new FileReader();" +
                    "  rd.onloadend=function(){" +
                    "    var parts=rd.result.split(',');" +
                    "    var mt=parts[0];" +
                    "    var b64=parts[1];" +
                    "    if(mt.indexOf('pdf')>-1)" +
                    "      window.AndroidSlip.receivePdfBase64(b64);" +
                    "    else" +
                    "      window.AndroidSlip.receiveImageBase64(b64);" +
                    "  };" +
                    "  rd.readAsDataURL(b);" +
                    "}).catch(function(){});})();";
                webView.loadUrl(js);
            } else if (mimeType != null && mimeType.contains("pdf") &&
                       url.startsWith("data:")) {
                String b64 = url.contains(",") ? url.substring(url.indexOf(',')+1) : url;
                new SlipBridge().receivePdfBase64(b64);
            } else {
                dismissLoading();
            }
        });

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                String url = r.getUrl().toString();
                // Catch any navigation to blob: (some apps navigate instead of download)
                if (url.startsWith("blob:")) {
                    showLoading("তৈরি হচ্ছে...");
                    String js = "javascript:(function(){" +
                        "fetch('" + url + "')" +
                        ".then(function(r){return r.blob();})" +
                        ".then(function(b){" +
                        "  var rd=new FileReader();" +
                        "  rd.onloadend=function(){" +
                        "    var b64=rd.result.split(',')[1];" +
                        "    window.AndroidSlip.receivePdfBase64(b64);" +
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
            // Handle window.open() popups (print-in-popup pattern)
            @Override
            public boolean onCreateWindow(WebView view, boolean isDialog,
                    boolean isUserGesture, android.os.Message resultMsg) {
                WebView popup = new WebView(MainActivity.this);
                WebSettings ps = popup.getSettings();
                ps.setJavaScriptEnabled(true);
                ps.setDomStorageEnabled(true);
                popup.addJavascriptInterface(new SlipBridge(), "AndroidSlip");
                popup.setWebViewClient(new WebViewClient() {
                    @Override
                    public void onPageFinished(WebView pv, String url) {
                        pv.postDelayed(() ->
                            pv.evaluateJavascript(
                                "(function(){return document.documentElement.outerHTML;})();",
                                html -> {
                                    if (html != null && !html.equals("null") && html.length() > 10) {
                                        // Inject bridge first, then after it settles call captureAndSend via print
                                        injectBridgeOverrides(pv);
                                        // Delay enough for evaluateJavascript to complete + html2canvas to load
                                        pv.postDelayed(() ->
                                            pv.evaluateJavascript("if(window.AndroidSlip){window.print();}else{setTimeout(function(){window.print();},800);}", null),
                                        600);
                                    }
                                }), 800);
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
