package com.pvolkov.imsforpixel
 
import android.app.Instrumentation
import android.app.UiAutomation
import android.content.Context
import android.os.Bundle
import android.os.PersistableBundle
import android.telephony.CarrierConfigManager
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.util.Log
import org.lsposed.hiddenapibypass.HiddenApiBypass
import java.lang.reflect.Method

 
class BrokerInstrumentation : Instrumentation() {
    companion object {
        private const val TAG = "VoLTEBrokerInst"
    }
 
    /**
     * Finds a method by name walking up the class hierarchy and interfaces.
     * When [paramCount] is given, only a method with exactly that many parameters is returned;
     * the order of [Class.getDeclaredMethods] is unspecified, so overloads must be pinned explicitly.
     */
    private fun findMethod(obj: Any, name: String, paramCount: Int? = null): Method? {
        fun matches(m: Method) = m.name == name && (paramCount == null || m.parameterTypes.size == paramCount)

        var clazz: Class<*>? = obj.javaClass
        while (clazz != null) {
            try {
                clazz.declaredMethods.firstOrNull(::matches)?.let {
                    it.isAccessible = true
                    return it
                }
            } catch (e: Exception) {
                // Ignore
            }
            clazz = clazz.superclass
        }

        for (iface in obj.javaClass.interfaces) {
            try {
                iface.declaredMethods.firstOrNull(::matches)?.let {
                    it.isAccessible = true
                    return it
                }
            } catch (e: Exception) {
                // Ignore
            }
        }
        return null
    }

    /**
     * Applies (or clears, when [bundle] is null) a carrier config override.
     *
     * Always prefers `overrideConfig(int, PersistableBundle, boolean persistent)` with
     * `persistent = true`: persistent overrides are stored by CarrierConfigLoader and restored on
     * boot, so VoLTE/VoWiFi survive a reboot without any help from this app. Clearing with the
     * same overload also deletes the persisted file; the 2-arg overload would leave it behind.
     */
    private fun overrideCarrierConfig(
        carrierConfigManager: CarrierConfigManager,
        subId: Int,
        bundle: PersistableBundle?,
    ) {
        val persistentMethod = findMethod(carrierConfigManager, "overrideConfig", paramCount = 3)
        if (persistentMethod != null) {
            persistentMethod.invoke(carrierConfigManager, subId, bundle, true)
            return
        }
        val legacyMethod = findMethod(carrierConfigManager, "overrideConfig", paramCount = 2)
            ?: throw NoSuchMethodException("CarrierConfigManager.overrideConfig not found")
        Log.w(TAG, "Only non-persistent overrideConfig(int, PersistableBundle) is available; override will not survive reboot")
        legacyMethod.invoke(carrierConfigManager, subId, bundle)
    }
 
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        Log.d(TAG, "BrokerInstrumentation starting...")
        
        val clearArg = arguments?.getString("clear") == "true" || arguments?.getBoolean("clear") == true
        Log.d(TAG, "clearArg: $clearArg")

