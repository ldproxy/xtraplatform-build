package de.interactive_instruments.xtraplatform

import org.gradle.api.Project

class ClassGenerator {


    static void generateClassTask(Project project, String taskName, String packageName, String className, Closure taskConfiguration, Closure sourceCodeSupplier, String generated = "generated/src/main/java/") {

        File generatedSourceDir = new File(project.buildDir, generated)
        generatedSourceDir.mkdirs()

        if (generated.startsWith('generated'))
        project.sourceSets.main.java { project.sourceSets.main.java.srcDir generatedSourceDir }

        def newTask = project.task(taskName)

        newTask.with { taskConfiguration }

        newTask.doLast {
            def sourceCode = sourceCodeSupplier()

            File packageDir = new File(generatedSourceDir, packageName.replaceAll("\\.", "/"))
            packageDir.mkdirs()

            new File(packageDir, "${className}.java").write(sourceCode)
        }

        project.tasks.compileJava.with {
            inputs.dir(generatedSourceDir)
            dependsOn newTask
        }

    }

}
