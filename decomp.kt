import java.io.File

fun main() {
    Runtime.getRuntime().exec("javap -c -p /tmp/oww/classes_out/com/rementia/openwakeword/lib/WakeWordEngine.class").inputStream.bufferedReader().use {
        println(it.readText())
    }
}
