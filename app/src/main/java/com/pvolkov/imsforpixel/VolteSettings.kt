package com.pvolkov.imsforpixel

import android.content.Context
import android.content.SharedPreferences

object VolteSettings {
    const val PREFS_NAME = "volte_settings"

    const val BOOT_STATUS_NONE = "none"
    const val BOOT_STATUS_PENDING = "pending"
    const val BOOT_STATUS_SUCCESS = "success"
    const val BOOT_STATUS_FAILED = "failed"

    const val KEY_SHOW_4G_ICON = "show_4g_icon"
    const val KEY_SHOW_LTE_PLUS = "show_lte_plus"
    const val KEY_SHOW_VOWIFI_SPN = "show_vowifi_spn"

    fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun migrateDisplaySettings(prefs: SharedPreferences) {
        if (prefs.getBoolean("initialized_defaults_v6", false)) return
        val editor = prefs.edit()
        if (!prefs.contains(KEY_SHOW_4G_ICON)) {
            editor.putBoolean(KEY_SHOW_4G_ICON, prefs.getBoolean("show_4g_icon_slot_0", true))
        }
        if (!prefs.contains(KEY_SHOW_LTE_PLUS)) {
            editor.putBoolean(KEY_SHOW_LTE_PLUS, prefs.getBoolean("show_lte_plus_slot_0", true))
        }
        if (!prefs.contains(KEY_SHOW_VOWIFI_SPN)) {
            editor.putBoolean(KEY_SHOW_VOWIFI_SPN, prefs.getBoolean("show_vowifi_spn_slot_0", true))
        }
        editor.putBoolean("initialized_defaults_v6", true)
        editor.commit()
    }

    fun show4gIcon(prefs: SharedPreferences): Boolean =
        prefs.getBoolean(KEY_SHOW_4G_ICON, true)

    fun showLtePlus(prefs: SharedPreferences): Boolean =
        prefs.getBoolean(KEY_SHOW_LTE_PLUS, true)

    fun showVowifiSpn(prefs: SharedPreferences): Boolean =
        prefs.getBoolean(KEY_SHOW_VOWIFI_SPN, true)

    fun isApplyOnBoot(prefs: SharedPreferences, slot: Int): Boolean =
        prefs.getBoolean("apply_on_boot_slot_$slot", false)

    fun setApplyOnBoot(prefs: SharedPreferences, slot: Int, enabled: Boolean) {
        prefs.edit().putBoolean("apply_on_boot_slot_$slot", enabled).commit()
    }

    fun hasBootApply(context: Context): Boolean {
        val prefs = prefs(context)
        return isApplyOnBoot(prefs, 0) || isApplyOnBoot(prefs, 1)
    }

    fun slotsWithBootApply(context: Context): List<Int> =
        (0..1).filter { isApplyOnBoot(prefs(context), it) }

    fun setLastAdbPort(context: Context, port: Int) {
        prefs(context).edit().putInt("last_adb_port", port).apply()
    }

    fun getLastAdbPort(context: Context): Int? {
        val port = prefs(context).getInt("last_adb_port", -1)
        return port.takeIf { it in 1..65535 }
    }

    fun setBootReapplyStatus(context: Context, status: String, message: String = "") {
        prefs(context).edit()
            .putString("boot_reapply_status", status)
            .putLong("boot_reapply_time", System.currentTimeMillis())
            .putString("boot_reapply_message", message)
            .commit()
    }

    fun getBootReapplyStatus(context: Context): String =
        prefs(context).getString("boot_reapply_status", BOOT_STATUS_NONE) ?: BOOT_STATUS_NONE

    fun getBootReapplyMessage(context: Context): String =
        prefs(context).getString("boot_reapply_message", "").orEmpty()

    fun getBootReapplyStatusLabel(context: Context): String {
        return when (getBootReapplyStatus(context)) {
            BOOT_STATUS_PENDING -> context.getString(R.string.boot_status_pending)
            BOOT_STATUS_SUCCESS -> context.getString(R.string.boot_status_success)
            BOOT_STATUS_FAILED -> {
                val msg = getBootReapplyMessage(context)
                if (msg.isNotEmpty()) {
                    context.getString(R.string.boot_status_failed_detail, msg)
                } else {
                    context.getString(R.string.boot_status_failed)
                }
            }
            else -> context.getString(R.string.boot_status_none)
        }
    }
}
