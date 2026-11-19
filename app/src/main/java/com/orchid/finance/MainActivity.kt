package com.orchid.finance

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.orchid.finance.ui.MainViewModel
import com.orchid.finance.ui.OrchidRoot
import com.orchid.finance.ui.theme.OrchidTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as OrchidApp
        setContent {
            val vm: MainViewModel = viewModel(factory = MainViewModel.Factory(app.repository, app))
            val lang by vm.lang.collectAsState()
            OrchidTheme {
                OrchidRoot(vm = vm, lang = lang)
            }
        }
    }
}
