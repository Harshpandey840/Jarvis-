package com.user.jarvis.gestures

import android.content.Context
import android.content.SharedPreferences
import com.user.jarvis.gestures.models.Direction
import com.user.jarvis.gestures.models.GestureAction
import com.user.jarvis.gestures.models.GestureConfig
import com.user.jarvis.gestures.models.HandMode
import com.user.jarvis.gestures.models.HandShape
import org.json.JSONArray
import org.json.JSONObject

class GestureRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("gesture_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_GESTURES = "custom_gestures"

        val DEFAULT_GESTURES = listOf(
            GestureConfig("g1", "Lock Screen", HandShape.ROCK_ON, Direction.NONE, GestureAction.LOCK_SCREEN, true),
            GestureConfig("g2", "Volume Up", HandShape.PEACE, Direction.UP, GestureAction.VOLUME_UP, true),
            GestureConfig("g3", "Volume Down", HandShape.PEACE, Direction.DOWN, GestureAction.VOLUME_DOWN, true),
            GestureConfig("g4", "Scroll Up", HandShape.PALM, Direction.DOWN, GestureAction.SCROLL_UP, true),
            GestureConfig("g5", "Scroll Down", HandShape.PALM, Direction.UP, GestureAction.SCROLL_DOWN, true),
            GestureConfig("g6", "Click", HandShape.PINCH, Direction.NONE, GestureAction.NONE, true), // Internal click handling usually
            GestureConfig("g7", "Home", HandShape.PALM, Direction.NONE, GestureAction.HOME, true),
            GestureConfig("g8", "Back", HandShape.POINTING, Direction.LEFT, GestureAction.BACK, true)
        )
    }

    fun getGestures(): List<GestureConfig> {
        val jsonString = prefs.getString(KEY_GESTURES, null)
        if (jsonString == null) {
            saveGestures(DEFAULT_GESTURES)
            return DEFAULT_GESTURES
        }

        val list = mutableListOf<GestureConfig>()
        try {
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    GestureConfig(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        handShape = HandShape.valueOf(obj.getString("handShape")),
                        direction = Direction.valueOf(obj.getString("direction")),
                        action = GestureAction.valueOf(obj.getString("action")),
                        enabled = obj.getBoolean("enabled"),
                        handMode = HandMode.valueOf(obj.optString("handMode", HandMode.SINGLE.name))
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return DEFAULT_GESTURES
        }
        return list
    }

    fun saveGestures(gestures: List<GestureConfig>) {
        val jsonArray = JSONArray()
        for (g in gestures) {
            val obj = JSONObject().apply {
                put("id", g.id)
                put("name", g.name)
                put("handShape", g.handShape.name)
                put("direction", g.direction.name)
                put("action", g.action.name)
                put("enabled", g.enabled)
                put("handMode", g.handMode.name)
            }
            jsonArray.put(obj)
        }
        prefs.edit().putString(KEY_GESTURES, jsonArray.toString()).apply()
    }

    fun resetToDefaults() {
        saveGestures(DEFAULT_GESTURES)
    }

    fun getActionFor(handShape: HandShape, direction: Direction): GestureAction {
        val activeGestures = getGestures().filter { it.enabled }
        val matching = activeGestures.find { it.handShape == handShape && it.direction == direction }
        return matching?.action ?: GestureAction.NONE
    }
}