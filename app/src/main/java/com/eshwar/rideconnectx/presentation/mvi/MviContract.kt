package com.eshwar.rideconnectx.presentation.mvi

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface UiState
interface UiIntent
interface UiEffect

interface MviViewModel<S : UiState, I : UiIntent, E : UiEffect> {
    val uiState: StateFlow<S>
    val effect: SharedFlow<E>
    fun onIntent(intent: I)
}
