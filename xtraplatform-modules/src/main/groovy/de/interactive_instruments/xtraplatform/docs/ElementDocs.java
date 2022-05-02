package de.interactive_instruments.xtraplatform.docs;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

class ElementDocs {
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

  Stream<AnnotationDocs> getAnnotations() {
    if (Objects.isNull(annotations)) {
      return Stream.empty();
    }

    return annotations.stream();
  }
  static String asTag(String name) {
    return String.format("{@%s}", name);
  }
}
