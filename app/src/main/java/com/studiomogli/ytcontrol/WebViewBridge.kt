package com.studiomogli.ytcontrol

object WebViewBridge {
    @Volatile
    var controlHandler: ((String) -> Unit)? = null

    @Volatile
    var exitHandler: (() -> Unit)? = null

    fun sendControl(action: String) {
        controlHandler?.invoke(action)
    }

    fun requestExit() {
        exitHandler?.invoke()
    }
}
