package by.eschools.diarysigner

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

class DiaryApi(private val login: String, private val password: String) {
    companion object { private const val BASE = "https://diary.e-schools.by/api/v1"; private val MINSK = ZoneId.of("Europe/Minsk");  }
    private class ApiException(message: String, val code: Int) : IllegalStateException(message)
    private fun request(method: String, path: String, token: String? = null, body: JSONObject? = null): String {
        var last: Exception? = null
        for (attempt in 1..3) {
            var conn: HttpURLConnection? = null
            try {
                conn = (URL(BASE + path).openConnection() as HttpURLConnection).apply {
                    requestMethod = method; connectTimeout = 15000; readTimeout = 20000
                    setRequestProperty("Accept", "application/json, text/plain, */*"); setRequestProperty("Origin", "https://diary.e-schools.by"); setRequestProperty("Referer", "https://diary.e-schools.by/")
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/130.0 Mobile Safari/537.36")
                    if (token != null) setRequestProperty("Authorization", token)
                    if (body != null || method == "POST") { doOutput = true; setRequestProperty("Content-Type", "application/json"); outputStream.use { if (body != null) it.write(body.toString().toByteArray(Charsets.UTF_8)) } }
                }
                val code = conn.responseCode; val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val response = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                if (code !in 200..299) { if (code >= 500 && attempt < 3) { Thread.sleep(attempt * 1000L); continue }; throw ApiException("$method $path -> HTTP $code: ${response.take(180)}", code) }
                return response.ifBlank { "null" }
            } catch (e: ApiException) { if (e.code in 400..499) throw e; last = e; if (attempt == 3) throw e }
            catch (e: Exception) { last = e; if (attempt == 3) break }
            finally { conn?.disconnect() }
            Thread.sleep(attempt * 1000L)
        }
        throw IllegalStateException("Ошибка связи с дневником: ${last?.message ?: "сеть недоступна"}")
    }
    private fun alreadySigned(raw: String): Boolean {
        val v = JSONTokener(raw).nextValue()
        return when (v) { null, JSONObject.NULL -> false; is Boolean -> v; is Number -> v.toDouble() != 0.0; is String -> v.equals("true", true) || v.equals("signed", true); is JSONObject -> v.optBoolean("signed", v.optBoolean("is_signed", false)); is JSONArray -> v.length() > 0; else -> false }
    }
    fun signOrCheck(): String {
        val auth = JSONObject(request("POST", "/auth/login/parent", body = JSONObject().put("login", login).put("password", password)))
        val token = auth.optString("auth_token"); if (token.isBlank() || token == "null") error("Не получен токен. Проверьте логин и пароль.")
        val me = JSONObject(request("GET", "/admin/auth/me", token)); val students = me.optJSONArray("students_for_parent") ?: JSONArray(); if (students.length() == 0) error("В аккаунте не найдены ученики.")
        val now = System.currentTimeMillis(); val weeks = JSONArray(request("GET", "/education/diary/time_activities/week_activities", token)); var week: JSONObject? = null
        for (i in 0 until weeks.length()) { val w = weeks.optJSONObject(i) ?: continue; val start = w.optLong("start_ts", -1); val end = w.optLong("end_ts", -1); if (start <= now && now < end && end > start && (week == null || start > week!!.optLong("start_ts"))) week = w }
        val w = week ?: return "Сейчас нет активной учебной недели."; val weekId = w.optString("uuid"); if (weekId.isBlank()) error("Не найден идентификатор недели.")
        val monday = Instant.ofEpochMilli(w.optLong("start_ts")).atZone(MINSK); val sunday = monday.plusDays(6)
        val from = String.format(Locale.ROOT, "%02d.%02d.%04d", monday.dayOfMonth, monday.monthValue, monday.year); val to = String.format(Locale.ROOT, "%02d.%02d.%04d", sunday.dayOfMonth, sunday.monthValue, sunday.year)
        val summary = w.optString("summary", "$from-$to"); val errors = mutableListOf<String>(); var processed = 0; var signedCount = 0
        for (i in 0 until students.length()) {
            val s = students.optJSONObject(i) ?: continue; val name = s.optString("full_name", "Ученик"); val sid = s.optString("profile_id"); val school = s.optString("school_id")
            try {
                require(sid.isNotBlank() && school.isNotBlank()) { "нет ID ученика или школы" }
                val classResponse = JSONObject(request("POST", "/education/diary/schools/$school/students/$sid/classes", token, JSONObject().put("from", from).put("to", to)))
                val classes = classResponse.optJSONArray("classes") ?: JSONArray(); if (classes.length() == 0) error("класс на эту неделю не найден")
                val classId = classes.getJSONObject(0).optString("uuid"); require(classId.isNotBlank()) { "не найден ID класса" }
                val base = "/education/diary/schools/$school/classes/$classId/students/$sid/diary_signatures/weekly"
                val existing = request("GET", "$base?week_activity_id=${URLEncoder.encode(weekId, "UTF-8")}", token)
                if (!alreadySigned(existing)) { request("POST", "$base/activities/$weekId", token); signedCount++ }; processed++
            } catch (e: Exception) { errors.add("$name: ${e.message ?: "ошибка"}") }
        }
        if (errors.isNotEmpty()) error(errors.joinToString("; ").take(350)); if (processed == 0) error("Не обработан ни один ученик.")
        return if (signedCount == 0) "На неделю $summary подпись уже есть у всех учеников." else "Дневник подписан за неделю $summary. Учеников: $processed."
    }
}
