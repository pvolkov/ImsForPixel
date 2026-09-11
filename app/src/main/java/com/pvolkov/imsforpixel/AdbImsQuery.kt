package com.pvolkov.imsforpixel

import android.content.Context
import android.util.Log
import com.flyfishxu.kadb.Kadb

object AdbImsQuery {
    private const val TAG = "AdbImsQuery"

    fun query(context: Context, port: Int): Result<Unit> {
        return try {
            Kadb.create("127.0.0.1", port, 8000, 8000).use { kadb ->
                val appId = BuildConfig.APPLICATION_ID
                val pathRes = kadb.shell("pm path $appId")
                if (pathRes.exitCode != 0) {
                    return Result.failure(Exception(pathRes.output.ifBlank { "pm path failed" }))
                }
                val path = pathRes.output.trim().substringAfter("package:")
                if (path.isEmpty()) {
                    return Result.failure(Exception("package path is empty"))
                }
                val queryRes = kadb.shell(
                    "export CLASSPATH=$path; app_process /system/bin ${appId}.ImsQueryTool",
                )
                if (queryRes.exitCode != 0) {
                    return Result.failure(Exception(queryRes.output.ifBlank { "IMS query failed" }))
                }
                var parsed = false
                for (line in queryRes.output.lines()) {
                    if (!line.startsWith("RESULT:")) continue
                    val parts = line.split(":")
                    if (parts.size != 3) continue
                    val slot = parts[1].toIntOrNull() ?: continue
                    val isImsRegistered = parts[2].trim().toBoolean()
                    SlotStatus.writeImsRegistered(context, slot, isImsRegistered)
                    parsed = true
                    Log.d(TAG, "Updated slot $slot IMS status: $isImsRegistered")
                }
                if (!parsed) {
                    return Result.failure(Exception("no IMS result"))
                }
            }
            VolteSettings.setLastAdbPort(context, port)
            VolteSettings.setAdbPaired(context, true)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
