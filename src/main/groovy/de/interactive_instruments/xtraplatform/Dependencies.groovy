package de.interactive_instruments.xtraplatform

import org.gradle.api.artifacts.ResolvedDependency
import org.slf4j.LoggerFactory

import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.zip.ZipEntry

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

    static Set<Map<String,String>> getPackages(dependencies) {
        def packages = [] as Set
        def seen = [] as Set

        dependencies.each { dep ->
            dep.moduleArtifacts.each { art ->
                //println " - artifact " + art.file.absolutePath

                // Your jar file
                JarFile jar = new JarFile(art.file);
                // Getting the files into the jar
                Enumeration<? extends JarEntry> enumeration = jar.entries();

                // Iterates into the files in the jar file
                while (enumeration.hasMoreElements()) {
                    ZipEntry zipEntry = enumeration.nextElement();

                    // Is this a class?
                    if (zipEntry.getName().endsWith(".class") && zipEntry.getName().indexOf('/') > -1) {
                        def name = zipEntry.getName().substring(0, zipEntry.getName().lastIndexOf('/')).replace('/', '.')
                        if (!seen.contains(name)) {
                            packages.add([name: name, version: dep.moduleVersion.replaceAll('(-[\\w]+)+$', ''), dep: dep.name])
                            seen.add(name)
                        }
                    }
                }
            }
        }
        /*packages.each { pkg ->
            println "package " + pkg
            }*/

        return packages
    }
}
