package de.interactive_instruments.xtraplatform

import groovy.io.FileType
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.tasks.Jar

import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Collectors
import java.util.stream.StreamSupport

class ModulePlugin implements Plugin<Project> {

    static class ModuleInfoExtension {
        String name = ""
        Set<String> exports = []
        Set<String> requires = []
        Map<String, List<String>> provides = [:]
        Set<String> uses = []
    }

    @Override
    void apply(Project project) {
        ModuleInfoExtension moduleInfo = project.extensions.create('moduleInfo', ModuleInfoExtension)

        def isIntelliJ = System.getProperty("idea.active") == "true"

        setupConfigurations(project)

        project.afterEvaluate {
            moduleInfo.name = getModuleName(project.group as String, project.name)

            setupEmbedding(project, moduleInfo, isIntelliJ)

            setupModuleInfo(project, moduleInfo, isIntelliJ)
        }

        setupAnnotationProcessors(project)

        //TODO: configurable versions
        setupUnitTests(project)
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
    }

    static void setupEmbedding(Project project, ModuleInfoExtension moduleInfo, boolean isIntelliJ) {
        def embeddedClassesDir = isIntelliJ
                ? new File(project.buildDir, 'idea/classes/java/main')
                : new File(project.buildDir, 'classes/java/main')
        def embeddedClassesDirOther = isIntelliJ
                ? new File(project.buildDir, 'classes/java/main')
                : new File(project.buildDir, 'idea/classes/java/main')
        def embeddedResourcesDir = isIntelliJ
                ? new File(project.buildDir, 'idea/src/main/resources')
                : new File(project.buildDir, 'generated/src/main/resources')
        def embeddedResourcesDirOther = isIntelliJ
                ? new File(project.buildDir, 'generated/src/main/resources')
                : new File(project.buildDir, 'idea/src/main/resources')

        project.tasks.register('embedClean', Delete) {
            inputs.property('isIntelliJ', isIntelliJ)
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
            ModuleInfoExtension moduleInfoEmbedded = new ModuleInfoExtension();
            moduleInfoEmbedded.name = "${moduleInfo.name}.embedded"
            def generatedDir = 'idea/src/embedded/java/'
            ClassGenerator.generateClassTask(project, 'moduleInfoIntellij', '', 'module-info', {}, generateModuleInfo(project, moduleInfoEmbedded, false), generatedDir)

            project.tasks.register('embedIntellij', Jar) {
                onlyIf { embeddedClassesDir.exists() && embeddedClassesDir.directory && !(embeddedClassesDir.list() as List).empty }
                dependsOn project.tasks.named('moduleInfoIntellij')
                dependsOn project.tasks.named('embedResources')
                archiveAppendix.set("embedded")
                from embeddedClassesDir
                from embeddedResourcesDir
                from new File(project.buildDir, generatedDir)

                destinationDirectory = new File(project.buildDir, 'tmp')
                doLast {
                    moduleInfo.requires += "transitive ${moduleInfoEmbedded.name}"
                }
            }
            project.tasks.named('embedClasses') {
                finalizedBy project.tasks.named('embedIntellij')
            }
            project.dependencies.add('api', project.tasks.named('embedIntellij').map { it.outputs.files })
            project.artifacts {
                archives project.tasks.named('embedIntellij').map { it.outputs.files.singleFile }
            }
        } else {
            project.sourceSets.main.resources { srcDir embeddedResourcesDir }
        }

        project.tasks.named('compileJava') {
            dependsOn project.tasks.named('embedClasses')
        }

        project.tasks.named('processResources') {
            dependsOn project.tasks.named('embedResources')
            //TODO: needed? outputs.dir(embeddedResourcesDir)
        }

        project.tasks.named('jar') {
            inputs.property('isIntelliJ', isIntelliJ)
        }
    }

    static void setupModuleInfo(Project project, ModuleInfoExtension moduleInfo, boolean isIntelliJ) {
        project.afterEvaluate {

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
                moduleInfo.requires.add("dagger");
                moduleInfo.requires.add("com.github.azahnen.dagger");
            }

            project.configurations.provided.dependencies.each {
                moduleInfo.requires.add(getModuleName(it.group, it.name))
            }

            ClassGenerator.generateClassTask(project, 'moduleInfo', '', 'module-info', { inputs.property('isIntelliJ', isIntelliJ) }, generateModuleInfo(project, moduleInfo, isIntelliJ), isIntelliJ ? 'idea/src/main/java/' : 'generated/src/main/java/')

            if (project.name != FeaturePlugin.XTRAPLATFORM_RUNTIME) {
                def packageName = "${moduleInfo.name}.domain"
                def packageInfo = { "@AutoModule(single = true, encapsulate = true)\npackage ${packageName};\n\nimport com.github.azahnen.dagger.annotations.AutoModule;" }
                ClassGenerator.generateClassTask(project, 'packageInfo', packageName, 'package-info', { inputs.property('isIntelliJ', isIntelliJ) }, packageInfo, 'generated/sources/annotationProcessor/java/main/')
            }
        }
    }

    static Closure generateModuleInfo(Project project, ModuleInfoExtension moduleInfo, boolean requiresOnly) {
        return {
            def excludes = []

            // determine artifacts that should be included in the bundle, might be transitive or not
            def deps = Dependencies.getDependencies(project, 'embeddedExport', excludes, true)
            deps += Dependencies.getDependencies(project, 'embeddedFlatExport', excludes, false)

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
                    .map({ require -> "\trequires ${require};" })
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
            project.dependencies.add('implementation', "com.google.dagger:dagger:2.+", { transitive = false })
            project.dependencies.add('implementation', "com.github.azahnen:dagger-auto:1.+")
            project.dependencies.add('annotationProcessor', "com.google.dagger:dagger-compiler:2.+")
            project.dependencies.add('annotationProcessor', "com.github.azahnen:dagger-auto-compiler:1.+")
        }
        //TODO: immutables
    }

    static void setupUnitTests(Project project) {
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

    static boolean isExcluded(String item, Collection<String> items) {
        return item.contains("-") || item.startsWith("!") || items.stream().anyMatch({ item2 -> item2 == "!${item}" || (item2.endsWith("*") && "!${item}".startsWith(item2.substring(0, item2.length() - 1))) })
    }

    public static String getModuleName(String group, String name) {
        return group.replace("interactive_instruments", "ii") + "." + name.replaceAll("-", ".")
    }
}
