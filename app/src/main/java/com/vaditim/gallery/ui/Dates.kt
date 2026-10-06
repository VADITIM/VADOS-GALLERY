package com.vaditim.gallery.ui

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.Locale

private val DAY_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yy", Locale.ENGLISH)

fun dayOf(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()

// ISO weeks, Monday first, the way calendar weeks are counted in Europe.
fun calendarWeekOf(day: LocalDate): Int = day.get(WeekFields.ISO.weekOfWeekBasedYear())

fun calendarWeekLabel(day: LocalDate): String = "CW${calendarWeekOf(day)}"

// The day's stamp on a grid tile: 05/10/26 - CW41.
fun dayStamp(day: LocalDate): String = "${DAY_FORMAT.format(day)} - ${calendarWeekLabel(day)}"
