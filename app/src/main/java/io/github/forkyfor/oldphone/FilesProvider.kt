package io.github.forkyfor.oldphone

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import java.io.File

/**
 * Minimal private file provider (not exported). It hands the camera app a place to write a
 * photo and hands the web engine the photos and shared files waiting to be attached.
 * Files live in the app cache and are deleted after a day.
 */
class FilesProvider : ContentProvider() {
    override fun onCreate() = true

    private fun fileFor(uri: Uri): File {
        val name = uri.lastPathSegment ?: throw IllegalArgumentException("no name")
        if (name.contains('/') || name.contains("..")) throw SecurityException("bad name")
        // content://<app>.files/attach/<name> or .../dl/<name> (downloads saved by the Gecko engine)
        val base = if (uri.pathSegments.firstOrNull() == "dl") downloads(context!!) else dir(context!!)
        return File(base, name)
    }

    override fun getType(uri: Uri): String =
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileFor(uri).extension.lowercase())
            ?: "application/octet-stream"

    override fun query(uri: Uri, projection: Array<String>?, s: String?, a: Array<String>?, o: String?): Cursor {
        val f = fileFor(uri)
        val cols = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        val row = cols.map {
            when (it) {
                OpenableColumns.DISPLAY_NAME -> f.name
                OpenableColumns.SIZE -> f.length()
                else -> null
            }
        }
        return MatrixCursor(cols, 1).apply { addRow(row) }
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val f = fileFor(uri)
        val flags = if (mode.contains('w')) {
            ParcelFileDescriptor.MODE_READ_WRITE or ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE
        } else {
            ParcelFileDescriptor.MODE_READ_ONLY
        }
        return ParcelFileDescriptor.open(f, flags)
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, s: String?, a: Array<String>?): Int = 0
    override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<String>?): Int = 0

    companion object {
        private const val DAY_MS = 24L * 60 * 60 * 1000

        fun dir(ctx: Context): File = File(ctx.cacheDir, "attach").apply { mkdirs() }

        fun downloads(ctx: Context): File =
            ctx.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS) ?: dir(ctx)

        fun uriFor(ctx: Context, file: File): Uri {
            val root = if (file.parentFile == dir(ctx)) "attach" else "dl"
            return Uri.parse("content://${ctx.packageName}.files/$root/${Uri.encode(file.name)}")
        }

        fun newFile(ctx: Context, displayName: String): File {
            val safe = displayName.replace(Regex("[^A-Za-z0-9._-]"), "_").takeLast(60).ifEmpty { "file" }
            return File(dir(ctx), "${System.currentTimeMillis()}_$safe")
        }

        fun purgeOld(ctx: Context) {
            val limit = System.currentTimeMillis() - DAY_MS
            dir(ctx).listFiles()?.forEach { if (it.lastModified() < limit) it.delete() }
        }
    }
}
