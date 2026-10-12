package io.github.akrishna87.jotter

import android.content.Context
import android.net.Uri
import io.github.akrishna87.jotter.model.Data
import org.json.JSONObject

/** Saves everything to a JSON file the user picks, and reads one back. */
object Backup {
    fun export(context: Context, uri: Uri): Boolean = runCatching {
        val json = Store.data.value.toJson().toString(2)
        context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(json.toByteArray()) }
        true
    }.getOrDefault(false)

    /** Reads a backup; null if the file isn't one. */
    fun read(context: Context, uri: Uri): Data? = runCatching {
        val text = context.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() }
        val o = JSONObject(text)
        if (!o.has("notes")) null else Data.fromJson(o)
    }.getOrNull()
}
