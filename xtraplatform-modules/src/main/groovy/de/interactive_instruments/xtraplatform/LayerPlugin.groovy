package de.interactive_instruments.xtraplatform

import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.file.FileCopyDetails
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.tasks.GenerateModuleMetadata
import org.gradle.api.tasks.Copy
import org.jetbrains.gradle.ext.ActionDelegationConfig
import org.jetbrains.gradle.ext.JUnit
import org.slf4j.LoggerFactory

/**
 * @author zahnen
 */
class LayerPlugin implements Plugin<Project> {

    static def LOGGER = LoggerFactory.getLogger(LayerPlugin.class)


    public static String XTRAPLATFORM_CORE = "xtraplatform-core"
    public static String XTRAPLATFORM_RUNTIME = "xtraplatform-runtime"
    public static String XTRAPLATFORM_BASE = "xtraplatform-base"

    @Override
    void apply(Project project) {
        project.plugins.apply("java") // needed for platform constraints
        project.plugins.apply("maven-publish")

        // consumed layers
        project.configurations.create("layers")

        // consumed modules
        project.configurations.create("layerModules")

        //provided modules
        project.configurations.create("modules")

        project.configurations.runtimeElements.extendsFrom(project.configurations.modules)
        project.configurations.runtimeElements.setTransitive(false)
        project.configurations.modules.setTransitive(false)
        project.configurations.layers.setTransitive(true)
        project.configurations.layerModules.setTransitive(true)
        project.configurations.layers.resolutionStrategy.cacheDynamicVersionsFor(5, 'minutes')
        project.configurations.layerModules.resolutionStrategy.cacheDynamicVersionsFor(5, 'minutes')
        project.configurations.layers.resolutionStrategy.cacheChangingModulesFor(5, 'minutes')
        project.configurations.layerModules.resolutionStrategy.cacheChangingModulesFor(5, 'minutes')

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

        addFeatureModules(project, includedBuilds)

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


        project.extensions.create('layer', LayerMaturityExtension)

        project.tasks.register("modules") {
            doLast {
                println "\nLayer ${project.name} ${project.version}"
                project.subprojects.each {
                    println "+---- ${it.name} ${it.maturity}${it.deprecated ? " DEPRECATED" : ""}"
                }
            }
        }
        project.tasks.register("createModule", ModuleCreateTask)

        project.plugins.apply('build-dashboard')

        project.tasks.check.finalizedBy project.tasks.named("buildDashboard")
    }

    void addFeatureModules(Project project, includedBuilds) {
        project.afterEvaluate {
            //println "INC " + includedBuilds
            project.configurations.layers.resolvedConfiguration.firstLevelModuleDependencies.collect().each {
                def isIncludedBuild = includedBuilds.contains(it.moduleName)

                if (!isIncludedBuild) {
                    //println "add bom " + it.moduleName + " to " + project.name
                    def bom = [group: it.moduleGroup, name: "${it.moduleName}", version: it.moduleVersion]

                    project.dependencies.add('layerModules', project.dependencies.enforcedPlatform(bom))

                    //println "add modules " + it.moduleName + "-modules to " + project.name
                    def modules = [group: it.moduleGroup, name: "${it.moduleName}-modules", version: it.moduleVersion]

                    project.dependencies.add('layerModules', modules)
                } else {
                    //println "add included modules " + it.moduleName + " to " + project.name
                    def modules = [group: it.moduleGroup, name: "${it.moduleName}", version: it.moduleVersion]

                    project.dependencies.add('layerModules', modules)
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

        project.tasks.register('pmdInit', Copy) {
            from(project.zipTree(LayerPlugin.class.getResource("").file.split('!')[0])) {
                include "/pmd/*"
            }
            eachFile { FileCopyDetails fcd ->
                int slashIndex = fcd.path.indexOf('/', 1)
                fcd.path = fcd.path.substring(slashIndex+1)
            }
            includeEmptyDirs = false
            into new File(project.buildDir, 'pmd')
        }

        project.subprojects { Project subproject ->

            subproject.plugins.apply('java-library')
            subproject.plugins.apply('java-test-fixtures')
            subproject.plugins.apply('maven-publish')

            ModulePlugin.setupConfigurations(subproject)
            ModuleInfoExtension moduleInfo = subproject.extensions.create('moduleInfo', ModuleInfoExtension)
            subproject.ext.notAModule = false
            subproject.ext.maturity = Maturity.EXPERIMENTAL.name()
            subproject.ext.deprecated = false

            subproject.afterEvaluate {
                if (moduleInfo.enabled) {
                    subproject.plugins.apply(ModulePlugin.class)
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
                    LOGGER.warn("Warning: Module version '{}' is set for '{}'. Module versions are ignored, the layer version '{}' from '{}' is used instead.", subproject.version, subproject.name, project.version, project.name)
                }
                subproject.version = project.version

                def maturity
                try {
                    maturity = subproject.maturity as Maturity
                } catch (Throwable e) {
                    throw new IllegalArgumentException("Invalid maturity '${subproject.maturity}' (valid values: ${Maturity.values()})")
                }
                if (!project.layer.isValid(maturity)) {
                    throw new IllegalArgumentException("Invalid maturity '${subproject.maturity}' (minimum required for this layer: ${project.layer.minimumModuleMaturity})")
                }
            }


            project.afterEvaluate {

                // add all modules from all layers with all transitive dependencies to provided
                project.configurations.layerModules.resolvedConfiguration.firstLevelModuleDependencies.each({
                    it.children.each { module ->
                        subproject.dependencies.add('provided', module.name)
                    }
                })

                // special handling for xtraplatform-core modules
                if (project.name == XTRAPLATFORM_CORE && subproject.name != XTRAPLATFORM_RUNTIME) {
                    def runtime = project.configurations.modules.dependencies.find { it.name == XTRAPLATFORM_RUNTIME }

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

                subproject.dependencies.add('compileOnly', [group: 'de.interactive_instruments', name: 'xtraplatform-modules', version: '+'], {
                    transitive = false
                    capabilities {
                        requireCapability("de.interactive_instruments:xtraplatform-modules-annotations")
                    }
                })
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

                            project.configurations.modules.dependencies.each {
                                def dependencyNode = dependencyManagementNode.appendNode('dependency')
                                dependencyNode.appendNode('groupId', it.group)
                                dependencyNode.appendNode('artifactId', it.name)
                                dependencyNode.appendNode('version', it.version)
                                //dependencyNode.appendNode('scope', 'compile')
                            }

                        }
                    }
                    modules(MavenPublication) {

                        artifactId "${project.name}-modules"

                        pom.withXml {

                            def dependenciesNode = asNode().appendNode('dependencies')

                            /*project.configurations.layers.dependencies.each {
                                def dependencyNode = dependenciesNode.appendNode('dependency')
                                dependencyNode.appendNode('groupId', it.group)
                                dependencyNode.appendNode('artifactId', it.name)
                                dependencyNode.appendNode('version', it.version)
                                dependencyNode.appendNode('scope', 'runtime')
                            }*/

                            project.configurations.modules.dependencies.each {
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
