package de.interactive_instruments.xtraplatform


import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.artifacts.ResolvedDependency
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.Exec
import org.gradle.internal.os.OperatingSystem

import java.util.jar.Manifest
import java.util.regex.Pattern
import java.util.stream.Collectors

/**
 * @author zahnen
 */
class ApplicationPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        if (project.logger.isInfoEnabled()) {
            project.logger.info("Applying ApplicationPlugin {} to {}", ApplicationPlugin.getVersion(project), project.name)
        }

        project.plugins.apply(LayerPlugin.class)
        project.plugins.apply("application")

        def appExtension = project.extensions.create('app', ApplicationExtension, project)
        ModuleInfoExtension moduleInfo = project.extensions.create('moduleInfo', ModuleInfoExtension)

        //suppress java 9+ illegal access warnings for felix, jackson afterburner, geotools/hsqldb, mustache
        project.application.applicationDefaultJvmArgs += ['--add-opens', 'java.base/java.lang=ALL-UNNAMED', '--add-opens', 'java.base/java.net=ALL-UNNAMED', '--add-opens', 'java.base/java.security=ALL-UNNAMED', '--add-opens', 'java.base/java.nio=ALL-UNNAMED', '--add-opens', 'java.base/java.util=ALL-UNNAMED']

        project.configurations.create("featureDevOnly")
        project.configurations.featureDevOnly.resolutionStrategy.cacheDynamicVersionsFor(5, 'minutes')
        project.configurations.create("app")
        project.configurations.implementation.extendsFrom(project.configurations.app)

        def includedBuilds = CompositePlugin.getIncludedBuildNames(project)

        project.afterEvaluate {
            def baseFound = false
            project.configurations.layers.dependencies.each {
                if (it.name == LayerPlugin.XTRAPLATFORM_CORE) {
                    /*if (!includedBuilds.contains(it.name)) {
                        project.dependencies.add('app', project.dependencies.enforcedPlatform(it))
                    }*/

                    project.dependencies.add('app', "de.interactive_instruments:${LayerPlugin.XTRAPLATFORM_RUNTIME}")
                    project.dependencies.add('app', "de.interactive_instruments:${LayerPlugin.XTRAPLATFORM_BASE}")
                    baseFound = true
                }
            }
            if (!baseFound) {
                throw new IllegalStateException("You have to add '${LayerPlugin.XTRAPLATFORM_CORE}' to configuration 'layers'")
            }

            def layers = getModules(project)
            layers.eachWithIndex { feature, index ->

                feature.collectMany({ bundle ->
                    bundle.moduleArtifacts
                }).each({ ResolvedArtifact artifact ->
                    if (!artifact.moduleVersion.id.name.startsWith("ldproxy-")) {
                        project.dependencies.add('app', "${artifact.moduleVersion.id.group}:${artifact.moduleVersion.id.name}:${artifact.moduleVersion.id.version}")
                    }
                })
            }
            project.configurations.modules.dependencies.each {
                project.dependencies.add('app', it)
            }

            moduleInfo.name = ModulePlugin.getModuleName(project.group as String, project.name)
            ModulePlugin.setupModuleInfo(project, moduleInfo, false, true)

            project.dependencies.add('app', [group: 'de.interactive_instruments', name: 'xtraplatform-build', version: getVersion(project)], {
                transitive = false
                capabilities {
                    requireCapability("de.interactive_instruments:xtraplatform-build-annotations")
                }
            })
        }

        /*project.configurations.featureDevOnly.incoming.beforeResolve {
            project.configurations.featureDevOnly.dependencies.collect().each {
                if (!it.name.endsWith("-bundles")) {
                    if (includedBuilds.contains(it.name)) {

                    } else {
                        def bom = [group: it.group, name: "${it.name}", version: it.version]
                        def bundles = [group: it.group, name: "${it.name}-bundles", version: it.version]
                        //subproject.dependencies.add('provided', subproject.dependencies.enforcedPlatform(bom))

                        //project.dependencies.add('featureDevOnly', bundles)
                    }
                }
            }
        }*/

        ModulePlugin.setupAnnotationProcessors(project)

        //TODO: get version from xtraplatform (or the other way around)
        //project.dependencies.add('compileOnly', ModulePlugin.findCatalogLibrary(project, "dagger"), { transitive = false })
        //project.dependencies.add('annotationProcessor', ModulePlugin.findCatalogLibrary(project, "dagger-compiler"))

        project.tasks.named('compileJava') {
            options.javaModuleVersion = project.provider { project.version }
        }

        addCreateRuntimeClassTask(project, appExtension)

        addDistribution(project)

        addRunConfiguration(project)

        addDocker(project)

        addSbom(project)
    }

    static String getVersion(Project project) {
        final Configuration classpath = project.getBuildscript().getConfigurations().getByName("classpath");
        return classpath.getResolvedConfiguration().getResolvedArtifacts().stream()
                .map(artifact -> artifact.getModuleVersion().getId())
                .filter(id -> "de.interactive_instruments".equals(id.getGroup()) && id.getName().startsWith("xtraplatform-"))
                .findAny()
                .map(ModuleVersionIdentifier::getVersion)
                .orElse("+");
    }

    void addRunConfiguration(Project project) {
        def dataDir = new File(project.buildDir, 'data')

        project.ext.useNativeRun = isSupportedOs()
                ? project.findProperty('runInDocker') != 'true'
                : project.findProperty('runInDocker') == 'false'

        project.task('initData') {
            doLast {
                dataDir.mkdirs()
            }
        }

        project.gradle.taskGraph.whenReady { graph ->
            def tasks = graph.allTasks.collect { it.name }
            if (tasks.contains("run") || tasks.contains("dockerRun")) {
                System.setProperty("taskIsRun", "true")
            }
        }

        project.tasks.run.with {
            dependsOn project.tasks.installDist
            dependsOn project.tasks.initData
            onlyIf { project.ext.useNativeRun }
            workingDir = project.tasks.installDist.destinationDir
            debug = project.findProperty('debug') ?: false
            args project.findProperty('data') ?: dataDir.absolutePath
            standardInput = System.in
            environment 'XTRAPLATFORM_ENV', 'DEVELOPMENT'
            doFirst {
                if (project.ext.useNativeRun && !isSupportedOs()) {
                    logger.warn("WARNING: Running natively is not supported for '${System.getProperty("os.name")}' and will most likely lead to errors.")
                }
            }
        }
    }

    static boolean isSupportedOs() {
        return OperatingSystem.current().isLinux() || OperatingSystem.current().isMacOsX()
    }

    void addDistribution(Project project) {
        project.afterEvaluate {
            project.distributions.with {
                main {
                    contents {
                        into('') {
                            //create an empty 'data/log' directory in distribution root
                            def appDirBase = new File(project.buildDir, 'tmp/app-dummy-dir')
                            def logDir = new File(appDirBase, 'data/log')
                            logDir.mkdirs()

                            from { appDirBase }
                        }
                    }
                }
            }
        }

        // for docker
        project.tasks.distTar.archiveVersion.set('')
        project.tasks.distZip.archiveVersion.set('')
    }

    void addSbom(Project project) {
        project.afterEvaluate {
            project.cyclonedxBom {
                projectType = "application"
                componentVersion = project.version
            }
        }

        project.extensions.add('dependencyTrack', [apiUrl: project.findProperty('dtrackApiUrl') ?: '', apiKey: project.findProperty('dtrackApiKey') ?: ''])

        project.tasks.register("createSbom", SbomTask) {
            dependsOn project.tasks.named("cyclonedxBom")
        }

        project.tasks.register("publishSbom") {
            dependsOn project.tasks.named("createSbom")
            doLast {
                println "Uploading SBOM to ${project.dependencyTrack.apiUrl}"
                def baseUrl = new URL("${project.dependencyTrack.apiUrl}")
                def apiKey = "${project.dependencyTrack.apiKey}"
                def bom = project.tasks.createSbom.outputs.files.singleFile.bytes.encodeBase64().toString()
                def queryString = """{
                    "autoCreate": true,
                    "projectName": "${project.name}",
                    "projectVersion": "${project.version}",
                    "bom": "${bom}"
                }"""

                def connection = baseUrl.openConnection()
                connection.with {
                    doOutput = true
                    requestMethod = 'PUT'
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("X-API-Key", apiKey)
                    outputStream.withWriter { writer ->
                        writer << queryString
                    }
                    def response = content.text
                }
            }
        }
    }

    void addDocker(Project project) {
        def baseImage = 'eclipse-temurin:17-jre'
        File dockerFile = new File(project.buildDir, 'tmp/Dockerfile')
        File dockerContext = new File(project.buildDir, 'docker')

        project.tasks.register('dockerFile') {
            outputs.file dockerFile
            doLast {
                dockerFile.text = """
FROM ${baseImage}
MAINTAINER interactive instruments GmbH
ARG TARGETOS
ARG TARGETARCH
ADD ${project.name}-\$TARGETOS-\$TARGETARCH.tar /
ENTRYPOINT ["/${project.name}/bin/${project.name}"]
EXPOSE 7080
WORKDIR /${project.name}
VOLUME /${project.name}/data
ENV XTRAPLATFORM_ENV CONTAINER
"""
            }
        }

        project.tasks.register('dockerContext', Copy) {
            from project.tasks.distTar
            from project.tasks.dockerFile
            into dockerContext
            rename {
                it.replace("${project.name}", "${project.name}-${project.platform}")
            }
        }

        project.tasks.register('dockerBuild', Exec) {
            dependsOn project.tasks.dockerContext
            outputs.upToDateWhen { project.tasks.dockerContext.state.upToDate }
            workingDir dockerContext
            commandLine 'docker', 'buildx', 'build', '-t', 'iide/ldproxy:local-dev', '--load', '.'
        }

        project.tasks.register('dockerRun', Exec) {
            dependsOn project.tasks.dockerBuild
            //network=host is only supported in linux
            if (OperatingSystem.current().isLinux()) {
                commandLine 'docker', 'run', '--rm', '-i', '--network=host', '-v', "${project.findProperty('data') ?: "$project.buildDir/data"}:/ldproxy/data", '-e', 'XTRAPLATFORM_ENV=DEVELOPMENT', '-e', "JAVA_OPTS=-agentlib:jdwp=transport=dt_socket,server=y,suspend=${project.findProperty('debug') ? 'y' : 'n'},address=*:5005", 'iide/ldproxy:local-dev'
            } else {
                commandLine 'docker', 'run', '--rm', '-i', '-p', '7080:7080', '-p', '7081:7081', '-p', '5005:5005', '-v', "${project.findProperty('data') ?: "$project.buildDir/data"}:/ldproxy/data", '-e', 'XTRAPLATFORM_ENV=DEVELOPMENT', '-e', "JAVA_OPTS=-agentlib:jdwp=transport=dt_socket,server=y,suspend=${project.findProperty('debug') ? 'y' : 'n'},address=*:5005", 'iide/ldproxy:local-dev'
            }
        }


        if (!project.useNativeRun) {
            project.tasks.run.finalizedBy project.tasks.dockerRun
        }
    }

    //TODO: use ClassGenerator
    void addCreateRuntimeClassTask(Project project, appExtension) {
        project.application.mainClass = "de.ii.xtraplatform.application.Launcher"
        project.application.mainModule = "de.ii.${project.name.replaceAll('-', '.')}"

        File generatedSourceDir = new File(project.buildDir, 'generated/src/main/java/')
        project.mkdir(generatedSourceDir)

        project.sourceSets.main.java { project.sourceSets.main.java.srcDir generatedSourceDir }

        project.task('createLauncher') {
            inputs.files project.configurations.layers
            inputs.files project.configurations.layerModules
            inputs.files project.configurations.featureDevOnly
            inputs.files project.configurations.modules
            inputs.property("name", { appExtension.name2 })
            inputs.property("version", { appExtension.version2 })
            inputs.property("baseConfigs", { appExtension.additionalBaseConfigs })
            outputs.dir(generatedSourceDir)

            doLast {

                def modules = createModules(project, [LayerPlugin.XTRAPLATFORM_RUNTIME])
                def skipped = getSkippedModules(modules)
                def baseConfigs = createBaseConfigList(appExtension.additionalBaseConfigs)

                def graphs = modules.keySet().collect {
                    """
                        @Singleton
                        @Component(modules={${modules.get(it)}})
                        interface AppComponent_${it} extends App {
                            @Component.Builder
                            interface Builder extends App.Builder {
                            }
                        }
                    """
                }.join("\n")

                def skips = "static Map<String, List<String>> skipped = new LinkedHashMap<>();\nstatic {\n" + skipped.keySet().collect {
                    "skipped.put(\"${it}\", ${skipped.get(it).isEmpty() ? 'List.of()' : skipped.get(it).stream().collect(Collectors.joining("\", \"", "List.of(\"", "\")"))});\n"
                }.join("\n") + "\n}\n"

                def mainClass = """
                    package de.ii.xtraplatform.application;

                    import dagger.Component;
                    import de.ii.xtraplatform.base.domain.AppLauncher;
                    import de.ii.xtraplatform.base.domain.App;
                    import com.google.common.collect.ImmutableList;
                    import com.google.common.collect.ImmutableMap;
                    import com.google.common.io.ByteSource;
                    import com.google.common.io.Resources;
                    import java.lang.reflect.Method;
                    import java.lang.Runtime;
                    import java.util.AbstractMap.SimpleEntry;
                    import java.util.LinkedHashMap;
                    import java.util.Map;
                    import java.util.List;
                    import javax.inject.Singleton;
        
                    public class Launcher {

                        ${graphs}

                        ${skips}

                        public static void main(String[] args) throws Exception {                            
                            AppLauncher launcher = new AppLauncher("${appExtension.name2}", "${appExtension.version2}");
                            Map<String, ByteSource> baseConfigs = ${baseConfigs}.stream()
                                .map(cfgPath -> new SimpleEntry<>(cfgPath, Resources.asByteSource(Resources.getResource(Launcher.class, cfgPath))))
                                .collect(ImmutableMap.toImmutableMap(Map.Entry::getKey, Map.Entry::getValue));
                                
                            String graph = launcher.init(args, baseConfigs);
                            
                            App app;
                            try {
                              Class<?> daggerGraph = Class.forName("de.ii.xtraplatform.application.DaggerLauncher_AppComponent_" + graph);
                              Method getBuilder = daggerGraph.getMethod("builder");
                              App.Builder builder = (App.Builder) getBuilder.invoke(null);
                              app = builder.appContext(launcher).build();
                            } catch (Throwable e) {
                              e.printStackTrace();
                              throw new IllegalStateException("Dagger graph not found: " + graph);
                            }                    
                            
                            launcher.start(app, skipped.get(graph));
                            
                            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                                Thread.currentThread().setName("shutdown");
                                launcher.stop(app);
                            }));
                        }
                    }
                """

                File packageDir = new File(generatedSourceDir, 'de/ii/xtraplatform/application/')
                packageDir.mkdirs()

                //println packageDir
                //println mainClass

                new File(packageDir, "Launcher.java").write(mainClass)
            }
        }

        project.tasks.compileJava.with {
            inputs.dir(generatedSourceDir)
            dependsOn project.tasks.createLauncher
        }

    }

    List<Set<ResolvedDependency>> getModules(Project project) {
        def includedBuilds = CompositePlugin.getIncludedBuildNames(project)
        def deps = project.configurations.layers.resolvedConfiguration.firstLevelModuleDependencies.findAll({ feature -> includedBuilds.contains(feature.moduleName) }) + project.configurations.layerModules.resolvedConfiguration.firstLevelModuleDependencies.findAll({ feature -> feature.moduleName.endsWith("-modules") })
        def features = sortByDependencyGraph(deps)
        def bundles = features.collect({ it.children.findAll({ bundle -> !(bundle in features) }) })

        bundles.add(project.configurations.modules.resolvedConfiguration.firstLevelModuleDependencies)

        return bundles
    }

    Map<String, String> createModules(Project project, List<String> excludeNames = []) {
        def modules = getModules(project)
        Map<String, String> moduleSets = [:]

        for (Maturity minMaturity : Maturity.values()) {
            for (Maintenance minMaintenance : Maintenance.values()) {
                Set<String> moduleNames = []
                modules.eachWithIndex { module, index ->
                    def mods = module
                            .collectMany({ ResolvedDependency it -> it.moduleArtifacts })
                            .findAll({ ResolvedArtifact it -> !(it.name in excludeNames) && !it.name.endsWith("-tpl") })
                            .collect({ ResolvedArtifact it ->
                                Manifest manifest = readManifest(project, it.file)

                                return [
                                        "name"          : ModulePlugin.getModuleName(it.moduleVersion.id.group, it.moduleVersion.id.name),
                                        "shortName"     : it.moduleVersion.id.name,
                                        "maturity"      : manifest.getMainAttributes().getValue("Maturity"),
                                        "maintenance"   : manifest.getMainAttributes().getValue("Maintenance"),
                                        "replacementFor": manifest.getMainAttributes().getValue("Replacement-For")
                                ]
                            })
                            .findAll({ Map<String, String> mod ->
                                return !Maturity.valueOf(mod.maturity).isLowerThan(minMaturity)
                                        && !Maintenance.valueOf(mod.maintenance).isLowerThan(minMaintenance)
                            })


                    def modNames = mods
                            .findAll({ Map<String, String> mod ->
                                return Objects.isNull(mod.replacementFor) || !mods.any { it.shortName == mod.replacementFor }
                            })
                            .collect({ Map<String, String> mod -> mod.name + ".domain.AutoBindings.class" })

                    moduleNames.addAll(modNames)
                }

                moduleSets.put(ModulePlugin.getModuleLevel(minMaturity, minMaintenance), moduleNames.join(", "))
            }
        }

        return moduleSets
    }

    Map<String, List<String>> getSkippedModules(Map<String, String> modules) {
        return modules.keySet().stream()
                .map { String level ->
                    Set<String> skipped = (modules.get("PROPOSAL_NONE").split(", ") as Set).minus(modules.get(level).split(", ") as Set)

                    Map.entry(level, skipped.stream()
                            .map { ModulePlugin.getModuleNameShort(it.replace(".domain.AutoBindings.class", "")) }
                            .filter { !it.isBlank() }
                            .sorted()
                            .collect(Collectors.toList()))
                }
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue))
    }

    Set<ResolvedDependency> sortByDependencyGraph(Set<ResolvedDependency> features) {
        return features.toSorted { featureA, featureB ->
            if (featureA == featureB) return 0
            def dependsOn = featureA.children.stream().anyMatch({ child -> child == featureB })
            return dependsOn ? 1 : -1
        }
    }

    String createBaseConfigList(List<String> baseConfigs) {
        String baseConfigList = 'ImmutableList.<String>of('

        baseConfigs.eachWithIndex { baseConfig, index ->

            baseConfigList += "\"${baseConfig}\""

            if (index < baseConfigs.size() - 1) {
                baseConfigList += ','
            }
        }

        baseConfigList += ')'
    }

    boolean manifestContains(Project project, File jar, String value) {
        if (value == null || value.isEmpty()) return false;

        Pattern pattern = stringToRegex(value)

        return project
                .zipTree(jar)
                .matching({ it.include('**/META-INF/MANIFEST.MF') })
                .files
                .any({ manifest -> pattern.matcher(manifest.text).find() })
    }

    Manifest readManifest(Project project, File jar) {
        File manifest = project
                .zipTree(jar)
                .matching({ it.include('**/META-INF/MANIFEST.MF') })
                .singleFile
        return new Manifest(new FileInputStream(manifest))
    }

    Pattern stringToRegex(String value) {
        String pattern = ''
        value.each { ch ->
            pattern += ch.replaceAll("([^a-zA-Z0-9 ])", '\\\\$1') + '\\s*'
        }
        return Pattern.compile(pattern)
    }
}
