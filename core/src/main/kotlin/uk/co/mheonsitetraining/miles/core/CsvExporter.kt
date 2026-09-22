package uk.co.mheonsitetraining.miles.core

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Writes trips as CSV that opens directly in Excel, Numbers or Google Sheets. */
object CsvExporter {

    private val dateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.UK)
    private val timeFormat = DateTimeFormatter.ofPattern("HH:mm", Locale.UK)

    val HEADER = listOf(
        "Date", "Start time", "End time", "From", "To", "Miles", "Category",
        "Purpose / notes", "Tax year", "Miles at 45p", "Miles at 25p", "Claim (GBP)",
    )

    fun export(
        trips: List<TripRecord>,
        claims: Map<Long, TripClaim>,
        zone: ZoneId,
        vehicle: String = "",
        includeSummary: Boolean = true,
        rates: MileageRates = MileageRates(),
    ): String {
        val sb = StringBuilder()
        // Header row, retitled for the configured rates.
        val header = HEADER.toMutableList()
        header[9] = "Miles at ${formatRate(rates.firstRatePence)}"
        header[10] = "Miles at ${formatRate(rates.secondRatePence)}"
        sb.appendRow(header)

        for (trip in trips.sortedWith(compareBy({ it.startMillis }, { it.id }))) {
            val start = Instant.ofEpochMilli(trip.startMillis).atZone(zone)
            val end = Instant.ofEpochMilli(trip.endMillis).atZone(zone)
            val claim = claims[trip.id]
            sb.appendRow(
                listOf(
                    dateFormat.format(start),
                    timeFormat.format(start),
                    timeFormat.format(end),
                    trip.fromAddress,
                    trip.toAddress,
                    formatMiles(trip.miles),
                    trip.category.label,
                    trip.purpose,
                    TaxYear.ofMillis(trip.startMillis, zone).label,
                    claim?.let { formatMiles(it.milesAtFirstRate) } ?: "",
                    claim?.let { formatMiles(it.milesAtSecondRate) } ?: "",
                    claim?.let { formatPounds(it.claimPence) } ?: "",
                ),
            )
        }

        if (includeSummary) {
            val summary = ClaimCalculator.summarise(trips, claims)
            sb.append("\r\n")
            if (vehicle.isNotBlank()) sb.appendRow(listOf("Vehicle", vehicle))
            sb.appendRow(listOf("Business miles", formatMiles(summary.businessMiles)))
            sb.appendRow(listOf("Personal miles", formatMiles(summary.personalMiles)))
            if (summary.unclassifiedTrips > 0) {
                sb.appendRow(listOf("Unclassified miles", formatMiles(summary.unclassifiedMiles)))
            }
            sb.appendRow(listOf("Total miles", formatMiles(summary.totalMiles)))
            sb.appendRow(listOf("Mileage allowance (GBP)", formatPounds(summary.claimPence)))
        }
        return sb.toString()
    }

    fun formatMiles(miles: Double): String = String.format(Locale.UK, "%.1f", miles)

    fun formatPounds(pence: Long): String {
        val sign = if (pence < 0) "-" else ""
        val abs = kotlin.math.abs(pence)
        return "$sign${abs / 100}.${(abs % 100).toString().padStart(2, '0')}"
    }

    private fun formatRate(pence: Double): String =
        if (pence % 1.0 == 0.0) "${pence.toLong()}p" else "${pence}p"

    private fun StringBuilder.appendRow(cells: List<String>) {
        cells.joinTo(this, separator = ",") { escape(it) }
        append("\r\n")
    }

    internal fun escape(value: String): String {
        // Stop spreadsheet apps treating user-entered text as a formula.
        val safe = if (value.isNotEmpty() && value[0] in "=+-@\t\r" && value.toDoubleOrNull() == null) {
            "'$value"
        } else {
            value
        }
        return if (safe.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + safe.replace("\"", "\"\"") + "\""
        } else {
            safe
        }
    }
}
