package de.interactive_instruments.xtraplatform.docs;

import de.interactive_instruments.xtraplatform.Maturity;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

class ModuleDocs {
  String id;
  String name;
  String version;
  String description;
  Maturity maturity;
  boolean deprecated;
  Set<String> exports;
  Set<String> requires;
  Map<String, TypeDocs> api;

  Optional<TypeDocs> getType(String qualifiedName) {
    if (Objects.isNull(api)) {
      return Optional.empty();
    }

    return Optional.ofNullable(api.get(qualifiedName));
  }
}
