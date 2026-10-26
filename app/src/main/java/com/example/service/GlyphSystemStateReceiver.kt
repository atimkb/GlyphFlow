package com.example.service

import android.app.usage.UsageStatsManager
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.media.AudioManager
import android.net.NetworkInfo
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Handler
import android.provider.Settings
import com.example.domain.model.ConditionType
import com.example.domain.model.ScreenTriggerState
import com.example.domain.model.SystemSignalEvent
import com.example.domain.model.SystemToggleFilter
import com.example.engine.EventIngestBus
import com.example.engine.GlobalSystemStateCache

/**
 * Dedicated System Broadcast Receiver & Telemetry Bridge for OS-Level Triggers:
 * - Bluetooth connection & adapter state changes
 * - Power connected / disconnected & battery level changes
 * - Ringer mode changes (Silent / Vibrate / Normal)
 * - Screen on / off & user unlock events
 * - Wi-Fi state & Network connection changes
 * - Airplane mode toggles
 */
class GlyphSystemStateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent == null || context == null) return
        val action = intent.action ?: return

        when (action) {
            BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED,
            BluetoothAdapter.ACTION_STATE_CHANGED,
            "android.bluetooth.device.action.ACL_CONNECTED",
            "android.bluetooth.device.action.ACL_DISCONNECTED" -> {
                val state = intent.getIntExtra(BluetoothAdapter.EXTRA_CONNECTION_STATE, -1)
                val isConnected = action == "android.bluetooth.device.action.ACL_CONNECTED" ||
                    state == BluetoothAdapter.STATE_CONNECTED
                val connStr = if (isConnected) "connected" else "disconnected"
                GlobalSystemStateCache.update("system.bluetooth", connStr)
                GlobalSystemStateCache.recordTemporalEvent("system.bluetooth_$connStr")

                EventIngestBus.emitSignal(
                    SystemSignalEvent(
                        conditionType = ConditionType.BLUETOOTH_STATE,
                        sourcePackage = "android.bluetooth",
                        sourceAppLabel = "Bluetooth Subsystem",
                        title = "Bluetooth $connStr",
                        toggleActive = isConnected,
                        payload = GlobalSystemStateCache.getAll()
                    )
                )
            }

            Intent.ACTION_POWER_CONNECTED -> {
                GlobalSystemStateCache.update("system.power", "charging")
                GlobalSystemStateCache.recordTemporalEvent("system.power_connected")
                EventIngestBus.emitSignal(
                    SystemSignalEvent(
                        conditionType = ConditionType.CHARGING_STATE,
                        sourcePackage = "android.power",
                        sourceAppLabel = "Power Supply",
                        title = "Power Connected (Charging)",
                        isCharging = true,
                        payload = GlobalSystemStateCache.getAll()
                    )
                )
            }

            Intent.ACTION_POWER_DISCONNECTED -> {
                GlobalSystemStateCache.update("system.power", "discharging")
                GlobalSystemStateCache.recordTemporalEvent("system.power_disconnected")
                EventIngestBus.emitSignal(
                    SystemSignalEvent(
                        conditionType = ConditionType.CHARGING_STATE,
                        sourcePackage = "android.power",
                        sourceAppLabel = "Power Supply",
                        title = "Power Disconnected (Discharging)",
                        isCharging = false,
                        payload = GlobalSystemStateCache.getAll()
                    )
                )
            }

            Intent.ACTION_BATTERY_CHANGED -> {
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
                val pct = ((level.toFloat() / scale.toFloat()) * 100f).toInt().coerceIn(0, 100)
                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL

                GlobalSystemStateCache.update("system.battery_level", pct.toString())
                GlobalSystemStateCache.update("system.power", if (isCharging) "charging" else "discharging")

                EventIngestBus.emitSignal(
                    SystemSignalEvent(
                        conditionType = ConditionType.BATTERY_LEVEL,
                        sourcePackage = "android.battery",
                        sourceAppLabel = "Battery Monitor",
                        batteryLevel = pct,
                        isCharging = isCharging,
                        payload = GlobalSystemStateCache.getAll()
                    )
                )
            }

            AudioManager.RINGER_MODE_CHANGED_ACTION -> {
                val mode = intent.getIntExtra(AudioManager.EXTRA_RINGER_MODE, AudioManager.RINGER_MODE_NORMAL)
                val (filterMode, modeStr) = when (mode) {
                    AudioManager.RINGER_MODE_SILENT -> SystemToggleFilter.RINGER_SILENT to "silent"
                    AudioManager.RINGER_MODE_VIBRATE -> SystemToggleFilter.RINGER_VIBRATE to "vibrate"
                    else -> SystemToggleFilter.RINGER_NORMAL to "normal"
                }
                GlobalSystemStateCache.update("system.ringer", modeStr)
                GlobalSystemStateCache.recordTemporalEvent("system.ringer_$modeStr")

                EventIngestBus.emitSignal(
                    SystemSignalEvent(
                        conditionType = ConditionType.RINGER_MODE,
                        sourcePackage = "android.audio",
                        sourceAppLabel = "Ringer Controller",
                        title = filterMode.label,
                        ringerMode = filterMode,
                        toggleActive = mode != AudioManager.RINGER_MODE_SILENT,
                        payload = GlobalSystemStateCache.getAll()
                    )
                )
            }

            Intent.ACTION_SCREEN_OFF -> {
                GlobalSystemStateCache.update("system.screen", "off")
                GlobalSystemStateCache.recordTemporalEvent("system.screen_off")
                EventIngestBus.emitSignal(
                    SystemSignalEvent(
                        conditionType = ConditionType.SCREEN_STATE,
                        sourcePackage = "android.screen",
                        sourceAppLabel = "Display Manager",
                        title = "Screen Turned Off",
                        screenState = ScreenTriggerState.SCREEN_OFF,
                        payload = GlobalSystemStateCache.getAll()
                    )
                )
            }

            Intent.ACTION_SCREEN_ON -> {
                GlobalSystemStateCache.update("system.screen", "on")
                GlobalSystemStateCache.recordTemporalEvent("system.screen_on")
                EventIngestBus.emitSignal(
                    SystemSignalEvent(
                        conditionType = ConditionType.SCREEN_STATE,
                        sourcePackage = "android.screen",
                        sourceAppLabel = "Display Manager",
                        title = "Screen Turned On",
                        screenState = ScreenTriggerState.SCREEN_ON,
                        payload = GlobalSystemStateCache.getAll()
                    )
                )
            }

            Intent.ACTION_USER_PRESENT -> {
                GlobalSystemStateCache.update("system.screen", "unlocked")
                GlobalSystemStateCache.recordTemporalEvent("system.screen_unlock")
                EventIngestBus.emitSignal(
                    SystemSignalEvent(
                        conditionType = ConditionType.SCREEN_STATE,
                        sourcePackage = "android.screen",
                        sourceAppLabel = "Lockscreen Subsystem",
                        title = "User Present / Device Unlocked",
                        payload = GlobalSystemStateCache.getAll()
                    )
                )
            }

            WifiManager.WIFI_STATE_CHANGED_ACTION,
            WifiManager.NETWORK_STATE_CHANGED_ACTION -> {
                val wifiState = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, -1)
                val netInfo = intent.getParcelableExtra<NetworkInfo>(WifiManager.EXTRA_NETWORK_INFO)
                val isConnected = netInfo?.isConnected == true || wifiState == WifiManager.WIFI_STATE_ENABLED
                val connStr = if (isConnected) "connected" else "disconnected"

                GlobalSystemStateCache.update("system.wifi", connStr)
                GlobalSystemStateCache.recordTemporalEvent("system.wifi_$connStr")

                EventIngestBus.emitSignal(
                    SystemSignalEvent(
                        conditionType = ConditionType.WIFI_STATE,
                        sourcePackage = "android.net.wifi",
                        sourceAppLabel = "Wi-Fi Subsystem",
                        title = "Wi-Fi $connStr",
                        toggleActive = isConnected,
                        payload = GlobalSystemStateCache.getAll()
                    )
                )
            }

            Intent.ACTION_AIRPLANE_MODE_CHANGED -> {
                val isOn = intent.getBooleanExtra("state", false)
                val stateStr = if (isOn) "on" else "off"
                GlobalSystemStateCache.update("system.airplane_mode", stateStr)
                GlobalSystemStateCache.recordTemporalEvent("system.airplane_mode_$stateStr")

                EventIngestBus.emitSignal(
                    SystemSignalEvent(
                        conditionType = ConditionType.AIRPLANE_MODE,
                        sourcePackage = "android.settings",
                        sourceAppLabel = "Airplane Mode Controller",
                        title = "Airplane Mode $stateStr",
                        toggleActive = isOn,
                        payload = GlobalSystemStateCache.getAll()
                    )
                )
            }
        }
    }

    companion object {
        fun registerDynamically(context: Context, receiver: GlyphSystemStateReceiver) {
            val filter = android.content.IntentFilter().apply {
                addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED)
                addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
                addAction("android.bluetooth.device.action.ACL_CONNECTED")
                addAction("android.bluetooth.device.action.ACL_DISCONNECTED")
                addAction(Intent.ACTION_POWER_CONNECTED)
                addAction(Intent.ACTION_POWER_DISCONNECTED)
                addAction(Intent.ACTION_BATTERY_CHANGED)
                addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
                addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
                addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
                addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED)
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                context.registerReceiver(receiver, filter)
            }
        }

        fun detectForegroundPackage(context: Context): String {
            return com.example.service.detectForegroundPackage(context) ?: ""
        }
    }
}

