package de.interactive_instruments.xtraplatform

import org.gradle.api.Project

class ClassGenerator {


    static void generateClassTask(Project project, String taskName, String packageName, String className, Closure taskConfiguration, Closure sourceCodeSupplier) {

        File generatedSourceDir = new File(project.buildDir, 'generated/src/main/java/')
        project.mkdir(generatedSourceDir)

        project.sourceSets.main.java { project.sourceSets.main.java.srcDir generatedSourceDir }

        def newTask = project.task(taskName)

        newTask.with { taskConfiguration }

        newTask.doLast {
            def sourceCode = sourceCodeSupplier()

            File packageDir = new File(generatedSourceDir, packageName)
            packageDir.mkdirs()

            new File(packageDir, "${className}.java").write(sourceCode)
        }

        project.tasks.compileJava.with {
            inputs.dir(generatedSourceDir)
            dependsOn newTask
        }

    }

}
