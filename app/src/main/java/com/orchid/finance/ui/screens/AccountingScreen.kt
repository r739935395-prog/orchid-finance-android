package com.orchid.finance.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.orchid.finance.data.*
import com.orchid.finance.ui.*
import com.orchid.finance.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountingScreen(vm: MainViewModel, lang: String) {
    var tab by remember { mutableStateOf(0) }
    val tabs = listOf(t(Str.trialBalance, lang), t(Str.journal, lang), t(Str.chartAccounts, lang), t(Str.periods, lang), t(Str.reconciliation, lang))
    Column(Modifier.fillMaxSize()) {
        ScrollableTabRow(selectedTabIndex = tab, containerColor = Navy800, contentColor = GoldSoft, edgePadding = 8.dp) {
            tabs.forEachIndexed { i, s -> Tab(selected = tab==i, onClick = { tab=i }, text = { Text(s, maxLines = 1) }) }
        }
        when (tab) {
            0 -> TrialBalanceTab(vm, lang)
            1 -> JournalTab(vm, lang)
            2 -> AccountsTab(vm, lang)
            3 -> PeriodsTab(vm, lang)
            4 -> ReconTab(vm, lang)
        }
    }
}

@Composable fun TrialBalanceTab(vm: MainViewModel, lang: String) {
    val scope = rememberCoroutineScope()
    var tb by remember { mutableStateOf<TrialBalanceResult?>(null) }
    fun load() = scope.launch { runCatching { tb = vm.r.engine.trialBalance(null, null) } }
    LaunchedEffect(Unit) { load() }
    val r = tb ?: return LoadingBox()
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("${t(Str.debit, lang)}: ${fmt(r.debit)}  ${t(Str.credit, lang)}: ${fmt(r.credit)}  ${if (r.balanced) "OK" else "DIFF ${fmt(r.difference)}"}", color = if (r.balanced) Green else Red, fontWeight = FontWeight.Bold) }
        items(r.rows) { row ->
            PageCard { Text("${row.raw.code} ${if (lang=="ar") row.raw.nameAr else row.raw.nameEn}", color = TextPrimary); Text("D ${fmt(row.raw.debit)}  C ${fmt(row.raw.credit)}  ${t(Str.balance, lang)} ${fmt(row.normal)}", color = Muted) }
        }
    }
}

@Composable fun JournalTab(vm: MainViewModel, lang: String) {
    val scope = rememberCoroutineScope()
    var entries by remember { mutableStateOf<List<JournalEntry>>(emptyList()) }
    var show by remember { mutableStateOf(false) }
    fun load() = scope.launch { runCatching { entries = vm.r.journals.list(null, null, 200) } }
    LaunchedEffect(Unit) { load() }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Button(onClick = { show = true }, colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Navy900)) { Text(t(Str.newJournal, lang)) } }
        items(entries) { e -> PageCard { Text("${e.date} ${e.reference}", color = GoldSoft); Text(e.description, color = TextPrimary) } }
        if (entries.isEmpty()) item { Text(t(Str.noData, lang), color = Muted) }
    }
    if (show) {
        var date by remember { mutableStateOf(Dates.today()) }
        var desc by remember { mutableStateOf("") }
        var a1 by remember { mutableStateOf("1000") }; var d1 by remember { mutableStateOf("") }; var c1 by remember { mutableStateOf("0") }
        var a2 by remember { mutableStateOf("4000") }; var d2 by remember { mutableStateOf("0") }; var c2 by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest={show=false}, confirmButton={ TextButton(onClick={ scope.launch { runCatching { vm.r.manualJournal(date, desc, listOf(JournalLineInput(a1, d1.toDoubleOrNull()?:0.0, c1.toDoubleOrNull()?:0.0), JournalLineInput(a2, d2.toDoubleOrNull()?:0.0, c2.toDoubleOrNull()?:0.0)), vm.uid); load(); show=false } } }){ Text(t(Str.save, lang)) } }, dismissButton={ TextButton(onClick={show=false}){ Text(t(Str.cancel, lang)) } }, title={ Text(t(Str.newJournal, lang)) }, text={
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Field(t(Str.date, lang), date, { date=it })
                Field(t(Str.description, lang), desc, { desc=it })
                Field("${t(Str.account, lang)}1 code", a1, {a1=it}); Field("${t(Str.debit, lang)}1", d1, {d1=it}, keyboardType = KeyboardType.Decimal)
                Field("${t(Str.account, lang)}2 code", a2, {a2=it}); Field("${t(Str.credit, lang)}2", c2, {c2=it}, keyboardType = KeyboardType.Decimal)
            }
        })
    }
}

