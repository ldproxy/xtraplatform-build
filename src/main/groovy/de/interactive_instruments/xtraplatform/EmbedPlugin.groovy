package de.interactive_instruments.xtraplatform

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.github.jengelman.gradle.plugins.shadow.transformers.ManifestResourceTransformer
import groovy.io.FileType
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.UnknownDomainObjectException
import org.gradle.api.XmlProvider
import org.gradle.api.artifacts.*
import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.Usage
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.Bundling
import org.gradle.api.attributes.DocsType
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.attributes.java.TargetJvmEnvironment
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.internal.artifacts.dependencies.DefaultExternalModuleDependency
import org.gradle.api.plugins.quality.Pmd
import org.gradle.api.provider.Provider
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test

import java.math.RoundingMode
import java.nio.file.Path
import java.util.stream.Collectors
import java.util.stream.StreamSupport

class EmbedPlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {
        if (project.logger.isInfoEnabled()) {
            project.logger.info("Applying EmbedPlugin {} to {}", ApplicationPlugin.getVersion(project), project.name)
        }

        setupEmbedding(project, project.parent.moduleInfo)
    }

    static void setupEmbedding(Project project, ModuleInfoExtension moduleInfo) {
        List<Dependency> depsFlat = []
        List<Dependency> depsTransitive = []
        depsTransitive.addAll(project.parent.configurations.embedded.dependencies)
        depsTransitive.addAll(project.parent.configurations.embeddedExport.dependencies)
        depsFlat.addAll(project.parent.configurations.embeddedFlat.dependencies)
        depsFlat.addAll(project.parent.configurations.embeddedFlatExport.dependencies)
        List<Dependency> depsAll = depsFlat + depsTransitive

        /*if (deps.isEmpty()) {
            return
        } else {
            println "${moduleInfo.name} " + deps
        }*/

        project.plugins.apply('maven-publish')
        project.plugins.apply('com.gradleup.shadow')

        project.configurations.with {
            register('tpl') {
                it.canBeDeclared = true
                it.canBeConsumed = false
                it.canBeResolved = false
            }
            resolvable('tplClasspath') {
                it.extendsFrom tpl
                it.attributes {
                    it.attribute(Usage.USAGE_ATTRIBUTE, project.objects.named(Usage.class, Usage.JAVA_API))
                    it.attribute(TargetJvmEnvironment.TARGET_JVM_ENVIRONMENT_ATTRIBUTE, project.objects.named(TargetJvmEnvironment.class, TargetJvmEnvironment.STANDARD_JVM))
                }
            }

            register('tplJavadoc') {
                it.canBeDeclared = true
                it.canBeConsumed = false
                it.canBeResolved = false
            }
            resolvable('tplJavadocClasspath') {
                it.extendsFrom tplJavadoc
                it.attributes {
                    it.attribute(Usage.USAGE_ATTRIBUTE, project.objects.named(Usage.class, Usage.JAVA_API))
                    it.attribute(TargetJvmEnvironment.TARGET_JVM_ENVIRONMENT_ATTRIBUTE, project.objects.named(TargetJvmEnvironment.class, TargetJvmEnvironment.STANDARD_JVM))
                }
            }

            register('tplSources') {
                it.canBeDeclared = true
                it.canBeConsumed = false
                it.canBeResolved = false
            }
            resolvable('tplSourcesClasspath') {
                it.extendsFrom tplSources
                it.attributes {
                    it.attribute(Usage.USAGE_ATTRIBUTE, project.objects.named(Usage.class, Usage.JAVA_API))
                    it.attribute(TargetJvmEnvironment.TARGET_JVM_ENVIRONMENT_ATTRIBUTE, project.objects.named(TargetJvmEnvironment.class, TargetJvmEnvironment.STANDARD_JVM))
                }
            }

            consumable('sourcesElements') {
                it.extendsFrom tplSources
                it.attributes {
                    it.attribute(Category.CATEGORY_ATTRIBUTE, project.objects.named(Category.class, Category.DOCUMENTATION))
                    it.attribute(Bundling.BUNDLING_ATTRIBUTE, project.objects.named(Bundling.class, Bundling.EXTERNAL))
                    it.attribute(Usage.USAGE_ATTRIBUTE, project.objects.named(Usage.class, Usage.JAVA_RUNTIME))
                    it.attribute(DocsType.DOCS_TYPE_ATTRIBUTE, project.objects.named(DocsType.class, DocsType.SOURCES))
                }
            }

            register('provided') {
                it.transitive = true
                it.canBeDeclared = true
                it.canBeConsumed = false
                it.canBeResolved = false
            }
            register('compileOnly') {
                it.extendsFrom provided
                it.canBeDeclared = true
                it.canBeConsumed = false
                it.canBeResolved = false
            }
            resolvable('compileClasspath') {
                it.extendsFrom compileOnly
                it.attributes {
                    it.attribute(Usage.USAGE_ATTRIBUTE, project.objects.named(Usage.class, Usage.JAVA_API))
                    it.attribute(TargetJvmEnvironment.TARGET_JVM_ENVIRONMENT_ATTRIBUTE, project.objects.named(TargetJvmEnvironment.class, TargetJvmEnvironment.STANDARD_JVM))
                }
            }

            consumable('apiElements') {
                it.attributes {
                    it.attribute(Category.CATEGORY_ATTRIBUTE, project.objects.named(Category.class, Category.LIBRARY))
                    it.attribute(Bundling.BUNDLING_ATTRIBUTE, project.objects.named(Bundling.class, Bundling.EXTERNAL))
                    it.attribute(Usage.USAGE_ATTRIBUTE, project.objects.named(Usage.class, Usage.JAVA_API))
                    it.attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, project.objects.named(LibraryElements.class, LibraryElements.JAR))
                    it.attribute(TargetJvmEnvironment.TARGET_JVM_ENVIRONMENT_ATTRIBUTE, project.objects.named(TargetJvmEnvironment.class, TargetJvmEnvironment.STANDARD_JVM))
                }
            }
            consumable('runtimeElements') {
                it.attributes {
                    it.attribute(Category.CATEGORY_ATTRIBUTE, project.objects.named(Category.class, Category.LIBRARY))
                    it.attribute(Bundling.BUNDLING_ATTRIBUTE, project.objects.named(Bundling.class, Bundling.EXTERNAL))
                    it.attribute(Usage.USAGE_ATTRIBUTE, project.objects.named(Usage.class, Usage.JAVA_RUNTIME))
                    it.attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, project.objects.named(LibraryElements.class, LibraryElements.JAR))
                    it.attribute(TargetJvmEnvironment.TARGET_JVM_ENVIRONMENT_ATTRIBUTE, project.objects.named(TargetJvmEnvironment.class, TargetJvmEnvironment.STANDARD_JVM))
                }
            }
        }

        depsTransitive.each {
            project.dependencies.add('tpl', it)
        }
        depsFlat.each {
            project.dependencies.add('tpl', it, { transitive = false })
        }
        depsAll.each {
            if (it instanceof DefaultExternalModuleDependency) {
                project.dependencies.add('tplJavadoc', "${it.group}:${it.name}:${it.version}:javadoc")
                project.dependencies.add('tplSources', "${it.group}:${it.name}:${it.version}:sources")
            }
        }

        project.parent.configurations.provided2.dependencies.each {
            project.dependencies.add('provided', it)
        }

        File embeddedClassesDir = new File(project.parent.buildDir, 'tpl/classes/java/main')
        File generatedSourcesDir = new File(project.parent.buildDir, 'tpl/src/main/java')

        project.tasks.register('compileJava') {
            dependsOn project.tasks.named('moduleInfo')
            dependsOn project.configurations.tplClasspath
            dependsOn project.configurations.compileClasspath

            inputs.file new File(generatedSourcesDir, 'module-info.java')
            inputs.files project.configurations.tplClasspath
            inputs.files project.configurations.compileClasspath

            outputs.cacheIf { true }
            outputs.dir embeddedClassesDir

            doFirst {
                project.copy {
                    from project.configurations.tplClasspath.collect { it.isDirectory() ? it : project.zipTree(it) }
                    into embeddedClassesDir
                    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
                    exclude('**/module-info.class')
                }
            }

            doLast {
                def jars = project.files(project.configurations.tplClasspath).files + project.files(project.parent.configurations.compileClasspath).files

                ant.javac(
                        destdir: embeddedClassesDir,
                        srcdir: generatedSourcesDir,
                        includeantruntime:false,
                        failonerror: true,
                        release: LayerPlugin.JAVA_VERSION_MAJOR,
                        modulepath : jars.join(":"),
                        //verbose: true,
                )  {
                    include(name: 'module-info.java')
                    // compilerarg(value: '-Xlint')
                    compilerarg(value: "--module-version=${project.version}")
                }
            }
        }

        //TODO: javadoc jar
        project.tasks.register('jar', ShadowJar) {
            dependsOn(project.tasks.named('compileJava'))
            from embeddedClassesDir
            configurations = [
                    project.configurations.tplClasspath,
            ]

            mergeServiceFiles()
            transform(ManifestResourceTransformer) {
                manifestEntries = [
                        'Multi-Release': 'true',
                ]
            }

            exclude {
                it.isDirectory() && it.file != null && ((File)it.file).parentFile.absolutePath.endsWith("classes/java/main")
            }
            exclude {
                it.name == 'module-info.class' && it.path != "module-info.class"
            }
            exclude('META-INF/MANIFEST.MF', 'META-INF/INDEX.LIST', 'META-INF/*.SF', 'META-INF/*.DSA', 'META-INF/*.RSA')
            exclude '**/PLACEHOLDER.class'

            destinationDirectory = new File(project.buildDir, 'libs')
            archiveBaseName = project.name
            archiveVersion = project.parent.version
        }

        project.tasks.register('javadocJar', ShadowJar) {
            configurations = [
                    project.configurations.tplJavadocClasspath,
            ]

            destinationDirectory = new File(project.buildDir, 'libs')
            archiveBaseName = project.name
            archiveVersion = project.parent.version
            archiveClassifier = 'javadoc'
            exclude {
                it.name.endsWith(".jar") && !it.name.endsWith("-javadoc.jar")
            }
        }

        project.tasks.register('sourcesJar', ShadowJar) {
            configurations = [
                    project.configurations.tplSourcesClasspath,
            ]

            destinationDirectory = new File(project.buildDir, 'libs')
            archiveBaseName = project.name
            archiveVersion = project.parent.version
            archiveClassifier = 'sources'
            exclude {
                it.name.endsWith(".jar") && !it.name.endsWith("-sources.jar")
            }
        }

        project.tasks.register('initTpl') {
            dependsOn project.tasks.named('jar')
            /*doLast {
                println "INIT ${project.name} ${project.version}"
            }*/
        }

        ModuleInfoExtension moduleInfoTpl = new ModuleInfoExtension(moduleInfo);
        moduleInfoTpl.name = "${moduleInfo.name}.tpl"
        moduleInfoTpl.exports = moduleInfoTpl.exports.findAll {!it.startsWith("de.ii") }
        moduleInfo.exports = moduleInfo.exports.findAll {it.startsWith("de.ii") }
        moduleInfo.requires += "transitive ${moduleInfoTpl.name}"

        project.parent.configurations.embeddedImport.dependencies.each {
            moduleInfoTpl.requires.add(ModulePlugin.getModuleName(it.group, it.name) + ".tpl")
        }
        def runtime = ModulePlugin.getModuleName("de.interactive_instruments", "xtraplatform-runtime")
        if (moduleInfo.name != runtime) {
            moduleInfoTpl.requires.add(runtime + ".tpl")
        }

        ClassGenerator.generateClassesTask(project, 'moduleInfo', {
            outputs.cacheIf { true }
            inputs.property('moduleInfo.name', moduleInfoTpl.name)
            inputs.property('moduleInfo.exports', moduleInfoTpl.exports)
            //inputs.property('moduleInfo.requires', moduleInfoTpl.requires)
            inputs.property('moduleInfo.provides', moduleInfoTpl.provides)
            inputs.property('moduleInfo.uses', moduleInfoTpl.uses)
            outputs.file(new File(generatedSourcesDir, 'module-info.java'))
        }, [(new File(generatedSourcesDir, 'module-info.java')): ModulePlugin.generateModuleInfo(project.parent, moduleInfoTpl, false, true)])

        project.artifacts {
            apiElements project.tasks.jar
            runtimeElements project.tasks.jar
            sourcesElements project.tasks.sourcesJar
        }

        project.tasks.register('clean', Delete) {
            delete project.buildDir
        }

        project.tasks.register('check') {}

        Common.addPublishingRepos(project)

        project.publishing {
            publications {
                'default'(MavenPublication) {
                    artifact project.tasks.jar
                    artifact project.tasks.sourcesJar
                    artifact project.tasks.javadocJar

                    pom.withXml { XmlProvider xml ->
                        def node = xml.asNode()
                        if (node.get('dependencies') != null && node.get('dependencies').size() > 0) {
                            node.get('dependencies')?.replaceNode {}
                        }
                    }
                }
            }
        }
    }
}
