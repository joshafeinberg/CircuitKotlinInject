package com.joshafeinberg.circuitkotlininject.processor

import com.google.devtools.ksp.containingFile
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSNode
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSValueParameter
import com.google.devtools.ksp.validate
import com.joshafeinberg.circuitkotlininject.annotations.CircuitInject
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.STAR
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.writeTo
import java.util.Locale

private const val CIRCUIT_RUNTIME_BASE_PACKAGE = "com.slack.circuit.runtime"
private const val KOTLIN_INJECT_BASE_PACKAGE = "me.tatarka.inject.annotations"
private const val CIRCUIT_RUNTIME_UI_PACKAGE = "$CIRCUIT_RUNTIME_BASE_PACKAGE.ui"
private const val CIRCUIT_RUNTIME_SCREEN_PACKAGE = "$CIRCUIT_RUNTIME_BASE_PACKAGE.screen"
private const val CIRCUIT_RUNTIME_PRESENTER_PACKAGE = "$CIRCUIT_RUNTIME_BASE_PACKAGE.presenter"
private val COMPOSABLE = ClassName("androidx.compose.runtime", "Composable")
private val MODIFIER = ClassName("androidx.compose.ui", "Modifier")
private val CIRCUIT = ClassName("com.slack.circuit.foundation", "Circuit")
private val CIRCUIT_PRESENTER = ClassName(CIRCUIT_RUNTIME_PRESENTER_PACKAGE, "Presenter")
private val CIRCUIT_PRESENTER_FACTORY = CIRCUIT_PRESENTER.nestedClass("Factory")
private val CIRCUIT_UI = ClassName(CIRCUIT_RUNTIME_UI_PACKAGE, "Ui")
private val CIRCUIT_UI_FACTORY = CIRCUIT_UI.nestedClass("Factory")
private val CIRCUIT_UI_STATE = ClassName(CIRCUIT_RUNTIME_BASE_PACKAGE, "CircuitUiState")
private val SCREEN = ClassName(CIRCUIT_RUNTIME_SCREEN_PACKAGE, "Screen")
private val NAVIGATOR = ClassName(CIRCUIT_RUNTIME_BASE_PACKAGE, "Navigator")
private val CIRCUIT_CONTEXT = ClassName(CIRCUIT_RUNTIME_BASE_PACKAGE, "CircuitContext")
private val KOTLIN_INJECT_ANNOTATION = ClassName(KOTLIN_INJECT_BASE_PACKAGE, "Inject")
private val KOTLIN_INJECT_COMPONENT = ClassName(KOTLIN_INJECT_BASE_PACKAGE, "Component")
private val KOTLIN_INJECT_INTO_SET = ClassName(KOTLIN_INJECT_BASE_PACKAGE, "IntoSet")
private val KOTLIN_INJECT_PROVIDES = ClassName(KOTLIN_INJECT_BASE_PACKAGE, "Provides")

private const val CIRCUIT_COMPONENT_PACKAGE = "circuit.codegen.package"
private const val CIRCUIT_PARENT_COMPONENT = "circuit.codegen.parent.component"

