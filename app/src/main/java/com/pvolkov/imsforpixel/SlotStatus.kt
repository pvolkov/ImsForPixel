package com.pvolkov.imsforpixel

import android.content.Context
import android.os.SystemClock
import android.telephony.CarrierConfigManager
import android.telephony.SubscriptionManager

object SlotStatus {
    const val OVERRIDE_SENTINEL_KEY = "imsforpixel_override_applied_bool"

    enum class ImsState {
        Registered,
        NotRegistered,
        Unknown,
    }

    fun bootTimeMillis(): Long = System.currentTimeMillis() - SystemClock.elapsedRealtime()

    fun isConfigApplied(context: Context, slotIndex: Int): Boolean {
        val fileApplied = readFlag(context, configFileName(slotIndex))
        val config = carrierConfigForSlot(context, slotIndex) ?: return fileApplied
        if (config.containsKey(OVERRIDE_SENTINEL_KEY)) {
            return config.getBoolean(OVERRIDE_SENTINEL_KEY, false)
        }
        if (!fileApplied) return false
        val prefs = VolteSettings.prefs(context)
        return config.getBoolean("carrier_volte_available_bool", false) ==
            prefs.getBoolean("volte_slot_$slotIndex", true) &&
            config.getBoolean("vonr_enabled_bool", false) ==
            prefs.getBoolean("vonr_slot_$slotIndex", true) &&
            config.getBoolean("carrier_wfc_ims_available_bool", false) ==
            prefs.getBoolean("vowifi_slot_$slotIndex", true)
    }

    fun presentSlots(context: Context): List<Int> {
        val detected = mutableListOf<Int>()
        var permissionDenied = false
        for (slot in 0..1) {
            when (hasSimInSlot(context, slot)) {
                true -> detected += slot
                false -> Unit
                null -> permissionDenied = true
            }
        }
        return when {
            detected.isNotEmpty() -> detected
            permissionDenied -> listOf(0, 1)
            else -> listOf(0, 1)
        }
    }

    private fun hasSimInSlot(context: Context, slotIndex: Int): Boolean? {
        return try {
            val subManager = context.getSystemService(SubscriptionManager::class.java) ?: return null
            subManager.getActiveSubscriptionInfoForSimSlotIndex(slotIndex) != null
        } catch (_: SecurityException) {
            null
        } catch (_: Exception) {
            null
        }
    }

    fun imsState(context: Context, slotIndex: Int): ImsState {
        val file = java.io.File(context.filesDir, imsFileName(slotIndex))
        if (!file.exists() || file.lastModified() < bootTimeMillis()) {
            return ImsState.Unknown
        }
        return try {
            if (file.readText().trim().toBoolean()) ImsState.Registered else ImsState.NotRegistered
        } catch (_: Exception) {
            ImsState.Unknown
        }
    }

    fun writeConfigApplied(context: Context, slotIndex: Int, applied: Boolean) {
        writeFlag(context, configFileName(slotIndex), applied)
    }

    fun writeImsRegistered(context: Context, slotIndex: Int, registered: Boolean) {
        writeFlag(context, imsFileName(slotIndex), registered)
    }

    fun readFlag(context: Context, fileName: String): Boolean {
        return try {
            java.io.File(context.filesDir, fileName).readText().trim().toBoolean()
        } catch (_: Exception) {
            false
        }
    }

    private fun writeFlag(context: Context, fileName: String, value: Boolean) {
        try {
            java.io.File(context.filesDir, fileName).writeText(value.toString())
        } catch (_: Exception) {
        }
    }

    private fun configFileName(slotIndex: Int) = "config_applied_$slotIndex.txt"

    private fun imsFileName(slotIndex: Int) = "ims_status_$slotIndex.txt"

    private fun carrierConfigForSlot(context: Context, slotIndex: Int): android.os.PersistableBundle? {
        return try {
            val subManager = context.getSystemService(SubscriptionManager::class.java) ?: return null
            val info = subManager.getActiveSubscriptionInfoForSimSlotIndex(slotIndex) ?: return null
            val carrierConfig = context.getSystemService(CarrierConfigManager::class.java) ?: return null
            carrierConfig.getConfigForSubId(info.subscriptionId)
        } catch (_: SecurityException) {
            null
        } catch (_: Exception) {
            null
        }
    }
}
