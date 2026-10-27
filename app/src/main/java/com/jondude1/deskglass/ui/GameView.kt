package com.jondude1.deskglass.ui

import android.annotation.SuppressLint
import android.graphics.Color as AColor
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebView
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

private fun withMode(html: String, mode: String): String {
    val tag = "<script>window.DG_MODE=\"$mode\";</script>"
    val head = Regex("<head[^>]*>", RegexOption.IGNORE_CASE).find(html)
    return if (head != null) {
        html.substring(0, head.range.last + 1) + tag + html.substring(head.range.last + 1)
    } else {
        tag + html
    }
}

/**
 * A game page in a WebView. [interactive] false makes it a pure wallpaper that ignores touches.
 */
@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
@Composable
fun GameView(
    html: String,
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
                    blockNetworkLoads = true
                    mediaPlaybackRequiresUserGesture = true
                }
                if (!interactive) {
                    isClickable = false
                    isFocusable = false
                    setOnTouchListener { _, _ -> true }
                }
                addJavascriptInterface(GameBridge(onNarrate), "DeskGlass")
                loadDataWithBaseURL("https://game.local/", withMode(html, mode), "text/html", "utf-8", null)
            }
        },
        onRelease = {
            holder[0] = null
            it.removeJavascriptInterface("DeskGlass")
            it.destroy()
        },
    )
}
