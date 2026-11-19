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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductsScreen(vm: MainViewModel, lang: String) {
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<ProductRow>>(emptyList()) }
    var cats by remember { mutableStateOf<List<Category>>(emptyList()) }
    var showForm by remember { mutableStateOf(false) }
    var edit by remember { mutableStateOf<ProductRow?>(null) }
    var search by remember { mutableStateOf("") }

    fun load() = scope.launch { runCatching { items = vm.r.products.list(search.ifBlank { null }, 0); cats = vm.r.categoryList() } }
    LaunchedEffect(Unit) { load() }

    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            TopActions {
                OutlinedTextField(search, { search = it }, label = { Text(t(Str.search, lang)) }, modifier = Modifier.weight(1f), singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Gold, unfocusedBorderColor = Line))
                Button(onClick = { load() }, colors = ButtonDefaults.buttonColors(containerColor = Navy600, contentColor = GoldSoft)) { Text(t(Str.search, lang)) }
                Button(onClick = { edit = null; showForm = true }, colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Navy900)) { Text(t(Str.add, lang)) }
            }
        }
        items(items) { p ->
            PageCard {
                Text(if (lang=="ar") p.nameAr else p.nameEn, color = TextPrimary, style = MaterialTheme.typography.titleMedium)
                Text("${t(Str.sku, lang)}: ${p.sku ?: "-"}  ${t(Str.quantity, lang)}: ${fmt(p.quantity)}  ${t(Str.sellingPrice, lang)}: ${fmt(p.sellingPrice)}", color = Muted)
                Text("${t(Str.cost, lang)}: ${fmt(p.avgCost)}  ${t(Str.inventoryValue, lang)}: ${fmt(p.quantity*p.avgCost)}", color = Muted)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { edit = p; showForm = true }) { Text(t(Str.edit, lang)) }
                    var showAdj by remember { mutableStateOf(false) }
                    OutlinedButton(onClick = { showAdj = true }) { Text(t(Str.adjust, lang)) }
                    var showMov by remember { mutableStateOf(false) }
                    OutlinedButton(onClick = { showMov = true }) { Text(t(Str.history, lang)) }
                    if (showAdj) {
                        var qty by remember { mutableStateOf(p.quantity.toString()) }
                        var reason by remember { mutableStateOf("") }
                        AlertDialog(onDismissRequest = { showAdj = false }, confirmButton = {
                            TextButton(onClick = { scope.launch { runCatching { vm.r.adjustProductStock(p.id, qty.toDoubleOrNull()?:p.quantity, null, reason, vm.uid); load() }.onFailure { vm.notify(it.message?:"Error") }; showAdj = false } }) { Text(t(Str.save, lang)) }
                        }, dismissButton = { TextButton(onClick={showAdj=false}){ Text(t(Str.cancel, lang)) } }, title = { Text(t(Str.adjust, lang)) }, text = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Field(t(Str.quantity, lang), qty, { qty = it }, keyboardType = KeyboardType.Decimal)
                                Field(t(Str.reason, lang), reason, { reason = it })
                            }
                        })
                    }
                    if (showMov) {
                        var moves by remember { mutableStateOf<List<com.orchid.finance.data.InventoryMovement>>(emptyList()) }
                        LaunchedEffect(Unit) { moves = vm.r.movements.forProduct(p.id, 100) }
                        AlertDialog(onDismissRequest = { showMov = false }, confirmButton = { TextButton(onClick={showMov=false}){ Text("OK") } }, title = { Text(t(Str.history, lang)) }, text = {
                            LazyColumn(modifier = Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(moves) { m -> Text("${m.ts} ${m.movementType} qty=${fmt(m.quantity)} bal=${fmt(m.balanceQty)}", color = TextPrimary) }
                                if (moves.isEmpty()) item { Text(t(Str.noData, lang), color = Muted) }
                            }
                        })
                    }
                }
            }
        }
        if (items.isEmpty()) item { Text(t(Str.noData, lang), color = Muted) }
    }

    if (showForm) {
        val p = edit
        var nameAr by remember { mutableStateOf(p?.nameAr ?: "") }
        var nameEn by remember { mutableStateOf(p?.nameEn ?: "") }
        var sku by remember { mutableStateOf(p?.sku ?: "") }
        var purchaseCost by remember { mutableStateOf((p?.purchaseCost ?: 0.0).toString()) }
        var sellingPrice by remember { mutableStateOf((p?.sellingPrice ?: 0.0).toString()) }
        var qty by remember { mutableStateOf(if (p==null) "0" else p.quantity.toString()) }
        var minStock by remember { mutableStateOf((p?.minStock ?: 0.0).toString()) }
        AlertDialog(onDismissRequest = { showForm = false }, confirmButton = {
            TextButton(onClick = {
                scope.launch {
                    runCatching {
                        val input = ProductInput(sku.ifBlank{null}, null, nameAr, nameEn, p?.categoryId, purchaseCost.toDoubleOrNull()?:0.0, sellingPrice.toDoubleOrNull()?:0.0, qty.toDoubleOrNull()?:0.0, minStock.toDoubleOrNull()?:0.0, null)
                        if (p == null) vm.r.addProduct(input, vm.uid) else vm.r.updateProduct(p.id, input, vm.uid)
                        load(); showForm = false
                    }.onFailure { vm.notify(it.message ?: "Error") }
                }
            }) { Text(t(Str.save, lang)) }
        }, dismissButton = { TextButton(onClick={showForm=false}){ Text(t(Str.cancel, lang)) } }, title = { Text(if (p==null) t(Str.newProduct, lang) else t(Str.edit, lang)) }, text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.heightIn(max = 420.dp)) {
                item { Field(t(Str.nameAr, lang), nameAr, { nameAr = it }) }
                item { Field(t(Str.nameEn, lang), nameEn, { nameEn = it }) }
                item { Field(t(Str.sku, lang), sku, { sku = it }) }
                item { Field(t(Str.purchaseCost, lang), purchaseCost, { purchaseCost = it }, keyboardType = KeyboardType.Decimal) }
                item { Field(t(Str.sellingPrice, lang), sellingPrice, { sellingPrice = it }, keyboardType = KeyboardType.Decimal) }
                item { Field(t(Str.quantity, lang), qty, { qty = it }, keyboardType = KeyboardType.Decimal) }
                item { Field(t(Str.minStock, lang), minStock, { minStock = it }, keyboardType = KeyboardType.Decimal) }
            }
        })
    }
}
