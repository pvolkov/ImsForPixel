package com.pvolkov.imsforpixel

object PairingSession {
    @Volatile
    var pairingPort: Int? = null
    var onAuthStatusChanged: (() -> Unit)? = null
    var onPermissionsChanged: (() -> Unit)? = null
}
