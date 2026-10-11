import re

def update_file():
    with open('app/src/main/java/com/user/jarvis/JarvisListenerService.kt', 'r') as f:
        content = f.read()

    # Update CommandExecutor initialization in onCreate
    init_orig = """            commandExecutor = CommandExecutor(
                applicationContext,
                { text ->
                    handler.post {
                        speak(text)
                    }
                }
            )"""
    init_new = """            commandExecutor = CommandExecutor(
                applicationContext,
                onSpeak = { text ->
                    handler.post {
                        speak(text)
                    }
                },
                onFinished = { isSilent ->
                    handler.post {
                        if (isSilent) {
                            // Command finished silently, jump straight to next listening phase
                            speakThenListenForCommand = true
                            // Since no TTS will trigger the onDone callback, we manually trigger the transition
                            if (serviceActive) {
                                startCommandListening()
                            }
                        } else {
                            // Command finished but spoke something. The TTS onDone callback will handle the transition.
                        }
                    }
                }
            )"""
    content = content.replace(init_orig, init_new)

    # Update executeCommand to not assume TTS completion triggers the loop.
    # Instead, we just execute, and rely on the new `onFinished` callback.
    exec_orig = """    private fun executeCommand(
        text: String
    ) {
        if (text.equals("band ho jao", ignoreCase = true) || text.equals("stop", ignoreCase = true) || text.equals("bas karo", ignoreCase = true)) {
            speakThenListenForCommand = false
            speak("Theek hai, main standby mode mein jaa raha hoon.")
            return
        }

        try {
            // Re-trigger listening after execute by simulating a callback
            // In a real scenario, the callback would be invoked when the response from execution completes

            // To simulate conversation, we will wait for TTS to complete, and the onUtteranceProgress will trigger startCommandListening because we set speakThenListenForCommand = true
            speakThenListenForCommand = true
            commandExecutor?.execute(text)

        } catch (e: Exception) {

            android.util.Log.e(
                TAG,
                "Command execution failed",
                e
            )

            speakThenListenForCommand = true
            speak(
                "Command execute nahi ho paya."
            )
        }
    }"""
    exec_new = """    private fun executeCommand(
        text: String
    ) {
        if (text.equals("band ho jao", ignoreCase = true) || text.equals("stop", ignoreCase = true) || text.equals("bas karo", ignoreCase = true)) {
            speakThenListenForCommand = false
            speak("Theek hai, main standby mode mein jaa raha hoon.")
            return
        }

        try {
            // We set the flag so if TTS is generated, onDone will loop back.
            // If the command is silent, the executor's onFinished callback will manually trigger the loop.
            speakThenListenForCommand = true
            commandExecutor?.execute(text)

        } catch (e: Exception) {
            android.util.Log.e(TAG, "Command execution failed", e)
            speakThenListenForCommand = true
            speak("Command execute nahi ho paya.")
        }
    }"""
    content = content.replace(exec_orig, exec_new)

    with open('app/src/main/java/com/user/jarvis/JarvisListenerService.kt', 'w') as f:
        f.write(content)

update_file()
