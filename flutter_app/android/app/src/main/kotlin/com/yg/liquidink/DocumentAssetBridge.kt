package com.yg.liquidink

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import io.flutter.plugin.common.MethodChannel
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.Executors

/** SAF sibling lookup and optional persistent folder grant. No storage permission
 * or network access is requested. Only the referenced image is copied locally. */
class DocumentAssetBridge(private val activity: Activity, channel: MethodChannel) {
    private var pending: MethodChannel.Result? = null
    private val worker = Executors.newSingleThreadExecutor()

    init {
        channel.setMethodCallHandler { call, result ->
            when (call.method) {
                "pickAssetDirectory" -> {
                    if (pending != null) {
                        result.error("BUSY", "A folder picker is already open", null)
                    } else {
                        pending = result
                        activity.startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
                        }, REQUEST)
                    }
                }
                "resolveAsset" -> {
                    val original = call.argument<String>("uri")
                    val tree = call.argument<String>("tree")
                    val relative = call.argument<String>("relative") ?: ""
                    worker.execute {
                        val path = try { resolve(original, tree, relative) } catch (_: Exception) { null }
                        activity.runOnUiThread { result.success(path) }
                    }
                }
                else -> result.notImplemented()
            }
        }
    }

    fun onActivityResult(request: Int, code: Int, intent: Intent?): Boolean {
        if (request != REQUEST) return false
        val result = pending
        pending = null
        val uri = if (code == Activity.RESULT_OK) intent?.data else null
        if (uri != null) {
            try {
                activity.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: SecurityException) { }
        }
        result?.success(uri?.toString())
        return true
    }

    private fun resolve(original: String?, tree: String?, relative: String): String? {
        if (relative.isBlank() || relative.startsWith('/') || relative.contains('\u0000')) return null
        val candidates = mutableListOf<Uri>()
        if (original != null) {
            try {
                val uri = Uri.parse(original)
                val id = DocumentsContract.getDocumentId(uri)
                val slash = id.lastIndexOf('/')
                if (slash >= 0) {
                    val prefix = id.substringBefore(':', "")
                    val idPath = if (prefix.isEmpty()) id else id.substringAfter(':')
                    val resolved = File("/" + idPath.substringBeforeLast('/'), relative).normalize().path.removePrefix("/")
                    val sibling = if (prefix.isEmpty()) resolved else "$prefix:$resolved"
                    candidates.add(DocumentsContract.buildDocumentUri(uri.authority, sibling))
                }
            } catch (_: Exception) { }
        }
        if (tree != null) {
            try { findInTree(Uri.parse(tree), relative)?.let(candidates::add) } catch (_: Exception) { }
        }
        for (uri in candidates) {
            try {
                val key = MessageDigest.getInstance("SHA-256").digest(uri.toString().toByteArray())
                    .joinToString("") { "%02x".format(it) }
                val folder = File(activity.cacheDir, "quikpad_images").apply { mkdirs() }
                val target = File(folder, key)
                val temporary = File(folder, "$key.part")
                activity.contentResolver.openInputStream(uri)?.use { input ->
                    temporary.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var total = 0L
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            total += count
                            if (total > 32L * 1024 * 1024) throw IllegalArgumentException("Image exceeds 32 MB")
                            output.write(buffer, 0, count)
                        }
                    }
                    if (temporary.renameTo(target)) return target.path
                }
                temporary.delete()
            } catch (_: Exception) { }
        }
        return null
    }

    private fun findInTree(tree: Uri, relative: String): Uri? {
        val segments = mutableListOf<String>()
        for (part in relative.replace('\\', '/').split('/')) {
            when (part) {
                "", "." -> Unit
                ".." -> if (segments.isEmpty()) return null else segments.removeAt(segments.lastIndex)
                else -> segments.add(part)
            }
        }
        var id = DocumentsContract.getTreeDocumentId(tree)
        for (segment in segments) {
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, id)
            var found: String? = null
            activity.contentResolver.query(children, arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    if (cursor.getString(1) == segment) { found = cursor.getString(0); break }
                }
            }
            id = found ?: return null
        }
        return DocumentsContract.buildDocumentUriUsingTree(tree, id)
    }

    companion object { private const val REQUEST = 6023 }
}
