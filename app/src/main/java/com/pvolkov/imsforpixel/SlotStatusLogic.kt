package com.pvolkov.imsforpixel

object SlotStatusLogic {
    fun imsState(
        fileLastModified: Long?,
        content: String?,
        bootTimeMillis: Long,
    ): SlotStatus.ImsState {
        if (fileLastModified == null || fileLastModified < bootTimeMillis) {
            return SlotStatus.ImsState.Unknown
        }
        return when (content?.trim()?.lowercase()) {
            "true" -> SlotStatus.ImsState.Registered
            "false" -> SlotStatus.ImsState.NotRegistered
            else -> SlotStatus.ImsState.Unknown
        }
    }

    fun isConfigApplied(
        liveAvailable: Boolean,
        hasSentinelKey: Boolean,
        sentinelValue: Boolean,
        fileApplied: Boolean,
        liveMatchesPrefs: Boolean,
    ): Boolean {
        if (!liveAvailable) return fileApplied
        if (hasSentinelKey) return sentinelValue
        if (!fileApplied) return false
        return liveMatchesPrefs
    }
}
