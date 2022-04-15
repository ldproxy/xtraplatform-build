package de.interactive_instruments.xtraplatform.docs;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sun.javadoc.AnnotationDesc;
import com.sun.javadoc.ClassDoc;
import com.sun.javadoc.ConstructorDoc;
import com.sun.javadoc.FieldDoc;
import com.sun.javadoc.MethodDoc;
import com.sun.javadoc.Parameter;
import com.sun.javadoc.RootDoc;
import com.sun.javadoc.Type;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.lang.model.SourceVersion;
import jdk.javadoc.doclet.Doclet;
import jdk.javadoc.doclet.DocletEnvironment;
import jdk.javadoc.doclet.Reporter;

public class XtraPlatformDoclet implements Doclet {

    private Path targetPath;
    private ModuleDocs moduleInfo;

    private final Set<Option> options = Set.of(
        new Option("-d", true, "targetPath", "<string>") {
            @Override
            public boolean process(String option, List<String> arguments) {
                XtraPlatformDoclet.this.targetPath = Path.of(arguments.get(0));
                System.out.println("targetPath: " + targetPath);
                return true;
            }
        },
        new Option("-doctitle", true, "doctitle", "<string>") {
            @Override
            public boolean process(String option, List<String> arguments) {
                System.out.println("doctitle: " + arguments.get(0));
                return true;
            }
        },
        new Option("-modinfo", true, "moduleInfo", "<string>") {
            @Override
            public boolean process(String option, List<String> arguments) {
                Gson gson = new GsonBuilder().create();
                XtraPlatformDoclet.this.moduleInfo = gson.fromJson(arguments.get(0), ModuleDocs.class);
                System.out.println("moduleInfo: " + "---" + moduleInfo);
                return true;
            }
        },
        new Option("-notimestamp", false, "notimestamp", null) {
            @Override
            public boolean process(String option, List<String> arguments) {
                System.out.println("notimestamp");
                return true;
            }
        },
        new Option("-windowtitle", true, "windowtitle", "<string>") {
            @Override
            public boolean process(String option, List<String> arguments) {
                System.out.println("windowtitle: " + arguments.get(0));
                return true;
            }
        }
    );

    @Override
    public void init(Locale locale, Reporter reporter) {
    }

    @Override
    public String getName() {
        return getClass().getSimpleName();
    }

    @Override
    public Set<? extends Option> getSupportedOptions() {
        return options;
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latest();
    }

    @Override
    public boolean run(DocletEnvironment docletEnvironment) {
        TypeScanner typeScanner = new TypeScanner(docletEnvironment.getDocTrees(), docletEnvironment.getElementUtils(), docletEnvironment.getTypeUtils());
        List<ElementDocs> typeDocs = typeScanner.show(docletEnvironment.getIncludedElements());
        for (ElementDocs td : typeDocs) {
            try {
                writeJson(targetPath.resolve(td.qualifiedName + ".json").toFile(), td);
            } catch (IOException e) {
                System.out.println(e.getMessage());
                return false;
            }
        }

        //TODO
        //new BundleDocsGenerator(classList, readManifest(root.options())).generate(targetDir);

        return true;
    }

    private static void writeJson(File f, Object o) throws IOException {
        if(f.exists())f.delete();
        if(!f.createNewFile()) throw new IOException("Cannot create file "+f.getName());
        if(!f.canWrite()) throw new IOException("Cannot write to "+f.getName());

        FileWriter fw = new FileWriter(f);
        Gson gson = new GsonBuilder()
            .setPrettyPrinting()
            .create();
        String json = gson.toJson(o);
        fw.write(json);
        fw.flush();
        fw.close();
        System.out.println(json);
    }

    /**
     * A base class for declaring options.
     * Subtypes for specific options should implement
     * the {@link #process(String,List) process} method
     * to handle instances of the option found on the
     * command line.
     */
    abstract class Option implements Doclet.Option {
        private final String name;
        private final boolean hasArg;
        private final String description;
        private final String parameters;

        Option(String name, boolean hasArg,
            String description, String parameters) {
            this.name = name;
            this.hasArg = hasArg;
            this.description = description;
            this.parameters = parameters;
        }

        @Override
        public int getArgumentCount() {
            return hasArg ? 1 : 0;
        }

        @Override
        public String getDescription() {
            return description;
        }

        @Override
        public Kind getKind() {
            return Kind.STANDARD;
        }

        @Override
        public List<String> getNames() {
            return List.of(name);
        }

        @Override
        public String getParameters() {
            return hasArg ? parameters : "";
        }
    }

}
