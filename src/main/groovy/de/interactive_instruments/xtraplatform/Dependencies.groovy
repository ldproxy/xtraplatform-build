package de.interactive_instruments.xtraplatform

import org.gradle.api.artifacts.FileCollectionDependency
import org.gradle.api.artifacts.ResolvedDependency
import org.slf4j.LoggerFactory

import java.util.jar.JarFile

class Dependencies {

    static LOGGER = LoggerFactory.getLogger(Dependencies.class)

    /**
     * Gets the list of ResolvedDependencies for the list of embeded dependency names
     * @param embededList the list with the dependencies to embed
     * @param recursive The embed transitive state
     * @return the list of dependencies. An empty Set if none
     */
    static Set<ResolvedDependency> getDependencies(project, configuration, excludes, recursive = false, noLog = false) {
        def dependencies = [] as Set //resolved Dependencies


        project.configurations[configuration].resolvedConfiguration.firstLevelModuleDependencies.each { dependency ->
            if (!noLog) LOGGER.info("embedding dependency (recursive: ${recursive}): {}", dependency.moduleName)
            dependencies.addAll(getDependenciesRecursive(dependency, recursive, excludes))

        }

        return dependencies
    }

    static Set<ResolvedDependency> getDependenciesRecursive(dependency, recursive, excludes) {
        def dependencies = [] as Set
        //println "dependency "+dependency.name
        if (recursive) {
            dependency.children.each { child ->
                //println "  child "+child.name+" Parents: "+child.parents
                dependencies.addAll(getDependenciesRecursive(child, recursive, excludes))
            }
        }

        if (!(dependency.moduleName in excludes))
            dependencies.add(dependency)

        return dependencies
    }

    static Set<File> getFileDependencies(project, configuration) {
        return project.configurations[configuration].allDependencies
                .withType(FileCollectionDependency)
                .collectMany { it.files.files } as Set
    }

    static Set<Map<String,String>> getPackages(dependencies, Set<File> files = []) {
        def packages = [] as Set
        def seen = [] as Set
        def addPackage = { String name, Map<String,String> origin ->
            if (!seen.contains(name)) {
                packages.add([name: name] + origin)
                seen.add(name)
            }
        }

        dependencies.each { dep ->
            dep.moduleArtifacts.each { art ->
                getPackageNames(art.file).each { addPackage(it, [version: dep.moduleVersion.replaceAll('(-[\\w]+)+$', ''), dep: dep.name]) }
            }
        }
        // file dependencies have no module version, which is fine since only the package names end up in module-info
        files.findAll { it.isFile() }.each { file ->
            getPackageNames(file).each { addPackage(it, [dep: file.name]) }
        }

        return packages
    }

    static Set<String> getPackageNames(File jarFile) {
        new JarFile(jarFile).withCloseable { jar ->
            jar.entries().findAll { it.name.endsWith(".class") && it.name.contains('/') }
                    .collect { it.name.substring(0, it.name.lastIndexOf('/')).replace('/', '.') } as LinkedHashSet
        }
    }
}
