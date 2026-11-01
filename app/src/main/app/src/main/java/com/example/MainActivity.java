package com.example;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.activity.ComponentActivity;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;

import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;

public class MainActivity extends ComponentActivity {
    private static final String TAG = "WaterSortAds";
    public static final String REWARDED_AD_UNIT_ID = "ca-app-pub-5963124440498727/4464428881";

    private WebView webView;
    private RewardedAd rewardedAd;
    private boolean isLoadingAd = false;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Dark theme status bar and navigation bar
        Window window = getWindow();
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setStatusBarColor(Color.parseColor("#121212"));
        window.setNavigationBarColor(Color.parseColor("#121212"));

        // 1. Initialize Google Mobile Ads SDK
        MobileAds.initialize(this, initializationStatus -> {
            Log.d(TAG, "Google Mobile Ads SDK Initialized.");
            loadRewardedAd();
        });

        // 2. Setup WebView with Web Audio API & HTML5 LocalStorage
        webView = new WebView(this);
        webView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        webView.setBackgroundColor(Color.parseColor("#121212"));
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);                 // LocalStorage support
        settings.setDatabaseEnabled(true);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setMediaPlaybackRequiresUserGesture(false); // Web Audio API procedural sound

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                Log.d(TAG, "Game loaded successfully in WebView.");
            }
        });
        webView.setWebChromeClient(new WebChromeClient());

        // 3. Register JavaScriptInterface for showRewardedAd() and isOnline()
        webView.addJavascriptInterface(new WebAppInterface(this), "AndroidBridge");

        setContentView(webView);

        // 4. Load the local HTML5 Game from assets
        webView.loadUrl("file:///android_asset/index.html");

        // 5. Hardware Back Button Navigation Handler
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (webView != null) {
                    webView.evaluateJavascript(
                        "(function() {" +
                        "  if (document.querySelector('.modal-backdrop.active')) {" +
                        "    document.querySelectorAll('.modal-backdrop').forEach(function(m) { m.classList.remove('active'); });" +
                        "    return 'modal_closed';" +
                        "  }" +
                        "  var gameScreen = document.getElementById('game-screen');" +
                        "  if (gameScreen && gameScreen.classList.contains('active')) {" +
                        "    if (window.gameInstance && typeof window.gameInstance.showHomeScreen === 'function') {" +
                        "      window.gameInstance.showHomeScreen();" +
                        "      return 'went_home';" +
                        "    }" +
                        "  }" +
                        "  return 'exit';" +
                        "})()",
                        result -> {
                            if ("\"exit\"".equals(result) || result == null) {
                                finish();
                            }
                        }
                    );
                } else {
                    finish();
                }
            }
        });
    }

    public void loadRewardedAd() {
        if (isLoadingAd || rewardedAd != null) {
            return;
        }
        isLoadingAd = true;
        AdRequest adRequest = new AdRequest.Builder().build();

        RewardedAd.load(this, REWARDED_AD_UNIT_ID, adRequest, new RewardedAdLoadCallback() {
            @Override
            public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                Log.e(TAG, "Rewarded Ad failed to load: " + loadAdError.getMessage());
                rewardedAd = null;
                isLoadingAd = false;
            }

            @Override
            public void onAdLoaded(@NonNull RewardedAd ad) {
                Log.d(TAG, "Rewarded Ad loaded successfully.");
                rewardedAd = ad;
                isLoadingAd = false;
            }
        });
    }

    public class WebAppInterface {
        private final Context context;

        public WebAppInterface(Context context) {
            this.context = context;
        }

        @JavascriptInterface
        public boolean isOnline() {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return false;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                android.net.Network network = cm.getActiveNetwork();
                if (network == null) return false;
                NetworkCapabilities caps = cm.getNetworkCapabilities(network);
                return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
            } else {
                android.net.NetworkInfo netInfo = cm.getActiveNetworkInfo();
                return netInfo != null && netInfo.isConnected();
            }
        }

        @JavascriptInterface
        public void showRewardedAd(final String rewardType) {
            mainHandler.post(() -> {
                if (rewardedAd != null) {
                    rewardedAd.setFullScreenContentCallback(new FullScreenContentCallback() {
                        @Override
                        public void onAdDismissedFullScreenContent() {
                            rewardedAd = null;
                            loadRewardedAd(); // Preload next ad
                        }

                        @Override
                        public void onAdFailedToShowFullScreenContent(@NonNull AdError adError) {
                            Log.e(TAG, "Ad failed to show: " + adError.getMessage());
                            rewardedAd = null;
                            loadRewardedAd();
                            notifyRewardGranted(false);
                        }
                    });

                    rewardedAd.show(MainActivity.this, rewardItem -> {
                        Log.d(TAG, "User earned reward: " + rewardType);
                        notifyRewardGranted(true);
                    });
                } else {
                    Log.w(TAG, "Rewarded ad still caching. Preloading and triggering fallback...");
                    loadRewardedAd();
                    mainHandler.postDelayed(() -> notifyRewardGranted(true), 3500);
                }
            });
        }

        private void notifyRewardGranted(boolean granted) {
            if (webView != null) {
                webView.evaluateJavascript(
                    "if (typeof window.__adRewardCallback === 'function') { window.__adRewardCallback(" + granted + "); }",
                    null
                );
            }
        }
    }
}
