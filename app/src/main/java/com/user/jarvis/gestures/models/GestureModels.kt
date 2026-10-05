package com.user.jarvis.gestures.models

enum class HandShape {
    ROCK_ON,
    PEACE,
    PALM,
    THREE_FINGERS,
    POINTING,
    PINCH,
    UNKNOWN
}

enum class Direction {
    UP,
    DOWN,
    LEFT,
    RIGHT,
    NONE
}

enum class HandMode {
    SINGLE,
    DUAL
}

enum class GestureAction {
    LOCK_SCREEN,
    VOLUME_UP,
    VOLUME_DOWN,
    SCROLL_UP,
    SCROLL_DOWN,
    BACK,
    HOME,
    RECENTS,
    SCREENSHOT,
    PLAY_PAUSE,
    CUSTOM_COMMAND,
    NONE
}

data class GestureConfig(
    val id: String,
    val name: String,
    val handShape: HandShape,
    val direction: Direction,
    val action: GestureAction,
    var enabled: Boolean,
    val handMode: HandMode = HandMode.SINGLE
)