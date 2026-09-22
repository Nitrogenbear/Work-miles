package uk.co.mheonsitetraining.miles.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * A UK tax year, running from 6 April of [startYear] to 5 April of the next year.
 */
data class TaxYear(val startYear: Int) : Comparable<TaxYear> {

    val firstDay: LocalDate get() = LocalDate.of(startYear, 4, 6)
    val lastDay: LocalDate get() = LocalDate.of(startYear + 1, 4, 5)

    /** For example "2025/26". */
    val label: String get() = "$startYear/${((startYear + 1) % 100).toString().padStart(2, '0')}"

    fun contains(date: LocalDate): Boolean = !date.isBefore(firstDay) && !date.isAfter(lastDay)

    /** Start of the tax year (inclusive) in epoch millis. */
    fun startMillis(zone: ZoneId): Long = firstDay.atStartOfDay(zone).toInstant().toEpochMilli()

    /** Start of the following tax year (exclusive end) in epoch millis. */
    fun endMillisExclusive(zone: ZoneId): Long = next().startMillis(zone)

    fun next(): TaxYear = TaxYear(startYear + 1)

    fun previous(): TaxYear = TaxYear(startYear - 1)

    override fun compareTo(other: TaxYear): Int = startYear.compareTo(other.startYear)

    override fun toString(): String = label

    companion object {
        fun of(date: LocalDate): TaxYear {
            val sixthOfApril = LocalDate.of(date.year, 4, 6)
            return if (date.isBefore(sixthOfApril)) TaxYear(date.year - 1) else TaxYear(date.year)
        }

        fun ofMillis(epochMillis: Long, zone: ZoneId): TaxYear =
            of(Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate())
    }
}
