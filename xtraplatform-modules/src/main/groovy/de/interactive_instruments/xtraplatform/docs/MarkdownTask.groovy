package de.interactive_instruments.xtraplatform.docs

import com.google.gson.Gson
import org.gradle.api.DefaultTask
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.*

@CacheableTask
class MarkdownTask extends DefaultTask {
    private FileCollection sourceFiles = project.files(project.configurations.layerDocs.resolvedConfiguration.firstLevelModuleDependencies.collectMany { it.moduleArtifacts }.collect { it.file }) + project.files(project.tasks.jar);
    private File outputDir = new File(project.buildDir, "tmp/markdown")
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
    void generateFiles() {
        println new Date()
        Docs docs = loadDocs()
        List<DocFilesTemplate> templates = docs.getDocFilesTemplates()

        docs.getDocFiles()
                .forEach(docFile -> writeDocFile(docFile.docRef, docFile.path, docFile.name, docFile.tables, docFile.vars))

        if (!templates.isEmpty()) {
            docs.streamTypes()
                    .forEach(typeRef -> {
                        templates.each { template ->
                            if (typeRef.getType().hasInterface(template.getTypeName())) {
                                java.util.Optional<DocFile> defs = docs.getDocDefs(typeRef);
                                List<DocTable> additionalTables = defs.map(df -> df.tables).orElse([]);
                                List<DocVar> additionalVars = defs.map(df -> df.vars).orElse([]);

                                writeDocFile(typeRef, template.path, template.getName(typeRef), template.tables + additionalTables, template.vars + additionalVars, template.template)
                            }
                        }
                    });
        }
        println new Date()
    }

    protected Docs loadDocs() {
        Gson gson = new Gson()
        List<LayerDocs> layers = []

        getSourceFiles().files.each {
            def docs = project.zipTree(it).matching { include 'layer-docs.json' }.singleFile
            layers.add(gson.fromJson(docs.text, LayerDocs.class))
        }

        return new Docs(layers)
    }

    protected void writeDocFile(DocRef docRef, String path, String name, List<DocTable> tables, List<DocVar> vars, List<DocI18n> templates = []) {
        def languages = docRef.getDocLanguages()
        def filePath = path + "/" + name;

        println "DOC " + docRef.getType().qualifiedName + " - " + filePath + " - " + languages + " - " + templates.size()

        languages.each { lang ->
            def md = new File(new File(getOutputDir(), lang == 'en' ? '' : lang), filePath)
            md.parentFile.mkdirs()
            java.util.Optional<String> template = templates.stream()
                    .filter(t -> Objects.equals(t.language, lang))
                    .map(t -> t.value)
                    .findFirst()
            md.text = docRef.getDocText(docRef, lang, tables, vars, template)
        }
    }
}
