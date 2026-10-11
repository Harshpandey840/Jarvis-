import re

def update_file():
    with open('app/src/main/java/com/user/jarvis/CommandExecutor.kt', 'r') as f:
        content = f.read()

    # We need to update CommandExecutor constructor to take a callback for completion
    # Replace:
    # class CommandExecutor(
    #     private val context: Context,
    #     private val onSpeak: (String) -> Unit,
    #     private val onShowView: ((android.view.View) -> Unit)? = null
    # )

    # We will use an internal boolean to know if onSpeak was called, but wait, the prompt says:
    # "Update CommandExecutor and VoiceCommandProcessor only as necessary to signal when command execution has genuinely completed."

    init_orig = """class CommandExecutor(
    private val context: Context,
    private val onSpeak: (String) -> Unit,
    private val onShowView: ((android.view.View) -> Unit)? = null
) {"""
    init_new = """class CommandExecutor(
    private val context: Context,
    private val onSpeak: (String) -> Unit,
    private val onShowView: ((android.view.View) -> Unit)? = null,
    private val onCommandCompleted: (() -> Unit)? = null
) {"""
    content = content.replace(init_orig, init_new)

    # Let's wrap onSpeak so it automatically notifies, or just call onCommandCompleted at the end of execute if it's synchronous,
    # but some are asynchronous.
    # To keep it simple, we can add a method `notifyComplete()` and call it in all the async callbacks, or just have `execute()`
    # return a Boolean meaning "isAsync", and if false, we call onCommandCompleted immediately.
    # Better yet, let's just make onSpeak trigger completion implicitly inside JarvisListenerService since that's how it communicates.
    # Wait, the prompt specifically says: "Do not assume every command automatically triggers TTS."

    with open('app/src/main/java/com/user/jarvis/CommandExecutor.kt', 'w') as f:
        f.write(content)

update_file()
