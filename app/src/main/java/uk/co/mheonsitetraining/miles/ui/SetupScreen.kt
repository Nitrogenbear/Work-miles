package uk.co.mheonsitetraining.miles.ui

import android.Manifest
import android.app.Activity
import android.content.IntentFilter
import android.os.BatteryManager
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.text.font.FontFamily
import uk.co.mheonsitetraining.miles.tracking.CarConnectionReceiver
import uk.co.mheonsitetraining.miles.tracking.CarPresence
import uk.co.mheonsitetraining.miles.tracking.EventLog
import uk.co.mheonsitetraining.miles.tracking.TripStatus
import uk.co.mheonsitetraining.miles.tracking.TripStatusStore
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uk.co.mheonsitetraining.miles.R
import uk.co.mheonsitetraining.miles.core.CsvExporter

private data class PairedDevice(val name: String, val address: String)

private fun granted(context: Context, permission: String) =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

@Composable
fun SetupScreen(viewModel: MilesViewModel) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val store = viewModel.settingsStore

    // Re-check permissions whenever the user comes back from the system settings.
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh++ }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refresh++ }

    var choosingCar by remember { mutableStateOf(false) }
    val linkLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        EventLog.log(context, if (result.resultCode == Activity.RESULT_OK) "Car link approved" else "Car link cancelled")
        refresh++
    }
    val carLinked = remember(refresh, settings.carAddress) { CarPresence.isLinked(context, settings.carAddress) }
    val logLines by EventLog.lines.collectAsStateWithLifecycle()
    val recorderStatus by TripStatusStore.status.collectAsStateWithLifecycle()

    // Live check of the two triggers, so you can test in the car.
    var carNow by remember { mutableStateOf<Boolean?>(null) }
    val chargingNow = remember(refresh, recorderStatus) { isCharging(context) }
    LaunchedEffect(refresh, settings.carAddress) {
        carNow = if (settings.carAddress == null) {
            null
        } else {
            CarPresence.isCarConnected(context) { CarConnectionReceiver.isCar(it, store) }
        }
    }

    val fineLocation = remember(refresh) { granted(context, Manifest.permission.ACCESS_FINE_LOCATION) }
    val backgroundLocation = remember(refresh) { granted(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) }
    val bluetooth = remember(refresh) {
        Build.VERSION.SDK_INT < 31 || granted(context, Manifest.permission.BLUETOOTH_CONNECT)
    }
    val notifications = remember(refresh) {
        Build.VERSION.SDK_INT < 33 || granted(context, Manifest.permission.POST_NOTIFICATIONS)
    }
    val batteryUnrestricted = remember(refresh) {
        context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)
    }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
        SectionTitle("Your car")
        BrandCard(Modifier.fillMaxWidth()) {
            if (settings.carName != null) {
                Text(settings.carName ?: "", style = MaterialTheme.typography.titleLarge, color = Brand.Navy)
                Text(settings.carAddress ?: "", style = MaterialTheme.typography.bodySmall, color = Brand.Grey)
            } else {
                Text("No car chosen yet", style = MaterialTheme.typography.titleMedium, color = Brand.Navy)
            }
            Text(
                "Pick the Nissan's Bluetooth from your paired devices. Nissan head units usually show up " +
                    "as “MY-CAR”, “NissanConnect” or the car's model name.",
                style = MaterialTheme.typography.bodyMedium,
                color = Brand.Grey,
            )
            Button(
                onClick = {
                    if (bluetooth) {
                        choosingCar = true
                    } else if (Build.VERSION.SDK_INT >= 31) {
                        permissionLauncher.launch(arrayOf(Manifest.permission.BLUETOOTH_CONNECT))
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Brand.Orange),
            ) { Text(if (settings.carName == null) "Choose car Bluetooth" else "Change car") }
        }

        if (settings.carAddress != null) {
            SectionTitle("Right now")
            BrandCard(Modifier.fillMaxWidth()) {
                CheckRow("Connected to ${settings.carName ?: "car"}", carNow)
                CheckRow("Phone charging", chargingNow)
                CheckRow(
                    "Recording",
                    recorderStatus is TripStatus.Recording,
                    detail = if (recorderStatus == TripStatus.WaitingForCharge) "Waiting for charging" else null,
                )
                TextButton(onClick = { refresh++ }) { Text("Check again", color = Brand.Orange) }
            }
        }

        SectionTitle("Automatic recording")
        BrandCard(Modifier.fillMaxWidth()) {
            SwitchRow(
                title = "Record trips automatically",
                subtitle = "Start when the phone connects to your car, finish when it disconnects.",
                checked = settings.autoTracking,
                onChange = { on -> store.update { it.copy(autoTracking = on) } },
            )
            HorizontalDivider()
            SwitchRow(
                title = "Only when charging",
                subtitle = "Wait until the phone is on charge in the car before recording.",
                checked = settings.requireCharging,
                onChange = { on -> store.update { it.copy(requireCharging = on) } },
            )
        }

        SectionTitle("Permissions")
        BrandCard(Modifier.fillMaxWidth()) {
            Text(
                "All of these are needed for trips to record by themselves with the app closed.",
                style = MaterialTheme.typography.bodyMedium,
                color = Brand.Grey,
            )
            if (Build.VERSION.SDK_INT >= 31) {
                PermissionRow("Nearby devices", "Detects your car's Bluetooth", bluetooth) {
                    permissionLauncher.launch(arrayOf(Manifest.permission.BLUETOOTH_CONNECT))
                }
            }
            if (CarPresence.canLink(context)) {
                PermissionRow(
                    "Link car to app",
                    if (settings.carAddress == null) "Choose your car first" else "Lets Android start recording when the car connects",
                    carLinked,
                    enabled = settings.carAddress != null,
                ) {
                    val address = settings.carAddress ?: return@PermissionRow
                    CarPresence.link(
                        context,
                        address,
                        onShowDialog = { sender -> linkLauncher.launch(IntentSenderRequest.Builder(sender).build()) },
                        onError = { message ->
                            EventLog.log(context, "Car link failed: $message")
                            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                        },
                    )
                }
            }
            PermissionRow("Location", "Measures the miles you drive", fineLocation) {
                permissionLauncher.launch(
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                )
            }
            PermissionRow(
                "Location – Allow all the time",
                if (fineLocation) "Lets trips record with the app closed" else "Allow Location first",
                backgroundLocation,
                enabled = fineLocation,
            ) {
                permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION))
            }
            if (Build.VERSION.SDK_INT >= 33) {
                PermissionRow("Notifications", "Asks “work or personal?” after each trip", notifications) {
                    permissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
                }
            }
            PermissionRow("Battery – Unrestricted", "Stops Android blocking the app in the background", batteryUnrestricted) {
                requestBatteryExemption(context)
            }
        }

        SectionTitle("Tax details")
        BrandCard(Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = settings.vehicle,
                onValueChange = { v -> store.update { it.copy(vehicle = v) } },
                label = { Text("Vehicle (e.g. Nissan Qashqai AB12 CDE)") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            NumberField("Rate for first ${"%,.0f".format(settings.rates.thresholdMiles)} miles (pence)", settings.rates.firstRatePence) { v ->
                store.update { it.copy(rates = it.rates.copy(firstRatePence = v)) }
            }
            NumberField("Rate after that (pence)", settings.rates.secondRatePence) { v ->
                store.update { it.copy(rates = it.rates.copy(secondRatePence = v)) }
            }
            NumberField("Ignore trips shorter than (miles)", settings.minTripMiles) { v ->
                store.update { it.copy(minTripMiles = v) }
            }
            Text(
                "Defaults are HMRC's approved mileage rates for cars and vans: 45p a mile for the first 10,000 " +
                    "business miles in a tax year, then 25p.",
                style = MaterialTheme.typography.bodySmall,
                color = Brand.Grey,
            )
        }

        SectionTitle("Activity log")
        BrandCard(Modifier.fillMaxWidth()) {
            Text(
                "What the app noticed on your recent drives. If a trip doesn't record, tap Share and send this to whoever supports the app.",
                style = MaterialTheme.typography.bodySmall,
                color = Brand.Grey,
            )
            if (logLines.isEmpty()) {
                Text("Nothing yet.", style = MaterialTheme.typography.bodySmall)
            }
            logLines.take(40).forEach { line ->
                Text(line, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = {
                    val report = diagnosticReport(
                        context, settings.carName, settings.carAddress,
                        mapOf(
                            "Nearby devices" to bluetooth,
                            "Location" to fineLocation,
                            "Location all the time" to backgroundLocation,
                            "Notifications" to notifications,
                            "Battery unrestricted" to batteryUnrestricted,
                            "Car linked" to carLinked,
                            "Auto recording" to settings.autoTracking,
                            "Only when charging" to settings.requireCharging,
                        ),
                    )
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                        .putExtra(Intent.EXTRA_SUBJECT, "MHE Miles activity log")
                        .putExtra(Intent.EXTRA_TEXT, report)
                    context.startActivity(Intent.createChooser(send, "Share activity log"))
                }) { Text("Share", color = Brand.Orange) }
                TextButton(onClick = { EventLog.clear(context) }) { Text("Clear", color = Brand.Grey) }
            }
        }

        Column(
            Modifier.fillMaxWidth().padding(top = 28.dp, bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(painterResource(R.drawable.mhe_logo), contentDescription = null, modifier = Modifier.height(64.dp))
            Text("MHE Miles · business mileage log", style = MaterialTheme.typography.bodySmall, color = Brand.Grey)
        }
    }

    if (choosingCar) {
        CarPickerDialog(
            onDismiss = { choosingCar = false },
            onPick = { device ->
                store.update { it.copy(carAddress = device.address, carName = device.name) }
                choosingCar = false
            },
        )
    }
}

