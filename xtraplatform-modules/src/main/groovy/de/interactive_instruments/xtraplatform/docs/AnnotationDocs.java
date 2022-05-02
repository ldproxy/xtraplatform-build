package de.interactive_instruments.xtraplatform.docs;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

class AnnotationDocs extends ElementDocs {

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

  Optional<String> getAttributeAsJson(String name) {
    return getAttribute(name).map(attribute -> attribute
        .replaceAll("(^|=)\\{?@[\\w.]+\\(", "$1[{")
        .replaceAll("\\{?@[\\w.]+\\(", "{")
        .replaceAll("\\),", "},")
        .replaceAll("\\)}", "}]")
        .replaceAll("\\)$", "}]")
        .replaceAll("=([\"\\[])", ":$1")
        .replaceAll("=\\{\"", ":[\"")
        .replaceAll("\"}}", "\"]}")
        .replaceAll("=(?:\\w+\\.)*(\\w+)", ":\"$1\""));
  }
}
