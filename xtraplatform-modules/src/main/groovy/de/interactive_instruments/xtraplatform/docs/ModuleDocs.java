package de.interactive_instruments.xtraplatform.docs;

import de.interactive_instruments.xtraplatform.Maintenance;
import de.interactive_instruments.xtraplatform.Maturity;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

class ModuleDocs {
  String id;
  String name;
  String version;
  String description;
  Maturity maturity;
  Maintenance maintenance;
  boolean deprecated;
  boolean docIgnore;
  Set<String> exports;
  Set<String> requires;
  Map<String, TypeDocs> api;

  Optional<TypeDocs> getType(String qualifiedName) {
    if (Objects.isNull(api)) {
      return Optional.empty();
    }

    return Optional.ofNullable(api.get(qualifiedName));
  }

  Stream<TypeDocs> getTypes(Predicate<TypeDocs> predicate) {
    if (Objects.isNull(api)) {
      return Stream.of();
    }

    return api.values().stream()
        .filter(predicate);
  }
}
