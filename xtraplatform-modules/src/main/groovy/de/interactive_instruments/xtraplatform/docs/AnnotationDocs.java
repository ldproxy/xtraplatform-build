package de.interactive_instruments.xtraplatform.docs;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

class AnnotationDocs extends ElementDocs{

  Map<String, String> attributes;

  boolean hasAttribute(String name) {
    if (Objects.isNull(attributes)) {
      return false;
    }

    return attributes.containsKey(name);
  }

  Optional<String> getAttribute(String name) {
    if (Objects.isNull(attributes)) {
      return Optional.empty();
    }

    return Optional.ofNullable(attributes.get(name));
  }
}
