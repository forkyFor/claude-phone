package io.github.forkyfor.oldphone

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri

/**
 * NotebookLM (Google) does the transcription and the summary, in the owner's own Google account
 * and app. This app only hands it the recording; it never talks to NotebookLM's servers.
 */
object NotebookLm {
    /** Play Store package of the NotebookLM app (also looked up by name, in case it changes). */
    const val PACKAGE = "com.google.android.apps.labs.language.tailwind"

    private fun installed(pm: PackageManager, pkg: String) = try {
        pm.getPackageInfo(pkg, 0)
        true
    } catch (e: Exception) {
        false
    }

    fun find(ctx: Context): String? {
        val pm = ctx.packageManager
        if (installed(pm, PACKAGE)) return PACKAGE
        val byName = { intent: Intent ->
            try {
                pm.queryIntentActivities(intent, 0).firstOrNull {
                    it.loadLabel(pm).toString().contains("NotebookLM", ignoreCase = true) ||
                        it.activityInfo.packageName.contains("notebooklm", ignoreCase = true)
                }?.activityInfo?.packageName
            } catch (e: Exception) {
                null
            }
        }
        return byName(Intent(Intent.ACTION_SEND).setType("audio/*"))
            ?: byName(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER))
    }

    private fun acceptsAudio(ctx: Context, pkg: String): Boolean = try {
        ctx.packageManager.queryIntentActivities(Intent(Intent.ACTION_SEND).setType("audio/mp4").setPackage(pkg), 0).isNotEmpty()
    } catch (e: Exception) {
        false
    }

    /**
     * Shares the recording. Straight to NotebookLM when it accepts audio from the Share menu,
     * otherwise through Android's share sheet so the owner can pick it (or another app).
     */
    fun share(a: Activity, audio: Uri, title: String): Boolean {
        val send = Intent(Intent.ACTION_SEND).setType("audio/mp4")
            .putExtra(Intent.EXTRA_STREAM, audio)
            .putExtra(Intent.EXTRA_TITLE, title)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = ClipData.newRawUri(title, audio)
        val pkg = find(a)
        val intent = if (pkg != null && acceptsAudio(a, pkg)) send.setPackage(pkg) else Intent.createChooser(send, title)
        return try {
            a.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun storePage(): Intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$PACKAGE"))
}
