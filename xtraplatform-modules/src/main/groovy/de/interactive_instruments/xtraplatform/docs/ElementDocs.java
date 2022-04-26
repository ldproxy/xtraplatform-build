package de.interactive_instruments.xtraplatform.docs;

import com.google.common.base.Splitter;
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
  static final String ENDPOINT_TABLE = "endpointTable";
  static final String QUERY_PARAMETER_TABLE = "queryParameterTable";
  static final String EXAMPLE = "example";
  static final Set<String> METHODS = Set.of(
      "javax.ws.rs.GET",
      "javax.ws.rs.POST",
      "javax.ws.rs.PUT",
      "javax.ws.rs.DELETE",
      "javax.ws.rs.PATCH"
  );
  static final Splitter SPLITTER = Splitter.on(',')
      .omitEmptyStrings()
      .trimResults();
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

  Stream<String> getDocTag(String name) {
    if (Objects.isNull(doc)) {
      return Stream.of();
    }

    return doc.stream()
        .flatMap(map -> map.entrySet().stream())
        .filter(entry -> Objects.equals(entry.getKey(), name))
        .flatMap(entry -> entry.getValue().stream());
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

  String getDocText(String language, TypeFinder typeFinder, Optional<String> template) {
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

    String propertyTable = doc.stream()
        .flatMap(map -> map.entrySet().stream())
        .filter(entry -> Objects.equals(entry.getKey(), PROPERTY_TABLE))
        .map(entry -> {
          TypeDocs typeDocs = typeFinder.find(entry.getValue().get(0));
          return typeDocs.getPropertyTable(language, typeFinder);
        })
        .findFirst()
        .orElse("NO CONFIGURATION TODO");

    //TODO: multilang
    String endpointHeader = String.format("| %s | %s | %s | %s |\n| --- | --- | --- | --- |\n", "Resource", "Path", "HTTP Methods", "Media Types");

    String endpointTable = doc.stream()
        .flatMap(map -> map.entrySet().stream())
        .filter(entry -> Objects.equals(entry.getKey(), ENDPOINT_TABLE))
        .flatMap(entry -> SPLITTER.splitToStream(entry.getValue().get(0)))
        .map(qualifiedName -> {
          TypeDocs typeDocs = typeFinder.find(qualifiedName);
          return typeDocs.getEndpointRow(language, typeFinder);
        })
        .collect(Collectors.joining("",endpointHeader,""));

    //TODO: multilang
    String queryParameterHeader = String.format("| %s | %s | %s |\n| --- | --- | --- |\n", "Name", "Resources", "Description");

    String queryParameterTable = doc.stream()
        .flatMap(map -> map.entrySet().stream())
        .filter(entry -> Objects.equals(entry.getKey(), QUERY_PARAMETER_TABLE))
        .flatMap(entry -> SPLITTER.splitToStream(entry.getValue().get(0)))
        .map(qualifiedName -> {
          TypeDocs typeDocs = typeFinder.find(qualifiedName);
          return typeDocs.getQueryParameterRow(language, typeFinder);
        })
        .collect(Collectors.joining("",queryParameterHeader,""));

    String example = doc.stream()
        .flatMap(map -> map.entrySet().stream())
        .filter(entry -> Objects.equals(entry.getKey(), EXAMPLE))
        .flatMap(entry -> {
          TypeDocs typeDocs = typeFinder.find(entry.getValue().get(0));
          return typeDocs.getDocTag(EXAMPLE);
        })
        .findFirst()
        .orElse("NO EXAMPLE TODO");

    if (template.isEmpty()) {
      //TODO: parser adds blank to inline tags
      return body
          .replace(asTag(PROPERTY_TABLE + " "), propertyTable)
          .replace(asTag(ENDPOINT_TABLE + " "), endpointTable)
          .replace(asTag(EXAMPLE + " "), example);
    }

    return template.get()
        .replace(asTag(BODY), body)
        .replace(asTag(PROPERTY_TABLE), propertyTable)
        .replace(asTag(ENDPOINT_TABLE), endpointTable)
        .replace(asTag(QUERY_PARAMETER_TABLE), queryParameterTable)
        .replace(asTag(EXAMPLE), example);
  }

  static String asTag(String name) {
    return String.format("{@%s}", name);
  }
}