@Composable fun AccountsTab(vm: MainViewModel, lang: String) {
    val scope = rememberCoroutineScope()
    var rows by remember { mutableStateOf<List<Account>>(emptyList()) }
    LaunchedEffect(Unit) { scope.launch { runCatching { rows = vm.r.accounts.all() } } }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items(rows) { a -> PageCard { Text("${a.code} ${if (lang=="ar") a.nameAr else a.nameEn}", color = TextPrimary); Text("${a.groupName} - ${a.type}", color = Muted) } }
    }
}

@Composable fun PeriodsTab(vm: MainViewModel, lang: String) {
    val scope = rememberCoroutineScope()
    var rows by remember { mutableStateOf<List<AccountingPeriod>>(emptyList()) }
    var show by remember { mutableStateOf(false) }
    fun load() = scope.launch { runCatching { rows = vm.r.extra.periods() } }
    LaunchedEffect(Unit) { load() }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Button(onClick={show=true}, colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Navy900)) { Text(t(Str.lock, lang)) } }
        items(rows) { p ->
            PageCard {
                Text("${p.dateFrom} .. ${p.dateTo} ${if (p.locked==1) t(Str.locked, lang) else ""}", color = TextPrimary)
                if (p.locked==1) OutlinedButton(onClick={ scope.launch { runCatching { vm.r.deletePeriod(p.id, vm.uid); load() } } }) { Text(t(Str.unlock, lang)) }
            }
        }
    }
    if (show) {
        var from by remember { mutableStateOf(Dates.today().substring(0,8)+"01") }
        var to by remember { mutableStateOf(Dates.today()) }
        var notes by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest={show=false}, confirmButton={ TextButton(onClick={ scope.launch { runCatching { vm.r.setPeriod(from,to,true,notes,vm.uid); load(); show=false } } }){ Text(t(Str.save, lang)) } }, dismissButton={ TextButton(onClick={show=false}){ Text(t(Str.cancel, lang)) } }, title={ Text(t(Str.periods, lang)) }, text={
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Field(t(Str.from, lang), from, {from=it}); Field(t(Str.to, lang), to, {to=it}); Field(t(Str.notes, lang), notes, {notes=it})
            }
        })
    }
}

@Composable fun ReconTab(vm: MainViewModel, lang: String) {
    val scope = rememberCoroutineScope()
    var rows by remember { mutableStateOf<List<Reconciliation>>(emptyList()) }
    var show by remember { mutableStateOf(false) }
    fun load() = scope.launch { runCatching { rows = vm.r.extra.reconciliations() } }
    LaunchedEffect(Unit) { load() }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Button(onClick={show=true}, colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Navy900)) { Text(t(Str.reconciliation, lang)) } }
        items(rows) { r -> PageCard { Text("${r.date} acc#${r.accountId}", color = TextPrimary); Text("Ledger ${fmt(r.ledgerBalance)} Stmt ${fmt(r.statementBalance)} Diff ${fmt(r.difference)}", color = if (kotlin.math.abs(r.difference)<0.01) Green else Red) } }
    }
    if (show) {
        var acc by remember { mutableStateOf("1000") }
        var date by remember { mutableStateOf(Dates.today()) }
        var stmt by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest={show=false}, confirmButton={ TextButton(onClick={ scope.launch { runCatching { val a = vm.r.accounts.byCode(acc) ?: vm.r.accounts.byId(acc.toLongOrNull()?:0); if (a!=null) vm.r.reconcile(a.id,date,stmt.toDoubleOrNull()?:0.0,"",vm.uid); load(); show=false } } }){ Text(t(Str.save, lang)) } }, dismissButton={ TextButton(onClick={show=false}){ Text(t(Str.cancel, lang)) } }, title={ Text(t(Str.reconciliation, lang)) }, text={
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Field(t(Str.account, lang)+" code/id", acc, {acc=it}); Field(t(Str.date, lang), date, {date=it}); Field("Statement balance", stmt, {stmt=it}, keyboardType = KeyboardType.Decimal) }
        })
    }
}
