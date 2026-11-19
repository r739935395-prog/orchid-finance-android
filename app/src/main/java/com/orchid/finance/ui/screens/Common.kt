package com.orchid.finance.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.orchid.finance.ui.theme.*
import java.text.DecimalFormat

fun fmt(v: Double, dp: Int = 2): String = DecimalFormat("0.${"0".repeat(dp)}").format(v)

@Composable
fun PageCard(title: String? = null, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Navy700), border = androidx.compose.foundation.BorderStroke(1.dp, Line)) {
        Column(Modifier.padding(16.dp)) {
            if (title != null) { Text(title, color = GoldSoft, fontWeight = FontWeight.Bold); Spacer(Modifier.height(12.dp)) }
            content()
        }
    }
}

@Composable
fun Kpi(label: String, value: String, modifier: Modifier = Modifier, color: Color = TextPrimary) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = Navy600), border = androidx.compose.foundation.BorderStroke(1.dp, Line)) {
        Column(Modifier.padding(12.dp)) {
            Text(label, color = Muted, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(4.dp))
            Text(value, color = color, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
fun Field(label: String, value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier, keyboardType: KeyboardType = KeyboardType.Text, singleLine: Boolean = true) {
    OutlinedTextField(value = value, onValueChange = onValueChange, label = { Text(label) }, modifier = modifier.fillMaxWidth(),
        singleLine = singleLine, keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Gold, unfocusedBorderColor = Line, focusedLabelColor = GoldSoft, cursorColor = Gold))
}

@Composable
fun <T> SimpleTable(headers: List<String>, rows: List<T>, modifier: Modifier = Modifier, row: @Composable (T) -> List<String>) {
    PageCard(modifier = modifier) {
        if (rows.isEmpty()) { Text("-", color = Muted) } else {
            Row(Modifier.fillMaxWidth().background(Navy600).padding(8.dp)) {
                headers.forEach { Text(it, color = GoldSoft, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold) }
            }
            rows.forEach { item ->
                Row(Modifier.fillMaxWidth().padding(8.dp)) {
                    row(item).forEach { cell -> Text(cell, color = TextPrimary, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall) }
                }
                HorizontalDivider(color = Line)
            }
        }
    }
}

@Composable
fun LoadingBox() { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Gold) } }

@Composable
fun TopActions(content: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, content = content)
}
