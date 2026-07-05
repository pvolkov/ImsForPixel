package com.pvolkov.imsforpixel

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
        val component = "${BuildConfig.APPLICATION_ID}/${BuildConfig.APPLICATION_ID}.BrokerInstrumentation"
        return "nohup am instrument -w $extras $component > /dev/null 2>&1 &"
    }
}
