package com.hero;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.webkit.DownloadListener;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.hero.depandency.ContextUtils;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/**
 * Created by liuguoping on 15/9/24.
 */
@SuppressLint("SetJavaScriptEnabled")
public class HeroWebView extends WebView implements IHero {
    static final String TAG = "HeroWebView";
    public final static boolean NEED_VERIFY_URL_HOST = false;
    private HeroFragment parentFragment = null;

    public HeroWebView(Context context) {
        super(context);
        // On some phones SecurityException will be thrown for lacking WRITE_SECURE_SETTINGS permission,
        // but the method doesn't need a secure permission at all, furthermore, we can't ask for
        // the WRITE_SECURE_SETTINGS permission because it's a system permission
        try {
            this.getSettings().setJavaScriptEnabled(true);
        } catch (Exception e) {
            e.printStackTrace();
        }
        this.addJavascriptInterface(this, "native");
//        if (Build.VERSION.SDK_INT < 17) {
//            this.removeJavascriptInterface("searchBoxJavaBridge_");
//        }
        String userAgent = this.getSettings().getUserAgentString();
        userAgent += " Android/" + ContextUtils.getSystemVersion() + " hero-android/" + ContextUtils.getVersionCode(this.getContext()) + " imei/" + ContextUtils.getIMEI(context) + " androidId/" + ContextUtils.getAndroidId(context);
        this.getSettings().setUserAgentString(userAgent);
        this.getSettings().setDomStorageEnabled(true);
        String appCachePath = getContext().getCacheDir().getAbsolutePath();
        this.getSettings().setAppCachePath(appCachePath);
        this.getSettings().setAllowFileAccess(true);
        this.getSettings().setAppCacheEnabled(true);
        this.getSettings().setCacheMode(WebSettings.LOAD_DEFAULT);
        this.setDownloadListener(new MyWebViewDownLoadListener());

        this.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (!isUrlAuthenticated(url)) {
                    return true;
                }

                if(!url.startsWith("http:") || url.startsWith("https:") ) {
                    return false;
                }

                view.loadUrl(url);
                return true;
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);

                Log.i(TAG, "onReceivedError " + error);
            }

            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                super.onReceivedError(view, errorCode, description, failingUrl);
                InputStream page404 = getResources().openRawResource(R.raw.page_404);
                if (page404 != null) {
                    String content = null;
                    try {
                        content = inputStreamTOString(page404);
                    } catch (Exception e) {
                        Log.d("Error", e.getMessage());
                    }
                    view.loadData(content, "text/html;charset=UTF-8", null);
                }
                Log.i(TAG, "onReceivedError deprecated " + failingUrl);
            }

            @Override
            public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse errorResponse) {
                super.onReceivedHttpError(view, request, errorResponse);
                Log.i(TAG, "onReceivedHttpError " + errorResponse);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (view.getParent() != null && parentFragment != null) {
                    parentFragment.showToolBar(true);
                    try {
                        JSONObject object = new JSONObject("{common:{event:finish}}");
                        HeroView.sendActionToContext(getContext(), object);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
            }

            @Override
            public boolean shouldOverrideKeyEvent(WebView view, KeyEvent event) {
                if ((event.getKeyCode() == KeyEvent.KEYCODE_BACK) && view.canGoBack()) {
                    view.goBack();
                    return true;
                }
                return false;
            }
        });
        this.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onReceivedTitle(WebView view, String title) {
                super.onReceivedTitle(view, title);
                if (view.getParent() != null && parentFragment != null) {
                    if (!TextUtils.isEmpty(title) && !title.contains(".html")) {
                        parentFragment.showToolBar(true);
                        parentFragment.setTitle(title);
                    }
                }
            }


            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                    super.onProgressChanged(view, newProgress);
            }
        });
    }

    public static String inputStreamTOString(InputStream in) throws Exception{
        int BUFFER_SIZE = 1024;

        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        byte[] data = new byte[BUFFER_SIZE];
        int count = -1;
        while((count = in.read(data,0,BUFFER_SIZE)) != -1)
            outStream.write(data, 0, count);

        data = null;
        return new String(outStream.toByteArray(),"UTF-8");
    }

    public HeroWebView(Context context, int initColor) {
        this(context);
        this.setBackgroundColor(initColor);
    }

    @JavascriptInterface
    public void on(String jsonStr) {
        try {
            Object json = new JSONTokener(jsonStr).nextValue();
            if (json instanceof JSONObject && parentFragment != null && parentFragment.getTag() != null) {
                ((JSONObject)json).put("fragment_tag", parentFragment.getTag());
            }
            ((IHeroContext) this.getContext()).on((JSONObject) json);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void loadUrl(String url) {
        if (BuildConfig.DEBUG && url.startsWith("http")) {
            if (url.contains("?")) {
                url = url + "&test=true";
            } else {
                url = url + "?test=true";
            }
        }
        if (!isUrlAuthenticated(url)) {
            return;
        }
        super.loadUrl(url);
    }

    @Override
    public void on(JSONObject jsonObject) throws JSONException {
        HeroView.on(this, jsonObject);
        if (jsonObject.has("url")) {
            String urlStr = jsonObject.getString("url");
            this.loadUrl(urlStr);
        }
        if (jsonObject.has("innerHtml")) {
            this.loadData(jsonObject.getString("innerHtml"), "text/html;charset=UTF-8", null);
        }
    }

    public void setFragment(HeroFragment fragment) {
        parentFragment = fragment;
    }

    private class MyWebViewDownLoadListener implements DownloadListener {

        @Override
        public void onDownloadStart(String url, String userAgent, String contentDisposition, String mimetype,
                                    long contentLength) {
            Uri uri = Uri.parse(url);
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(intent);
        }

    }

    private boolean isUrlAuthenticated(String url) {
        if (NEED_VERIFY_URL_HOST) {
            Context application = getContext().getApplicationContext();
            if (application instanceof HeroApplication) {
                String urlDomain = HeroApplication.getDomainAddress(url);
                String host = ((HeroApplication)application).getHomeAddress();
                String hostDomain = HeroApplication.getDomainAddress(host);
                if (urlDomain.equals(hostDomain)) {
                    return true;
                }
                return false;
            }
        }
        return true;
    }
}
