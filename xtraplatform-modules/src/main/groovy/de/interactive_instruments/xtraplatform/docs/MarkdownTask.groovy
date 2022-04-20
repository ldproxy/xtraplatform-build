package de.interactive_instruments.xtraplatform.docs

import com.google.common.base.CaseFormat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.gradle.api.DefaultTask
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.*

import java.lang.reflect.Type
import java.util.function.Function

import static java.util.Optional.*
import static java.util.Optional.*

@CacheableTask
class MarkdownTask extends DefaultTask {
    //TODO: other layers
    private FileCollection sourceFiles = project.files(project.configurations.layers.resolvedConfiguration.firstLevelModuleDependencies.collectMany { it.moduleArtifacts }.collect { it.file }) + project.files(project.tasks.jar);
    private File outputDir = new File(project.buildDir, "docs")
    private String docsName

    @SkipWhenEmpty
    @InputFiles
    @PathSensitive(PathSensitivity.NONE)
    FileCollection getSourceFiles() {
        return this.sourceFiles;
    }

    @Input
    String getDocsName() {
        return docsName
    }

    void sources(FileCollection sourceFiles) {
        this.sourceFiles = this.sourceFiles + sourceFiles;
    }

    void docsName(String docsName) {
        this.docsName = docsName;
    }

    @OutputDirectory
    File getOutputDir() {
        return new File(outputDir, docsName)
    }

    @TaskAction
    void generateDocs() {
        Gson gson = new Gson();
        Type listMapType = new TypeToken<List<Map<String, String>>>() {}.getType();
        List<LayerDocs> layers = loadLayers()
        Map<String, AnnotationDocs> templates = getDocTemplates(layers)
        Function<String, TypeDocs> typeFinder = qualifiedName -> layers.stream()
                .map(layer -> layer.getType(qualifiedName))
                .filter(java.util.Optional::isPresent)
                .map(java.util.Optional::get)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Referenced type could not be resolved: " + qualifiedName))

        layers.each {
            it.modules.values().each {
                it.api.values().each {
                    def docFile = it.getAnnotation("de.ii.xtraplatform.docs.DocFile")
                    if (docFile.isPresent()) {
                        def path = docFile.get()
                                .getAttribute("path")
                                .orElse(it.qualifiedName.replaceAll("\\.", "/") + ".md")
                        writeDocFile(it, path, typeFinder, [])
                    }
                    if (!templates.isEmpty()) {
                        templates.each { template ->
                            if (it.hasInterface(template.key)) {
                                def path = template.value
                                        .getAttribute("path")
                                        .orElse(it.qualifiedName.substring(0, it.qualifiedName.lastIndexOf('.')).replaceAll("\\.", "/"))
                                def name = it.getName()
                                template.value
                                        .getAttribute("stripPrefix")
                                        .ifPresent {if (name.startsWith(it)) name = name.substring(it.length())}
                                template.value
                                        .getAttribute("stripSuffix")
                                        .ifPresent {if (name.endsWith(it)) name = name.substring(0, name.length() - it.length())}
                                def caseFormat = template.value
                                        .getAttribute("caseFormat")
                                        .map(cf -> cf as CaseFormat)
                                        .orElse(CaseFormat.LOWER_UNDERSCORE)
                                List<Map<String, String>> mdTemplates = template.value
                                        .getAttribute("template")
                                        .map(t -> gson.fromJson(t, listMapType))
                                        .orElse([])
                                name = CaseFormat.UPPER_CAMEL.to(caseFormat, name) + ".md"
                                writeDocFile(it, path + "/" + name, typeFinder, mdTemplates)
                            }
                        }
                    }
                }
            }
        }
    }

    protected List<LayerDocs> loadLayers() {
        Gson gson = new Gson()
        List<LayerDocs> layers = []

        getSourceFiles().files.each {
            def docs = project.zipTree(it).matching { include 'layer-docs.json' }.singleFile
            layers.add(gson.fromJson(docs.text, LayerDocs.class))
        }

        return layers
    }

    protected Map<String, AnnotationDocs> getDocTemplates(List<LayerDocs> layers) {
        Map<String, AnnotationDocs> templates = new LinkedHashMap<>()

        layers.each {
            it.modules.values().each {
                it.api.values().each {
                    if (it.hasAnnotation("de.ii.xtraplatform.docs.DocFileTemplate")) {
                        AnnotationDocs a = it.getAnnotation("de.ii.xtraplatform.docs.DocFileTemplate").get()
                        if (a.hasAttribute("template")) {
                            a.attributes.put("template", "[" + a.attributes.get("template")
                                    .replaceAll("@de.ii.xtraplatform.docs.DocTemplate\\(", "{")
                                    .replaceAll("\\)", "}")
                                    .replaceAll("=", ":") + "]")
                        }
                        templates.put(it.qualifiedName, a)
                    }
                }
            }
        }

        return templates
    }

    protected void writeDocFile(TypeDocs typeDocs, String path, Function<String, TypeDocs> typeFinder, List<Map<String, String>> mdTemplates) {
        def languages = typeDocs.getDocLanguages()
        println "DOC " + typeDocs.qualifiedName + " - " + path + " - " + languages + " - " + mdTemplates.size()
        languages.each { lang ->
            def md = new File(new File(getOutputDir(), lang), path)
            md.parentFile.mkdirs()
            java.util.Optional<String> template = mdTemplates.stream()
                    .filter(t -> Objects.equals(t.get("language"), lang))
                    .map(t -> t.get("template"))
                    .findFirst()
            md.text = typeDocs.getDocText(lang, typeFinder, template)
        }
    }
}
