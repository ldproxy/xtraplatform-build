package de.interactive_instruments.xtraplatform.docs

import com.google.gson.Gson
import org.gradle.api.DefaultTask
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.*

@CacheableTask
class LayerDocsTask extends DefaultTask {

    static final String LAYER_DOCS_FILE_NAME = "layer-docs.json";

    private FileCollection sourceFiles = project.files();
    private File outputDir = new File(project.buildDir, "generated/sources/annotationProcessor/resources/main")

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
        Map<String, ModuleDocs> modules = sourceFiles.files.collectEntries {
            def moduleDocsFile = new File(it, XtraPlatformDoclet.MOD_DOCS_FILE_NAME)
            if (!moduleDocsFile.exists()) {
                return [:]
            }
            def moduleDocs = new Gson().fromJson(moduleDocsFile.text, ModuleDocs.class)
            [(moduleDocs.id): moduleDocs]
        }

        LayerDocs docs = new LayerDocs(
                id: project.group.toString() + "." + project.name.toString(),
                name: project.name.toString(),
                version: project.version.toString(),
                description: Objects.requireNonNullElse(project.description, '').toString(),
                modules: modules,
        )

        try {
            outputDir.mkdirs()
            XtraPlatformDoclet.writeJson(new File(outputDir, LAYER_DOCS_FILE_NAME), docs);
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }
    }
}
