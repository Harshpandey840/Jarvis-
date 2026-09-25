package com.user.jarvis

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceCommandProcessorTest {

    @Test
    fun testUnknown() {
        assertEquals(Command.Unknown(""), VoiceCommandProcessor.parse(""))
        assertEquals(Command.Unknown("   "), VoiceCommandProcessor.parse("   "))
        assertEquals(Command.Unknown("some random gibberish"), VoiceCommandProcessor.parse("some random gibberish"))
    }

    @Test
    fun testLockPhone() {
        assertEquals(Command.LockPhone, VoiceCommandProcessor.parse("lock"))
        assertEquals(Command.LockPhone, VoiceCommandProcessor.parse("screen lock"))
        assertEquals(Command.LockPhone, VoiceCommandProcessor.parse("phone lock"))
        assertEquals(Command.LockPhone, VoiceCommandProcessor.parse("mobile lock"))
    }

    @Test
    fun testGetWeather() {
        assertEquals(Command.GetWeather, VoiceCommandProcessor.parse("mausam"))
        assertEquals(Command.GetWeather, VoiceCommandProcessor.parse("weather"))
    }

    @Test
    fun testTellTime() {
        assertEquals(Command.TellTime, VoiceCommandProcessor.parse("time"))
        assertEquals(Command.TellTime, VoiceCommandProcessor.parse("samay"))
    }

    @Test
    fun testTellBattery() {
        assertEquals(Command.TellBattery, VoiceCommandProcessor.parse("battery"))
    }

    @Test
    fun testShareNotes() {
        assertEquals(Command.ShareNotes, VoiceCommandProcessor.parse("share note"))
        assertEquals(Command.ShareNotes, VoiceCommandProcessor.parse("note share"))
    }

    @Test
    fun testShareTodos() {
        assertEquals(Command.ShareTodos, VoiceCommandProcessor.parse("share todo"))
        assertEquals(Command.ShareTodos, VoiceCommandProcessor.parse("todo share"))
    }

    @Test
    fun testCreateContact() {
        assertEquals(Command.CreateContact("john", "9876543210"), VoiceCommandProcessor.parse("john naya contact banao 9876543210"))
        assertEquals(Command.CreateContact("amit", "1234567890"), VoiceCommandProcessor.parse("amit add contact 1234567890"))
    }

    @Test
    fun testVolumeUp() {
        assertEquals(Command.VolumeUp, VoiceCommandProcessor.parse("volume badhao"))
        assertEquals(Command.VolumeUp, VoiceCommandProcessor.parse("volume up"))
    }

    @Test
    fun testVolumeDown() {
        assertEquals(Command.VolumeDown, VoiceCommandProcessor.parse("volume kam karo"))
        assertEquals(Command.VolumeDown, VoiceCommandProcessor.parse("volume down"))
    }

    @Test
    fun testFlashlightOff() {
        assertEquals(Command.FlashlightOff, VoiceCommandProcessor.parse("torch bandh"))
        assertEquals(Command.FlashlightOff, VoiceCommandProcessor.parse("flashlight off"))
    }

    @Test
    fun testFlashlightOn() {
        assertEquals(Command.FlashlightOn, VoiceCommandProcessor.parse("torch jalao"))
        assertEquals(Command.FlashlightOn, VoiceCommandProcessor.parse("flashlight on"))
    }

    @Test
    fun testOpenWifiSettings() {
        assertEquals(Command.OpenWifiSettings, VoiceCommandProcessor.parse("wifi"))
        assertEquals(Command.OpenWifiSettings, VoiceCommandProcessor.parse("wi fi"))
    }

    @Test
    fun testOpenBluetoothSettings() {
        assertEquals(Command.OpenBluetoothSettings, VoiceCommandProcessor.parse("bluetooth"))
    }

    @Test
    fun testSilentModeOn() {
        assertEquals(Command.SilentModeOn, VoiceCommandProcessor.parse("silent"))
        assertEquals(Command.SilentModeOn, VoiceCommandProcessor.parse("silent mode"))
    }

    @Test
    fun testSilentModeOff() {
        assertEquals(Command.SilentModeOff, VoiceCommandProcessor.parse("sound on"))
        assertEquals(Command.SilentModeOff, VoiceCommandProcessor.parse("ringer on"))
    }

    @Test
    fun testSetAlarm() {
        assertEquals(Command.SetAlarm(7, 30), VoiceCommandProcessor.parse("alarm 7 30"))
        assertEquals(Command.SetAlarm(19, 0), VoiceCommandProcessor.parse("alarm 7 pm"))
        assertEquals(Command.SetAlarm(20, 15), VoiceCommandProcessor.parse("alarm 8 15 raat"))
        assertEquals(Command.SetAlarm(14, 0), VoiceCommandProcessor.parse("alarm 2 dopahar"))
    }

    @Test
    fun testClipboardRead() {
        assertEquals(Command.ReadClipboard, VoiceCommandProcessor.parse("clipboard padho"))
        assertEquals(Command.ReadClipboard, VoiceCommandProcessor.parse("clipboard kya hai"))
    }

    @Test
    fun testClipboardWrite() {
        assertEquals(Command.WriteClipboard("hello world"), VoiceCommandProcessor.parse("clipboard likho hello world"))
        assertEquals(Command.WriteClipboard("important text"), VoiceCommandProcessor.parse("clipboard save karo important text"))
    }

    @Test
    fun testTodoRead() {
        assertEquals(Command.ReadTodos, VoiceCommandProcessor.parse("todo padho"))
        assertEquals(Command.ReadTodos, VoiceCommandProcessor.parse("todo list batao"))
    }

    @Test
    fun testTodoAdd() {
        assertEquals(Command.AddTodo("buy milk"), VoiceCommandProcessor.parse("todo add karo buy milk"))
        assertEquals(Command.AddTodo("call mom"), VoiceCommandProcessor.parse("todo likho call mom"))
    }

    @Test
    fun testNotesRead() {
        assertEquals(Command.ReadNotes, VoiceCommandProcessor.parse("note padho"))
        assertEquals(Command.ReadNotes, VoiceCommandProcessor.parse("note read"))
    }

    @Test
    fun testNotesSave() {
        assertEquals(Command.SaveNote("my new idea"), VoiceCommandProcessor.parse("note likho my new idea"))
        assertEquals(Command.SaveNote("meeting at 5"), VoiceCommandProcessor.parse("note save karo meeting at 5"))
    }

    @Test
    fun testPlaySong() {
        assertEquals(Command.PlaySong("despacito"), VoiceCommandProcessor.parse("gaana chalao despacito"))
        assertEquals(Command.PlaySong("tum hi ho"), VoiceCommandProcessor.parse("song play tum hi ho"))
    }

    @Test
    fun testGoogleSearch() {
        assertEquals(Command.GoogleSearch("kotlin tutorial"), VoiceCommandProcessor.parse("google pe search karo kotlin tutorial"))
    }

    @Test
    fun testSendWhatsApp() {
        assertEquals(Command.SendWhatsApp("john", "hello there"), VoiceCommandProcessor.parse("whatsapp john message karo hello there"))
        assertEquals(Command.SendWhatsApp("mom", "i am coming"), VoiceCommandProcessor.parse("whatsapp mom message karo i am coming"))
    }

    @Test
    fun testCallContact() {
        assertEquals(Command.CallContact("john"), VoiceCommandProcessor.parse("call john"))
        assertEquals(Command.CallContact("mom"), VoiceCommandProcessor.parse("phone mom"))
    }

    @Test
    fun testSendMessage() {
        assertEquals(Command.SendMessage("john", "hello"), VoiceCommandProcessor.parse("message john hello"))
        assertEquals(Command.SendMessage("john", "hello"), VoiceCommandProcessor.parse("john ko message karo hello"))
    }

    @Test
    fun testOpenApp() {
        assertEquals(Command.OpenApp("youtube"), VoiceCommandProcessor.parse("open youtube"))
        assertEquals(Command.OpenApp("whatsapp"), VoiceCommandProcessor.parse("whatsapp khol"))
    }
}
