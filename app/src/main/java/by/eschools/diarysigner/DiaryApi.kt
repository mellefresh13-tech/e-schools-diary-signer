package by.eschools.diarysigner

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

class DiaryApi(private val login: String, private val password: String) {
    companion object {
        private const val BASE = "https://diary.e-schools.by/api/v1"
        private val MINSK = ZoneId.of("Europe/Minsk")
    }

    private class ApiException(message: String, val code: Int) : IllegalStateException(message)

    private fun request(method: String, path: String, token: String? = null, body: JSONObject? = null): String {
        var last: Exception? = null
        for (attempt in 1..3) {
            var conn: HttpURLConnection? = null
            try {
                conn = (URL(BASE + path).openConnection() as HttpURLConnection).apply {
                    requestMethod = method
                    connectTimeout = 15000
                    readTimeout = 20000
                    setRequestProperty("Accept", "application/json, text/plain, */*")
                    setRequestProperty("Origin", "https://diary.e-schools.by")
                    setRequestProperty("Referer", "https://diary.e-schools.by/")
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/130.0 Mobile Safari/537.36")
                    if (token != null) setRequestProperty("Authorization", token)
                    if (body != null || method == "POST") {
                        doOutput = true
                        setRequestProperty("Content-Type", "application/json")
                        outputStream.use {
                            if (body != null) it.write(body.toString().toByteArray(Charsets.UTF_8))
                        }
                    }
                }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val response = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                if (code !in 200..299) {
                    if (code >= 500 && attempt < 3) {
                        Thread.sleep(attempt * 1000L)
                        continue
                    }
                    throw ApiException("$method $path -> HTTP $code: ${response.take(180)}", code)
                }
                return response.ifBlank { "null" }
            } catch (e: ApiException) {
                if (e.code in 400..499) throw e
                last = e
                if (attempt == 3) throw e
            } catch (e: Exception) {
                last = e
                if (attempt == 3) break
            } finally {
                conn?.disconnect()
            }
            Thread.sleep(attempt * 1000L)
        }
        throw IllegalStateException("Ошибка связи с дневником: ${last?.message ?: "сеть недоступна"}")
    }

    /**
     * The endpoint returns an array of signature records. A record belongs to a
     * particular week only when its activity_id matches that week's UUID.
     * Unknown response formats fail closed so we never sign based on guesswork.
     */
    private fun isSignedForWeek(raw: String, weekId: String): Boolean {
        return when (val value = JSONTokener(raw).nextValue()) {
            is JSONArray -> {
                if (value.length() == 0) {
                    false
                } else {
                    val matching = (0 until value.length()).any { index ->
                        value.optJSONObject(index)?.optString("activity_id") == weekId
                    }
                    if (!matching) {
                        error("API вернул записи подписей, но среди них нет подписи для выбранной недели.")
                    }
                    true
                }
            }
            is JSONObject -> {
                if (value.has("activity_id")) {
                    val activityId = value.optString("activity_id")
                    if (activityId != weekId) {
                        error("API вернул подпись для другой недели.")
                    }
                    true
                } else if (value.has("signed") || value.has("is_signed")) {
                    value.optBoolean("signed", value.optBoolean("is_signed", false))
                } else {
                    error("Неизвестный формат ответа о подписи; подписание отменено для безопасности.")
                }
            }
            null, JSONObject.NULL -> false
            else -> error("Неизвестный формат ответа о подписи; подписание отменено для безопасности.")
        }
    }

    private fun signingIsAvailableNow(now: java.time.ZonedDateTime): Boolean {
        val day = now.dayOfWeek
        return day.value >= DayOfWeek.FRIDAY.value &&
            (day != DayOfWeek.FRIDAY || now.hour >= 18)
    }

