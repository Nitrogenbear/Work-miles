package uk.co.mheonsitetraining.miles.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import uk.co.mheonsitetraining.miles.core.ClaimCalculator
import uk.co.mheonsitetraining.miles.core.CsvExporter
import uk.co.mheonsitetraining.miles.core.MileageSummary
import uk.co.mheonsitetraining.miles.core.TaxYear
import uk.co.mheonsitetraining.miles.core.TripCategory
import uk.co.mheonsitetraining.miles.core.TripClaim
import uk.co.mheonsitetraining.miles.data.AppSettings
import uk.co.mheonsitetraining.miles.data.MilesDatabase
import uk.co.mheonsitetraining.miles.data.Settings
import uk.co.mheonsitetraining.miles.data.TripEntity
import uk.co.mheonsitetraining.miles.tracking.Notifications

/** A date range for reports and exports. [toMillis] is exclusive. */
data class Period(val label: String, val fileLabel: String, val fromMillis: Long?, val toMillis: Long?) {
    fun contains(millis: Long) = (fromMillis == null || millis >= fromMillis) && (toMillis == null || millis < toMillis)

    companion object {
        val ALL = Period("All time", "all-time", null, null)

        fun of(taxYear: TaxYear) = Period(
            "Tax year ${taxYear.label}",
            "tax-year-" + taxYear.label.replace('/', '-'),
            taxYear.startMillis(Format.zone),
            taxYear.endMillisExclusive(Format.zone),
        )

        fun custom(from: LocalDate, to: LocalDate) = Period(
            "${Format.shortDay(from.atStartOfDay(Format.zone).toInstant().toEpochMilli())} – " +
                Format.shortDay(to.atStartOfDay(Format.zone).toInstant().toEpochMilli()),
            "$from-to-$to",
            from.atStartOfDay(Format.zone).toInstant().toEpochMilli(),
            to.plusDays(1).atStartOfDay(Format.zone).toInstant().toEpochMilli(),
        )
    }
}

/** null = every category. */
data class ReportFilter(val period: Period, val category: TripCategory?)

data class Report(val trips: List<TripEntity>, val claims: Map<Long, TripClaim>, val summary: MileageSummary)

class MilesViewModel(app: Application) : AndroidViewModel(app) {

    private val dao = MilesDatabase.get(app).trips()
    val settingsStore = AppSettings.get(app)

    val trips: StateFlow<List<TripEntity>> =
        dao.observeFinished().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val settings: StateFlow<Settings> = settingsStore.flow

    /** HMRC allowance for every business trip; the 10,000 mile split needs the full history. */
    val claims: StateFlow<Map<Long, TripClaim>> = combine(trips, settings) { list, s ->
        ClaimCalculator.claims(list.map { it.toRecord() }, s.rates, Format.zone)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun report(filter: ReportFilter, all: List<TripEntity>, claims: Map<Long, TripClaim>): Report {
        val selected = all
            .filter { filter.period.contains(it.startMillis) }
            .filter { filter.category == null || it.category == filter.category }
            .sortedBy { it.startMillis }
        return Report(selected, claims, ClaimCalculator.summarise(selected.map { it.toRecord() }, claims))
    }

    /** Tax years that have trips, plus the current one, newest first. */
    fun taxYears(all: List<TripEntity>): List<TaxYear> =
        (all.map { TaxYear.ofMillis(it.startMillis, Format.zone) } + TaxYear.of(LocalDate.now(Format.zone)))
            .distinct()
            .sortedDescending()

    fun classify(trip: TripEntity, category: TripCategory) = viewModelScope.launch {
        dao.setCategory(trip.id, category)
        Notifications.cancel(getApplication(), Notifications.tripNotificationId(trip.id))
    }

    fun save(trip: TripEntity) = viewModelScope.launch {
        if (trip.id == 0L) dao.insert(trip) else dao.update(trip)
        if (trip.category != TripCategory.UNCLASSIFIED && trip.id != 0L) {
            Notifications.cancel(getApplication(), Notifications.tripNotificationId(trip.id))
        }
    }

    fun delete(trip: TripEntity) = viewModelScope.launch {
        dao.delete(trip)
        Notifications.cancel(getApplication(), Notifications.tripNotificationId(trip.id))
    }

    suspend fun trip(id: Long): TripEntity? = dao.get(id)

    fun csv(report: Report): String = CsvExporter.export(
        trips = report.trips.map { it.toRecord() },
        claims = report.claims,
        zone = Format.zone,
        vehicle = settings.value.vehicle,
        rates = settings.value.rates,
    )

    fun fileName(filter: ReportFilter): String {
        val category = filter.category?.name?.lowercase() ?: "all-trips"
        return "mhe-mileage-${filter.period.fileLabel}-$category.csv"
    }

    /** Writes the CSV to a file the user picked. */
    suspend fun writeCsv(uri: Uri, report: Report): Boolean = withContext(Dispatchers.IO) {
        try {
            getApplication<Application>().contentResolver.openOutputStream(uri)?.use {
                // BOM so Excel reads the £ and accented characters correctly.
                it.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
                it.write(csv(report).toByteArray(Charsets.UTF_8))
            } != null
        } catch (e: Exception) {
            false
        }
    }

    /** Builds a share sheet intent (email to your accountant, save to Drive, etc). */
    suspend fun shareIntent(filter: ReportFilter, report: Report): Intent = withContext(Dispatchers.IO) {
        val app = getApplication<Application>()
        val dir = File(app.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, fileName(filter))
        file.outputStream().use {
            it.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
            it.write(csv(report).toByteArray(Charsets.UTF_8))
        }
        val uri = FileProvider.getUriForFile(app, "${app.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/csv")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, "Mileage log – ${filter.period.label}")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        Intent.createChooser(send, "Share mileage log")
    }
}
