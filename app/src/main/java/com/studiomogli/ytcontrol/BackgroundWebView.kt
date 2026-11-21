package com.studiomogli.ytcontrol

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.webkit.WebView

class BackgroundWebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.webViewStyle
) : WebView(context, attrs, defStyleAttr) {

    var allowBackgroundPlayback: Boolean = true

    override fun onWindowVisibilityChanged(visibility: Int) {
        if (allowBackgroundPlayback && visibility != View.VISIBLE) {
            super.onWindowVisibilityChanged(View.VISIBLE)
        } else {
            super.onWindowVisibilityChanged(visibility)
        }
    }
}
