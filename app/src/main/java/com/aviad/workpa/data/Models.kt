package com.aviad.workpa.data

/** An employer the user works for. */
data class Employer(val id: String, val name: String)

/** What the app adds on top of a calendar event: who pays, how much, and whether it was paid. */
data class JobMeta(
    val key: String,
    val employerId: String? = null,
    val amount: Double? = null,
    val paid: Boolean = false,
    val notes: String = "",
)

/** A calendar event as read from the phone's calendar. */
data class CalEvent(
    val key: String,
    val eventId: Long,
    val calendarId: Long,
    val title: String,
    val begin: Long,
    val end: Long,
    val location: String,
    val allDay: Boolean,
    val recurring: Boolean,
)

/** A job = calendar event + its meta. */
data class Job(val event: CalEvent, val meta: JobMeta) {
    val key get() = event.key
}

data class CalendarInfo(
    val id: Long,
    val name: String,
    val account: String,
    val writable: Boolean,
    val primary: Boolean,
)

data class AppSettings(
    val keywords: List<String> = listOf("הגברה", "סאונד"),
    val calendarId: Long? = null,
    val reportName: String = "אביעד",
    val jobTypes: List<String> = listOf("הגברה", "סאונד"),
)
