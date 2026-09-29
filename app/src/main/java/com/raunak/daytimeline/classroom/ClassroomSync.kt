package com.raunak.daytimeline.classroom

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.raunak.daytimeline.MainActivity
import com.raunak.daytimeline.R
import com.raunak.daytimeline.campus.CampusStore
import com.raunak.daytimeline.campus.Deadline
import com.raunak.daytimeline.campus.DeadlineKind
import kotlinx.coroutines.suspendCancellableCoroutine
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/** Google sign-in for Classroom (read-only scopes). */
object ClassroomAuth {
    val scopes = listOf(
        "https://www.googleapis.com/auth/classroom.courses.readonly",
        "https://www.googleapis.com/auth/classroom.coursework.me.readonly",
        "https://www.googleapis.com/auth/classroom.announcements.readonly",
        "https://www.googleapis.com/auth/classroom.courseworkmaterials.readonly"
    )

    fun request(): AuthorizationRequest = AuthorizationRequest.builder().setRequestedScopes(scopes.map { Scope(it) }).build()

    /** Access token without showing UI, or null when the user has to sign in / consent again. */
    suspend fun silentToken(context: Context): String? = suspendCancellableCoroutine { cont ->
        Identity.getAuthorizationClient(context).authorize(request())
            .addOnSuccessListener { r: AuthorizationResult -> cont.resume(if (r.hasResolution()) null else r.accessToken) }
            .addOnFailureListener { cont.resume(null) }
    }

    /** SHA-1 of this app's signing certificate — needed once when registering the app with Google. */
    fun signingSha1(context: Context): String = runCatching {
        val pm = context.packageManager
        val sigs = if (android.os.Build.VERSION.SDK_INT >= 28) pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo?.apkContentsSigners
        else @Suppress("DEPRECATION") pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures
        MessageDigest.getInstance("SHA-1").digest(sigs!!.first().toByteArray()).joinToString(":") { "%02X".format(it) }
    }.getOrDefault("unavailable")
}

/** Reads courses, coursework (with your submission state), materials and announcements. */
object ClassroomApi {
    private const val BASE = "https://classroom.googleapis.com/v1"

    private fun get(url: String, token: String): JsonObject {
        val c = URL(url).openConnection() as HttpURLConnection
        c.setRequestProperty("Authorization", "Bearer $token")
        c.connectTimeout = 15_000; c.readTimeout = 20_000
        val code = c.responseCode
        val body = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (code !in 200..299) {
            val msg = runCatching { JsonParser.parseString(body).asJsonObject.getAsJsonObject("error").get("message").asString }.getOrDefault("HTTP $code")
            throw IllegalStateException(msg)
        }
        return JsonParser.parseString(body.ifBlank { "{}" }).asJsonObject
    }

    private fun JsonObject.str(k: String) = get(k)?.takeIf { !it.isJsonNull }?.asString ?: ""

    private fun time(v: String): Long = runCatching { java.time.Instant.parse(v).toEpochMilli() }.getOrDefault(System.currentTimeMillis())

    private fun due(w: JsonObject): Long? {
        val d = w.getAsJsonObject("dueDate") ?: return null
        val t = w.getAsJsonObject("dueTime")
        val date = runCatching { LocalDate.of(d.get("year").asInt, d.get("month").asInt, d.get("day").asInt) }.getOrNull() ?: return null
        // Classroom stores due times in UTC; with no time the work is due at the end of the day.
        return if (t == null || t.size() == 0) date.atTime(23, 59).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        else date.atTime(LocalTime.of(t.get("hours")?.asInt ?: 0, t.get("minutes")?.asInt ?: 0)).toInstant(ZoneOffset.UTC).toEpochMilli()
    }

