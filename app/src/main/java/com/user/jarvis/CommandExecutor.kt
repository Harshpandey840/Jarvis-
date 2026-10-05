package com.user.jarvis

import android.Manifest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.admin.DevicePolicyManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.location.LocationManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONObject
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class CommandExecutor(
    private val context: Context,
    private val onSpeak: (String) -> Unit,
    private val onFinished: () -> Unit = {},
    private val onShowView: ((android.view.View) -> Unit)? = null
) {

    private val appLauncher = AppLauncher(context)

    private val contactResolver = ContactResolver(context)

    private val messageSender = MessageSender(context)

    private val prefs: SharedPreferences =
        context.getSharedPreferences("jarvis_notes", Context.MODE_PRIVATE)

    private val httpClient = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY })
        .build()

    fun execute(spokenText: String) {
        val localCommand = VoiceCommandProcessor.parse(spokenText)

        if (localCommand !is Command.Unknown) {
            runCommand(localCommand)
        } else {
            AiCommandParser.parse(
                spokenText,
                ApiKeyManager.getGroqApiKey(context)
            ) { aiCommand ->
                runCommand(aiCommand)
            }
        }
    }

    private fun runCommand(command: Command) {

        when (command) {

            is Command.OpenApp -> {
                val opened = appLauncher.openApp(command.appName)

                if (opened) {
                    onFinished()
                } else {
                    onSpeak("${command.appName} nahi mila")
                }
            }

            is Command.SendMessage -> {

                if (!hasPermission(Manifest.permission.SEND_SMS) ||
                    !hasPermission(Manifest.permission.READ_CONTACTS)
                ) {
                    onSpeak("Permission nahi hai message bhejne ki")
                    return
                }

                val number =
                    contactResolver.findNumber(command.contactName)

                if (number == null) {
                    onSpeak("${command.contactName} contact nahi mila")
                    return
                }

                val sent =
                    messageSender.sendSms(
                        number,
                        command.text
                    )

                if (sent) {
                    onSpeak("${command.contactName} ko message bhej diya")
                } else {
                    onSpeak("Message nahi bhej paya")
                }
            }

            is Command.CallContact -> {

                if (!hasPermission(Manifest.permission.CALL_PHONE) ||
                    !hasPermission(Manifest.permission.READ_CONTACTS)
                ) {
                    onSpeak("Permission nahi hai call karne ki")
                    return
                }

                val number =
                    contactResolver.findNumber(command.contactName)

                if (number == null) {
                    onSpeak("${command.contactName} contact nahi mila")
                    return
                }

                val callIntent =
                    Intent(
                        Intent.ACTION_CALL,
                        Uri.parse("tel:$number")
                    ).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }

                context.startActivity(callIntent)

                onSpeak("${command.contactName} ko call kar raha hoon")
            }

            is Command.SendWhatsApp -> {

                if (!hasPermission(Manifest.permission.READ_CONTACTS)) {
                    onSpeak("Permission nahi hai contacts dekhne ki")
                    return
                }

                val number =
                    contactResolver.findNumber(command.contactName)

                if (number == null) {
                    onSpeak("${command.contactName} contact nahi mila")
                    return
                }

                val cleanNumber =
                    normalizeForWhatsApp(number)

                val encodedText =
                    URLEncoder
                        .encode(command.text, "UTF-8")
                        .replace("+", "%20")

                try {

                    val intent =
                        Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse(
                                "https://wa.me/$cleanNumber?text=$encodedText"
                            )
                        ).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }

                    context.startActivity(intent)

                    onSpeak(
                        "${command.contactName} ke WhatsApp mein message tayyar hai, Send dabana"
                    )

                } catch (_: Exception) {
                    onSpeak("WhatsApp nahi khul paya")
                }
            }

            is Command.GoogleSearch -> {

                val encoded =
                    URLEncoder.encode(command.query, "UTF-8")

                val intent =
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse(
                            "https://www.google.com/search?q=$encoded"
                        )
                    ).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }

                context.startActivity(intent)

                onSpeak(
                    "${command.query} search kar raha hoon"
                )
            }

            is Command.PlaySong -> {

                val encoded =
                    URLEncoder.encode(command.query, "UTF-8")

                val intent =
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse(
                            "https://www.youtube.com/results?search_query=$encoded"
                        )
                    ).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }

                context.startActivity(intent)

                onSpeak(
                    "${command.query} bajane ja raha hoon"
                )
            }

            is Command.SetAlarm -> {

                val intent =
                    Intent(AlarmClock.ACTION_SET_ALARM).apply {

                        if (command.hour in 0..23) {
                            putExtra(
                                AlarmClock.EXTRA_HOUR,
                                command.hour
                            )

                            putExtra(
                                AlarmClock.EXTRA_MINUTES,
                                command.minute.coerceIn(0, 59)
                            )
                        }

                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }

                try {

                    context.startActivity(intent)

                    if (command.hour in 0..23) {
                        onSpeak("Alarm laga raha hoon")
                    } else {
                        onSpeak(
                            "Alarm app khol raha hoon, time set kar lena"
                        )
                    }

                } catch (_: Exception) {
                    onSpeak("Alarm nahi laga paya")
                }
            }

            is Command.SetReminder -> {

                val calendar =
                    Calendar.getInstance().apply {

                        add(
                            Calendar.DAY_OF_YEAR,
                            command.dayOffset
                        )

                        if (command.hour in 0..23) {

                            set(
                                Calendar.HOUR_OF_DAY,
                                command.hour
                            )

                            set(
                                Calendar.MINUTE,
                                command.minute.coerceIn(0, 59)
                            )

                            set(Calendar.SECOND, 0)
                        }
                    }

                if (calendar.timeInMillis <= System.currentTimeMillis()) {
                    calendar.add(
                        Calendar.DAY_OF_YEAR,
                        1
                    )
                }

                scheduleReminder(
                    calendar.timeInMillis,
                    command.text,
                    command.hour,
                    command.minute,
                    command.isRecurring
                )

                val timeStr =
                    SimpleDateFormat(
                        "dd MMM, hh:mm a",
                        Locale.getDefault()
                    ).format(calendar.time)

                if (command.isRecurring) {
                    onSpeak(
                        "Theek hai, har roz $timeStr ke time yaad dilaunga"
                    )
                } else {
                    onSpeak(
                        "Theek hai, $timeStr ko yaad dila dunga"
                    )
                }
            }

            is Command.SaveNote -> {

                val existing =
                    prefs.getStringSet(
                        "notes",
                        mutableSetOf()
                    )?.toMutableSet()
                        ?: mutableSetOf()

                existing.add(command.text)

                prefs.edit()
                    .putStringSet("notes", existing)
                    .apply()

                onSpeak("Note save kar diya")
            }

            is Command.AddTodo -> {

                val existing =
                    prefs.getStringSet(
                        "todos",
                        mutableSetOf()
                    )?.toMutableSet()
                        ?: mutableSetOf()

                existing.add(command.text)

                prefs.edit()
                    .putStringSet("todos", existing)
                    .apply()

                onSpeak("Todo list mein add kar diya")
            }

            Command.ReadTodos -> {

                val todos =
                    prefs.getStringSet(
                        "todos",
                        setOf()
                    )?.toList()
                        ?: emptyList()

                if (todos.isEmpty()) {
                    onSpeak("Todo list khaali hai")
                } else {
                    onSpeak(
                        "Tumhari todo list: " +
                            todos.joinToString(". ")
                    )
                }
            }

            Command.ShareNotes -> {

                val notes =
                    prefs.getStringSet(
                        "notes",
                        setOf()
                    )?.toList()
                        ?: emptyList()

                shareText(
                    "Mere Notes:\n" +
                        notes.joinToString("\n")
                )
            }

            Command.ShareTodos -> {

                val todos =
                    prefs.getStringSet(
                        "todos",
                        setOf()
                    )?.toList()
                        ?: emptyList()

                shareText(
                    "Meri Todo List:\n" +
                        todos.joinToString("\n")
                )
            }

            Command.ReadClipboard -> {

                val clipboardManager =
                    context.getSystemService(
                        Context.CLIPBOARD_SERVICE
                    ) as ClipboardManager

                val clip =
                    clipboardManager.primaryClip

                if (clip != null && clip.itemCount > 0) {

                    val text =
                        clip.getItemAt(0)
                            .coerceToText(context)
                            .toString()

                    if (text.isBlank()) {
                        onSpeak("Clipboard khaali hai")
                    } else {
                        onSpeak(
                            "Clipboard mein hai: $text"
                        )
                    }

                } else {
                    onSpeak("Clipboard khaali hai")
                }
            }

            is Command.WriteClipboard -> {

                val clipboardManager =
                    context.getSystemService(
                        Context.CLIPBOARD_SERVICE
                    ) as ClipboardManager

                clipboardManager.setPrimaryClip(
                    ClipData.newPlainText(
                        "Jarvis",
                        command.text
                    )
                )

                onSpeak("Clipboard mein copy kar diya")
            }

            is Command.CreateContact -> {

                try {

                    val intent =
                        Intent(Intent.ACTION_INSERT).apply {

                            type =
                                ContactsContract
                                    .Contacts
                                    .CONTENT_TYPE

                            putExtra(
                                ContactsContract.Intents.Insert.NAME,
                                command.name
                            )

                            putExtra(
                                ContactsContract.Intents.Insert.PHONE,
                                command.phone
                            )

                            addFlags(
                                Intent.FLAG_ACTIVITY_NEW_TASK
                            )
                        }

                    context.startActivity(intent)

                    onSpeak(
                        "${command.name} ka contact tayyar kar diya, Save dabana"
                    )

                } catch (_: Exception) {
                    onSpeak("Contact nahi bana paya")
                }
            }

            Command.GetWeather -> {
                fetchWeatherForCurrentLocation()
            }

            is Command.ChatReply -> {
                onSpeak(command.text)
            }

            Command.LockPhone -> {

                val dpm =
                    context.getSystemService(
                        Context.DEVICE_POLICY_SERVICE
                    ) as DevicePolicyManager

                val adminComponent =
                    ComponentName(
                        context,
                        JarvisDeviceAdminReceiver::class.java
                    )

                if (dpm.isAdminActive(adminComponent)) {

                    onSpeak("Lock kar raha hoon")

                    dpm.lockNow()

                } else {

                    val intent =
                        Intent(
                            DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN
                        ).apply {

                            putExtra(
                                DevicePolicyManager.EXTRA_DEVICE_ADMIN,
                                adminComponent
                            )

                            putExtra(
                                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                                "Jarvis ko phone lock karne ke liye ye permission chahiye"
                            )

                            addFlags(
                                Intent.FLAG_ACTIVITY_NEW_TASK
                            )
                        }

                    context.startActivity(intent)

                    onSpeak(
                        "Pehle lock permission allow karo, ek baar hi dena hoga"
                    )
                }
            }

            Command.ReadNotes -> {

                val notes =
                    prefs.getStringSet(
                        "notes",
                        setOf()
                    )?.toList()
                        ?: emptyList()

                if (notes.isEmpty()) {
                    onSpeak("Koi note nahi hai")
                } else {
                    onSpeak(
                        "Tumhare notes hain: " +
                            notes.joinToString(". ")
                    )
                }
            }

            Command.TellTime -> {

                val time =
                    SimpleDateFormat(
                        "hh:mm a",
                        Locale.getDefault()
                    ).format(Date())

                onSpeak("Abhi time hai $time")
            }

            Command.TellBattery -> {

                val batteryManager =
                    context.getSystemService(
                        Context.BATTERY_SERVICE
                    ) as BatteryManager

                val level =
                    batteryManager.getIntProperty(
                        BatteryManager.BATTERY_PROPERTY_CAPACITY
                    )

                onSpeak(
                    "Battery $level percent hai"
                )
            }

            Command.VolumeUp -> {

                val audioManager =
                    context.getSystemService(
                        Context.AUDIO_SERVICE
                    ) as AudioManager

                audioManager.adjustStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    AudioManager.ADJUST_RAISE,
                    AudioManager.FLAG_SHOW_UI
                )

                onSpeak("Volume badha diya")
            }

            Command.VolumeDown -> {

                val audioManager =
                    context.getSystemService(
                        Context.AUDIO_SERVICE
                    ) as AudioManager

                audioManager.adjustStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    AudioManager.ADJUST_LOWER,
                    AudioManager.FLAG_SHOW_UI
                )

                onSpeak("Volume kam kar diya")
            }

            Command.FlashlightOn -> {
                setTorch(true)
                onSpeak("Torch jala diya")
            }

            Command.FlashlightOff -> {
                setTorch(false)
                onSpeak("Torch bandh kar diya")
            }

            Command.OpenWifiSettings -> {

                context.startActivity(
                    Intent(
                        Settings.ACTION_WIFI_SETTINGS
                    ).apply {
                        addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK
                        )
                    }
                )

                onSpeak(
                    "WiFi settings khol raha hoon"
                )
            }

            Command.OpenBluetoothSettings -> {

                context.startActivity(
                    Intent(
                        Settings.ACTION_BLUETOOTH_SETTINGS
                    ).apply {
                        addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK
                        )
                    }
                )

                onSpeak(
                    "Bluetooth settings khol raha hoon"
                )
            }

            Command.SilentModeOn -> {
                setRingerMode(
                    AudioManager.RINGER_MODE_SILENT,
                    "Silent kar diya"
                )
            }

            Command.SilentModeOff -> {
                setRingerMode(
                    AudioManager.RINGER_MODE_NORMAL,
                    "Sound on kar diya"
                )
            }

            Command.JarvisVision -> {
                val intent = Intent(context, VisionActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }

            Command.EnableAirTouch -> {
                AirTouchSettings.setEnabled(context, true)
                AirTouchForegroundService.start(context)
                onSpeak("AirTouch enabled sir.")
            }

            Command.DisableAirTouch -> {
                AirTouchSettings.setEnabled(context, false)
                AirTouchForegroundService.stop(context)
                onSpeak("AirTouch disabled.")
            }

            is Command.SetGesture -> {
                val repository = com.user.jarvis.gestures.GestureRepository(context)
                val allGestures = repository.getGestures().toMutableList()

                // Map strings to Enums safely
                val shapeEnum = try { com.user.jarvis.gestures.models.HandShape.valueOf(command.gestureShape.uppercase()) } catch (e: Exception) { null }
                val actionEnum = try { com.user.jarvis.gestures.models.GestureAction.valueOf(command.actionTarget.uppercase()) } catch (e: Exception) { null }

                if (shapeEnum != null && actionEnum != null) {
                    // Find if gesture config already exists for this shape and update it
                    val index = allGestures.indexOfFirst { it.handShape == shapeEnum }
                    if (index != -1) {
                        allGestures[index] = allGestures[index].copy(action = actionEnum, enabled = true)
                    } else {
                        // Or add new if it makes sense, though currently we have a fixed set of configs mostly
                        allGestures.add(com.user.jarvis.gestures.models.GestureConfig("g_voice", "Voice Custom", shapeEnum, com.user.jarvis.gestures.models.Direction.NONE, actionEnum, true))
                    }
                    repository.saveGestures(allGestures)
                    onSpeak("Gesture updated sir.")
                } else {
                    onSpeak("Sorry, I couldn't understand that gesture or action.")
                }
            }

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
                    onSpeak("Free Fire MAX is not installed, sir.")
                }
            }

            Command.EditVideo -> {
                val success = appLauncher.openApp("vn video editor")
                if (success) {
                    onSpeak("Opening editing suite.")
                } else {
                    onSpeak("Video editor nahi mil raha")
                }
            }

            Command.SecurityLockdown -> {
                try {
                    val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as android.app.admin.DevicePolicyManager
                    val adminComponent = android.content.ComponentName(context, JarvisDeviceAdminReceiver::class.java)
                    if (dpm.isAdminActive(adminComponent)) {
                        dpm.lockNow()
                        onSpeak("Security protocol activated. System locked.")
                    } else {
                        onSpeak("Device admin permission chahiye")
                    }
                } catch (e: Exception) {
                    onSpeak("Lock nahi kar paya")
                }
            }

            Command.LocalEnvironmentIntel -> {
                fetchLocalEnvironmentIntel()
            }


            Command.CheckServerStatus -> {
                Thread {
                    try {
                        val request = okhttp3.Request.Builder().url("https://rajebiz.wuaze.com").build()
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

            is Command.TrainStatus -> {
                fetchTrainStatus(command.trainNumber, onFinished)
            }

            is Command.TrainInfo -> {
                fetchTrainInfo(command.query, onFinished)
            }

            is Command.TrackFlights -> {
                onSpeak("Opening flight tracking.")
                val intent = Intent(context, Class.forName("com.user.jarvis.FlightTrackerActivity")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                onFinished()
            }

            is Command.SearchFlight -> {
                onSpeak("Searching for flight ${command.query}.")
                val intent = Intent(context, Class.forName("com.user.jarvis.FlightTrackerActivity")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    putExtra("SEARCH_QUERY", command.query)
                }
                context.startActivity(intent)
                onFinished()
            }

            is Command.FirstFlightDetails -> {
                handleFirstFlightDetails(onFinished)
            }

            is Command.Unknown -> {
                onSpeak("Samajh nahi aaya")
            }
        }
    }

    private fun handleFirstFlightDetails(onFinished: () -> Unit) {
        val flights = FlightRepository.lastFetchedFlights
        if (flights.isNotEmpty()) {
            val first = flights.first()
            speakFlightDetails(first)
            onFinished()
        } else {
            FlightRepository.fetchFlights(context, onSuccess = { fetchedFlights ->
                Handler(Looper.getMainLooper()).post {
                    if (fetchedFlights.isNotEmpty()) {
                        val first = fetchedFlights.first()
                        speakFlightDetails(first)
                    } else {
                        onSpeak("No tracked flights available.")
                    }
                    onFinished()
                }
            }, onError = { errorMsg ->
                Handler(Looper.getMainLooper()).post {
                    onSpeak(errorMsg)
                    onFinished()
                }
            })
        }
    }

    private fun speakFlightDetails(flight: FlightItem) {
        val route = if (flight.fromCity != "Unknown" && flight.toCity != "Unknown") {
            "from ${flight.fromCity} to ${flight.toCity}"
        } else {
            "currently unavailable"
        }
        val message = "The first tracked flight is ${flight.flight}. The aircraft is an ${flight.type}. Its route is $route."
        onSpeak(message)
    }

    private fun fetchTrainInfo(query: String, onFinished: () -> Unit) {
        val apiKey = ApiKeyManager.getRapidApiKey(context)
        if (apiKey.isBlank()) {
            onSpeak("RapidAPI key nahi hai")
            return
        }

        Thread {
            try {
                val host = "indian-railway-irctc.p.rapidapi.com"
                val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
                val url = "https://$host/api/trains-search/v1/train/$encodedQuery?client=web&isH5=true"

                Log.d("TrainAPI", "Request URL: $url")

                val request = Request.Builder()
                    .url(url)
                    .addHeader("x-rapidapi-host", host)
                    .addHeader("x-rapidapi-key", apiKey)
                    .addHeader("x-rapid-api", "rapid-api-database")
                    .build()

                Log.d("TrainAPI", "Final Request URL: ${request.url}")
                httpClient.newCall(request).execute().use { response ->
                    Log.d("TrainAPI", "Response Code: ${response.code}")
                    val responseBody = response.body?.string().orEmpty()
                    Log.d("TrainDebug", "Raw JSON Response: $responseBody")
                    Log.d("TrainAPI", "Response Body: $responseBody")

                    if (!response.isSuccessful) {
                        Log.e("TrainAPI", "HTTP Error ${response.code}: $responseBody")
                        Handler(Looper.getMainLooper()).post { onSpeak("HTTP Error ${response.code}") }
                        return@use
                    }

                    try {
                        val tokener = org.json.JSONTokener(responseBody)
                        val parsed = tokener.nextValue()
                        var dataArray: org.json.JSONArray? = null

                        if (parsed is JSONObject) {
                            if (!parsed.optBoolean("status", true)) {
                                Handler(Looper.getMainLooper()).post { onSpeak("Train ka data nahi mila") }
                                return@use
                            }
                            dataArray = parsed.optJSONArray("data")
                            if (dataArray == null) {
                                val keys = parsed.keys()
                                while (keys.hasNext()) {
                                    val key = keys.next()
                                    val optArr = parsed.optJSONArray(key)
                                    if (optArr != null) {
                                        dataArray = optArr
                                        break
                                    }
                                }
                            }
                        } else if (parsed is org.json.JSONArray) {
                            dataArray = parsed
                        }

                        if (dataArray != null && dataArray.length() > 0) {
                            val trainObj = dataArray.optJSONObject(0)
                            if (trainObj != null) {
                                var tName = trainObj.optString("train_name", trainObj.optString("trainName", ""))
                                var tNum = trainObj.optString("train_number", trainObj.optString("trainNumber", ""))

                                if (tName.isBlank() || tNum.isBlank()) {
                                    val keys = trainObj.keys()
                                    val strValues = mutableListOf<String>()
                                    while (keys.hasNext()) {
                                        val k = keys.next()
                                        val v = trainObj.optString(k, "")
                                        if (v.isNotBlank()) strValues.add(v)
                                    }
                                    if (tNum.isBlank() && strValues.isNotEmpty()) tNum = strValues.removeAt(0)
                                    if (tName.isBlank() && strValues.isNotEmpty()) tName = strValues.removeAt(0)
                                }

                                if (tName.isNotBlank() && tNum.isNotBlank()) {
                                    Handler(Looper.getMainLooper()).post {
                                        onSpeak("Train hai: $tNum $tName")
                                        onFinished()
                                    }
                                } else {
                                    Handler(Looper.getMainLooper()).post { onSpeak("Train detail theek se nahi mili") }
                                }
                            } else {
                                Handler(Looper.getMainLooper()).post { onSpeak("Train object nahi mila") }
                            }
                        } else {
                            Handler(Looper.getMainLooper()).post { onSpeak("Train nahi mili") }
                        }

                    } catch (e: Exception) {
                        Log.e("TrainAPI", "JSON Parse error in fetchTrainInfo", e)
                        Handler(Looper.getMainLooper()).post { onSpeak("Data parse karne me error aayi") }
                    }
                }
            } catch (e: Exception) {
                Log.e("TrainAPI", "Network exception in fetchTrainInfo", e)
                Handler(Looper.getMainLooper()).post { onSpeak(e.message ?: "Network Error") }
            }
        }.start()
    }

    private fun fetchTrainStatus(trainNumber: String, onFinished: () -> Unit) {
        val apiKey = ApiKeyManager.getRapidApiKey(context)
        if (apiKey.isBlank()) {
            onSpeak("RapidAPI key nahi hai")
            return
        }

        Thread {
            try {
                val host = "indian-railway-irctc.p.rapidapi.com"
                val dateFormat = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
                val currentDate = dateFormat.format(Date())
                val url = "https://$host/api/trains/v1/train/status?train_number=$trainNumber&departure_date=$currentDate&deviceIdentifier=Mozilla%20Firefox-138.0.0.0&isH5=true&client=web"

                Log.d("TrainAPI", "Request URL: $url")

                val request = Request.Builder()
                    .url(url)
                    .addHeader("x-rapidapi-host", host)
                    .addHeader("x-rapidapi-key", apiKey)
                    .addHeader("x-rapid-api", "rapid-api-database")
                    .build()

                Log.d("TrainAPI", "Final Request URL: ${request.url}")
                httpClient.newCall(request).execute().use { response ->
                    Log.d("TrainAPI", "Response Code: ${response.code}")
                    val responseBody = response.body?.string().orEmpty()
                    Log.d("TrainDebug", "Raw JSON Response: $responseBody")
                    Log.d("TrainAPI", "Response Body: $responseBody")

                    if (!response.isSuccessful) {
                        Log.e("TrainAPI", "HTTP Error ${response.code}: $responseBody")
                        Handler(Looper.getMainLooper()).post { onSpeak("HTTP Error ${response.code}") }
                        return@use
                    }

                    try {
                        val tokener = org.json.JSONTokener(responseBody)
                        val parsed = tokener.nextValue()

                        if (parsed is JSONObject) {
                            val data = parsed.optJSONObject("data") ?: parsed

                            val updateTime = data.optString("last_updated", data.optString("updateTime", ""))

                            var currentStationName = data.optString("current_station", "")
                            if (currentStationName.isBlank()) currentStationName = data.optString("currentStationName", "")
                            if (currentStationName.isBlank()) currentStationName = data.optString("current_station_name", "")
                            if (currentStationName.isBlank()) currentStationName = data.optString("stationName", "")
                            if (currentStationName.isBlank()) currentStationName = data.optString("station_name", "Unknown Station")
                            if (currentStationName.isBlank()) currentStationName = "Unknown Station"

                            val delay = data.optInt("delay_minutes", data.optInt("delay", 0))
                            var eta = data.optString("eta", "")
                            if (eta.isBlank()) eta = "N/A"

                            // Station timeline parsing logic
                            val stationsTimeline = mutableListOf<String>()
                            val timelineArray = data.optJSONArray("timeline")
                            if (timelineArray != null && timelineArray.length() > 0) {
                                for (i in 0 until timelineArray.length()) {
                                    val stage = timelineArray.optJSONObject(i) ?: continue
                                    val stageName = stage.optString("station_name", "Unknown")
                                    stationsTimeline.add(stageName)
                                }
                            } else {
                                // Fallbacks for other structures
                                var stationsArray = data.optJSONArray("previousStages")
                                if (stationsArray == null) stationsArray = data.optJSONArray("previous_stages")
                                var nextStationsArray = data.optJSONArray("upcomingStages")
                                if (nextStationsArray == null) nextStationsArray = data.optJSONArray("upcoming_stages")

                                if (stationsArray != null && stationsArray.length() > 0) {
                                    val lastStage = stationsArray.optJSONObject(stationsArray.length() - 1)
                                    if (lastStage != null) {
                                        var stageName = lastStage.optString("stationName", "")
                                        if (stageName.isBlank()) stageName = lastStage.optString("station_name", "Unknown")
                                        stationsTimeline.add(stageName)
                                    }
                                }
                                stationsTimeline.add(currentStationName)
                                if (nextStationsArray != null && nextStationsArray.length() > 0) {
                                    val nextStage = nextStationsArray.optJSONObject(0)
                                    if (nextStage != null) {
                                        var stageName = nextStage.optString("stationName", "")
                                        if (stageName.isBlank()) stageName = nextStage.optString("station_name", "Unknown")
                                        stationsTimeline.add(stageName)
                                    }
                                }
                            }

                            val timelineText = stationsTimeline.joinToString(" → ")

                            data class LiveTrainStatus(
                                val currentStationName: String,
                                val delay: Int,
                                val eta: String,
                                val updateTime: String,
                                val timelineText: String
                            )

                            val status = LiveTrainStatus(
                                currentStationName = currentStationName,
                                delay = delay,
                                eta = eta,
                                updateTime = updateTime,
                                timelineText = timelineText
                            )

                            // Fallback speech
                            val statusMessage = "Train $trainNumber is at ${status.currentStationName}, delay is ${status.delay} minutes."

                            Handler(Looper.getMainLooper()).post {
                                onSpeak(statusMessage)

                                // Let's create a visual card if onShowView is available
                                onShowView?.let { show ->
                                    val card = createTrainStatusCard(trainNumber, status.currentStationName, status.delay, status.eta, status.updateTime, status.timelineText)
                                    show(card)
                                }

                                onFinished()
                            }
                        } else {
                            Log.e("TrainAPI", "JSON Parsing Error: Expected JSONObject but got ${parsed?.javaClass?.simpleName}")
                            Handler(Looper.getMainLooper()).post { onSpeak("JSON Parsing Error") }
                        }
                    } catch (e: Exception) {
                        Log.e("TrainAPI", "JSON Parse error", e)
                        Handler(Looper.getMainLooper()).post { onSpeak(e.message ?: "Unknown Error") }
                    }
                }
            } catch (e: Exception) {
                Log.e("TrainAPI", "Network exception in fetchTrainStatus", e)
                Handler(Looper.getMainLooper()).post { onSpeak(e.message ?: "Unknown Error") }
            }
        }.start()
    }

    private fun createTrainStatusCard(trainNumber: String, station: String, delay: Int, eta: String, updateTime: String, timelineText: String): android.view.View {
        val layout = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            val padding = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_DIP, 16f, resources.displayMetrics).toInt()
            setPadding(padding, padding, padding, padding)

            val marginParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_DIP, 12f, resources.displayMetrics).toInt()
            }
            layoutParams = marginParams

            // Sleek futuristic dark theme
            val bg = android.graphics.drawable.GradientDrawable()
            bg.setColor(android.graphics.Color.parseColor("#15151D"))
            bg.cornerRadius = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_DIP, 16f, resources.displayMetrics)
            bg.setStroke(2, android.graphics.Color.parseColor("#A5A5FF"))
            background = bg
        }

        val titleParams = android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_DIP, 8f, layout.resources.displayMetrics).toInt()
        }

        val titleView = android.widget.TextView(context).apply {
            text = "LIVE STATUS • $trainNumber"
            setTextColor(android.graphics.Color.parseColor("#A5A5FF"))
            textSize = 14f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            layoutParams = titleParams
        }

        val stationView = android.widget.TextView(context).apply {
            text = "📍 $station"
            setTextColor(android.graphics.Color.WHITE)
            textSize = 18f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            layoutParams = titleParams
        }

        val delayText = if (delay <= 0) "On Time" else "Late by ${delay}m"
        val delayColor = if (delay <= 0) "#4CAF50" else "#F44336"

        val delayView = android.widget.TextView(context).apply {
            text = "Status: $delayText"
            setTextColor(android.graphics.Color.parseColor(delayColor))
            textSize = 14f
            layoutParams = titleParams
        }

        val etaView = android.widget.TextView(context).apply {
            text = "ETA: $eta"
            setTextColor(android.graphics.Color.LTGRAY)
            textSize = 14f
            layoutParams = titleParams
        }

        val timelineView = android.widget.TextView(context).apply {
            text = "Timeline: $timelineText"
            setTextColor(android.graphics.Color.WHITE)
            textSize = 14f
            setTypeface(typeface, android.graphics.Typeface.ITALIC)
            layoutParams = titleParams
        }

        val updateView = android.widget.TextView(context).apply {
            text = "Last updated: $updateTime"
            setTextColor(android.graphics.Color.DKGRAY)
            textSize = 12f
        }

        layout.addView(titleView)
        layout.addView(stationView)
        layout.addView(delayView)
        layout.addView(etaView)
        if (timelineText.isNotBlank()) layout.addView(timelineView)
        layout.addView(updateView)

        return layout
    }


    private fun fetchLocalEnvironmentIntel() {
        val apiKey = ApiKeyManager.getGroqApiKey(context)
        Log.d("GroqAPI", "Key length: ${apiKey.length}, value: '$apiKey'")
        if (apiKey.isBlank()) {
            onSpeak("API key nahi hai")
            return
        }

        Thread {
            try {
                val prompt = "The user is currently in Arma, Lakhisarai, Bihar, India. Provide a very brief, realistic current weather estimation and local intel for this location."
                val requestJson = org.json.JSONObject().apply {
                    put("model", "openai/gpt-oss-20b")
                    put("temperature", 0.7)
                    put("messages", org.json.JSONArray()
                        .put(org.json.JSONObject().apply {
                            put("role", "user")
                            put("content", prompt)
                        })
                    )
                }
                val body = requestJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
                val request = okhttp3.Request.Builder()
                    .url("https://api.groq.com/openai/v1/chat/completions")
                    .addHeader("Authorization", "Bearer $apiKey")
                    .post(body)
                    .build()

                Log.d("GroqAPI", "Request headers: ${request.headers}")

                httpClient.newCall(request).execute().use { response ->
                    val responseText = response.body?.string().orEmpty()
                    val root = org.json.JSONObject(responseText)

                    if (root.has("error")) {
                        Handler(Looper.getMainLooper()).post { onSpeak("Mausam ka data nahi mil paaya") }
                        return@use
                    }
                    val rawText = root.getJSONArray("choices")
                        .getJSONObject(0).getJSONObject("message")
                        .getString("content")
                    Handler(Looper.getMainLooper()).post { onSpeak(rawText.trim()) }
                }
            } catch (e: Exception) {
                Handler(Looper.getMainLooper()).post { onSpeak("Mausam ka data nahi mil paaya") }
            }
        }.start()
    }

    private fun fetchWeatherForCurrentLocation() {

        if (
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            onSpeak(
                "Location permission nahi hai mausam batane ke liye"
            )
            return
        }

        val locationManager =
            context.getSystemService(
                Context.LOCATION_SERVICE
            ) as LocationManager

        var lat: Double? = null
        var lon: Double? = null

        try {

            for (provider in locationManager.getProviders(true)) {

                val loc =
                    locationManager.getLastKnownLocation(
                        provider
                    ) ?: continue

                lat = loc.latitude
                lon = loc.longitude

                break
            }

        } catch (_: Exception) {
        }

        if (lat == null || lon == null) {

            onSpeak(
                "Location nahi mil paayi, GPS on karke try karo"
            )

            return
        }

        val url =
            "https://api.open-meteo.com/v1/forecast" +
                "?latitude=$lat" +
                "&longitude=$lon" +
                "&current_weather=true"

        Thread {

            try {

                val request =
                    Request.Builder()
                        .url(url)
                        .build()

                httpClient
                    .newCall(request)
                    .execute()
                    .use { response ->

                        val json =
                            JSONObject(
                                response.body
                                    ?.string()
                                    .orEmpty()
                            )

                        val current =
                            json.getJSONObject(
                                "current_weather"
                            )

                        val temp =
                            current.getDouble(
                                "temperature"
                            )

                        val code =
                            current.getInt(
                                "weathercode"
                            )

                        val description =
                            weatherCodeToText(code)

                        val message =
                            "Abhi temperature " +
                                "${temp.toInt()} degree hai, " +
                                description

                        Handler(
                            Looper.getMainLooper()
                        ).post {
                            onSpeak(message)
                        }
                    }

            } catch (_: Exception) {

                Handler(
                    Looper.getMainLooper()
                ).post {
                    onSpeak(
                        "Mausam ka data nahi mil paaya"
                    )
                }
            }

        }.start()
    }

    private fun weatherCodeToText(code: Int): String {

        return when (code) {

            0 -> "saaf aasmaan hai"

            1, 2, 3 ->
                "halke badal hain"

            45, 48 ->
                "kohra hai"

            51, 53, 55 ->
                "halki boondabaandi hai"

            61, 63, 65 ->
                "baarish ho rahi hai"

            71, 73, 75 ->
                "baraf gir rahi hai"

            80, 81, 82 ->
                "tez baarish ho sakti hai"

            95, 96, 99 ->
                "toofan ya aandhi ho sakti hai"

            else ->
                "mausam mixed hai"
        }
    }

    private fun scheduleReminder(
        triggerAtMillis: Long,
        text: String,
        hour: Int,
        minute: Int,
        isRecurring: Boolean
    ) {

        val alarmManager =
            context.getSystemService(
                Context.ALARM_SERVICE
            ) as AlarmManager

        val reminderIntent =
            Intent(
                context,
                ReminderReceiver::class.java
            ).apply {

                putExtra(
                    "reminder_text",
                    text
                )

                putExtra(
                    "is_recurring",
                    isRecurring
                )

                putExtra(
                    "reminder_hour",
                    hour
                )

                putExtra(
                    "reminder_minute",
                    minute
                )
            }

        val requestCode =
            System.currentTimeMillis().toInt()

        val pendingIntent =
            PendingIntent.getBroadcast(
                context,
                requestCode,
                reminderIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
            )

        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAtMillis,
            pendingIntent
        )
    }

    private fun shareText(text: String) {

        val intent =
            Intent(Intent.ACTION_SEND).apply {

                type = "text/plain"

                putExtra(
                    Intent.EXTRA_TEXT,
                    text
                )

                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK
                )
            }

        try {

            context.startActivity(
                Intent.createChooser(
                    intent,
                    "Share karo"
                ).apply {
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK
                    )
                }
            )

            onSpeak(
                "Share karne ka option khol raha hoon"
            )

        } catch (_: Exception) {

            onSpeak(
                "Share nahi kar paya"
            )
        }
    }

    private fun setRingerMode(
        mode: Int,
        successMessage: String
    ) {

        val notificationManager =
            context.getSystemService(
                Context.NOTIFICATION_SERVICE
            ) as NotificationManager

        if (
            !notificationManager
                .isNotificationPolicyAccessGranted
        ) {

            val intent =
                Intent(
                    Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS
                ).apply {
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK
                    )
                }

            context.startActivity(intent)

            onSpeak(
                "Pehle Jarvis ko 'Do Not Disturb' permission do"
            )

            return
        }

        val audioManager =
            context.getSystemService(
                Context.AUDIO_SERVICE
            ) as AudioManager

        audioManager.ringerMode = mode

        onSpeak(successMessage)
    }

    private fun setTorch(on: Boolean) {

        try {

            val cameraManager =
                context.getSystemService(
                    Context.CAMERA_SERVICE
                ) as CameraManager

            val cameraId =
                cameraManager.cameraIdList.firstOrNull { id ->

                    cameraManager
                        .getCameraCharacteristics(id)
                        .get(
                            CameraCharacteristics.FLASH_INFO_AVAILABLE
                        ) == true
                }

            if (cameraId != null) {
                cameraManager.setTorchMode(
                    cameraId,
                    on
                )
            }

        } catch (_: Exception) {
        }
    }

    private fun normalizeForWhatsApp(
        rawNumber: String
    ): String {

        val digitsOnly =
            rawNumber.filter {
                it.isDigit()
            }

        return if (digitsOnly.length == 10) {
            "91$digitsOnly"
        } else {
            digitsOnly
        }
    }

    private fun hasPermission(
        permission: String
    ): Boolean {

        return ContextCompat.checkSelfPermission(
            context,
            permission
        ) == PackageManager.PERMISSION_GRANTED
    }
}
