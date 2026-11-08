package app.klar.audio

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import androidx.core.content.ContextCompat

/** The headphones the processed sound will play on. */
data class Headphones(
    val name: String,
    val isBluetooth: Boolean,
    /** True when the headset also offers its microphone (Bluetooth hands-free profile). */
    val hasMic: Boolean,
)

object AudioDevices {

    private val HEADPHONE_TYPES: Set<Int> = buildSet {
        add(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)
        add(AudioDeviceInfo.TYPE_BLUETOOTH_SCO)
        add(AudioDeviceInfo.TYPE_WIRED_HEADPHONES)
        add(AudioDeviceInfo.TYPE_WIRED_HEADSET)
        add(AudioDeviceInfo.TYPE_USB_HEADSET)
        add(AudioDeviceInfo.TYPE_HEARING_AID)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(AudioDeviceInfo.TYPE_BLE_HEADSET)
    }

    private fun isBluetooth(type: Int) =
        type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
            type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
            type == AudioDeviceInfo.TYPE_HEARING_AID ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && type == AudioDeviceInfo.TYPE_BLE_HEADSET)

    /**
     * Connected headphones, or null if sound would come out of the phone's own
     * speaker. Amplifying the microphone onto the speaker would howl, so the
     * app refuses to start without headphones.
     */
    fun headphones(am: AudioManager): Headphones? {
        val outputs = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).filter { it.type in HEADPHONE_TYPES }
        val best = outputs.minByOrNull {
            when (it.type) {
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> 0
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET,
                AudioDeviceInfo.TYPE_USB_HEADSET -> 1
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> 3
                else -> 2
            }
        } ?: return null
        val bluetooth = isBluetooth(best.type)
        val rawName = best.productName?.toString()?.trim().orEmpty()
        val name = when {
            rawName.isNotEmpty() && rawName != Build.MODEL -> rawName
            bluetooth -> "Bluetooth-Kopfhörer"
            else -> "Kopfhörer"
        }
        val hasMic = am.getDevices(AudioManager.GET_DEVICES_INPUTS).any {
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && it.type == AudioDeviceInfo.TYPE_BLE_HEADSET)
        }
        return Headphones(name, bluetooth, hasMic)
    }

    fun builtInMic(am: AudioManager): AudioDeviceInfo? =
        am.getDevices(AudioManager.GET_DEVICES_INPUTS).firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_MIC }

    /**
     * Routes capture and playback through the headset's Bluetooth hands-free
     * link, so the AirPods' own microphones are used. Blocks up to 4 s.
     */
    fun startHeadsetMic(context: Context, am: AudioManager): Boolean {
        am.mode = AudioManager.MODE_IN_COMMUNICATION
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val device = am.availableCommunicationDevices.firstOrNull {
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || it.type == AudioDeviceInfo.TYPE_BLE_HEADSET
            } ?: return false
            return am.setCommunicationDevice(device)
        }
        @Suppress("DEPRECATION")
        am.startBluetoothSco()
        @Suppress("DEPRECATION")
        am.isBluetoothScoOn = true
        val filter = IntentFilter(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED)
        repeat(40) {
            val sticky: Intent? = ContextCompat.registerReceiver(
                context, null, filter, ContextCompat.RECEIVER_EXPORTED,
            )
            val state = sticky?.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, -1)
            if (state == AudioManager.SCO_AUDIO_STATE_CONNECTED) return true
            Thread.sleep(100)
        }
        return false
    }

    fun stopHeadsetMic(am: AudioManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            am.clearCommunicationDevice()
        } else {
            @Suppress("DEPRECATION")
            am.isBluetoothScoOn = false
            @Suppress("DEPRECATION")
            am.stopBluetoothSco()
        }
        am.mode = AudioManager.MODE_NORMAL
    }
}
