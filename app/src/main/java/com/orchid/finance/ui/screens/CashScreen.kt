package com.orchid.finance.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.orchid.finance.data.*
import com.orchid.finance.ui.*
import com.orchid.finance.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun CashScreen(vm: MainViewModel, lang: String) {
    val scope = rememberCoroutineScope()
    var txs by remember { mutableStateOf<List<CashTransaction>>(emptyList()) }
    var accounts by remember { mutableStateOf<List<CashAccount>>(emptyList()) }
    var closings by remember { mutableStateOf<List<CashClosing>>(emptyList()) }
    var showForm by remember { mutableStateOf(false) }
    var showClose by remember { mutableStateOf(false) }

    fun load() = scope.launch { runCatching { accounts = vm.r.cashAccounts.all(); txs = vm.r.cash.list(null, null, null, 300); closings = vm.r.cash.closings(null, null) } }
    LaunchedEffect(Unit) { load() }

    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { showForm = true }, colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Navy900)) { Text(if (lang=="ar") "حركة نقدية" else "Cash tx") }
            OutlinedButton(onClick = { showClose = true }) { Text(t(Str.closeDay, lang)) }
        } }
        item { PageCard(title = if (lang=="ar") "حسابات النقد" else "Cash accounts") {
            accounts.forEach { a -> Text("#${a.id} ${if(lang=="ar") a.nameAr else a.nameEn}", color = TextPrimary); Spacer(Modifier.height(4.dp)) }
            if (accounts.isEmpty()) Text(t(Str.noData, lang), color = Muted)
        } }
        item { Text(t(Str.recentTx, lang), color = GoldSoft) }
        items(txs) { t ->
            PageCard {
                Text("${t.date} - ${t.direction} ${fmt(t.amount)} (acc#${t.cashAccountId})", color = TextPrimary)
                Text(t.description, color = Muted)
            }
        }
        if (closings.isNotEmpty()) {
            item { Text(t(Str.closeDay, lang), color = GoldSoft) }
            items(closings.take(20)) { c ->
                PageCard { Text("${c.date} acc#${c.accountId}", color = TextPrimary); Text("Open ${fmt(c.opening)} In ${fmt(c.receipts)} Out ${fmt(c.payments)} Close ${fmt(c.closing)}", color = Muted) }
            }
        }
        if (txs.isEmpty()) item { Text(t(Str.noData, lang), color = Muted) }
    }

    if (showForm) {
        var accId by remember { mutableStateOf(accounts.firstOrNull()?.id?.toString() ?: "") }
        var dir by remember { mutableStateOf("in") }
        var amount by remember { mutableStateOf("") }
        var desc by remember { mutableStateOf("") }
        var date by remember { mutableStateOf(Dates.today()) }
        var toAcc by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { showForm = false }, confirmButton = {
            TextButton(onClick = { scope.launch { runCatching { vm.r.cashTransaction(CashTxInput(accId.toLongOrNull()?:0, dir, amount.toDoubleOrNull()?:0.0, date, desc, null, null, null, toAcc.toLongOrNull()), vm.uid); load(); showForm=false }.onFailure { vm.notify(it.message?:"Error") } } }) { Text(t(Str.save, lang)) }
        }, dismissButton = { TextButton(onClick={showForm=false}){ Text(t(Str.cancel, lang)) } }, title = { Text(if (lang=="ar") "حركة نقدية" else "Cash transaction") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Field("Cash Account ID", accId, { accId = it }, keyboardType = KeyboardType.Number)
                Field("Direction (in/out/transfer)", dir, { dir = it })
                if (dir=="transfer") Field("To Account ID", toAcc, { toAcc = it }, keyboardType = KeyboardType.Number)
                Field(t(Str.amount, lang), amount, { amount = it }, keyboardType = KeyboardType.Decimal)
                Field(t(Str.date, lang), date, { date = it })
                Field(t(Str.description, lang), desc, { desc = it })
            }
        })
    }

    if (showClose) {
        var accId by remember { mutableStateOf(accounts.firstOrNull()?.id?.toString() ?: "") }
        var date by remember { mutableStateOf(Dates.today()) }
        var notes by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { showClose = false }, confirmButton = {
            TextButton(onClick = { scope.launch { runCatching { vm.r.cashClosing(accId.toLongOrNull()?:0, date, notes, vm.uid); load(); showClose=false }.onFailure { vm.notify(it.message?:"Error") } } }) { Text(t(Str.save, lang)) }
        }, dismissButton = { TextButton(onClick={showClose=false}){ Text(t(Str.cancel, lang)) } }, title = { Text(t(Str.closeDay, lang)) }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Field("Cash Account ID", accId, { accId = it }, keyboardType = KeyboardType.Number)
                Field(t(Str.date, lang), date, { date = it })
                Field(t(Str.notes, lang), notes, { notes = it })
            }
        })
    }
}
