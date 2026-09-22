package uk.co.mheonsitetraining.miles.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneOffset
import uk.co.mheonsitetraining.miles.core.TripCategory
import uk.co.mheonsitetraining.miles.core.milesToMetres
import uk.co.mheonsitetraining.miles.data.TripEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripEditDialog(
    trip: TripEntity,
    onDismiss: () -> Unit,
    onSave: (TripEntity) -> Unit,
    onDelete: (TripEntity) -> Unit,
) {
    val isNew = trip.id == 0L
    var category by remember { mutableStateOf(trip.category) }
    var purpose by remember { mutableStateOf(trip.purpose) }
    var from by remember { mutableStateOf(trip.fromAddress) }
    var to by remember { mutableStateOf(trip.toAddress) }
    var milesText by remember { mutableStateOf(if (isNew) "" else Format.miles(trip.miles)) }
    var startMillis by remember { mutableLongStateOf(trip.startMillis) }
    var pickingDate by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val miles = milesText.replace(",", ".").toDoubleOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "Add a trip" else "Trip details", color = Brand.Navy) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (trip.manual) {
                    OutlinedButton(onClick = { pickingDate = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(Format.day(startMillis), color = Brand.Navy)
                    }
                } else {
                    Text(
                        "${Format.day(trip.startMillis)}\n${Format.time(trip.startMillis)} – ${Format.time(trip.endMillis ?: trip.startMillis)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Brand.Grey,
                    )
                }

                Text("Was this trip for work?", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(TripCategory.BUSINESS to "Work", TripCategory.PERSONAL to "Personal").forEach { (value, label) ->
                        FilterChip(
                            selected = category == value,
                            onClick = { category = value },
                            label = { Text(label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = if (value == TripCategory.BUSINESS) Brand.Orange else Brand.Navy,
                                selectedLabelColor = Color.White,
                            ),
                        )
                    }
                }

                OutlinedTextField(
                    value = purpose,
                    onValueChange = { purpose = it },
                    label = { Text("Purpose (e.g. client, course)") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = milesText,
                    onValueChange = { milesText = it },
                    label = { Text("Miles") },
                    isError = miles == null || miles < 0,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = from,
                    onValueChange = { from = it },
                    label = { Text("From") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = to,
                    onValueChange = { to = it },
                    label = { Text("To") },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (!isNew) {
                    TextButton(onClick = { confirmDelete = true }) { Text("Delete trip", color = Color(0xFFB3261E)) }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = miles != null && miles >= 0,
                onClick = {
                    val metres = milesToMetres(miles ?: 0.0)
                    onSave(
                        trip.copy(
                            category = category,
                            purpose = purpose.trim(),
                            fromAddress = from.trim(),
                            toAddress = to.trim(),
                            // Keep the exact GPS figure unless the miles were changed.
                            distanceMetres = if (!isNew && milesText == Format.miles(trip.miles)) trip.distanceMetres else metres,
                            startMillis = startMillis,
                            endMillis = if (trip.manual) startMillis else trip.endMillis,
                        ),
                    )
                },
            ) { Text("Save", color = Brand.Orange) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Brand.Grey) } },
    )

    if (pickingDate) {
        val zone = Format.zone
        val local = Instant.ofEpochMilli(startMillis).atZone(zone)
        // The date picker works in UTC midnight millis.
        val state = rememberDatePickerState(
            initialSelectedDateMillis = local.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { utc ->
                        val date = Instant.ofEpochMilli(utc).atZone(ZoneOffset.UTC).toLocalDate()
                        startMillis = local.with(date).toInstant().toEpochMilli()
                    }
                    pickingDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickingDate = false }) { Text("Cancel") } },
        ) { DatePicker(state) }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this trip?") },
            text = { Text("It will be removed from your mileage log.") },
            confirmButton = { TextButton(onClick = { onDelete(trip) }) { Text("Delete", color = Color(0xFFB3261E)) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep") } },
        )
    }
}
