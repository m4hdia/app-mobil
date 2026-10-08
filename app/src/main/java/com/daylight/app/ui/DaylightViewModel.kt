package com.daylight.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.daylight.app.DaylightApplication
import com.daylight.app.data.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi

class DaylightViewModel(app: Application) : AndroidViewModel(app) {
    val container = app as DaylightApplication
    val repository = container.repository
    val ready = MutableStateFlow(false)
    val loadError = MutableStateFlow(false)
    private val reload = MutableStateFlow(0)
    private val messages = Channel<String>(Channel.BUFFERED)
    val events = messages.receiveAsFlow()
    @OptIn(ExperimentalCoroutinesApi::class)
    val state = reload.flatMapLatest {
        repository.data.onEach { ready.value = true; loadError.value = false }
            .catch { error ->
                if (error is CancellationException) throw error
                loadError.value = true
                ready.value = false
            }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppData())
    fun retryLoad() { loadError.value = false; reload.value += 1 }
    fun message(text: String) { viewModelScope.launch { messages.send(text) } }
    fun execute(success: String = "", reschedule: Boolean = false, block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
                if (reschedule) {
                    try { container.engine.reschedule() }
                    catch (error: Exception) {
                        if (error is CancellationException) throw error
                        messages.send("Android could not register reminders. Reopen the app to retry.")
                    }
                }
                if (success.isNotBlank()) messages.send(success)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                messages.send(if (e is IllegalArgumentException) e.message ?: "Please check your entries." else "This change could not be saved. Please try again.")
            }
        }
    }
}
