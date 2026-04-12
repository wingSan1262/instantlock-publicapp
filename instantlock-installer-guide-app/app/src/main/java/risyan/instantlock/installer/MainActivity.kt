package risyan.instantlock.installer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import risyan.instantlock.installer.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    InstallerGuideScreen()
                }
            }
        }
    }
}

@Composable
fun InstallerGuideScreen() {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding() // Add padding for status and navigation bars
            .verticalScroll(scrollState)
            .padding(24.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = "Install InstantLock (Accessibility App)",
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text("This guide helps you install InstantLock, a custom accessibility app. Play Protect may block this app, so follow these steps carefully.")
        Spacer(modifier = Modifier.height(16.dp))
        Text("1. Turn off Google Play Protect (scan apps):", style = MaterialTheme.typography.titleMedium)
        Text("In the Security settings, tap 'Google Play Protect', then tap the settings icon at the top right and turn off 'Scan apps with Play Protect'.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 8.dp))
        Button(onClick = {
            // Try to open Play Protect settings directly (hacky, may not work on all devices)
            val playProtectIntent = Intent().apply {
                setClassName(
                    "com.google.android.gms",
                    "com.google.android.gms.security.settings.VerifyAppsSettingsActivity"
                )
            }
            try {
                context.startActivity(playProtectIntent)
            } catch (_: Exception) {
                // Fallback: open generic Security settings
                val fallbackIntent = Intent(Settings.ACTION_SECURITY_SETTINGS)
                context.startActivity(fallbackIntent)
            }
        }) {
            Text("Open Play Protect Settings")
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text("2. Download the correct APK from GitHub:", style = MaterialTheme.typography.titleMedium)
        Button(onClick = {
            val url = "https://github.com/wingSan1262/instantlock-publicapp/releases"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            context.startActivity(intent)
        }) {
            Text("Go to GitHub Releases")
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text("Choose the APK for your device:")
        Text("• app-arm64-v8a-release.apk – Modern 64-bit ARM devices (recommended)\n" +
                "• app-armeabi-v7a-release.apk – 32-bit ARM devices\n" +
                "• app-x86_64-release.apk – 64-bit x86 devices (emulators/tablets)\n" +
                "• app-x86-release.apk – 32-bit x86 devices\n" +
                "• app-universal-release.apk – Works on all devices (larger size)",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 8.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text("3. Install the APK you downloaded:", style = MaterialTheme.typography.titleMedium)
        Text("Open your Downloads folder and tap the APK file to install.", style = MaterialTheme.typography.bodySmall)
        Spacer(modifier = Modifier.height(12.dp))
        Text("4. Turn Play Protect back on:", style = MaterialTheme.typography.titleMedium)
        Button(onClick = {
            val intent = Intent(Settings.ACTION_SECURITY_SETTINGS)
            context.startActivity(intent)
        }) {
            Text("Reopen Play Protect Settings")
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text("Why do I need to do this?", style = MaterialTheme.typography.titleMedium)
        Text("Play Protect may block accessibility apps that scan the screen, even if they help with digital wellbeing. Disabling Play Protect temporarily allows installation.", style = MaterialTheme.typography.bodySmall)
        Spacer(modifier = Modifier.height(16.dp))
        Text("Troubleshooting:", style = MaterialTheme.typography.titleMedium)
        Text("• If you can't install, make sure Play Protect is off and 'Install unknown apps' is enabled.\n• If you see warnings, tap 'Install anyway' or 'Allow'.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    MyApplicationTheme {
        Greeting("Android")
    }
}