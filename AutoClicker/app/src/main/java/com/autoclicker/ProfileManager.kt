package com.autoclicker

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Saves / loads ClickPoint lists as JSON files in the app's private files dir.
 * Each profile is stored as  profiles/<name>.json
 */
object ProfileManager {

    private const val DIR = "profiles"

    fun save(context: Context, name: String, points: List<ClickPoint>) {
        val arr = JSONArray()
        for (p in points) {
            arr.put(JSONObject().apply {
                put("id",    p.id)
                put("label", p.label)
                put("x",     p.x)
                put("y",     p.y)
                put("delay", p.delayMs)
            })
        }
        dir(context).mkdirs()
        file(context, name).writeText(arr.toString())
    }

    fun load(context: Context, name: String): List<ClickPoint> {
        val f = file(context, name)
        if (!f.exists()) return emptyList()
        return try {
            val arr = JSONArray(f.readText())
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                ClickPoint(
                    id      = obj.getLong("id"),
                    label   = obj.getString("label"),
                    x       = obj.getInt("x"),
                    y       = obj.getInt("y"),
                    delayMs = obj.getLong("delay")
                )
            }
        } catch (e: Exception) { emptyList() }
    }

    fun list(context: Context): List<String> =
        dir(context).listFiles()
            ?.filter { it.extension == "json" }
            ?.map { it.nameWithoutExtension }
            ?.sorted()
            ?: emptyList()

    fun delete(context: Context, name: String) {
        file(context, name).delete()
    }

    private fun dir(context: Context)                = File(context.filesDir, DIR)
    private fun file(context: Context, name: String) = File(dir(context), "$name.json")
}
