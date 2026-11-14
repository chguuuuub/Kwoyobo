package com.example.globalnews

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.webkit.*
import android.widget.Toast
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var web: WebView
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    private val patch = """
window.share=function(i){Android.share(items[i].title,items[i].link)};
window.__spDone=function(){document.querySelectorAll('.a button:first-child').forEach(function(b){b.textContent='🔊 朗读'});window.__sp=null};
window.speak=function(i,btn){
 if(window.__sp===i){Android.stopSpeak();__spDone();return}
 __spDone();
 var x=items[i];
 Android.speak(x.title+'。'+x.desc);
 window.__sp=i;btn.textContent='⏹ 停止';
};
"""

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

        @JavascriptInterface
        fun speak(text: String) {
            runOnUiThread {
                val t = tts
                if (t == null || !ttsReady) {
                    Toast.makeText(this@MainActivity, "未找到语音引擎", Toast.LENGTH_SHORT).show()
                    web.evaluateJavascript("__spDone()", null)
                    return@runOnUiThread
                }
                val zh = Regex("[\\u4e00-\\u9fff]").containsMatchIn(text)
                val r = t.setLanguage(if (zh) Locale.SIMPLIFIED_CHINESE else Locale.US)
                if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Toast.makeText(this@MainActivity, "缺少该语言的语音数据", Toast.LENGTH_SHORT).show()
                    web.evaluateJavascript("__spDone()", null)
                    return@runOnUiThread
                }
                t.speak(text, TextToSpeech.QUEUE_FLUSH, null, "n")
            }
        }

        @JavascriptInterface
        fun stopSpeak() { runOnUiThread { tts?.stop() } }

        @JavascriptInterface
        fun share(title: String, url: String) {
            runOnUiThread {
                val i = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, title + "\n" + url)
                }
                startActivity(Intent.createChooser(i, "分享"))
            }
        }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        web = WebView(this)
        setContentView(web)
        tts = TextToSpeech(this) { s ->
            ttsReady = s == TextToSpeech.SUCCESS
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) {}
                override fun onDone(id: String?) { runOnUiThread { web.evaluateJavascript("__spDone()", null) } }
                @Deprecated("old")
                override fun onError(id: String?) { runOnUiThread { web.evaluateJavascript("__spDone()", null) } }
            })
        }
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }
        web.addJavascriptInterface(Bridge(), "Android")
        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(v: WebView, url: String) {
                if (url.startsWith("file")) v.evaluateJavascript(patch, null)
            }
            override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean {
                if (r.url.scheme == "file") return false
                startActivity(Intent(Intent.ACTION_VIEW, r.url)); return true
            }
        }
        web.loadUrl("file:///android_asset/index.html")
    }

    override fun onDestroy() {
        tts?.shutdown()
        super.onDestroy()
    }

    @Deprecated("back")
    override fun onBackPressed() { if (web.canGoBack()) web.goBack() else super.onBackPressed() }
}
