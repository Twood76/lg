/*
 *  Copyright (c) 2019-present, Android Asset File Generator Contributors.
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in
 *  compliance with the License. You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is
 *  distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See
 *  the License for the specific language governing permissions and limitations under the License.
 */

package com.github.utilx.aafg

import com.android.build.gradle.AndroidConfig
import com.android.build.gradle.api.AndroidSourceSet
import com.github.utilx.aafg.java.GenerateJavaFileTask
import com.github.utilx.aafg.java.JavaFileConfig
import com.github.utilx.aafg.kotlin.GenerateKotlinFileTask
import com.github.utilx.aafg.kotlin.KotlinFileConfig
import com.github.utilx.aafg.xml.GenerateXmlFileTask
import com.github.utilx.aafg.xml.XmlFileConfig
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import org.gradle.internal.Try
import org.gradle.kotlin.dsl.findByType
import org.gradle.kotlin.dsl.task
import java.io.File

/**
 * generated/aafg/src
 */
private val GENERATED_SRC_DIR_NAME = listOf("generated", "aafg", "src").toFilePath()

private const val RES_OUTPUT_DIR_NAME = "res"
private const val JAVA_OUTPUT_DIR_NAME = "java"
private const val KOTLIN_OUTPUT_DIR_NAME = "kotlin"

private const val PRE_BUILD_TASK_NAME = "preBuild"

private const val ROOT_EXTENSION_NAME = "androidAssetFileGenerator"
private const val XML_GENERATOR_EXTENSION_NAME = "xmlFile"
private const val JAVA_GENERATOR_EXTENSION_NAME = "javaFile"
private const val KOTLIN_GENERATOR_EXTENSION_NANE = "kotlinFile"

/**
 * res/values/strings.xml
 */
private val XML_OUTPUT_FILE = listOf(RES_OUTPUT_DIR_NAME, "values", "asset-strings.xml").toFilePath()


