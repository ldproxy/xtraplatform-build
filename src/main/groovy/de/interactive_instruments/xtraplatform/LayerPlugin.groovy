package de.interactive_instruments.xtraplatform

import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.file.FileCopyDetails
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.tasks.GenerateModuleMetadata
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.plugins.ide.idea.model.IdeaProject
import org.jetbrains.gradle.ext.ActionDelegationConfig
import org.jetbrains.gradle.ext.Gradle
import org.jetbrains.gradle.ext.JUnit
import org.jetbrains.gradle.ext.Remote
import org.slf4j.LoggerFactory

import java.util.regex.Pattern

/**
 * @author zahnen
 */
class LayerPlugin implements Plugin<Project> {

    static def LOGGER = LoggerFactory.getLogger(LayerPlugin.class)

    static def JAVA_VERSION = JavaVersion.VERSION_17
    static def JAVA_VERSION_MAJOR = Integer.parseInt(JAVA_VERSION.majorVersion)

    public static String XTRAPLATFORM = "xtraplatform"
    public static String XTRAPLATFORM_BUILD = "xtraplatform-build"
    public static String XTRAPLATFORM_CORE = "xtraplatform-core"
    public static String XTRAPLATFORM_RUNTIME = "xtraplatform-runtime"
    public static String XTRAPLATFORM_BASE = "xtraplatform-base"

