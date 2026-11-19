package com.orchid.finance.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.orchid.finance.data.*
import com.orchid.finance.ui.*
import com.orchid.finance.ui.theme.*
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun SettingsScreen(vm: MainViewModel, lang: String) {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    var settings by remember { mutableStateOf<Map<String,String>>(emptyMap()) }
    var rates by remember { mutableStateOf<List<ExchangeRate>>(emptyList()) }
    var audits by remember { mutableStateOf<List<AuditLog>>(emptyList()) }
    var showRate by remember { mutableStateOf(false) }

    fun load() = scope.launch { runCatching {
        settings = vm.r.auditSettings.settings().associate { it.key to it.value }
        rates = vm.r.extra.rates()
        audits = vm.r.auditSettings.audits().take(100)
    } }
    LaunchedEffect(Unit) { load() }

    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { PageCard(title = t(Str.settings, lang)) {
            var tax by remember(settings) { mutableStateOf(settings["tax_rate"] ?: "0") }
            Field(t(Str.tax, lang) + " %", tax, { tax = it }, keyboardType = KeyboardType.Decimal)
            Spacer(Modifier.height(8.dp))
            Button(onClick = { scope.launch { vm.r.setSetting("tax_rate", tax); load() } }, colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Navy900)) { Text(t(Str.save, lang)) }
            Spacer(Modifier.height(12.dp))
            Text("Language", color = Muted)
            Button(onClick = { vm.setLang(if (lang=="ar") "en" else "ar") }, colors = ButtonDefaults.buttonColors(containerColor = Navy600, contentColor = GoldSoft)) { Text(t(Str.language, lang)) }
        } }
        item { PageCard(title = t(Str.exchangeRates, lang)) {
            Button(onClick={showRate=true}, colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Navy900)) { Text(t(Str.add, lang)) }
            Spacer(Modifier.height(8.dp))
            rates.forEach { r -> Text("${r.currency} = ${fmt(r.rate,4)} (${r.effectiveDate})", color = TextPrimary); Spacer(Modifier.height(4.dp)) }
            if (rates.isEmpty()) Text(t(Str.noData, lang), color = Muted)
        } }
        item { PageCard(title = t(Str.backup, lang)) {
            Text(if (lang=="ar") "قاعدة البيانات المحلية محفوظة داخل التطبيق (orchid.db)." else "Local database is stored inside the app (orchid.db).", color = Muted)
            Spacer(Modifier.height(8.dp))
            Button(onClick = {
                scope.launch {
                    runCatching {
                        val dbFile = ctx.getDatabasePath("orchid.db")
                        val out = File(ctx.cacheDir, "orchid_backup_${System.currentTimeMillis()}.db")
                        dbFile.copyTo(out, overwrite = true)
                        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", out)
                        val i = Intent(Intent.ACTION_SEND).apply { type = "application/octet-stream"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                        ctx.startActivity(Intent.createChooser(i, t(Str.backup, lang)))
                    }
                }
            }, colors = ButtonDefaults.buttonColors(containerColor = Navy600, contentColor = GoldSoft)) { Text(if (lang=="ar") "تصدير/مشاركة النسخة الاحتياطية" else "Export/Share backup") }
        } }
        item { PageCard(title = t(Str.auditLog, lang)) {
            audits.take(30).forEach { a -> Text("${a.ts.take(16)} ${a.action} ${a.entity} ${a.entityId ?: ""}", color = Muted); Spacer(Modifier.height(4.dp)) }
            if (audits.isEmpty()) Text(t(Str.noData, lang), color = Muted)
        } }
    }

    if (showRate) {
        var cur by remember { mutableStateOf("USD") }
        var rate by remember { mutableStateOf("") }
        var date by remember { mutableStateOf(Dates.today()) }
        AlertDialog(onDismissRequest={showRate=false}, confirmButton={ TextButton(onClick={ scope.launch { runCatching { vm.r.extra.insertRate(ExchangeRate(currency = cur, rate = rate.toDoubleOrNull()?:1.0, effectiveDate = date, createdAt = Dates.nowIso())); load(); showRate=false } } }){ Text(t(Str.save, lang)) } }, dismissButton={ TextButton(onClick={showRate=false}){ Text(t(Str.cancel, lang)) } }, title={ Text(t(Str.exchangeRates, lang)) }, text={
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Field(t(Str.currency, lang) ?: "Currency", cur, {cur=it})
                Field(t(Str.rate, lang) ?: "Rate", rate, {rate=it}, keyboardType = KeyboardType.Decimal)
                Field(t(Str.date, lang), date, {date=it})
            }
        })
    }
}
