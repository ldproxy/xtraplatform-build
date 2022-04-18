package de.interactive_instruments.xtraplatform.docs

import com.google.gson.Gson
import de.interactive_instruments.xtraplatform.Maturity
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.external.javadoc.JavadocMemberLevel
import org.gradle.external.javadoc.JavadocOutputLevel

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
                outputs.upToDateWhen { false }
                dependsOn subProject.tasks.findByName('jar')
                group = 'Documentation'
                description = 'Generates module docs'
                source = subProject.sourceSets.main.allJava
                //exclude '**/build/generated/**/*'
                /*exclude {
                    if (it.file.absolutePath.contains('/build/generated/') && it.file.name != "module-info.java") {
                        //println it.file.absolutePath;
                        return true
                    }
                    return false
                }*/
                classpath = subProject.sourceSets.main.compileClasspath + subProject.files(subProject.tasks.jar)
                destinationDir = subProject.reporting.file("mod-docs")
                options.with {
                    doclet = XtraPlatformDoclet.class.name
                    docletpath = project.buildscript.configurations.classpath.resolvedConfiguration.files as List
                    memberLevel = JavadocMemberLevel.PRIVATE
                    //outputLevel = JavadocOutputLevel.VERBOSE
                }
                doFirst {
                    ModuleDocs docs = new ModuleDocs(
                            id: subProject.moduleInfo.name.toString(),
                            name: subProject.name.toString(),
                            version: subProject.version.toString(),
                            description: Objects.requireNonNullElse(subProject.description, '').toString(),
                            maturity: subProject.maturity as Maturity,
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
