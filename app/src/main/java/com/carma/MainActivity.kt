package com.carma

import android.Manifest
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.carma.ui.theme.CarmaTheme

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
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Black
                ) {
                    CarmaWebView(
                        url = "https://carma-f4d4f.web.app",
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
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }
}

@Composable
fun CarmaWebView(
    url: String,
    onFileChooserRequest: (ValueCallback<Array<Uri>>?) -> Unit
) {
    AndroidView(
        modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
        factory = { context ->
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
