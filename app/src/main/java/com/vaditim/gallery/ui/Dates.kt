package com.vaditim.gallery.ui

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.Locale

private val DAY_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yy", Locale.ENGLISH)
private val SHORT_DAY_FORMAT = DateTimeFormatter.ofPattern("dd/MM", Locale.ENGLISH)

fun dayOf(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()

// ISO weeks, Monday first, the way calendar weeks are counted in Europe.
fun calendarWeekOf(day: LocalDate): Int = day.get(WeekFields.ISO.weekOfWeekBasedYear())

fun calendarWeekLabel(day: LocalDate): String = "CW${calendarWeekOf(day)}"

// The day's stamp on a grid tile: 05/10/26 - CW41.
fun dayStamp(day: LocalDate): String = "${DAY_FORMAT.format(day)} - ${calendarWeekLabel(day)}"

// The same with the year left out, for tiles too narrow to hold it.
fun shortDayStamp(day: LocalDate): String = "${SHORT_DAY_FORMAT.format(day)} - ${calendarWeekLabel(day)}"

// A week's header inside a month: CW41 · 05/10 – 11/10, the span of days that have photos.
fun weekStamp(first: LocalDate, last: LocalDate): String =
    if (first == last) "${calendarWeekLabel(first)} · ${SHORT_DAY_FORMAT.format(first)}" else "${calendarWeekLabel(first)} · ${SHORT_DAY_FORMAT.format(first)} – ${SHORT_DAY_FORMAT.format(last)}"
