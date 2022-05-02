package de.interactive_instruments.xtraplatform.docs;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

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

  boolean hasInterfaces() {
    return Objects.nonNull(interfaces)
        && !interfaces.isEmpty();
  }

  Optional<MethodDocs> findOverride(MethodDocs child) {
    if (Objects.isNull(methods)) {
      return Optional.empty();
    }
    return methods.stream()
        //TODO: methodDocs.getSignature
        .filter(methodDocs -> Objects.equals(methodDocs.qualifiedName, child.qualifiedName)
            && Objects.equals(methodDocs.parameters, child.parameters))
        .findFirst();
  }
}
