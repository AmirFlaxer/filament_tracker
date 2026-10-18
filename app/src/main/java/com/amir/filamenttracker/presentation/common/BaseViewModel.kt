package com.amir.filamenttracker.presentation.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amir.filamenttracker.domain.SpoolRuleException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

abstract class BaseViewModel : ViewModel() {
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    /**
     * Runs [block] and shows the text it returns. A broken rule or bad input shows its
     * Hebrew message instead of crashing.
     */
    protected fun launchAction(block: suspend () -> String?) {
        viewModelScope.launch {
            val message = try {
                block()
            } catch (e: SpoolRuleException) {
                e.message
            } catch (e: IllegalArgumentException) {
                e.message ?: "קלט לא תקין"
            }
            message?.let { _messages.emit(it) }
        }
    }

    protected fun <T> Flow<T>.asState(initial: T): StateFlow<T> =
        stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)
}