    fun fetch(token: String): List<ClassItem> {
        val out = mutableListOf<ClassItem>()
        val courses = get("$BASE/courses?studentId=me&courseStates=ACTIVE&pageSize=50", token).getAsJsonArray("courses") ?: return out
        for (ce in courses) {
            val course = ce.asJsonObject
            val cid = course.str("id"); val cname = course.str("name").ifBlank { "Course" }
            val enc = URLEncoder.encode(cid, "UTF-8")
            val subs = runCatching { get("$BASE/courses/$enc/courseWork/-/studentSubmissions?userId=me&pageSize=100", token).getAsJsonArray("studentSubmissions") }.getOrNull()
            val subByWork = subs?.associate { it.asJsonObject.str("courseWorkId") to it.asJsonObject } ?: emptyMap()
            runCatching { get("$BASE/courses/$enc/courseWork?orderBy=updateTime%20desc&pageSize=40", token).getAsJsonArray("courseWork") }.getOrNull()?.forEach { we ->
                val w = we.asJsonObject
                val title = w.str("title")
                val kind = when {
                    w.str("workType").contains("QUESTION") -> ClassKind.QUESTION
                    Regex("""\b(quiz|test)\b""", RegexOption.IGNORE_CASE).containsMatchIn(title) -> ClassKind.QUIZ
                    else -> ClassKind.ASSIGNMENT
                }
                val sub = subByWork[w.str("id")]
                val dueAt = due(w)
                val state = when (sub?.str("state")) {
                    "TURNED_IN" -> WorkState.SUBMITTED
                    "RETURNED" -> WorkState.RETURNED
                    else -> if (sub?.get("late")?.asBoolean == true || (dueAt != null && dueAt < System.currentTimeMillis())) WorkState.LATE else WorkState.PENDING
                }
                out += ClassItem("w" + w.str("id"), "API", kind, cname, title, w.str("description"), dueAt, time(w.str("creationTime")), w.str("alternateLink"), state,
                    w.get("maxPoints")?.takeIf { !it.isJsonNull }?.asDouble, sub?.get("assignedGrade")?.takeIf { !it.isJsonNull }?.asDouble)
                if (state == WorkState.RETURNED && sub?.get("assignedGrade")?.isJsonNull == false) {
                    out += ClassItem("g" + w.str("id"), "API", ClassKind.GRADE, cname, "$title: ${sub.get("assignedGrade").asDouble.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() }}" +
                        (w.get("maxPoints")?.takeIf { !it.isJsonNull }?.let { "/${it.asDouble.toInt()}" } ?: ""), "", null, time(sub.str("updateTime")), w.str("alternateLink"))
                }
            }
            runCatching { get("$BASE/courses/$enc/courseWorkMaterials?pageSize=20", token).getAsJsonArray("courseWorkMaterial") }.getOrNull()?.forEach { me ->
                val m = me.asJsonObject
                out += ClassItem("m" + m.str("id"), "API", ClassKind.MATERIAL, cname, m.str("title"), m.str("description"), null, time(m.str("creationTime")), m.str("alternateLink"))
            }
            runCatching { get("$BASE/courses/$enc/announcements?pageSize=20", token).getAsJsonArray("announcements") }.getOrNull()?.forEach { ae ->
                val a = ae.asJsonObject
                val text = a.str("text")
                out += ClassItem("a" + a.str("id"), "API", ClassKind.ANNOUNCEMENT, cname, text.lineSequence().firstOrNull { it.isNotBlank() }?.take(100) ?: "Announcement", text, null, time(a.str("creationTime")), a.str("alternateLink"))
            }
        }
        return out
    }
}

/** Glue: merges new items, keeps Campus deadlines in step, alerts smartly and schedules sync and digest. */
object ClassroomSync {
    const val CHANNEL_URGENT = "classroom_urgent"
    const val CHANNEL_DIGEST = "classroom_digest"
    private const val MARK = "classroom:"

