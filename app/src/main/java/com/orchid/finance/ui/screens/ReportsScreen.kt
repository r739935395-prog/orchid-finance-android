package com.orchid.finance.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.orchid.finance.data.*
import com.orchid.finance.data.Dates
import com.orchid.finance.ui.*
import com.orchid.finance.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(vm: MainViewModel, lang: String) {
    var tab by remember { mutableStateOf(0) }
    val tabs = listOf(t(Str.incomeStatement, lang), t(Str.balanceSheet, lang), t(Str.cashFlow, lang), t(Str.ledger, lang))
    Column(Modifier.fillMaxSize()) {
        ScrollableTabRow(selectedTabIndex = tab, containerColor = Navy800, contentColor = GoldSoft, edgePadding = 8.dp) {
            tabs.forEachIndexed { i, s -> Tab(selected = tab==i, onClick = { tab=i }, text = { Text(s, maxLines=1) }) }
        }
        when(tab){
            0 -> PLTab(vm, lang)
            1 -> BSTab(vm, lang)
            2 -> CFTab(vm, lang)
            3 -> LedgerTab(vm, lang)
        }
    }
}

@Composable fun PLTab(vm: MainViewModel, lang: String) {
    var r by remember { mutableStateOf<IncomeStatementResult?>(null) }
    LaunchedEffect(Unit) { runCatching { r = vm.r.engine.incomeStatement(null, null) } }
    val x = r ?: return LoadingBox()
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { PageCard { listOf(
            t(Str.salesTotal, lang) to x.revenue, "COGS" to x.cogs, t(Str.grossProfit, lang) to x.grossProfit,
            t(Str.expenses, lang) to x.operatingExpenses, t(Str.netProfit, lang) to x.netProfit
        ).forEach { (k,v) -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween){ Text(k,color=TextPrimary); Text(fmt(v), color=if(v>=0) Green else Red, fontWeight = FontWeight.Bold) }; Spacer(Modifier.height(6.dp)) } } }
    }
}

@Composable fun BSTab(vm: MainViewModel, lang: String) {
    var r by remember { mutableStateOf<BalanceSheetResult?>(null) }
    LaunchedEffect(Unit) { runCatching { r = vm.r.engine.balanceSheet(Dates.today()) } }
    val x = r ?: return LoadingBox()
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { PageCard { listOf("Assets" to x.assets, "Liabilities" to x.liabilities, "Equity" to x.equityAccounts, "Current Profit" to x.currentProfit, "Retained" to x.retainedEarnings).forEach { (k,v)-> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween){ Text(k,color=TextPrimary); Text(fmt(v), color=GoldSoft) }; Spacer(Modifier.height(6.dp)) }; Text(if(x.balanced) "Balanced" else "Diff ${fmt(x.difference)}", color=if(x.balanced) Green else Red) } }
    }
}

@Composable fun CFTab(vm: MainViewModel, lang: String) {
    var r by remember { mutableStateOf<CashFlowResult?>(null) }
    LaunchedEffect(Unit) { runCatching { r = vm.r.engine.cashFlow(null, null) } }
    val x = r ?: return LoadingBox()
    PageCard(title = t(Str.cashFlow, lang), modifier = Modifier.padding(16.dp)) {
        listOf("Opening" to x.opening, "Net" to x.net, "Closing" to x.closing).forEach { (k,v)-> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween){ Text(k,color=TextPrimary); Text(fmt(v), color=GoldSoft) }; Spacer(Modifier.height(6.dp)) }
    }
}

@Composable fun LedgerTab(vm: MainViewModel, lang: String) {
    val scope = rememberCoroutineScope()
    var accounts by remember { mutableStateOf<List<Account>>(emptyList()) }
    var accId by remember { mutableStateOf<Long?>(null) }
    var res by remember { mutableStateOf<LedgerResult?>(null) }
    LaunchedEffect(Unit) { runCatching { accounts = vm.r.accounts.all(); accId = accounts.firstOrNull()?.id } }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (accounts.isNotEmpty()) {
            var sel by remember { mutableStateOf(accounts.first().id.toString()) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(sel, { sel = it }, label={Text(t(Str.account, lang)+" id")}, modifier=Modifier.weight(1f), singleLine=true, colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Gold, unfocusedBorderColor = Line))
                Button(onClick={ scope.launch { runCatching { res = vm.r.engine.generalLedger(sel.toLongOrNull()?:accounts.first().id, null, null) } } }, colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Navy900)) { Text(t(Str.view, lang)) }
            }
        }
        val x = res
        if (x != null) LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            item { Text("${x.account.code} ${if(lang=="ar") x.account.nameAr else x.account.nameEn}  Opening ${fmt(x.opening)} Closing ${fmt(x.closing)}", color=GoldSoft) }
            items(x.rows) { r -> PageCard { Text("${r.date} ${r.reference}", color=TextPrimary); Text(r.description, color=Muted); Text("D ${fmt(r.debit)} C ${fmt(r.credit)} Bal ${fmt(r.balance)}", color=TextPrimary) } }
        }
    }
}
