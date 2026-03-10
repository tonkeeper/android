package com.tonapps.log.utils

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.Context
import android.os.Build
import android.provider.Settings
import java.util.concurrent.ConcurrentHashMap

open class LogHeaderBuilder {
    private val headerInfo = ConcurrentHashMap<String, String>()
    private val deviceInfo = ConcurrentHashMap<String, String>()

    fun add(key: String, value: String) = apply {
        val k = "$key: "
        if (!headerInfo.containsKey(k)) headerInfo[k] = value
    }

    internal fun addDeviceInfo(key: String, value: String) = apply {
        val k = "$key: "
        if (!deviceInfo.containsKey(k)) deviceInfo[k] = value
    }

    open fun build(): StringBuilder {
        val header = StringBuilder()
        headerInfo.forEach {
            header.append(it.key).append(it.value).append("\n")
        }

        header.apply { append("\n\n") }

        deviceInfo.forEach {
            header.append(it.key).append(it.value).append("\n")
        }
        return header.apply { append("\n\n") }
    }

    internal class DeviceSettingsProvider {
        val settings = ConcurrentHashMap<String, String>()

        fun provide(context: Context): Map<String, String> {
            if (settings.isNotEmpty()) return settings

            val contentResolver = context.contentResolver
            loadGlobalSettings(contentResolver)
            loadSecureSettings(contentResolver)
            loadDefaultAnimationSettings(contentResolver)
            return settings
        }

        private fun loadSecureSettings(contentResolver: ContentResolver) {
            val secures = arrayListOf(
                Settings.Secure.ACCESSIBILITY_DISPLAY_INVERSION_ENABLED,
                Settings.Secure.ACCESSIBILITY_ENABLED,
                Settings.Secure.ALLOWED_GEOLOCATION_ORIGINS,
                Settings.Secure.ANDROID_ID,
                Settings.Secure.DEFAULT_INPUT_METHOD,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                Settings.Secure.ENABLED_INPUT_METHODS,
                Settings.Secure.INPUT_METHOD_SELECTOR_VISIBILITY,
                Settings.Secure.PARENTAL_CONTROL_ENABLED,
                Settings.Secure.PARENTAL_CONTROL_LAST_UPDATE,
                Settings.Secure.SELECTED_INPUT_METHOD_SUBTYPE,
                Settings.Secure.SKIP_FIRST_USE_HINTS,
                Settings.Secure.TTS_ENABLED_PLUGINS,
                Settings.Secure.TTS_DEFAULT_RATE,
                Settings.Secure.TTS_DEFAULT_SYNTH,
            )
            secures.forEach {
                loadSecureSetting(contentResolver, it)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                loadSecureSetting(contentResolver, Settings.Secure.RTT_CALLING_MODE)
            }
        }

        private fun loadGlobalSettings(contentResolver: ContentResolver) {
            val globals = arrayListOf(
                Settings.Global.AIRPLANE_MODE_ON,
                Settings.Global.AIRPLANE_MODE_RADIOS,
                Settings.Global.ADB_ENABLED,
                Settings.Global.AUTO_TIME,
                Settings.Global.AUTO_TIME_ZONE,
                Settings.Global.ALWAYS_FINISH_ACTIVITIES,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                Settings.Global.BLUETOOTH_ON,
                Settings.Global.DEBUG_APP,
                Settings.Global.DEVELOPMENT_SETTINGS_ENABLED,
                Settings.Global.DEVICE_PROVISIONED,
                Settings.Global.HTTP_PROXY,
                Settings.Global.MODE_RINGER,
                Settings.Global.NAME,
                Settings.Global.NETWORK_PREFERENCE,
                Settings.Global.RADIO_BLUETOOTH,
                Settings.Global.RADIO_CELL,
                Settings.Global.RADIO_NFC,
                Settings.Global.RADIO_WIFI,
                Settings.Global.STAY_ON_WHILE_PLUGGED_IN,
                Settings.Global.USB_MASS_STORAGE_ENABLED,
                Settings.Global.USE_GOOGLE_MAIL,
                Settings.Global.WIFI_MAX_DHCP_RETRY_COUNT,
                Settings.Global.WIFI_MOBILE_DATA_TRANSITION_WAKELOCK_TIMEOUT_MS,
                Settings.Global.WIFI_ON,
            )

            globals.forEach {
                loadGlobalSetting(contentResolver, it)
            }

            loadGlobalSetting(contentResolver, Settings.Global.BOOT_COUNT)
            loadGlobalSetting(contentResolver, Settings.Global.CONTACT_METADATA_SYNC_ENABLED)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                @Suppress("DEPRECATION")
                loadGlobalSetting(contentResolver, Settings.Global.APPLY_RAMPING_RINGER)
            }

