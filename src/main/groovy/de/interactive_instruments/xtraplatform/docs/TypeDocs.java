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

  boolean hasSuperClass(String qualifiedName) {
    return Objects.nonNull(superClass) && Objects.equals(superClass.qualifiedName, qualifiedName);
  }

  boolean hasInterfaces() {
    return Objects.nonNull(interfaces) && !interfaces.isEmpty();
  }

  Optional<MethodDocs> findOverride(MethodDocs child) {
    if (Objects.isNull(methods)) {
      return Optional.empty();
    }
    return methods.stream()
        // TODO: methodDocs.getSignature
        .filter(methodDocs -> methodEquals(methodDocs, child))
        .findFirst();
  }

  private static boolean methodEquals(MethodDocs e1, MethodDocs e2) {
    List<VariableDocs> parameters1 = Objects.requireNonNullElse(e1.parameters, List.of());
    List<VariableDocs> parameters2 = Objects.requireNonNullElse(e2.parameters, List.of());

    if (!Objects.equals(e1.qualifiedName, e2.qualifiedName)
        || parameters1.size() != parameters2.size()) {
      return false;
    }

    for (int i = 0; i < parameters1.size(); i++) {
      if (!Objects.equals(parameters1.get(i).qualifiedName, parameters2.get(i).qualifiedName)
          || !Objects.equals(parameters1.get(i).type, parameters2.get(i).type)) {
        return false;
      }
    }

    return true;
  }
}
