package de.interactive_instruments.xtraplatform

import org.gradle.api.Plugin
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.initialization.Settings
import org.gradle.api.initialization.resolve.RepositoriesMode

/**
 * @author zahnen
 */
class SettingsPlugin implements Plugin<Settings> {

    @Override
    void apply(Settings settings) {

        settings.plugins.apply('org.danilopianini.gradle-pre-commit-git-hooks')

        def version = getVersion(settings)
        System.out.println("Applying SettingsPlugin ${version}")

        settings.gradle.beforeProject { project ->
            if (project.rootProject != project) {
                return
            }

            project.buildscript.with {
                dependencies {
                    classpath "de.interactive_instruments:xtraplatform-build:${version}"
                }
            }
        }

        settings.with {
            pluginManagement {
                repositories {
                    gradlePluginPortal()
                    mavenCentral()
                    maven {
                        url "https://dl.interactive-instruments.de/repository/maven-releases/"
                    }
                    maven {
                        url "https://dl.interactive-instruments.de/repository/maven-snapshots/"
                    }
                }

                plugins {
                    id "de.interactive_instruments.xtraplatform-layer" version "${version}"
                    id "de.interactive_instruments.xtraplatform-application" version "${version}"
                    id "de.interactive_instruments.xtraplatform-module" version "${version}"
                    id "de.interactive_instruments.xtraplatform-doc" version "${version}"
                    id "de.interactive_instruments.xtraplatform-composite" version "${version}"
                }
            }

            dependencyResolutionManagement {
                repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
                repositories {
                    mavenCentral()
                    maven {
                        url "https://dl.interactive-instruments.de/repository/maven-releases/"
                    }
                    maven {
                        url "https://dl.interactive-instruments.de/repository/maven-snapshots/"
                    }
                    // dagger-auto snapshots
                    maven {
                        url "https://s01.oss.sonatype.org/content/repositories/snapshots/"
                    }
                    // Declare the Node.js download repository needed by plugin com.github.node-gradle.node
                    ivy {
                        name = "Node.js"
                        setUrl("https://nodejs.org/dist/")
                        patternLayout {
                            artifact("v[revision]/[artifact](-v[revision]-[classifier]).[ext]")
                        }
                        metadataSources {
                            artifact()
                        }
                        content {
                            includeModule("org.nodejs", "node")
                        }
                    }
                }
                versionCatalogs {
                    xtraplatform {
                        from("de.interactive_instruments:xtraplatform-catalog:${version}")
                    }
                }
            }

            extensions.gitHooks.with {
                preCommit { ctx ->
                    ctx.tasks('check', [].toArray(), true)
                }
                createHooks(false)
            }
        }
    }

    static String getVersion(Settings settings) {
        final Configuration classpath = settings.getBuildscript().getConfigurations().getByName("classpath");
        return classpath.getResolvedConfiguration().getResolvedArtifacts().stream()
                .map(artifact -> artifact.getModuleVersion().getId())
                .filter(id -> "de.interactive_instruments".equals(id.getGroup()) && id.getName().startsWith("xtraplatform-"))
                .findAny()
                .map(ModuleVersionIdentifier::getVersion)
                .orElse("+");
    }

}
