package com.pvolkov.imsforpixel

import android.os.IBinder

object ImsQueryTool {
    @JvmStatic
    fun main(args: Array<String>) {
        try {
            val serviceManagerClass = Class.forName("android.os.ServiceManager")
            val getServiceMethod = serviceManagerClass.getMethod("getService", String::class.java)

            val isubBinder = getServiceMethod.invoke(null, "isub") as IBinder
            val isubStubClass = Class.forName("com.android.internal.telephony.ISub\$Stub")
            val isubAsInterface = isubStubClass.getMethod("asInterface", IBinder::class.java)
            val isubService = isubAsInterface.invoke(null, isubBinder)

            val iSubClass = Class.forName("com.android.internal.telephony.ISub")
            val getSubIdMethod = iSubClass.getMethod("getSubId", Int::class.javaPrimitiveType)
            getSubIdMethod.isAccessible = true

            for (slot in 0..1) {
                val subId = getSubIdMethod.invoke(isubService, slot) as? Int ?: -1
                if (subId != -1) {
                    println("RESULT:$slot:${ImsRegistration.isRegistered(subId)}")
                } else {
                    println("RESULT:$slot:false")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