@Composable
private fun CheckRow(label: String, ok: Boolean?, detail: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        val (text, colour) = when {
            detail != null -> detail to Brand.Orange
            ok == null -> "…" to Brand.Grey
            ok -> "✓ Yes" to Brand.Success
            else -> "✗ No" to Brand.Grey
        }
        Text(text, color = colour, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
    }
}

private fun isCharging(context: Context): Boolean {
    val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return false
    return battery.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
}

private fun diagnosticReport(context: Context, carName: String?, carAddress: String?, checks: Map<String, Boolean>): String {
    val version = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    } catch (e: Exception) {
        "?"
    }
    return buildString {
        appendLine("MHE Miles $version on ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLine("Car: ${carName ?: "not chosen"} ${carAddress ?: ""}")
        checks.forEach { (name, ok) -> appendLine("$name: ${if (ok) "yes" else "NO"}") }
        appendLine()
        append(EventLog.text())
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = Brand.Navy)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Brand.Grey)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = Brand.Orange),
        )
    }
}

@Composable
private fun PermissionRow(title: String, subtitle: String, ok: Boolean, enabled: Boolean = true, onGrant: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = Brand.Navy)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Brand.Grey)
        }
        if (ok) {
            Text("✓ Done", color = Brand.Success, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
        } else {
            TextButton(onClick = onGrant, enabled = enabled) { Text("Allow", color = if (enabled) Brand.Orange else Brand.Grey) }
        }
    }
}

