# CircuitKotlinInject

## What Is This

This is a project that the creator foolishly brought up in discussion and decided to open source.

The goal is to be able to generate all the factories and create a `CircuitComponent` using [kotlin-inject](https://github.com/evant/kotlin-inject)

## How Do I Use It

There has not been a release of this (yet) but you can probably use jitpack. I plan on continuing work on this but as "it works on my machine" there isn't a guarantee of additional development.

Once you have included the project, you simply can annote your UI composables and Presenter classes* with the `CircuitInject` annotation and off you go.

* Note - this project currently only supports your UI being a composable and presenter being classes. Additional support for UI classes and Presenter composables *may* come

Since this generates a single component it does not read from the files you must specify a package else it will use an empty string. You can also specify a "parent component" to pass in your dependencies

```
ksp {
    arg("circuit.codegen.package", "com.joshafeinberg.circuitkotlininject.sample")
    arg("circuit.codegen.parent.component", "com.joshafeinberg.circuitkotlininject.sample.ParentComponent")
}
```


## Example

You can view the `sample` project for a simple example.

You create your Circuit Screens like usual with the only difference being the `CircuitInject` annotation

```
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
```

We can see the parameter in `MyScreenPresenter` that is set to be injected from another component. From our configuration earlier, this string will come from `ParentComponent`

### What does this generate

You end up here with two files generated. The first, `CircuitFactories` contains all the `Ui.Factory` and `Presenter.Factory` implementations. In the sample that turns out to this

```
@Inject
public class MyScreenFactory : Ui.Factory {
  override fun create(screen: Screen, context: CircuitContext): Ui<*>? = if (screen is MyScreen) {
    object : Ui<MyScreen.MyScreenState> {
      @Composable
      override fun Content(state: MyScreen.MyScreenState, modifier: Modifier) {
        MyScreen(state, modifier)
      }
    }
  } else {
    null
  }
}

@Inject
public class MyScreenPresenterFactory(
  private val injectedString: String,
) : Presenter.Factory {
  override fun create(
    screen: Screen,
    navigator: Navigator,
    context: CircuitContext,
  ): Presenter<*>? = if (screen is MyScreen) {
    MyScreenPresenter(injectedString)
  } else {
    null
  }
}
```

The other file is `CircuitComponent`. This contains our new component that will build our `Circuit`. It binds all the factories into their respective Sets and creates the `Circuit`

```
@Component
internal abstract class CircuitComponent(
  @Component
  public val parentcomponent: ParentComponent,
) {
  public abstract val circuit: Circuit

  protected val MyScreenFactory.bind: Ui.Factory
    @Provides
    @IntoSet
    get() = this

  protected val MyScreenPresenterFactory.bind: Presenter.Factory
    @Provides
    @IntoSet
    get() = this

  @Provides
  public fun providesCircuit(uiFactories: Set<Ui.Factory>,
      presenterFactories: Set<Presenter.Factory>): Circuit =
      Circuit.Builder().addUiFactories(uiFactories).addPresenterFactories(presenterFactories).build()
}
```

### Wrapping  it up 

You now have a generated component with your circuit so you can begin this like any other project.

```
val circuitComponent = remember { CircuitComponent::class.create(parentComponent) }

CircuitCompositionLocals(circuitComponent.circuit) {
    CircuitContent(MyScreen)
}
```

## What is this project missing

If you need any of these I advise you fork or use a different solution

- UI must be defined as a composable
- Presenters must have a class
- Does not support custom scopes
- Only tested on a single module
- Probably a lot more

## Do I plan on making improvements

Probably? I switched to management so I use this in my side project and it currently supports everything I need. If I do have improvements heres what I'm looking at

- Make it so the `CircuitInject` doesn't require passing in the `State`
- Generate individual files to support multiple moduels in the future
- Support the official annotation generator (if possible)
- Add tests

