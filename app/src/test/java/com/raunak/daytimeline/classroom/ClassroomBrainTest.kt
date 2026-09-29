package com.raunak.daytimeline.classroom

import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.raunak.daytimeline.campus.CampusStore
import com.raunak.daytimeline.campus.Subject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ClassroomBrainTest {
    // 2026-03-10 is a Tuesday
    private val now = LocalDateTime.of(2026, 3, 10, 9, 0)
    private val today = now.toLocalDate()
    private val zone = ZoneId.systemDefault()
    private fun ms(t: LocalDateTime) = t.atZone(zone).toInstant().toEpochMilli()
    private val subjects = listOf(Subject(1, "Computer Networks", code = "CS301"), Subject(2, "DBMS"), Subject(3, "Machine Learning"))

    @Test
    fun finds_dates_and_due_times() {
        assertThat(DateFinder.due("Due tomorrow, 11:59 PM", now)).isEqualTo(LocalDateTime.of(2026, 3, 11, 23, 59))
        assertThat(DateFinder.due("Due Mar 14", now)).isEqualTo(LocalDateTime.of(2026, 3, 14, 23, 59))
        assertThat(DateFinder.due("please submit by 15 March 5pm", now)).isEqualTo(LocalDateTime.of(2026, 3, 15, 17, 0))
        assertThat(DateFinder.due("Due Fri", now)).isEqualTo(LocalDateTime.of(2026, 3, 13, 23, 59))
        assertThat(DateFinder.date("quiz on 12/03", today)).isEqualTo(LocalDate.of(2026, 3, 12))
        assertThat(DateFinder.date("see you next monday", today)).isEqualTo(LocalDate.of(2026, 3, 16))
        assertThat(DateFinder.date("nothing here", today)).isNull()
    }

    @Test
    fun understands_classroom_notifications() {
        val a = ClassroomBrain.fromNotification(ClassroomBrain.CLASSROOM_PACKAGE, "CS301 Computer Networks", "New assignment: \"Lab 4 – Socket programming\"", "New assignment: \"Lab 4 – Socket programming\"\nDue tomorrow, 11:59 PM", "", ms(now), now)!!
        assertThat(a.kind).isEqualTo(ClassKind.ASSIGNMENT)
        assertThat(a.title).isEqualTo("Lab 4 – Socket programming")
        assertThat(a.course).isEqualTo("CS301 Computer Networks")
        assertThat(a.dueAt).isEqualTo(ms(LocalDateTime.of(2026, 3, 11, 23, 59)))
        assertThat(a.state).isEqualTo(WorkState.PENDING)
        val q = ClassroomBrain.fromNotification(ClassroomBrain.CLASSROOM_PACKAGE, "DBMS", "New quiz: Normalization", "", "", ms(now), now)!!
        assertThat(q.kind).isEqualTo(ClassKind.QUIZ)
        val msg = ClassroomBrain.fromNotification(ClassroomBrain.CLASSROOM_PACKAGE, "Machine Learning", "Prof. Rao added a private comment", "Please resubmit with plots", "", ms(now), now)!!
        assertThat(msg.kind).isEqualTo(ClassKind.COMMENT)
        val mail = ClassroomBrain.fromNotification(ClassroomBrain.GMAIL_PACKAGE, "Prof. Rao (Classroom)", "New announcement: Mid sem syllabus", "New announcement in Machine Learning", "", ms(now), now)!!
        assertThat(mail.kind).isEqualTo(ClassKind.ANNOUNCEMENT)
        assertThat(ClassroomBrain.fromNotification("com.whatsapp", "x", "Due tomorrow", "", "", ms(now), now)).isNull()
        assertThat(ClassroomBrain.fromNotification(ClassroomBrain.GMAIL_PACKAGE, "Amazon", "Your order", "", "", ms(now), now)).isNull()
    }

    @Test
    fun matches_courses_to_subjects() {
        assertThat(ClassroomBrain.matchSubject("CS301 - Networks (B.Tech Sem 5)", subjects, emptyMap())?.id).isEqualTo(1L)
        assertThat(ClassroomBrain.matchSubject("Database Management Systems", subjects, emptyMap())?.id).isEqualTo(2L)
        assertThat(ClassroomBrain.matchSubject("Machine Learning Lab", subjects, emptyMap())?.id).isEqualTo(3L)
        assertThat(ClassroomBrain.matchSubject("Sports", subjects, emptyMap())).isNull()
        assertThat(ClassroomBrain.matchSubject("Sports", subjects, mapOf("Sports" to 2L))?.id).isEqualTo(2L)
    }

    @Test
    fun ranks_plans_and_alerts_intelligently() {
        val s = ClassroomSettings()
        val overdue = ClassItem("1", "API", ClassKind.ASSIGNMENT, "DBMS", "ER diagram", dueAt = ms(now.minusHours(5)), postedAt = 0, state = WorkState.LATE)
        val quizSoon = ClassItem("2", "API", ClassKind.QUIZ, "ML", "Quiz 2", dueAt = ms(now.plusHours(20)), postedAt = 0, state = WorkState.PENDING)
        val later = ClassItem("3", "API", ClassKind.ASSIGNMENT, "CN", "Lab 5", dueAt = ms(now.plusDays(5)), postedAt = 0, state = WorkState.PENDING)
        val submitted = later.copy(id = "4", state = WorkState.SUBMITTED)
        val material = ClassItem("5", "API", ClassKind.MATERIAL, "CN", "Slides", postedAt = 0)
        val r = ClassroomBrain.rank(listOf(material, later, submitted, quizSoon, overdue), now, s)
        assertThat(r.map { it.item.id }).containsExactly("1", "2", "3", "5").inOrder()
        assertThat(r.first().reason).startsWith("Overdue")
        val targets = ClassroomBrain.studyTargets(listOf(quizSoon, later), now, s)
        assertThat(targets.first().item.id).isEqualTo("2")
        assertThat(targets.first().minutesToday).isEqualTo(90) // due tomorrow → all of it today
        assertThat(targets[1].minutesToday).isEqualTo(25) // 120 min over the 4 days before it's due, rounded to 5
        assertThat(ClassroomBrain.alertNow(quizSoon, now, s)).isTrue()
        assertThat(ClassroomBrain.alertNow(later, now, s)).isFalse()
        assertThat(ClassroomBrain.alertNow(material, now, s)).isFalse()
        assertThat(ClassroomBrain.digest(listOf(overdue, quizSoon, later), now, s, 0).first()).contains("3 pending")
    }

    @Test
    fun spots_actions_in_announcements() {
        val cancel = ClassItem("a1", "API", ClassKind.ANNOUNCEMENT, "Computer Networks", "No class tomorrow", "There will be no class tomorrow due to the seminar.", postedAt = ms(now))
        val test = ClassItem("a2", "API", ClassKind.ANNOUNCEMENT, "DBMS", "Surprise test on 14 March", postedAt = ms(now))
        val old = ClassItem("a3", "API", ClassKind.ANNOUNCEMENT, "DBMS", "Class cancelled yesterday", postedAt = ms(now.minusDays(3)))
        val sg = ClassroomBrain.suggestions(listOf(cancel, test, old), subjects, ClassroomSettings(), today, emptySet())
        assertThat(sg.map { it.kind }).containsExactly(Suggestion.Kind.CANCEL_CLASS, Suggestion.Kind.EXAM)
        assertThat(sg.first().date).isEqualTo(today.plusDays(1))
        assertThat(sg.first().subjectId).isEqualTo(1L)
        assertThat(ClassroomBrain.suggestions(listOf(cancel), subjects, ClassroomSettings(), today, setOf(sg.first().id))).isEmpty()
    }

    @Test
    fun merges_sources_and_links_deadlines() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val store = ClassroomStore.get(ctx)
        val due = ms(LocalDateTime.now().plusDays(2).withHour(23).withMinute(59))
        val fromNotif = ClassItem("n1", "NOTIFICATION", ClassKind.ASSIGNMENT, "Classroom", "Lab 4", dueAt = due, postedAt = 1, state = WorkState.PENDING)
        ClassroomSync.ingest(ctx, listOf(fromNotif))
        assertThat(CampusStore.get(ctx).data.value.deadlines.count { it.notes.contains("classroom:n1") }).isEqualTo(1)
        // The sync later sees the same work, already turned in
        val fromApi = ClassItem("w9", "API", ClassKind.ASSIGNMENT, "CN", "Lab 4", dueAt = due, postedAt = 2, state = WorkState.SUBMITTED)
        val fresh = store.merge(listOf(fromApi))
        assertThat(fresh).isEmpty()
        assertThat(store.data.value.items.single().id).isEqualTo("w9")
        assertThat(store.data.value.items.single().done).isTrue()
        val old = com.google.gson.Gson().fromJson("{\"items\":[]}", ClassroomData::class.java).normalized()
        assertThat(old.settings.importantWords).isNotEmpty()
    }
}
