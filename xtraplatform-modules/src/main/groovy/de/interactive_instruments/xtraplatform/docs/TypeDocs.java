package de.interactive_instruments.xtraplatform.docs;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;
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

  String getPropertyTable(String language, Function<String, TypeDocs> typeFinder) {
    if (Objects.isNull(methods)) {
      return "";
    }

    //TODO: multilang
    String header = String.format("| %s | %s | %s | %s |\n| --- | --- | --- | --- |\n", "Option", "Type", "Default", "Description");

    return methods.stream()
        .map(methodDocs -> methodDocs.getPropertyRow(language, typeFinder))
        .collect(Collectors.joining("",header,""));
  }
}
