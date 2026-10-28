package com.yg.liquidink.data

import android.content.Context
import com.yg.liquidink.core.RecentDocument
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Small, synchronous recent-document store. The list is intentionally capped so
 * SharedPreferences remains a sensible place for it.
 */
class RecentDocumentStore(context: Context) {

    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    private val _recent = MutableStateFlow(load())
    val recent: StateFlow<List<RecentDocument>> = _recent.asStateFlow()

    fun markOpened(document: RecentDocument) {
        val updated = buildList {
            add(document)
            addAll(_recent.value.filterNot { it.uri == document.uri })
        }.take(MAX_ENTRIES)
        persist(updated)
    }

    fun updateUri(oldUri: String, replacement: RecentDocument) {
        val updated = _recent.value
            .filterNot { it.uri == oldUri || it.uri == replacement.uri }
            .toMutableList()
        updated.add(0, replacement)
        persist(updated.take(MAX_ENTRIES))
    }

    fun rename(uri: String, newUri: String, newName: String) {
        val updated = _recent.value.map { item ->
            if (item.uri == uri) {
                item.copy(uri = newUri, displayName = newName)
            } else {
                item
            }
        }
        persist(updated)
    }

    fun remove(uri: String) {
        persist(_recent.value.filterNot { it.uri == uri })
    }

    fun clear() {
        persist(emptyList())
    }

    private fun persist(documents: List<RecentDocument>) {
        val array = JSONArray()
        documents.forEach { document ->
            array.put(
                JSONObject().apply {
                    put("uri", document.uri)
                    put("name", document.displayName)
                    put("opened", document.lastOpened)
                    put("size", document.sizeBytes)
                    put("encoding", document.encodingLabel)
                },
            )
        }
        preferences.edit().putString(KEY_DOCUMENTS, array.toString()).apply()
        _recent.value = documents
    }

    private fun load(): List<RecentDocument> {
        val raw = preferences.getString(KEY_DOCUMENTS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val uri = item.optString("uri")
                    val name = item.optString("name")
                    if (uri.isBlank() || name.isBlank()) continue
                    add(
                        RecentDocument(
                            uri = uri,
                            displayName = name,
                            lastOpened = item.optLong("opened", 0L),
                            sizeBytes = item.optLong("size", 0L),
                            encodingLabel = item.optString("encoding", "UTF-8"),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private companion object {
        const val PREFERENCES_NAME = "liquid_ink_recent"
        const val KEY_DOCUMENTS = "documents"
        const val MAX_ENTRIES = 12
    }
}
