package de.interactive_instruments.xtraplatform.docs;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class ElementDocs {

  static final String LANG_PREFIX = "lang";
  static final String LANG_ALL = "langAll";
  static final String BODY = "body";
  static final String PROPERTY_TABLE = "propertyTable";
  static final String EXAMPLE = "example";
  String qualifiedName;
  List<Map<String, List<String>>> doc;
  List<AnnotationDocs> annotations;
  List<String> types;
  Set<String> modifiers;

  String getName() {
    if (Objects.nonNull(qualifiedName) && qualifiedName.contains(".")) {
      return qualifiedName.substring(qualifiedName.lastIndexOf('.') + 1);
    }

    return qualifiedName;
  }

  boolean hasAnnotation(String qualifiedName) {
    return Objects.nonNull(annotations)
        && annotations.stream()
        .anyMatch(annotationDocs -> Objects.equals(annotationDocs.qualifiedName, qualifiedName));
  }

  Optional<AnnotationDocs> getAnnotation(String qualifiedName) {
    if (Objects.isNull(annotations)) {
      return Optional.empty();
    }

    return annotations.stream()
        .filter(annotationDocs -> Objects.equals(annotationDocs.qualifiedName, qualifiedName))
        .findFirst();
  }

  List<String> getDocTag(String name) {
    if (Objects.isNull(doc)) {
      return List.of();
    }

    return doc.stream()
        .flatMap(map -> map.entrySet().stream())
        .filter(entry -> Objects.equals(entry.getKey(), name))
        .flatMap(entry -> entry.getValue().stream())
        .collect(Collectors.toList());
  }

  Set<String> getDocLanguages() {
    if (Objects.isNull(doc)) {
      return Set.of();
    }

    return doc.stream()
        .flatMap(map -> map.keySet().stream())
        .filter(key -> key.startsWith(LANG_PREFIX) && !key.startsWith(LANG_ALL))
        .map(key -> key.replace(LANG_PREFIX, "").toLowerCase())
        .collect(Collectors.toSet());
  }

  String getDocText(String language, Function<String, TypeDocs> typeFinder, Optional<String> template) {
    if (Objects.isNull(doc)) {
      return "";
    }

    String body = doc.stream()
        .flatMap(map -> map.entrySet().stream())
        .filter(entry -> entry.getKey().toLowerCase().startsWith(LANG_PREFIX + language)
            || Objects.equals(entry.getKey(), LANG_ALL)
            || Objects.equals(entry.getKey(), BODY))
        .flatMap(entry -> entry.getValue().stream())
        .collect(Collectors.joining("\n\n"));

    if (template.isEmpty()) {
      return body;
    }

    String propertyTable = doc.stream()
        .flatMap(map -> map.entrySet().stream())
        .filter(entry -> Objects.equals(entry.getKey(), PROPERTY_TABLE))
        .map(entry -> {
          TypeDocs typeDocs = typeFinder.apply(entry.getValue().get(0));
          return typeDocs.getPropertyTable(language, typeFinder);
        })
        .findFirst()
        .orElse("NO CONFIGURATION TODO");

    String example = doc.stream()
        .flatMap(map -> map.entrySet().stream())
        .filter(entry -> Objects.equals(entry.getKey(), EXAMPLE))
        .flatMap(entry -> {
          TypeDocs typeDocs = typeFinder.apply(entry.getValue().get(0));
          return typeDocs.getDocTag(EXAMPLE).stream();
        })
        .findFirst()
        .orElse("NO EXAMPLE TODO");

    return template.get()
        .replace(asTag(BODY), body)
        .replace(asTag(PROPERTY_TABLE), propertyTable)
        .replace(asTag(EXAMPLE), example);
  }

  static String asTag(String name) {
    return String.format("{@%s}", name);
  }
}