    /** Called with items from any source. */
    fun ingest(context: Context, incoming: List<ClassItem>) {
        val store = ClassroomStore.get(context)
        val fresh = store.merge(incoming)
        val data = store.data.value
        if (data.settings.autoDeadlines) syncDeadlines(context, data)
        val now = LocalDateTime.now()
        fresh.filter { ClassroomBrain.alertNow(it, now, data.settings) }.take(4).forEach { alert(context, it, now, data.settings) }
    }

    /** Pending work with a due date ↔ Campus deadline (created, moved, or ticked off when turned in). */
    fun syncDeadlines(context: Context, data: ClassroomData = ClassroomStore.get(context).data.value) {
        val campus = CampusStore.get(context)
        val zone = ZoneId.systemDefault()
        val now = System.currentTimeMillis()
        campus.update { d ->
            var deadlines = d.deadlines
            data.items.filter { it.kind in setOf(ClassKind.ASSIGNMENT, ClassKind.QUIZ, ClassKind.QUESTION) && it.dueAt != null }.forEach { i ->
                val marker = "$MARK${i.id}"
                val existing = deadlines.firstOrNull { it.notes.contains(marker) }
                val done = !ClassroomBrain.isOpenWork(i, now) && !i.hidden && i.snoozedUntil <= now
                val at = java.time.Instant.ofEpochMilli(i.dueAt!!).atZone(zone).toLocalDateTime()
                val subject = ClassroomBrain.matchSubject(i.course, d.subjects, data.settings.courseMap)?.id
                if (existing == null) {
                    if (!done && !i.hidden && at.isAfter(LocalDateTime.now().minusDays(7))) deadlines = deadlines + Deadline(campus.nextId(), i.title, if (i.kind == ClassKind.QUIZ) DeadlineKind.QUIZ else DeadlineKind.ASSIGNMENT,
                        at.toLocalDate().toString(), at.hour * 60 + at.minute, subject, false, "From Classroom · ${i.course}\n$marker", if (i.kind == ClassKind.QUIZ) "Quiz" else "Assignment")
                } else {
                    deadlines = deadlines.map { if (it.id == existing.id) it.copy(title = i.title, date = at.toLocalDate().toString(), minute = at.hour * 60 + at.minute, subjectId = it.subjectId ?: subject, done = it.done || done || i.hidden) else it }
                }
            }
            d.copy(deadlines = deadlines)
        }
    }

    /** When a Classroom deadline is ticked in Campus, mark the item done too. */
    fun deadlineDone(context: Context, notes: String) {
        val id = notes.substringAfter(MARK, "").lineSequence().firstOrNull()?.trim() ?: return
        if (id.isNotBlank()) ClassroomStore.get(context).item(id) { it.copy(done = true) }
    }

    private fun channels(context: Context) {
        val m = context.getSystemService(NotificationManager::class.java)
        m.createNotificationChannel(NotificationChannel(CHANNEL_URGENT, "Classroom — urgent", NotificationManager.IMPORTANCE_HIGH))
        m.createNotificationChannel(NotificationChannel(CHANNEL_DIGEST, "Classroom — daily digest", NotificationManager.IMPORTANCE_DEFAULT))
    }

