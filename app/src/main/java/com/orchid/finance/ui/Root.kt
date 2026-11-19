package com.orchid.finance.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import kotlinx.coroutines.launch
import com.orchid.finance.ui.screens.*
import com.orchid.finance.ui.theme.*

sealed class Dest(val route: String, val title: S, val icon: ImageVector) {
    data object Dashboard: Dest("dashboard", Str.dashboard, Icons.Default.Dashboard)
    data object Products: Dest("products", Str.products, Icons.Default.Inventory2)
    data object Sales: Dest("sales", Str.sales, Icons.Default.PointOfSale)
    data object Purchases: Dest("purchases", Str.purchases, Icons.Default.ShoppingCart)
    data object Customers: Dest("customers", Str.customers, Icons.Default.People)
    data object Suppliers: Dest("suppliers", Str.suppliers, Icons.Default.LocalShipping)
    data object Cash: Dest("cash", Str.cash, Icons.Default.AccountBalance)
    data object Expenses: Dest("expenses", Str.expenses, Icons.Default.ReceiptLong)
    data object Accounting: Dest("accounting", Str.accounting, Icons.Default.MenuBook)
    data object Reports: Dest("reports", Str.reports, Icons.Default.Assessment)
    data object Settings: Dest("settings", Str.settings, Icons.Default.Settings)
}

val allDests = listOf(Dest.Dashboard, Dest.Products, Dest.Sales, Dest.Purchases, Dest.Customers, Dest.Suppliers, Dest.Cash, Dest.Expenses, Dest.Accounting, Dest.Reports, Dest.Settings)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrchidRoot(vm: MainViewModel, lang: String) {
    val user by vm.currentUser.collectAsState()
    val needsSetup by vm.needsSetup.collectAsState()
    val err by vm.error.collectAsState()
    val msg by vm.message.collectAsState()
    val dir = if (lang == "ar") LayoutDirection.Rtl else LayoutDirection.Ltr

    CompositionLocalProvider(LocalLayoutDirection provides dir) {
        if (err != null) {
            AlertDialog(onDismissRequest = { vm.consumeError() }, confirmButton = { TextButton(onClick={vm.consumeError()}){ Text("OK") } }, title = { Text(t(Str.error, lang)) }, text = { Text(err ?: "") })
        }
        if (msg != null) {
            LaunchedEffect(msg) { kotlinx.coroutines.delay(2000); vm.consumeMessage() }
        }

        when {
            needsSetup == null -> Box(Modifier.fillMaxSize().background(Navy900), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Gold) }
            needsSetup == true -> AuthScreen(vm, lang, true)
            user == null -> AuthScreen(vm, lang, false)
            else -> MainShell(vm, lang)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainShell(vm: MainViewModel, lang: String) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = Navy800) {
                Spacer(Modifier.height(12.dp))
                Text("🌸 ${t(Str.appName, lang)}", color = GoldSoft, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp))
                Text(t(Str.business, lang), color = Muted, modifier = Modifier.padding(horizontal = 16.dp))
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = Line)
                allDests.forEach { d ->
                    NavigationDrawerItem(
                        label = { Text(t(d.title, lang)) },
                        selected = current == d.route,
                        onClick = { nav.navigate(d.route) { popUpTo(nav.graph.findStartDestination().id) { saveState = true }; launchSingleTop = true; restoreState = true }; scope.launch { drawerState.close() } },
                        icon = { Icon(d.icon, null) },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                        colors = NavigationDrawerItemDefaults.colors(selectedContainerColor = Navy600, selectedIconColor = Gold, selectedTextColor = GoldSoft, unselectedIconColor = Muted, unselectedTextColor = TextPrimary)
                    )
                }
                Spacer(Modifier.weight(1f))
                HorizontalDivider(color = Line)
                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { vm.setLang(if (lang=="ar") "en" else "ar") }) { Text(t(Str.language, lang)) }
                    OutlinedButton(onClick = { vm.logout() }) { Text(t(Str.logout, lang)) }
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(allDests.find { it.route == current }?.let { t(it.title, lang) } ?: t(Str.appName, lang)) },
                    navigationIcon = { IconButton(onClick = { scope.launch { drawerState.open() } }) { Icon(Icons.Default.Menu, null, tint = GoldSoft) } },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Navy800, titleContentColor = TextPrimary)
                )
            },
            containerColor = Navy900
        ) { pad ->
            NavHost(nav, startDestination = Dest.Dashboard.route, modifier = Modifier.padding(pad)) {
                composable(Dest.Dashboard.route) { DashboardScreen(vm, lang) }
                composable(Dest.Products.route) { ProductsScreen(vm, lang) }
                composable(Dest.Sales.route) { SalesScreen(vm, lang) }
                composable(Dest.Purchases.route) { PurchasesScreen(vm, lang) }
                composable(Dest.Customers.route) { PartiesScreen(vm, lang, "customer") }
                composable(Dest.Suppliers.route) { PartiesScreen(vm, lang, "supplier") }
                composable(Dest.Cash.route) { CashScreen(vm, lang) }
                composable(Dest.Expenses.route) { ExpensesScreen(vm, lang) }
                composable(Dest.Accounting.route) { AccountingScreen(vm, lang) }
                composable(Dest.Reports.route) { ReportsScreen(vm, lang) }
                composable(Dest.Settings.route) { SettingsScreen(vm, lang) }
            }
        }
    }
}
