package com.user.jarvis

sealed class Command {
    data class OpenApp(val appName: String) : Command()
    data class SendMessage(val contactName: String, val text: String) : Command()
    data class CallContact(val contactName: String) : Command()
    data class Unknown(val raw: String) : Command()
}
