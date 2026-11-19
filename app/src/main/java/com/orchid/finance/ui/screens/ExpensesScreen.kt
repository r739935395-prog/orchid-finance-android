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
fun ExpensesScreen(vm: MainViewModel, lang: String) {
    val scope = rememberCoroutineScope()
    var rows by remember { mutableStateOf<List<Expense>>(emptyList()) }
    var showForm by remember { mutableStateOf(false) }
    fun load() = scope.launch { runCatching { rows = vm.r.expenses.list(null, null, 300) } }
    LaunchedEffect(Unit) { load() }

    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Button(onClick = { showForm = true }, colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Navy900)) { Text(t(Str.newExpense, lang)) } }
        items(rows) { e ->
            PageCard {
                Text("${e.date} - ${fmt(e.amount)}", color = TextPrimary)
                Text(e.description, color = Muted)
                Spacer(Modifier.height(6.dp))
                OutlinedButton(onClick = { scope.launch { runCatching { vm.r.deleteExpense(e.id, vm.uid); load() } } }) { Text(t(Str.delete, lang)) }
            }
        }
        if (rows.isEmpty()) item { Text(t(Str.noData, lang), color = Muted) }
    }

    if (showForm) {
        var date by remember { mutableStateOf(Dates.today()) }
        var desc by remember { mutableStateOf("") }
        var amount by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { showForm = false }, confirmButton = {
            TextButton(onClick = { scope.launch { runCatching { vm.r.addExpense(ExpenseInput(date, null, null, amount.toDoubleOrNull()?:0.0, "YER", 1.0, desc, null), vm.uid); load(); showForm=false } } }) { Text(t(Str.save, lang)) }
        }, dismissButton = { TextButton(onClick={showForm=false}){ Text(t(Str.cancel, lang)) } }, title = { Text(t(Str.newExpense, lang)) }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Field(t(Str.date, lang), date, { date = it })
                Field(t(Str.description, lang), desc, { desc = it })
                Field(t(Str.amount, lang), amount, { amount = it }, keyboardType = KeyboardType.Decimal)
            }
        })
    }
}
