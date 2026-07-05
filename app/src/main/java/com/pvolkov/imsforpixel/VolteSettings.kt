package com.pvolkov.imsforpixel

import android.content.Context
import android.content.SharedPreferences

object VolteSettings {
    const val PREFS_NAME = "volte_settings"

    const val BOOT_STATUS_NONE = "none"
    const val BOOT_STATUS_PENDING = "pending"
    const val BOOT_STATUS_SUCCESS = "success"
    const val BOOT_STATUS_FAILED = "failed"

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
