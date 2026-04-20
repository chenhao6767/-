package com.autoclicker

import android.app.AlertDialog
import android.content.Context
import android.view.LayoutInflater
import android.widget.*

object ProfileDialogHelper {

    /** Show a dialog to save the current points under a name. */
    fun showSave(context: Context, points: List<ClickPoint>, onSaved: (String) -> Unit) {
        val et = EditText(context).apply {
            hint = "输入方案名称"
            setPadding(48, 32, 48, 32)
        }
        AlertDialog.Builder(context)
            .setTitle("保存方案")
            .setView(et)
            .setPositiveButton("保存") { _, _ ->
                val name = et.text.toString().trim()
                if (name.isNotEmpty()) {
                    ProfileManager.save(context, name, points)
                    Toast.makeText(context, "已保存：$name", Toast.LENGTH_SHORT).show()
                    onSaved(name)
                } else {
                    Toast.makeText(context, "方案名不能为空", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /** Show a list of saved profiles; callback receives selected points. */
    fun showLoad(
        context: Context,
        onLoaded: (name: String, points: List<ClickPoint>) -> Unit
    ) {
        val profiles = ProfileManager.list(context)
        if (profiles.isEmpty()) {
            Toast.makeText(context, "没有保存的方案", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(context)
            .setTitle("加载方案")
            .setItems(profiles.toTypedArray()) { _, idx ->
                val name   = profiles[idx]
                val loaded = ProfileManager.load(context, name)
                onLoaded(name, loaded)
                Toast.makeText(context, "已加载：$name (${loaded.size} 个点)", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /** Show a list of saved profiles with a delete option. */
    fun showManage(context: Context, onChanged: () -> Unit) {
        val profiles = ProfileManager.list(context).toMutableList()
        if (profiles.isEmpty()) {
            Toast.makeText(context, "没有保存的方案", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(context)
            .setTitle("管理方案")
            .setItems(profiles.map { "🗑  $it" }.toTypedArray()) { _, idx ->
                AlertDialog.Builder(context)
                    .setTitle("删除方案")
                    .setMessage("确定删除「${profiles[idx]}」？")
                    .setPositiveButton("删除") { _, _ ->
                        ProfileManager.delete(context, profiles[idx])
                        Toast.makeText(context, "已删除", Toast.LENGTH_SHORT).show()
                        onChanged()
                    }
                    .setNegativeButton("取消", null)
                    .show()
            }
            .setNegativeButton("关闭", null)
            .show()
    }
}
