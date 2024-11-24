package de.interactive_instruments.xtraplatform

import org.gradle.api.Project

class ClassGenerator {

    static void generateClassesTask(Project project, String taskName, Closure taskConfiguration, Map<File,Closure<GString>> filesSupplier) {

        def newTask = project.tasks.register(taskName, taskConfiguration)

        newTask.configure {
            doLast {
                filesSupplier.each {
                    //println "Generating class ${it.key} for ${project.name} in ${generatedSourceDir}"
                    File file =  it.key
                    file.parentFile.mkdirs()
                    file.write(it.value.call())
                }
            }
        }

        project.tasks.compileJava.with {
            dependsOn newTask
        }

    }

}
