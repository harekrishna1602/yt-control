package com.studiomogli.ytcontrol

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

class MainActivity : AppCompatActivity() {

    companion object {
        private const val YT_HOME_URL = "https://m.youtube.com"
        private const val YT_STUDIO_URL = "https://studio.youtube.com"
        private const val GEMINI_APP_URL = "https://gemini.google.com/app"
        private const val STUDIO_MOGLI_REFER_URL = "https://studiomogli.com/refer-and-earn"
        private const val STUDIO_MOGLI_HOME_URL = "https://studiomogli.com"
        private const val GITHUB_LATEST_RELEASE_API =
            "https://api.github.com/repos/harekrishna1602/yt-control/releases/latest"

        private const val PREFS_NAME = "ytcontrol_app_prefs"
        private const val KEY_LAST_SEEN_VERSION_CODE = "last_seen_version_code"

        private val DOCUMENT_START_ORIGINS = setOf(
            "https://m.youtube.com",
            "https://www.youtube.com",
            "https://youtube.com",
            "https://studio.youtube.com"
        )

        private val AUTH_HOSTS = listOf(
            "accounts.google.com",
            "accounts.youtube.com",
            "myaccount.google.com"
        )

        private val ALLOWED_HOST_SUFFIXES = listOf(
            "m.youtube.com",
            "youtube.com",
            "studio.youtube.com",
            "youtu.be",
            "gemini.google.com",
            "consent.google.com",
            "consent.youtube.com",
            "gds.google.com",
            "googleusercontent.com",
            "gstatic.com"
        )
    }

    private lateinit var webView: BackgroundWebView
    private lateinit var envManager: EnvironmentManager

    private lateinit var btnEnvSwitch: TextView
    private lateinit var txtTimerBadge: TextView
    private lateinit var btnEnvSettings: TextView
    private lateinit var btnUpdateApp: TextView
    private lateinit var lockOverlay: LinearLayout
    private lateinit var txtLockTitle: TextView
    private lateinit var txtLockSub: TextView
    private lateinit var btnSwitchFromLock: Button
    private lateinit var whitelistBar: LinearLayout
    private lateinit var whitelistChipsContainer: LinearLayout

    // Creator Mode UI
    private lateinit var creatorBar: LinearLayout
    private lateinit var geminiDrawer: LinearLayout
    private lateinit var txtGeminiDrawerTitle: TextView
    private lateinit var geminiWebView: WebView
    private lateinit var geminiApiResultScroll: ScrollView
    private lateinit var txtGeminiApiResult: TextView
    private lateinit var btnPastePrompt: TextView
    private lateinit var btnCopyApiResult: TextView
    private lateinit var btnCloseGeminiDrawer: TextView

    private var pendingGeminiPrompt: String = ""
    private var geminiWebLoaded: Boolean = false

    private var updateDownloadUrl: String? = null
    private var serviceStarted = false
    private var isExiting = false
    private var isForeground = false

