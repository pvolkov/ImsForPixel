package com.svenuks.imsforpixel

import android.content.Context
import android.content.SharedPreferences

object VolteSettings {
    const val PREFS_NAME = "volte_settings"

    fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isApplyOnBoot(prefs: SharedPreferences, slot: Int): Boolean =
        prefs.getBoolean("apply_on_boot_slot_$slot", false)

    fun setApplyOnBoot(prefs: SharedPreferences, slot: Int, enabled: Boolean) {
        prefs.edit().putBoolean("apply_on_boot_slot_$slot", enabled).commit()
    }

    fun hasBootApply(context: Context): Boolean {
        val prefs = prefs(context)
        return isApplyOnBoot(prefs, 0) || isApplyOnBoot(prefs, 1)
    }
}
