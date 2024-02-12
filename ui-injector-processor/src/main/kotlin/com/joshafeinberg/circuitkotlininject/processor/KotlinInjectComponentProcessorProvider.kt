package com.joshafeinberg.circuitkotlininject.processor

import com.google.auto.service.AutoService
import com.google.devtools.ksp.closestClassDeclaration
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSFile
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.writeTo
import java.util.*

private const val CIRCUIT_RUNTIME_BASE_PACKAGE = "com.slack.circuit.runtime"
private const val KOTLIN_INJECT_BASE_PACKAGE = "me.tatarka.inject.annotations"
private const val CIRCUIT_RUNTIME_UI_PACKAGE = "${CIRCUIT_RUNTIME_BASE_PACKAGE}.ui"
private const val CIRCUIT_RUNTIME_PRESENTER_PACKAGE = "${CIRCUIT_RUNTIME_BASE_PACKAGE}.presenter"
private val CIRCUIT = ClassName("com.slack.circuit.foundation", "Circuit")
private val CIRCUIT_UI = ClassName(CIRCUIT_RUNTIME_UI_PACKAGE, "Ui")
private val CIRCUIT_UI_FACTORY = CIRCUIT_UI.nestedClass("Factory")
private val CIRCUIT_PRESENTER = ClassName(CIRCUIT_RUNTIME_PRESENTER_PACKAGE, "Presenter")
private val CIRCUIT_PRESENTER_FACTORY = CIRCUIT_PRESENTER.nestedClass("Factory")
private val KOTLIN_INJECT_COMPONENT = ClassName(KOTLIN_INJECT_BASE_PACKAGE, "Component")
private val KOTLIN_INJECT_INTO_SET = ClassName(KOTLIN_INJECT_BASE_PACKAGE, "IntoSet")
private val KOTLIN_INJECT_PROVIDES = ClassName(KOTLIN_INJECT_BASE_PACKAGE, "Provides")
private const val CIRCUIT_CODEGEN_MODE = "circuit.codegen.mode"
private const val CIRCUIT_COMPONENT_PACKAGE = "circuit.codegen.package"
private const val CIRCUIT_PARENT_COMPONENT = "circuit.codegen.parent.component"

@AutoService(SymbolProcessorProvider::class)
public class KotlinInjectComponentProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return KotlinInjectComponentProcessor(
            environment.logger,
            environment.codeGenerator,
            environment.options,
        )
    }
}

private class KotlinInjectComponentProcessor(
    private val logger: KSPLogger,
    private val codeGenerator: CodeGenerator,
    private val options: Map<String, String>,
) : SymbolProcessor {
    override fun process(resolver: Resolver): List<KSAnnotated> {
        if (options[CIRCUIT_CODEGEN_MODE] != "kotlin_inject") {
            return emptyList()
        }

        val componentPackage = options[CIRCUIT_COMPONENT_PACKAGE]
        if (componentPackage == null) {
            logger.error("Should set component's package")
        }

        val parentComponent = options[CIRCUIT_PARENT_COMPONENT]?.let {
            ClassName.bestGuess(it.trim())
        }

        val bindingResults = mutableListOf<KotlinInjectBindingResult>()

        resolver.getNewFiles().forEach { file ->
            file.declarations.forEach { declarations ->
                declarations.closestClassDeclaration()?.let { classDeclaration ->
                    val isCircuitFactory = classDeclaration.superTypes.any {
                        val typeName = it.resolve().toTypeName()
                        typeName == CIRCUIT_UI_FACTORY || typeName == CIRCUIT_PRESENTER_FACTORY
                    }
                    if (isCircuitFactory) {
                        bindingResults.add(
                            KotlinInjectBindingResult(
                                classDeclaration.simpleName.asString(),
                                classDeclaration.packageName.asString(),
                                classDeclaration.superTypes.any { it.resolve().toTypeName() == CIRCUIT_PRESENTER_FACTORY },
                                originatingFiles = listOf(file)
                            )
                        )
                    }
                }
            }
        }

        createComponentFile(
            bindingResults,
            componentPackage,
            parentComponent?.let { listOf(it) },
        )

        return emptyList()
    }

    private fun createComponentFile(
        symbols: List<KotlinInjectBindingResult>,
        componentPackage: String?,
        parentClasses: List<ClassName>?
    ) {
        if (symbols.isEmpty()) {
            return
        }

        val bindMethods = symbols.map { bindingResult ->
            if (bindingResult.isPresenter) {
                PropertySpec.builder(
                    "bind",
                    CIRCUIT_PRESENTER_FACTORY, KModifier.PROTECTED
                )
                    .receiver(ClassName(bindingResult.factoryPackage, bindingResult.factoryName))
                    .getter(
                        FunSpec.getterBuilder()
                            .addAnnotation(KOTLIN_INJECT_PROVIDES)
                            .addAnnotation(KOTLIN_INJECT_INTO_SET)
                            .addStatement("return this")
                            .build(),
                    )
                    .build()
            } else {
                PropertySpec.builder(
                    "bind",
                    CIRCUIT_UI_FACTORY, KModifier.PROTECTED
                )
                    .receiver(ClassName(bindingResult.factoryPackage, bindingResult.factoryName))
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
            .addStatement("return %T.Builder().addUiFactories(uiFactories).addPresenterFactories(presenterFactories).build()",
                CIRCUIT
            )
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
            .writeTo(
                codeGenerator,
                Dependencies(false, *symbols.flatMap { it.originatingFiles }.toList().toTypedArray())
            )
    }

}

private data class KotlinInjectBindingResult(
    val factoryName: String,
    val factoryPackage: String,
    val isPresenter: Boolean,
    val originatingFiles: List<KSFile>,
)
