package xendroid.compose.updater

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import java.io.File
import java.security.MessageDigest

/**
 * R03: the downloaded APK must be this app, newer than this install and signed by the
 * same key (Android would refuse anything else, this says why up front); then the
 * system installer takes over and asks the user to confirm. Nothing installs silently.
 */
object UpdateInstaller {
    private const val TAG = "UpdateInstaller"

    /** Null when [apk] can be installed over this app, else the reason it cannot. */
    fun verify(context: Context, apk: File): String? {
        val pm = context.packageManager
        val archive = pm.getPackageArchiveInfo(apk.path, PackageManager.GET_SIGNING_CERTIFICATES)
            ?: return "the file is not an Android package"
        if (archive.packageName != context.packageName) return "it is another app (${archive.packageName})"
        val installed = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        if (archive.longVersionCode <= installed.longVersionCode) return "it is not newer than this build"
        val theirs = signers(archive)
        val ours = signers(installed)
        if (theirs.isEmpty() || ours.isEmpty() || theirs.intersect(ours).isEmpty()) {
            return "it is signed with another key, so Android would refuse to update"
        }
        return null
    }

    /** SHA-256 of the signing certificates, including the rotation history when there is one. */
    private fun signers(info: PackageInfo): Set<String> {
        val signing = info.signingInfo ?: return emptySet()
        val certificates = if (signing.hasMultipleSigners()) signing.apkContentsSigners else signing.signingCertificateHistory
        return certificates.orEmpty().map { cert ->
            MessageDigest.getInstance("SHA-256").digest(cert.toByteArray()).joinToString("") { "%02x".format(it) }
        }.toSet()
    }

    /**
     * Hands [apk] to the system installer. Without the user's "install unknown apps"
     * permission for this app, opens that setting instead (the user decides).
     */
    fun install(context: Context, apk: File) {
        val pm = context.packageManager
        if (!pm.canRequestPackageInstalls()) {
            Toast.makeText(context, "Allow XenDroid to install updates, then tap Download and install again",
                Toast.LENGTH_LONG).show()
            context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        val installer = pm.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
        }
        val id = installer.createSession(params)
        try {
            installer.openSession(id).use { session ->
                session.openWrite("base.apk", 0, apk.length()).use { out ->
                    apk.inputStream().use { it.copyTo(out) }
                    session.fsync(out)
                }
                val intent = Intent(context, UpdateInstallReceiver::class.java)
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                    (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
                session.commit(PendingIntent.getBroadcast(context, id, intent, flags).intentSender)
            }
        } catch (e: Exception) {
            runCatching { installer.abandonSession(id) }
            throw e
        }
    }

    internal fun log(message: String) = Log.i(TAG, message)
}

/** Receives the installer's status: shows the confirmation screen, reports failures. */
class UpdateInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                else intent.getParcelableExtra(Intent.EXTRA_INTENT)
                confirm?.let { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
            PackageInstaller.STATUS_SUCCESS -> UpdateInstaller.log("Update installed")
            else -> {
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                UpdateInstaller.log("Update not installed: status $status ${message.orEmpty()}")
                Toast.makeText(context, "Update not installed${message?.let { ": $it" }.orEmpty()}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
