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

            // components shared by multiple layers, e.g. libraries or native libs,
            // modules referenced by layers and by the application,
            // npm packages installed in multiple paths
            Map<String, String> refs = deduplicationRefs(allComponents)
            Set<String> kept = [] as Set
            main['components'] = allComponents.findAll { refs[it['bom-ref'] as String] == it['bom-ref'] && kept.add(it['bom-ref'] as String) }
            main['dependencies'] = deduplicateDependencies(allDependencies, refs)
        }

        getOutputDir().text = JsonOutput.toJson(main)

    }

    // maps every bom-ref to the bom-ref of the first component with the same identity
    static Map<String, String> deduplicationRefs(List<Map<String, Object>> components) {
        Map<String, String> refByIdentity = [:]
        Map<String, String> refs = [:]
        components.each {
            String ref = it['bom-ref']
            refs.putIfAbsent(ref, refByIdentity.putIfAbsent(identity(it), ref) ?: ref)
        }
        return refs
    }

    // purl without qualifiers that do not change the artifact
    static String identity(Map<String, Object> component) {
        String purl = component['purl']
        if (purl == null) {
            return component['bom-ref']
        }
        int hash = purl.indexOf('#')
        String subpath = hash >= 0 ? purl.substring(hash) : ''
        String rest = hash >= 0 ? purl.substring(0, hash) : purl
        int question = rest.indexOf('?')
        String base = question >= 0 ? rest.substring(0, question) : rest
        List<String> qualifiers = question >= 0
                ? rest.substring(question + 1).split('&').findAll { it && !it.startsWith('project_path=') && it != 'type=jar' }.sort()
                : []

        return base + (qualifiers ? '?' + qualifiers.join('&') : '') + subpath
    }

    static List<Map<String, Object>> deduplicateDependencies(List<Map<String, Object>> dependencies, Map<String, String> refs) {
        Map<String, Map<String, Object>> unique = new LinkedHashMap<>()
        dependencies.each {
            String ref = refs.get(it['ref']) ?: it['ref']
            List dependsOn = ((it['dependsOn'] ?: []) as List).collect { refs.get(it) ?: it }.findAll { it != ref }
            def existing = unique.get(ref)
            if (existing == null) {
                unique.put(ref, [ref: ref, dependsOn: dependsOn.unique()])
            } else {
                existing['dependsOn'] = (((existing['dependsOn'] ?: []) as List) + dependsOn).unique()
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
