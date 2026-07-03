package com.svenuks.imsforpixel

object InstrumentationHelper {

    fun instrumentCommand(
        clear: Boolean,
        slot: Int? = null,
        bootReapply: Boolean = false,
    ): String {
        val extras = buildString {
            append("-e clear $clear")
            if (slot != null) append(" -e slot $slot")
            if (bootReapply) append(" -e boot_reapply true")
        }
        return "nohup am instrument -w $extras " +
            "com.svenuks.imsforpixel/com.svenuks.imsforpixel.BrokerInstrumentation > /dev/null 2>&1 &"
    }
}
