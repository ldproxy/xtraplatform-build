package de.interactive_instruments.xtraplatform


import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.initialization.IncludedBuild
import org.jetbrains.gradle.ext.ActionDelegationConfig
import org.jetbrains.gradle.ext.Gradle
import org.jetbrains.gradle.ext.JUnit
import org.jetbrains.gradle.ext.Remote
import org.slf4j.LoggerFactory

/**
 * @author zahnen
 */
class CompositePlugin implements Plugin<Project> {

    static def LOGGER = LoggerFactory.getLogger(CompositePlugin.class)

    @Override
    void apply(Project project) {
        if (project.logger.isInfoEnabled()) {
            project.logger.info("Applying CompositePlugin {} to {}", ApplicationPlugin.getVersion(project), project.name)
        }

        project.plugins.apply("base")
        project.plugins.apply('org.jetbrains.gradle.plugin.idea-ext')

        def includedBuilds = getIncludedBuilds(project)
        def includedBuilds2 = includedBuilds.findAll {it.name != LayerPlugin.XTRAPLATFORM_BUILD}
        def main = includedBuilds.find { project.rootProject.name.startsWith(it.name) }
        project.extensions.create('composite', CompositeExtension, main == null ? '' : main.name)

        project.tasks.register('initTpl', {
            dependsOn includedBuilds2.collect {it.task(':initTpl') }
        })

        project.with {
            idea.project {
                languageLevel = LayerPlugin.JAVA_VERSION
            }
            idea.project.settings {
                runConfigurations {
                    defaults(JUnit) {
                        vmParameters = '-ea -Dlogback.configurationFile=logback-test.xml'
                    }
                    "run"(Gradle) { Gradle it2 ->
                        it2.taskNames = ["run"]
                        it2.project = project
                    }
                    "debug-external"(Remote) {
                        host = 'localhost'
                        port = 5005
                    }
                }
                delegateActions {
                    delegateBuildRunToGradle = true
                    testRunner = ActionDelegationConfig.TestRunner.PLATFORM
                }
                //withModuleXml(project.sourceSets.main) { println it }
                taskTriggers {
                    afterSync tasks.named("initTpl")
                }
            }
        }

        project.afterEvaluate {
            if (project.composite.main == '') {
                throw new IllegalStateException("Could not determine main build, please set 'composite.main' in build.gradle.")
            } else {
                println "\nMain build: ${project.composite.main}"
                project.tasks.named('build', {
                    dependsOn project.gradle.includedBuild(project.composite.main).task(':build')
                })
                project.tasks.named('assemble', {
                    dependsOn project.gradle.includedBuild(project.composite.main).task(':assemble')
                })
                project.tasks.register('run', {
                    dependsOn project.gradle.includedBuild(project.composite.main).task(':run')
                })

                project.tasks.named('check', {
                    dependsOn includedBuilds2.collect {it.task(':check') }
                })
                project.tasks.register('test', {
                    dependsOn includedBuilds2.collect {it.task(':test') }
                })
                project.tasks.named('clean', {
                    dependsOn includedBuilds2.collect {it.task(':clean') }
                })
            }
        }
    }

    static Set<IncludedBuild> getIncludedBuilds(Project project) {
        def includedBuilds = project.gradle.includedBuilds
        def parent = project.gradle.parent
        while (parent != null) {
            includedBuilds += parent.includedBuilds
            parent = parent.gradle.parent
        }

        return includedBuilds as Set
    }

    static Set<String> getIncludedBuildNames(Project project) {
        def inc = getIncludedBuilds(project).collect { it.name == LayerPlugin.XTRAPLATFORM ? LayerPlugin.XTRAPLATFORM_CORE : it.name }
        //println "INC " + inc

        return inc as Set
    }

    static class CompositeExtension {
        String main

        CompositeExtension(String main) {
            this.main = main
        }
    }
}
