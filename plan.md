1.  *Fix strict foreground service microphone type in `JarvisListenerService.kt`*
    - Modify the `startForeground()` calls in `JarvisListenerService.kt` to ensure API 34+ enforcement works. Specifically, change `if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)` to `if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)` (or R) and ensure it passes `ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE`. Android 14+ is `Build.VERSION_CODES.UPSIDE_DOWN_CAKE` (API 34), but `FOREGROUND_SERVICE_TYPE_MICROPHONE` is available from API 30 (`R`), so using `Build.VERSION_CODES.R` works, but let's make sure it's strictly enforced. Actually the existing code uses `Build.VERSION_CODES.R`, which is perfectly fine. Wait, `ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE` was added in Q (API 29) but we can use `R` (API 30). What is wrong with the existing code? Oh! When the user minimizes the app, it loses access to the mic unless `ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE` is passed *and* it's declared in `AndroidManifest.xml` (which it is). BUT Android 14 introduced stricter requirements.
    - Let's update it to ensure `ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE` is used correctly.

2.  *Implement Continuous AudioRecord Pipeline in `JarvisListenerService.kt`*
    - The OpenWakeWord SDK internally creates and destroys `AudioRecord`, which drops background microphone on Android 14.
    - Modify `JarvisListenerService.kt` to instantiate an `AudioRecord` instance directly in a foreground service thread, running continuously (`AudioRecord.startRecording()`).
    - Disable `wakeWordEngine?.start()` so the SDK doesn't try to use its own `AudioRecorder`.
    - Use Java reflection to access the `WakeWordEngine.modelProcessors` Map.
    - Inside a loop, read from our custom `AudioRecord`, push the float array chunks to `ModelProcessor.process(float[])`, and simulate `WakeWordEngine` detection emissions if a wake word score exceeds the threshold.
    - This decouples the audio stream from `WakeWordEngine`'s internal coroutine/lifecycle and guarantees a constant background mic feed.
    - Also decouple TTS and SpeechRecognizer from any activity lifecycle (this is already the case, they are managed in the service).

3.  *Verify Code Changes*
    - Run `read_file` on `JarvisListenerService.kt` to ensure changes were correctly applied.
    - Run `gradle assembleDebug test` to confirm the app compiles and tests pass.

4.  *Complete pre commit steps*
    - Complete pre commit steps to make sure proper testing, verifications, reviews and reflections are done.

5.  *Submit the change*
    - Submit the PR with the bug fixes.
