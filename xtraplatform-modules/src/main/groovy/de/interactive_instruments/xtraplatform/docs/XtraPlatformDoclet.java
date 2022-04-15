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
import com.sun.source.doctree.CommentTree;
import com.sun.source.doctree.DocCommentTree;
import com.sun.source.doctree.DocTree;
import com.sun.source.doctree.ReferenceTree;
import com.sun.source.doctree.SeeTree;
import com.sun.source.doctree.SummaryTree;
import com.sun.source.doctree.TextTree;
import com.sun.source.doctree.UnknownBlockTagTree;
import com.sun.source.util.DocTreeScanner;
import com.sun.source.util.DocTrees;
import com.sun.source.util.SimpleDocTreeVisitor;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.ElementScanner9;
import jdk.javadoc.doclet.Doclet;
import jdk.javadoc.doclet.DocletEnvironment;
import jdk.javadoc.doclet.Reporter;

public class XtraPlatformDoclet implements Doclet {

    private Path docletPath;
    private ModuleDocs moduleInfo;
    private Reporter reporter;
    private DocTrees treeUtils;

    private final Set<Option> options = Set.of(
        new Option("-d", true, "docletPath", "<string>") {
            @Override
            public boolean process(String option, List<String> arguments) {
                XtraPlatformDoclet.this.docletPath = Path.of(arguments.get(0));
                System.out.println("docletPath: " + docletPath);
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
        this.reporter = reporter;
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
        treeUtils = docletEnvironment.getDocTrees();
        ShowElements se = new ShowElements(System.out);
        List<ElementDocs> elementDocs = se.show(docletEnvironment.getIncludedElements());
        elementDocs.forEach(e -> System.out.println(e.toString()));

        return true;
    }


// TODO: does not work with Java 11 in Intellij, seems to work in console
    public static void main(String... args){}

    public static int optionLength(String option) {
        if(option.equals("-d")) {
            return 2;
        }
        return 2;
    }

    public static boolean start(RootDoc root) throws Exception {
        ClassDoc[] classes = root.classes();root.specifiedPackages();
        String targetDir = readOptions(root.options());
        float count = 0;
        int lastpct = 0;
        String lastClass = "";

        List<ClassDoc> classList = new ArrayList<>();

        for (ClassDoc c : classes) {
            Map<String, Object> cls = parseClass(c, targetDir);
            classList.add(c);
            count++;
            int curpct = Math.round((count/classes.length)*100f);
            String curClass = c.qualifiedName();
            if(lastpct!=curpct||count==classes.length){
                System.out.println("["+(int)count+"/"+classes.length+"] "+(curpct)+"% ("+lastClass+" - "+curClass+")");
                lastpct = curpct;
                lastClass = curClass;
            }
        }

        new BundleDocsGenerator(classList, readManifest(root.options())).generate(targetDir);

        return true;
    }

    private static String readOptions(String[][] options) {
        String tagName = null;
        for (int i = 0; i < options.length; i++) {
            String[] opt = options[i];
            if (opt[0].equals("-d")) {
                tagName = opt[1];
            }
        }
        return tagName;
    }

    private static String readManifest(String[][] options) {
        String tagName = null;
        for (int i = 0; i < options.length; i++) {
            String[] opt = options[i];
            if (opt[0].equals("-manifest")) {
                tagName = opt[1];
            }
        }
        return tagName;
    }

    private static Map<String, Object> parseClass(ClassDoc c, String targetDir) throws Exception{
        HashMap<String,Object> props = new HashMap<>();
        props.put("typeName",c.typeName());
        props.put("name",c.name());
        props.put("modifiers",c.modifiers());
        props.put("qualifiedName",c.qualifiedName());
        Object superclass = null;
        if(c.superclass()!=null){
            superclass = new HashMap<String,Object>();
            ((HashMap<String,Object>) superclass).put("name",c.superclass().name());
            ((HashMap<String,Object>) superclass).put("qualifiedName",c.superclass().qualifiedTypeName());
        }
        props.put("superclass",superclass);
        ArrayList<Object> ans = new ArrayList<>();
        for(AnnotationDesc at : c.annotations()){
            ans.add(parseAnnotation(at));
        }
        props.put("annotations",ans);
        ArrayList<Object> cns = new ArrayList<>();
        for(ConstructorDoc cn : c.constructors()){
            cns.add(parseConstructor(cn));
        }
        props.put("constructors",cns);
        ArrayList<Object> mthds = new ArrayList<>();
        for(MethodDoc mtd : c.methods()){
            mthds.add(parseMethod(mtd));
        }
        props.put("methods",mthds);
        ArrayList<Object> flds = new ArrayList<>();
        for(FieldDoc fld : c.fields()){
            flds.add(parseField(fld));
        }
        props.put("fields",flds);
        ArrayList<Object> ifaces = new ArrayList<>();
        for(Type ifct : c.interfaceTypes()){
            ifaces.add(parseType(ifct));
        }
        props.put("interfaces",ifaces);
        writeJson(new File(new File(targetDir), c.qualifiedName()+".json"),props);
        for (ClassDoc ic:c.innerClasses()) {
            // TODO
            parseClass(ic, targetDir);
        }

        return props;
    }

    private static Object parseField(FieldDoc fld) {
        HashMap<String,Object> props = new HashMap<>();
        props.put("name",fld.name());
        props.put("modifiers",fld.modifiers());
        props.put("type",parseType(fld.type()));
        props.put("qualifiedName",fld.qualifiedName());
        props.put("docString",fld.commentText());
        ArrayList<Object> ans = new ArrayList<>();
        for(AnnotationDesc at : fld.annotations()){
            ans.add(parseAnnotation(at));
        }
        props.put("annotations",ans);
        return props;
    }

    private static Object parseMethod(MethodDoc mt) {
        HashMap<String,Object> props = new HashMap<>();
        props.put("name",mt.name());
        props.put("modifiers",mt.modifiers());
        props.put("docString",mt.commentText());
        props.put("qualifiedName",mt.qualifiedName());
        ArrayList<Object> xcpts = new ArrayList<>();
        for(Type xcpt : mt.thrownExceptionTypes()){
            xcpts.add(parseType(xcpt));
        }
        props.put("exceptions",xcpts);
        ArrayList<Object> ans = new ArrayList<>();
        for(AnnotationDesc at : mt.annotations()){
            ans.add(parseAnnotation(at));
        }
        props.put("annotations",ans);
        ArrayList<Object> prms = new ArrayList<>();
        for(Parameter pm : mt.parameters()){
            prms.add(parseParameter(pm));
        }
        props.put("parameters",prms);
        props.put("returnType",parseType(mt.returnType()));
        return props;
    }

    private static Object parseType(Type t){
        HashMap<String,Object> props = new HashMap<>();
        props.put("name",t.typeName());
        props.put("qualifiedName",t.qualifiedTypeName());
        props.put("type",t.toString());
        return props;
    }

    private static Object parseConstructor(ConstructorDoc cn) {
        HashMap<String,Object> props = new HashMap<>();
        props.put("name",cn.name());
        props.put("modifiers",cn.modifiers());
        props.put("docString",cn.commentText());
        props.put("qualifiedName",cn.qualifiedName());
        ArrayList<Object> xcpts = new ArrayList<>();
        for(Type xcpt : cn.thrownExceptionTypes()){
            xcpts.add(parseType(xcpt));
        }
        props.put("exceptions",xcpts);
        ArrayList<Object> ans = new ArrayList<>();
        for(AnnotationDesc at : cn.annotations()){
            ans.add(parseAnnotation(at));
        }
        props.put("annotations",ans);
        ArrayList<Object> prms = new ArrayList<>();
        for(Parameter pm : cn.parameters()){
            prms.add(parseParameter(pm));
        }
        props.put("parameters",prms);
        return props;
    }

    private static Object parseParameter(Parameter pm) {
        HashMap<String,Object> props = new HashMap<>();
        props.put("name",pm.name());
        props.put("type",parseType(pm.type()));
        ArrayList<Object> ans = new ArrayList<>();
        for(AnnotationDesc at : pm.annotations()){
            ans.add(parseAnnotation(at));
        }
        props.put("annotations",ans);
        return props;
    }

    private static Object parseAnnotation(AnnotationDesc at) {
        HashMap<String,Object> props = new HashMap<>();
        props.put("typeName",at.annotationType().name());
        props.put("qualifiedTypeName",at.annotationType().qualifiedTypeName());
        ArrayList<Object> els = new ArrayList<>();
        for (AnnotationDesc.ElementValuePair evp:at.elementValues()) {
            HashMap<String,Object> elprops = new HashMap<>();
            elprops.put("name",evp.element().name());
            elprops.put("qualifiedName",evp.element().qualifiedName());
            elprops.put("value",evp.value().toString());
            els.add(elprops);
        }
        props.put("elements",els);
        return props;
    }

    private static void writeJson(File f, Object o) throws IOException {
        if(f.exists())f.delete();
        if(!f.createNewFile()) throw new IOException("Cant create file "+f.getName());
        if(!f.canWrite()) throw new IOException("Hey bud let me write to "+f.getName());

        FileWriter fw = new FileWriter(f);
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        String json = gson.toJson(o);
        fw.write(json);
        fw.flush();
        fw.close();
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

    /**
     * A scanner to display the structure of a series of elements
     * and their documentation comments.
     */
    class ShowElements extends ElementScanner9<List<ElementDocs>, Integer> {
        final PrintStream out;

        ShowElements(PrintStream out) {
            super(new ArrayList<>());
            this.out = out;
        }

        List<ElementDocs> show(Set<? extends Element> elements) {
            return scan(elements, 0);
        }

        @Override
        public List<ElementDocs> scan(Element e, Integer depth) {
            DocCommentTree dcTree = treeUtils.getDocCommentTree(e);
            String indent = "  ".repeat(depth);
            if (depth == 0 && e.getKind() == ElementKind.CLASS) {
                //out.println(indent + "| " + e.getKind() + " " + e);
            }
            if (depth == 0 && dcTree != null) {
                //new ShowDocTrees(out).scan(dcTree, depth + 1);

                out.println(indent + "| " + e.getKind() + " " + e);
                List<Map<String, List<String>>> tags = new ArrayList<>();
                new TagScanner(tags).visit(dcTree, null);
                tags.forEach(ts -> ts.forEach((t,l) -> {
                    out.println(indent + "  @" + t);
                    l.forEach(c -> out.println(indent + "    '" + c + "'"));
                }));
            }
            return super.scan(e, depth + 1);
        }

        @Override
        public List<ElementDocs> visitType(TypeElement e, Integer integer) {
            //out.println("  -> TYPE " + integer + " " + e.getQualifiedName());
            if (integer == 1) {
                List<ElementDocs> enclosed = super.visitType(e, integer)
                    .stream()
                    .filter(en -> !(en instanceof ClassDocs))
                    .collect(Collectors.toList());
                ClassDocs c = new ClassDocs();
                c.qualifiedName = e.getQualifiedName().toString();
                c.name = e.getSimpleName().toString();
                c.constructors = enclosed;
                DEFAULT_VALUE.add(c);
            }

            return DEFAULT_VALUE;
        }

        @Override
        public List<ElementDocs> visitExecutable(ExecutableElement e, Integer integer) {
            if (e.getKind() == ElementKind.CONSTRUCTOR) {
                ElementDocs c = new ElementDocs();
                c.name = e.getSimpleName().toString();
                return List.of(c);
            }
            //return super.visitExecutable(e, integer);
            return List.of();
        }
    }

    /**
     * A scanner to display the structure of a documentation comment.
     */
    class ShowDocTrees extends DocTreeScanner<Void, Integer> {
        final PrintStream out;

        ShowDocTrees(PrintStream out) {
            this.out = out;
        }

        @Override
        public Void scan(DocTree t, Integer depth) {
            String indent = "  ".repeat(depth);
            if (depth == 1) {
                out.println(indent + "# "
                    + t.getKind() + " "
                    + t.toString().replace("\n", "\n" + indent + "#    "));
            }
            return super.scan(t, depth + 1);
        }
    }

    /**
     * A visitor to gather the block tags found in a comment.
     */
    class TagScanner extends SimpleDocTreeVisitor<Void, Void> {
        private final List<Map<String, List<String>>> tags;
        private Map<String, List<String>> currentTags;

        TagScanner(List<Map<String, List<String>>> tags) {
            this.tags = tags;
            this.currentTags = new LinkedHashMap<>();
            tags.add(currentTags);
        }

        @Override
        public Void visitDocComment(DocCommentTree tree, Void p) {
            tree.getFullBody().forEach(body -> addTag("_BODY_", body.toString(), true));

            return visit(tree.getBlockTags(), null);
        }

        @Override
        public Void visitUnknownBlockTag(UnknownBlockTagTree tree,
            Void p) {
            String name = tree.getTagName();
            String content = tree.getContent().toString();
            addTag(name, content, false);
            return null;
        }

        @Override
        public Void visitSee(SeeTree node, Void unused) {
            String name = node.getTagName();
            //TODO: qualify using imports
            String content = node.getReference()
                .stream()
                .map(dt -> ((ReferenceTree)dt).getSignature())
                .collect(Collectors.joining(","));
            addTag(name, content, false);
            return null;
        }

        private void addTag(String name, String content, boolean append) {
            if (!append && currentTags.containsKey(name)) {
                this.currentTags = new LinkedHashMap<>();
                tags.add(currentTags);
            }
            currentTags.computeIfAbsent(name,
                n -> new ArrayList<>()).add(content);
        }
    }
}
