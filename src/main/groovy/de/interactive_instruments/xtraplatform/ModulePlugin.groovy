package de.interactive_instruments.xtraplatform

import com.github.spotbugs.snom.SpotBugsTask
import de.interactive_instruments.xtraplatform.pmd.PmdInvokerSarif
import de.interactive_instruments.xtraplatform.pmd.SarifForGithub
import groovy.io.FileType
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.UnknownDomainObjectException
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ExternalModuleDependencyBundle
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.artifacts.VersionConstraint
import org.gradle.api.attributes.Category
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.internal.artifacts.dependencies.DefaultExternalModuleDependency
import org.gradle.api.plugins.quality.Pmd
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.tasks.Jar

import java.math.RoundingMode
import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Collectors
import java.util.stream.StreamSupport

class ModulePlugin implements Plugin<Project> {

    void executeTask(Task task) {
        task.taskDependencies.getDependencies(task).each {
            subTask -> executeTask(subTask)
        }
        if (task.path.startsWith(":xtraplatform-build:")) {
            println "BREAK"
            return
        }
        println task.path
        task.actions.each {
            println "  " + it
            if (it != null && task != null) {
                try {
                    it.execute(task)
                } catch (NullPointerException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    void executeTask(TaskProvider<Task> task) {
        task.configure({ executeTask(it) })
    }

    @Override
    void apply(Project project) {
        ModuleInfoExtension moduleInfo = project.moduleInfo

        def includedBuilds = CompositePlugin.getIncludedBuildNames(project)

        // apply layer boms
        //project.parent.configurations.layers.incoming.beforeResolve {
        project.parent.configurations.layers.dependencies.collect().each {
            def isIncludedBuild = includedBuilds.contains(it.name)
            if (!isIncludedBuild) {
                def bom = [group: it.group, name: "${it.name}", version: it.version]

                project.dependencies.add('provided', project.dependencies.enforcedPlatform(bom))
            }
        }
        //}

        //setupConfigurations(project)

        setupAnnotationProcessors(project)

        setupUnitTests(project)

        setupCodeQuality(project, includedBuilds.contains(project.parent.name))

        project.afterEvaluate {
            if (moduleInfo.enabled) {
                moduleInfo.name = getModuleName(project.group as String, project.name)

                setupModuleInfo(project, moduleInfo, true)

                setupEmbedding(project, moduleInfo)

                //TODO: fix TODO in line 235
                /*if (System.getProperty("idea.sync.active") == "true") {
                    println "SYNC"
                    def task = project.tasks.findByPath("embedTpl")
                    if (task != null) {
                        executeTask(task)
                    }
                }*/
            }
        }
    }

    static void setupConfigurations(Project project) {
        project.configurations.create('provided')
        project.configurations.create('embedded')
        project.configurations.create('embeddedExport')
        project.configurations.create('embeddedFlat')
        project.configurations.create('embeddedFlatExport')
        project.configurations.create('embeddedImport')

        project.configurations.provided.setTransitive(true)
        project.configurations.embedded.setTransitive(true)
        project.configurations.embeddedExport.setTransitive(true)
        project.configurations.embeddedFlat.setTransitive(false)
        project.configurations.embeddedFlatExport.setTransitive(false)
        project.configurations.embeddedImport.setTransitive(true)

        project.configurations.compileOnly.extendsFrom(project.configurations.provided)
        project.configurations.testImplementation.extendsFrom(project.configurations.provided)
        project.configurations.testFixturesImplementation.extendsFrom(project.configurations.provided)
    }

    static void setupEmbedding(Project project, ModuleInfoExtension moduleInfo) {
        List<Dependency> deps = []
        deps.addAll(project.configurations.embedded.allDependencies.collect { "EMB " + it.group + "::" + it.name })
        deps.addAll(project.configurations.embeddedExport.allDependencies.collect { "EXP " + it.group + "::" + it.name })
        deps.addAll(project.configurations.embeddedFlat.allDependencies.collect { "EMB FLAT " + it.group + "::" + it.name })
        deps.addAll(project.configurations.embeddedFlatExport.allDependencies.collect { "EXP FLAT " + it.group + "::" + it.name })

        if (deps.isEmpty()) {
            return
        }/* else {
            println "${moduleInfo.name} " + deps
        }*/

        def embeddedClassesDir = new File(project.buildDir, 'classes/java/tpl')
        def embeddedResourcesDir = new File(project.buildDir, 'generated/sources/annotationProcessor/resources/tpl')
        File generatedSourcesDir = new File(project.buildDir, 'generated/sources/annotationProcessor/java/tpl')

        project.tasks.register('embedClasses', Copy) {
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

        ModuleInfoExtension moduleInfoTpl = new ModuleInfoExtension(moduleInfo);
        moduleInfoTpl.name = "${moduleInfo.name}.tpl"
        moduleInfoTpl.exports = moduleInfoTpl.exports.findAll {!it.startsWith("de.ii") }
        moduleInfo.exports = moduleInfo.exports.findAll {it.startsWith("de.ii") }
        moduleInfo.requires += "transitive ${moduleInfoTpl.name}"

        project.configurations.embeddedImport.dependencies.each {
            moduleInfoTpl.requires.add(getModuleName(it.group, it.name) + ".tpl")
        }
        def runtime = getModuleName("de.interactive_instruments", "xtraplatform-runtime")
        if (moduleInfo.name != runtime) {
            moduleInfoTpl.requires.add(runtime + ".tpl")
        }

        project.sourceSets {
            tpl {
                java {
                    srcDirs(generatedSourcesDir)
                }
                resources {
                    srcDirs(embeddedResourcesDir)
                }
            }
        }
        project.configurations.tplCompileOnly.extendsFrom(project.configurations.compileOnly)
        project.tasks.compileTplJava.inputs.dir(generatedSourcesDir)

        ClassGenerator.generateClassTask(project, 'moduleInfoTpl', '', 'module-info', {
            outputs.cacheIf { true }
            inputs.property('moduleInfo.name', moduleInfoTpl.name)
            inputs.property('moduleInfo.exports', moduleInfoTpl.exports)
            //inputs.property('moduleInfo.requires', moduleInfoTpl.requires)
            inputs.property('moduleInfo.provides', moduleInfoTpl.provides)
            inputs.property('moduleInfo.uses', moduleInfoTpl.uses)
            outputs.file(new File(generatedSourcesDir, 'module-info.java'))
            dependsOn project.tasks.named('embedClasses')
            dependsOn project.tasks.named('embedResources')
            dependsOn project.tasks.named('embedServices')
            finalizedBy project.tasks.named('compileTplJava')
        }, generateModuleInfo(project, moduleInfoTpl, false, true), generatedSourcesDir)

        project.tasks.register('embedTpl', Jar) {
            onlyIf { embeddedClassesDir.exists() && embeddedClassesDir.directory && !(embeddedClassesDir.list() as List).empty }
            dependsOn project.tasks.named('moduleInfoTpl')
            dependsOn project.tasks.named('embedResources')
            dependsOn project.tasks.named('embedServices')
            dependsOn project.tasks.named('compileTplJava')
            finalizedBy project.tasks.named('moduleInfo')
            archiveAppendix.set("tpl")
            from embeddedClassesDir
            from embeddedResourcesDir

            destinationDirectory = new File(project.buildDir, 'tmp')
        }
        project.tasks.named('embedClasses') {
            finalizedBy project.tasks.named('embedTpl')
        }
        project.dependencies.add('api', project.tasks.named('embedTpl').map { it.outputs.files })
        def tplArtifact = project.artifacts.add('archives', project.tasks.named('embedTpl').map { it.outputs.files.singleFile })

        project.publishing.publications {
            tpl(MavenPublication) {
                artifact tplArtifact

                artifactId "${project.name}-tpl"
                groupId project.group
                version project.version

                pom {
                    packaging = "jar"
                }
            }
        }
        project.publishing.publications['default'].with {
            pom.withXml {
                def dependenciesNode = asNode().get('dependencies')[0]
                def dependencyNode = dependenciesNode.appendNode('dependency')
                dependencyNode.appendNode('groupId', project.group)
                dependencyNode.appendNode('artifactId', "${project.name}-tpl")
                dependencyNode.appendNode('version', project.version)
            }
        }

        project.tasks.named('processTplResources') {
            dependsOn project.tasks.named('embedResources')
            dependsOn project.tasks.named('embedServices')
        }
    }

    static void setupModuleInfo(Project project, ModuleInfoExtension moduleInfo, boolean requiresOnly, boolean isApp = false) {
        if (!isApp) {
            moduleInfo.requires.add("de.ii.xtraplatform.build")

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
            project.configurations.provided.dependencies.each {
                // exclude boms
                if (it instanceof DefaultExternalModuleDependency) {
                    def cat = ((DefaultExternalModuleDependency) it).attributes.getAttribute(Category.CATEGORY_ATTRIBUTE)
                    if (cat != null && cat.name == Category.ENFORCED_PLATFORM) {
                        return
                    }
                }

                moduleInfo.requires.add(getModuleName(it.group, it.name))
            }
        } else {
            project.configurations.layerModules.resolvedConfiguration.firstLevelModuleDependencies.each {
                it.children.each { module ->
                    moduleInfo.requires.add(getModuleName(module.moduleGroup, module.moduleName))
                }
            }
            project.configurations.modules.dependencies.each {
                moduleInfo.requires.add(getModuleName(it.group, it.name))
            }
        }

        File generatedSrcDir = new File(project.buildDir, 'generated/sources/annotationProcessor/java/mod')
        project.sourceSets.main.java { srcDir generatedSrcDir }
        //project.tasks.compileJava.inputs.dir(generatedSrcDir)

        ClassGenerator.generateClassTask(project, 'moduleInfo', '', 'module-info', {
            outputs.cacheIf { true }
            inputs.property('moduleInfo.name', moduleInfo.name)
            inputs.property('moduleInfo.exports', moduleInfo.exports)
            inputs.property('moduleInfo.requires', moduleInfo.requires)
            inputs.property('moduleInfo.provides', moduleInfo.provides)
            inputs.property('moduleInfo.uses', moduleInfo.uses)
            outputs.file(new File(generatedSrcDir, 'module-info.java'))
        }, generateModuleInfo(project, moduleInfo, requiresOnly, false, isApp), generatedSrcDir)

        //TODO: is not recognized by dagger-auto
        /*if (project.name != LayerPlugin.XTRAPLATFORM_RUNTIME) {
            def packageName = "${moduleInfo.name}.domain"
            def packageInfo = { "@AutoModule(single = true, encapsulate = true)\npackage ${packageName};\n\nimport com.github.azahnen.dagger.annotations.AutoModule;" }
            ClassGenerator.generateClassTask(project, 'packageInfo', packageName, 'package-info', { inputs.property('isIntelliJ', isIntelliJ) }, packageInfo, 'generated/sources/annotationProcessor/java/main/')
        }*/

        project.tasks.named('compileJava') {
            options.javaModuleVersion = project.provider { project.version }
        }
    }

    static Closure generateModuleInfo(Project project, ModuleInfoExtension moduleInfo, boolean requiresOnly, boolean exportAll = false, boolean isApp = false) {
        return {
            def excludes = []
            def deps = [] as Set
            def pkgs = [] as Set
            Map<String, Set<String>> services = new LinkedHashMap<>();

            if (!isApp) {
                // determine artifacts that should be included in the module, might be transitive or not
                deps += Dependencies.getDependencies(project, 'embeddedExport', excludes, true)
                deps += Dependencies.getDependencies(project, 'embeddedFlatExport', excludes, false)
                if (exportAll) {
                    deps += Dependencies.getDependencies(project, 'embedded', excludes, true)
                    deps += Dependencies.getDependencies(project, 'embeddedFlat', excludes, true)
                }

                // determine packages for export
                pkgs += Dependencies.getPackages(deps)

                (project.configurations.embeddedExport + project.configurations.embeddedFlatExport)
                        .filter { it.isFile() }
                        .collect { project.zipTree(it).matching { it2 -> it2.include("META-INF/services/*") } }
                        .collectMany { it.getFiles() }
                        .forEach { File it3 ->
                            services.putIfAbsent(it3.name, new LinkedHashSet<String>())
                            it3.eachLine { if (!it.isEmpty() && !it.startsWith("#") && !it.contains("\$")) services.get(it3.name).add(it) }
                        }
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
                    .map({ require ->
                        project.name == LayerPlugin.XTRAPLATFORM_RUNTIME && !require.startsWith("transitive")
                                ? "\trequires transitive ${require};"
                                : "\trequires ${require};"
                    })
                    .collect(Collectors.joining("\n", "\n", ""))
            def provides = moduleInfo.provides.entrySet().stream()
                    .filter({ provide -> !isExcluded(provide.key, moduleInfo.provides.keySet()) })
                    .map({ provide -> "\tprovides ${provide.key} with ${provide.value.join(', ')};" })
                    .collect(Collectors.joining("\n", "\n", ""))
            def uses = moduleInfo.uses.stream()
                    .filter({ use -> !isExcluded(use, moduleInfo.uses) })
                    .map({ use -> "\tuses ${use};" })
                    .collect(Collectors.joining("\n", "\n", ""))
            def additions = !exportAll ? moduleInfo.additions.stream().collect(Collectors.joining("\n", "\n", "")) : ""

            return """
@SuppressWarnings("module")
open module ${moduleInfo.name} {
${exports}
${requires}
${provides}
${uses}
${additions}
}
            """
        }
    }

    //TODO: configurable versions
    static void setupAnnotationProcessors(Project project) {
        if (project.name != LayerPlugin.XTRAPLATFORM_RUNTIME) {
            //TODO: get version from xtraplatform (or the other way around)
            findCatalogBundle(project, 'annotations').each {
                project.dependencies.add('annotationProcessor', it)
            }

            project.tasks.named('compileJava') {
                options.compilerArgs.add("-Aimmutables.gradle.incremental")
            }
        }
    }

    static void setupUnitTests(Project project) {
        project.plugins.apply('groovy')
        project.plugins.apply('jacoco')

        findCatalogBundle(project, 'transitive').each {
            project.dependencies.add('testImplementation', it)
        }
        findCatalogBundle(project, 'nontransitive').each {
            project.dependencies.add('testImplementation', it, { transitive = false })
        }
        findCatalogBundle(project, 'fixtures').each {
            project.dependencies.add('testFixturesImplementation', it)
        }

        project.tasks.register("coverageReportInfo") {
            doLast {
                println "\nJacoco report: file://${project.buildDir}/reports/jacoco/test/html/index.html"
            }
        }

        def testConfig = {
            useJUnitPlatform()

            testLogging.showStandardStreams = true
            reports {
                html.required = false
                junitXml.required = false
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
            finalizedBy project.tasks.jacocoTestReport
            finalizedBy project.tasks.jacocoTestCoverageVerification
            finalizedBy project.tasks.coverageReportInfo

            jacoco {
                includes = ['de.ii.*']
                excludes = ['*AutoBindings*', '*Dagger*', '*Factory.class']
            }
        }

        project.tasks.check.dependsOn project.tasks.jacocoTestCoverageVerification
        project.tasks.jacocoTestCoverageVerification.dependsOn project.tasks.test

        project.jacocoTestReport {
            project.afterEvaluate {
                classDirectories.setFrom(project.files(classDirectories.files.collect {
                    project.fileTree(dir: it, include: 'de/ii/**').filter { File file ->
                        project.test.jacoco.excludes.stream().allMatch((String e) -> {
                            if (e.startsWith("*") && e.endsWith("*"))
                                return !file.name.contains(e.replace('*', ''));
                            else if (e.startsWith("*"))
                                return !file.name.endsWith(e.replace('*', ''));
                            else if (e.endsWith("*"))
                                return !file.name.startsWith(e.replace('*', ''));
                            return !file.name.equals(e);
                        })
                    }
                }))
            }
        }
        project.jacocoTestCoverageVerification {
            project.afterEvaluate {
                classDirectories.setFrom(project.files(classDirectories.files.collect {
                    project.fileTree(dir: it, include: 'de/ii/**').filter { File file ->
                        project.test.jacoco.excludes.stream().allMatch((String e) -> {
                            if (e.startsWith("*") && e.endsWith("*"))
                                return !file.name.contains(e.replace('*', ''));
                            else if (e.startsWith("*"))
                                return !file.name.endsWith(e.replace('*', ''));
                            else if (e.endsWith("*"))
                                return !file.name.startsWith(e.replace('*', ''));
                            return !file.name.equals(e);
                        })
                    }
                }))
                LayerMaturityExtension.MaturityConfiguration cfg = project.parent.layer.cfgForMaturity(project.maturity)
                violationRules {
                    rule {
                        limit {
                            counter = 'LINE'
                            value = 'COVEREDRATIO'
                            minimum = new BigDecimal(cfg.minimumCoverage).setScale(2, RoundingMode.UP)
                        }
                    }
                }
            }
        }

        project.tasks.test.with testConfig

        project.tasks.register("testSlow", Test) {
            with testConfig
            systemProperty 'spock.include.Slow', 'true'
        }
    }

    static void setupCodeQuality(Project project, boolean isParentIncluded) {
        project.plugins.apply('pmd')
        //project.plugins.apply('com.github.spotbugs')

        project.pmd {
            toolVersion = findCatalogVersion(project, 'pmd').displayName
            consoleOutput = project.maturity as Maturity >= Maturity.CANDIDATE
            ignoreFailures = project.maturity as Maturity <= Maturity.CANDIDATE
            ruleSets = []
            ruleSetFiles new File(project.parent.buildDir, "pmd/code.xml")
            if (!project.parent.layer.lowLevel) {
                ruleSetFiles new File(project.parent.buildDir, "pmd/highlevel.xml")
            }
        }

        /*project.spotbugs {
            toolVersion = '4.6.0'
            showStackTraces = project.maturity as Maturity >= Maturity.CANDIDATE
            showProgress = true //project.maturity as Maturity >= Maturity.CANDIDATE
            ignoreFailures = project.maturity as Maturity <= Maturity.CANDIDATE
            onlyAnalyze = ['de.ii.-']
            //extraArgs = ['-choosePlugins', '+com.h3xstream.findsecbugs']
        }*/

        project.tasks.withType(Pmd).configureEach { pmd ->
            pmd.dependsOn project.parent.tasks.named("pmdInit")
            pmd.reports.xml.required = false
            pmd.actions.clear()
            pmd.doFirst {
                validate(rulesMinimumPriority.get());
                //PmdInvokerSarif.invoke(pmd);
            }
            /*pmd.doLast {
                def json = new File(pmd.reports.html.outputLocation.asFile.get().absolutePath.replace(".html", ".json"))
                def rootPath = project.rootDir.absolutePath
                if (isParentIncluded) {
                    rootPath.replace(project.parent.name, "")
                } else {
                    rootPath += "/"
                }
                def severity = project.maturity as Maturity == Maturity.MATURE
                        ? SarifForGithub.Severity.error
                        : project.maturity as Maturity == Maturity.CANDIDATE
                        ? SarifForGithub.Severity.warning
                        : SarifForGithub.Severity.recommendation


                if (json.exists()) {
                    SarifForGithub.prepare(json, rootPath, project.name, pmd.name.toLowerCase().replace("pmd", ""), severity)
                }
            }*/
        }

        /*project.tasks.withType(SpotBugsTask).configureEach { spotbugs ->
            // workaround for spotbugsIntellij
            if (spotbugs.classDirs == null) {
                spotbugs.classDirs = project.files()
            }
            spotbugs.onlyIf { project.findProperty('spotbugs') == 'true' } //TODO: pretty slow, when to run?
            spotbugs.reports {
                html {
                    required = true
                    stylesheet = 'fancy-hist.xsl'
                }
                sarif {
                    required = true
                }
            }
            spotbugs.classes = spotbugs.classes.filter { File it ->
                !it.absolutePath.contains("/build/generated/")
            }
            spotbugs.doLast {
                def json = spotbugs.reports.SARIF.outputLocation.asFile.get()
                //TODO: SarifForGithub
                if (json.exists()) {
                    def sarif = new JsonSlurper().parse(json)
                    def sarifText = JsonOutput.prettyPrint(JsonOutput.toJson(sarif))
                    json.text = sarifText
                }
            }
        }*/
        //TODO: The following classes needed for analysis were missing: apply
        //project.dependencies.add('spotbugsPlugins', "com.h3xstream.findsecbugs:findsecbugs-plugin:1.11.0")

        project.afterEvaluate {
            LayerMaturityExtension.MaturityConfiguration cfg = project.parent.layer.cfgForMaturity(project.maturity)

            project.tasks.withType(JavaCompile).configureEach {
                if (cfg.warningsAsErrors) {
                    options.compilerArgs.add("-Werror")
                    //TODO: Xlint, siehe https://sol.cs.hm.edu/4129/html/431-warnungendesjavacompilers.xhtml
                }
            }

            if (cfg.ignorePmdErrors) {
                project.pmd.ignoreFailures = true
            }
        }
    }

    static ExternalModuleDependencyBundle findCatalogBundle(Project project, String name) {
        def catalog = project.rootProject
                .extensions
                .getByType(VersionCatalogsExtension.class)
                .find("xtraplatform")

        if (catalog.isEmpty()) {
            throw new UnknownDomainObjectException("Version catalog 'xtraplatform' not found")
        }

        def bundle = catalog.get().findBundle(name)

        if (bundle.isEmpty()) {
            throw new UnknownDomainObjectException("Bundle '${name}' not found in catalog 'xtraplatform'")
        }

        return bundle.get().get()
    }

    static VersionConstraint findCatalogVersion(Project project, String name) {
        def catalog = project.rootProject
                .extensions
                .getByType(VersionCatalogsExtension.class)
                .find("xtraplatform")

        if (catalog.isEmpty()) {
            throw new UnknownDomainObjectException("Version catalog 'xtraplatform' not found")
        }

        def version = catalog.get().findVersion(name)

        if (version.isEmpty()) {
            throw new UnknownDomainObjectException("Version '${name}' not found in catalog 'xtraplatform'")
        }

        return version.get()
    }

    static boolean isExcluded(String item, Collection<String> items) {
        return item.contains("-") || item.startsWith("!") || items.stream().anyMatch({ item2 -> item2 == "!${item}" || (item2.endsWith("*") && "!${item}".startsWith(item2.substring(0, item2.length() - 1))) })
    }

    public static String getModuleName(String group, String name) {
        return group.replace("interactive_instruments", "ii") + "." + name.replaceAll("-", ".")
    }

    public static String getModuleName(String simpleName) {
        return "de.ii." + simpleName.replaceAll("-", ".")
    }

    public static String getModuleNameShort(String moduleName) {
        return moduleName.replace("de.ii.", "").replaceAll("\\.", "-")
    }

    public static String getModuleLevel(Maturity maturity, Maintenance maintenance) {
        return maturity.toString() + "_" + maintenance.toString()
    }
}