    private fun open(context: Context) = PendingIntent.getActivity(context, 7501, Intent(context, MainActivity::class.java).putExtra(EXTRA_OPEN, "classroom").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    fun alert(context: Context, i: ClassItem, now: LocalDateTime, s: ClassroomSettings) {
        channels(context)
        val text = ClassroomBrain.explain(i, now, s)
        val n = NotificationCompat.Builder(context, CHANNEL_URGENT).setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(when (i.kind) { ClassKind.QUIZ -> "Quiz: ${i.title}"; ClassKind.COMMENT -> "Message in ${i.course}"; ClassKind.GRADE -> "Graded in ${i.course}"; else -> "${i.kind.label}: ${i.title}" })
            .setContentText(text).setStyle(NotificationCompat.BigTextStyle().bigText(text + if (i.body.isNotBlank() && i.kind != ClassKind.ASSIGNMENT) "\n\n" + i.body.take(300) else ""))
            .setContentIntent(open(context)).setAutoCancel(true).setPriority(NotificationCompat.PRIORITY_HIGH).build()
        runCatching { NotificationManagerCompat.from(context).notify(i.id.hashCode(), n) }
    }

    fun digest(context: Context) {
        val store = ClassroomStore.get(context)
        val d = store.data.value
        val prefs = context.getSharedPreferences("chronora_classroom_meta", Context.MODE_PRIVATE)
        val since = prefs.getLong("digest_at", System.currentTimeMillis() - 86_400_000L)
        prefs.edit().putLong("digest_at", System.currentTimeMillis()).apply()
        if (d.items.isEmpty()) return
        val lines = ClassroomBrain.digest(d.items, LocalDateTime.now(), d.settings, since)
        channels(context)
        val style = NotificationCompat.InboxStyle().also { st -> lines.drop(1).forEach { st.addLine(it) } }
        val n = NotificationCompat.Builder(context, CHANNEL_DIGEST).setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Classroom today").setContentText(lines.first()).setStyle(style)
            .setContentIntent(open(context)).setAutoCancel(true).build()
        runCatching { NotificationManagerCompat.from(context).notify(7502, n) }
    }

    /** Daily digest alarm and the periodic sync (when connected). */
    fun schedule(context: Context) {
        val s = ClassroomStore.get(context).data.value.settings
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = PendingIntent.getBroadcast(context, 7503, Intent(context, ClassroomReceiver::class.java).setAction(ACTION_DIGEST), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        am.cancel(pi)
        if (!s.digestOff) {
            var at = LocalDate.now().atTime(s.digestMinute / 60, s.digestMinute % 60)
            if (!at.isAfter(LocalDateTime.now())) at = at.plusDays(1)
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), pi)
        }
        val wm = runCatching { WorkManager.getInstance(context) }.getOrNull() ?: return
        if (s.connected) wm.enqueueUniquePeriodicWork("classroom-sync", ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<ClassroomSyncWorker>(s.syncEveryHours.toLong(), TimeUnit.HOURS).setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build())
        else wm.cancelUniqueWork("classroom-sync")
    }

    /** Pulls from Google Classroom with [token] (or a silent one) and ingests the result. */
    suspend fun syncNow(context: Context, token: String? = null): Result<Int> = runCatching {
        val t = token ?: ClassroomAuth.silentToken(context) ?: error("Sign in again to continue syncing")
        val items = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { ClassroomApi.fetch(t) }
        ingest(context, items)
        items.size
    }.also { r ->
        ClassroomStore.get(context).settings { it.copy(lastSync = if (r.isSuccess) System.currentTimeMillis() else it.lastSync, lastError = r.exceptionOrNull()?.message) }
    }

    const val ACTION_DIGEST = "chronora.classroom.DIGEST"
    const val EXTRA_OPEN = "chronora.open"
}

class ClassroomSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = if (ClassroomSync.syncNow(applicationContext).isSuccess) Result.success() else Result.retry()
}

class ClassroomReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ClassroomSync.ACTION_DIGEST) ClassroomSync.digest(context)
        ClassroomSync.schedule(context)
    }
}

/** Hook for the notification listener: turns Classroom notifications into items. */
object ClassroomCapture {
    fun onPosted(context: Context, pkg: String, extras: android.os.Bundle, postTime: Long) {
        if (pkg != ClassroomBrain.CLASSROOM_PACKAGE && pkg != ClassroomBrain.GMAIL_PACKAGE) return
        val store = ClassroomStore.get(context)
        if (!store.data.value.settings.captureNotifications) return
        fun s(k: String) = extras.getCharSequence(k)?.toString().orEmpty()
        val item = ClassroomBrain.fromNotification(pkg, s("android.title"), s("android.text"), s("android.bigText"), s("android.subText"), postTime, LocalDateTime.now()) ?: return
        ClassroomSync.ingest(context, listOf(item))
    }
}
