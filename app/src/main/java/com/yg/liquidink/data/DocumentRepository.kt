package com.yg.liquidink.data

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import com.yg.liquidink.core.DecodedText
import com.yg.liquidink.core.TextCodec
import com.yg.liquidink.core.TextEncoding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

class DocumentException(message: String, cause: Throwable? = null) : IOException(message, cause)

data class DocumentMetadata(
    val uri: Uri,
    val displayName: String,
    val sizeBytes: Long,
    val lastModified: Long,
)

data class LoadedDocument(
    val metadata: DocumentMetadata,
    val text: String,
    val encoding: TextEncoding,
)

/**
 * Storage Access Framework boundary. All heavy byte work happens on IO.
 *
 * SAF does not expose an atomic rename/replace primitive, so writes go through
 * the provider's truncating stream. This is the best portable behaviour for a
 * document viewer without asking for broad storage permissions.
 */
class DocumentRepository(context: Context) {

    private val appContext = context.applicationContext
    private val resolver: ContentResolver = appContext.contentResolver

    fun persistPermission(uri: Uri) {
        try {
            resolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        } catch (_: SecurityException) {
            try {
                resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: SecurityException) {
                // Some providers (and transient VIEW intents) are not persistable.
                // The document remains readable for this process lifetime.
            }
        }
    }

    suspend fun read(uri: Uri): LoadedDocument = withContext(Dispatchers.IO) {
        val metadata = queryMetadata(uri)
        if (metadata.sizeBytes > MAX_DOCUMENT_BYTES) {
            throw DocumentException("文件超过 32 MB，为保护内存已停止打开。")
        }

        val bytes = try {
            resolver.openInputStream(uri)?.use { input -> readCapped(input) }
                ?: throw DocumentException("无法打开文件流，请检查文件访问权限。")
        } catch (exception: SecurityException) {
            throw DocumentException("没有读取该文件的权限，请重新选择文件。", exception)
        } catch (exception: FileNotFoundException) {
            throw DocumentException("文件不存在或已被移动。", exception)
        } catch (exception: IOException) {
            throw DocumentException(
                exception.message ?: "读取文件失败，请稍后重试。",
                exception,
            )
        }

        val decoded: DecodedText = TextCodec.decode(bytes)
        val finalMetadata = metadata.copy(sizeBytes = bytes.size.toLong())
        LoadedDocument(
            metadata = finalMetadata,
            text = decoded.text,
            encoding = decoded.encoding,
        )
    }

    suspend fun metadata(uri: Uri): DocumentMetadata = withContext(Dispatchers.IO) {
        queryMetadata(uri)
    }

    suspend fun write(uri: Uri, text: String, encoding: TextEncoding) {
        val bytes = TextCodec.encode(text, encoding)
        withContext(Dispatchers.IO) {
            var lastError: Throwable? = null
            var written = false
            for (mode in listOf("wt", "w")) {
                try {
                    val stream = resolver.openOutputStream(uri, mode) ?: continue
                    stream.use { output ->
                        output.write(bytes)
                        output.flush()
                    }
                    written = true
                    break
                } catch (exception: SecurityException) {
                    lastError = DocumentException("没有写入该文件的权限，请使用“另存为”。", exception)
                } catch (exception: FileNotFoundException) {
                    lastError = exception
                } catch (exception: IOException) {
                    lastError = exception
                }
            }
            if (!written) {
                throw DocumentException(
                    "保存失败：${lastError?.message ?: "存储提供程序不可写"}",
                    lastError,
                )
            }
        }
    }

    suspend fun rename(uri: Uri, newName: String): DocumentMetadata = withContext(Dispatchers.IO) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty() || trimmed.contains('/') || trimmed.contains('\\')) {
            throw DocumentException("文件名不能为空，也不能包含斜杠。")
        }
        val renamed = try {
            DocumentsContract.renameDocument(resolver, uri, trimmed)
        } catch (exception: Exception) {
            throw DocumentException(
                "当前存储提供程序不支持重命名，请使用“另存为”创建新文件。",
                exception,
            )
        } ?: throw DocumentException("重命名失败，存储提供程序返回空结果。")
        persistPermission(renamed)
        queryMetadata(renamed)
    }

    suspend fun delete(uri: Uri) = withContext(Dispatchers.IO) {
        try {
            val deleted = DocumentsContract.deleteDocument(resolver, uri)
            if (!deleted) throw DocumentException("存储提供程序拒绝删除该文件。")
        } catch (exception: DocumentException) {
            throw exception
        } catch (exception: Exception) {
            throw DocumentException("删除失败，请检查文件权限。", exception)
        }
    }

    private fun queryMetadata(uri: Uri): DocumentMetadata {
        var name: String? = null
        var size = -1L
        try {
            queryCursor(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIndex >= 0) name = cursor.getString(nameIndex)
                    if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                        size = cursor.getLong(sizeIndex)
                    }
                }
            }
        } catch (_: Exception) {
            // Fall through to a path-derived name. Some virtual providers do not
            // expose OpenableColumns.
        }

        var lastModified = 0L
        try {
            queryCursor(
                uri,
                arrayOf(DocumentsContract.Document.COLUMN_LAST_MODIFIED),
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                    if (index >= 0 && !cursor.isNull(index)) lastModified = cursor.getLong(index)
                }
            }
        } catch (_: Exception) {
            // Optional metadata.
        }

        val fallbackName = uri.lastPathSegment
            ?.substringAfterLast('/')
            ?.takeIf { it.isNotBlank() }
            ?: "未命名.md"
        return DocumentMetadata(
            uri = uri,
            displayName = name?.takeIf { it.isNotBlank() } ?: fallbackName,
            sizeBytes = size,
            lastModified = lastModified,
        )
    }

    private fun queryCursor(uri: Uri, projection: Array<String>): Cursor? =
        resolver.query(uri, projection, null, null, null)

    private fun readCapped(input: InputStream): ByteArray {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        val output = ByteArrayOutputStream()
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > MAX_DOCUMENT_BYTES) {
                throw DocumentException("文件超过 32 MB，为保护内存已停止打开。")
            }
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    companion object {
        const val MAX_DOCUMENT_BYTES = 32L * 1024L * 1024L
        val SUPPORTED_EXTENSIONS = setOf("md", "markdown", "txt")

        fun hasSupportedExtension(displayName: String): Boolean {
            val extension = displayName.substringAfterLast('.', missingDelimiterValue = "")
            return extension.isEmpty() || extension.lowercase() in SUPPORTED_EXTENSIONS
        }

        fun hasSupportedMimeType(mimeType: String?): Boolean {
            if (mimeType == null) return true
            return mimeType.startsWith("text/") ||
                mimeType == "application/octet-stream" ||
                mimeType == "application/x-markdown" ||
                mimeType == "application/markdown"
        }
    }
}
