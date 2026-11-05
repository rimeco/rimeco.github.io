@file:OptIn(ExperimentalMaterial3Api::class)

package be.rimeco.weekmenu.ui

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Ingebouwde browser om in te loggen bij een site met een account (bv. Libelle Lekker).
 * De app slaat je wachtwoord niet op: enkel de sessie-cookies van de browser worden gebruikt om pagina's op te halen.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SiteLoginScreen(vm: AppViewModel, url: String) {
    var web by remember { mutableStateOf<WebView?>(null) }
    BackHandler(enabled = web?.canGoBack() == true) { web?.goBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Inloggen") },
                navigationIcon = { BackButton { vm.pop() } },
                actions = {
                    TextButton({
                        runCatching { CookieManager.getInstance().flush() }
                        vm.pop()
                    }) { Text("Klaar") }
                },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            Text(
                "Log in met je eigen account en tik daarna rechtsboven op Klaar.",
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        webViewClient = WebViewClient()
                        loadUrl(url)
                        web = this
                    }
                },
                onRelease = { it.destroy() },
                modifier = Modifier.weight(1f).fillMaxSize(),
            )
        }
    }
}
