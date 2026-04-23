// ============================================================
// Datei:    MainActivity.kt
// Autor:    Carlo
// Erstellt: 2026-03-24
// Stand:    1.5
// Projekt:  CarMa
// Zweck:    WebView-Host; lädt CarMa Web-App
//
// HISTORY:
// 2026-04-23  v1.5  APK Auto-Update: getVersionCode() + installApk(url) im JS-Interface —
//                   Web-App prüft version.json und löst bei neuer Version Download+Install aus
// 2026-04-23  v1.4  closeApp() startet App neu via FLAG_ACTIVITY_CLEAR_TASK + finishAffinity()
// 2026-04-22  v1.3  Header: Stand statt Version; Versionsformat x.y.z → x.z
// 2026-04-21  v1.3  Feature: JS-Interface "Android.closeApp()"
// 2026-04-11  v1.2  Bug-Fix: LOAD_NO_CACHE — WebView ignoriert lokalen Cache
// 2026-04-11  v1.1  Feature: Auto-Update (entfernt in v1.3)
// ============================================================
package com.carma

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import com.carma.ui.theme.CarmaTheme
import java.io.File
import java.net.URL

private const val APP_URL = "https://carma-f4d4f.web.app"

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        Log.d("Carma", "Kamera-Berechtigung erteilt: $isGranted")
    }

    private var filePathCallback: ValueCallback<Array<Uri>>? = null

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        val result = if (uri != null) arrayOf(uri) else null
        filePathCallback?.onReceiveValue(result)
        filePathCallback = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        checkAndRequestPermissions()

        setContent {
            CarmaTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
                    CarmaWebView(
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                            .navigationBarsPadding(),
                        url = APP_URL,
                        onCloseApp = {
                            val intent = packageManager.getLaunchIntentForPackage(packageName)
                            intent?.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
                            intent?.let { startActivity(it) }
                            finishAffinity()
                        },
                        onFileChooserRequest = { callback ->
                            filePathCallback?.onReceiveValue(null)
                            filePathCallback = callback
                            filePickerLauncher.launch("image/*")
                        }
                    )
                }
            }
        }
    }

    private fun checkAndRequestPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }
}

@Composable
fun CarmaWebView(
    modifier: Modifier = Modifier,
    url: String,
    onCloseApp: () -> Unit = {},
    onFileChooserRequest: (ValueCallback<Array<Uri>>?) -> Unit
) {
    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory  = { context ->
            WebView(context).apply {
                setBackgroundColor(android.graphics.Color.BLACK)
                setLayerType(View.LAYER_TYPE_SOFTWARE, null)

                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
                }

                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                // Kein lokaler HTTP-Cache: WebView lädt bei jedem Start frisch von Firebase.
                settings.cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE

                // JS-Interface: Einheitliches Android-Bridge für Web-App
                addJavascriptInterface(object : Any() {
                    // SW-Update: Cache leeren + App neu starten
                    @JavascriptInterface
                    fun closeApp() {
                        android.os.Handler(android.os.Looper.getMainLooper()).post { onCloseApp() }
                    }

                    // APK-Version der installierten App zurückgeben
                    @JavascriptInterface
                    fun getVersionCode(): Int = BuildConfig.VERSION_CODE

                    // APK im Hintergrund herunterladen und System-Installer starten
                    @JavascriptInterface
                    fun installApk(apkUrl: String) {
                        Thread {
                            try {
                                val apkFile = File(context.getExternalFilesDir(null), "update.apk")
                                URL(apkUrl).openStream().use { input ->
                                    apkFile.outputStream().use { output -> input.copyTo(output) }
                                }
                                android.os.Handler(android.os.Looper.getMainLooper()).post {
                                    val uri = FileProvider.getUriForFile(
                                        context, "${context.packageName}.provider", apkFile
                                    )
                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(uri, "application/vnd.android.package-archive")
                                        flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                                Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                    context.startActivity(intent)
                                }
                            } catch (e: Exception) {
                                Log.e("AutoUpdate", "APK-Download fehlgeschlagen: ${e.message}")
                            }
                        }.start()
                    }
                }, "Android")

                WebView.setWebContentsDebuggingEnabled(true)

                webChromeClient = object : WebChromeClient() {
                    override fun onPermissionRequest(request: PermissionRequest?) {
                        val requestedResources = request?.resources ?: arrayOf()
                        val videoOnly = requestedResources.filter {
                            it == PermissionRequest.RESOURCE_VIDEO_CAPTURE
                        }.toTypedArray()
                        if (videoOnly.isNotEmpty()) {
                            Log.d("CarmaWebView", "Erlaube Zugriff auf: ${videoOnly.joinToString()}")
                            request?.grant(videoOnly)
                        } else {
                            Log.w("CarmaWebView", "Anfrage für nicht-Video-Ressource abgelehnt.")
                            request?.deny()
                        }
                    }

                    override fun onShowFileChooser(
                        webView: WebView?,
                        filePathCallback: ValueCallback<Array<Uri>>?,
                        fileChooserParams: FileChooserParams?
                    ): Boolean {
                        onFileChooserRequest(filePathCallback)
                        return true
                    }
                }

                webViewClient = object : WebViewClient() {
                    override fun onReceivedError(
                        view: WebView?, request: WebResourceRequest?, error: WebResourceError?
                    ) {
                        if (request?.isForMainFrame == true) {
                            Log.e("CarmaWebView", "Ladefehler: ${error?.description}")
                        }
                    }
                }

                loadUrl(url)
            }
        }
    )
}
