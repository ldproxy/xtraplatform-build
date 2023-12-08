package de.interactive_instruments.xtraplatform.docs

import com.google.gson.Gson
import de.interactive_instruments.xtraplatform.Maintenance
import de.interactive_instruments.xtraplatform.Maturity
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.external.javadoc.JavadocMemberLevel

class DocPlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {
        def docsTask = project.task("layerDocs", type: LayerDocsTask) {
            group = 'Documentation'
            description = 'Generates layer docs'
        }

        project.sourceSets.main.resources.srcDir(new File(project.buildDir, "generated/sources/annotationProcessor/resources/main"))
        //project.sourceSets.main.output.dir(new File(project.buildDir, "generated/sources/annotationProcessor/resources/main"))

        project.subprojects { subProject ->
            def modTask = subProject.task("moduleDocs", type: Javadoc) {
                dependsOn subProject.tasks.named('jar')
                group = 'Documentation'
                description = 'Generates module docs'
                source = subProject.sourceSets.main.allJava.filter { it.name != 'module-info.java' }
                classpath = subProject.sourceSets.main.compileClasspath + subProject.files(new File(subProject.buildDir, 'classes/java/main'))
                modularity.inferModulePath = false
                destinationDir = new File(subProject.buildDir, 'tmp/module-docs')
                options.with {
                    doclet = XtraPlatformDoclet.class.name
                    docletpath = project.buildscript.configurations.classpath.resolvedConfiguration.files as List
                    memberLevel = JavadocMemberLevel.PUBLIC
                    //outputLevel = JavadocOutputLevel.VERBOSE
                }
                doFirst {
                    ModuleDocs docs = new ModuleDocs(
                            id: subProject.moduleInfo.name.toString(),
                            name: subProject.name.toString(),
                            version: subProject.version.toString(),
                            description: Objects.requireNonNullElse(subProject.description, '').toString(),
                            maturity: subProject.maturity as Maturity,
                            maintenance: subProject.maintenance as Maintenance,
                            deprecated: subProject.deprecated,
                            exports: subProject.moduleInfo.exports.collect { it.toString() },
                            requires: subProject.moduleInfo.requires.collect { it.toString() },
                    )
                    options.addStringOption('modinfo', new Gson().toJson(docs))
                }

                docsTask.sources outputs.files
            }
            docsTask.dependsOn modTask
            project.tasks.named("processResources").configure {
                dependsOn docsTask
            }
        }
    }
}
