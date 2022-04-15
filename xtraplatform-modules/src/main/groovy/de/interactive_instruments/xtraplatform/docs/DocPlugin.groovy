package de.interactive_instruments.xtraplatform.docs

import groovy.json.JsonOutput
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.external.javadoc.JavadocMemberLevel

class DocPlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {
        def docsTask = project.task("docs", type: DocsTask) {
            group = 'Documentation'
            description = 'Generates XtraPlatform Docs'
        }

        project.subprojects { subProject ->
            subProject.task("bundleDocs", type: Javadoc) {
                outputs.upToDateWhen { false }
                dependsOn subProject.tasks.findByName('jar')
                group = 'Documentation'
                description = 'Generates Bundle Manifest and Javadoc as JSON'
                source = subProject.sourceSets.main.allJava
                //exclude '**/build/generated/**/*'
                exclude {  if (it.file.absolutePath.contains('/build/generated/') && it.file.name != "module-info.java") { println it.file.absolutePath; return true }; return false }
                classpath = subProject.sourceSets.main.compileClasspath
                destinationDir = subProject.reporting.file("mod-docs")
                options.with {
                    doclet = XtraPlatformDoclet.class.name
                    docletpath = project.buildscript.configurations.classpath.resolvedConfiguration.files as List
                    memberLevel = JavadocMemberLevel.PRIVATE
                }
                doFirst {
                    ModuleDocs docs = new ModuleDocs(name: subProject.name, version: subProject.version)
                    options.addStringOption('modinfo', JsonOutput.toJson(docs))
                }

                docsTask.sources outputs.files
            }
        }



        /*def doclet = project.files('/home/zahnen/development/javadoc-json-doclet/build/libs/javadoc-json-doclet-all.jar')

        project.configurations.create("doc")

        project.dependencies.add('doc', doclet)

        project.task('generateDocs', type: Javadoc) {
            group = 'Documentation'
            description = 'bla'
            dependsOn project.tasks.jar
            source = project.sourceSets.main.allJava
            destinationDir = project.reporting.file("xtraplatform-docs")

            options.docletpath = project.configurations.doc.files.asType(List)
            options.doclet = "com.raidandfade.JsonDoclet.Main"
            //options.doclet = "com.rga78.javadoc.JsonDoclet"
            options.addStringOption("jaxrscontext", "http://localhost:8080/myapp")
            options.memberLevel = JavadocMemberLevel.PRIVATE

            doFirst {
                project.tasks.generateDocs.options.addStringOption("manifest", project.jar.manifest.effectiveManifest.attributes.collect { k, v -> "$k==$v" }.join('&&').replaceAll(/,(?=([^"]*"[^"]*")*[^"]*$)/, '||'))
            }
        }*/

        //subproject.tasks.javadoc.dependsOn subproject.configurations.doc

        /*project.tasks.jar.doLast {
            project.tasks.generateDocs.options.addStringOption("manifest", project.jar.manifest.effectiveManifest.attributes.collect { k, v -> "$k==$v" }.join('&&').replaceAll(/,(?=([^"]*"[^"]*")*[^"]*$)/, '||'))
        }*/
    }
}
