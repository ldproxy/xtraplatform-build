package de.interactive_instruments.xtraplatform

import com.moowork.gradle.node.yarn.YarnTask
import groovy.json.JsonGenerator
import groovy.json.JsonOutput
import org.gradle.api.Action;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.tasks.Copy;
import org.gradle.api.tasks.Sync;

class DocsPlugin implements Plugin<Project> {

    def jsonGenerator = new JsonGenerator.Options()
    .excludeFieldsByName('extensions', 'convention', 'class', 'conventionMapping', 'asDynamicObject')
    .build()

    @Override
    void apply(Project project) {
        project.plugins.apply("com.moowork.node")

        NamedDomainObjectContainer<Doc> docs = project.container(Doc)
        project.extensions.add('docs', docs)

        def docsTmpDir = project.file("$project.buildDir/tmp/docs")
        def docsDir = project.file("$project.buildDir/docs")

        createInitTask(project, docsTmpDir);
        createYarnInstallTask(project, docsTmpDir);
        createConfigTasks(project, docsTmpDir, docs);
        createBuildTasks(project, docsTmpDir, docs);
        createCleanTasks(project, docsTmpDir, docs);
        createPublishTasks(project, docsTmpDir, docsDir, docs);
        createServeTasks(project, docsTmpDir, docs); 
        createDevelopTasks(project, docsTmpDir, docs); 
    }

    void createInitTask(Project project, File docsTmpDir) {
        project.task("docsInit", type: Copy) {
            /*inputs.property("logo", {project.docs.logo})
            def logoPath

            doFirst {
                if (extension.logo != "") {
                    logoPath = new File(new File(project.projectDir, extension.srcDir), extension.logo).absolutePath
                }
            }*/
            
            from(project.zipTree(project.buildscript.configurations.classpath.find { it.name.startsWith('xtraplatform-docs-gatsby') } )) {
                eachFile {
                    path -= ~/^.+?\//
                }
                include "gatsby/"
            }

            into docsTmpDir;
            includeEmptyDirs = false

            /*from({logoPath}) {
                into {"static/${extension.logo.substring(0, extension.logo.lastIndexOf('/'))}"}
                rename({'logo.png'})
            }*/
        };
    }

    void createYarnInstallTask(Project project, File docsTmpDir) {
        project.task("docsInstallDeps", type: YarnTask) {
            inputs.file("$docsTmpDir/yarn.lock")
            outputs.dir("$docsTmpDir/node_modules")
            dependsOn "docsInit"
            execOverrides {
                it.workingDir = docsTmpDir
            }
            args = ['install']
        }
    }

    void createConfigTasks(Project project, File docsTmpDir, NamedDomainObjectContainer<Doc> docs) {
        docs.all {
            def docName = name
            def taskName = "docsConfig${name.capitalize()}"
            
            project.task(taskName) {
                inputs.property("doc", {project.docs.getByName(docName)})
                outputs.file("$docsTmpDir/xtraplatform.json")
                dependsOn "docsInit"

                doLast {

                    def config = new Doc(project.extensions.docs.getByName(docName))
            
                                        
                    config.srcDir = new File(project.projectDir, config.srcDir).absolutePath

                    if (config.assetDir == "") {
                        config.assetDir = new File(project.buildDir, "tmp/assets").absolutePath
                        project.mkdir config.assetDir
                    } else {
                        config.assetDir = new File(project.projectDir, config.assetDir).absolutePath
                    }

                    new File(docsTmpDir, "xtraplatform.json").text = JsonOutput.prettyPrint(jsonGenerator.toJson( config ))
                }
            }
        }
    }

    void createCleanTasks(Project project, File docsTmpDir, NamedDomainObjectContainer<Doc> docs) {
        docs.all {
            def docName = name
            def taskName = "docsClean${name.capitalize()}"
            def configTaskName = "docsConfig${name.capitalize()}"
            
            project.task(taskName, type: YarnTask) {
                inputs.files(project.fileTree("$docsTmpDir").matching {
                    exclude "public", ".cache" // relative to the file tree's root directory
                })
                inputs.files(project.fileTree({project.extensions.docs.getByName(docName).srcDir}))
                outputs.dir("$docsTmpDir/public")
                dependsOn "docsInstallDeps"
                dependsOn configTaskName
                execOverrides {
                    it.workingDir = docsTmpDir
                }
                args = ['clean']
            }
        }
    }

    void createBuildTasks(Project project, File docsTmpDir, NamedDomainObjectContainer<Doc> docs) {
        docs.all {
            def docName = name
            def taskName = "docsBuild${name.capitalize()}"
            def configTaskName = "docsConfig${name.capitalize()}"
            def cleanTaskName = "docsClean${name.capitalize()}"
            def publishTaskName = "docsPublish${name.capitalize()}"
            
            project.task(taskName, type: YarnTask) {
                inputs.files(project.fileTree("$docsTmpDir").matching {
                    exclude "public", ".cache" // relative to the file tree's root directory
                })
                inputs.files(project.fileTree({project.extensions.docs.getByName(docName).srcDir}))
                outputs.dir("$docsTmpDir/public")
                dependsOn "docsInstallDeps"
                dependsOn configTaskName
                dependsOn cleanTaskName
                finalizedBy publishTaskName
                execOverrides {
                    it.workingDir = docsTmpDir
                }
                args = ['build']
            }
        }

        project.task("docsBuild") {
            docs.all {
                dependsOn "docsBuild${name.capitalize()}"
            }
        }
    }

    void createPublishTasks(Project project, File docsTmpDir, File docsDir, NamedDomainObjectContainer<Doc> docs) {
        docs.all {
            def docName = name
            def taskName = "docsPublish${name.capitalize()}"
            
            project.task(taskName, type: Sync) {
                from project.file("$docsTmpDir/public")
                into new File(docsDir, docName);
            }; 
        }
    }

    void createServeTasks(Project project, File docsTmpDir, NamedDomainObjectContainer<Doc> docs) {
        docs.all {
            def docName = name
            def taskName = "docsServe${name.capitalize()}"
            def buildTaskName = "docsBuild${name.capitalize()}"
            
            project.task(taskName, type: YarnTask) {
                dependsOn buildTaskName
                execOverrides {
                    it.workingDir = docsTmpDir
                }
                args = ['prod']
            }
        }
    }

    void createDevelopTasks(Project project, File docsTmpDir, NamedDomainObjectContainer<Doc> docs) {
        docs.all {
            def docName = name
            def taskName = "docsDevelop${name.capitalize()}"
            def configTaskName = "docsConfig${name.capitalize()}"
            
            project.task(taskName, type: YarnTask) {
                dependsOn configTaskName
                dependsOn "docsInstallDeps"
                execOverrides {
                    it.workingDir = docsTmpDir
                }
                args = ['develop']
            }
        }
    }
}