            loadGlobalSetting(contentResolver, Settings.Global.DEVICE_NAME)

            loadGlobalSetting(contentResolver, Settings.Global.WIFI_DEVICE_OWNER_CONFIGS_LOCKDOWN)
            // Data roaming default, based on build
            val isRoaming = systemReadProperties("ro.com.android.dataroaming", "false").toBoolean()
            loadGlobalSetting(
                contentResolver, Settings.Global.DATA_ROAMING,
                if (isRoaming) 1 else 0
            )
        }

        private fun loadSecureSetting(context: ContentResolver, key: String, defaultValue: Any? = null) {
            try {
                val value = Settings.Secure.getString(context, key) ?: defaultValue?.toString()
                value?.let {
                    settings[key] = it
                }
            } catch (ignored: Throwable) {
                // do nothing
            }
        }

        private fun loadGlobalSetting(context: ContentResolver, key: String, defaultValue: Any? = null) {
            try {
                val value = Settings.Global.getString(context, key) ?: defaultValue?.toString()
                value?.let {
                    settings[key] = it
                }
            } catch (ignored: Throwable) {
                // do nothing
            }
        }

        private fun loadGlobalFractionSetting(context: ContentResolver, key: String) {
            try {
                settings[key] = Settings.Global.getFloat(context, key).toString()
            } catch (ignored: Throwable) {
                // do nothing
            }
        }

        private fun loadDefaultAnimationSettings(context: ContentResolver) {
            loadGlobalFractionSetting(context, Settings.Global.WINDOW_ANIMATION_SCALE)
            loadGlobalFractionSetting(context, Settings.Global.TRANSITION_ANIMATION_SCALE)
        }

        private fun systemReadProperties(property: String, defaultValue: String): String {
            try {
                @SuppressLint("PrivateApi")
                val clazz = Class.forName("android.os.SystemProperties")
                val method = clazz.getDeclaredMethod("get", String::class.java, String::class.java)
                return method.invoke(null, property, defaultValue) as String
            } catch (ignored: Throwable) {
                // do nothing
            }
            return String()
        }
    }

    class Default(
        private val context: Context,
    ) : LogHeaderBuilder() {

        private val deviceSettingsProvider = DeviceSettingsProvider()

        override fun build(): StringBuilder {
            fillDeviceInfo()
            fillDeviceSettingsInfo()

            return super.build()
        }

        private fun fillDeviceInfo() {
            add("VERSION_CODENAME", Build.VERSION.CODENAME)
            add("SDK CODE", Build.VERSION.SDK_INT.toString())
            add("MANUFACTURER", Build.MANUFACTURER)
            add("MODEL", Build.MODEL)
            add("BOARD", Build.BOARD)
            add("BRAND", Build.BRAND)
            add("DEVICE", Build.DEVICE)
            add("HARDWARE", Build.HARDWARE)
            add("DISPLAY", Build.DISPLAY)
            add("FINGERPRINT", Build.FINGERPRINT)
            add("PRODUCT", Build.PRODUCT)
            add("USER", Build.USER)
        }

        private fun fillDeviceSettingsInfo() {
            deviceSettingsProvider.provide(context)
                .forEach { (k, v) ->
                    addDeviceInfo(k.uppercase(), v)
                }
        }
    }
}
