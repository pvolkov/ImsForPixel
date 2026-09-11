package com.pvolkov.imsforpixel

import android.content.Context
import android.content.SharedPreferences

object VolteSettings {
    const val PREFS_NAME = "volte_settings"

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

    fun setLastAdbPort(context: Context, port: Int) {
        prefs(context).edit().putInt("last_adb_port", port).apply()
    }

    fun getLastAdbPort(context: Context): Int? {
        val port = prefs(context).getInt("last_adb_port", -1)
        return port.takeIf { it in 1..65535 }
    }

    fun isAdbPaired(context: Context): Boolean {
        val prefs = prefs(context)
        if (prefs.contains("adb_paired")) return prefs.getBoolean("adb_paired", false)
        return getLastAdbPort(context) != null
    }

    fun setAdbPaired(context: Context, paired: Boolean) {
        prefs(context).edit().putBoolean("adb_paired", paired).apply()
    }
}
