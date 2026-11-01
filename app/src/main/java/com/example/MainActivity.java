package com.example.watersort;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.annotation.NonNull;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;

public class MainActivity extends Activity {

    private WebView webView;
    private RewardedAd rewardedAd;
    private boolean isLoadingAd = false;
    private final String AD_UNIT_ID = "ca-app-pub-3940256099942544/5224354917";

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            webView = new WebView(this);
            setContentView(webView);

            WebSettings settings = webView.getSettings();
            settings.setJavaScriptEnabled(true);
            settings.setDomStorageEnabled(true);
            settings.setAllowFileAccess(true);
            settings.setDatabaseEnabled(true);
            settings.setUseWideViewPort(true);
            settings.setLoadWithOverviewMode(true);
            settings.setMediaPlaybackRequiresUserGesture(false);

            webView.setWebViewClient(new WebViewClient());
            webView.setWebChromeClient(new WebChromeClient());
            webView.addJavascriptInterface(new WebAppInterface(), "AndroidBridge");

            webView.loadUrl("file:///android_asset/index.html");
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Initialize Ads safely in background
        new Thread(() -> {
            try {
                MobileAds.initialize(MainActivity.this, initializationStatus -> {});
                runOnUiThread(this::loadRewardedAd);
            } catch (Exception ignored) {}
        }).start();
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
        } catch (Exception e) {
            isLoadingAd = false;
        }
    }

    public class WebAppInterface {
        @JavascriptInterface
        public void showRewardedAd() {
            runOnUiThread(() -> {
                if (rewardedAd != null) {
                    rewardedAd.show(MainActivity.this, rewardItem -> {
                        runOnUiThread(() -> {
                            if (webView != null) {
                                webView.evaluateJavascript("if(window.onAdRewardGranted) window.onAdRewardGranted();", null);
                            }
                        });
                    });
                    rewardedAd = null;
                    loadRewardedAd();
                } else {
                    // Fallback to in-game reward if ad isn't loaded
                    if (webView != null) {
                        webView.evaluateJavascript("if(window.onAdRewardGranted) window.onAdRewardGranted();", null);
                    }
                    loadRewardedAd();
                }
            });
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
