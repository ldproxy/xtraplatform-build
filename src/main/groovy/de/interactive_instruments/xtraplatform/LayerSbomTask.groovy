package de.interactive_instruments.xtraplatform

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.*

import java.nio.charset.StandardCharsets

/**
 * Adds the non-Java components declared via {@link SbomExtension} to the layer SBOM created by cyclonedxBom.
 * Each component is attached to the dependencies of the module that declared it.
 */
@CacheableTask
abstract class LayerSbomTask extends DefaultTask {

    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    abstract RegularFileProperty getBaseSbom()

    @Nested
    abstract ListProperty<SbomExtension> getModules()

    @OutputFile
    abstract RegularFileProperty getOutputFile()

    @TaskAction
    void generate() {
        def jsonSlurper = new JsonSlurper()
        Map<String, Object> bom = jsonSlurper.parse(getBaseSbom().get().asFile) as Map<String, Object>
        List<Map<String, Object>> components = (bom['components'] ?: []) as List<Map<String, Object>>
        List<Map<String, Object>> dependencies = (bom['dependencies'] ?: []) as List<Map<String, Object>>

        getModules().get().each { SbomExtension module ->
            List<Map<String, Object>> declared = module.getComponents().get()
            Set<File> fragments = module.getFragments().files

            if (declared.isEmpty() && fragments.isEmpty()) {
                return
            }

            def moduleRef = findModuleRef(components, module.getModulePath().get())
            def roots = []

            def refs = declared.collectEntries { [(it['name']): purl(it)] }
            def dependedOn = declared.collectMany { (it['dependsOn'] ?: []) as List }.toSet()

            declared.each { declaration ->
                def component = toComponent(declaration)
                addComponent(components, component)
                def dependsOn = ((declaration['dependsOn'] ?: []) as List).collect {
                    if (!refs.containsKey(it)) {
                        throw new GradleException("SBOM component '${declaration['name']}' of module '${module.getModulePath().get()}' depends on unknown component '${it}'")
                    }
                    refs[it]
                }
                addDependency(dependencies, component['bom-ref'] as String, dependsOn)
                if (!dependedOn.contains(declaration['name'])) {
                    roots.add(component['bom-ref'])
                }
            }

            fragments.each { file ->
                Map<String, Object> fragment = jsonSlurper.parse(file) as Map<String, Object>
                def root = (fragment['metadata'] as Map)?.get('component') as Map<String, Object>
                if (root != null) {
                    addComponent(components, root)
                    roots.add(root['bom-ref'])
                }
                ((fragment['components'] ?: []) as List<Map<String, Object>>).each { addComponent(components, it) }
                ((fragment['dependencies'] ?: []) as List<Map<String, Object>>).each {
                    addDependency(dependencies, it['ref'] as String, (it['dependsOn'] ?: []) as List)
                }
            }

            addDependency(dependencies, moduleRef, roots)
        }

        bom['components'] = components
        bom['dependencies'] = dependencies

        getOutputFile().get().asFile.text = JsonOutput.toJson(bom)
    }

    static String findModuleRef(List<Map<String, Object>> components, String modulePath) {
        def suffix = "?project_path=" + URLEncoder.encode(modulePath, StandardCharsets.UTF_8)
        def module = components.find { (it['bom-ref'] as String)?.endsWith(suffix) }

        if (module == null) {
            throw new GradleException("Module '${modulePath}' not found in layer SBOM")
        }

        return module['bom-ref']
    }

    static String purl(Map<String, Object> declaration) {
        return declaration['purl'] ?: "pkg:generic/${declaration['name']}@${declaration['version']}"
    }

    static Map<String, Object> toComponent(Map<String, Object> declaration) {
        ['name', 'version'].each {
            if (!declaration[it]) {
                throw new GradleException("SBOM component is missing '${it}': ${declaration}")
            }
        }

        def ref = purl(declaration)
        Map<String, Object> component = [type: declaration['type'] ?: 'library', 'bom-ref': ref]

        if (declaration['group']) component['group'] = declaration['group']
        component['name'] = declaration['name']
        component['version'] = declaration['version']
        if (declaration['description']) component['description'] = declaration['description']
        if (declaration['sha256']) component['hashes'] = [[alg: 'SHA-256', content: declaration['sha256']]]
        if (declaration['license']) {
            def licenses = declaration['license'] instanceof List ? declaration['license'] : [declaration['license']]
            component['licenses'] = licenses.size() == 1 && licenses[0] =~ / (OR|AND|WITH) /
                    ? [[expression: licenses[0]]]
                    : licenses.collect { [license: [id: it]] }
        }
        if (declaration['cpe']) component['cpe'] = declaration['cpe']
        component['purl'] = ref

        def externalReferences = [distribution: declaration['url'], website: declaration['website'], vcs: declaration['vcs']]
                .findAll { it.value }
                .collect { [type: it.key, url: it.value] }
        if (!externalReferences.isEmpty()) component['externalReferences'] = externalReferences

        return component
    }

    static void addComponent(List<Map<String, Object>> components, Map<String, Object> component) {
        if (!components.any { it['bom-ref'] == component['bom-ref'] }) {
            components.add(component)
        }
    }

    static void addDependency(List<Map<String, Object>> dependencies, String ref, List dependsOn) {
        def dependency = dependencies.find { it['ref'] == ref }

        if (dependency == null) {
            dependencies.add([ref: ref, dependsOn: dependsOn.unique()])
        } else {
            dependency['dependsOn'] = (((dependency['dependsOn'] ?: []) as List) + dependsOn).unique()
        }
    }
}
