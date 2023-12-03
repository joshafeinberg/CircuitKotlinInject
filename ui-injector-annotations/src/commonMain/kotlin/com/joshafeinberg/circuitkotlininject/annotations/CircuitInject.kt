package com.joshafeinberg.circuitkotlininject.annotations

import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.screen.Screen
import kotlin.reflect.KClass

@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
annotation class CircuitInject(val screen: KClass<out Screen>)
