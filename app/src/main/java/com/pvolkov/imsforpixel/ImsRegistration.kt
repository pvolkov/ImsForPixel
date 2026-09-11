package com.pvolkov.imsforpixel

import android.os.IBinder

object ImsRegistration {
    fun isRegistered(subId: Int): Boolean {
        return try {
            val serviceManagerClass = Class.forName("android.os.ServiceManager")
            val getServiceMethod = serviceManagerClass.getMethod("getService", String::class.java)
            val binder = getServiceMethod.invoke(null, "phone") as IBinder
            val stubClass = Class.forName("com.android.internal.telephony.ITelephony\$Stub")
            val asInterfaceMethod = stubClass.getMethod("asInterface", IBinder::class.java)
            val telephonyService = asInterfaceMethod.invoke(null, binder)
            val iTelephonyClass = Class.forName("com.android.internal.telephony.ITelephony")
            val method = iTelephonyClass.getMethod("isImsRegistered", Int::class.javaPrimitiveType)
            method.isAccessible = true
            method.invoke(telephonyService, subId) as Boolean
        } catch (_: Exception) {
            false
        }
    }
}
