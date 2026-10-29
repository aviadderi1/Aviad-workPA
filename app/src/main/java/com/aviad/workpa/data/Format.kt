package com.aviad.workpa.data

import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

val HE: Locale = Locale("iw", "IL")

private val monthFmt = DateTimeFormatter.ofPattern("MMMM yyyy", HE)
private val dayNameFmt = DateTimeFormatter.ofPattern("EEEE", HE)
private val dateFmt = DateTimeFormatter.ofPattern("dd/MM/yyyy")
private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")

/** All-day events are stored at UTC midnight; timed events in local time. */
fun CalEvent.localDate(): LocalDate =
    Instant.ofEpochMilli(begin).atZone(if (allDay) ZoneOffset.UTC else ZoneId.systemDefault()).toLocalDate()

fun CalEvent.hoursText(): String {
    if (allDay) return "כל היום"
    val z = ZoneId.systemDefault()
    val s = Instant.ofEpochMilli(begin).atZone(z).format(timeFmt)
    val e = Instant.ofEpochMilli(end).atZone(z).format(timeFmt)
    return "$s–$e"
}

fun monthTitle(m: YearMonth): String = m.atDay(1).format(monthFmt)
fun dayName(d: LocalDate): String = d.format(dayNameFmt)
fun shortDayName(d: LocalDate): String = dayName(d).removePrefix("יום ").trim()
fun dateText(d: LocalDate): String = d.format(dateFmt)

fun money(v: Double): String {
    val nf = NumberFormat.getNumberInstance(Locale.US).apply {
        maximumFractionDigits = if (v % 1.0 == 0.0) 0 else 2
        minimumFractionDigits = 0
    }
    return "₪" + nf.format(v)
}
