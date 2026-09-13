package com.user.jarvis

import android.content.Context
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class ListActivity : AppCompatActivity() {

    private lateinit var container: LinearLayout
    private lateinit var prefs: android.content.SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_list)
        container = findViewById(R.id.listContainer)
        prefs = getSharedPreferences("jarvis_notes", Context.MODE_PRIVATE)
        renderList()
    }

    private fun renderList() {
        container.removeAllViews()
        addSectionHeader("NOTES")
        val notes = prefs.getStringSet("notes", setOf())?.toList() ?: listOf()
        if (notes.isEmpty()) addEmptyLabel("Koi note nahi hai")
        notes.forEach { note -> addItemRow(note) { removeItem("notes", note) } }

        addSectionHeader("TODOS")
        val todos = prefs.getStringSet("todos", setOf())?.toList() ?: listOf()
        if (todos.isEmpty()) addEmptyLabel("Koi todo nahi hai")
        todos.forEach { todo -> addItemRow(todo) { removeItem("todos", todo) } }
    }

    private fun removeItem(key: String, value: String) {
        val current = prefs.getStringSet(key, mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        current.remove(value)
        prefs.edit().putStringSet(key, current).apply()
        renderList()
    }

    private fun addSectionHeader(title: String) {
        val label = TextView(this).apply {
            text = title
            setTextColor(getColorCompat(R.color.accent_cyan_dim))
            textSize = 12f
            letterSpacing = 0.12f
            setPadding(0, 24, 0, 8)
        }
        container.addView(label)
    }

    private fun addEmptyLabel(text: String) {
        val label = TextView(this).apply {
            this.text = text
            setTextColor(getColorCompat(R.color.text_secondary))
            textSize = 13f
            setPadding(0, 0, 0, 8)
        }
        container.addView(label)
    }

    private fun addItemRow(text: String, onDelete: () -> Unit) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundResource(R.drawable.status_card_bg)
            setPadding(20, 16, 20, 16)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 10) }
        }
        val textView = TextView(this).apply {
            this.text = text
            setTextColor(getColorCompat(R.color.text_primary))
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val deleteButton = TextView(this).apply {
            this.text = "✕"
            setTextColor(getColorCompat(R.color.stop_red))
            textSize = 16f
            setPadding(20, 0, 0, 0)
            setOnClickListener { onDelete() }
        }
        row.addView(textView)
        row.addView(deleteButton)
        container.addView(row)
    }

    private fun getColorCompat(resId: Int) = ContextCompat.getColor(this, resId)
}
