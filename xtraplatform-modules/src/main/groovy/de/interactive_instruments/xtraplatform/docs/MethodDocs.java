package de.interactive_instruments.xtraplatform.docs;

import com.google.common.base.Splitter;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class MethodDocs extends ElementDocs {

  static final String JSON_PROPERTY = "com.fasterxml.jackson.annotation.JsonProperty";
  static final String JSON_IGNORE = "com.fasterxml.jackson.annotation.JsonIgnore";
  static final String JSON_ALIAS = "com.fasterxml.jackson.annotation.JsonAlias";
  private static final Splitter SPLITTER = Splitter.on(',')
      .trimResults()
      .omitEmptyStrings();

  List<VariableDocs> parameters;
  boolean isConstructor;
  String returnType;

  //TODO: since ?
  String getPropertyRow(String language, TypeFinder typeFinder) {
    if (Objects.isNull(doc) || hasAnnotation(JSON_IGNORE)) {
      return "";
    }

    Optional<String> name = getAnnotation(JSON_PROPERTY).flatMap(
        annotationDocs -> annotationDocs.getAttribute("value"));

    if (name.isEmpty()) {
      return "";
    }

    List<MethodDocs> overrides = getOverrides(typeFinder);
    String dataType = getDataType(returnType);

    String defaultValue = Stream.concat(
            getDocTag("default"),
            overrides.stream()
                .flatMap(methodDocs -> methodDocs.getDocTag("default")))
        .filter(s -> !s.isEmpty())
        .findFirst()
        .map(s -> String.format("`%s`", s))
        .orElse("");

    String since = Stream.concat(
            getDocTag("since"),
            overrides.stream()
                .flatMap(methodDocs -> methodDocs.getDocTag("since")))
        .filter(s -> !s.isEmpty())
        .findFirst()
        .orElse("v2.0");

    Stream<String> parentDescription = overrides.stream()
        .map(methodDocs -> methodDocs.getDocText(language).replaceAll("\n", " "))
        .filter(text -> !text.isEmpty());

    String description = Stream.concat(parentDescription,
            Stream.of(getDocText(language).replaceAll("\n", " ")))
        .distinct()
        .collect(Collectors.joining("<br/>"));

    String row = String.format("| `%s` | %s | %s | %s | %s |\n", name.get(), dataType, defaultValue,
        since, description);

    Optional<String> alias = getAnnotation(JSON_ALIAS).flatMap(
        annotationDocs -> annotationDocs.getAttribute("value"));

    if (alias.isPresent()) {
      String aliasDescription = String.format("*Deprecated* See `%s`.", name.get());
      String aliasRows = SPLITTER.splitToStream(alias.get().replaceAll("\"", ""))
          .map(a -> String.format("| `%s` | %s | %s | %s | %s |\n", a, dataType, defaultValue,
              since, aliasDescription))
          .collect(Collectors.joining());
      return row + aliasRows;
    }

    return row;
  }

  private List<MethodDocs> getOverrides(TypeFinder typeFinder) {
    return getDocTag("_overrides_")
        .map(typeFinder::find)
        .map(typeDocs -> typeDocs.findOverride(this))
        .filter(Optional::isPresent)
        .map(Optional::get)
        .collect(Collectors.toList());
  }

  static String getDataType(String type) {
    if (type.startsWith("java.util.List")
        || type.startsWith("java.util.Set")
        || type.startsWith("java.util.Collection")
        || type.endsWith("[]")) {
      return "array";
    }
    if (hasSimpleType(type, "string")) {
      return "string";
    }
    if (hasSimpleType(type, "boolean")) {
      return "boolean";
    }
    if (hasSimpleType(type, "int", "integer", "long", "double", "float")) {
      return "number";
    }

    return "object";
  }

  static boolean hasSimpleType(String type, String... matches) {
    for (String match : matches) {
      if (type.equalsIgnoreCase(match)
          || type.equalsIgnoreCase(String.format("java.lang.%s", match))
          || type.equalsIgnoreCase(String.format("java.util.Optional<java.lang.%s>", match))) {
        return true;
      }
    }
    return false;
  }
}
