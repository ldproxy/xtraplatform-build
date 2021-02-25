package de.interactive_instruments.xtraplatform

import groovy.io.FileType
import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import org.gradle.api.DefaultTask
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.*

import java.util.stream.Collectors

@CacheableTask
class DocsTask extends DefaultTask {
    private FileCollection sourceFiles = project.files();
    private File outputDir = new File(project.buildDir, "docs/platform")

    @SkipWhenEmpty
    @InputFiles
    @PathSensitive(PathSensitivity.NONE)
    FileCollection getSourceFiles() {
        return this.sourceFiles;
    }

    void sources(FileCollection sourceFiles) {
        this.sourceFiles = this.sourceFiles + sourceFiles;
    }

    @OutputDirectory
    File getOutputDir() {
        return outputDir
    }

    @TaskAction
    void generateDocs() {
        def bundles = sourceFiles.files.collectEntries {
            def bundleInfo = new JsonSlurper().parseText(new File(it, 'bundle-info.json').text)
            def classes = [:]
            it.eachFile(FileType.FILES) { cls ->
                if (cls.name != 'bundle-info.json')
                    classes[cls.name] = new JsonSlurper().parseText(cls.text)
            }
            [(bundleInfo.name): [info: bundleInfo, classes: classes]]
        }

        def appInfo = [name: project.name, version: project.version, bundles: [:]]

        bundles.each { k,v ->
            println k
            println '  - version: ' + v.info.version
            println '  - provides: ' + v.info.provided2Packages
            println '  - consumes: ' + v.info.imported3Packages.findAll {it.startsWith('de.ii.ldproxy')}
            println '  - deps: ' + bundles.findAll {
                it.key != k && !v.info.imported3Packages.disjoint(it.value.info.provided2Packages)
            }.collect { it.key }
            println '  - components: ' + v.classes.findAll {it.value.annotations.stream().anyMatch({ann -> ann.qualifiedTypeName == 'org.apache.felix.ipojo.annotations.Component'})}.collect { it.value.qualifiedName }
            println '  - required: ' + v.classes.findAll {it.value.annotations.stream().anyMatch({ann -> ann.qualifiedTypeName == 'org.apache.felix.ipojo.annotations.Component'})}.collect {
                it.value.fields.stream()
                        .filter({field -> field.annotations.stream().anyMatch({ann -> ann.qualifiedTypeName == 'org.apache.felix.ipojo.annotations.Requires'})})
                        .map({field -> field.type.qualifiedName})
                        .collect(Collectors.toList())
            }

            appInfo.bundles[k] = [
                    name: v.info.name,
                    version: v.info.version,
                    provides: v.info.provided2Packages,
                    consumes: v.info.imported3Packages.findAll {it.startsWith('de.ii.ldproxy')},
                    deps: bundles.findAll {
                        it.key != k && !v.info.imported3Packages.disjoint(it.value.info.provided2Packages)
                    }.collect { it.key },
                    components: v.classes.findAll {it.value.annotations.stream().anyMatch({ann -> ann.qualifiedTypeName == 'org.apache.felix.ipojo.annotations.Component'})}.collect { it.value.qualifiedName },
                    required: v.classes.findAll {it.value.annotations.stream().anyMatch({ann -> ann.qualifiedTypeName == 'org.apache.felix.ipojo.annotations.Component'})}.collectEntries {
                        [(it.value.qualifiedName): it.value.fields.stream()
                                .filter({field -> field.annotations.stream().anyMatch({ann -> ann.qualifiedTypeName == 'org.apache.felix.ipojo.annotations.Requires'})})
                                .map({field -> field.type.qualifiedName})
                                .collect(Collectors.toList())
                        ]
                    }
            ]
        }

        outputDir.mkdirs()
        project.file("${outputDir.absolutePath}/app.json").text = new JsonBuilder(appInfo).toPrettyString()

        project.file("${outputDir.absolutePath}/app.md").text = "# ${project.name}\n\n" +
                bundles.values().stream()
                        .map({bundle ->
                    "## ${bundle.info.name}\n\n" +

                            (!appInfo.bundles[bundle.info.name].deps.isEmpty()
                                    ? "```mermaid\ngraph BT\n" +
                                    appInfo.bundles[bundle.info.name].deps.stream()
                                            .map({dep -> "   ${bundle.info.name}(${bundle.info.name}) -->|requires| ${dep}(${dep})"})
                                            .collect(Collectors.joining('\n'))
                                    +
                                    "\n"
                                    +
                                    appInfo.bundles[bundle.info.name].deps.stream()
                                            .map({dep -> "   click ${dep} \"/modules/${dep.replaceAll('\\.','-')}/\""})
                                            .collect(Collectors.joining('\n'))
                                    +
                                    "\n   classDef default fill:#f9f,stroke:#333,stroke-width:4px\n```\n\n"
                                    : ""
                            ) +

                            appInfo.bundles[bundle.info.name].components.stream()
                                    .map({component ->
                                "### ${component.tokenize('.')[-1]}\n\n" +
                                        (!appInfo.bundles[bundle.info.name].required[component].isEmpty()
                                                ? "```mermaid\ngraph RL\n" +
                                                appInfo.bundles[bundle.info.name].required[component].stream()
                                                        .map({r -> "   ${component}(${component.tokenize('.')[-1]}) -->|requires| ${r}(${r.tokenize('.')[-1]})"})
                                                        .collect(Collectors.joining('\n'))
                                                +
                                                "\n```\n\n"
                                                : ""
                                        )
                            })
                                    .collect(Collectors.joining(''))


                })
                        .collect(Collectors.joining('\n\n\n'))

// TODO: generate into build, add copy task in build.gradle
        project.file("${outputDir}/modules").mkdirs()
        bundles.values()
                .forEach({bundle ->
            project.file("${outputDir}/modules/${bundle.info.name}.md").text = "## ${bundle.info.name}\n\n" +
                    'This module is responsible for ...\n\n### Bundle dependencies\n\n' +
                    (!appInfo.bundles[bundle.info.name].deps.isEmpty()
                            ? "```mermaid\ngraph BT\n" +
                            appInfo.bundles[bundle.info.name].deps.stream()
                                    .map({dep -> "   ${bundle.info.name}(${bundle.info.name}) -->|requires| ${appInfo.bundles[dep].name}(${appInfo.bundles[dep].name})"})
                                    .collect(Collectors.joining('\n'))
                            +
                            "\n"
                            +
                            appInfo.bundles[bundle.info.name].deps.stream()
                                    .map({dep -> "   click ${appInfo.bundles[dep].name} goto"})
                                    .collect(Collectors.joining('\n'))
                            + //.map({dep -> '${appInfo.bundles[dep].name}'})
                            "\n   class ${appInfo.bundles[bundle.info.name].deps.stream().map({dep -> "${appInfo.bundles[dep].name}"}).collect(Collectors.joining(','))} light\n```\n\n"
                            : ""
                    ) +
                    '### Components\n\n' +

                    "```mermaid\ngraph RL\n" +
                    (!appInfo.bundles[bundle.info.name].components.isEmpty()
                            ?
                            "  subgraph ${bundle.info.name}\n" +
                                    appInfo.bundles[bundle.info.name].components.stream()
                                            .map({component ->
                                        "   ${component}(${component.tokenize('.')[-1]})\n"
                                    })
                                            .collect(Collectors.joining())
                                    + "  end\n"
                                    +
                                    appInfo.bundles[bundle.info.name].components.stream()
                                            .map({component ->
                                        (!appInfo.bundles[bundle.info.name].required[component].isEmpty()
                                                ? appInfo.bundles[bundle.info.name].required[component].stream()
                                                .map({r -> "   ${component}(${component.tokenize('.')[-1]}) -->|requires| ${r}(${r.tokenize('.')[-1]})\n"})
                                                .collect(Collectors.joining())
                                                : ""
                                        )
                                    })
                                            .collect(Collectors.joining())
                            : "") + "\n```\n\n" +
                    appInfo.bundles[bundle.info.name].components.stream()
                            .map({component ->
                        "#### ${component.tokenize('.')[-1]}\n\nThis component is responsible for ...\n\n" +
                                (!appInfo.bundles[bundle.info.name].required[component].isEmpty()
                                        ? "```mermaid\ngraph RL\n" +
                                        appInfo.bundles[bundle.info.name].required[component].stream()
                                                .map({r -> "   ${component}(${component.tokenize('.')[-1]}) -->|requires| ${r}(${r.tokenize('.')[-1]})"})
                                                .collect(Collectors.joining('\n'))
                                        +
                                        "\n```\n\n"
                                        : ""
                                ) + "<br/><br/><br/><br/><br/>\n\n"
                    })
                            .collect(Collectors.joining(''))


        })

    }
}
