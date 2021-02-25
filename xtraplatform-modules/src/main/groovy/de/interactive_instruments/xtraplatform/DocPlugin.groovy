package de.interactive_instruments.xtraplatform

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.external.javadoc.JavadocMemberLevel

import java.util.jar.JarFile

class DocPlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {
        def docsTask = project.task("docs", type: DocsTask) {
            group = 'Documentation'
            description = 'Generates XtraPlatform Docs'
        }

        project.subprojects { subProject ->
            subProject.task("bundleDocs", type: Javadoc) {
                dependsOn subProject.tasks.findByName('jar')
                group = 'Documentation'
                description = 'Generates Bundle Manifest and Javadoc as JSON'
                source = subProject.sourceSets.main.allJava
                classpath = subProject.sourceSets.main.compileClasspath
                destinationDir = subProject.reporting.file("xtraplatform-docs")
                options.with {
                    doclet = XtraPlatformDoclet.class.name
                    docletpath = project.buildscript.configurations.classpath.resolvedConfiguration.files as List
                    memberLevel = JavadocMemberLevel.PRIVATE
                }
                doFirst {
                    def manifest = new JarFile(subProject.tasks.jar.outputs.files.singleFile).manifest.mainAttributes;
                    //def manifest = project.jar.manifest.effectiveManifest.attributes;

                    options.addStringOption('manifest', manifest.collect { k, v ->
                        if ("$k" == 'Import-Package')
                            return "$k==${v.replaceAll(',(?=[a-z])', '||')}";
                        if ("$k" == 'Export-Package')
                            return "$k==${v.replaceAll('(?<="),(?=[a-z])', '||')}";
                        else
                            return "$k==$v"
                    }.join('&&'))
                    //options.addStringOption('manifest', project.jar.manifest.effectiveManifest.attributes.collect { k,v -> if (k.equals("Import-Package")) return "$k==${v.replaceAll(',(?=[a-z])', '||')}" ; else return "$k==$v" }.join('&&'))
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
