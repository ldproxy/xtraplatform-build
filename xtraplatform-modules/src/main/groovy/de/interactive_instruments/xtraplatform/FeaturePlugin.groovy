package de.interactive_instruments.xtraplatform


import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.tasks.GenerateModuleMetadata
import org.gradle.api.tasks.bundling.Jar
import org.jetbrains.gradle.ext.ActionDelegationConfig
import org.jetbrains.gradle.ext.JUnit
import org.slf4j.LoggerFactory

/**
 * @author zahnen
 */
class FeaturePlugin implements Plugin<Project> {

    static def LOGGER = LoggerFactory.getLogger(FeaturePlugin.class)

    public static String XTRAPLATFORM_CORE = "xtraplatform-core"
    public static String XTRAPLATFORM_RUNTIME = "xtraplatform-runtime"
    public static String XTRAPLATFORM_BASE = "xtraplatform-base"

    @Override
    void apply(Project project) {
        project.plugins.apply("java") // needed for platform constraints
        project.plugins.apply("maven-publish")

        // consumed features
        project.configurations.create("feature")

        // consumed bundles
        project.configurations.create("featureBundles")

        //provided bundles
        project.configurations.create("bundle")

        project.configurations.runtimeElements.extendsFrom(project.configurations.bundle)
        project.configurations.runtimeElements.setTransitive(false)
        project.configurations.bundle.setTransitive(false)
        project.configurations.feature.setTransitive(true)
        project.configurations.featureBundles.setTransitive(true)
        project.configurations.feature.resolutionStrategy.cacheDynamicVersionsFor(5, 'minutes')
        project.configurations.featureBundles.resolutionStrategy.cacheDynamicVersionsFor(5, 'minutes')
        project.configurations.feature.resolutionStrategy.cacheChangingModulesFor(5, 'minutes')
        project.configurations.featureBundles.resolutionStrategy.cacheChangingModulesFor(5, 'minutes')

        project.repositories {
            mavenCentral()
            maven {
                url "https://dl.interactive-instruments.de/repository/maven-releases/"
            }
            maven {
                url "https://dl.interactive-instruments.de/repository/maven-snapshots/"
            }
            maven {
                url "https://s01.oss.sonatype.org/content/repositories/snapshots/"
            }
        }


        def includedBuilds = project.gradle.includedBuilds.collect { it.name }
        def parent = project.gradle.parent
        while (parent != null) {
            includedBuilds += parent.includedBuilds.collect { it.name }
            parent = parent.gradle.parent
        }

        addFeatureBundles(project, includedBuilds)

        addPublication(project)

        configureSubprojects(project, includedBuilds)

        project.plugins.apply(DocPlugin.class)
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
                //withModuleXml(project.sourceSets.main) { println it }
            }
        }
    }

    void addFeatureBundles(Project project, includedBuilds) {
        project.afterEvaluate {
            //println "INC " + includedBuilds
            project.configurations.feature.resolvedConfiguration.firstLevelModuleDependencies.collect().each {
                def isIncludedBuild = includedBuilds.contains(it.moduleName)

                if (!isIncludedBuild) {
                    //println "add bom " + it.moduleName + " to " + project.name
                    def bom = [group: it.moduleGroup, name: "${it.moduleName}", version: it.moduleVersion]

                    project.dependencies.add('featureBundles', project.dependencies.enforcedPlatform(bom))

                    //println "add bundles " + it.moduleName + "-bundles to " + project.name
                    def bundles = [group: it.moduleGroup, name: "${it.moduleName}-bundles", version: it.moduleVersion]

                    project.dependencies.add('featureBundles', bundles)
                } else {
                    //println "add included bundles " + it.moduleName + " to " + project.name
                    def bundles = [group: it.moduleGroup, name: "${it.moduleName}", version: it.moduleVersion]

                    project.dependencies.add('featureBundles', bundles)
                }

            }
        }
    }

    void configureSubprojects(Project project, includedBuilds) {

        project.tasks.register("testReportInfo") {
            doLast {
                println "\nSpock report: file://${project.buildDir}/reports/spock/index.html"
            }
        }

        project.subprojects { Project subproject ->

            subproject.plugins.apply('java-library')
            subproject.plugins.apply('java-test-fixtures')
            subproject.plugins.apply('maven-publish')

            ModulePlugin.setupConfigurations(subproject)
            ModuleInfoExtension moduleInfo = subproject.extensions.create('moduleInfo', ModuleInfoExtension)
            subproject.ext.notAModule = false

            subproject.afterEvaluate {
                if (moduleInfo.enabled) {
                    subproject.plugins.apply(ModulePlugin.class)
                } else {
                    println "NOT "+ subproject.name
                }
            }

            // stay java 11 compatible
            subproject.setSourceCompatibility(JavaVersion.VERSION_11)

            subproject.repositories {
                mavenCentral()
                maven {
                    url "https://dl.interactive-instruments.de/repository/maven-releases/"
                }
                maven {
                    url "https://dl.interactive-instruments.de/repository/maven-snapshots/"
                }
                maven {
                    url "https://s01.oss.sonatype.org/content/repositories/snapshots/"
                }
            }

            subproject.afterEvaluate {
                if (subproject.version != null && subproject.version != 'unspecified') {
                    LOGGER.warn("Warning: Module version '{}' is set for '{}'. Module versions are ignored, the feature version '{}' from '{}' is used instead.", subproject.version, subproject.name, project.version, project.name)
                }
                subproject.version = project.version
            }


            project.afterEvaluate {

                // add all bundles from all features with all transitive dependencies to provided
                project.configurations.featureBundles.resolvedConfiguration.firstLevelModuleDependencies.each({
                    it.children.each { bundle ->
                        subproject.dependencies.add('provided', bundle.name)
                    }
                })

                // special handling for xtraplatform-core bundles
                if (project.name == XTRAPLATFORM_CORE && subproject.name != XTRAPLATFORM_RUNTIME) {
                    def runtime = project.configurations.bundle.dependencies.find { it.name == XTRAPLATFORM_RUNTIME }

                    subproject.dependencies.add('provided', runtime)

                    if (subproject.name != XTRAPLATFORM_BASE) {
                        def base = project.subprojects.find { it.name == XTRAPLATFORM_BASE }

                        subproject.dependencies.add('provided', base)
                    }
                }

                subproject.extensions.publishing.with {
                    repositories {
                        maven {
                            def releasesRepoUrl = "https://dl.interactive-instruments.de/repository/maven-releases/"
                            def snapshotsRepoUrl = "https://dl.interactive-instruments.de/repository/maven-snapshots/"

                            url project.version.endsWith('SNAPSHOT') ? snapshotsRepoUrl : releasesRepoUrl
                            credentials {
                                username project.findProperty('deployUser') ?: ''
                                password project.findProperty('deployPassword') ?: ''
                            }
                        }
                    }
                    publications {
                        'default'(MavenPublication) {
                            from subproject.components.java
                            suppressPomMetadataWarningsFor('runtimeElements')
                            suppressPomMetadataWarningsFor('testFixturesApiElements')
                            suppressPomMetadataWarningsFor('testFixturesRuntimeElements')
                        }
                    }
                }
                subproject.tasks.withType(GenerateModuleMetadata).configureEach {
                    suppressedValidationErrors.add('enforced-platform')
                }
            }

            subproject.java {
                //TODO withJavadocJar()
                withSourcesJar()
            }
            subproject.tasks.named('sourcesJar') {
                dependsOn subproject.tasks.named('compileJava')
                duplicatesStrategy = DuplicatesStrategy.EXCLUDE
            }

//            subproject.tasks.register("dependencyUpdates", CustomDependencyUpdatesTask)
        }
    }

    void addPublication(Project project) {
        project.afterEvaluate {
            project.extensions.publishing.with {
                repositories {
                    maven {
                        def releasesRepoUrl = "https://dl.interactive-instruments.de/repository/maven-releases/"
                        def snapshotsRepoUrl = "https://dl.interactive-instruments.de/repository/maven-snapshots/"

                        url project.version.endsWith('SNAPSHOT') ? snapshotsRepoUrl : releasesRepoUrl
                        credentials {
                            username project.findProperty('deployUser') ?: ''
                            password project.findProperty('deployPassword') ?: ''
                        }
                    }
                }
                publications {
                    'default'(MavenPublication) {

                        pom.withXml {

                            def dependencyManagementNode = asNode().appendNode('dependencyManagement').appendNode('dependencies')

                            project.configurations.bundle.dependencies.each {
                                def dependencyNode = dependencyManagementNode.appendNode('dependency')
                                dependencyNode.appendNode('groupId', it.group)
                                dependencyNode.appendNode('artifactId', it.name)
                                dependencyNode.appendNode('version', it.version)
                                //dependencyNode.appendNode('scope', 'compile')
                            }

                        }
                    }
                    bundles(MavenPublication) {

                        artifactId "${project.name}-bundles"

                        pom.withXml {

                            def dependenciesNode = asNode().appendNode('dependencies')

                            /*project.configurations.feature.dependencies.each {
                                def dependencyNode = dependenciesNode.appendNode('dependency')
                                dependencyNode.appendNode('groupId', it.group)
                                dependencyNode.appendNode('artifactId', it.name)
                                dependencyNode.appendNode('version', it.version)
                                dependencyNode.appendNode('scope', 'runtime')
                            }*/

                            project.configurations.bundle.dependencies.each {
                                def dependencyNode = dependenciesNode.appendNode('dependency')
                                dependencyNode.appendNode('groupId', it.group)
                                dependencyNode.appendNode('artifactId', it.name)
                                dependencyNode.appendNode('version', it.version)
                                dependencyNode.appendNode('scope', 'runtime')
                            }

                        }

                    }
                }
            }
            project.tasks.withType(GenerateModuleMetadata).configureEach {
                suppressedValidationErrors.add('enforced-platform')
            }
        }
    }
}
