package com.example.watersort;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
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

        // WebView setup
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

        // Game load assets
        webView.loadUrl("file:///android_asset/index.html");

        // AdMob load delay
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
