package com.user.jarvis

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WakeWordEngineManagerTest {

    private class FakeDetector : IWakeWordDetector {
        var startCalled = false
        var stopCalled = false
        var releaseCalled = false
        var simulateStartFailure = false
        var lastOnDetection: ((Float) -> Unit)? = null

        override fun startDetector(modelPath: String, onDetection: (score: Float) -> Unit): Boolean {
            startCalled = true
            lastOnDetection = onDetection
            return !simulateStartFailure
        }

        override fun stopDetector() {
            stopCalled = true
        }

        override fun releaseDetector() {
            releaseCalled = true
        }
    }

    private lateinit var detector: FakeDetector
    private var callbackFired = false
    private var modelPathResult: String? = "dummy/path.onnx"
    private var modelSizeResult: Long = 1000L

    private lateinit var manager: WakeWordEngineManager

    @Before
    fun setup() {
        detector = FakeDetector()
        callbackFired = false
        modelPathResult = "dummy/path.onnx"
        modelSizeResult = 1000L

        manager = WakeWordEngineManager(
            modelProvider = { Pair(modelPathResult, modelSizeResult) },
            detector = detector,
            onWakeWord = { callbackFired = true }
        )
    }

    @Test
    fun testInitializationFailure_MissingModel_ReturnsFalse() {
        modelPathResult = null
        manager.start()

        assertFalse(detector.startCalled)
        assertFalse("Engine should not be running if model path is missing", manager.isRunning())
    }

    @Test
    fun testInitializationFailure_EmptyModel_ReturnsFalse() {
        modelSizeResult = 0L
        manager.start()

        assertFalse(detector.startCalled)
        assertFalse("Engine should not be running if model file is empty", manager.isRunning())
    }

    @Test
    fun testStartFailure_UnderlyingDetectorFails() {
        detector.simulateStartFailure = true
        manager.start()

        assertTrue(detector.startCalled)
        assertFalse("Engine should not be running if detector fails to start", manager.isRunning())
    }

    @Test
    fun testStartSuccess_IsRunningTrue() {
        manager.start()

        assertTrue(detector.startCalled)
        assertTrue(manager.isRunning())
    }

    @Test
    fun testIsRunning_FalseWhenStopCalled() {
        manager.start()
        manager.stop()

        assertTrue(detector.stopCalled)
        assertFalse(manager.isRunning())
    }

    @Test
    fun testIsRunning_FalseWhenReleaseCalled() {
        manager.start()
        manager.release()

        assertTrue(detector.stopCalled)
        assertTrue(detector.releaseCalled)
        assertFalse(manager.isRunning())
    }

    @Test
    fun testDetectionCallback_TriggersActionWhenRunning() {
        manager.start()
        detector.lastOnDetection?.invoke(0.6f) // Score > 0.5f triggers callback

        assertTrue(callbackFired)
    }

    @Test
    fun testDetectionCallback_IgnoredWhenStopped() {
        manager.start()
        manager.stop()
        detector.lastOnDetection?.invoke(0.6f)

        assertFalse("Callback should be ignored if manager is not running", callbackFired)
    }

    @Test
    fun testDetectionCallback_IgnoredLowScore() {
        manager.start()
        detector.lastOnDetection?.invoke(0.3f) // Score < 0.5f

        assertFalse("Callback should not fire on low confidence score", callbackFired)
    }
}
