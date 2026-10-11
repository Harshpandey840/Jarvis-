import re

def update_file():
    with open('app/src/main/java/com/user/jarvis/CommandExecutor.kt', 'r') as f:
        content = f.read()

    # Wrap onSpeak to call onFinished when TTS completes OR do we not want to assume TTS triggers it?
    # Actually, in runCommand, many branches do `onSpeak(...)` and DO NOT call `onFinished()`.
    # This means the listening state would hang because it expects onFinished() to trigger transition.
    # To fix this globally, we should replace `onSpeak(text)` with `onSpeak(text)` and if it's the terminal call, we let TTS handle the follow-up.
    # Wait, the prompt says: "Do not assume every command automatically triggers TTS... Keep conversation transitions centralized in JarvisListenerService. Resume follow up listening only after the command has completed and any response TTS has actually finished."

    # We should have CommandExecutor return completion state explicitly via a callback, and pass an `isSilent` flag.
    # We'll update the constructor:
    init_orig = """class CommandExecutor(
    private val context: Context,
    private val onSpeak: (String) -> Unit,
    private val onFinished: () -> Unit = {},
    private val onShowView: ((android.view.View) -> Unit)? = null
) {"""
    init_new = """class CommandExecutor(
    private val context: Context,
    private val onSpeak: (String) -> Unit,
    private val onFinished: (Boolean) -> Unit = {},
    private val onShowView: ((android.view.View) -> Unit)? = null
) {"""
    content = content.replace(init_orig, init_new)

    # Replace all `onFinished()` calls with `onFinished(true)` (silent/no TTS expected)
    content = content.replace("onFinished()", "onFinished(true)")
    # We need to replace the definition in parameters too
    content = content.replace("onFinished: () -> Unit", "onFinished: (Boolean) -> Unit")
    content = content.replace("onFinished: () -> Unit = {}", "onFinished: (Boolean) -> Unit = {}")


    # We also need to call `onFinished(false)` (TTS is expected) whenever `onSpeak` is called.
    # Let's wrap `onSpeak` invocations. Actually, many are async.
    # We can create a private helper `speakAndFinish(msg)` inside CommandExecutor.

    helper = """
    private fun speakAndFinish(msg: String) {
        onSpeak(msg)
        onFinished(false)
    }
"""
    content = content.replace("private val contactResolver = ContactResolver(context)", "private val contactResolver = ContactResolver(context)\n" + helper)

    # Replace all `onSpeak(` with `speakAndFinish(`
    content = content.replace("onSpeak(", "speakAndFinish(")

    # Except in the constructor, we leave it as `onSpeak: (String) -> Unit`
    content = content.replace("private val speakAndFinish: (String) -> Unit", "private val onSpeak: (String) -> Unit")

    with open('app/src/main/java/com/user/jarvis/CommandExecutor.kt', 'w') as f:
        f.write(content)

update_file()
