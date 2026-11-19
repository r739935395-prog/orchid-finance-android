package com.orchid.finance.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.orchid.finance.ui.*
import com.orchid.finance.ui.theme.*

@Composable
fun AuthScreen(vm: MainViewModel, lang: String, setup: Boolean) {
    var username by remember { mutableStateOf("") }
    var fullName by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }

    Box(Modifier.fillMaxSize().background(Navy900), contentAlignment = Alignment.Center) {
        Card(colors = CardDefaults.cardColors(containerColor = Navy700), modifier = Modifier.padding(24.dp).fillMaxWidth(0.95f)) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("🌸 ${t(Str.appName, lang)}", color = GoldSoft, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineSmall)
                Text(if (setup) t(Str.setup, lang) else t(Str.login, lang), color = TextPrimary, style = MaterialTheme.typography.titleMedium)
                if (setup) Text(t(Str.setupHint, lang), color = Muted)
                OutlinedTextField(username, { username = it }, label = { Text(t(Str.username, lang)) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Gold, unfocusedBorderColor = Line, focusedLabelColor = GoldSoft))
                if (setup) OutlinedTextField(fullName, { fullName = it }, label = { Text(t(Str.fullName, lang)) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Gold, unfocusedBorderColor = Line, focusedLabelColor = GoldSoft))
                OutlinedTextField(password, { password = it }, label = { Text(t(Str.password, lang)) }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Gold, unfocusedBorderColor = Line, focusedLabelColor = GoldSoft))
                if (setup) OutlinedTextField(confirm, { confirm = it }, label = { Text(t(Str.confirmPassword, lang)) }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Gold, unfocusedBorderColor = Line, focusedLabelColor = GoldSoft))
                Button(onClick = { if (setup) vm.setup(username, password, confirm, fullName) else vm.login(username, password) }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Navy900)) {
                    Text(if (setup) t(Str.create, lang) else t(Str.signIn, lang))
                }
                TextButton(onClick = { vm.setLang(if (lang=="ar") "en" else "ar") }) { Text(t(Str.language, lang), color = GoldSoft) }
            }
        }
    }
}
