package uk.co.mheonsitetraining.miles.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.launch
import uk.co.mheonsitetraining.miles.core.CsvExporter
import uk.co.mheonsitetraining.miles.core.TaxYear
import uk.co.mheonsitetraining.miles.core.TripCategory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(viewModel: MilesViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val trips by viewModel.trips.collectAsStateWithLifecycle()
    val claims by viewModel.claims.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    val taxYears = viewModel.taxYears(trips)
    var period by remember { mutableStateOf(Period.of(taxYears.first())) }
    var category by remember { mutableStateOf<TripCategory?>(TripCategory.BUSINESS) }
    var pickingRange by remember { mutableStateOf(false) }

    val filter = ReportFilter(period, category)
    val report = viewModel.report(filter, trips, claims)
    val summary = report.summary

    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) {
            scope.launch {
                val ok = viewModel.writeCsv(uri, report)
                Toast.makeText(context, if (ok) "Mileage log saved" else "Couldn't save the file", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
        SectionTitle("Period")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            taxYears.forEach { year ->
                val option = Period.of(year)
                BrandChip(selected = period == option, label = year.label) { period = option }
            }
            BrandChip(selected = period == Period.ALL, label = "All time") { period = Period.ALL }
            val isCustom = period != Period.ALL && taxYears.none { Period.of(it) == period }
            BrandChip(selected = isCustom, label = if (isCustom) period.label else "Custom dates…") { pickingRange = true }
        }

        SectionTitle("Trips")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BrandChip(selected = category == TripCategory.BUSINESS, label = "Work") { category = TripCategory.BUSINESS }
            BrandChip(selected = category == TripCategory.PERSONAL, label = "Personal") { category = TripCategory.PERSONAL }
            BrandChip(selected = category == null, label = "All") { category = null }
        }

        SectionTitle(period.label)
        BrandCard(Modifier.fillMaxWidth()) {
            Row {
                Stat("Work miles", Format.miles(summary.businessMiles), Modifier.weight(1f))
                Stat("Mileage allowance", Format.pounds(summary.claimPence), Modifier.weight(1f), accent = Brand.Orange)
            }
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            Row {
                Stat("Personal miles", Format.miles(summary.personalMiles), Modifier.weight(1f))
                Stat("Trips", summary.tripCount.toString(), Modifier.weight(1f))
            }
            Text(
                "Allowance uses HMRC rates: ${rate(settings.rates.firstRatePence)} a mile for the first " +
                    "${"%,.0f".format(settings.rates.thresholdMiles)} work miles in a tax year, then " +
                    "${rate(settings.rates.secondRatePence)}.",
                style = MaterialTheme.typography.bodySmall,
                color = Brand.Grey,
            )
        }

        val unsorted = trips.count { it.category == TripCategory.UNCLASSIFIED && period.contains(it.startMillis) }
        if (unsorted > 0) {
            Text(
                "$unsorted trip${if (unsorted == 1) "" else "s"} in this period still need marking as work or personal.",
                color = Brand.Orange,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 12.dp),
            )
        }

        SectionTitle("Export")
        Text(
            "Download a spreadsheet (CSV) of the trips above for your Self Assessment or accountant. " +
                "It opens in Excel, Numbers and Google Sheets.",
            style = MaterialTheme.typography.bodyMedium,
            color = Brand.Grey,
        )
        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { saveLauncher.launch(viewModel.fileName(filter)) },
                enabled = report.trips.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = Brand.Orange),
                modifier = Modifier.weight(1f),
            ) { Text("Save CSV") }
            OutlinedButton(
                onClick = { scope.launch { context.startActivity(viewModel.shareIntent(filter, report)) } },
                enabled = report.trips.isNotEmpty(),
                modifier = Modifier.weight(1f),
            ) { Text("Share / email", color = Brand.Navy) }
        }

        if (report.trips.isNotEmpty()) {
            SectionTitle("Included trips")
            BrandCard(Modifier.fillMaxWidth()) {
                report.trips.asReversed().take(50).forEach { trip ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(Format.shortDay(trip.startMillis), style = MaterialTheme.typography.labelMedium, color = Brand.Grey)
                            Text(
                                trip.purpose.ifBlank { trip.toAddress.ifBlank { "Trip" } },
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text("${Format.miles(trip.miles)} mi", style = MaterialTheme.typography.titleSmall, color = Brand.Navy)
                    }
                }
                if (report.trips.size > 50) {
                    Text("…and ${report.trips.size - 50} more in the export", style = MaterialTheme.typography.bodySmall, color = Brand.Grey)
                }
            }
        }
    }

    if (pickingRange) {
        val state = rememberDateRangePickerState()
        DatePickerDialog(
            onDismissRequest = { pickingRange = false },
            confirmButton = {
                TextButton(
                    enabled = state.selectedStartDateMillis != null && state.selectedEndDateMillis != null,
                    onClick = {
                        val from = Instant.ofEpochMilli(state.selectedStartDateMillis!!).atZone(ZoneOffset.UTC).toLocalDate()
                        val to = Instant.ofEpochMilli(state.selectedEndDateMillis!!).atZone(ZoneOffset.UTC).toLocalDate()
                        period = Period.custom(from, to)
                        pickingRange = false
                    },
                ) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickingRange = false }) { Text("Cancel") } },
        ) {
            DateRangePicker(state, modifier = Modifier.weight(1f), title = {
                Text("Choose dates", modifier = Modifier.padding(start = 24.dp, top = 16.dp))
            })
        }
    }
}

private fun rate(pence: Double): String = CsvExporter.formatMiles(pence).removeSuffix(".0") + "p"

@Composable
private fun BrandChip(selected: Boolean, label: String, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = Brand.Navy,
            selectedLabelColor = Color.White,
        ),
    )
}
