with open("app/src/main/java/com/user/jarvis/Command.kt", "r") as f:
    t = f.read()

# Add GamingMode
if "object GamingMode : Command()" not in t:
    t = t.replace("object GetWeather : Command()", "object GetWeather : Command()\n    object GamingMode : Command()")

# Add CheckServerStatus
if "object CheckServerStatus : Command()" not in t:
    t = t.replace("object GamingMode : Command()", "object GamingMode : Command()\n    object CheckServerStatus : Command()")

with open("app/src/main/java/com/user/jarvis/Command.kt", "w") as f:
    f.write(t)

with open("app/src/main/java/com/user/jarvis/CommandExecutor.kt", "r") as f:
    c = f.read()

gaming = """
            Command.GamingMode -> {
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                val targetVolume = (maxVolume * 0.85).toInt()
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVolume, AudioManager.FLAG_SHOW_UI)

                val intent = context.packageManager.getLaunchIntentForPackage("com.dts.freefiremax")
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    onSpeak("Gaming mode activated for BlackHawk. Good luck, sir.")
                } else {
                    onSpeak("Free Fire MAX installed nahi hai")
                }
            }
"""

server = """
            Command.CheckServerStatus -> {
                Thread {
                    try {
                        val request = Request.Builder().url("https://rajebiz.wuaze.com").build()
                        httpClient.newCall(request).execute().use { response ->
                            if (response.isSuccessful) {
                                Handler(Looper.getMainLooper()).post { onSpeak("Sir, your domain rajebiz is live and running.") }
                            } else {
                                Handler(Looper.getMainLooper()).post { onSpeak("Sir, the server seems to be unreachable at the moment.") }
                            }
                        }
                    } catch (e: Exception) {
                        Handler(Looper.getMainLooper()).post { onSpeak("Sir, the server seems to be unreachable at the moment.") }
                    }
                }.start()
            }
"""

if "Command.GamingMode -> {" not in c:
    c = c.replace("Command.GamingModeOn -> {", gaming + "\n            Command.GamingModeOn -> {")

if "Command.CheckServerStatus -> {" not in c:
    c = c.replace("Command.JarvisVision -> {", server + "\n            Command.JarvisVision -> {")

with open("app/src/main/java/com/user/jarvis/CommandExecutor.kt", "w") as f:
    f.write(c)

with open("app/src/main/java/com/user/jarvis/VoiceCommandProcessor.kt", "r") as f:
    text3 = f.read()

# Add to VoiceCommandProcessor
if 'return Command.GamingMode' not in text3:
    text3 = text3.replace('// GAMING MODE', '// GAMING MODE\n        // ========================================================\n\n        if (containsAny(norm, "gaming mode", "free fire", "start game")) {\n            return Command.GamingMode\n        }')

if 'return Command.CheckServerStatus' not in text3:
    text3 = text3.replace('// GAMING MODE', '// GAMING MODE\n        // ========================================================\n\n        if (containsAny(norm, "server status", "check domain", "is website up")) {\n            return Command.CheckServerStatus\n        }\n\n        // GAMING MODE')

with open("app/src/main/java/com/user/jarvis/VoiceCommandProcessor.kt", "w") as f:
    f.write(text3)
