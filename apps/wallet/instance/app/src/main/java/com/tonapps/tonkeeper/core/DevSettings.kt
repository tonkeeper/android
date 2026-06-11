package com.tonapps.tonkeeper.core

import android.annotation.SuppressLint
import android.content.Context
import com.tonapps.log.L
import com.tonapps.extensions.putBoolean
import com.tonapps.extensions.putLong
import com.tonapps.extensions.putString
import com.tonapps.tonkeeper.App
import com.tonapps.tonkeeperx.BuildConfig

@Deprecated(
    message = "We're migrating to DataStore from SharedPreferences",
    replaceWith = ReplaceWith("com.tonapps.core.flags.LocalConfig"),
)
object DevSettings {

    private val prefs by lazy { App.instance.getSharedPreferences("dev_settings", 0) }

    fun checkInstallationVersion(version: Long) {
        if (installationVersion == 0L) {
            installationVersion = version
        }
    }

    var installationVersion: Long = prefs.getLong("install_version", 0)
        set(value) {
            if (field != value) {
                field = value
                prefs.putLong("install_version", value)
            }
        }

    var tetraEnabled: Boolean = prefs.getBoolean("tetra_enabled", false)
        set(value) {
            if (field != value) {
                field = value
                prefs.putBoolean("tetra_enabled", value)
            }
        }

    var country: String? = prefs.getString("country", null)
        set(value) {
            if (field != value) {
                field = value
                prefs.putString("country", value)
            }
        }

    var blurEnabled: Boolean = prefs.getBoolean("blur_enabled", true)
        set(value) {
            if (field != value) {
                field = value
                prefs.putBoolean("blur_enabled", value)
            }
        }

    var dnsAll: Boolean = prefs.getBoolean("dns_all", false)
        set(value) {
            if (field != value) {
                field = value
                prefs.putBoolean("dns_all", value)
            }
        }

    var firstLaunchDate: Long = prefs.getLong("first_launch_date", 0)
        set(value) {
            if (field != value) {
                field = value
                prefs.putLong("first_launch_date", value)
            }
        }

    var firstLaunchDeeplink: String = prefs.getString("first_launch_deeplink", "") ?: ""
        set(value) {
            if (field != value) {
                field = value
                prefs.putString("first_launch_deeplink", value)
            }
        }

    var tonConnectLogs: Boolean = prefs.getBoolean("ton_connect_logs", false)
        set(value) {
            if (field != value) {
                field = value
                prefs.putBoolean("ton_connect_logs", value)
            }
        }

    var isLogsEnabled: Boolean = prefs.getBoolean("is_logs_enabled", false)
        set(value) {
            if (field != value) {
                field = value
                prefs.putBoolean("is_logs_enabled", value)
            }
        }

    var webViewDataDir: String? = prefs.getString("webview_data_dir", null)
        set(value) {
            if (field != value) {
                field = value
                prefs.putString("webview_data_dir", value)
            }
        }

    var ignoreSystemFontSize: Boolean = prefs.getBoolean("ignore_system_font_size", false)
        set(value) {
            if (field != value) {
                field = value
                prefs.putBoolean("ignore_system_font_size", value)
            }
        }


    fun tonConnectLog(message: String, error: Boolean = false) {
        if (tonConnectLogs || BuildConfig.DEBUG) {
            if (error) {
                L.e("TonConnect", message)
            } else {
                L.d("TonConnect", message)
            }
        }
    }

    // Migrations
    var isWebviewFolderMigrated: Boolean = prefs.getBoolean("is_webview_folder_migrated", false)
        set(value) {
            if (field != value) {
                field = value
                prefs.putBoolean("is_webview_folder_migrated", value)
            }
        }
}