    @Override
    void apply(Project project) {
        if (project.logger.isInfoEnabled()) {
            project.logger.info("Applying LayerPlugin {} to {}", ApplicationPlugin.getVersion(project), project.name)
        }

        project.plugins.apply("java") // needed for platform constraints
        project.plugins.apply("maven-publish")
        project.plugins.apply('version-catalog')
        project.plugins.apply('com.google.osdetector')
        project.plugins.apply('org.cyclonedx.bom')

        def isBranch = project.hasProperty('branch')
        def isMainBranch = isBranch && (project.getProperty('branch') == 'master' || project.getProperty('branch') == 'main')
        def isRelease = project.findProperty('release') == 'true'
        def isDocker = project.findProperty('runInDocker') == 'true'
        def isExtSuffix = project.hasProperty('versionSuffix')
        project.ext {
            versionSuffix = (isExtSuffix ? project.getProperty('versionSuffix') : '') + (isBranch && !isMainBranch && !isRelease ? ('-' + project.getProperty('branch')) : '') + (!isRelease ? '-SNAPSHOT' : '')
            platform = project.findProperty('platform') ?: project.osdetector.classifier.replace('x86_64', 'amd64').replace('aarch_64', 'arm64').replace('osx', isDocker ? 'linux' : 'osx')
        }

        // consumed layers
        project.configurations.create("layers")

        // consumed modules
        project.configurations.create("layerModules")

        // consumed docs
        project.configurations.create("layerDocs")

        //provided modules
        project.configurations.create("modules")

        project.configurations.runtimeElements.extendsFrom(project.configurations.modules)
        project.configurations.runtimeElements.setTransitive(false)
        project.configurations.modules.setTransitive(false)
        project.configurations.layers.setTransitive(true)
        project.configurations.layerModules.setTransitive(true)
        project.configurations.layerDocs.setTransitive(false)
        project.configurations.layers.resolutionStrategy.cacheDynamicVersionsFor(5, 'minutes')
        project.configurations.layerModules.resolutionStrategy.cacheDynamicVersionsFor(5, 'minutes')
        project.configurations.layerDocs.resolutionStrategy.cacheDynamicVersionsFor(5, 'minutes')
        project.configurations.layers.resolutionStrategy.cacheChangingModulesFor(5, 'minutes')
        project.configurations.layerModules.resolutionStrategy.cacheChangingModulesFor(5, 'minutes')
        project.configurations.layerDocs.resolutionStrategy.cacheChangingModulesFor(5, 'minutes')

        def includedBuilds = CompositePlugin.getIncludedBuildNames(project)

        addFeatureModules(project, includedBuilds)

        addPublication(project)

        configureSubprojects(project, includedBuilds)

        project.plugins.apply(de.interactive_instruments.xtraplatform.docs.DocPlugin.class)
        project.plugins.apply('org.jetbrains.gradle.plugin.idea-ext')

        project.tasks.register('initTpl') {
            dependsOn project.subprojects*.tasks*.matching({it.name == 'initTpl'})
            /*doLast {
                println "INIT ${project.name} ${project.version}"
            }*/
        }

        project.with {
            idea.project.settings {
                runConfigurations {
                    defaults(JUnit) {
                        vmParameters = '-ea -Dlogback.configurationFile=logback-test.xml'
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


        project.extensions.create('layer', LayerMaturityExtension)

        project.tasks.register("modules") {
            doLast {
                println "\nLayer ${project.name} ${project.version}"
                project.subprojects.each {
                    printf("+---- %-40s %-10s %-5s %s%n", it.name, it.maturity,  it.maintenance, it.deprecated ? " DEPRECATED" : "");
                }
            }
        }
        project.tasks.register("createModule", ModuleCreateTask)

        project.plugins.apply('build-dashboard')

        project.tasks.check.finalizedBy project.tasks.named("buildDashboard")

        project.cyclonedxBom {
            destination = project.file("build/generated/sources/xtraplatform/resources/main/")
            includeConfigs = ["embedded", "embeddedExport", "embeddedFlat", "embeddedFlatExport"]
            projectType = "library"
            outputName = "sbom"
            outputFormat = "json"
            includeBomSerialNumber = false
        }

        project.tasks.named("processResources").configure {
            dependsOn project.tasks.named("cyclonedxBom")
            from project.tasks.named("cyclonedxBom")
        }

        applyFormatting(project)
    }

    static void applyFormatting(Project project) {
        project.subprojects { Project subproject ->
            if (subproject.name.endsWith("-tpl")) {
                return
            }

            subproject.plugins.apply('com.diffplug.spotless')
            subproject.with {
                spotless {
                    // optional: limit format enforcement to just the files changed by this feature branch
                    //ratchetFrom 'origin/master'

                    /*format 'misc', {
        // define the files to apply `misc` to
        target '*.gradle', '*.md', '.gitignore'

        // define the steps to apply to those files
        trimTrailingWhitespace()
        indentWithSpaces() // or spaces. Takes an integer argument if you don't like 4
        endWithNewline()
      }*/

                    java {
                        //target '**/ConfigurationReader.java'
                        targetExclude '**/build/generated/**/*'

                        googleJavaFormat('1.18.1')

                        bumpThisNumberIfACustomStepChanges(2)
                        custom("errorOnWildcard", {
                            def matcher = Pattern.compile("^(.*?)\\.\\*;\$", Pattern.MULTILINE).matcher(it)
                            if (matcher.find()) {
                                throw new WildcardError(matcher.group())
                            }
                            return it
                        })

                        // make sure every file has the following copyright header.
                        licenseHeader '''/*
 * Copyright $YEAR interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
'''
                    }
                }
            }
        }
    }

    void addFeatureModules(Project project, includedBuilds) {
        project.subprojects.each {
            if (project.rootProject.extensions.xtraplatformLayers.getExcludedModules().contains(it.name)) {
                return
            }
            project.dependencies.add('modules', it)
        }

        //println project.rootProject.extensions.xtraplatformLayers.allLayers
        project.rootProject.extensions.xtraplatformLayers.getAllLayers().each {
            //println "LAYER " + it
            project.dependencies.add('layers', it)
        }

        project.afterEvaluate {
            //println "INC " + includedBuilds
            project.configurations.layers.resolvedConfiguration.firstLevelModuleDependencies.collect().each {
                def isIncludedBuild = includedBuilds.contains(it.moduleName)

                if (!isIncludedBuild) {
                    //println "add bom " + it.moduleName + " to " + project.name
                    /*def bom = [group: it.moduleGroup, name: "${it.moduleName}", version: it.moduleVersion]

                    project.dependencies.add('layerModules', project.dependencies.enforcedPlatform(bom))
                    */
                    //println "add modules " + it.moduleName + "-modules to " + project.name
                    def modules = [group: it.moduleGroup, name: "${it.moduleName}-modules", version: it.moduleVersion]

                    project.dependencies.add('layerModules', modules)

                    def docs = [group: it.moduleGroup, name: "${it.moduleName}-docs", version: it.moduleVersion]

                    project.dependencies.add('layerDocs', docs)
                } else {
                    //println "add included modules " + it.moduleName + " to " + project.name
                    def modules = [group: it.moduleGroup, name: "${it.moduleName}", version: it.moduleVersion]

                    project.dependencies.add('layerModules', modules)
                    project.dependencies.add('layerDocs', modules)
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
            if (subproject.name.endsWith("-tpl")) {
                subproject.afterEvaluate {
                    subproject.group = project.group
                    subproject.version = project.version
                }

                return
            }

            subproject.plugins.apply('java-library')
            subproject.plugins.apply('java-test-fixtures')
            subproject.plugins.apply('maven-publish')

            subproject.tasks.withType(GenerateModuleMetadata) {
                enabled = false
            }

            ModulePlugin.setupConfigurations(subproject)
            ModuleInfoExtension moduleInfo = subproject.extensions.create('moduleInfo', ModuleInfoExtension)
            subproject.ext.notAModule = false
            subproject.ext.maturity = Maturity.PROPOSAL.name()
            subproject.ext.maintenance = Maintenance.LOW.name()
            subproject.ext.deprecated = false
            subproject.ext.docIgnore = false
            subproject.ext.descriptionDe = null
            subproject.ext.replacementFor = null

            subproject.afterEvaluate {
                if (moduleInfo.enabled) {
                    subproject.plugins.apply(ModulePlugin.class)
                }
            }

            subproject.java {
                sourceCompatibility = JAVA_VERSION
            }
            subproject.tasks.withType(JavaCompile).configureEach {
                options.release = JAVA_VERSION_MAJOR
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

                def maintenance
                try {
                    maintenance = subproject.maintenance as Maintenance
                } catch (Throwable e) {
                    throw new IllegalArgumentException("Invalid maintenance '${subproject.maintenance}' (valid values: ${Maintenance.values()})")
                }

                subproject.jar.manifest {
                    attributes("Maturity": maturity.toString(),
                            "Maintenance": maintenance.toString())
                    if (Objects.nonNull(subproject.replacementFor)) {
                        attributes("Replacement-For": subproject.replacementFor)
                    }
                }
            }


            project.afterEvaluate {

                // add all modules from all layers with all transitive dependencies to provided
                project.configurations.layerModules.resolvedConfiguration.firstLevelModuleDependencies.each({
                    it.children.each { module ->
                        if (module.moduleName == XTRAPLATFORM_RUNTIME) {
                            subproject.dependencies.add('provided', module.name)
                        }
                        if (module.moduleName == XTRAPLATFORM_BASE) {
                            subproject.dependencies.add('provided', module.name)
                        }
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

                subproject.dependencies.add('compileOnly', [group: 'de.interactive_instruments', name: 'xtraplatform-build', version: ApplicationPlugin.getVersion(project)], {
                    transitive = false
                    capabilities {
                        requireCapability("de.interactive_instruments:xtraplatform-build-annotations")
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
        }
    }

    void addPublication(Project project) {
        project.afterEvaluate {
            project.catalog {
                versionCatalog {
                    version(project.name, project.version)

                    project.subprojects.each { subproject ->
                        if (subproject.name.endsWith("-tpl")) {
                            return
                        }
                        library(subproject.name, subproject.group, subproject.name).versionRef(project.name)
                    }
                }
            }

            Common.addPublishingRepos(project)

            project.extensions.publishing.with {
                publications {
                    'default'(MavenPublication) {
                        from project.components.versionCatalog
                    }
                    modules(MavenPublication) {
                        artifactId "${project.name}-modules"
                        pom.withXml {
                            def dependenciesNode = asNode().appendNode('dependencies')

                            project.configurations.modules.dependencies.each {
                                def dependencyNode = dependenciesNode.appendNode('dependency')
                                dependencyNode.appendNode('groupId', it.group)
                                dependencyNode.appendNode('artifactId', it.name)
                                dependencyNode.appendNode('version', it.version)
                                dependencyNode.appendNode('scope', 'runtime')
                            }
                        }
                    }
                    docs(MavenPublication) {
                        from project.components.java
                        artifactId "${project.name}-docs"
                    }
                }
            }
        }
    }
}
