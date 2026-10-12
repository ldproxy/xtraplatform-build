package de.interactive_instruments.xtraplatform

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.gradle.api.DefaultTask
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.*

@CacheableTask
class SbomTask extends DefaultTask {
    // layerDocs is not transitive, resolved lazily (eager resolution fails in composite builds)
    private FileCollection sourceFiles = project.files(project.configurations.layerDocs, project.tasks.jar);
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

            // components shared by multiple layers, e.g. libraries or native libs
            main['components'] = deduplicateComponents(allComponents)
            main['dependencies'] = deduplicateDependencies(allDependencies)
        }

        getOutputDir().text = JsonOutput.toJson(main)

    }

    static List<Map<String, Object>> deduplicateComponents(List<Map<String, Object>> components) {
        Map<Object, Map<String, Object>> unique = new LinkedHashMap<>()
        components.each { unique.putIfAbsent(it['bom-ref'] ?: it['purl'] ?: it, it) }
        return unique.values() as List
    }

    static List<Map<String, Object>> deduplicateDependencies(List<Map<String, Object>> dependencies) {
        Map<Object, Map<String, Object>> unique = new LinkedHashMap<>()
        dependencies.each {
            def existing = unique.get(it['ref'])
            if (existing == null) {
                unique.put(it['ref'], [ref: it['ref'], dependsOn: ((it['dependsOn'] ?: []) as List).unique()])
            } else {
                existing['dependsOn'] = (((existing['dependsOn'] ?: []) as List) + ((it['dependsOn'] ?: []) as List)).unique()
            }
        }
        return unique.values() as List
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
