package de.interactive_instruments.xtraplatform

import groovy.io.FileType
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.internal.artifacts.dependencies.DefaultExternalModuleDependency
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.tasks.Jar

import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Collectors
import java.util.stream.StreamSupport

class ModulePlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {
        ModuleInfoExtension moduleInfo = project.extensions.create('moduleInfo', ModuleInfoExtension)

        def isIntelliJ = System.getProperty("idea.active") == "true"

        setupConfigurations(project)

        setupAnnotationProcessors(project)

        setupUnitTests(project)

        project.afterEvaluate {
            moduleInfo.name = getModuleName(project.group as String, project.name)

            setupEmbedding(project, moduleInfo, isIntelliJ)

            setupModuleInfo(project, moduleInfo, isIntelliJ)
        }
    }

    static void setupConfigurations(Project project) {
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

        project.configurations.compileOnly.extendsFrom(project.configurations.provided)
        project.configurations.testImplementation.extendsFrom(project.configurations.provided)
        project.configurations.testFixturesImplementation.extendsFrom(project.configurations.provided)
    }

    static void setupEmbedding(Project project, ModuleInfoExtension moduleInfo, boolean isIntelliJ) {
        def embeddedClassesDir = isIntelliJ
                ? new File(project.buildDir, 'classes/java/intellij')
                : new File(project.buildDir, 'classes/java/main')
        def embeddedClassesDirOther = isIntelliJ
                ? new File(project.buildDir, 'classes/java/main')
                : new File(project.buildDir, 'classes/java/intellij')
        def embeddedResourcesDir = isIntelliJ
                ? new File(project.buildDir, 'generated/sources/annotationProcessor/resources/intellij')
                : new File(project.buildDir, 'generated/sources/annotationProcessor/resources/main')
        def embeddedResourcesDirOther = isIntelliJ
                ? new File(project.buildDir, 'generated/sources/annotationProcessor/resources/main')
                : new File(project.buildDir, 'generated/sources/annotationProcessor/resources/intellij')
        File generatedSourcesDir = isIntelliJ
                ? new File(project.buildDir, 'generated/sources/annotationProcessor/java/intellij')
                : new File(project.buildDir, 'generated/sources/annotationProcessor/java/main')

        project.tasks.register('embedClean', Delete) {
            inputs.property('isIntelliJ', isIntelliJ)
            outputs.upToDateWhen { true }
            delete embeddedClassesDirOther
            delete embeddedResourcesDirOther
        }

        project.tasks.register('embedClasses', Copy) {
            inputs.property('isIntelliJ', isIntelliJ)
            dependsOn project.tasks.named('embedClean')
            from {
                project.configurations.embedded.collect { it.isDirectory() ? it : project.zipTree(it) }
            }
            from {
                project.configurations.embeddedExport.collect { it.isDirectory() ? it : project.zipTree(it) }
            }
            from {
                project.configurations.embeddedFlat.collect { it.isDirectory() ? it : project.zipTree(it) }
            }
            from {
                project.configurations.embeddedFlatExport.collect { it.isDirectory() ? it : project.zipTree(it) }
            }
            include '**/*.class'
            exclude('**/module-info.class')
            into embeddedClassesDir
            duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        }

        project.tasks.register('embedResources', Copy) {
            inputs.property('isIntelliJ', isIntelliJ)
            from {
                project.configurations.embedded.collect { it.isDirectory() ? it : project.zipTree(it) }
            }
            from {
                project.configurations.embeddedExport.collect { it.isDirectory() ? it : project.zipTree(it) }
            }
            from {
                project.configurations.embeddedFlat.collect { it.isDirectory() ? it : project.zipTree(it) }
            }
            from {
                project.configurations.embeddedFlatExport.collect { it.isDirectory() ? it : project.zipTree(it) }
            }
            exclude '**/*.class'
            exclude('META-INF/MANIFEST.MF', 'META-INF/INDEX.LIST', 'META-INF/*.SF', 'META-INF/*.DSA', 'META-INF/*.RSA', "META-INF/services/*")
            into embeddedResourcesDir
            duplicatesStrategy = DuplicatesStrategy.EXCLUDE
            finalizedBy project.tasks.named('embedServices')
        }

        project.tasks.register('embedServices') {
            inputs.property('isIntelliJ', isIntelliJ)
            inputs.files(project.configurations.embedded)
            inputs.files(project.configurations.embeddedExport)
            inputs.files(project.configurations.embeddedFlat)
            inputs.files(project.configurations.embeddedFlatExport)
            outputs.dir(new File(embeddedResourcesDir, 'META-INF/services'))
            doLast {
                Map<String, Set<String>> services = new LinkedHashMap<>();
                def dir = new File(embeddedResourcesDir, 'META-INF/services')
                Files.createDirectories(dir.toPath())
                (project.configurations.embedded + project.configurations.embeddedExport + project.configurations.embeddedFlat + project.configurations.embeddedFlatExport)
                        .filter { it.isFile() }
                        .collect { project.zipTree(it).matching { it2 -> it2.include("META-INF/services/*") } }
                        .collectMany { it.getFiles() }
                        .forEach { File it3 ->
                            services.putIfAbsent(it3.name, new LinkedHashSet<String>())
                            it3.eachLine { if (!it.isEmpty() && !it.startsWith("#")) services.get(it3.name).add(it) }
                        }
                services.entrySet().forEach {
                    def file = new File(dir, it.key)
                    file.createNewFile()
                    it.value.forEach { line -> file.append(line + "\n") }
                }
            }
        }

        if (isIntelliJ) {
            ModuleInfoExtension moduleInfoIntelliJ = new ModuleInfoExtension(moduleInfo);
            moduleInfoIntelliJ.name = "${moduleInfo.name}.intellij"
            moduleInfo.requires += "transitive ${moduleInfoIntelliJ.name}"

            project.configurations.provided.dependencies.each {
                // exclude boms
                // TODO: setForce is deprecated, so the implementation of enforcePlatform might change and break this
                if (it instanceof DefaultExternalModuleDependency && ((DefaultExternalModuleDependency) it).isForce()) {
                    return
                }
                moduleInfoIntelliJ.requires.add(getModuleName(it.group, it.name))
            }

            project.sourceSets {
                intellij {
                    java {
                        srcDirs(generatedSourcesDir)
                    }
                    resources {
                        srcDirs(embeddedResourcesDir)
                    }
                }
            }

            project.configurations.intellijCompileOnly.extendsFrom(project.configurations.provided)
            project.tasks.compileIntellijJava.inputs.dir(generatedSourcesDir)

            ClassGenerator.generateClassTask(project, 'moduleInfoIntellij', '', 'module-info', {
                inputs.property('moduleInfo.name', moduleInfoIntelliJ.name)
                inputs.property('moduleInfo.exports', moduleInfoIntelliJ.exports)
                inputs.property('moduleInfo.requires', moduleInfoIntelliJ.requires)
                inputs.property('moduleInfo.provides', moduleInfoIntelliJ.provides)
                inputs.property('moduleInfo.uses', moduleInfoIntelliJ.uses)
                outputs.file(new File(generatedSourcesDir, 'module-info.java'))
                dependsOn project.tasks.named('embedClasses')
                dependsOn project.tasks.named('embedResources')
                dependsOn project.tasks.named('embedServices')
                finalizedBy project.tasks.named('compileIntellijJava')
            }, generateModuleInfo(project, moduleInfoIntelliJ, false, true), generatedSourcesDir)

            project.tasks.register('embedIntellij', Jar) {
                onlyIf { embeddedClassesDir.exists() && embeddedClassesDir.directory && !(embeddedClassesDir.list() as List).empty }
                dependsOn project.tasks.named('moduleInfoIntellij')
                dependsOn project.tasks.named('embedResources')
                dependsOn project.tasks.named('embedServices')
                dependsOn project.tasks.named('compileIntellijJava')
                finalizedBy project.tasks.named('moduleInfo')
                archiveAppendix.set("intellij")
                from embeddedClassesDir
                from embeddedResourcesDir

                destinationDirectory = new File(project.buildDir, 'tmp')
            }
            project.tasks.named('embedClasses') {
                finalizedBy project.tasks.named('embedIntellij')
            }
            project.dependencies.add('api', project.tasks.named('embedIntellij').map { it.outputs.files })
            project.artifacts {
                archives project.tasks.named('embedIntellij').map { it.outputs.files.singleFile }
            }
        } else {
            project.sourceSets.main.output.dir(embeddedResourcesDir)
        }

        project.tasks.named('compileJava') {
            dependsOn project.tasks.named('embedClasses')
        }

        project.tasks.named('processResources') {
            dependsOn project.tasks.named('embedResources')
            dependsOn project.tasks.named('embedServices')
        }

        project.tasks.named('jar') {
            inputs.property('isIntelliJ', isIntelliJ)
        }
    }

    static void setupModuleInfo(Project project, ModuleInfoExtension moduleInfo, boolean isIntelliJ) {
        project.sourceSets.main.java.srcDirs.each { File root ->
            if (root.exists()) {
                root.eachFileRecurse(FileType.DIRECTORIES) { File dir ->
                    Path path = root.toPath().relativize(dir.toPath())
                    List<String> elements = StreamSupport.stream(Spliterators.spliteratorUnknownSize(path.iterator(), Spliterator.ORDERED), false)
                            .map({ element -> element.toString() })
                            .collect(Collectors.toList())
                    boolean doExport = elements.stream()
                            .anyMatch({ element -> element.toString() == "domain" || element.toString() == "api" })
                    if (doExport) {
                        moduleInfo.exports = [elements.join(".")] + moduleInfo.exports
                    }
                }
            }
        }

        if (project.name != FeaturePlugin.XTRAPLATFORM_RUNTIME) {
            //moduleInfo.requires.add("dagger");
            //moduleInfo.requires.add("com.github.azahnen.dagger");
        }

        project.configurations.provided.dependencies.each {
            // exclude boms
            // TODO: setForce is deprecated, so the implementation of enforcePlatform might change and break this
            if (it instanceof DefaultExternalModuleDependency && ((DefaultExternalModuleDependency) it).isForce()) {
                return
            }
            moduleInfo.requires.add(getModuleName(it.group, it.name))
        }

        File generatedSrcDir = new File(project.buildDir, 'generated/sources/annotationProcessor/java/main')
        project.sourceSets.main.java { srcDir generatedSrcDir }
        project.tasks.compileJava.inputs.dir(generatedSrcDir)

        ClassGenerator.generateClassTask(project, 'moduleInfo', '', 'module-info', {
            inputs.property('moduleInfo.name', moduleInfo.name)
            inputs.property('moduleInfo.exports', moduleInfo.exports)
            inputs.property('moduleInfo.requires', moduleInfo.requires)
            inputs.property('moduleInfo.provides', moduleInfo.provides)
            inputs.property('moduleInfo.uses', moduleInfo.uses)
            inputs.property('isIntelliJ', isIntelliJ)
            outputs.file(new File(generatedSrcDir, 'module-info.java'))
        }, generateModuleInfo(project, moduleInfo, isIntelliJ), generatedSrcDir)

        //TODO: is not recognized by dagger-auto
        /*if (project.name != FeaturePlugin.XTRAPLATFORM_RUNTIME) {
            def packageName = "${moduleInfo.name}.domain"
            def packageInfo = { "@AutoModule(single = true, encapsulate = true)\npackage ${packageName};\n\nimport com.github.azahnen.dagger.annotations.AutoModule;" }
            ClassGenerator.generateClassTask(project, 'packageInfo', packageName, 'package-info', { inputs.property('isIntelliJ', isIntelliJ) }, packageInfo, 'generated/sources/annotationProcessor/java/main/')
        }*/

        project.tasks.named('compileJava') {
            // use the project's version or define one directly
            options.javaModuleVersion = project.version
        }
    }

    static Closure generateModuleInfo(Project project, ModuleInfoExtension moduleInfo, boolean requiresOnly, boolean exportAll = false) {
        return {
            def excludes = []

            // determine artifacts that should be included in the bundle, might be transitive or not
            def deps = Dependencies.getDependencies(project, 'embeddedExport', excludes, true)
            deps += Dependencies.getDependencies(project, 'embeddedFlatExport', excludes, false)
            if (exportAll) {
                deps += Dependencies.getDependencies(project, 'embedded', excludes, true)
                deps += Dependencies.getDependencies(project, 'embeddedFlat', excludes, true)
            }

            // determine packages for export
            def pkgs = Dependencies.getPackages(deps)

            Map<String, Set<String>> services = new LinkedHashMap<>();
            (project.configurations.embeddedExport + project.configurations.embeddedFlatExport)
                    .filter { it.isFile() }
                    .collect { project.zipTree(it).matching { it2 -> it2.include("META-INF/services/*") } }
                    .collectMany { it.getFiles() }
                    .forEach { File it3 ->
                        services.putIfAbsent(it3.name, new LinkedHashSet<String>())
                        it3.eachLine { if (!it.isEmpty() && !it.startsWith("#") && !it.contains("\$")) services.get(it3.name).add(it) }
                    }

            if (!requiresOnly) {
                pkgs.each { pkg ->
                    moduleInfo.exports += pkg.name
                }
                services.entrySet().forEach {
                    moduleInfo.uses += it.key
                }
                services.entrySet().forEach {
                    if (!it.value.isEmpty())
                        moduleInfo.provides.put(it.key, it.value as List<String>)
                }
            }

            def exports = moduleInfo.exports.stream()
                    .filter({ export -> !isExcluded(export, moduleInfo.exports) })
                    .map({ export -> "\texports ${export};" })
                    .collect(Collectors.joining("\n"))
            def requires = moduleInfo.requires.stream()
                    .filter({ require -> !isExcluded(require, moduleInfo.requires) })
                    .map({ require -> project.name == FeaturePlugin.XTRAPLATFORM_RUNTIME && !require.startsWith("transitive")
                            ? "\trequires transitive ${require};"
                            : "\trequires ${require};" })
                    .collect(Collectors.joining("\n", "\n", ""))
            def provides = moduleInfo.provides.entrySet().stream()
                    .filter({ provide -> !isExcluded(provide.key, moduleInfo.provides.keySet()) })
                    .map({ provide -> "\tprovides ${provide.key} with ${provide.value.join(', ')};" })
                    .collect(Collectors.joining("\n", "\n", ""))
            def uses = moduleInfo.uses.stream()
                    .filter({ use -> !isExcluded(use, moduleInfo.uses) })
                    .map({ use -> "\tuses ${use};" })
                    .collect(Collectors.joining("\n", "\n", ""))

            return """
open module ${moduleInfo.name} {
${exports}
${requires}
${provides}
${uses}
}
            """
        }
    }

    //TODO: configurable versions
    static void setupAnnotationProcessors(Project project) {
        if (project.name != FeaturePlugin.XTRAPLATFORM_RUNTIME) {
            //project.dependencies.add('compileOnly', "com.google.dagger:dagger:2.+", { transitive = false })
            //project.dependencies.add('compileOnly', "io.github.azahnen:dagger-auto:1.0.0-SNAPSHOT")
            project.dependencies.add('annotationProcessor', "com.google.dagger:dagger-compiler:2.+")
            project.dependencies.add('annotationProcessor', "io.github.azahnen:dagger-auto-compiler:1.0.0-SNAPSHOT")

            //project.dependencies.add('compileOnly', "org.immutables:value:2.8.8:annotations")
            //project.dependencies.add('compileOnly', "org.immutables:encode:2.8.8")
            project.dependencies.add('annotationProcessor', "org.immutables:value:2.8.8")
        }
    }

    //TODO: configurable versions
    static void setupUnitTests(Project project) {
        project.plugins.apply('groovy')

        project.dependencies.add('testImplementation', "org.spockframework:spock-core:2.1-groovy-3.0")
        project.dependencies.add('testFixturesImplementation', "org.spockframework:spock-core:2.1-groovy-3.0")
        project.dependencies.add('testImplementation', "com.athaydes:spock-reports:2.3.0-groovy-3.0", { transitive = false })

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

    static boolean isExcluded(String item, Collection<String> items) {
        return item.contains("-") || item.startsWith("!") || items.stream().anyMatch({ item2 -> item2 == "!${item}" || (item2.endsWith("*") && "!${item}".startsWith(item2.substring(0, item2.length() - 1))) })
    }

    public static String getModuleName(String group, String name) {
        return group.replace("interactive_instruments", "ii") + "." + name.replaceAll("-", ".")
    }
}
