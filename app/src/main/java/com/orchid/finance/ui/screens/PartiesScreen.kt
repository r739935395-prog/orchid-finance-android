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
fun PartiesScreen(vm: MainViewModel, lang: String, kind: String) {
    val scope = rememberCoroutineScope()
    var customers by remember { mutableStateOf<List<Customer>>(emptyList()) }
    var suppliers by remember { mutableStateOf<List<Supplier>>(emptyList()) }
    var balances by remember { mutableStateOf<Map<Long, Double>>(emptyMap()) }
    var showForm by remember { mutableStateOf(false) }
    var statementFor by remember { mutableStateOf<Pair<Long,String>?>(null) }
    var statementRows by remember { mutableStateOf<List<com.orchid.finance.data.Repository.StatementRow>>(emptyList()) }

    fun load() = scope.launch { runCatching {
        if (kind=="customer") {
            customers = vm.r.parties.customers()
            balances = customers.associate { it.id to vm.r.partyBalance("customer", it.id) }
        } else {
            suppliers = vm.r.parties.suppliers()
            balances = suppliers.associate { it.id to vm.r.partyBalance("supplier", it.id) }
        }
    } }
    LaunchedEffect(kind) { load() }

    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Button(onClick = { showForm = true }, colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Navy900)) { Text(if (kind=="customer") t(Str.newCustomer, lang) else t(Str.newSupplier, lang)) } }
        if (kind=="customer") {
            items(customers) { c ->
                PageCard {
                    Text(if (lang=="ar") c.nameAr else c.nameEn, color = TextPrimary)
                    Text("${c.phone ?: ""} ${c.email ?: ""}", color = Muted)
                    Text("${t(Str.balance, lang)}: ${fmt(balances[c.id] ?: 0.0)}", color = GoldSoft)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { scope.launch { statementRows = vm.r.partyStatement("customer", c.id); statementFor = c.id to (if(lang=="ar") c.nameAr else c.nameEn) } }) { Text(t(Str.statement, lang)) }
                        var showPay by remember { mutableStateOf(false) }
                        OutlinedButton(onClick = { showPay = true }) { Text(if (lang=="ar") "سند قبض" else "Receive") }
                        if (showPay) {
                            var amt by remember { mutableStateOf("") }; var date by remember { mutableStateOf(com.orchid.finance.data.Dates.today()) }
                            AlertDialog(onDismissRequest={showPay=false}, confirmButton={ TextButton(onClick={ scope.launch { runCatching { vm.r.recordPayment(com.orchid.finance.data.PaymentInput(date, "in", "customer", c.id, null, amt.toDoubleOrNull()?:0.0, "YER", 1.0, "cash", null), vm.uid); load(); showPay=false }.onFailure { vm.notify(it.message?:"Error") } } }){ Text(t(Str.save, lang)) } }, dismissButton={ TextButton(onClick={showPay=false}){ Text(t(Str.cancel, lang)) } }, title={ Text(if (lang=="ar") "سند قبض" else "Receive payment") }, text={ Column(verticalArrangement = Arrangement.spacedBy(8.dp)){ Field(t(Str.date, lang), date, {date=it}); Field(t(Str.amount, lang), amt, {amt=it}, keyboardType = KeyboardType.Decimal) } })
                        }
                    }
                }
            }
            if (customers.isEmpty()) item { Text(t(Str.noData, lang), color = Muted) }
        } else {
            items(suppliers) { s ->
                PageCard {
                    Text(if (lang=="ar") s.nameAr else s.nameEn, color = TextPrimary)
                    Text("${s.phone ?: ""} ${s.email ?: ""}", color = Muted)
                    Text("${t(Str.balance, lang)}: ${fmt(balances[s.id] ?: 0.0)}", color = GoldSoft)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { scope.launch { statementRows = vm.r.partyStatement("supplier", s.id); statementFor = s.id to (if(lang=="ar") s.nameAr else s.nameEn) } }) { Text(t(Str.statement, lang)) }
                        var showPay by remember { mutableStateOf(false) }
                        OutlinedButton(onClick = { showPay = true }) { Text(if (lang=="ar") "سند دفع" else "Pay") }
                        if (showPay) {
                            var amt by remember { mutableStateOf("") }; var date by remember { mutableStateOf(com.orchid.finance.data.Dates.today()) }
                            AlertDialog(onDismissRequest={showPay=false}, confirmButton={ TextButton(onClick={ scope.launch { runCatching { vm.r.recordPayment(com.orchid.finance.data.PaymentInput(date, "out", "supplier", s.id, null, amt.toDoubleOrNull()?:0.0, "YER", 1.0, "cash", null), vm.uid); load(); showPay=false }.onFailure { vm.notify(it.message?:"Error") } } }){ Text(t(Str.save, lang)) } }, dismissButton={ TextButton(onClick={showPay=false}){ Text(t(Str.cancel, lang)) } }, title={ Text(if (lang=="ar") "سند دفع" else "Payment") }, text={ Column(verticalArrangement = Arrangement.spacedBy(8.dp)){ Field(t(Str.date, lang), date, {date=it}); Field(t(Str.amount, lang), amt, {amt=it}, keyboardType = KeyboardType.Decimal) } })
                        }
                    }
                }
            }
            if (suppliers.isEmpty()) item { Text(t(Str.noData, lang), color = Muted) }
        }
    }

    if (statementFor != null) {
        AlertDialog(onDismissRequest = { statementFor = null }, confirmButton = { TextButton(onClick={statementFor=null}){ Text("OK") } }, title = { Text("${t(Str.statement, lang)} - ${statementFor!!.second}") }, text = {
            LazyColumn(modifier = Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(statementRows) { r -> Text("${r.date} ${r.ref} ${r.kind} ${r.direction} ${fmt(r.amount)}", color = TextPrimary) }
                if (statementRows.isEmpty()) item { Text(t(Str.noData, lang), color = Muted) }
            }
        })
    }

    if (showForm) {
        var nameAr by remember { mutableStateOf("") }
        var nameEn by remember { mutableStateOf("") }
        var phone by remember { mutableStateOf("") }
        var email by remember { mutableStateOf("") }
        var address by remember { mutableStateOf("") }
        var opening by remember { mutableStateOf("0") }
        AlertDialog(onDismissRequest = { showForm = false }, confirmButton = {
            TextButton(onClick = { scope.launch { runCatching { vm.r.addParty(kind, PartyInput(nameAr, nameEn, phone.ifBlank{null}, email.ifBlank{null}, address.ifBlank{null}, opening.toDoubleOrNull()?:0.0), vm.uid); load(); showForm=false }.onFailure { vm.notify(it.message?:"Error") } } }) { Text(t(Str.save, lang)) }
        }, dismissButton = { TextButton(onClick={showForm=false}){ Text(t(Str.cancel, lang)) } }, title = { Text(if (kind=="customer") t(Str.newCustomer, lang) else t(Str.newSupplier, lang)) }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Field(t(Str.nameAr, lang), nameAr, { nameAr = it })
                Field(t(Str.nameEn, lang), nameEn, { nameEn = it })
                Field(t(Str.phone, lang), phone, { phone = it })
                Field(t(Str.email, lang), email, { email = it })
                Field(t(Str.openingBalance, lang), opening, { opening = it }, keyboardType = KeyboardType.Decimal)
            }
        })
    }
}
