package app.ultragrid

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import org.json.JSONArray
import org.json.JSONObject

data class PlanItem(val uri: Uri, val posted: Boolean = false)

/**
 * Upcoming posts laid out like an Instagram profile. Index 0 is top-left, i.e. the newest
 * post. Saved to SharedPreferences so the plan survives restarts.
 */
class PlannerViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("planner", Context.MODE_PRIVATE)

    var items by mutableStateOf(load())
        private set
    var selected by mutableStateOf<Int?>(null)
        private set
    var message by mutableStateOf<String?>(null)
        private set

    /** Adds [uris] to the top of the plan, keeping their order. */
    fun add(uris: List<Uri>) {
        if (uris.isEmpty()) return
        uris.forEach(::keepAccess)
        update(uris.map { PlanItem(it) } + items)
        selected = null
        message = if (uris.size == 1) "Added 1 photo to the planner" else "Added ${uris.size} photos to the planner"
    }

    /** Selects a photo, or swaps it with the already selected one. */
    fun tap(index: Int) {
        val current = selected
        selected = when (current) {
            null -> index
            index -> null
            else -> {
                val list = items.toMutableList()
                list[current] = items[index]
                list[index] = items[current]
                update(list)
                null
            }
        }
    }

    fun removeSelected() {
        val index = selected ?: return
        update(items.filterIndexed { i, _ -> i != index })
        selected = null
    }

    fun togglePostedSelected() {
        val index = selected ?: return
        update(items.mapIndexed { i, item -> if (i == index) item.copy(posted = !item.posted) else item })
        selected = null
    }

    fun clearSelection() {
        selected = null
    }

    fun clearMessage() {
        message = null
    }

    private fun keepAccess(uri: Uri) {
        try {
            getApplication<Application>().contentResolver
                .takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (e: SecurityException) {
            // Our own MediaStore files need no grant; other URIs may only be readable this session.
        }
    }

    private fun update(list: List<PlanItem>) {
        items = list
        val json = JSONArray()
        list.forEach { json.put(JSONObject().put("uri", it.uri.toString()).put("posted", it.posted)) }
        prefs.edit().putString(KEY_ITEMS, json.toString()).apply()
    }

    private fun load(): List<PlanItem> {
        val raw = prefs.getString(KEY_ITEMS, null) ?: return emptyList()
        return try {
            val json = JSONArray(raw)
            (0 until json.length()).map {
                val item = json.getJSONObject(it)
                PlanItem(Uri.parse(item.getString("uri")), item.optBoolean("posted"))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private companion object {
        const val KEY_ITEMS = "items"
    }
}
