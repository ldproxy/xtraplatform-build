package de.interactive_instruments.xtraplatform.docs

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import org.gradle.api.DefaultTask
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.*

import java.lang.reflect.Type

@CacheableTask
class JsonSchemaTask extends DefaultTask {
    //TODO: pass parent as param?
    private FileCollection sourceFiles = project.files(project.parent.configurations.layerDocs.resolvedConfiguration.firstLevelModuleDependencies.collectMany { it.moduleArtifacts }.collect { it.file });
    private File outputDir = new File(project.projectDir, "src/main/resources/json-schema")
    private String docsName
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
    void generateFiles() {
        println new Date()

        Docs docs = loadDocs()
        JsonSchemaGenerator generator = new JsonSchemaGenerator(docs, gson)

        Map<String, List<DocRef>> refs = docs.findTypeByInterface("de.ii.xtraplatform.entities.domain.PersistentEntity")
                .findAll { it.type.getAnnotation("de.ii.xtraplatform.entities.domain.Entity").map { it.getAttribute("type") }.isPresent() }
                .groupBy { it.type.getAnnotation("de.ii.xtraplatform.entities.domain.Entity").get().getAttribute("type").get() }

        refs.each { ref ->
            println ref.key

            Map<String, List<Map<String, String>>> discriminators = new LinkedHashMap<>()

            List<DocRef> dataClasses = ref.value.collect {
                def entity = it.type.getAnnotation("de.ii.xtraplatform.entities.domain.Entity").get()
                String dataClass = entity.getAttribute("data").get()
                DocRef impl = dataClass.contains(".Immutable")
                        ? docs.findTypeRef(dataClass)
                        : docs.findTypeByInterface(dataClass).find { it.type.qualifiedName.contains(".Immutable") }

                if (Objects.isNull(impl)) {
                    throw new IllegalStateException("No immutable implementation found for " + dataClass)
                }

                List<Map<String, Object>> subTypes = entity.getAttributeAsJson("subTypes")
                        .map(t -> (List<Map<String, Object>>) gson.fromJson(t, JsonSchemaGenerator.LIST_MAP))
                        .orElse(List.of());

                Map<String, String> st = new LinkedHashMap<>()
                Map<String, String> st2 = new LinkedHashMap<>()
                subTypes.each {
                    if (it.containsKey("keyAlias") && it.get("keyAlias") instanceof List && !((List<String>) it.get("keyAlias")).isEmpty()) {
                        List<String> aliases = (List<String>) it.get("keyAlias")

                        st.put((String) it['key'] + "|" + aliases.get(0), (String) it['value'])
                        st2.put(aliases.get(0) + "|" + (String) it['key'], (String) it['value'])
                    } else {
                        st.put((String) it['key'], (String) it['value'])
                        st2.put((String) it['key'], (String) it['value'])
                    }
                }

                List<Map<String, String>> sts = st.isEmpty() ? [] : [st]
                if (!Objects.equals(st, st2)) {
                    sts.add(st2)
                }

                if (!sts.isEmpty()) {
                    discriminators.put(impl.type.qualifiedName, sts)
                }
                println "- " + impl.type.qualifiedName + ": " + sts

                return impl
            }

            def schema = generator.generate(dataClasses, discriminators)

            writeSchema(schema, ref.key)
        }

        println new Date()
    }

    protected Docs loadDocs() {
        List<LayerDocs> layers = []

        getSourceFiles().files.each {
            def docs = project.zipTree(it).matching { include 'layer-docs.json' }.singleFile
            layers.add(gson.fromJson(docs.text, LayerDocs.class))
        }

        return new Docs(layers)
    }

    protected void writeSchema(Map<String, Object> schema, String name) {
        String json = gson.toJson(schema);

        getOutputDir().mkdirs()

        File file = new File(getOutputDir(), name + ".json")

        file.text = json
    }
}
