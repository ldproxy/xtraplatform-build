package de.interactive_instruments.xtraplatform


import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.slf4j.LoggerFactory

class BundlePlugin implements Plugin<Project> {

    static LOGGER = LoggerFactory.getLogger(BundlePlugin.class)

    void apply(Project project) {
        project.configurations.create('provided')
        project.configurations.create('embedded')
        project.configurations.create('embeddedExport')
        project.configurations.create('embeddedFlat')
        project.configurations.create('embeddedFlatExport')

        project.configurations.provided.setTransitive(true)
        project.configurations.embedded.setTransitive(true)
        project.configurations.embeddedExport.setTransitive(true)
        project.configurations.embeddedFlat.setTransitive(false)
        project.configurations.embeddedFlatExport.setTransitive(false)


        project.configurations.api.extendsFrom(project.configurations.provided, project.configurations.embeddedExport)
        project.configurations.implementation.extendsFrom(project.configurations.embedded)

        project.afterEvaluate {
            project.configurations.embeddedFlat.dependencies.each {
                project.dependencies.add('implementation', it, { transitive = false })
            }
            project.configurations.embeddedFlatExport.dependencies.each {
                project.dependencies.add('api', it, { transitive = false })
            }
        }

        addEmbeddingToJarTask(project);

        setupUnitTests(project);

        //TODO: adjust to module-info
        /*project.tasks.jar.doLast {
            def failed = false
            project.jar.manifest.effectiveManifest.attributes.get('Import-Package').tokenize(',').each {
                if (it.startsWith('de.ii.') || it.startsWith('de.interactive_instruments.')) {
                    if (!it.contains('.domain') && !it.contains('.api')) {
                        println "Invalid import in bundle ${project.name}: ${it}"
                        failed = true
                    }
                }
            }
            if (failed) {
                throw new GradleException("Invalid imports found, aborting build. Cross-bundle imports are only allowed from packages containing '.domain' or '.api'.")
            }
        }*/
    }

    void addEmbeddingToJarTask(Project project) {
        project.tasks.jar.doFirst {
            def doExport = true;// project.jar.manifest.instructions.get("Embed-Export") != null && project.jar.manifest.instructions.get("Embed-Export")[0] == "true";
            def doImport = false; //project.jar.manifest.instructions.get("Embed-Import") == null || project.jar.manifest.instructions.get("Embed-Import")[0] != "false";
            def excludes = null; //project.jar.manifest.instructions.get("Embed-Excludes")

            //if (embedInstruction != null) {
            def includedArtifacts = [] as Set
            def includedArtifactsExport = [] as Set

            // determine artifacts that should be included in the bundle, might be transitive or not
            def deps = Dependencies.getDependencies(project, 'embedded', excludes, true) + Dependencies.getDependencies(project, 'embeddedFlat', excludes, false)
            def depsExport = Dependencies.getDependencies(project, 'embeddedExport', excludes, true) + Dependencies.getDependencies(project, 'embeddedFlatExport', excludes, false)

            deps.each { dependency ->
                dependency.moduleArtifacts.each { artifact ->
                    includedArtifacts.add(artifact.file)
                }
            }
            depsExport.each { dependency ->
                dependency.moduleArtifacts.each { artifact ->
                    includedArtifactsExport.add(artifact.file)
                }
            }

            project.jar.manifest.attributes(["Bundle-ClassPath": '.']) //add the default classpath

            includedArtifacts.each { artifact ->
                project.jar.from(artifact)
                project.jar.manifest.attributes(["Bundle-ClassPath": artifact.name])
            }
            includedArtifactsExport.each { artifact ->
                project.jar.from(artifact)
                project.jar.manifest.attributes(["Bundle-ClassPath": artifact.name])
            }

            // determine all dependent artifacts to analyze packages to be imported
            if (doImport) {
                def requiredArtifacts = [] as Set
                def deps2 = Dependencies.getDependencies(project, 'embeddedExport', [], true) + Dependencies.getDependencies(project, 'embeddedFlatExport', [], false) + Dependencies.getDependencies(project, 'embedded', [], true) + Dependencies.getDependencies(project, 'embeddedFlat', [], false)

                deps2.each { dependency ->
                    dependency.moduleArtifacts.each { artifact ->
                        requiredArtifacts.add(artifact.file)
                    }
                }

                requiredArtifacts.each { artifact ->
                    // for bnd analysis
                    project.copy {
                        from artifact
                        into project.jar.manifest.classesDir
                    }
                }
            }

            // determine packages for export
            def pkgs = Dependencies.getPackages(deps)
            def pkgsExport = Dependencies.getPackages(depsExport)

            //if (doExport) {
            // export only direct dependencies
            // pkgs = getPackages(getDependencies(project, embedInstruction, false))
            pkgsExport.each { pkg ->
                project.jar.manifest.attributes(["Export-Package": "${pkg.name};version=${pkg.version.replaceAll('(-[\\w]+)+$', '')}"])
                project.jar.manifest.attributes(["Import-Package": "${pkg.name};version=${pkg.version.replaceAll('(-[\\w]+)+$', '')}"])
            }
            //} else {
            pkgs.each { pkg ->
                project.jar.manifest.attributes/*First*/(["Export-Package": "!${pkg.name}"])
                project.jar.manifest.attributes/*First*/(["Private-Package": "${pkg.name}"])
                //if (!doImport) {
                project.jar.manifest.attributes/*First*/(["Import-Package": "!${pkg.name}"])
                //}
            }
            //}

            project.jar.manifest.attributes(['Import-Package': "com.fasterxml.jackson.module.afterburner.ser"])

            project.jar.manifest.attributes(["Export-Package": "*.domain*,*.api*,!*"])
            project.jar.manifest.attributes(["Import-Package": "*"])

            project.jar.manifest.attributes(["-noee": "true"])
            //}
        }
    }