class UiInjectorProcessor(
    private val options: Map<String, String>,
    private val logger: KSPLogger,
    private val codeGenerator: CodeGenerator,
) : SymbolProcessor {
    override fun process(resolver: Resolver): List<KSAnnotated> {
        val componentPackage = options[CIRCUIT_COMPONENT_PACKAGE]
        if (componentPackage == null) {
            logger.error("Should set component's package")
        }

        val parentClasses = options[CIRCUIT_PARENT_COMPONENT]?.split(",")?.map {
            ClassName.bestGuess(it.trim())
        }

        val symbols = resolver
            .getSymbolsWithAnnotation(CircuitInject::class.qualifiedName!!)
            .filter(KSNode::validate)
            .map { ksAnnotated ->
                val circuitInject = ksAnnotated.annotations.firstOrNull { it.shortName.asString() == CircuitInject::class.simpleName }
                val screen = circuitInject.getScreenQualifiedRoute()
                val state = circuitInject.getStateQualifiedRoute()

                when (ksAnnotated) {
                    is KSFunctionDeclaration -> NeededInjector(
                        ksAnnotated.containingFile,
                        ksAnnotated.simpleName.getShortName(),
                        ksAnnotated.packageName.asString(),
                        screen,
                        state,
                        null,
                    )
                    is KSClassDeclaration -> NeededInjector(
                        ksAnnotated.containingFile,
                        ksAnnotated.simpleName.getShortName(),
                        ksAnnotated.packageName.asString(),
                        screen,
                        state,
                        ksAnnotated.primaryConstructor?.parameters,
                    )
                    else -> {
                        NeededInjector(
                            ksAnnotated.containingFile,
                            "Unknown type: ${ksAnnotated::class} -> $ksAnnotated",
                            "",
                            null,
                            null,
                            null,
                        )
                    }
                }
            }

        if (!symbols.iterator().hasNext()) return emptyList()

        createFactoriesFile(symbols, componentPackage)
        createComponentFile(symbols, componentPackage, parentClasses)

        return emptyList()
    }

    private fun KSAnnotation?.getScreenQualifiedRoute(): KSType? {
        if (this == null) {
            error("Screen is not provided")
        }
        return this.arguments.firstOrNull { it.name?.asString() == "screen" }?.value as? KSType
    }

    private fun KSAnnotation?.getStateQualifiedRoute(): KSType? {
        if (this == null) {
            error("State is not provided")
        }
        return this.arguments.firstOrNull { it.name?.asString() == "state" }?.value as? KSType
    }

    /**
     * Creates all the [CIRCUIT_UI_FACTORY] and [CIRCUIT_PRESENTER_FACTORY]. Currently only supports
     * Presenters defined as a class
     * UI defined as a function
     */
    private fun createFactoriesFile(symbols: Sequence<NeededInjector>, componentPackage: String?) {
        val factories = symbols.map { neededInjector ->
            if (neededInjector.isPresenter) {
                generatePresenterFactory(neededInjector)
            } else {
                generateUiFactory(neededInjector)
            }
        }

        FileSpec.builder(componentPackage ?: "", "CircuitFactories")
            .addTypes(factories.toList())
            .build()
            .writeTo(codeGenerator, Dependencies(false, *getAffectedFilesFromSymbols(symbols)))
    }

    private fun generateUiFactory(neededInjector: NeededInjector): TypeSpec {
        val innerObject = TypeSpec.anonymousClassBuilder()
            .addSuperinterface(CIRCUIT_UI.parameterizedBy(neededInjector.state!!.toTypeName()))
            .addFunction(
                FunSpec.builder("Content")
                    .addAnnotation(COMPOSABLE)
                    .addModifiers(KModifier.OVERRIDE)
                    .addParameter("state", neededInjector.state.toTypeName())
                    .addParameter("modifier", MODIFIER)
                    .addStatement("%T(%L, %L)", ClassName(neededInjector.packageName, neededInjector.name), "state", "modifier")
                    .build(),
            )
            .build()

        return TypeSpec.classBuilder("${neededInjector.name}Factory")
            .addAnnotation(KOTLIN_INJECT_ANNOTATION)
            .addSuperinterface(CIRCUIT_UI_FACTORY)
            .addFunction(
                FunSpec.builder("create")
                    .addModifiers(KModifier.OVERRIDE)
                    .addParameter("screen", SCREEN)
                    .addParameter("context", CIRCUIT_CONTEXT)
                    .returns(CIRCUIT_UI.parameterizedBy(STAR).copy(nullable = true))
                    .beginControlFlow("return if (screen is %T)", neededInjector.screen!!.toTypeName())
                    .addStatement("%L", innerObject)
                    .nextControlFlow("else")
                    .addStatement("null")
                    .endControlFlow()
                    .build(),
            )
            .build()
    }

    private fun generatePresenterFactory(neededInjector: NeededInjector): TypeSpec {
        val isNotAssistedType: (KSValueParameter) -> Boolean = {
            val type = it.type.resolve().declaration.qualifiedName!!.asString()
            type == NAVIGATOR.canonicalName || type.endsWith("Screen")
        }

        val factoryParameterSpecs = neededInjector.parameters?.mapNotNull {
            if (isNotAssistedType(it)) {
                return@mapNotNull null
            }

            ParameterSpec.builder(it.name!!.asString(), it.type.resolve().toTypeName())
                .build()
        } ?: emptyList()

        val factoryPropertySpecs = neededInjector.parameters?.mapNotNull {
            if (isNotAssistedType(it)) {
                return@mapNotNull null
            }

            PropertySpec.builder(it.name!!.asString(), it.type.resolve().toTypeName())
                .initializer(it.name!!.asString())
                .addModifiers(KModifier.PRIVATE)
                .build()
        } ?: emptyList()

        val contructorParams = neededInjector.parameters?.joinToString(",") {
            it.name!!.asString()
        } ?: ""

        val primaryConstructor = FunSpec.constructorBuilder()
            .addParameters(factoryParameterSpecs)
            .build()

        return TypeSpec.classBuilder("${neededInjector.name}Factory")
            .addAnnotation(KOTLIN_INJECT_ANNOTATION)
            .addSuperinterface(CIRCUIT_PRESENTER_FACTORY)
            .primaryConstructor(primaryConstructor)
            .addProperties(factoryPropertySpecs)
            .addFunction(
                FunSpec.builder("create")
                    .addModifiers(KModifier.OVERRIDE)
                    .addParameter("screen", SCREEN)
                    .addParameter("navigator", NAVIGATOR)
                    .addParameter("context", CIRCUIT_CONTEXT)
                    .returns(CIRCUIT_PRESENTER.parameterizedBy(STAR).copy(nullable = true))
                    .beginControlFlow("return if (screen is ${neededInjector.screen})")
                    .addStatement("%T(%L)", ClassName(neededInjector.packageName, neededInjector.name), contructorParams)
                    .nextControlFlow("else")
                    .addStatement("null")
                    .endControlFlow()
                    .build(),
            )
            .build()
    }

    private fun createComponentFile(
        symbols: Sequence<NeededInjector>,
        componentPackage: String?,
        parentClasses: List<ClassName>?
    ) {
        val bindMethods = symbols.map { neededInjector ->
            if (neededInjector.isPresenter) {
                PropertySpec.builder("bind", CIRCUIT_PRESENTER_FACTORY, KModifier.PROTECTED)
                    .receiver(ClassName(componentPackage ?: "", "${neededInjector.name}Factory"))
                    .getter(
                        FunSpec.getterBuilder()
                            .addAnnotation(KOTLIN_INJECT_PROVIDES)
                            .addAnnotation(KOTLIN_INJECT_INTO_SET)
                            .addStatement("return this")
                            .build(),
                    )
                    .build()
            } else {
                PropertySpec.builder("bind", CIRCUIT_UI_FACTORY, KModifier.PROTECTED)
                    .receiver(ClassName(componentPackage ?: "", "${neededInjector.name}Factory"))
                    .getter(
                        FunSpec.getterBuilder()
                            .addAnnotation(KOTLIN_INJECT_PROVIDES)
                            .addAnnotation(KOTLIN_INJECT_INTO_SET)
                            .addStatement("return this")
                            .build(),
                    )
                    .build()
            }
        }

        val injectableCircuitProperty = PropertySpec.builder("circuit", CIRCUIT, KModifier.ABSTRACT)
            .build()

        val circuitProvider = FunSpec.builder("providesCircuit")
            .addAnnotation(KOTLIN_INJECT_PROVIDES)
            .returns(CIRCUIT)
            .addParameter("uiFactories", Set::class.asClassName().parameterizedBy(CIRCUIT_UI_FACTORY))
            .addParameter("presenterFactories", Set::class.asClassName().parameterizedBy(CIRCUIT_PRESENTER_FACTORY))
            .addStatement("return %T.Builder().addUiFactories(uiFactories).addPresenterFactories(presenterFactories).build()", CIRCUIT)
            .build()

        val parameters = parentClasses?.map {
            ParameterSpec.builder(it.simpleName.lowercase(Locale.getDefault()), it)
                .build()
        }

        val properties = parentClasses?.map {
            val name = it.simpleName.lowercase(Locale.getDefault())
            PropertySpec.builder(name, it)
                .addAnnotation(KOTLIN_INJECT_COMPONENT)
                .initializer(name)
                .build()
        }

        val classSpec = TypeSpec.classBuilder("CircuitComponent")
            .addAnnotation(KOTLIN_INJECT_COMPONENT)
            .addModifiers(KModifier.INTERNAL, KModifier.ABSTRACT)
            .primaryConstructor(
                FunSpec.constructorBuilder()
                    .apply {
                        parameters?.let {
                            addParameters(it)
                        }
                    }
                    .build(),
            )
            .apply {
                properties?.let {
                    addProperties(it)
                }
            }
            .addProperty(injectableCircuitProperty)
            .addFunction(circuitProvider)
            .addProperties(bindMethods.toList())
            .build()

        FileSpec.builder(componentPackage ?: "", "CircuitComponent")
            .addType(classSpec)
            .build()
            .writeTo(codeGenerator, Dependencies(false, *getAffectedFilesFromSymbols(symbols)))
    }

    private fun getAffectedFilesFromSymbols(symbols: Sequence<NeededInjector>): Array<KSFile> {
        return symbols.mapNotNull { it.file }.toList().toTypedArray()
    }

    data class NeededInjector(
        val file: KSFile?,
        val name: String,
        val packageName: String,
        val screen: KSType?,
        val state: KSType?,
        val parameters: List<KSValueParameter>?,
    ) {
        val isPresenter: Boolean = name.endsWith("Presenter")
    }
}
