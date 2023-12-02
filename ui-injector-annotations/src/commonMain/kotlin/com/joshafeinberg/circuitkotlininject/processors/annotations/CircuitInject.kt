package com.joshafeinberg.circuitkotlininject.processors.annotations

import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.screen.Screen
import kotlin.reflect.KClass

@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
annotation class CircuitInject(val screen: KClass<out Screen>, val state: KClass<out CircuitUiState>)
