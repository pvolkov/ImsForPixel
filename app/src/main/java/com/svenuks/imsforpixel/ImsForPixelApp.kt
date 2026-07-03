package com.svenuks.imsforpixel

import android.app.Application
import android.util.Log
import com.flyfishxu.kadb.cert.KadbCert
import com.flyfishxu.kadb.cert.KadbCertPolicy
import com.flyfishxu.kadb.cert.OkioFilePrivateKeyStore
import okio.Path.Companion.toPath
import org.lsposed.hiddenapibypass.HiddenApiBypass

class ImsForPixelApp : Application() {

    override fun onCreate() {
        super.onCreate()
        runCatching { HiddenApiBypass.addHiddenApiExemptions("L") }
            .onFailure { Log.e(TAG, "Failed to apply HiddenApiBypass exemptions", it) }
        runCatching {
            val privateKeyFile = java.io.File(filesDir, "kadb_private_key.pem")
            KadbCert.configure(
                store = OkioFilePrivateKeyStore(privateKeyFile.absolutePath.toPath()),
                policy = KadbCertPolicy(),
                additionalPrivateKeysPem = emptyList()
            )
            KadbCert.ensureReady()
        }.onFailure { Log.e(TAG, "Failed to configure KadbCert", it) }
    }

    private companion object {
        const val TAG = "IMSForPixel"
    }
}
