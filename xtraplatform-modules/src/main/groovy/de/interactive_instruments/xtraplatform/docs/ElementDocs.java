package de.interactive_instruments.xtraplatform.docs;

import com.google.common.base.Splitter;
import java.util.AbstractMap.SimpleEntry;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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

  String getDocText(String language) {
    if (Objects.isNull(doc)) {
      return "";
    }

    return doc.stream()
        .flatMap(map -> map.entrySet().stream())
        .filter(entry -> entry.getKey().toLowerCase().startsWith(LANG_PREFIX + language)
            || Objects.equals(entry.getKey(), LANG_ALL)
            || Objects.equals(entry.getKey(), BODY))
        .flatMap(entry -> entry.getValue().stream())
        .collect(Collectors.joining("\n\n"));
  }
  String getDocText(String language, TypeFinder typeFinder, Optional<String> template, Map<String, String> vars) {
    if (Objects.isNull(doc)) {
      return "";
    }

    String body = getDocText(language);

    Map<String, String> propertyTables = doc.stream()
        .flatMap(map -> map.entrySet().stream())
        .filter(entry -> entry.getKey().startsWith(PROPERTY_TABLE))
        .map(entry -> {
          TypeDocs typeDocs = typeFinder.find(entry.getValue().get(0));
          return new SimpleEntry<>(entry.getKey(), typeDocs.getPropertyTable(language, typeFinder));
        })
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

    Map<String, String> docTables = getAnnotation("de.ii.xtraplatform.docs.DocTable")
        .map(docTableDef -> {
          Entry<String, String> docTable = DocTableGenerator.generate(docTableDef, this, typeFinder, vars);
          return Map.ofEntries(docTable);
        })
        .orElse(Map.<String, String>of());

    //TODO: multilang
    String endpointHeader = String.format("| %s | %s | %s | %s | %s |\n| --- | --- | --- | --- | --- |\n", "Resource", "Path", "HTTP Methods", "Media Types", "Description");

    Optional<String> endpointTable = doc.stream()
        .flatMap(map -> map.entrySet().stream())
        .filter(entry -> Objects.equals(entry.getKey(), ENDPOINT_TABLE))
        .flatMap(entry -> SPLITTER.splitToStream(entry.getValue().get(0)))
        .map(qualifiedName -> {
          TypeDocs typeDocs = typeFinder.find(qualifiedName);
          return typeDocs.getEndpointRow(language, typeFinder);
        })
        .collect(Collectors.collectingAndThen(
            Collectors.joining(""),
            rows -> rows.isEmpty()
                ? Optional.empty()
                : Optional.of(endpointHeader + rows)));

    //TODO: multilang
    String queryParameterHeader = String.format("| %s | %s | %s |\n| --- | --- | --- |\n", "Name", "Resources", "Description");

    Optional<String> queryParameterTable = doc.stream()
        .flatMap(map -> map.entrySet().stream())
        .filter(entry -> Objects.equals(entry.getKey(), QUERY_PARAMETER_TABLE))
        .flatMap(entry -> SPLITTER.splitToStream(entry.getValue().get(0)))
        .map(qualifiedName -> {
          TypeDocs typeDocs = typeFinder.find(qualifiedName);
          return typeDocs.getQueryParameterRow(language, typeFinder);
        })
        .collect(Collectors.collectingAndThen(
            Collectors.joining(""),
            rows -> rows.isEmpty()
                ? Optional.empty()
                : Optional.of(queryParameterHeader + rows)));

    Optional<String> example = doc.stream()
        .flatMap(map -> map.entrySet().stream())
        .filter(entry -> Objects.equals(entry.getKey(), EXAMPLE))
        .flatMap(entry -> {
          TypeDocs typeDocs = typeFinder.find(entry.getValue().get(0));
          return typeDocs.getDocTag(EXAMPLE);
        })
        .findFirst();

    Map<String, String> specialties = new LinkedHashMap<>(vars);
    specialties.putAll(docTables);
    propertyTables.forEach((k,p) -> specialties.put(k, p));
    endpointTable.ifPresent(e -> specialties.put(ENDPOINT_TABLE, e));
    queryParameterTable.ifPresent(q -> specialties.put(QUERY_PARAMETER_TABLE, q));
    example.ifPresent(e -> specialties.put(EXAMPLE, e));

    TagReplacer tagReplacer = new TagReplacer(this, specialties, language);

    String docText = template.isEmpty()
        ? body
        : template.get().replace(asTag(BODY), body);

    return tagReplacer.replaceStrings(docText);
  }
  static String asTag(String name) {
    return String.format("{@%s}", name);
  }
}
