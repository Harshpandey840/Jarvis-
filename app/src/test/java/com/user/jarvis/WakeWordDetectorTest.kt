package com.user.jarvis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WakeWordDetectorTest {

    @Test
    fun stripWakeWord_withBlankText_returnsNull() {
        assertNull(WakeWordDetector.stripWakeWord(""))
        assertNull(WakeWordDetector.stripWakeWord("   "))
    }

    @Test
    fun stripWakeWord_withValidWakeWord_returnsEmptyString() {
        assertEquals("", WakeWordDetector.stripWakeWord("hey jarvis"))
        assertEquals("", WakeWordDetector.stripWakeWord("jarvis"))
    }

    @Test
    fun stripWakeWord_withValidWakeWordAndText_returnsRemainingText() {
        assertEquals("turn on the lights", WakeWordDetector.stripWakeWord("hey jarvis turn on the lights"))
        assertEquals("what is the weather", WakeWordDetector.stripWakeWord("jarvis what is the weather"))
    }

    @Test
    fun stripWakeWord_withInvalidWakeWord_returnsNull() {
        assertNull(WakeWordDetector.stripWakeWord("turn on the lights"))
        assertNull(WakeWordDetector.stripWakeWord("hello world"))
    }

    @Test
    fun stripWakeWord_withPunctuation_returnsRemainingText() {
        assertEquals("turn on the lights", WakeWordDetector.stripWakeWord("hey jarvis, turn on the lights!"))
    }

    @Test
    fun stripWakeWord_withDifferentCase_returnsRemainingText() {
        assertEquals("turn on the lights", WakeWordDetector.stripWakeWord("Hey Jarvis turn on the lights"))
    }

    @Test
    fun stripWakeWord_withHindiWakeWord_returnsEmptyString() {
        assertEquals("", WakeWordDetector.stripWakeWord("हे जार्विस"))
        assertEquals("", WakeWordDetector.stripWakeWord("जार्विस"))
    }

    @Test
    fun stripWakeWord_withHindiWakeWordAndText_returnsRemainingText() {
        assertEquals("क्या समय हुआ है", WakeWordDetector.stripWakeWord("हे जार्विस क्या समय हुआ है"))
    }
}
