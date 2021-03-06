package org.gradle.plugins.compile

import accessors.java
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.tasks.compile.AbstractCompile
import org.gradle.api.tasks.compile.CompileOptions
import org.gradle.api.tasks.compile.GroovyCompile
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.toolchain.internal.JavaInstallationProbe
import org.gradle.kotlin.dsl.the
import org.gradle.kotlin.dsl.withType


open class GradleCompilePlugin : Plugin<Project> {
    override fun apply(project: Project) = project.run {
        if (rootProject == project) {
            val projectInternal = project as ProjectInternal
            val javaInstallationProbe = projectInternal.services.get(JavaInstallationProbe::class.java)
            extensions.create(
                "availableJavaInstallations",
                AvailableJavaInstallations::class.java,
                listOfNotNull(resolveJavaHomePath("java7Home", project)),
                resolveJavaHomePath("testJavaHome", project),
                javaInstallationProbe
            )
        }

        afterEvaluate {
            val availableJavaInstallations = rootProject.the<AvailableJavaInstallations>()

            tasks.withType<JavaCompile> {
                options.isIncremental = true
                configureCompileTask(this, options, availableJavaInstallations)
            }
            tasks.withType<GroovyCompile> {
                groovyOptions.encoding = "utf-8"
                configureCompileTask(this, options, availableJavaInstallations)
            }
        }
    }

    private
    fun configureCompileTask(compileTask: AbstractCompile, options: CompileOptions, availableJavaInstallations: AvailableJavaInstallations) {
        options.isFork = true
        options.encoding = "utf-8"
        options.compilerArgs = mutableListOf("-Xlint:-options", "-Xlint:-path")
        val targetJdkVersion = maxOf(compileTask.project.java.targetCompatibility, JavaVersion.VERSION_1_7)
        val jdkForCompilation = availableJavaInstallations.jdkForCompilation(targetJdkVersion)
        if (!jdkForCompilation.current) {
            options.forkOptions.javaHome = jdkForCompilation.javaHome
        }
        compileTask.inputs.property("javaInstallation", when (compileTask) {
            is JavaCompile -> jdkForCompilation
            else -> availableJavaInstallations.currentJavaInstallation
        }.displayName)
    }

    private
    fun resolveJavaHomePath(propertyName: String, project: Project): String? = when {
        project.hasProperty(propertyName) -> project.property(propertyName) as String
        System.getProperty(propertyName) != null -> System.getProperty(propertyName)
        else -> null
    }
}
