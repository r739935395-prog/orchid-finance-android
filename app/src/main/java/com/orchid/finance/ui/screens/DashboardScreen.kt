package com.orchid.finance.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.orchid.finance.data.DashboardData
import com.orchid.finance.ui.*
import com.orchid.finance.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun DashboardScreen(vm: MainViewModel, lang: String) {
    var data by remember { mutableStateOf<DashboardData?>(null) }
    val scope = rememberCoroutineScope()
    fun load() = scope.launch { runCatching { data = vm.r.dashboard(null, null) }.onFailure { /* shown globally not wired */ } }
    LaunchedEffect(Unit) { load() }
    val d = data
    if (d == null) { LoadingBox(); return }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text(t(Str.dashboard, lang), color = GoldSoft, style = MaterialTheme.typography.titleLarge)
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Kpi(t(Str.salesTotal, lang), fmt(d.salesTotal), Modifier.weight(1f), Green)
                    Kpi(t(Str.purchaseTotal, lang), fmt(d.purchaseTotal), Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Kpi(t(Str.grossProfit, lang), fmt(d.grossProfit), Modifier.weight(1f), if (d.grossProfit>=0) Green else Red)
                    Kpi(t(Str.netProfit, lang), fmt(d.netProfit), Modifier.weight(1f), if (d.netProfit>=0) Green else Red)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Kpi(t(Str.cashBalance, lang), fmt(d.cashBalance), Modifier.weight(1f), GoldSoft)
                    Kpi(t(Str.inventoryValue, lang), fmt(d.inventoryValue), Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Kpi(t(Str.receivables, lang), fmt(d.receivables), Modifier.weight(1f))
                    Kpi(t(Str.payables, lang), fmt(d.payables), Modifier.weight(1f))
                }
            }
        }
        item { Text(t(Str.lowStock, lang), color = GoldSoft, style = MaterialTheme.typography.titleMedium) }
        if (d.lowStock.isEmpty()) item { Text(t(Str.noData, lang), color = Muted) }
        items(d.lowStock) { p ->
            PageCard { Text(if (lang=="ar") p.nameAr else p.nameEn, color = TextPrimary); Text("${t(Str.quantity, lang)}: ${fmt(p.quantity)} / ${t(Str.minStock, lang)}: ${fmt(p.minStock)}", color = Red) }
        }
        item { Text(t(Str.recentTx, lang), color = GoldSoft, style = MaterialTheme.typography.titleMedium) }
        if (d.recent.isEmpty()) item { Text(t(Str.noData, lang), color = Muted) }
        items(d.recent) { tx ->
            PageCard { Text("${tx.date} ${tx.ref}", color = TextPrimary); Text("${tx.kind} - ${fmt(tx.amount)}", color = Muted) }
        }
        item { Button(onClick = { load() }, colors = ButtonDefaults.buttonColors(containerColor = Navy600, contentColor = GoldSoft)) { Text(t(Str.refresh, lang)) } }
    }
}
