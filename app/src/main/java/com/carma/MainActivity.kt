package com.carma

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.webkit.PermissionRequest
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.carma.ui.theme.CarmaTheme

class MainActivity : ComponentActivity() {

    // Launcher für nur EINE Berechtigung (Kamera)
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        Log.d("Carma", "Kamera-Berechtigung erteilt: $isGranted")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Edge-to-edge aktivieren und Insets über Compose verwalten
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Nur Kamera-Berechtigung beim Start prüfen
        checkAndRequestPermissions()

        setContent {
            CarmaTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Black
                ) {
                    CarmaWebView(url = "https://carma-f4d4f.web.app")
                }
            }
        }
    }

    private fun checkAndRequestPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }
}

@Composable
fun CarmaWebView(url: String) {
    AndroidView(
        // statusBarsPadding: WebView startet unterhalb der Statusleiste
        // navigationBarsPadding: WebView endet oberhalb der Navigationsleiste
        modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
        factory = { context ->
            WebView(context).apply {
                setBackgroundColor(android.graphics.Color.BLACK)

                // Software-Rendering: Video-Element wird im normalen DOM-Layer
                // gerendert statt als SurfaceView — behebt das Überdeckungs-Problem
                setLayerType(View.LAYER_TYPE_SOFTWARE, null)

                // Autofill-Dienst deaktivieren: verhindert nativen Indikator-Strich
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
                }

                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true

                // Erlaubt Video-Wiedergabe ohne Interaktion
                settings.mediaPlaybackRequiresUserGesture = false

                WebView.setWebContentsDebuggingEnabled(true)

                webChromeClient = object : WebChromeClient() {
                    override fun onPermissionRequest(request: PermissionRequest?) {
                        // Wir filtern die Anfrage: Nur Video (Kamera) wird erlaubt
                        val requestedResources = request?.resources ?: arrayOf()
                        val videoOnly = requestedResources.filter {
                            it == PermissionRequest.RESOURCE_VIDEO_CAPTURE
                        }.toTypedArray()

                        if (videoOnly.isNotEmpty()) {
                            Log.d("CarmaWebView", "Erlaube Zugriff auf: ${videoOnly.joinToString()}")
                            request?.grant(videoOnly)
                        } else {
                            // Falls Mikrofon oder anderes angefragt wird, lehnen wir ab
                            Log.w("CarmaWebView", "Anfrage für nicht-Video-Ressource abgelehnt.")
                            request?.deny()
                        }
                    }
                }

                webViewClient = object : WebViewClient() {
                    override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
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