    fun signOrCheck(): String {
        val auth = JSONObject(
            request(
                "POST",
                "/auth/login/parent",
                body = JSONObject().put("login", login).put("password", password)
            )
        )
        val token = auth.optString("auth_token")
        if (token.isBlank() || token == "null") error("Не получен токен. Проверьте логин и пароль.")

        val me = JSONObject(request("GET", "/admin/auth/me", token))
        val students = me.optJSONArray("students_for_parent") ?: JSONArray()
        if (students.length() == 0) error("В аккаунте не найдены ученики.")

        val now = Instant.now().atZone(MINSK)
        val weeks = JSONArray(request("GET", "/education/diary/time_activities/week_activities", token))
        var week: JSONObject? = null
        for (i in 0 until weeks.length()) {
            val candidate = weeks.optJSONObject(i) ?: continue
            val start = candidate.optLong("start_ts", -1)
            val end = candidate.optLong("end_ts", -1)
            if (start >= 0 && end > start && start <= now.toInstant().toEpochMilli() &&
                now.toInstant().toEpochMilli() < end &&
                (week == null || start > week!!.optLong("start_ts", -1))
            ) {
                week = candidate
            }
        }

        val currentWeek = week ?: return "Сейчас нет активной учебной недели."
        val weekId = currentWeek.optString("uuid")
        if (weekId.isBlank()) error("Не найден идентификатор текущей недели.")

        val startDate = Instant.ofEpochMilli(currentWeek.optLong("start_ts")).atZone(MINSK).toLocalDate()
        // end_ts is treated as the exclusive boundary; subtract 1 ms to get the final covered date.
        val endDate = Instant.ofEpochMilli(currentWeek.optLong("end_ts") - 1).atZone(MINSK).toLocalDate()
        if (endDate.isBefore(startDate)) error("Некорректный интервал текущей недели.")

        val from = String.format(Locale.ROOT, "%02d.%02d.%04d", startDate.dayOfMonth, startDate.monthValue, startDate.year)
        val to = String.format(Locale.ROOT, "%02d.%02d.%04d", endDate.dayOfMonth, endDate.monthValue, endDate.year)
        val summary = currentWeek.optString("summary").takeIf { it.isNotBlank() } ?: "$from–$to"

        if (!signingIsAvailableNow(now)) {
            return "Подпись дневника доступна после 18:00 пятницы текущей недели. Сейчас выбрана неделя $summary; подпись не отправлялась."
        }

        val errors = mutableListOf<String>()
        var processed = 0
        var signedCount = 0
        for (i in 0 until students.length()) {
            val student = students.optJSONObject(i) ?: continue
            val name = student.optString("full_name", "Ученик")
            val studentId = student.optString("profile_id")
            val schoolId = student.optString("school_id")
            try {
                require(studentId.isNotBlank() && schoolId.isNotBlank()) { "нет ID ученика или школы" }
                val classResponse = JSONObject(
                    request(
                        "POST",
                        "/education/diary/schools/$schoolId/students/$studentId/classes",
                        token,
                        JSONObject().put("from", from).put("to", to)
                    )
                )
                val classes = classResponse.optJSONArray("classes") ?: JSONArray()
                if (classes.length() == 0) error("класс на эту неделю не найден")
                val classId = classes.getJSONObject(0).optString("uuid")
                require(classId.isNotBlank()) { "не найден ID класса" }

                val base = "/education/diary/schools/$schoolId/classes/$classId/students/$studentId/diary_signatures/weekly"
                val existing = request(
                    "GET",
                    "$base?week_activity_id=${URLEncoder.encode(weekId, "UTF-8")}",
                    token
                )
                if (!isSignedForWeek(existing, weekId)) {
                    request("POST", "$base/activities/$weekId", token)
                    signedCount++
                }
                processed++
            } catch (e: Exception) {
                errors.add("$name: ${e.message ?: "ошибка"}")
            }
        }

        if (errors.isNotEmpty()) error(errors.joinToString("; ").take(350))
        if (processed == 0) error("Не обработан ни один ученик.")
        return if (signedCount == 0) {
            "На неделю $summary подпись уже есть у всех учеников."
        } else {
            "Дневник подписан за неделю $summary. Учеников: $processed."
        }
    }
}
