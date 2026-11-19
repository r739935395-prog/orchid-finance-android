package com.orchid.finance.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.orchid.finance.data.*
import com.orchid.finance.ui.*
import com.orchid.finance.ui.theme.*
import kotlinx.coroutines.launch

data class SaleLineDraft(var productId: Long = 0, var qty: String = "1", var price: String = "0", var discount: String = "0")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SalesScreen(vm: MainViewModel, lang: String) {
    val scope = rememberCoroutineScope()
    var list by remember { mutableStateOf<List<SalesInvoice>>(emptyList()) }
    var showForm by remember { mutableStateOf(false) }
    var products by remember { mutableStateOf<List<Product>>(emptyList()) }
    var customers by remember { mutableStateOf<List<Customer>>(emptyList()) }

    fun load() = scope.launch { runCatching { list = vm.r.sales.list(null, null, null, 300); products = vm.r.products.allActive(); customers = vm.r.parties.customers() } }
    LaunchedEffect(Unit) { load() }

    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Button(onClick = { showForm = true }, colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Navy900)) { Text(t(Str.newSale, lang)) } }
        items(list) { inv ->
            PageCard {
                Text("${inv.invoiceNo} - ${inv.date}", color = TextPrimary)
                Text("${t(Str.total, lang)}: ${fmt(inv.total)}  ${t(Str.paid, lang)}: ${fmt(inv.paid)}  ${t(Str.status, lang)}: ${inv.status}", color = Muted)
                if (inv.status == "confirmed") {
                    Spacer(Modifier.height(6.dp))
                    OutlinedButton(onClick = { scope.launch { runCatching { vm.r.cancelSale(inv.id, "Cancelled", vm.uid); load() } } }) { Text(t(Str.cancelInv, lang)) }
                }
            }
        }
        if (list.isEmpty()) item { Text(t(Str.noData, lang), color = Muted) }
    }

    if (showForm) {
        var date by remember { mutableStateOf(Dates.today()) }
        var custId by remember { mutableStateOf<Long?>(null) }
        var method by remember { mutableStateOf("cash") }
        var discount by remember { mutableStateOf("0") }
        var tax by remember { mutableStateOf("0") }
        var paid by remember { mutableStateOf("") }
        var lines by remember { mutableStateOf(listOf(SaleLineDraft())) }
        AlertDialog(onDismissRequest = { showForm = false }, confirmButton = {
            TextButton(onClick = {
                scope.launch {
                    runCatching {
                        val items = lines.mapNotNull { l -> val pid = if (l.productId>0) l.productId else products.firstOrNull()?.id ?: return@mapNotNull null; SaleItemInput(pid, l.qty.toDoubleOrNull()?:0.0, l.price.toDoubleOrNull()?:0.0, l.discount.toDoubleOrNull()?:0.0) }
                        val total = items.sumOf { it.quantity*it.unitPrice - it.discount } - (discount.toDoubleOrNull()?:0.0) + (tax.toDoubleOrNull()?:0.0)
                        val paidVal = paid.toDoubleOrNull() ?: if (method=="cash") total else 0.0
                        vm.r.createSale(SaleInput(date, custId, if (paidVal>=total) "cash" else if (paidVal>0) "partial" else "credit", "YER", 1.0, discount.toDoubleOrNull()?:0.0, tax.toDoubleOrNull()?:0.0, paidVal, items, null), vm.uid)
                        load(); showForm = false
                    }
                }
            }) { Text(t(Str.save, lang)) }
        }, dismissButton = { TextButton(onClick={showForm=false}){ Text(t(Str.cancel, lang)) } }, title = { Text(t(Str.newSale, lang)) }, text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.heightIn(max = 460.dp)) {
                item { Field(t(Str.date, lang), date, { date = it }) }
                item { Field(t(Str.discount, lang), discount, { discount = it }, keyboardType = KeyboardType.Decimal) }
                item { Field(t(Str.tax, lang), tax, { tax = it }, keyboardType = KeyboardType.Decimal) }
                item { Field(t(Str.paid, lang), paid, { paid = it }, keyboardType = KeyboardType.Decimal) }
                itemsIndexed(lines) { idx, l ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("${t(Str.product, lang)} ${idx+1}", color = GoldSoft)
                        var pidText by remember { mutableStateOf(if (l.productId>0) l.productId.toString() else (products.firstOrNull()?.id?.toString()?:"")) }
                        Field("Product ID", pidText, { pidText = it; l.productId = it.toLongOrNull()?:0 }, keyboardType = KeyboardType.Number)
                        Field(t(Str.quantity, lang), l.qty, { v -> l.qty = v; lines = lines.toList() }, keyboardType = KeyboardType.Decimal)
                        Field(t(Str.price, lang), l.price, { v -> l.price = v; lines = lines.toList() }, keyboardType = KeyboardType.Decimal)
                        HorizontalDivider(color = Line)
                    }
                }
                item { OutlinedButton(onClick = { lines = lines + SaleLineDraft(products.firstOrNull()?.id ?: 0) }) { Text(t(Str.addItem, lang)) } }
                item { Text(if (lang=="ar") "ملاحظة: أدخل معرف المنتج (ID) من شاشة المنتجات" else "Note: enter product ID from Products screen", color = Muted, style = MaterialTheme.typography.bodySmall) }
            }
        })
    }
}
