package de.interactive_instruments.xtraplatform.docs;

import java.util.*;
import java.util.AbstractMap.SimpleEntry;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class DocRef {

  static final String LANG_PREFIX = "lang";
  static final String LANG_ALL = "langAll";
  static final String BODY = "body";
  static final String OVERRIDES_TAG = "_overrides_";

  private final Docs docs;
  private final LayerDocs layer;
  private final ModuleDocs module;
  private final TypeDocs type;
  private final MethodDocs method;
  private final String alias;
  private final Map<String, String> aliasDescription;
  private final Map<String, String> additionalVars;

  DocRef(Docs docs, LayerDocs layer, ModuleDocs module, TypeDocs type) {
    this(docs, layer, module, type, null, null, Map.of(), new HashMap<>());
  }

  DocRef(DocRef orig, String alias, Map<String, String> aliasDescription) {
    this(
        orig.docs,
        orig.layer,
        orig.module,
        orig.type,
        orig.method,
        alias,
        aliasDescription,
        orig.additionalVars);
  }

  private DocRef(DocRef orig, MethodDocs method) {
    this(
        orig.docs,
        orig.layer,
        orig.module,
        orig.type,
        method,
        orig.alias,
        orig.aliasDescription,
        orig.additionalVars);
  }

  private DocRef(
      Docs docs,
      LayerDocs layer,
      ModuleDocs module,
      TypeDocs type,
      MethodDocs method,
      String alias,
      Map<String, String> aliasDescription,
      Map<String, String> additionalVars) {
    this.docs = docs;
    this.layer = layer;
    this.module = module;
    this.type = type;
    this.method = method;
    this.alias = alias;
    this.aliasDescription = aliasDescription;
    this.additionalVars = additionalVars;
  }

  TypeDocs getType() {
    return type;
  }

  Map<String, String> getVars() {
    Map<String, String> staticVars = docs.getVars(layer, module);

    if (!additionalVars.isEmpty()) {
      Map<String, String> mergedVars = new HashMap<>(staticVars);
      mergedVars.putAll(additionalVars);
      return mergedVars;
    }

    return staticVars;
  }

  boolean isType() {
    return Objects.nonNull(type);
  }

  boolean isMethod() {
    return Objects.nonNull(method);
  }

  Optional<MethodDocs> getMethod() {
    return Optional.ofNullable(method);
  }

  Stream<DocRef> getMethods() {
    if (isMethod() || Objects.isNull(getType().methods)) {
      return Stream.empty();
    }
    return getType().methods.stream().map(methodDocs -> new DocRef(this, methodDocs));
  }

  Stream<AnnotationDocs> getAnnotations() {
    if (isMethod()) {
      return Stream.concat(
          getOverrides().flatMap(ElementDocs::getAnnotations), method.getAnnotations());
    }
    return type.getAnnotations();
  }

  boolean hasAnnotation(String qualifiedName) {
    if (isMethod()) {
      boolean result = method.hasAnnotation(qualifiedName);
      if (!result) {
        return getOverrides().anyMatch(methodDocs -> methodDocs.hasAnnotation(qualifiedName));
      }
      return true;
    }
    return type.hasAnnotation(qualifiedName);
  }

  boolean hasAnnotation(String qualifiedName, Map<String, String> attributes) {
    Optional<AnnotationDocs> annotationDocs =
        isMethod()
            ? method
                .getAnnotation(qualifiedName)
                .or(
                    () ->
                        getOverrides()
                            .flatMap(methodDocs -> methodDocs.getAnnotation(qualifiedName).stream())
                            .findFirst())
            : type.getAnnotation(qualifiedName);

    return annotationDocs
        .filter(
            a -> {
              for (Entry<String, String> entry : attributes.entrySet()) {
                boolean matches =
                    a.getAttribute(entry.getKey())
                        .filter(value -> Objects.equals(value, entry.getValue()))
                        .isPresent();
                if (!matches) {
                  return false;
                }
              }
              return true;
            })
        .isPresent();
  }

  Stream<MethodDocs> getOverrides() {
    if (!isMethod()) {
      return Stream.empty();
    }
    return getDocTag(method, OVERRIDES_TAG)
        .map(docs::findType)
        .map(typeDocs -> typeDocs.findOverride(method))
        .filter(Optional::isPresent)
        .map(Optional::get);
  }

  Stream<TypeDocs> getOverridesTypes() {
    if (!isMethod()) {
      return Stream.empty();
    }
    List<ElementDocs> o = Objects.nonNull(type.interfaces) ? type.interfaces : List.of();
    return o.stream().map(ed -> ed.qualifiedName).map(docs::findType).filter(Objects::nonNull);
  }

  String getDocText(
      DocRef docRef,
      String language,
      List<DocTable> tables,
      List<DocVar> vars,
      Optional<String> template) {
    TypeDocs type = docRef.getType();
    if (Objects.isNull(type.doc)) {
      return "";
    }

    String body = docRef.getDocText(language);

    Map<String, String> docTables =
        tables.stream()
            .flatMap(
                docTable -> {
                  String key = "docTable:" + docTable.name;
                  DocTableGenerator tableGenerator = docs.getTableGenerator(docRef);

                  return tableGenerator.generate(docTable, language).stream()
                      .map(table -> new SimpleEntry<>(key, table));
                })
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

    Map<String, String> docVars =
        vars.stream()
            .flatMap(
                docVar -> {
                  Optional<String> value =
                      DocTableGenerator.resolveColumnSteps(docs, docRef, docVar.value, language);

                  if (value.isPresent()) {
                    return Stream.of(new SimpleEntry<>("docVar:" + docVar.name, value.get()));
                  }

                  return Stream.empty();
                })
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

    Map<String, String> specialties = new LinkedHashMap<>(getVars());
    specialties.putAll(docTables);
    specialties.putAll(docVars);

    TagReplacer tagReplacer = new TagReplacer(docRef, language, specialties);

    String docText = template.isEmpty() ? body : template.get().replace(asTag(BODY), body);

    return tagReplacer.replaceStrings(docText);
  }

  static String asTag(String name) {
    return String.format("{@%s}", name);
  }

  String getDocText(String language) {
    if (isMethod()) {
      String text = getDocText(method, language);
      if (hasAlias()) {
        text = aliasDescription.getOrDefault(language, "");
      }
      if (text.isBlank()) {
        return getOverrides()
            .map(override -> getDocText(override, language))
            .filter(overrideText -> !overrideText.isBlank())
            .findFirst()
            .orElse("");
      }
      return text;
    }
    return getDocText(type, language);
  }

  Stream<String> getDocTag(String tag) {
    if (isMethod()) {
      return Stream.concat(
              Stream.concat(
                  getDocTag(method, tag),
                  getOverrides().flatMap(override -> getDocTag(override, tag))),
              Stream.concat(
                  getDocTag(type, tag),
                  getOverridesTypes().flatMap(override -> getDocTag(override, tag))))
          .filter(s -> !s.isBlank());
    }
    if (isType()) {
      return getDocTag(type, tag).filter(s -> !s.isBlank());
    }

    return Stream.of(getVars().getOrDefault(tag, "")).filter(s -> !s.isBlank());
  }

  boolean hasDocs() {
    return !Objects.isNull(getType().doc) && !getType().doc.isEmpty();
  }

  Set<String> getDocLanguages() {
    if (Objects.isNull(getType().doc)) {
      return Set.of();
    }

    return getType().doc.stream()
        .flatMap(map -> map.keySet().stream())
        .filter(key -> key.startsWith(LANG_PREFIX) && !key.startsWith(LANG_ALL))
        .map(key -> key.replace(LANG_PREFIX, "").toLowerCase())
        .collect(Collectors.toSet());
  }

  static Stream<String> getDocTag(ElementDocs element, String name) {
    if (Objects.isNull(element) || Objects.isNull(element.doc)) {
      return Stream.of();
    }

    return element.doc.stream()
        .flatMap(map -> map.entrySet().stream())
        .filter(entry -> Objects.equals(entry.getKey(), name))
        .flatMap(entry -> entry.getValue().stream());
  }

  static String getDocText(ElementDocs element, String language) {
    if (Objects.isNull(element.doc)) {
      return "";
    }

    return element.doc.stream()
        .flatMap(map -> map.entrySet().stream())
        .filter(
            entry ->
                entry.getKey().toLowerCase().startsWith(LANG_PREFIX + language)
                    || Objects.equals(entry.getKey(), LANG_ALL)
                    || Objects.equals(entry.getKey(), BODY))
        .flatMap(entry -> entry.getValue().stream())
        .collect(Collectors.joining("\n\n"));
  }

  boolean hasAlias() {
    return Objects.nonNull(alias);
  }

  String getAlias() {
    return alias;
  }

  DocRef addVar(String name, String value) {
    additionalVars.put(name, value);
    return this;
  }
}
