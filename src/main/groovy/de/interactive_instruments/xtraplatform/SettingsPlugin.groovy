package de.interactive_instruments.xtraplatform

import org.gradle.api.Plugin
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.initialization.ProjectDescriptor
import org.gradle.api.initialization.Settings
import org.gradle.api.initialization.dsl.VersionCatalogBuilder
import org.gradle.api.initialization.resolve.RepositoriesMode
import org.gradle.api.logging.Logging

/**
 * @author zahnen
 */
class SettingsPlugin implements Plugin<Settings> {

    private static final LOGGER = Logging.getLogger(SettingsPlugin.class)

    @Override
    void apply(Settings settings) {
        settings.plugins.apply('org.danilopianini.gradle-pre-commit-git-hooks')

        def version = getVersion(settings)
        LOGGER.info("Applying SettingsPlugin ${version}")

        settings.extensions.add("xtraplatform", XtraplatformExtension)
        XtraplatformExtension xtraplatformExt = settings.extensions.getByType(XtraplatformExtension)

        boolean noCache = settings.getProviders().gradleProperty('noCache').isPresent()
        boolean forceCache = settings.getProviders().gradleProperty('forceCache').isPresent()
        boolean isCI = System.getenv().containsKey("CI")

        settings.buildCache.local.enabled = !noCache && (!isCI || forceCache)

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

        settings.gradle.settingsEvaluated {
            settings.with {
                subprojects(rootProject, rootDir, true) { String name ->
                    if (xtraplatformExt.getExcludedModules().contains(name)) {
                        include name
                        return
                    }

                    include name, "${name}:tpl"

                    def tplDir = new File(rootDir, "${name}/build/tpl")
                    try {
                        tplDir.mkdirs()
                    } catch (Exception e) {
                        LOGGER.error("Failed to create directory for ${name}", e)
                    }

                    def tpl = project(":${name}:tpl")
                    tpl.projectDir = tplDir
                    tpl.name = "${name}-tpl"

                    return
                }

                def isDocker = it.getProviders().gradleProperty('runInDocker').getOrElse("") == 'true'
                def os = XtraplatformExtension.detectOs().replace('osx', isDocker ? 'linux' : 'osx')
                def platform = it.getProviders().gradleProperty('platform').getOrElse(os)

                xtraplatformExt.getIncLayers(platform).each { layer ->
                    LOGGER.info("Included layer ${layer.group}:${layer.name}")

                    settings.includeBuild("${layer.path}${layer.dir ?: layer.name}")

                    dependencyResolutionManagement.versionCatalogs.create("${layer.name.replaceAll('-', '')}") { VersionCatalogBuilder vc ->
                        vc.version(layer.name, '+')

                        subprojects(rootProject, rootDir, false) { String name ->
                            vc.library(name, 'de.interactive_instruments', name).versionRef(layer.name)
                        }
                    }
                }

                xtraplatformExt.getExtLayers(platform).each { layer ->
                    LOGGER.info("External layer ${layer.group}:${layer.name}")

                    dependencyResolutionManagement.versionCatalogs.create("${layer.name.replaceAll('-', '')}") {
                        from(layer)
                    }
                }

                if (xtraplatformExt.isUseMavenLocal() || settings.hasProperty('useMavenLocal')) {
                    def ml = dependencyResolutionManagement.repositories.mavenLocal()
                    dependencyResolutionManagement.repositories.remove(ml)
                    dependencyResolutionManagement.repositories.add(0, ml)
                }

                gradle.beforeProject { project ->
                    if (project.rootProject != project) {
                        return
                    }
                    project.extensions.add("xtraplatformLayers", xtraplatformExt)
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

    static void subprojects(ProjectDescriptor rootProject, File rootDir, boolean doLog, Closure<Void> with) {
        def prefix = rootProject.name.contains('-')
                ? rootProject.name.substring(0, rootProject.name.indexOf('-') + 1)
                : "${rootProject.name}-"

        if (doLog) {
            LOGGER.info("Loading projects with prefix '${prefix}' for ${rootProject.name}")
        }

        rootDir.listFiles().each { file ->
            if (file.isDirectory() && file.name.startsWith(prefix)) {
                with(file.name)

                if (doLog) {
                    LOGGER.info("  - ${file.name}")
                }
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