@Composable
private fun NumberField(label: String, value: Double, onValue: (Double) -> Unit) {
    var text by remember(value) { mutableStateOf(CsvExporter.formatMiles(value).removeSuffix(".0")) }
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            it.replace(",", ".").toDoubleOrNull()?.takeIf { v -> v >= 0 }?.let(onValue)
        },
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@SuppressLint("MissingPermission", "BatteryLife")
private fun requestBatteryExemption(context: Context) {
    val intent = Intent(AndroidSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        context.startActivity(Intent(AndroidSettings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    }
}

@SuppressLint("MissingPermission")
private fun pairedDevices(context: Context): List<PairedDevice> = try {
    context.getSystemService(BluetoothManager::class.java)?.adapter?.bondedDevices.orEmpty()
        .map { PairedDevice(it.name ?: it.address, it.address) }
        .sortedWith(compareByDescending<PairedDevice> { looksLikeCar(it.name) }.thenBy { it.name.lowercase() })
} catch (e: SecurityException) {
    emptyList()
}

private fun looksLikeCar(name: String): Boolean {
    val n = name.lowercase()
    return listOf("nissan", "my-car", "my car", "carwings", "qashqai", "juke", "leaf", "x-trail", "navara", "micra", "ariya")
        .any { it in n }
}

@Composable
private fun CarPickerDialog(onDismiss: () -> Unit, onPick: (PairedDevice) -> Unit) {
    val context = LocalContext.current
    val devices = remember { pairedDevices(context) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose your car", color = Brand.Navy) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (devices.isEmpty()) {
                    Text(
                        "No paired Bluetooth devices found. Pair your phone with the Nissan first " +
                            "(Phone settings → Bluetooth), then come back.",
                    )
                }
                devices.forEach { device ->
                    Column(
                        Modifier.fillMaxWidth().clickable { onPick(device) }.padding(vertical = 10.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(device.name, style = MaterialTheme.typography.titleSmall, color = Brand.Navy, modifier = Modifier.weight(1f))
                            if (looksLikeCar(device.name)) {
                                Surface(color = Brand.Orange, shape = RoundedCornerShape(50)) {
                                    Text(
                                        "LOOKS LIKE YOUR CAR",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    )
                                }
                            }
                        }
                        Text(device.address, style = MaterialTheme.typography.bodySmall, color = Brand.Grey)
                    }
                    HorizontalDivider()
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
