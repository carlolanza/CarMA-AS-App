// ============================================================
// Datei:    MainActivity.kt
// Autor:    Carlo
// Erstellt: 2026-03-24
// Version:  1.1
// Projekt:  CarMa
// Zweck:    WebView-Host; lädt CarMa Web-App, prüft auf neue APK-Version
//
// HISTORY:
// 2026-04-11  v1.2  Bug-Fix: WebView cached JS/CSS → Speicher löschen nötig nach Web-Deploy
//                   Lösung: LOAD_NO_CACHE — WebView ignoriert lokalen Cache, lädt immer frisch
// 2026-04-11  v1.1  Feature: Auto-Update
//                   Beim Start wird version.json auf Firebase Hosting abgefragt.
//                   Ist versionCode > BuildConfig.VERSION_CODE, erscheint ein
//                   blauer Banner mit Download-Button.
//                   NetGuard-Hinweis im Banner: Falls Download fehlschlägt,
//                   muss CarMa in NetGuard freigeschaltet werden.
//                   Download via DownloadManager → Installation via FileProvider.
// ============================================================
package com.carma

import android.Manifest
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import com.carma.ui.theme.CarmaTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.URL
import javax.net.ssl.HttpsURLConnection

private const val APP_URL      = "https://carma-f4d4f.web.app"
private const val VERSION_URL  = "$APP_URL/version.json"
private const val TAG          = "CarMaUpdate"

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

                    // Update-State
                    var updateApkUrl  by remember { mutableStateOf<String?>(null) }
                    var isDownloading by remember { mutableStateOf(false) }
                    var downloadId    by remember { mutableStateOf<Long?>(null) }
                    val context = LocalContext.current

                    // Versionscheck beim App-Start (Hintergrund-Thread)
                    LaunchedEffect(Unit) {
                        updateApkUrl = checkForUpdate()
                    }

                    // BroadcastReceiver: wartet auf Abschluss des DownloadManagers
                    val currentDownloadId = downloadId
                    if (currentDownloadId != null) {
                        DisposableEffect(currentDownloadId) {
                            val receiver = object : BroadcastReceiver() {
                                override fun onReceive(ctx: Context, intent: Intent) {
                                    val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                                    if (id != currentDownloadId) return

                                    val dm = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                                    val success = dm.query(DownloadManager.Query().setFilterById(id)).use { c ->
                                        c.moveToFirst() && c.getInt(
                                            c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
                                        ) == DownloadManager.STATUS_SUCCESSFUL
                                    }

                                    isDownloading = false
                                    downloadId    = null
                                    if (success) {
                                        installApk(ctx)
                                        updateApkUrl = null
                                    }
                                    // Bei Fehler: Banner bleibt → NetGuard-Hinweis sichtbar → Retry möglich
                                }
                            }
                            ContextCompat.registerReceiver(
                                context, receiver,
                                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                                ContextCompat.RECEIVER_EXPORTED  // DownloadManager ist System-Prozess
                            )
                            onDispose {
                                try { context.unregisterReceiver(receiver) } catch (_: Exception) {}
                            }
                        }
                    }

                    // Layout: Update-Banner oben (wenn vorhanden) + WebView darunter
                    Column(modifier = Modifier.fillMaxSize()) {
                        if (updateApkUrl != null) {
                            UpdateBanner(
                                modifier      = Modifier.statusBarsPadding(),
                                isDownloading = isDownloading,
                                onDownload    = {
                                    isDownloading = true
                                    downloadId    = startApkDownload(context, updateApkUrl!!)
                                },
                                onDismiss = { updateApkUrl = null }
                            )
                        }
                        CarmaWebView(
                            modifier = Modifier
                                .weight(1f)
                                .then(
                                    // statusBarsPadding nur wenn kein Banner oben ist
                                    if (updateApkUrl == null) Modifier.statusBarsPadding()
                                    else Modifier
                                )
                                .navigationBarsPadding(),
                            url = APP_URL,
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
    }

    private fun checkAndRequestPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }
}

// ---------------------------------------------------------------------------
// Update-Hilfsfunktionen
// ---------------------------------------------------------------------------

/**
 * Fragt version.json auf Firebase Hosting ab.
 * Gibt die APK-URL zurück wenn versionCode > BuildConfig.VERSION_CODE, sonst null.
 */
