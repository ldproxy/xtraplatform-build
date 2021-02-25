package de.interactive_instruments.xtraplatform


import org.gradle.api.Plugin
import org.gradle.api.Project
import org.jetbrains.gradle.ext.ActionDelegationConfig
import org.jetbrains.gradle.ext.JUnit
import org.slf4j.LoggerFactory

/**
 * @author zahnen
 */
class CompositePlugin implements Plugin<Project> {

    static def LOGGER = LoggerFactory.getLogger(CompositePlugin.class)

    @Override
    void apply(Project project) {
        project.plugins.apply('org.jetbrains.gradle.plugin.idea-ext')

        project.with {
            idea.project.settings {
                runConfigurations {
                    defaults(JUnit) {
                        vmParameters = '-ea -Dlogback.configurationFile=logback-test.xml'
                    }
                }
                delegateActions {
                    delegateBuildRunToGradle = true
                    testRunner = ActionDelegationConfig.TestRunner.PLATFORM
                }
            }
        }

        def main = project.gradle.includedBuilds.find {project.rootProject.name.startsWith(it.name)}
        project.extensions.create('composite', CompositeExtension, main == null ? '' : main.name)

        project.afterEvaluate {
            if (project.composite.main == '') {
                throw new IllegalStateException("Could not determine main build, please set 'composite.main' in build.gradle.")
            } else {
                println "\nMain build: ${project.composite.main}"
                project.tasks.register('build', { dependsOn project.gradle.includedBuild(project.composite.main).task(':build') })
                project.tasks.register('assemble', { dependsOn project.gradle.includedBuild(project.composite.main).task(':assemble') })
                project.tasks.register('check', { dependsOn project.gradle.includedBuild(project.composite.main).task(':check') })
                project.tasks.register('test', { dependsOn project.gradle.includedBuild(project.composite.main).task(':test') })
                project.tasks.register('run', { dependsOn project.gradle.includedBuild(project.composite.main).task(':run') })
            }
        }
    }

    static class CompositeExtension {
        String main

        CompositeExtension(String main) {
            this.main = main
        }
    }
}
