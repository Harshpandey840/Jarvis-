package com.user.jarvis.gestures.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.user.jarvis.R
import com.user.jarvis.gestures.models.GestureAction
import com.user.jarvis.gestures.models.GestureConfig

class CustomGesturesActivity : AppCompatActivity() {

    private lateinit var viewModel: GestureSettingsViewModel
    private lateinit var adapter: GesturesAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_custom_gestures)

        viewModel = ViewModelProvider(this).get(GestureSettingsViewModel::class.java)

        val recyclerView = findViewById<RecyclerView>(R.id.recyclerViewGestures)
        recyclerView.layoutManager = LinearLayoutManager(this)

        adapter = GesturesAdapter(
            onToggle = { id, enabled -> viewModel.toggleGesture(id, enabled) },
            onActionChange = { id, action -> viewModel.updateGestureAction(id, action) }
        )
        recyclerView.adapter = adapter

        viewModel.gestures.observe(this) { gestures ->
            adapter.submitList(gestures)
        }

        findViewById<Button>(R.id.btnResetDefaults).setOnClickListener {
            viewModel.resetToDefaults()
        }

        findViewById<Button>(R.id.btnAddGesture).setOnClickListener {
            // Future implementation for adding entirely new gestures with custom shapes
        }
    }

    class GesturesAdapter(
        private val onToggle: (String, Boolean) -> Unit,
        private val onActionChange: (String, GestureAction) -> Unit
    ) : RecyclerView.Adapter<GesturesAdapter.ViewHolder>() {

        private var gestures: List<GestureConfig> = emptyList()

        fun submitList(list: List<GestureConfig>) {
            gestures = list
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_gesture_card, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val gesture = gestures[position]
            holder.bind(gesture, onToggle, onActionChange)
        }

        override fun getItemCount(): Int = gestures.size

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            private val tvName: TextView = view.findViewById(R.id.tvGestureName)
            private val tvShape: TextView = view.findViewById(R.id.tvGestureShape)
            private val tvDirection: TextView = view.findViewById(R.id.tvGestureDirection)
            private val switchEnable: Switch = view.findViewById(R.id.switchGestureEnable)
            private val spinnerAction: Spinner = view.findViewById(R.id.spinnerAction)

            fun bind(
                gesture: GestureConfig,
                onToggle: (String, Boolean) -> Unit,
                onActionChange: (String, GestureAction) -> Unit
            ) {
                tvName.text = gesture.name
                tvShape.text = gesture.handShape.name
                tvDirection.text = gesture.direction.name

                switchEnable.setOnCheckedChangeListener(null)
                switchEnable.isChecked = gesture.enabled
                switchEnable.setOnCheckedChangeListener { _, isChecked ->
                    onToggle(gesture.id, isChecked)
                }

                val actions = GestureAction.values().map { it.name }
                val adapter = ArrayAdapter(itemView.context, android.R.layout.simple_spinner_item, actions)
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                spinnerAction.adapter = adapter

                val currentActionIndex = actions.indexOf(gesture.action.name)
                if (currentActionIndex >= 0) spinnerAction.setSelection(currentActionIndex)

                spinnerAction.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                        val selectedAction = GestureAction.valueOf(actions[pos])
                        if (selectedAction != gesture.action) {
                            onActionChange(gesture.id, selectedAction)
                        }
                    }
                    override fun onNothingSelected(parent: AdapterView<*>?) {}
                }
            }
        }
    }
}