package de.interactive_instruments.xtraplatform.docs;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

class TypeDocs extends ElementDocs {

  ElementDocs superClass;
  List<ElementDocs> interfaces;
  List<MethodDocs> methods;
  List<VariableDocs> fields;

  boolean hasInterface(String qualifiedName) {
    return Objects.nonNull(interfaces)
        && interfaces.stream()
        .anyMatch(elementDocs -> Objects.equals(elementDocs.qualifiedName, qualifiedName));
  }

  String getPropertyTable(String language, TypeFinder typeFinder) {
    if (Objects.isNull(methods)) {
      return "";
    }

    //TODO: multilang
    String header = String.format("| %s | %s | %s | %s |\n| --- | --- | --- | --- |\n", "Option", "Type", "Default", "Description");

    return methods.stream()
        .map(methodDocs -> methodDocs.getPropertyRow(language, typeFinder))
        .collect(Collectors.joining("",header,""));
  }

  String getEndpointRow(String language, TypeFinder typeFinder) {
    if (Objects.isNull(doc)) {
      return "";
    }

    String description = getDocText(language, typeFinder, Optional.empty()).replaceAll("\n", " ").trim();

    if (description.isEmpty()) {
      return "";
    }

    String path = getDocTag("path").findFirst()
        .map(p -> "`" + p.trim() + "`")
        .orElse("")
        .replaceAll("\n", " ")
        .trim();
    String httpMethods = methods.stream()
        .flatMap(methodDocs -> methodDocs.annotations.stream())
        .filter(annotationDocs -> ElementDocs.METHODS.contains(annotationDocs.qualifiedName))
        .map(ElementDocs::getName)
        .distinct()
        .sorted()
        .collect(Collectors.joining(", "));
    String formats = getDocTag("formats").findFirst()
        .map(f -> typeFinder.findByInterface(f)
            .stream()
            .flatMap(typeDocs -> typeDocs.getDocTag("format"))
            .map(String::trim)
            .distinct()
            .sorted()
            .collect(Collectors.joining(", ")))
        .orElse("")
        .replaceAll("\n", " ");

    return String.format("| %s | %s | %s | %s |\n", description, path, httpMethods, formats);
  }

  String getQueryParameterRow(String language, TypeFinder typeFinder) {
    if (Objects.isNull(doc)) {
      return "";
    }

    String description = getDocText(language, typeFinder, Optional.empty()).replaceAll("\n", " ").trim();

    if (description.isEmpty()) {
      return "";
    }

    String name = getDocTag("name").findFirst()
        .orElse("")
        .replaceAll("\n", " ")
        .trim();
    String endpoints = getDocTag("endpoints").findFirst()
        .orElse("")
        .replaceAll("\n", " ")
        .trim();

    return String.format("| %s | %s | %s |\n", name, endpoints, description);
  }
}