        Thread {
            try {
                HiddenApiBypass.addHiddenApiExemptions("L")
                Log.d(TAG, "Successfully applied HiddenApiBypass exemptions in instrumentation")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to apply HiddenApiBypass exemptions in instrumentation", e)
            }
            
            try {
                var uiAutomation: UiAutomation? = null
                var retries = 5
                while (retries > 0) {
                    try {
                        // Try to get standard UiAutomation first to avoid flags mismatch and disconnect() call on Android 15/16
                        uiAutomation = getUiAutomation()
                        if (uiAutomation != null) {
                            Log.d(TAG, "Successfully connected UiAutomation")
                            break
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to connect UiAutomation, retries left: ${retries - 1}", e)
                        retries--
                        if (retries > 0) {
                            try { Thread.sleep(500) } catch (ignored: Exception) {}
                        } else {
                            throw e
                        }
                    }
                }

                if (uiAutomation != null) {
                    uiAutomation.adoptShellPermissionIdentity()
                    Log.d(TAG, "Successfully adopted shell permission identity via UiAutomation")
                } else {
                    Log.e(TAG, "UiAutomation is null, cannot adopt shell permission identity")
                }
                
                try {
                    patchAllSimsAndPoll(arguments)
                    showImsStatusNotification(!clearArg)
                } finally {
                    if (uiAutomation != null) {
                        try {
                            uiAutomation.dropShellPermissionIdentity()
                            Log.d(TAG, "Released shell permission identity via UiAutomation")
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to drop shell permission identity", e)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to run instrumentation patch", e)
            } finally {
                finish(0, Bundle())
            }
        }.start()
    }

    override fun finish(resultCode: Int, results: Bundle?) {
        Log.d(TAG, "Instrumentation finish() called")
        try {
            super.finish(resultCode, results)
        } catch (e: Exception) {
            Log.e(TAG, "Exception during Instrumentation.finish() ignored safely", e)
        }
    }

    private fun patchAllSimsAndPoll(arguments: Bundle?) {
        val sharedPrefs = context.getSharedPreferences("volte_settings", Context.MODE_PRIVATE)
        VolteSettings.migrateDisplaySettings(sharedPrefs)
        val subManager = context.getSystemService(SubscriptionManager::class.java) ?: return
        val carrierConfigManager = context.getSystemService(CarrierConfigManager::class.java) ?: return
        val telephonyManager = context.getSystemService(TelephonyManager::class.java) ?: return

        val activeSubscriptions = subManager.activeSubscriptionInfoList ?: emptyList()
        Log.d(TAG, "Found ${activeSubscriptions.size} active SIMs")

        val hasClearArg = arguments?.containsKey("clear") == true
        val clearArg = arguments?.getString("clear") == "true" || arguments?.getBoolean("clear") == true
        val slotFilter = arguments?.getString("slot")?.toIntOrNull()

        if (slotFilter != null) {
            Log.d(TAG, "Slot filter active: $slotFilter")
        }

        val processedSubscriptions = mutableListOf<android.telephony.SubscriptionInfo>()

        // Phase 1: Apply overrides or Clear overrides, and trigger IMS reset
        for (subInfo in activeSubscriptions) {
            val subId = subInfo.subscriptionId
            val slotIndex = subInfo.simSlotIndex

            if (slotFilter != null && slotIndex != slotFilter) {
                continue
            }

            processedSubscriptions.add(subInfo)
            Log.d(TAG, "Processing SIM slot $slotIndex (SubID $subId)")

            CarrierInfo.cacheCarrierName(
                context,
                slotIndex,
                subInfo.displayName?.toString() ?: subInfo.carrierName?.toString(),
            )

            val clear = if (hasClearArg) {
                clearArg
            } else {
                sharedPrefs.getBoolean("clear_slot_$slotIndex", false)
            }

            if (clear) {
                Log.d(TAG, "Clearing config for slot $slotIndex")
                SlotStatus.writeConfigApplied(context, slotIndex, false)
                try {
                    overrideCarrierConfig(carrierConfigManager, subId, null)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to clear carrier config reflectively", e)
                }
            } else {
                val showLtePlus = VolteSettings.showLtePlus(sharedPrefs)
                val show4gIcon = VolteSettings.show4gIcon(sharedPrefs)
                val showVowifiSpn = VolteSettings.showVowifiSpn(sharedPrefs)
                val bundle = PersistableBundle()
                bundle.putBoolean("carrier_volte_available_bool", true)
                bundle.putBoolean("enhanced_4g_lte_on_by_default_bool", true)
                bundle.putBoolean("hide_enhanced_4g_lte_bool", false)
                bundle.putBoolean("editable_enhanced_4g_lte_bool", true)
                bundle.putBoolean("carrier_volte_provisioned_bool", true)
                bundle.putBoolean("carrier_volte_provisioning_required_bool", false)
                bundle.putBoolean("hide_lte_plus_data_icon_bool", !showLtePlus)
                bundle.putBoolean("show_4g_for_lte_data_icon_bool", show4gIcon)

                // VoNR (5G Calling) overrides
                bundle.putBoolean("vonr_enabled_bool", true)
                bundle.putBoolean("vonr_setting_visibility_bool", true)

                bundle.putBoolean("carrier_wfc_ims_available_bool", true)
                bundle.putBoolean("carrier_default_wfc_ims_enabled_bool", true)
                bundle.putBoolean("carrier_wfc_ims_provisioned_bool", true)
                bundle.putBoolean("editable_wfc_mode_bool", true)
                bundle.putBoolean("editable_wfc_roaming_mode_bool", true)
                bundle.putBoolean("carrier_default_wfc_ims_roaming_enabled_bool", true)
                if (showVowifiSpn) {
                    // Index 6: "%s VoWifi" — shown next to operator name when Wi-Fi calling is active
                    bundle.putInt("wfc_spn_format_idx_int", 6)
                    bundle.putInt("wfc_data_spn_format_idx_int", 6)
                } else {
                    bundle.putInt("wfc_spn_format_idx_int", 0)
                    bundle.putInt("wfc_data_spn_format_idx_int", 0)
                }

                bundle.putBoolean("carrier_supports_ss_over_ut_bool", true)
                bundle.putBoolean("show_ims_registration_status_bool", true)
                bundle.putBoolean(SlotStatus.OVERRIDE_SENTINEL_KEY, true)

                Log.d(TAG, "Applying full IMS override for slot $slotIndex")
                
                try {
                    overrideCarrierConfig(carrierConfigManager, subId, bundle)
                    Log.d(TAG, "Applied config reflectively")
                    SlotStatus.writeConfigApplied(context, slotIndex, true)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to apply config reflectively", e)
                }
            }

            // Reset IMS registration to force reload
            try {
                val resetImsMethod = findMethod(telephonyManager, "resetIms")
                resetImsMethod?.invoke(telephonyManager, slotIndex)
                Log.d(TAG, "IMS reset sent for slot $slotIndex")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to reset IMS reflectively", e)
            }
        }

        if (processedSubscriptions.isEmpty()) {
            Log.w(TAG, "No SIM slots matched filter; skipping IMS poll")
            return
        }

        // Phase 2: Poll and update status for up to 30 seconds
        Log.d(TAG, "Entering status polling loop for ${processedSubscriptions.size} slot(s)...")
        var pollsLeft = 30
        val totalPolls = 30
        while (pollsLeft > 0) {
            var allRegistered = true
            for (subInfo in processedSubscriptions) {
                val subId = subInfo.subscriptionId
                val slotIndex = subInfo.simSlotIndex
                val isImsRegistered = ImsRegistration.isRegistered(subId)
                Log.d(TAG, "Poll $pollsLeft: SIM slot $slotIndex IMS Registered: $isImsRegistered")
                
                SlotStatus.writeImsRegistered(context, slotIndex, isImsRegistered)

                if (!isImsRegistered) {
                    allRegistered = false
                }
            }

            val secondsElapsed = totalPolls - pollsLeft
            if (hasClearArg && clearArg) {
                // When clearing/restoring, wait until it's unregistered
                if (!allRegistered) {
                    Log.d(TAG, "IMS successfully unregistered. Stopping poll.")
                    break
                }
            } else {
                // When applying/activating, wait until it's registered
                // To avoid stale true values right after reset, only exit early after at least 5 seconds
                if (allRegistered && secondsElapsed >= 5) {
                    Log.d(TAG, "IMS successfully registered. Stopping poll.")
                    break
                }
            }

            try {
                Thread.sleep(1000)
            } catch (ignored: Exception) {}
            pollsLeft--
        }
    }

    private fun showImsStatusNotification(isActivate: Boolean) {
        ImsStatusNotification.show(context, isActivate)
    }
}
