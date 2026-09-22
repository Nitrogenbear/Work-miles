package uk.co.mheonsitetraining.miles.ui

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import uk.co.mheonsitetraining.miles.core.CsvExporter

object Format {
    val zone: ZoneId get() = ZoneId.systemDefault()
    private val day = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.UK)
    private val shortDay = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.UK)
    private val time = DateTimeFormatter.ofPattern("HH:mm", Locale.UK)

    fun day(millis: Long): String = day.format(Instant.ofEpochMilli(millis).atZone(zone))
    fun shortDay(millis: Long): String = shortDay.format(Instant.ofEpochMilli(millis).atZone(zone))
    fun time(millis: Long): String = time.format(Instant.ofEpochMilli(millis).atZone(zone))
    fun miles(miles: Double): String = CsvExporter.formatMiles(miles)
    fun pounds(pence: Long): String = "£" + CsvExporter.formatPounds(pence).let { s ->
        val (whole, frac) = s.split('.')
        String.format(Locale.UK, "%,d", whole.toLong()) + "." + frac
    }
}
