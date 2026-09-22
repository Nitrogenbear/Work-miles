package uk.co.mheonsitetraining.miles.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import uk.co.mheonsitetraining.miles.core.TripCategory

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Row(modifier.padding(top = 20.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(color = Brand.Orange, shape = RoundedCornerShape(2.dp), modifier = Modifier.padding(end = 8.dp)) {
            Text(" ", modifier = Modifier.padding(horizontal = 1.dp))
        }
        Text(text.uppercase(), style = MaterialTheme.typography.titleSmall, color = Brand.Navy)
    }
}

@Composable
fun BrandCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}

@Composable
fun CategoryBadge(category: TripCategory) {
    val (bg, fg, label) = when (category) {
        TripCategory.BUSINESS -> Triple(Brand.Navy, Color.White, "WORK")
        TripCategory.PERSONAL -> Triple(Color(0xFFE6E6E6), Brand.Grey, "PERSONAL")
        TripCategory.UNCLASSIFIED -> Triple(Brand.Orange, Color.White, "TO SORT")
    }
    Surface(color = bg, shape = RoundedCornerShape(50)) {
        Text(
            label,
            color = fg,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@Composable
fun Stat(label: String, value: String, modifier: Modifier = Modifier, accent: Color = Brand.Navy) {
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.headlineSmall, color = accent)
        Text(label, style = MaterialTheme.typography.bodySmall, color = Brand.Grey)
    }
}
