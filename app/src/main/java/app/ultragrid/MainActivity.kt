package app.ultragrid

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.content.IntentCompat
import app.ultragrid.ui.NavViewModel
import app.ultragrid.ui.Screen
import app.ultragrid.ui.UltraGridApp
import app.ultragrid.ui.UltraGridTheme

class MainActivity : ComponentActivity() {
    private val nav: NavViewModel by viewModels()
    private val grid: GridViewModel by viewModels()
    private val collage: CollageViewModel by viewModels()
    private val planner: PlannerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleShare(intent)
        setContent {
            UltraGridTheme {
                UltraGridApp(nav, grid, collage, planner)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    /** "Share → UltraGrid" from another app opens the photo in the profile grid editor. */
    private fun handleShare(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND || intent.type?.startsWith("image/") != true) return
        val uri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java) ?: return
        grid.startMode(SplitMode.GRID)
        grid.load(uri)
        nav.screen = Screen.GRID
    }
}
