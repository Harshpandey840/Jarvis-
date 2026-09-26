import java.io.File

fun main() {
    val gradleCache = File(System.getProperty("user.home"), ".gradle/caches/modules-2/files-2.1/xyz.rementia/openwakeword")
    if (gradleCache.exists()) {
        Runtime.getRuntime().exec("find ${gradleCache.absolutePath} -name *.jar").inputStream.bufferedReader().use {
            val jars = it.readText().trim().split("\n")
            for (jar in jars) {
                if (jar.isNotBlank()) {
                    println("JAR: $jar")
                    Runtime.getRuntime().exec("jar tf $jar").inputStream.bufferedReader().use { jarIt ->
                        println(jarIt.readText())
                    }
                }
            }
        }
    } else {
        println("Cache not found")
    }
}
