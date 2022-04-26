package de.interactive_instruments.xtraplatform.docs;

import java.util.List;
import java.util.stream.Collectors;

public class TypeFinder {
  private final List<LayerDocs> layers;

  TypeFinder(List<LayerDocs> layers) {
    this.layers = layers;
  }

  TypeDocs find(String qualifiedName) {
    return layers.stream()
        .map(layer -> layer.getType(qualifiedName))
        .filter(java.util.Optional::isPresent)
        .map(java.util.Optional::get)
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Referenced type could not be resolved: " + qualifiedName));
  }

  List<TypeDocs> findByInterface(String qualifiedName) {
    return layers.stream()
        .flatMap(layer -> layer.getTypes(typeDocs -> typeDocs.hasInterface(qualifiedName)))
        .collect(Collectors.toList());
  }
}
