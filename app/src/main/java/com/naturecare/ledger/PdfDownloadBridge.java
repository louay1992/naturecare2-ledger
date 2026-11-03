package com.naturecare.ledger;

import android.webkit.JavascriptInterface;

/**
 * Separate PDF-only JavaScript interface for NatureCare WebView.
 * Public class ensures method visibility independently of the original LedgerBridge.
 * Does not alter authentication, payments or Firebase notification registration.
 */
public final class PdfDownloadBridge {
    private final MainActivity activity;

    public PdfDownloadBridge(MainActivity activity) {
        this.activity = activity;
    }

    @JavascriptInterface
    public String bridgeVersion() {
        return "NatureCarePdf/1.2.0";
    }

    @JavascriptInterface
    public void savePdf(String filename, String base64Pdf) {
        activity.savePdfFromDedicatedBridge(filename, base64Pdf);
    }
}
