package app.ultragrid

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.content.IntentCompat
import app.ultragrid.ui.UltraGridApp
import app.ultragrid.ui.UltraGridTheme

class MainActivity : ComponentActivity() {
    private val viewModel: GridViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleShare(intent)
        setContent {
            UltraGridTheme {
                UltraGridApp(viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    private fun handleShare(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND || intent.type?.startsWith("image/") != true) return
        IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.let(viewModel::load)
    }
}
