package com.joshafeinberg.circuitkotlininject.sample

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.joshafeinberg.circuitkotlininject.processors.annotations.CircuitInject
import com.slack.circuit.foundation.CircuitCompositionLocals
import com.slack.circuit.foundation.CircuitContent
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.presenter.Presenter
import com.slack.circuit.runtime.screen.Screen
import me.tatarka.inject.annotations.Component
import me.tatarka.inject.annotations.Provides

fun main() = application {
    val parentComponent = remember { ParentComponent::class.create() }
    val circuitComponent = remember { CircuitComponent::class.create(parentComponent) }

    Window(
        onCloseRequest = ::exitApplication,
        title = "Sample"
    ) {
        CircuitCompositionLocals(circuitComponent.circuit) {
            CircuitContent(MyScreen)
        }
    }

}

@Component
abstract class ParentComponent {

    @Provides
    fun providesString(): String {
        return "Injected String!"
    }

}

@CircuitInject(MyScreen::class, MyScreen.MyScreenState::class)
@Composable
fun MyScreen(state: MyScreen.MyScreenState, modifier: Modifier) {
    Text(state.visibleString)
}

data object MyScreen : Screen {

    data class MyScreenState(
        val visibleString: String
    ) : CircuitUiState

}

@CircuitInject(MyScreen::class, MyScreen.MyScreenState::class)
class MyScreenPresenter(private val injectedString: String) : Presenter<MyScreen.MyScreenState> {
    @Composable
    override fun present(): MyScreen.MyScreenState {
        return MyScreen.MyScreenState(injectedString)
    }

}