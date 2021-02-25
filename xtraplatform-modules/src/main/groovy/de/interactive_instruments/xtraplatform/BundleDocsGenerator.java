package de.interactive_instruments.xtraplatform;

/**
 * @author zahnen
 */
public class BundleDocsGenerator {
/* TODO: does not work with Java 11 in Intellij, seems to work in console
    private final List<ClassDoc> classes;
    private final Map<String, String> manifest;

    public BundleDocsGenerator(List<ClassDoc> classes, String manifest) {
        this.classes = classes;
        this.manifest = parseManifest(manifest);
    }

    private Map<String, String> parseManifest(final String manifest) {
        return Splitter.on("&&").trimResults().withKeyValueSeparator("==").split(manifest);
    }

    public void generate(final String targetDir) throws IOException {
        final File bundleInfoFile = new File(new File(targetDir), "bundle-info.json");

        if (bundleInfoFile.exists()) bundleInfoFile.delete();
        if (!bundleInfoFile.createNewFile()) throw new IOException("Cant create file " + bundleInfoFile.getName());
        if (!bundleInfoFile.canWrite()) throw new IOException("Hey bud let me write to " + bundleInfoFile.getName());

        final Map<String, Object> bundleInfo = generate();

        FileWriter fileWriter = new FileWriter(bundleInfoFile);
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        fileWriter.write(gson.toJson(bundleInfo));
        fileWriter.flush();
        fileWriter.close();
    }

    private Map<String, Object> generate() {
        final List<String> packages = classes.stream()
                .map(ClassDoc::qualifiedName)
                .map(this::getPkg)
                .sorted()
                .collect(Collectors.toList());


        // does not find classes that are only used in method bodies
        final List<String> imports = classes.stream()
                .flatMap(this::getAllUsedClassesStream)
                .filter(ignorablePackages())
                .map(this::getPkg)
                .sorted()
                .collect(Collectors.toList());


        // does not resolve wildcard imports, throws exception when a static import is found
        final List<String> imports2 = classes.stream()
                .flatMap(cls -> {
                    ClassDoc[] classDocs = null;
                    try {
                        classDocs = cls.importedClasses();
                    } catch (NullPointerException e) {
                        System.out.println("WARNING: imports for class " + cls.qualifiedName() + " could not be determined");
                    }
                    return classDocs != null ? Arrays.stream(classDocs) : Stream.empty();
                })
                .map(ProgramElementDoc::qualifiedName)
                .filter(ignorablePackages())
                .map(this::getPkg)
                .sorted()
                .collect(Collectors.toList());

        final List<String> imports3 = manifest.containsKey("Import-Package") ? Splitter.on("||").trimResults().splitToList(manifest.get("Import-Package")).stream()
                .map(pkg -> pkg.replaceAll(";.*", ""))
                .collect(Collectors.toList()) : ImmutableList.of();

        final List<String> imports4 = manifest.containsKey("Export-Package") ? Splitter.on("||").trimResults().splitToList(manifest.get("Export-Package")).stream()
                                              .map(pkg -> pkg.replaceAll(".*;uses:=\"(.*)\"", "$1"))
                                              .flatMap(uses -> Splitter.on(",").trimResults().splitToList(uses).stream())
                                                .map(pkg -> pkg.replaceAll("\";.*", ""))
                                              .collect(Collectors.toList()) : ImmutableList.of();

        final List<String> provided2 = manifest.containsKey("Export-Package") ? Splitter.on("||").trimResults().splitToList(manifest.get("Export-Package")).stream()
                                                                                       .map(pkg -> pkg.replaceAll(";.*", ""))
                                                                                       .collect(Collectors.toList()) : ImmutableList.of();

        return new ImmutableMap.Builder<String, Object>()
                .put("name", manifest.get("Bundle-Name"))
                //.put("label", manifest.get("Implementation-Title"))
                .put("version", manifest.get("Bundle-Version"))
                .put("providedPackages", ImmutableSet.copyOf(packages))
                .put("provided2Packages", ImmutableSet.copyOf(provided2))
                .put("importedPackages", ImmutableSet.copyOf(imports))
                .put("imported2Packages", ImmutableSet.copyOf(imports2))
                .put("imported3Packages", ImmutableSet.copyOf(imports3))
                .put("imported4Packages", ImmutableSet.copyOf(imports4))
                .put("manifest", manifest)
                .build();
    }

    private Stream<String> getAllUsedClassesStream(final ClassDoc classDoc) {
        return Stream.concat(
                Stream.concat(
                        Stream.concat(
                                Stream.concat(getParameterTypeStream(classDoc), getExceptionTypeStream(classDoc)),
                                Stream.concat(getReturnTypeStream(classDoc), getFieldsTypeStream(classDoc))
                        ),
                        Stream.concat(
                                Stream.concat(getParameterAnnotationTypeStream(classDoc), getElementAnnotationTypeStream(classDoc)),
                                Stream.concat(getInterfaceTypeStream(classDoc), getSuperTypeStream(classDoc))
                        )),
                getClassAnnotationTypeStream(classDoc)
        );
    }

    private Stream<String> getParameterTypeStream(final ClassDoc classDoc) {
        return getConstructorAndMethodStream(classDoc)
                .flatMap(executableMember -> Arrays.stream(executableMember.parameters()))
                .map(parameter -> parameter.type().qualifiedTypeName());
    }

    private Stream<String> getExceptionTypeStream(final ClassDoc classDoc) {
        return getConstructorAndMethodStream(classDoc)
                .flatMap(executableMember -> Arrays.stream(executableMember.thrownExceptionTypes()))
                .map(Type::qualifiedTypeName);
    }

    private Stream<String> getReturnTypeStream(final ClassDoc classDoc) {
        return Arrays.stream(classDoc.methods())
                .map(method -> method.returnType().qualifiedTypeName());
    }

    private Stream<String> getElementAnnotationTypeStream(final ClassDoc classDoc) {
        return getConstructorAndMethodAndFieldStream(classDoc)
                .flatMap(element -> Arrays.stream(element.annotations()))
                .map(annotation -> annotation.annotationType().qualifiedTypeName());
    }

    private Stream<String> getParameterAnnotationTypeStream(final ClassDoc classDoc) {
        return getConstructorAndMethodStream(classDoc)
                .flatMap(executableMember -> Arrays.stream(executableMember.parameters()))
                .flatMap(parameter -> Arrays.stream(parameter.annotations()))
                .map(annotation -> annotation.annotationType().qualifiedTypeName());
    }

    private Stream<String> getFieldsTypeStream(final ClassDoc classDoc) {
        return Arrays.stream(classDoc.fields())
                .map(field -> field.type().qualifiedTypeName());
    }

    private Stream<String> getInterfaceTypeStream(final ClassDoc classDoc) {
        return Arrays.stream(classDoc.interfaceTypes())
                .map(Type::qualifiedTypeName);
    }

    private Stream<String> getSuperTypeStream(final ClassDoc classDoc) {
        return classDoc.superclassType() != null ? Stream.of(classDoc.superclassType().qualifiedTypeName()) : Stream.empty();
    }

    private Stream<String> getClassAnnotationTypeStream(final ClassDoc classDoc) {
        return Arrays.stream(classDoc.annotations())
                .map(annotation -> annotation.annotationType().qualifiedTypeName());
    }

    private Stream<ExecutableMemberDoc> getConstructorAndMethodStream(final ClassDoc classDoc) {
        return Stream.concat(Arrays.stream(classDoc.constructors()), Arrays.stream(classDoc.methods()));
    }

    private Stream<ProgramElementDoc> getConstructorAndMethodAndFieldStream(final ClassDoc classDoc) {
        return Stream.concat(Stream.concat(Arrays.stream(classDoc.constructors()), Arrays.stream(classDoc.methods())), Arrays.stream(classDoc.fields()));
    }

    private String getPkg(final String className) {
        return Splitter.on('.').splitToList(className).stream()
                       .filter(segment -> segment.matches("[a-z].*"))
                .collect(Collectors.joining("."));

        //return className.contains(".") ? className.substring(0, className.lastIndexOf('.')) : className;
    }

    private Predicate<String> ignorablePackages() {
        return pkg -> !pkg.startsWith("java.") && !pkg.equals("void");
    }
    */
}
