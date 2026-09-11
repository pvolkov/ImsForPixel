package com.pvolkov.imsforpixel

import android.content.Context
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager

object CarrierInfo {

    fun getCarrierNameForSlot(context: Context, slotIndex: Int): String? {
        liveOperatorName(context, slotIndex)?.let { live ->
            cacheCarrierName(context, slotIndex, live)
            return live
        }
        return readCachedName(context, slotIndex)
    }

    fun getCarrierLabel(context: Context, slotIndex: Int): String {
        return getCarrierNameForSlot(context, slotIndex)
            ?: context.getString(R.string.sim_slot_fallback, slotIndex + 1)
    }

    fun cacheCarrierName(context: Context, slotIndex: Int, name: String?) {
        val trimmed = sanitizeOperatorName(name?.trim().orEmpty())
        if (trimmed.isEmpty()) return
        try {
            java.io.File(context.filesDir, "carrier_name_$slotIndex.txt").writeText(trimmed)
        } catch (_: Exception) {
        }
    }

    private fun liveOperatorName(context: Context, slotIndex: Int): String? {
        return try {
            val subManager = context.getSystemService(SubscriptionManager::class.java) ?: return null
            val info = subManager.getActiveSubscriptionInfoForSimSlotIndex(slotIndex) ?: return null
            val display = sanitizeOperatorName(info.displayName?.toString().orEmpty())
            if (display.isNotEmpty()) return display
            val telephony = context.getSystemService(TelephonyManager::class.java)
                ?.createForSubscriptionId(info.subscriptionId)
            sanitizeOperatorName(telephony?.simOperatorName.orEmpty()).ifEmpty { null }
        } catch (_: SecurityException) {
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun readCachedName(context: Context, slotIndex: Int): String? {
        return try {
            val name = sanitizeOperatorName(
                java.io.File(context.filesDir, "carrier_name_$slotIndex.txt").readText(),
            )
            name.ifEmpty { null }
        } catch (_: Exception) {
            null
        }
    }

    internal fun sanitizeOperatorName(name: String): String {
        return name.replace(Regex("""(?i)[\s\-]*Vo\-?Wi\-?Fi"""), "").trim()
    }
}
