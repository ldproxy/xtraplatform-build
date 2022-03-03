package de.interactive_instruments.xtraplatform


import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.artifacts.ResolvedDependency

import java.util.regex.Pattern

/**
 * @author zahnen
 */
class ApplicationPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        project.plugins.apply(FeaturePlugin.class)
        project.plugins.apply("application")

        def appExtension = project.extensions.create('app', ApplicationExtension, project)
        ModuleInfoExtension moduleInfo = project.extensions.create('moduleInfo', ModuleInfoExtension)

        //suppress java 9+ illegal access warnings for felix, jackson afterburner, geotools/hsqldb, mustache
        project.application.applicationDefaultJvmArgs  += ['--add-opens', 'java.base/java.lang=ALL-UNNAMED', '--add-opens', 'java.base/java.net=ALL-UNNAMED', '--add-opens', 'java.base/java.security=ALL-UNNAMED', '--add-opens', 'java.base/java.nio=ALL-UNNAMED', '--add-opens', 'java.base/java.util=ALL-UNNAMED']

        project.configurations.create("featureDevOnly")
        project.configurations.featureDevOnly.resolutionStrategy.cacheDynamicVersionsFor(5, 'minutes')
        project.configurations.create("app")
        project.configurations.implementation.extendsFrom(project.configurations.app)

        def includedBuilds = getIncludedBuilds(project)

        project.afterEvaluate {
            def baseFound = false
            project.configurations.feature.dependencies.each {
                if (it.name == FeaturePlugin.XTRAPLATFORM_CORE) {
                    if (!includedBuilds.contains(it.name)) {
                        project.dependencies.add('app', project.dependencies.enforcedPlatform(it))
                    }

                    project.dependencies.add('app', "de.interactive_instruments:${FeaturePlugin.XTRAPLATFORM_RUNTIME}")
                    project.dependencies.add('app', "de.interactive_instruments:${FeaturePlugin.XTRAPLATFORM_BASE}")
                    baseFound = true
                }
            }
            if (!baseFound) {
                throw new IllegalStateException("You have to add '${FeaturePlugin.XTRAPLATFORM_CORE}' to configuration 'feature'")
            }

            def features = getModules(project)
            features.eachWithIndex { feature, index ->

                feature.collectMany({ bundle ->
                    bundle.moduleArtifacts
                }).each({ ResolvedArtifact artifact ->
                    if (!artifact.moduleVersion.id.name.startsWith("ldproxy-")) {
                        project.dependencies.add('app', "${artifact.moduleVersion.id.group}:${artifact.moduleVersion.id.name}:${artifact.moduleVersion.id.version}")
                    }
                })
            }
            project.subprojects.each {
                project.dependencies.add('app', it)
            }

            moduleInfo.name = ModulePlugin.getModuleName(project.group as String, project.name)
            ModulePlugin.setupModuleInfo(project, moduleInfo, false, true)
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

        project.dependencies.add('compileOnly', "com.google.dagger:dagger:2.+", { transitive = false })
        project.dependencies.add('annotationProcessor', "com.google.dagger:dagger-compiler:2.+")

        project.tasks.named('compileJava') {
            options.javaModuleVersion = project.provider { project.version }
        }

        addCreateRuntimeClassTask(project, appExtension)

        addDistribution(project)

        addRunConfiguration(project)
    }

    void addRunConfiguration(Project project) {
        def dataDir = new File(project.buildDir, 'data')

        project.task('initData') {
            doLast {
                dataDir.mkdirs()
            }
        }

        project.tasks.run.with {
            dependsOn project.tasks.installDist
            dependsOn project.tasks.initData
            workingDir = project.tasks.installDist.destinationDir
            args dataDir.absolutePath
            standardInput = System.in
            environment 'XTRAPLATFORM_ENV', 'DEVELOPMENT'
        }
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

    //TODO: use ClassGenerator
    void addCreateRuntimeClassTask(Project project, appExtension) {
        project.application.mainClass = "de.ii.xtraplatform.application.Launcher"
        project.application.mainModule = "de.ii.${project.name.replaceAll('-', '.')}"

        File generatedSourceDir = new File(project.buildDir, 'generated/src/main/java/')
        project.mkdir(generatedSourceDir)

        project.sourceSets.main.java { project.sourceSets.main.java.srcDir generatedSourceDir }

        project.task('createLauncher') {
            inputs.files project.configurations.feature
            inputs.files project.configurations.featureBundles
            inputs.files project.configurations.featureDevOnly
            inputs.files project.configurations.bundle
            inputs.property("name", {appExtension.name2})
            inputs.property("version", {appExtension.version2})
            inputs.property("baseConfigs", {appExtension.additionalBaseConfigs})
            outputs.dir(generatedSourceDir)

            doLast {

                def modules = createModules(project, [FeaturePlugin.XTRAPLATFORM_RUNTIME])
                def baseConfigs = createBaseConfigList(appExtension.additionalBaseConfigs)

                def mainClass = """
                    package de.ii.xtraplatform.application;

                    import dagger.Component;
                    import de.ii.xtraplatform.base.domain.AppLauncher;
                    import de.ii.xtraplatform.base.domain.App;
                    import com.google.common.collect.ImmutableList;
                    import com.google.common.collect.ImmutableMap;
                    import com.google.common.io.ByteSource;
                    import com.google.common.io.Resources;
                    import java.lang.Runtime;
                    import java.util.AbstractMap.SimpleEntry;
                    import java.util.Map;
                    import javax.inject.Singleton;
        
                    public class Launcher {

                        @Singleton
                        @Component(modules={${modules}})
                        interface AppComponent extends App {
                            @Component.Builder
                            interface Builder extends App.Builder {
                            }
                        }

                        public static void main(String[] args) throws Exception {                            
                            AppLauncher launcher = new AppLauncher("${appExtension.name2}", "${appExtension.version2}");
                            Map<String, ByteSource> baseConfigs = ${baseConfigs}.stream()
                                .map(cfgPath -> new SimpleEntry<>(cfgPath, Resources.asByteSource(Resources.getResource(Launcher.class, cfgPath))))
                                .collect(ImmutableMap.toImmutableMap(Map.Entry::getKey, Map.Entry::getValue));
                                
                            launcher.init(args, baseConfigs);
                            
                            App app = DaggerLauncher_AppComponent.builder()
                                .appContext(launcher)
                                .build();                            
                            
                            launcher.start(app);
                            
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
        def includedBuilds = getIncludedBuilds(project)
        def deps = project.configurations.feature.resolvedConfiguration.firstLevelModuleDependencies.findAll({ feature -> includedBuilds.contains(feature.moduleName)}) + project.configurations.featureBundles.resolvedConfiguration.firstLevelModuleDependencies.findAll({ feature -> feature.moduleName.endsWith("-bundles")})
        def features = sortByDependencyGraph(deps)
        def bundles = features.collect({ it.children.findAll({ bundle -> !(bundle in features) }) })

        bundles.add(project.configurations.bundle.resolvedConfiguration.firstLevelModuleDependencies)

        return bundles
    }

    String createModules(Project project, List<String> excludeNames = []) {
        def bundles = getModules(project)

        def modules = []
        bundles.eachWithIndex { feature, index ->

            def mods = feature.collectMany({ bundle ->
                bundle.moduleArtifacts
            }).findAll({ !(it.name in excludeNames) }).collect({ ResolvedArtifact it ->
                ModulePlugin.getModuleName(it.moduleVersion.id.group, it.moduleVersion.id.name) + ".domain.AutoBindings.class"
            })
            modules.addAll(mods)
        }

        return modules.join(", ")
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

    Pattern stringToRegex(String value) {
        String pattern = ''
        value.each { ch ->
            pattern += ch.replaceAll("([^a-zA-Z0-9 ])", '\\\\$1') + '\\s*'
        }
        return Pattern.compile(pattern)
    }

    Collection<String> getIncludedBuilds(Project project) {
        def includedBuilds = project.gradle.includedBuilds.collect {it.name}
        def parent = project.gradle.parent
        while (parent != null) {
            includedBuilds += parent.includedBuilds.collect {it.name}
            parent = parent.gradle.parent
        }
        return includedBuilds
    }
}
