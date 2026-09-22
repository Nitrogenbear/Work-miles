package uk.co.mheonsitetraining.miles.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.widget.Toast
import uk.co.mheonsitetraining.miles.core.TripCategory
import uk.co.mheonsitetraining.miles.core.metresToMiles
import uk.co.mheonsitetraining.miles.data.TripEntity
import uk.co.mheonsitetraining.miles.tracking.TripService
import uk.co.mheonsitetraining.miles.tracking.TripStatus
import uk.co.mheonsitetraining.miles.tracking.TripStatusStore

@Composable
fun TripsScreen(viewModel: MilesViewModel, onEdit: (TripEntity) -> Unit, onOpenSetup: () -> Unit) {
    val trips by viewModel.trips.collectAsStateWithLifecycle()
    val claims by viewModel.claims.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val status by TripStatusStore.status.collectAsStateWithLifecycle()
    val toSort = trips.filter { it.category == TripCategory.UNCLASSIFIED }
    val byDay = trips.groupBy { Format.day(it.startMillis) }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            StatusCard(status, carName = settings.carName, onOpenSetup = onOpenSetup)
        }

        if (toSort.isNotEmpty()) {
            item { SectionTitle("Was this for work?") }
            items(toSort, key = { "sort-${it.id}" }) { trip ->
                ClassifyCard(
                    trip = trip,
                    onWork = { viewModel.classify(trip, TripCategory.BUSINESS) },
                    onPersonal = { viewModel.classify(trip, TripCategory.PERSONAL) },
                    onEdit = { onEdit(trip) },
                )
            }
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionTitle("Trips", Modifier.weight(1f))
                TextButton(onClick = {
                    val now = System.currentTimeMillis()
                    onEdit(TripEntity(startMillis = now, endMillis = now, category = TripCategory.BUSINESS, manual = true))
                }) { Text("+ Add trip", color = Brand.Orange) }
            }
        }

        if (trips.isEmpty()) {
            item {
                Text(
                    "No trips yet. Once your car is set up, trips are recorded automatically when " +
                        "your phone connects to the car's Bluetooth and starts charging.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Brand.Grey,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        }

        byDay.forEach { (day, dayTrips) ->
            item(key = "day-$day") {
                Text(day, style = MaterialTheme.typography.labelLarge, color = Brand.Grey, modifier = Modifier.padding(top = 8.dp))
            }
            items(dayTrips, key = { it.id }) { trip ->
                TripRow(trip, claimPence = claims[trip.id]?.claimPence, onClick = { onEdit(trip) })
            }
        }
    }
}

@Composable
private fun StatusCard(status: TripStatus, carName: String?, onOpenSetup: () -> Unit) {
    val context = LocalContext.current
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Brand.Navy, contentColor = Color.White),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            when (status) {
                is TripStatus.Recording -> {
                    Text("● RECORDING", style = MaterialTheme.typography.labelMedium, color = Brand.Orange)
                    Text("${Format.miles(metresToMiles(status.metres))} miles", style = MaterialTheme.typography.displaySmall)
                    Text("Started ${Format.time(status.startMillis)}", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.padding(2.dp))
                    Button(
                        onClick = { TripService.stop(context) },
                        colors = ButtonDefaults.buttonColors(containerColor = Brand.Orange),
                    ) { Text("End trip") }
                }
                TripStatus.WaitingForCharge -> {
                    Text("CAR CONNECTED", style = MaterialTheme.typography.labelMedium, color = Brand.Orange)
                    Text("Waiting for charging", style = MaterialTheme.typography.headlineSmall)
                    Text("Plug your phone in and recording starts automatically.", style = MaterialTheme.typography.bodyMedium)
                    StartButton()
                }
                TripStatus.Idle -> {
                    Text("READY", style = MaterialTheme.typography.labelMedium, color = Brand.Orange)
                    if (carName != null) {
                        Text("Not driving", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            "Recording starts when your phone connects to “$carName” and is charging.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        StartButton()
                    } else {
                        Text("Set up your car", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            "Choose your Nissan's Bluetooth and allow permissions so trips record by themselves.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Button(
                            onClick = onOpenSetup,
                            colors = ButtonDefaults.buttonColors(containerColor = Brand.Orange),
                        ) { Text("Go to setup") }
                    }
                }
            }
        }
    }
}

@Composable
private fun StartButton() {
    val context = LocalContext.current
    OutlinedButton(
        onClick = {
            try {
                TripService.startManually(context)
            } catch (e: Exception) {
                Toast.makeText(context, "Allow location in Setup first", Toast.LENGTH_LONG).show()
            }
        },
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
    ) { Text("Start trip now") }
}

@Composable
private fun ClassifyCard(trip: TripEntity, onWork: () -> Unit, onPersonal: () -> Unit, onEdit: () -> Unit) {
    BrandCard(Modifier.fillMaxWidth().clickable(onClick = onEdit)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "${Format.shortDay(trip.startMillis)} · ${Format.time(trip.startMillis)}–${Format.time(trip.endMillis ?: trip.startMillis)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = Brand.Grey,
                )
                Route(trip)
            }
            Text("${Format.miles(trip.miles)} mi", style = MaterialTheme.typography.titleLarge, color = Brand.Navy)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onWork,
                colors = ButtonDefaults.buttonColors(containerColor = Brand.Orange),
                modifier = Modifier.weight(1f),
            ) { Text("Work") }
            OutlinedButton(onClick = onPersonal, modifier = Modifier.weight(1f)) { Text("Personal", color = Brand.Navy) }
        }
    }
}

@Composable
private fun Route(trip: TripEntity) {
    val from = trip.fromAddress.ifBlank { "Unknown start" }
    val to = trip.toAddress.ifBlank { "Unknown end" }
    Text("$from → $to", style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun TripRow(trip: TripEntity, claimPence: Long?, onClick: () -> Unit) {
    BrandCard(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${Format.time(trip.startMillis)}–${Format.time(trip.endMillis ?: trip.startMillis)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = Brand.Grey,
                    )
                    Spacer(Modifier.width(8.dp))
                    CategoryBadge(trip.category)
                }
                Route(trip)
                if (trip.purpose.isNotBlank()) {
                    Text(trip.purpose, style = MaterialTheme.typography.bodySmall, color = Brand.Grey, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${Format.miles(trip.miles)} mi", style = MaterialTheme.typography.titleMedium, color = Brand.Navy)
                if (claimPence != null) {
                    Text(Format.pounds(claimPence), style = MaterialTheme.typography.bodySmall, color = Brand.Orange)
                }
            }
        }
    }
}
