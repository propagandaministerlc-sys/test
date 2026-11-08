package app.klar.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import app.klar.HearingRepository
import app.klar.MainActivity
import app.klar.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Foreground service that keeps the audio engine running while the screen is
 * off or another app is open. Android requires the "microphone" service type
 * and a visible notification for this.
 */
class HearingService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var engine: AudioEngine? = null
    private var receiverRegistered = false

    /** Headphones unplugged or Bluetooth dropped: stop before sound goes to the speaker. */
    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == android.media.AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                HearingRepository.reportError("Kopfhörer getrennt. Die Verstärkung wurde gestoppt.")
                shutdown()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        HearingRepository.init(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            shutdown()
            return START_NOT_STICKY
        }
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0,
        )
        if (engine == null) {
            if (!receiverRegistered) {
                ContextCompat.registerReceiver(
                    this, noisyReceiver,
                    IntentFilter(android.media.AudioManager.ACTION_AUDIO_BECOMING_NOISY),
                    ContextCompat.RECEIVER_EXPORTED,
                )
                receiverRegistered = true
            }
            HearingRepository.reportError(null)
            startEngine()
            // Switching between phone and AirPods microphone needs a fresh audio setup.
            scope.launch {
                HearingRepository.settings.map { it.micSource }.distinctUntilChanged().drop(1).collect {
                    engine?.stop()
                    engine = null
                    startEngine()
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun startEngine() {
        engine = AudioEngine(applicationContext) { message ->
            scope.launch {
                HearingRepository.reportError(message)
                shutdown()
            }
        }.also { it.start() }
        HearingRepository.setRunning(true)
    }

    private fun shutdown() {
        engine?.stop()
        engine = null
        HearingRepository.setRunning(false)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        engine?.stop()
        engine = null
        if (receiverRegistered) {
            unregisterReceiver(noisyReceiver)
            receiverRegistered = false
        }
        HearingRepository.setRunning(false)
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Hörhilfe aktiv", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Wird angezeigt, solange Klar das Mikrofon verstärkt."
                    setShowBadge(false)
                },
            )
        }
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, HearingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_klar)
            .setContentTitle("Klar hört mit")
            .setContentText("Umgebung wird verstärkt. Tippen zum Anpassen.")
            .setContentIntent(open)
            .addAction(0, "Stoppen", stop)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "hearing"
        private const val NOTIFICATION_ID = 7
        private const val ACTION_STOP = "app.klar.STOP"

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, HearingService::class.java))
        }

        fun stop(context: Context) {
            context.startService(Intent(context, HearingService::class.java).setAction(ACTION_STOP))
        }
    }
}