private suspend fun checkForUpdate(): String? = withContext(Dispatchers.IO) {
    try {
        val conn = URL(VERSION_URL).openConnection() as HttpsURLConnection
        conn.connectTimeout = 5_000
        conn.readTimeout    = 5_000
        // Cache umgehen: immer aktuelle Version laden
        conn.setRequestProperty("Cache-Control", "no-cache, no-store, must-revalidate")
        conn.setRequestProperty("Pragma", "no-cache")
        val json = JSONObject(conn.inputStream.bufferedReader().readText())
        conn.disconnect()
        val remoteCode = json.getInt("versionCode")
        val apkUrl     = json.getString("apkUrl")
        Log.d(TAG, "Remote versionCode=$remoteCode  Local=${BuildConfig.VERSION_CODE}")
        if (remoteCode > BuildConfig.VERSION_CODE) apkUrl else null
    } catch (e: Exception) {
        Log.w(TAG, "Versionscheck fehlgeschlagen: ${e.message}")
        null
    }
}

/**
 * Startet den APK-Download via DownloadManager.
 * Gibt die Download-ID zurück (wird für den BroadcastReceiver benötigt).
 */
private fun startApkDownload(context: Context, apkUrl: String): Long {
    // Alte APK löschen um Konflikte zu vermeiden
    File(context.getExternalFilesDir(null), "carma-update.apk").delete()

    val request = DownloadManager.Request(Uri.parse(apkUrl))
        .setTitle("CarMa Update")
        .setDescription("Neue Version wird heruntergeladen…")
        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        .setDestinationInExternalFilesDir(context, null, "carma-update.apk")
        .setMimeType("application/vnd.android.package-archive")
    val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    return dm.enqueue(request)
}

/**
 * Öffnet den System-Installer für die heruntergeladene APK.
 * Benötigt FileProvider (AndroidManifest.xml) und REQUEST_INSTALL_PACKAGES.
 */
private fun installApk(context: Context) {
    val file = File(context.getExternalFilesDir(null), "carma-update.apk")
    if (!file.exists()) { Log.e(TAG, "APK nicht gefunden: ${file.path}"); return }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
    context.startActivity(
        Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
    )
}

// ---------------------------------------------------------------------------
// Composables
// ---------------------------------------------------------------------------

/**
 * Blauer Banner: zeigt Update-Verfügbarkeit, Download-Button und NetGuard-Hinweis.
 * Bleibt bei Download-Fehler (NetGuard-Block) sichtbar → Retry möglich.
 */
@Composable
fun UpdateBanner(
    modifier: Modifier = Modifier,
    isDownloading: Boolean,
    onDownload: () -> Unit,
    onDismiss: () -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF1565C0))
            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text       = "Neue Version verfügbar",
                color      = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize   = 13.sp
            )
            Text(
                text       = "⚠ Falls Download fehlschlägt: NetGuard → CarMa freischalten",
                color      = Color(0xFFBBDEFB),
                fontSize   = 11.sp,
                lineHeight = 15.sp
            )
        }
        Spacer(Modifier.width(8.dp))
        if (isDownloading) {
            CircularProgressIndicator(
                color       = Color.White,
                modifier    = Modifier.size(20.dp),
                strokeWidth = 2.dp
            )
            Spacer(Modifier.width(12.dp))
        } else {
            TextButton(
                onClick         = onDownload,
                contentPadding  = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text("Download", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
        TextButton(
            onClick        = onDismiss,
            contentPadding = PaddingValues(4.dp),
            modifier       = Modifier.size(36.dp)
        ) {
            Text("✕", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun CarmaWebView(
    modifier: Modifier = Modifier,
    url: String,
    onFileChooserRequest: (ValueCallback<Array<Uri>>?) -> Unit
) {
    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory  = { context ->
            WebView(context).apply {
                setBackgroundColor(android.graphics.Color.BLACK)
                setLayerType(View.LAYER_TYPE_SOFTWARE, null)

                // Autofill-Dienst deaktivieren: verhindert nativen Indikator-Strich
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
                }

                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                // Kein lokaler HTTP-Cache: WebView lädt bei jedem Start frisch von Firebase.
                // Verhindert dass alte JS/CSS-Bundles angezeigt werden nach einem Web-Deploy.
                settings.cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE

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
