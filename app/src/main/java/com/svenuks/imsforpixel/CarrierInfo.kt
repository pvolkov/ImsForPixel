package com.svenuks.imsforpixel

import android.content.Context
import android.os.Build
import android.telephony.SubscriptionManager

object CarrierInfo {

    fun getCarrierNameForSlot(context: Context, slotIndex: Int): String? {
        readCachedName(context, slotIndex)?.let { return it }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            try {
                val subManager = context.getSystemService(SubscriptionManager::class.java) ?: return null
                val info = subManager.getActiveSubscriptionInfoForSimSlotIndex(slotIndex) ?: return null
                val carrier = info.carrierName?.toString()?.trim()
                if (!carrier.isNullOrEmpty()) return carrier
                val display = info.displayName?.toString()?.trim()
                if (!display.isNullOrEmpty()) return display
            } catch (_: SecurityException) {
                // READ_PHONE_STATE may be denied — fall back to cached / generic label
            }
        }
        return null
    }

    fun getCarrierLabel(context: Context, slotIndex: Int): String {
        return getCarrierNameForSlot(context, slotIndex)
            ?: context.getString(R.string.sim_slot_fallback, slotIndex + 1)
    }

    fun cacheCarrierName(context: Context, slotIndex: Int, name: String?) {
        val trimmed = name?.trim().orEmpty()
        if (trimmed.isEmpty()) return
        try {
            java.io.File(context.filesDir, "carrier_name_$slotIndex.txt").writeText(trimmed)
        } catch (_: Exception) {
        }
    }

    private fun readCachedName(context: Context, slotIndex: Int): String? {
        return try {
            val name = java.io.File(context.filesDir, "carrier_name_$slotIndex.txt").readText().trim()
            name.ifEmpty { null }
        } catch (_: Exception) {
            null
        }
    }
}