    private val timerHandler = Handler(Looper.getMainLooper())
    private val timerRunnable = object : Runnable {
        override fun run() {
            if (isForeground && !isExiting) {
                val active = envManager.getActiveProfile()
                if (active.dailyLimitMinutes > 0 && !envManager.isProfileLocked(active)) {
                    envManager.incrementUsedSeconds(active.id, 1)
                    updateTimerAndLockUi()
                }
            }
            timerHandler.postDelayed(this, 1000L)
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    private val loginLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        CookieManager.getInstance().flush()
        if (result.resultCode == Activity.RESULT_OK && ::webView.isInitialized) {
            webView.reload()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        envManager = EnvironmentManager(this)
        requestNotificationPermissionIfNeeded()

        webView = findViewById(R.id.ytWebView)
        btnEnvSwitch = findViewById(R.id.btnEnvSwitch)
        txtTimerBadge = findViewById(R.id.txtTimerBadge)
        btnEnvSettings = findViewById(R.id.btnEnvSettings)
        btnUpdateApp = findViewById(R.id.btnUpdateApp)
        lockOverlay = findViewById(R.id.lockOverlay)
        txtLockTitle = findViewById(R.id.txtLockTitle)
        txtLockSub = findViewById(R.id.txtLockSub)
        btnSwitchFromLock = findViewById(R.id.btnSwitchFromLock)
        whitelistBar = findViewById(R.id.whitelistBar)
        whitelistChipsContainer = findViewById(R.id.whitelistChipsContainer)

        creatorBar = findViewById(R.id.creatorBar)
        geminiDrawer = findViewById(R.id.geminiDrawer)
        txtGeminiDrawerTitle = findViewById(R.id.txtGeminiDrawerTitle)
        geminiWebView = findViewById(R.id.geminiWebView)
        geminiApiResultScroll = findViewById(R.id.geminiApiResultScroll)
        txtGeminiApiResult = findViewById(R.id.txtGeminiApiResult)
        btnPastePrompt = findViewById(R.id.btnPastePrompt)
        btnCopyApiResult = findViewById(R.id.btnCopyApiResult)
        btnCloseGeminiDrawer = findViewById(R.id.btnCloseGeminiDrawer)

        setupTopBar()
        setupCreatorBarAndDrawer()
        configureWebView()
        configureGeminiWebView()
        registerBridgeHandlers()
        registerBackNavigation()
        refreshEnvironmentUi()

        if (savedInstanceState == null) {
            webView.loadUrl(YT_HOME_URL)
            showWelcomePromptIfFirstRunOrUpdated()
            checkForGitHubUpdateSilently()
        }
    }

    private fun setupTopBar() {
        val btnCredits: TextView = findViewById(R.id.btnStudioMogliCredits)

        btnEnvSwitch.setOnClickListener {
            val next = envManager.toggleNextProfile()
            refreshEnvironmentUi()
            pushEnvironmentConfigToWebView()
            Toast.makeText(this, "Switched to ${next.name} mode", Toast.LENGTH_SHORT).show()
        }

        btnEnvSettings.setOnClickListener {
            val active = envManager.getActiveProfile()
            if (active.mode == EnvironmentProfile.MODE_CREATOR) {
                showGeminiSettingsDialog()
            } else {
                showEnvironmentManagerDialog()
            }
        }

        btnCredits.setOnClickListener { showStudioMogliCreditsDialog() }

        btnUpdateApp.setOnClickListener {
            val url = updateDownloadUrl
            if (!url.isNullOrBlank()) openExternalBrowser(url)
        }

        btnSwitchFromLock.setOnClickListener {
            val next = envManager.toggleNextProfile()
            refreshEnvironmentUi()
            pushEnvironmentConfigToWebView()
            Toast.makeText(this, "Switched to ${next.name} mode", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupCreatorBarAndDrawer() {
        findViewById<TextView>(R.id.btnCreatorStudio).setOnClickListener {
            val currentUrl = webView.url ?: ""
            if (currentUrl.contains("studio.youtube.com")) {
                webView.settings.userAgentString = buildCleanMobileUa()
                webView.loadUrl(YT_HOME_URL)
                Toast.makeText(this, "Opened YouTube Feed (Outlier Radar Active)", Toast.LENGTH_SHORT).show()
            } else {
                webView.settings.userAgentString = buildDesktopChromeUa()
                webView.loadUrl(YT_STUDIO_URL)
                Toast.makeText(this, "Opened YouTube Studio Dashboard", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<TextView>(R.id.btnCreatorRadar).setOnClickListener {
            triggerCreatorAiTool("CHANNEL_RADAR")
        }

        findViewById<TextView>(R.id.btnCreatorComments).setOnClickListener {
            triggerCreatorAiTool("MINE_COMMENTS")
        }

        findViewById<TextView>(R.id.btnCreatorHooks).setOnClickListener {
            triggerCreatorAiTool("HOOKS_AUDIT")
        }

        findViewById<TextView>(R.id.btnCreatorAiSettings).setOnClickListener {
            showGeminiSettingsDialog()
        }

        btnCloseGeminiDrawer.setOnClickListener {
            geminiDrawer.visibility = View.GONE
        }

        btnPastePrompt.setOnClickListener {
            if (pendingGeminiPrompt.isNotBlank()) {
                copyToClipboard("YT Control Prompt", pendingGeminiPrompt)
                injectPromptIntoGeminiWebView(pendingGeminiPrompt)
                Toast.makeText(this, "Prompt sent & copied to clipboard", Toast.LENGTH_SHORT).show()
            }
        }

        btnCopyApiResult.setOnClickListener {
            copyToClipboard("YT Control AI Result", txtGeminiApiResult.text.toString())
            Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT).show()
        }
    }

    private fun triggerCreatorAiTool(action: String) {
        webView.evaluateJavascript("window.__ytcScrapeContext ? window.__ytcScrapeContext() : '{}'") { raw ->
            val unescaped = try {
                if (raw != null && raw.startsWith("\"") && raw.endsWith("\"")) {
                    JSONObject("{\"v\":$raw}").getString("v")
                } else {
                    raw ?: "{}"
                }
            } catch (e: Exception) {
                "{}"
            }
            launchGeminiWithContext(action, unescaped)
        }
    }

    private fun launchGeminiWithContext(action: String, contextJson: String) {
        val (title, prompt) = CreatorPromptBuilder.buildPrompt(action, contextJson)
        pendingGeminiPrompt = prompt
        copyToClipboard("YT Control Prompt", prompt)

        txtGeminiDrawerTitle.text = title
        geminiDrawer.visibility = View.VISIBLE

        val useApi = envManager.isUseApiKeyEnabled()
        val apiKey = envManager.getGeminiApiKey()

        if (useApi && apiKey.isNotBlank()) {
            geminiWebView.visibility = View.GONE
            btnPastePrompt.visibility = View.GONE
            geminiApiResultScroll.visibility = View.VISIBLE
            btnCopyApiResult.visibility = View.VISIBLE
            txtGeminiApiResult.text = "Analyzing with Gemini API..."
            callGeminiRestApi(apiKey, prompt)
        } else {
            geminiApiResultScroll.visibility = View.GONE
            btnCopyApiResult.visibility = View.GONE
            geminiWebView.visibility = View.VISIBLE
            btnPastePrompt.visibility = View.VISIBLE
            if (!geminiWebLoaded) {
                geminiWebLoaded = true
                geminiWebView.loadUrl(GEMINI_APP_URL)
            } else {
                injectPromptIntoGeminiWebView(prompt)
            }
        }
    }

    private fun callGeminiRestApi(apiKey: String, prompt: String) {
        Thread {
            var conn: HttpURLConnection? = null
            try {
                val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey"
                conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 15000
                    readTimeout = 30000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                }

                val bodyJson = JSONObject().apply {
                    put("contents", JSONArray().put(
                        JSONObject().put("parts", JSONArray().put(
                            JSONObject().put("text", prompt)
                        ))
                    ))
                }

                OutputStreamWriter(conn.outputStream).use { it.write(bodyJson.toString()) }

                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val respText = stream.bufferedReader().use { it.readText() }

                val output = if (code in 200..299) {
                    val json = JSONObject(respText)
                    json.optJSONArray("candidates")
                        ?.optJSONObject(0)
                        ?.optJSONObject("content")
                        ?.optJSONArray("parts")
                        ?.optJSONObject(0)
                        ?.optString("text", "No response returned.")
                        ?: "No response returned."
                } else {
                    "Gemini API Error ($code):\n$respText"
                }

                runOnUiThread { txtGeminiApiResult.text = output }
            } catch (e: Exception) {
                runOnUiThread { txtGeminiApiResult.text = "Network error: ${e.message}" }
            } finally {
                conn?.disconnect()
            }
        }.start()
    }

    private fun injectPromptIntoGeminiWebView(prompt: String) {
        val escapedPrompt = JSONObject.quote(prompt)
        val js = "(function(){" +
            "var p=" + escapedPrompt + ";" +
            "var box=document.querySelector('rich-textarea div[contenteditable=\"true\"], div[contenteditable=\"true\"][role=\"textbox\"], textarea');" +
            "if(!box)return;" +
            "box.focus();" +
            "if(box.tagName==='TEXTAREA'){box.value=p;}else{box.innerText=p;}" +
            "box.dispatchEvent(new Event('input',{bubbles:true}));" +
            "setTimeout(function(){" +
            "var sendBtn=document.querySelector('button[aria-label*=\"Send\"], button.send-button, button[data-test-id=\"send-button\"]');" +
            "if(sendBtn&&!sendBtn.disabled)sendBtn.click();" +
            "},400);" +
            "})();"
        geminiWebView.evaluateJavascript(js, null)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureGeminiWebView() {
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(geminiWebView, true)

        geminiWebView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            userAgentString = buildCleanMobileUa()
        }

        geminiWebView.webChromeClient = WebChromeClient()
        geminiWebView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val uri = request?.url ?: return false
                val host = uri.host?.lowercase() ?: return false
                if (AUTH_HOSTS.any { host == it || host.endsWith(".$it") }) {
                    launchIsolatedGoogleSignIn(uri.toString())
                    return true
                }
                return false
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                CookieManager.getInstance().flush()
                if (url != null && url.contains("gemini.google.com") && pendingGeminiPrompt.isNotBlank()) {
                    view?.postDelayed({ injectPromptIntoGeminiWebView(pendingGeminiPrompt) }, 1200L)
                }
            }
        }
    }

    private fun showGeminiSettingsDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_gemini_settings, null)
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val chkUseApi: CheckBox = dialogView.findViewById(R.id.chkUseApiKey)
        val edtApiKey: EditText = dialogView.findViewById(R.id.edtGeminiApiKey)
        val btnSave: Button = dialogView.findViewById(R.id.btnSaveGeminiSettings)
        val btnClose: TextView = dialogView.findViewById(R.id.btnCloseGeminiSettings)

        chkUseApi.isChecked = envManager.isUseApiKeyEnabled()
        edtApiKey.setText(envManager.getGeminiApiKey())

        btnSave.setOnClickListener {
            envManager.saveGeminiSettings(chkUseApi.isChecked, edtApiKey.text.toString())
            dialog.dismiss()
            val modeLabel = if (chkUseApi.isChecked) "Gemini API Key" else "Logged-in Gemini Account"
            Toast.makeText(this, "AI Mode: $modeLabel", Toast.LENGTH_SHORT).show()
        }
        btnClose.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun copyToClipboard(label: String, text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    }

    private fun refreshEnvironmentUi() {
        val active = envManager.getActiveProfile()
        val modeTag = when (active.mode) {
            EnvironmentProfile.MODE_WHITELIST -> "Only"
            EnvironmentProfile.MODE_CREATOR -> "Pro"
            else -> "Block"
        }
        btnEnvSwitch.text = "${active.name} ($modeTag)"
        btnEnvSettings.text = if (active.mode == EnvironmentProfile.MODE_CREATOR) "AI Key" else "Edit"

        creatorBar.visibility = if (active.mode == EnvironmentProfile.MODE_CREATOR) View.VISIBLE else View.GONE
        updateTimerAndLockUi()
        renderWhitelistQuickBar(active)
    }

    private fun renderWhitelistQuickBar(profile: EnvironmentProfile) {
        whitelistChipsContainer.removeAllViews()
        if (profile.mode == EnvironmentProfile.MODE_WHITELIST && profile.channels.isNotEmpty()) {
            whitelistBar.visibility = View.VISIBLE
            profile.channels.forEach { ch ->
                val chip = TextView(this).apply {
                    text = ch
                    textSize = 11f
                    setTextColor(0xFFFFFFFF.toInt())
                    setBackgroundResource(R.drawable.bg_chip_dark)
                    setPadding(24, 10, 24, 10)
                    val lp = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    lp.marginEnd = 12
                    layoutParams = lp
                    setOnClickListener { openChannelInWebView(ch) }
                }
                whitelistChipsContainer.addView(chip)
            }
        } else {
            whitelistBar.visibility = View.GONE
        }
    }

    private fun openChannelInWebView(channelRaw: String) {
        val trimmed = channelRaw.trim()
        val url = if (trimmed.startsWith("@")) {
            "https://m.youtube.com/$trimmed/videos"
        } else {
            "https://m.youtube.com/results?search_query=${Uri.encode(trimmed)}"
        }
        webView.loadUrl(url)
    }

    private fun updateTimerAndLockUi() {
        val active = envManager.getActiveProfile()
        if (active.dailyLimitMinutes <= 0) {
            txtTimerBadge.visibility = View.GONE
            lockOverlay.visibility = View.GONE
            return
        }

        val remaining = envManager.getRemainingSecondsToday(active)
        val mins = remaining / 60
        val secs = remaining % 60
        txtTimerBadge.text = String.format(Locale.US, "%02d:%02d", mins, secs)
        txtTimerBadge.visibility = View.VISIBLE

        if (remaining <= 0) {
            lockOverlay.visibility = View.VISIBLE
            txtLockTitle.text = "${active.name} Time Limit Reached"
            txtLockSub.text = "You used your ${active.dailyLimitMinutes} min daily allowance for ${active.name}. Switch to another environment or come back tomorrow."
            webView.evaluateJavascript("window.__ytcControl('pause');", null)
        } else {
            lockOverlay.visibility = View.GONE
        }
    }

    private fun pushEnvironmentConfigToWebView() {
        if (!::webView.isInitialized) return
        val active = envManager.getActiveProfile()
        val json = envManager.buildConfigJsonForJs(active)
        val escaped = JSONObject.quote(json)
        webView.evaluateJavascript("if(window.__ytcApplyConfig){window.__ytcApplyConfig($escaped);}", null)
    }

    private fun showEnvironmentManagerDialog() {
        val active = envManager.getActiveProfile()
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_environment_settings, null)
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val txtTitle: TextView = dialogView.findViewById(R.id.txtEnvDialogTitle)
        val radioGroup: RadioGroup = dialogView.findViewById(R.id.rgEnvMode)
        val rbWhitelist: RadioButton = dialogView.findViewById(R.id.rbWhitelist)
        val rbBlacklist: RadioButton = dialogView.findViewById(R.id.rbBlacklist)
        val edtTimerMinutes: EditText = dialogView.findViewById(R.id.edtDailyLimit)
        val edtAddChannel: EditText = dialogView.findViewById(R.id.edtAddChannel)
        val btnAddChannel: Button = dialogView.findViewById(R.id.btnAddChannel)
        val channelsListContainer: LinearLayout = dialogView.findViewById(R.id.channelsListContainer)
        val btnSave: Button = dialogView.findViewById(R.id.btnSaveEnv)
        val btnClose: TextView = dialogView.findViewById(R.id.btnCloseEnv)

        txtTitle.text = "Configure: ${active.name}"
        if (active.mode == EnvironmentProfile.MODE_WHITELIST) rbWhitelist.isChecked = true
        else rbBlacklist.isChecked = true
        edtTimerMinutes.setText(active.dailyLimitMinutes.toString())

        fun renderChannelRows() {
            channelsListContainer.removeAllViews()
            val fresh = envManager.getActiveProfile()
            if (fresh.channels.isEmpty()) {
                val empty = TextView(this).apply {
                    text = "No channels added yet. Add a @handle or channel name above."
                    textSize = 12f
                    setTextColor(0xFF888888.toInt())
                }
                channelsListContainer.addView(empty)
                return
            }
            fresh.channels.forEach { ch ->
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(0, 8, 0, 8)
                }
                val nameTv = TextView(this).apply {
                    text = ch
                    textSize = 13f
                    setTextColor(0xFFFFFFFF.toInt())
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
                val removeTv = TextView(this).apply {
                    text = "Remove"
                    textSize = 12f
                    setTextColor(0xFFFF5252.toInt())
                    setPadding(16, 4, 4, 4)
                    setOnClickListener {
                        envManager.removeChannelFromActive(ch)
                        renderChannelRows()
                        refreshEnvironmentUi()
                        pushEnvironmentConfigToWebView()
                    }
                }
                row.addView(nameTv)
                row.addView(removeTv)
                channelsListContainer.addView(row)
            }
        }

        renderChannelRows()

        btnAddChannel.setOnClickListener {
            val ch = edtAddChannel.text.toString().trim()
            if (ch.isNotEmpty()) {
                envManager.addChannelToActive(ch)
                edtAddChannel.setText("")
                renderChannelRows()
                refreshEnvironmentUi()
                pushEnvironmentConfigToWebView()
            }
        }

        btnSave.setOnClickListener {
            val fresh = envManager.getActiveProfile()
            fresh.mode = if (radioGroup.checkedRadioButtonId == R.id.rbWhitelist) {
                EnvironmentProfile.MODE_WHITELIST
            } else {
                EnvironmentProfile.MODE_BLACKLIST
            }
            fresh.dailyLimitMinutes = edtTimerMinutes.text.toString().toIntOrNull()?.coerceAtLeast(0) ?: 0
            envManager.updateProfile(fresh)
            refreshEnvironmentUi()
            pushEnvironmentConfigToWebView()
            dialog.dismiss()
            Toast.makeText(this, "${fresh.name} saved", Toast.LENGTH_SHORT).show()
        }

        btnClose.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun getCurrentVersionCode(): Long {
        return try {
            val pkgInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, 0)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) pkgInfo.longVersionCode
            else @Suppress("DEPRECATION") pkgInfo.versionCode.toLong()
        } catch (e: Exception) {
            2L
        }
    }

    private fun getCurrentVersionName(): String {
        return try {
            val pkgInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, 0)
            }
            pkgInfo.versionName ?: "1.1.0"
        } catch (e: Exception) {
            "1.1.0"
        }
    }

    private fun showWelcomePromptIfFirstRunOrUpdated() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentCode = getCurrentVersionCode()
        val lastSeenCode = prefs.getLong(KEY_LAST_SEEN_VERSION_CODE, -1L)
        if (lastSeenCode != currentCode) {
            prefs.edit().putLong(KEY_LAST_SEEN_VERSION_CODE, currentCode).apply()
            showTrustWelcomeDialog()
        }
    }

    private fun showTrustWelcomeDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_trust_welcome, null)
        val dialog = AlertDialog.Builder(this).setView(dialogView).setCancelable(true).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialogView.findViewById<Button>(R.id.btnTrustGotIt).setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun showStudioMogliCreditsDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_studio_mogli, null)
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogView.findViewById<Button>(R.id.btnReferAndEarn).setOnClickListener {
            openExternalBrowser(STUDIO_MOGLI_REFER_URL)
            dialog.dismiss()
        }
        dialogView.findViewById<Button>(R.id.btnReadTrustGuarantee).setOnClickListener {
            dialog.dismiss()
            showTrustWelcomeDialog()
        }
        dialogView.findViewById<Button>(R.id.btnVisitStudioMogli).setOnClickListener {
            openExternalBrowser(STUDIO_MOGLI_HOME_URL)
            dialog.dismiss()
        }
        dialogView.findViewById<TextView>(R.id.btnCloseCredits).setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun checkForGitHubUpdateSilently() {
        Thread {
            var conn: HttpURLConnection? = null
            try {
                val url = URL(GITHUB_LATEST_RELEASE_API)
                conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 5000
                    readTimeout = 5000
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("User-Agent", "YTControl-StudioMogli")
                }
                if (conn.responseCode != HttpURLConnection.HTTP_OK) return@Thread

                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(body)
                val latestTag = json.optString("tag_name", "").trim()
                if (latestTag.isEmpty() || !isNewerVersion(latestTag, getCurrentVersionName())) return@Thread

                var downloadUrl = json.optString("html_url", "")
                val assets = json.optJSONArray("assets")
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.optJSONObject(i) ?: continue
                        val name = asset.optString("name", "").lowercase()
                        val directUrl = asset.optString("browser_download_url", "")
                        if (name.endsWith(".apk") && directUrl.isNotBlank()) {
                            downloadUrl = directUrl
                            break
                        }
                    }
                }

                if (downloadUrl.isNotBlank()) {
                    runOnUiThread {
                        if (!isFinishing && !isDestroyed) {
                            updateDownloadUrl = downloadUrl
                            btnUpdateApp.visibility = View.VISIBLE
                        }
                    }
                }
            } catch (_: Exception) {
            } finally {
                conn?.disconnect()
            }
        }.start()
    }

    private fun isNewerVersion(latestRaw: String, currentRaw: String): Boolean {
        val latestParts = latestRaw.removePrefix("v").removePrefix("V")
            .split(".").map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        val currentParts = currentRaw.removePrefix("v").removePrefix("V")
            .split(".").map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        val maxLen = maxOf(latestParts.size, currentParts.size)
        for (i in 0 until maxLen) {
            val l = latestParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (l > c) return true
            if (l < c) return false
        }
        return false
    }

    private fun launchIsolatedGoogleSignIn(authUrl: String) {
        val intent = Intent(this, LoginActivity::class.java).apply {
            putExtra(LoginActivity.EXTRA_AUTH_URL, authUrl)
        }
        loginLauncher.launch(intent)
    }

    private fun openExternalBrowser(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            Toast.makeText(this, "Unable to open link", Toast.LENGTH_SHORT).show()
        }
    }

    private fun buildCleanMobileUa(): String {
        val realDefaultUa = WebSettings.getDefaultUserAgent(this)
        return realDefaultUa
            .replace("; wv", "")
            .replace(Regex("Version/\\d+\\.\\d+\\s*"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun buildDesktopChromeUa(): String {
        val defaultUa = WebSettings.getDefaultUserAgent(this)
        val chromeMatch = Regex("Chrome/[0-9.]+").find(defaultUa)?.value ?: "Chrome/134.0.0.0"
        return "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) $chromeMatch Safari/537.36"
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView() {
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
            setSupportMultipleWindows(false)
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            userAgentString = buildCleanMobileUa()
        }

        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(
                webView,
                JsScripts.DOCUMENT_START_SCRIPT,
                DOCUMENT_START_ORIGINS
            )
        }

        webView.addJavascriptInterface(AndroidBridge(), "AndroidBridge")
        webView.webChromeClient = WebChromeClient()

        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                return AdBlocker.intercept(request) ?: super.shouldInterceptRequest(view, request)
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val uri = request?.url ?: return false
                return handleNavigation(uri)
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                if (isYouTubeHost(url)) {
                    view?.evaluateJavascript(JsScripts.DOCUMENT_START_SCRIPT, null)
                    view?.evaluateJavascript(JsScripts.RUNTIME_SCRIPT, null)
                    pushEnvironmentConfigToWebView()
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (isYouTubeHost(url)) {
                    view?.evaluateJavascript(JsScripts.RUNTIME_SCRIPT, null)
                    pushEnvironmentConfigToWebView()
                }
                CookieManager.getInstance().flush()
            }
        }
    }

    private fun isYouTubeHost(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val host = Uri.parse(url).host?.lowercase() ?: return false
        return host == "m.youtube.com" || host == "youtube.com" || host.endsWith(".youtube.com")
    }

    private fun handleNavigation(uri: Uri): Boolean {
        val scheme = uri.scheme?.lowercase() ?: return true
        if (scheme != "https" && scheme != "http") return true

        val host = uri.host?.lowercase() ?: return true
        val path = uri.path?.lowercase() ?: ""

        if (host == "i.ytimg.com") {
            openExternalBrowser(uri.toString())
            return true
        }

        if (path.startsWith("/shorts")) {
            Toast.makeText(this, "Shorts blocked by YT Control", Toast.LENGTH_SHORT).show()
            return true
        }

        if (AUTH_HOSTS.any { host == it || host.endsWith(".$it") }) {
            launchIsolatedGoogleSignIn(uri.toString())
            return true
        }

        val isAllowed = ALLOWED_HOST_SUFFIXES.any { suffix ->
            host == suffix || host.endsWith(".$suffix")
        }
        return !isAllowed
    }

    private fun registerBridgeHandlers() {
        WebViewBridge.controlHandler = { action ->
            runOnUiThread {
                if (!isExiting && ::webView.isInitialized) {
                    webView.evaluateJavascript("window.__ytcControl('$action');", null)
                }
            }
        }

        WebViewBridge.exitHandler = {
            runOnUiThread {
                isExiting = true
                if (::webView.isInitialized) {
                    webView.allowBackgroundPlayback = false
                    webView.stopLoading()
                    webView.loadUrl("about:blank")
                }
                finishAndRemoveTask()
            }
        }
    }

    private fun registerBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (geminiDrawer.visibility == View.VISIBLE) {
                    geminiDrawer.visibility = View.GONE
                } else if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    moveTaskToBack(true)
                }
            }
        })
    }

    private fun updatePlaybackService(isPlaying: Boolean, title: String, artist: String) {
        if (!isPlaying && !serviceStarted) return
        serviceStarted = true
        val intent = Intent(this, PlaybackService::class.java).apply {
            action = PlaybackService.ACTION_UPDATE_STATE
            putExtra(PlaybackService.EXTRA_IS_PLAYING, isPlaying)
            putExtra(PlaybackService.EXTRA_TITLE, title)
            putExtra(PlaybackService.EXTRA_ARTIST, artist)
        }
        ContextCompat.startForegroundService(this, intent)
    }

    override fun onResume() {
        super.onResume()
        isForeground = true
        webView.onResume()
        webView.resumeTimers()
        updateTimerAndLockUi()
        timerHandler.removeCallbacks(timerRunnable)
        timerHandler.postDelayed(timerRunnable, 1000L)
    }

    override fun onPause() {
        isForeground = false
        timerHandler.removeCallbacks(timerRunnable)
        super.onPause()
    }

    override fun onDestroy() {
        timerHandler.removeCallbacks(timerRunnable)
        WebViewBridge.controlHandler = null
        WebViewBridge.exitHandler = null
        if (isExiting) {
            stopService(Intent(this, PlaybackService::class.java))
            webView.destroy()
            geminiWebView.destroy()
        }
        super.onDestroy()
    }

    inner class AndroidBridge {
        @JavascriptInterface
        fun onPlaybackStateChanged(isPlaying: Boolean, title: String, artist: String) {
            runOnUiThread {
                if (!isExiting) updatePlaybackService(isPlaying, title, artist)
            }
        }

        @JavascriptInterface
        fun onCreatorActionRequested(action: String, contextJson: String) {
            runOnUiThread {
                if (!isExiting) launchGeminiWithContext(action, contextJson)
            }
        }
    }
}