/**
 * Deep System Settings Observer monitoring Settings.System and Settings.Global URIs
 * for Airplane Mode, NFC, Auto-Rotate, and Location/GPS toggles.
 */
class SettingsContentObserver(
    handler: Handler,
    private val context: Context
) : ContentObserver(handler) {

    fun register(context: Context) {
        runCatching {
            context.contentResolver.registerContentObserver(Settings.System.CONTENT_URI, true, this)
            context.contentResolver.registerContentObserver(Settings.Global.CONTENT_URI, true, this)
        }
    }

    fun unregister(context: Context) {
        runCatching {
            context.contentResolver.unregisterContentObserver(this)
        }
    }

    override fun onChange(selfChange: Boolean, uri: Uri?) {
        super.onChange(selfChange, uri)
        val cr = context.contentResolver

        // 1. Airplane Mode
        val airplaneOn = runCatching {
            Settings.Global.getInt(cr, Settings.Global.AIRPLANE_MODE_ON, 0) == 1
        }.getOrDefault(false)
        GlobalSystemStateCache.update("system.airplane_mode", if (airplaneOn) "on" else "off")

        // 2. NFC
        val nfcOn = runCatching {
            Settings.Global.getInt(cr, "nfc_on", 0) == 1
        }.getOrDefault(false)
        GlobalSystemStateCache.update("system.nfc", if (nfcOn) "on" else "off")

        // 3. Auto-Rotate
        val autoRotateOn = runCatching {
            Settings.System.getInt(cr, Settings.System.ACCELEROMETER_ROTATION, 0) == 1
        }.getOrDefault(false)
        GlobalSystemStateCache.update("system.auto_rotate", if (autoRotateOn) "on" else "off")

        // 4. Location Mode
        val locationOn = runCatching {
            Settings.Secure.getInt(cr, Settings.Secure.LOCATION_MODE, 0) != 0
        }.getOrDefault(false)
        GlobalSystemStateCache.update("system.location", if (locationOn) "on" else "off")

        EventIngestBus.emitSignal(
            SystemSignalEvent(
                conditionType = ConditionType.SYSTEM_STATE,
                sourcePackage = "android.settings",
                sourceAppLabel = "Deep Settings Observer",
                title = "System Settings Toggled",
                payload = GlobalSystemStateCache.getAll()
            )
        )
    }
}

/**
 * Utility for active foreground application package detection via UsageStatsManager.
 */
fun detectForegroundPackage(context: Context): String? {
    return runCatching {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
        val now = System.currentTimeMillis()
        val stats = usm?.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 10_000L, now)
        val top = stats?.filter { it.lastTimeUsed > 0 }?.maxByOrNull { it.lastTimeUsed }
        top?.packageName
    }.getOrNull()
}
