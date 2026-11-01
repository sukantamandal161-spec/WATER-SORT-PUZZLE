package com.example.watersort;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.os.Bundle;
import android.webkit.ConsoleMessage;
import android.webkit.JavascriptInterface;
import android.webkit.JsResult;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private RewardedAd rewardedAd;
    private boolean isLoadingAd = false;
    private final String AD_UNIT_ID = "ca-app-pub-3940256099942544/5224354917";

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        webView = new WebView(this);
        setContentView(webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);

        // Allow local file & script loading
        settings.setAllowFileAccessFromFileURLs(true);
        settings.setAllowUniversalAccessFromFileURLs(true);

        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setMediaPlaybackRequiresUserGesture(false);

        // Alert popups ko enable karein taaki JS errors screen par dikh sakein
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onJsAlert(WebView view, String url, String message, JsResult result) {
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("Game Message")
                        .setMessage(message)
                        .setPositiveButton(android.R.string.ok, (dialog, which) -> result.confirm())
                        .setCancelable(false)
                        .create()
                        .show();
                return true;
            }
        });

        webView.setWebViewClient(new WebViewClient());
        webView.addJavascriptInterface(new WebAppInterface(), "AndroidBridge");

        // Enable remote debugging
        WebView.setWebContentsDebuggingEnabled(true);

        // Load game
        webView.loadUrl("file:///android_asset/index.html");

        // Safe ad initialization
        webView.postDelayed(() -> {
            try {
                MobileAds.initialize(this, initializationStatus -> {});
                loadRewardedAd();
            } catch (Throwable ignored) {}
        }, 1500);
    }

    private void loadRewardedAd() {
        if (rewardedAd != null || isLoadingAd) return;
        isLoadingAd = true;
        try {
            AdRequest adRequest = new AdRequest.Builder().build();
            RewardedAd.load(this, AD_UNIT_ID, adRequest, new RewardedAdLoadCallback() {
                @Override
                public void onAdLoaded(@NonNull RewardedAd ad) {
                    rewardedAd = ad;
                    isLoadingAd = false;
                }

                @Override
                public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                    rewardedAd = null;
                    isLoadingAd = false;
                }
            });
        } catch (Throwable t) {
            isLoadingAd = false;
        }
    }

    public class WebAppInterface {
        @JavascriptInterface
        public void showRewardedAd() {
            runOnUiThread(() -> {
                if (rewardedAd != null) {
                    try {
                        rewardedAd.show(MainActivity.this, rewardItem -> {
                            runOnUiThread(() -> {
                                if (webView != null) {
                                    webView.evaluateJavascript("if(window.onAdRewardGranted) window.onAdRewardGranted();", null);
                                }
                            });
                        });
                        rewardedAd = null;
                        loadRewardedAd();
                    } catch (Throwable t) {
                        grantReward();
                    }
                } else {
                    grantReward();
                    loadRewardedAd();
                }
            });
        }

        private void grantReward() {
            if (webView != null) {
                webView.evaluateJavascript("if(window.onAdRewardGranted) window.onAdRewardGranted();", null);
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
