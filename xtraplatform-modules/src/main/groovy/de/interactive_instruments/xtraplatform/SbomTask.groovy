package de.interactive_instruments.xtraplatform

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.gradle.api.DefaultTask
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.*

@CacheableTask
class SbomTask extends DefaultTask {
    private FileCollection sourceFiles = project.files(project.configurations.layerDocs.resolvedConfiguration.firstLevelModuleDependencies.collectMany { it.moduleArtifacts }.collect { it.file }) + project.files(project.tasks.jar);
    private File outputDir = new File(project.buildDir, "reports")

    @SkipWhenEmpty
    @InputFiles
    @PathSensitive(PathSensitivity.NONE)
    FileCollection getSourceFiles() {
        return this.sourceFiles;
    }

    void sources(FileCollection sourceFiles) {
        this.sourceFiles = this.sourceFiles + sourceFiles;
    }

    @OutputFile
    File getOutputDir() {
        return new File(outputDir, "sbom.json")
    }

    @TaskAction
    void generateFiles() {
        List<Map<String, Object>> sboms = loadLayerSboms()
        //println "SBOMS: " + sboms.collect { it['bomFormat'] }

        if (sboms.isEmpty()) {
            println "EMPTY"
            return
        }

        Map<String, Object> main = sboms.get(sboms.size() - 1)

        if (sboms.size() > 1) {
            // merge components and dependencies
            def allComponents = sboms.collectMany { ((List) it['components']) }
            def allDependencies = sboms.collectMany { ((List) it['dependencies']) }
            main['components'] = allComponents
            main['dependencies'] = allDependencies

            // get main dependencies
            def appRef = main['metadata']['component']['purl']
            def deps = allDependencies.find { it['ref'] == appRef }

            // add modules from layers to main dependencies
            def layerRefs = sboms.subList(0, sboms.size() - 1).collect { it['metadata']['component']['purl'] }
            def modRefs = allDependencies.findAll { layerRefs.contains(it['ref']) }.collectMany { ((List) it['dependsOn']) }
            deps['dependsOn'] += modRefs

            // remove layers
            allDependencies.removeAll { layerRefs.contains(it['ref']) }
        }

        getOutputDir().text = JsonOutput.toJson(main)

    }

    protected List<Map<String, Object>> loadLayerSboms() {
        List<Map<String, Object>> layers = []
        def jsonSlurper = new JsonSlurper()

        getSourceFiles().files.each {
            try {
                def sbom = project.zipTree(it).matching { include 'sbom.json' }.singleFile
                Map<String, Object> sb = jsonSlurper.parse(sbom)
                layers.add(sb)
            } catch (Throwable e) {
                println "NO SBOM for " + it.name
            }
        }

        return layers
    }

}
