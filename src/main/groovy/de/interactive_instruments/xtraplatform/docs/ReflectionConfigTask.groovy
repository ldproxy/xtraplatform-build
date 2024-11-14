package de.interactive_instruments.xtraplatform.docs

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import org.gradle.api.DefaultTask
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.*

@CacheableTask
class ReflectionConfigTask extends DefaultTask {

    static class EntryPoint implements Serializable {
        String intrface
        String annotation = null
        String classAttribute = null
        String attribute = null
        List<String> attributeValues = []
    }

    //TODO: pass parent as param?
    private FileCollection sourceFiles = project.files(project.parent.configurations.layerDocs.resolvedConfiguration.firstLevelModuleDependencies.collectMany { it.moduleArtifacts }.collect { it.file });
    private File outputDir = new File(project.projectDir, "src/main/resources/META-INF/native-image/")
    private String docsName
    private List<EntryPoint> entryPoints
    private List<String> extraTypes = []

    private static final Gson gson = new GsonBuilder()
            .setPrettyPrinting()
            .create()

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

    @Input
    List<EntryPoint> getEntryPoints() {
        return entryPoints
    }

    @Input
    List<String> getExtraTypes() {
        return extraTypes
    }

    void sources(FileCollection sourceFiles) {
        this.sourceFiles = this.sourceFiles + sourceFiles;
    }

    void docsName(String docsName) {
        this.docsName = docsName;
    }

    void entryPoints(List<EntryPoint> entryPoints) {
        this.entryPoints = entryPoints;
    }

    void extraTypes(List<String> extraTypes) {
        this.extraTypes = extraTypes;
    }

    @OutputDirectory
    File getOutputDir() {
        return outputDir
    }

    @TaskAction
    void generateFiles() {
        Docs docs = loadDocs()
        ReflectionConfigGenerator generator = new ReflectionConfigGenerator(docs, gson)

        List<DocRef> refs = entryPoints.collectMany { entryPoint ->
            docs.findTypeByInterface(entryPoint.intrface)
                    .findAll {
                        if (entryPoint.annotation == null) {
                            return true
                        }

                        def annotation = it.type.getAnnotation(entryPoint.annotation)
                        if (entryPoint.attribute != null) {
                            annotation = annotation.flatMap { it.getAttribute(entryPoint.attribute) }
                        }

                        return annotation.isPresent()
                                && (entryPoint.attribute == null
                                || entryPoint.attributeValues.isEmpty()
                                || entryPoint.attributeValues.contains(annotation.get()))
                    }.collect {
                        entryPoint.annotation != null &&entryPoint.classAttribute != null
                                ? docs.findTypeRef(it.type.getAnnotation(entryPoint.annotation).get().getAttribute(entryPoint.classAttribute).get())
                                : it
                    }
        }

        List<Map<String, Object>> schemas = generator.generate(refs, extraTypes)

        writeSchema(schemas, "reflect-config")
    }

    protected Docs loadDocs() {
        List<LayerDocs> layers = []

        getSourceFiles().files.each {
            def docs = project.zipTree(it).matching { include 'layer-docs.json' }.singleFile
            layers.add(gson.fromJson(docs.text, LayerDocs.class))
        }

        return new Docs(layers)
    }

    protected void writeSchema(List<Map<String, Object>> schema, String name) {
        String json = gson.toJson(schema);

        getOutputDir().mkdirs()

        File file = new File(getOutputDir(), name + ".json")

        file.text = json
    }
}