open class AssetFileGeneratorPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        val extension = project.extensions.create(ROOT_EXTENSION_NAME, AssetFileGeneratorConfig::class.java)
        val extensionAware = extension as ExtensionAware

        val xmlExtension =
            extensionAware.extensions.create(XML_GENERATOR_EXTENSION_NAME, XmlFileConfig::class.java)

        val javaExtension = extensionAware.extensions.create(JAVA_GENERATOR_EXTENSION_NAME, JavaFileConfig::class.java)

        val kotlinExtension =
            extensionAware.extensions.create(KOTLIN_GENERATOR_EXTENSION_NANE, KotlinFileConfig::class.java)

        val androidConfig = Try.ofFailable { project.extensions.findByType<AndroidConfig>() }
            .mapFailure { IllegalStateException("Failed to locate android plugin extension, make sure plugin is applied after android gradle plugin") }
            .get()

        project.afterEvaluate {
            extension.sourceSetNames.forEach { sourceSetName ->
                val sourceSet = Try.ofFailable { androidConfig.sourceSets.findByName(sourceSetName)!! }
                    .mapFailure { IllegalStateException("failed to locate $sourceSetName sourceSet") }
                    .get()

                if (xmlExtension.enabled) {
                    configureXmlTask(project, xmlExtension, sourceSet)
                }

                if (javaExtension.enabled) {
                    configureJavaTask(project, javaExtension, sourceSet)
                }

                if (kotlinExtension.enabled) {
                    configureKotlinTask(project, kotlinExtension, sourceSet)
                }
            }
        }
    }

    private fun configureXmlTask(
        project: Project,
        xmlConfig: XmlFileConfig,
        sourceSet: AndroidSourceSet
    ) {
        //Register new res directory to provided sourceSet so all generated xml files are accessible in the project
        val generatedResDirectory = getGeneratedResOutputDirForSourceSet(
            projectBuildDir = project.buildDir,
            sourceSetName = sourceSet.name
        )
        sourceSet.res.srcDirs(generatedResDirectory)

        val generatedXmlFile = getOutpulXmFileForSourceSet(
            projectBuildDir = project.buildDir,
            sourceSetName = sourceSet.name
        )

        val xmlAssetFileTask = project.task<GenerateXmlFileTask>("generateAssetXmlFile${sourceSet.name}") {
            this.sourceSet = sourceSet
            this.outputFile = generatedXmlFile
        }.apply { configureUsing(xmlConfig) }

        // register new xml generation task
        project.tasks.getByName(PRE_BUILD_TASK_NAME).dependsOn(xmlAssetFileTask)

        println(
            "Configured xml generation task for [${sourceSet.name}] source set\n" +
                    "Registered new res directory - $generatedResDirectory\n" +
                    "Asset xml file will be generated at $generatedXmlFile"
        )
    }

    private fun configureJavaTask(
        project: Project,
        extension: JavaFileConfig,
        sourceSet: AndroidSourceSet
    ) {
        val outputSrcDir = getGeneratedJavaOutputDirForSourceSet(
            projectBuildDir = project.buildDir,
            sourceSetName = sourceSet.name
        )

        val generateJavaTask = project.task<GenerateJavaFileTask>("generateAssetJavaFile${sourceSet.name}") {
            this.sourceSet = sourceSet
            this.outputSrcDir = outputSrcDir
        }.apply { configureUsing(extension) }

        sourceSet.java.srcDirs(outputSrcDir)
        project.tasks.getByName(PRE_BUILD_TASK_NAME).dependsOn(generateJavaTask)

        println(
            "Configured java generation task for [${sourceSet.name}] source set\n" +
                    "Registered new java source directory - $outputSrcDir"
        )
    }

    fun configureKotlinTask(
        project: Project,
        extension: KotlinFileConfig,
        sourceSet: AndroidSourceSet
    ) {
        val outputSrcDir = getGeneratedKotlinOutputDirForSourceSet(
            projectBuildDir = project.buildDir,
            sourceSetName = sourceSet.name
        )
        val generateKotlinTask = project.task<GenerateKotlinFileTask>("generateAssetKotlinFile${sourceSet.name}") {
            this.sourceSet = sourceSet
            this.outputSrcDir = outputSrcDir
        }.apply { configureUsing(extension) }

        sourceSet.java.srcDirs(outputSrcDir)
        project.tasks.getByName(PRE_BUILD_TASK_NAME).dependsOn(generateKotlinTask)

        println(
            "Configured kotlin generation task for [${sourceSet.name}] source set\n" +
                    "Registered new kotlin source directory - $outputSrcDir"
        )

    }

    /**
     * Returns SourceSet dependant output directory where files will be generated
     * usually returns something like <Project>/build/generated/aafg/src/<main>/
     */
    fun getGeneratedSrcDirForSourceSet(
        projectBuildDir: File,
        sourceSetName: String
    ) = File(projectBuildDir, listOf(GENERATED_SRC_DIR_NAME, sourceSetName).toFilePath())


    /**
     * Returns SourceSet dependant res directory where files will be generated
     * usually returns something like <Project>/build/generated/aafg/src/<main>/res
     */
    fun getGeneratedResOutputDirForSourceSet(
        projectBuildDir: File,
        sourceSetName: String
    ) = File(getGeneratedSrcDirForSourceSet(projectBuildDir, sourceSetName), RES_OUTPUT_DIR_NAME)

    /**
     * Returns SourceSet dependant java source root directory where files will be generated
     * usually returns something like <Project>/build/generated/aafg/src/<main>/java
     */
    fun getGeneratedJavaOutputDirForSourceSet(
        projectBuildDir: File,
        sourceSetName: String
    ) = File(getGeneratedSrcDirForSourceSet(projectBuildDir, sourceSetName), JAVA_OUTPUT_DIR_NAME)

    /**
     * Returns SourceSet dependant kotlin source root directory where files will be generated
     * usually returns something like <Project>/build/generated/aafg/src/<main>/kotlin
     */
    fun getGeneratedKotlinOutputDirForSourceSet(
        projectBuildDir: File,
        sourceSetName: String
    ) = File(getGeneratedSrcDirForSourceSet(projectBuildDir, sourceSetName), KOTLIN_OUTPUT_DIR_NAME)

    /**
     * Returns SourceSet dependant res directory where files will be generated
     * usually returns something like <Project>/build/generated/aafg/src/<main>/res/values/strings.xml
     */
    fun getOutpulXmFileForSourceSet(
        projectBuildDir: File,
        sourceSetName: String
    ) = File(getGeneratedSrcDirForSourceSet(projectBuildDir, sourceSetName), XML_OUTPUT_FILE)
}

private fun <T> Iterable<T>.toFilePath(): String {
    return this.joinToString(separator = File.separator)
}