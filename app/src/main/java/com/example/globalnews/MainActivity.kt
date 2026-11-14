package com.example.globalnews

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.webkit.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : Activity() {
    private lateinit var web: WebView

    inner class Bridge {
        @JavascriptInterface
        fun get(id: Int, u: String) {
            Thread {
                val t = try {
                    val c = URL(u).openConnection() as HttpURLConnection
                    c.connectTimeout = 10000; c.readTimeout = 10000
                    c.setRequestProperty("User-Agent", "Mozilla/5.0")
                    c.inputStream.bufferedReader().use { it.readText() }
                } catch (e: Exception) { "" }
                web.post { web.evaluateJavascript("__cb($id,${JSONObject.quote(t)})", null) }
            }.start()
        }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        web = WebView(this)
        setContentView(web)
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }
        web.addJavascriptInterface(Bridge(), "Android")
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean {
                if (r.url.scheme == "file") return false
                startActivity(Intent(Intent.ACTION_VIEW, r.url)); return true
            }
        }
        web.loadUrl("file:///android_asset/index.html")
    }

    @Deprecated("back")
    override fun onBackPressed() { if (web.canGoBack()) web.goBack() else super.onBackPressed() }
}
