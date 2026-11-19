package com.orchid.finance.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.orchid.finance.data.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MainViewModel(private val repo: Repository, app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("orchid_prefs", 0)

    private val _lang = MutableStateFlow(prefs.getString("lang", "ar") ?: "ar")
    val lang: StateFlow<String> = _lang

    private val _sessionToken = MutableStateFlow<String?>(prefs.getString("session_token", null))
    val sessionToken: StateFlow<String?> = _sessionToken

    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser

    private val _needsSetup = MutableStateFlow<Boolean?>(null)
    val needsSetup: StateFlow<Boolean?> = _needsSetup

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    init { bootstrap() }

    fun bootstrap() = viewModelScope.launch {
        _loading.value = true
        try {
            _needsSetup.value = repo.userCount() == 0
            val u = repo.getSessionUser(_sessionToken.value)
            _currentUser.value = u
            if (u == null) clearSession()
        } catch (e: Exception) { _error.value = e.message }
        finally { _loading.value = false }
    }

    fun setLang(l: String) {
        _lang.value = l
        prefs.edit().putString("lang", l).apply()
    }

    private fun saveSession(token: String) {
        _sessionToken.value = token
        prefs.edit().putString("session_token", token).apply()
    }
    private fun clearSession() {
        _sessionToken.value = null
        _currentUser.value = null
        prefs.edit().remove("session_token").apply()
    }

    fun setup(username: String, password: String, confirm: String, fullName: String) = viewModelScope.launch {
        if (password != confirm) { _error.value = if (_lang.value=="ar") "كلمتا المرور غير متطابقتين" else "Passwords do not match"; return@launch }
        runCatching {
            repo.createUser(username, password, fullName, "admin")
            val u = repo.validateCredentials(username, password)!!
            val token = repo.createSession(u.id)
            saveSession(token); _currentUser.value = u; _needsSetup.value = false
        }.onFailure { _error.value = it.message }
    }

    fun login(username: String, password: String) = viewModelScope.launch {
        runCatching {
            val u = repo.validateCredentials(username, password) ?: error(if (_lang.value=="ar") "بيانات الدخول غير صحيحة" else "Invalid credentials")
            val token = repo.createSession(u.id)
            saveSession(token); _currentUser.value = u
        }.onFailure { _error.value = it.message }
    }

    fun logout() = viewModelScope.launch {
        repo.deleteSession(_sessionToken.value)
        clearSession()
    }

    fun consumeError() { _error.value = null }
    fun consumeMessage() { _message.value = null }
    fun notify(msg: String) { _message.value = msg }

    val r get() = repo
    val uid get() = _currentUser.value?.id

    class Factory(private val repo: Repository, private val app: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = MainViewModel(repo, app) as T
    }
}
