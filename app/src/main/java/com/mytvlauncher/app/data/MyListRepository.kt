package com.mytvlauncher.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray

/**
 * "My List": a cross-app watchlist. Hold OK on any show (Home rows, search
 * results) or use the spotlight's My List button to save or remove it.
 * Newest first, stored as JSON in SharedPreferences.
 */
class MyListRepository(context: Context) {

    private val prefs = context.getSharedPreferences("my_list", Context.MODE_PRIVATE)
    private val _items = MutableStateFlow(read())
    val items: StateFlow<List<ContentItem>> = _items

    fun contains(item: ContentItem): Boolean = _items.value.any { it.id == item.id }

    /** Returns true if the item is now in the list. */
    fun toggle(item: ContentItem): Boolean {
        val current = _items.value
        val now = if (current.any { it.id == item.id }) current.filterNot { it.id == item.id } else listOf(item) + current
        _items.value = now
        val arr = JSONArray()
        now.forEach { arr.put(it.toJson()) }
        prefs.edit().putString("items", arr.toString()).apply()
        return now.any { it.id == item.id }
    }

    private fun read(): List<ContentItem> = try {
        val arr = JSONArray(prefs.getString("items", "[]"))
        (0 until arr.length()).map { contentItemFromJson(arr.getJSONObject(it)) }
    } catch (e: Exception) { emptyList() }
}
