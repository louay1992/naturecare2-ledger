package com.naturecare.ledger;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.os.Build;
import com.google.firebase.messaging.FirebaseMessaging;
import org.json.JSONObject;
import android.app.Activity;
import android.print.PrintManager;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
    private static final String SITE_HOST = "acc-naturecare.netlify.app";
    private static final int SAVE_CSV_REQUEST = 1813;
    private static final int SAVE_PDF_REQUEST = 1815;
    private static final int NOTIFICATION_PERMISSION_REQUEST = 1814;
    private WebView webView;
    private LinearLayout overlay;
    private TextView message;
    private ProgressBar progress;
    private Button retry;
    private String pendingCsv;
    private String pendingPdf;
    private boolean pageFailed;

    private static final String WEB_INTEGRATION = "(function(){" +
        "if(window.__naturecareAndroidReady)return;window.__naturecareAndroidReady=true;" +
        "window.print=function(){NatureCareAndroid.printPage();};" +
        "try{if(!navigator.clipboard||!navigator.clipboard.writeText){" +
        "Object.defineProperty(navigator,'clipboard',{configurable:true,value:{" +
        "writeText:function(t){NatureCareAndroid.copyText(String(t));return Promise.resolve();}" +
        "}});}}catch(e){}" +
        "document.addEventListener('click',function(e){" +
        "var a=e.target&&e.target.closest&&e.target.closest('a[download]');" +
        "if(!a||!a.href||a.href.indexOf('blob:')!==0)return;" +
        "e.preventDefault();e.stopPropagation();" +
        "fetch(a.href).then(function(r){return r.text();}).then(function(t){" +
        "NatureCareAndroid.saveCsv(a.getAttribute('download')||'NatureCare_Ledger.csv',t);" +
        "}).catch(function(err){console.error('CSV save failed',err);});" +
        "},true);" +
        "})();";

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(13,80,59));
        getWindow().setNavigationBarColor(Color.rgb(243,248,245));

        FrameLayout screen = new FrameLayout(this);
        screen.setBackgroundColor(Color.rgb(243,248,245));
        webView = new WebView(this);
        webView.setBackgroundColor(Color.rgb(243,248,245));
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setSafeBrowsingEnabled(true);
        s.setSupportZoom(false);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, false);
        webView.addJavascriptInterface(new LedgerBridge(), "NatureCareAndroid");
        // v1.2.0: independent, public Android-JavaScript bridge dedicated to PDF files.
        // The original bridge remains intact for Firebase push and CSV export.
        webView.addJavascriptInterface(new PdfDownloadBridge(this), "NatureCarePdf");
        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override public void onPageStarted(WebView view, String url, android.graphics.Bitmap icon) {
                super.onPageStarted(view, url, icon);
                pageFailed = false;
                showLoading();
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if ("https".equalsIgnoreCase(uri.getScheme()) && SITE_HOST.equalsIgnoreCase(uri.getHost())) {
                    return false;
                }
                openOutside(uri);
                return true;
            }
            @Override public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (!pageFailed) {
                    overlay.setVisibility(View.GONE);
                    view.evaluateJavascript(WEB_INTEGRATION, null);
                }
            }
            @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) {
                    pageFailed = true;
                    showError("تعذر الاتصال بالموقع. تأكد من الإنترنت وحاول مجدداً.");
                }
            }
            @Override public void onReceivedHttpError(WebView view, WebResourceRequest request,
                    android.webkit.WebResourceResponse response) {
                if (request.isForMainFrame() && response.getStatusCode() >= 400) {
                    pageFailed = true;
                    showError("صفحة التطبيق غير متاحة حالياً (" + response.getStatusCode() + ").");
                }
            }
        });
        screen.addView(webView, new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        overlay = new LinearLayout(this);
        overlay.setOrientation(LinearLayout.VERTICAL);
        overlay.setGravity(Gravity.CENTER);
        overlay.setPadding(dp(24), dp(24), dp(24), dp(24));
        overlay.setBackgroundColor(Color.rgb(243,248,245));
        TextView title = new TextView(this);
        title.setText("🌿 NatureCare Ledger");
        title.setTextSize(24);
        title.setTextColor(Color.rgb(13,80,59));
        title.setTypeface(null, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        overlay.addView(title);
        progress = new ProgressBar(this);
        LinearLayout.LayoutParams progressLp = new LinearLayout.LayoutParams(dp(42),dp(42));
        progressLp.topMargin = dp(22);
        overlay.addView(progress, progressLp);
        message = new TextView(this);
        message.setText("جارٍ الاتصال بدفتر الحسابات…");
        message.setTextColor(Color.rgb(49,75,62));
        message.setTextSize(16);
        message.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams msgLp = new LinearLayout.LayoutParams(-1,-2);
        msgLp.topMargin = dp(20);
        overlay.addView(message, msgLp);
        retry = new Button(this);
        retry.setText("إعادة المحاولة");
        retry.setVisibility(View.GONE);
        retry.setOnClickListener(v -> webView.loadUrl(BuildConfig.START_URL));
        LinearLayout.LayoutParams retryLp = new LinearLayout.LayoutParams(-2,-2);
        retryLp.topMargin = dp(16);
        overlay.addView(retry, retryLp);
        screen.addView(overlay, new FrameLayout.LayoutParams(-1,-1));
        setContentView(screen);
        if (savedInstanceState == null || webView.restoreState(savedInstanceState) == null) {
            webView.loadUrl(BuildConfig.START_URL +
                ("approvals".equals(getIntent().getStringExtra("nc_target")) ? "#approvals" : ""));
        }
    }

    private int dp(int n) {return (int)(n * getResources().getDisplayMetrics().density + .5f);}
    private void showLoading() {
        overlay.setVisibility(View.VISIBLE);
        message.setText("جارٍ فتح دفتر الحسابات…");
        progress.setVisibility(View.VISIBLE);
        retry.setVisibility(View.GONE);
    }
    private void showError(String text) {
        overlay.setVisibility(View.VISIBLE);
        message.setText(text);
        progress.setVisibility(View.GONE);
        retry.setVisibility(View.VISIBLE);
    }
    private void openOutside(Uri uri) {
        if (uri == null) return;
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equals("https") || scheme.equals("http") || scheme.equals("mailto") || scheme.equals("tel"))) return;
        try {startActivity(new Intent(Intent.ACTION_VIEW, uri));}
        catch (ActivityNotFoundException e) {Toast.makeText(this,"تعذر فتح الرابط",Toast.LENGTH_SHORT).show();}
    }

    // Invoked only from the separate annotated PDF bridge. The existing savePdf
    // implementation performs filename sanitization, PDF validation and MediaStore I/O.
    void savePdfFromDedicatedBridge(String fileName, String pdfBase64) {
        new LedgerBridge().savePdf(fileName, pdfBase64);
    }

    private final class LedgerBridge {
        @JavascriptInterface public void requestPushToken() {
            // Called only after the web app has confirmed a Supabase session.
            runOnUiThread(() -> askNotificationPermissionThenFetch());
        }
        @JavascriptInterface public void copyText(String text) {
            runOnUiThread(() -> {
                ClipboardManager cb = (ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
                cb.setPrimaryClip(ClipData.newPlainText("NatureCare", text));
            });
        }
        @JavascriptInterface public void printPage() {
            runOnUiThread(() -> {
                PrintManager printManager = (PrintManager)getSystemService(Context.PRINT_SERVICE);
                printManager.print("NatureCare Ledger", webView.createPrintDocumentAdapter("NatureCare Ledger"),null);
            });
        }
        // v8.5: Save a real application/pdf directly, never through the CSV blob handler.
        @JavascriptInterface public void savePdf(String fileName, String pdfBase64) {
            if (pdfBase64 == null || pdfBase64.isEmpty() || pdfBase64.length() > 32000000) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this,
                    "تعذر حفظ PDF: الملف غير صالح أو كبير جداً", Toast.LENGTH_LONG).show());
                return;
            }
            String name = (fileName == null ? "NatureCare_Statement.pdf" : fileName)
                    .replaceAll("[\\\\/:*?\"<>|]", "_");
            if (!name.toLowerCase(java.util.Locale.ROOT).endsWith(".pdf")) name += ".pdf";
            final String safeName = name;
            if (Build.VERSION.SDK_INT >= 29) {
                new Thread(() -> {
                    Uri saved = null;
                    try {
                        byte[] bytes = Base64.decode(pdfBase64, Base64.DEFAULT);
                        if (bytes.length < 5 || bytes[0] != '%' || bytes[1] != 'P'
                                || bytes[2] != 'D' || bytes[3] != 'F' || bytes[4] != '-') {
                            throw new IllegalArgumentException("Not a valid PDF");
                        }
                        ContentValues v = new ContentValues();
                        v.put(MediaStore.MediaColumns.DISPLAY_NAME, safeName);
                        v.put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf");
                        v.put(MediaStore.MediaColumns.RELATIVE_PATH,
                                Environment.DIRECTORY_DOWNLOADS + "/NatureCare Ledger");
                        v.put(MediaStore.MediaColumns.IS_PENDING, 1);
                        saved = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                        if (saved == null) throw new IllegalStateException("Cannot create download");
                        try (OutputStream out = getContentResolver().openOutputStream(saved)) {
                            if (out == null) throw new IllegalStateException("Cannot open output");
                            out.write(bytes);
                        }
                        ContentValues ready = new ContentValues();
                        ready.put(MediaStore.MediaColumns.IS_PENDING, 0);
                        getContentResolver().update(saved, ready, null, null);
                        runOnUiThread(() -> Toast.makeText(MainActivity.this,
                            "تم تنزيل PDF في Downloads/NatureCare Ledger", Toast.LENGTH_LONG).show());
                    } catch (Exception ex) {
                        if (saved != null) try {getContentResolver().delete(saved, null, null);} catch(Exception ignored) {}
                        runOnUiThread(() -> Toast.makeText(MainActivity.this,
                            "تعذر حفظ PDF: " + ex.getMessage(), Toast.LENGTH_LONG).show());
                    }
                }).start();
            } else {
                runOnUiThread(() -> {
                    pendingPdf = pdfBase64;
                    Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("application/pdf");
                    intent.putExtra(Intent.EXTRA_TITLE, safeName);
                    try { startActivityForResult(intent, SAVE_PDF_REQUEST); }
                    catch (ActivityNotFoundException ex) {
                        pendingPdf = null;
                        Toast.makeText(MainActivity.this, "تعذر فتح حفظ PDF", Toast.LENGTH_LONG).show();
                    }
                });
            }
        }
        @JavascriptInterface public void saveCsv(String fileName, String csv) {
            runOnUiThread(() -> {
                pendingCsv = csv;
                String safeName = fileName.replaceAll("[\\\\/:*?\"<>|]", "_");
                if (!safeName.toLowerCase().endsWith(".csv")) safeName += ".csv";
                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("text/csv");
                intent.putExtra(Intent.EXTRA_TITLE, safeName);
                try {startActivityForResult(intent,SAVE_CSV_REQUEST);}
                catch (ActivityNotFoundException e) {Toast.makeText(MainActivity.this,"تعذر فتح حفظ الملفات",Toast.LENGTH_LONG).show();}
            });
        }
    }
    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode,resultCode,data);
        if (requestCode == SAVE_PDF_REQUEST) {
            String encoded = pendingPdf;
            pendingPdf = null;
            if (resultCode != RESULT_OK || data == null || data.getData() == null || encoded == null) return;
            try (OutputStream output = getContentResolver().openOutputStream(data.getData())) {
                if (output == null) throw new IllegalStateException("No output stream");
                output.write(Base64.decode(encoded, Base64.DEFAULT));
                Toast.makeText(this, "تم حفظ كشف الحساب PDF", Toast.LENGTH_LONG).show();
            } catch (Exception ex) {
                Toast.makeText(this, "تعذر حفظ PDF: " + ex.getMessage(), Toast.LENGTH_LONG).show();
            }
            return;
        }
        if (requestCode != SAVE_CSV_REQUEST) return;
        if (resultCode != RESULT_OK || data == null || data.getData()==null || pendingCsv == null) {
            pendingCsv = null;
            return;
        }
        try(OutputStream output = getContentResolver().openOutputStream(data.getData())) {
            if (output == null) throw new IllegalStateException("No output stream");
            output.write(pendingCsv.getBytes(StandardCharsets.UTF_8));
            Toast.makeText(this,"تم حفظ كشف الحساب",Toast.LENGTH_LONG).show();
        } catch(Exception ex) {
            Toast.makeText(this,"تعذر حفظ الملف: " + ex.getMessage(),Toast.LENGTH_LONG).show();
        } finally {pendingCsv = null;}
    }
    private void askNotificationPermissionThenFetch() {
        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_PERMISSION_REQUEST);
            return;
        }
        fetchPushToken();
    }
    private void fetchPushToken() {
        FirebaseMessaging.getInstance().getToken().addOnCompleteListener(task -> {
            if (!task.isSuccessful() || webView == null) return;
            String token = task.getResult();
            if (token == null || token.isEmpty()) return;
            // JSON-encode the token: never splice untrusted text directly into JavaScript.
            String script = "window.dispatchEvent(new CustomEvent('naturecare:push-token',{detail:{token:"
                    + JSONObject.quote(token) + "}}));";
            webView.post(() -> { if (webView != null) webView.evaluateJavascript(script, null); });
        });
    }
    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == NOTIFICATION_PERMISSION_REQUEST && grantResults.length > 0 &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED) fetchPushToken();
    }
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (webView != null && "approvals".equals(intent.getStringExtra("nc_target"))) {
            webView.evaluateJavascript("window.dispatchEvent(new Event('naturecare:open-approvals'));", null);
        }
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        webView.saveState(state);
        super.onSaveInstanceState(state);
    }
    @Override public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }
    @Override protected void onDestroy() {
        if (webView!=null) {
            webView.removeJavascriptInterface("NatureCareAndroid");
            webView.stopLoading();
            webView.destroy();
            webView=null;
        }
        super.onDestroy();
    }
}
