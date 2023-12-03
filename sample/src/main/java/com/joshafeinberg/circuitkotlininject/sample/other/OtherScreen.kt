package com.joshafeinberg.circuitkotlininject.sample.other

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.joshafeinberg.circuitkotlininject.sample.AppScope
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.presenter.Presenter
import com.slack.circuit.runtime.screen.Screen

@com.slack.circuit.codegen.annotations.CircuitInject(OtherScreen::class, AppScope::class)
@Composable
fun OtherScreen(modifier: Modifier) {
    Text("Other Screen")
}

data object OtherScreen : Screen {

    data object OtherScreenState : CircuitUiState

}

@com.slack.circuit.codegen.annotations.CircuitInject(OtherScreen::class, AppScope::class)
class OtherScreenPresenter(private val injectedString: String) : Presenter<OtherScreen.OtherScreenState> {
    @Composable
    override fun present(): OtherScreen.OtherScreenState {
        return OtherScreen.OtherScreenState
    }

}