    void setupUnitTests(Project project) {
        project.plugins.apply('groovy')

        project.dependencies.add('testImplementation', "org.spockframework:spock-core:2.+")
        project.dependencies.add('testImplementation', "com.athaydes:spock-reports:2.+", { transitive = false })

        project.dependencies.add('testImplementation', "net.bytebuddy:byte-buddy:1.10.9")
        // needed by spock to mock non-interface types
        project.dependencies.add('testImplementation', "org.objenesis:objenesis:1.2")
        // needed by spock to mock constructors for non-interface types

        project.dependencies.add('testImplementation', "org.codehaus.groovy:groovy-templates:3.+")
        // needed by spock-reports
        project.dependencies.add('testImplementation', "org.codehaus.groovy:groovy-xml:3.+")
        // needed by spock-reports
        project.dependencies.add('testImplementation', "org.codehaus.groovy:groovy-json:3.+")
        // needed by spock-reports

        def testConfig = {
            useJUnitPlatform()

            testLogging.showStandardStreams = true
            reports {
                html.enabled false
                junitXml.enabled = false
            }

            //outputs.dir("${project.rootProject.buildDir}/reports/spock")

            systemProperty 'com.athaydes.spockframework.report.outputDir', "${project.rootProject.buildDir}/reports/spock"
            systemProperty 'com.athaydes.spockframework.report.showCodeBlocks', 'true'
            systemProperty 'com.athaydes.spockframework.report.projectName', project.rootProject.name
            systemProperty 'com.athaydes.spockframework.report.projectVersion', project.rootProject.version
            systemProperty 'com.athaydes.spockframework.report.projectVersion', project.rootProject.version
            systemProperty 'com.athaydes.spockframework.report.internal.HtmlReportCreator.printThrowableStackTrace', 'true'

            systemProperty 'logback.configurationFile', 'logback-test.xml'
            systemProperty 'spock.configuration', 'SpockConfig.groovy'

            finalizedBy project.rootProject.tasks.testReportInfo
        }

        project.tasks.test.with testConfig

        project.tasks.register("testSlow", Test) {
            with testConfig
            systemProperty 'spock.include.Slow', 'true'
        }

        /*tasks.withType(Test).configureEach { testTask ->
        testTask.configure {
            useJUnitPlatform()

            afterSuite { desc, result ->
                if (!desc.parent) {
                    if (result.testCount == 0) {
                        throw new IllegalStateException("No tests were found. Failing the build")
                    }
                }
            }
        }
    }*/
    }

}
