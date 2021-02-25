package de.interactive_instruments.xtraplatform


import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.publish.maven.MavenPublication
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
            jcenter()
            maven {
                url "https://dl.bintray.com/iide/maven"
            }
        }


        def includedBuilds = project.gradle.includedBuilds.collect {it.name}
        def parent = project.gradle.parent
        while (parent != null) {
            includedBuilds += parent.includedBuilds.collect {it.name}
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
            subproject.plugins.apply('maven-publish')
            if (subproject.name == XTRAPLATFORM_RUNTIME) {
                subproject.plugins.apply(RuntimePlugin.class)
            } else {
                subproject.plugins.apply(BundlePlugin.class)
            }

            // stay java 8 compatible
            subproject.setSourceCompatibility(JavaVersion.VERSION_1_8)

            subproject.repositories {
                jcenter()
                maven {
                    url "https://dl.bintray.com/iide/maven"
                }
            }

            subproject.configurations.all {
                exclude group: 'org.osgi', module: 'org.osgi.core'
                exclude group: 'org.osgi', module: 'org.osgi.compendium'
            }

            subproject.afterEvaluate {
                if (subproject.version != null && subproject.version  != 'unspecified') {
                    LOGGER.warn("Warning: Bundle version '{}' is set for '{}'. Bundle versions are ignored, the feature version '{}' from '{}' is used instead.", subproject.version, subproject.name, project.version, project.name)
                }
                subproject.version = project.version
            }

            // apply feature boms
            project.configurations.feature.incoming.beforeResolve {
                project.configurations.feature.dependencies.collect().each {
                    def isIncludedBuild = includedBuilds.contains(it.name)
                    if (!isIncludedBuild) {
                        def bom = [group: it.group, name: "${it.name}", version: it.version]

                        subproject.dependencies.add('provided', subproject.dependencies.enforcedPlatform(bom))
                    }
                }
            }

            project.afterEvaluate {
                // add all bundles from all features with all transitive dependencies to compileOnly
                project.configurations.featureBundles.resolvedConfiguration.firstLevelModuleDependencies.each({
                        it.children.each { bundle ->
                            subproject.dependencies.add('compileOnly', bundle.name)
                            subproject.dependencies.add('testImplementation', bundle.name)
                        }
                })

                // special handling for xtraplatform-core bundles
                if (project.name == XTRAPLATFORM_CORE && subproject.name != XTRAPLATFORM_RUNTIME) {
                    def runtime = project.subprojects.find {it.name == XTRAPLATFORM_RUNTIME}

                    subproject.dependencies.add('compileOnly', runtime)
                    subproject.dependencies.add('testImplementation', runtime)

                    // add all bundles from xtraplatform-core with all transitive dependencies to compileOnly
                    project.configurations.bundle.resolvedConfiguration.firstLevelModuleDependencies.each({ bundle ->
                        if (bundle.moduleName.startsWith('org.apache.felix.ipojo')) {
                            subproject.dependencies.add('compileOnly', bundle.name)
                            subproject.dependencies.add('testImplementation', bundle.name)
                        }
                    })
                }
            }

            subproject.task('sourceJar', type: Jar) {
                from sourceSets.main.allSource
            }

            subproject.extensions.publishing.with {
                publications {
                    'default'(MavenPublication) {
                        from subproject.components.java

                        artifact sourceJar {
                            classifier "sources"
                        }
                    }
                }
            }
        }
    }

    void addPublication(Project project) {
        project.extensions.publishing.with {
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
    }
}
