package uk.co.mheonsitetraining.miles.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import uk.co.mheonsitetraining.miles.R
import uk.co.mheonsitetraining.miles.data.TripEntity

private enum class Tab(val label: String, val icon: ImageVector) {
    Trips("Trips", Icons.AutoMirrored.Filled.List),
    Reports("Tax report", Icons.Filled.DateRange),
    Setup("Setup", Icons.Filled.Settings),
}

@Composable
fun MilesApp(viewModel: MilesViewModel, openTripId: Long?, onTripOpened: () -> Unit) {
    var tab by rememberSaveable { mutableStateOf(Tab.Trips) }
    var editing by remember { mutableStateOf<TripEntity?>(null) }

    LaunchedEffect(openTripId) {
        if (openTripId != null) {
            viewModel.trip(openTripId)?.let {
                tab = Tab.Trips
                editing = it
            }
            onTripOpened()
        }
    }

    Scaffold(
        topBar = { LogoBar() },
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                Tab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { tab = item },
                        icon = { Icon(item.icon, contentDescription = null) },
                        label = { Text(item.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Brand.Navy,
                            selectedTextColor = Brand.Navy,
                            indicatorColor = Brand.OrangeTint,
                            unselectedIconColor = Brand.Grey,
                            unselectedTextColor = Brand.Grey,
                        ),
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (tab) {
                Tab.Trips -> TripsScreen(viewModel, onEdit = { editing = it }, onOpenSetup = { tab = Tab.Setup })
                Tab.Reports -> ReportsScreen(viewModel)
                Tab.Setup -> SetupScreen(viewModel)
            }
        }
    }

    editing?.let { trip ->
        TripEditDialog(
            trip = trip,
            onDismiss = { editing = null },
            onSave = {
                viewModel.save(it)
                editing = null
            },
            onDelete = {
                viewModel.delete(it)
                editing = null
            },
        )
    }
}

@Composable
private fun LogoBar() {
    Surface(color = Color.White, shadowElevation = 2.dp) {
        Column(Modifier.fillMaxWidth().statusBarsPadding()) {
            Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), contentAlignment = Alignment.CenterStart) {
                Image(
                    painter = painterResource(R.drawable.mhe_logo),
                    contentDescription = "MHE Onsite Training",
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.CenterStart,
                    modifier = Modifier.height(44.dp),
                )
            }
            HorizontalDivider(thickness = 3.dp, color = Brand.Orange)
        }
    }
}
