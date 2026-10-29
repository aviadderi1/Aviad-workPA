package com.aviad.workpa.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract
import java.time.YearMonth
import java.time.ZoneId
import java.util.TimeZone

/**
 * Reads and writes the phone's calendar (which is kept in sync with Google Calendar
 * by Android itself – no sign-in needed, only the calendar permission).
 */
class CalendarRepo(private val context: Context) {
    private val cr get() = context.contentResolver

    /** All events in [month] whose title contains one of [keywords]. */
    fun loadMonth(month: YearMonth, keywords: List<String>): List<CalEvent> {
        val zone = ZoneId.systemDefault()
        // one day margin on each side so all-day events (stored in UTC) are not missed
        val begin = month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli() - DAY
        val end = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli() + DAY

        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.RRULE,
            CalendarContract.Instances.CALENDAR_ID,
        )
        val out = ArrayList<CalEvent>()
        val kw = keywords.map { it.trim() }.filter { it.isNotEmpty() }
        CalendarContract.Instances.query(cr, projection, begin, end)?.use { c ->
            while (c.moveToNext()) {
                val title = c.getString(3) ?: ""
                if (kw.none { title.contains(it, ignoreCase = true) }) continue
                val eventId = c.getLong(0)
                val b = c.getLong(1)
                val allDay = c.getInt(5) == 1
                val recurring = !c.getString(6).isNullOrEmpty()
                val ev = CalEvent(
                    key = if (recurring) "${eventId}_$b" else "$eventId",
                    eventId = eventId,
                    calendarId = c.getLong(7),
                    title = title,
                    begin = b,
                    end = c.getLong(2),
                    location = c.getString(4) ?: "",
                    allDay = allDay,
                    recurring = recurring,
                )
                // keep only events whose (local) date really is in this month
                if (YearMonth.from(ev.localDate()) == month) out += ev
            }
        }
        return out.distinctBy { it.key }.sortedBy { it.begin }
    }

    fun calendars(): List<CalendarInfo> {
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
            CalendarContract.Calendars.IS_PRIMARY,
            CalendarContract.Calendars.OWNER_ACCOUNT,
        )
        val out = ArrayList<CalendarInfo>()
        cr.query(CalendarContract.Calendars.CONTENT_URI, projection, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val account = c.getString(2) ?: ""
                val owner = c.getString(5) ?: ""
                out += CalendarInfo(
                    id = c.getLong(0),
                    name = c.getString(1) ?: account,
                    account = account,
                    writable = c.getInt(3) >= CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR,
                    primary = c.getInt(4) == 1 || (account.isNotEmpty() && account == owner && account.contains("@")),
                )
            }
        }
        return out
    }

    /** The calendar new jobs are written to: the chosen one, else the main Google calendar. */
    fun defaultCalendarId(chosen: Long?): Long? {
        val cals = calendars().filter { it.writable }
        chosen?.let { id -> if (cals.any { it.id == id }) return id }
        return (cals.firstOrNull { it.primary && it.account.endsWith("gmail.com") }
            ?: cals.firstOrNull { it.primary }
            ?: cals.firstOrNull())?.id
    }

    fun insertEvent(calendarId: Long, title: String, begin: Long, end: Long, location: String, notes: String): Long? {
        val v = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, title)
            put(CalendarContract.Events.DTSTART, begin)
            put(CalendarContract.Events.DTEND, end)
            put(CalendarContract.Events.EVENT_LOCATION, location)
            put(CalendarContract.Events.DESCRIPTION, notes)
            put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
        }
        val uri = cr.insert(CalendarContract.Events.CONTENT_URI, v) ?: return null
        return ContentUris.parseId(uri)
    }

    fun updateEvent(eventId: Long, title: String, begin: Long, end: Long, location: String) {
        val v = ContentValues().apply {
            put(CalendarContract.Events.TITLE, title)
            put(CalendarContract.Events.DTSTART, begin)
            put(CalendarContract.Events.DTEND, end)
            put(CalendarContract.Events.EVENT_LOCATION, location)
            put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
        }
        cr.update(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId), v, null, null)
    }

    fun deleteEvent(eventId: Long) {
        cr.delete(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId), null, null)
    }

    companion object {
        const val DAY = 24L * 60 * 60 * 1000
    }
}
