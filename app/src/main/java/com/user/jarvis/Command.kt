package com.user.jarvis

sealed class Command {
    data class OpenApp(val appName: String) : Command()
    data class SendMessage(val contactName: String, val text: String) : Command()
    data class CallContact(val contactName: String) : Command()
    data class SendWhatsApp(val contactName: String, val text: String) : Command()
    data class GoogleSearch(val query: String) : Command()
    data class PlaySong(val query: String) : Command()
    data class SetAlarm(val hour: Int, val minute: Int) : Command()
    data class SaveNote(val text: String) : Command()
    object ReadNotes : Command()
    object TellTime : Command()
    object TellBattery : Command()
    object VolumeUp : Command()
    object VolumeDown : Command()
    object FlashlightOn : Command()
    object FlashlightOff : Command()
    object OpenWifiSettings : Command()
    object OpenBluetoothSettings : Command()
    object SilentModeOn : Command()
    object SilentModeOff : Command()
    data class Unknown(val raw: String) : Command()
}
