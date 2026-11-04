package dev.jcob.launcher.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

val Http: OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(10, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .build()

private fun getJson(url: String): JsonObject {
    Http.newCall(Request.Builder().url(url).build()).execute().use { response ->
        check(response.isSuccessful) { "HTTP ${response.code}" }
        return Json.parseToJsonElement(response.body!!.string()).jsonObject
    }
}

data class Place(val name: String, val lat: Double, val lon: Double)
enum class WeatherKind(val description: String) {
    CLEAR("Clear"), PARTLY_CLOUDY("Partly cloudy"), OVERCAST("Overcast"), FOG("Fog"),
    DRIZZLE("Drizzle"), RAIN("Rain"), SNOW("Snow"), THUNDERSTORM("Thunderstorm"), UNKNOWN(""),
}

data class Weather(val temperature: Int, val kind: WeatherKind, val isDay: Boolean) {
    val description get() = kind.description
}

/** Open-Meteo: free, no API key. */
object WeatherApi {
    suspend fun findCity(query: String): Place? = withContext(Dispatchers.IO) {
        runCatching {
            val url = "https://geocoding-api.open-meteo.com/v1/search".toHttpUrl().newBuilder()
                .addQueryParameter("name", query).addQueryParameter("count", "1").build().toString()
            val first = getJson(url)["results"]?.jsonArray?.firstOrNull()?.jsonObject ?: return@runCatching null
            val country = first["country_code"]?.jsonPrimitive?.content.orEmpty()
            Place(
                name = listOf(first["name"]!!.jsonPrimitive.content, country).filter { it.isNotEmpty() }.joinToString(", "),
                lat = first["latitude"]!!.jsonPrimitive.double,
                lon = first["longitude"]!!.jsonPrimitive.double,
            )
        }.getOrNull()
    }

    suspend fun current(lat: Double, lon: Double, fahrenheit: Boolean): Weather? = withContext(Dispatchers.IO) {
        runCatching {
            val unit = if (fahrenheit) "fahrenheit" else "celsius"
            val current = getJson(
                "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                    "&current=temperature_2m,weather_code,is_day&temperature_unit=$unit"
            )["current"]!!.jsonObject
            Weather(
                temperature = Math.round(current["temperature_2m"]!!.jsonPrimitive.double).toInt(),
                kind = kindOf(current["weather_code"]!!.jsonPrimitive.int),
                isDay = current["is_day"]?.jsonPrimitive?.int != 0,
            )
        }.getOrNull()
    }

    /** WMO weather interpretation codes. */
    private fun kindOf(code: Int) = when (code) {
        0 -> WeatherKind.CLEAR
        1, 2 -> WeatherKind.PARTLY_CLOUDY
        3 -> WeatherKind.OVERCAST
        45, 48 -> WeatherKind.FOG
        in 51..57 -> WeatherKind.DRIZZLE
        in 61..67, in 80..82 -> WeatherKind.RAIN
        in 71..77, 85, 86 -> WeatherKind.SNOW
        in 95..99 -> WeatherKind.THUNDERSTORM
        else -> WeatherKind.UNKNOWN
    }
}

object SpeedTest {
    /** Downloads ~25 MB from Cloudflare and returns megabits per second. */
    suspend fun downloadMbps(onProgress: (Float) -> Unit = {}): Double = withContext(Dispatchers.IO) {
        val bytes = 25_000_000L
        val request = Request.Builder().url("https://speed.cloudflare.com/__down?bytes=$bytes").build()
        val start = System.nanoTime()
        var read = 0L
        Http.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "HTTP ${response.code}" }
            val source = response.body!!.source()
            val buffer = okio.Buffer()
            while (true) {
                val n = source.read(buffer, 64 * 1024)
                if (n == -1L) break
                read += n
                buffer.clear()
                onProgress(read.toFloat() / bytes)
            }
        }
        val seconds = (System.nanoTime() - start) / 1e9
        read * 8 / seconds / 1e6
    }

    /** Round-trip time of a tiny request, in milliseconds. */
    suspend fun latencyMs(): Long = withContext(Dispatchers.IO) {
        val request = Request.Builder().url("https://speed.cloudflare.com/__down?bytes=0").build()
        // First call warms up DNS + TLS; time the second.
        Http.newCall(request).execute().close()
        val start = System.nanoTime()
        Http.newCall(request).execute().close()
        (System.nanoTime() - start) / 1_000_000
    }
}

/** Downloads an APK to [dir] and returns the file. */
suspend fun downloadApk(url: String, dir: File, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
    dir.mkdirs()
    val target = File(dir, "download.apk")
    Http.newCall(Request.Builder().url(url).build()).execute().use { response ->
        check(response.isSuccessful) { "HTTP ${response.code}" }
        val body = response.body!!
        val total = body.contentLength().takeIf { it > 0 }
        body.byteStream().use { input ->
            target.outputStream().use { output ->
                val buf = ByteArray(64 * 1024)
                var done = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    output.write(buf, 0, n)
                    done += n
                    total?.let { onProgress(done.toFloat() / it) }
                }
            }
        }
    }
    target
}
