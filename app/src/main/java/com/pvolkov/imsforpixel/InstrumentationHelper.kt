package com.pvolkov.imsforpixel

import android.os.Build

object InstrumentationHelper {

    fun instrumentCommand(
        clear: Boolean,
        slot: Int? = null,
    ): String {
        val extras = buildString {
            append("-e clear $clear")
            if (slot != null) append(" -e slot $slot")
        }
        val component = "${BuildConfig.APPLICATION_ID}/${BuildConfig.APPLICATION_ID}.BrokerInstrumentation"
        // Android 11+: --no-restart keeps this process alive so -w can wait for finish().
        // Older releases force-stop the target package; background the instrument call instead.
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            "am instrument -w --no-restart $extras $component"
        } else {
            "nohup am instrument -w $extras $component > /dev/null 2>&1 &"
        }
    }
}
