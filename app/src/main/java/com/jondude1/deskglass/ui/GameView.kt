package com.jondude1.deskglass.ui

import android.annotation.SuppressLint
import android.graphics.Color as AColor
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.jondude1.deskglass.data.GameInfo
import com.jondude1.deskglass.data.GameStore
import java.io.ByteArrayInputStream
import java.io.FileInputStream
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/** What a game page can call. Nothing else on the device is reachable from it. */
class GameBridge(private val onNarrate: (String) -> Unit) {
    @JavascriptInterface
    fun narrate(text: String) = onNarrate(text)

    @JavascriptInterface
    fun ended(score: Double, seconds: Double) {
    }
}

private val MIME = mapOf(
    "html" to "text/html", "js" to "text/javascript", "wasm" to "application/wasm",
    "png" to "image/png", "json" to "application/json", "css" to "text/css",
    "pck" to "application/octet-stream",
)

private fun notFound() = WebResourceResponse(
    "text/plain", "utf-8", 404, "Not Found", emptyMap(), ByteArrayInputStream(ByteArray(0))
)

private fun serve(store: GameStore, game: GameInfo, mode: String, request: WebResourceRequest): WebResourceResponse {
    val url = request.url
    if (url.host != "game.local") return notFound()
    val path = url.path.orEmpty().trimStart('/').ifEmpty { "index.html" }
    val file = store.resolve(game, path) ?: return notFound()
    val ext = path.substringAfterLast('.', "")
    val mime = MIME[ext] ?: "application/octet-stream"
    if (path == "index.html") {
        val tag = "<script>window.DG_MODE=\"$mode\";</script>"
        var html = file.readText()
        val head = Regex("<head[^>]*>", RegexOption.IGNORE_CASE).find(html)
        html = if (head != null) html.substring(0, head.range.last + 1) + tag + html.substring(head.range.last + 1) else tag + html
        return WebResourceResponse(mime, "utf-8", ByteArrayInputStream(html.toByteArray()))
    }
    return WebResourceResponse(
        mime, if (mime.startsWith("text")) "utf-8" else null, 200, "OK",
        mapOf("Content-Length" to file.length().toString()), FileInputStream(file),
    )
}

/**
 * A game page in a WebView. [interactive] false makes it a pure wallpaper that ignores touches.
 */
@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
@Composable
fun GameView(
    store: GameStore,
    game: GameInfo,
    mode: String,
    interactive: Boolean,
    onNarrate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val holder = remember { arrayOfNulls<WebView>(1) }

    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_PAUSE -> holder[0]?.onPause()
                Lifecycle.Event.ON_RESUME -> holder[0]?.onResume()
                else -> {}
            }
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                holder[0] = this
                setBackgroundColor(AColor.BLACK)
                overScrollMode = View.OVER_SCROLL_NEVER
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                with(settings) {
                    javaScriptEnabled = true
                    domStorageEnabled = false
                    allowFileAccess = false
                    allowContentAccess = false
                    mediaPlaybackRequiresUserGesture = true
                }
                if (!interactive) {
                    isClickable = false
                    isFocusable = false
                    setOnTouchListener { _, _ -> true }
                }
                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse =
                        serve(store, game, mode, request)
                }
                addJavascriptInterface(GameBridge(onNarrate), "DeskGlass")
                loadUrl("https://game.local/index.html")
            }
        },
        onRelease = {
            holder[0] = null
            it.removeJavascriptInterface("DeskGlass")
            it.destroy()
        },
    )
}
