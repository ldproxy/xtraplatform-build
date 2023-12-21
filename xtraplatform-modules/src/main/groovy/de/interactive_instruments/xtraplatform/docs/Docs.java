package de.interactive_instruments.xtraplatform.docs;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Docs {

  private final List<LayerDocs> layers;

  Docs(List<LayerDocs> layers) {
    this.layers = layers;
  }

  TypeDocs findType(String qualifiedName) {
    return layers.stream()
        .map(layer -> layer.getType(qualifiedName))
        .filter(java.util.Optional::isPresent)
        .map(java.util.Optional::get)
        .findFirst()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Referenced type could not be resolved: " + qualifiedName));
  }

  DocRef findTypeRef(String qualifiedName) {
    return layers.stream()
        .flatMap(
            layer ->
                getTypes(layer, typeDocs -> Objects.equals(typeDocs.qualifiedName, qualifiedName)))
        .findFirst()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Referenced type could not be resolved: " + qualifiedName));
  }

  List<DocRef> findTypeByInterface(String qualifiedName) {
    return layers.stream()
        .flatMap(layer -> getTypes(layer, typeDocs -> typeDocs.hasInterface(qualifiedName)))
        .collect(Collectors.toList());
  }

  List<DocRef> findTypeBySuperClass(String qualifiedName) {
    return layers.stream()
        .flatMap(layer -> getTypes(layer, typeDocs -> typeDocs.hasSuperClass(qualifiedName)))
        .collect(Collectors.toList());
  }

  Stream<DocRef> getTypes(LayerDocs layer, Predicate<TypeDocs> predicate) {
    if (Objects.isNull(layer.modules)) {
      return Stream.of();
    }

    return layer.modules.values().stream()
        .flatMap(
            module ->
                module.getTypes(predicate).map(type -> new DocRef(this, layer, module, type)));
  }

  Stream<DocRef> streamTypes() {
    return layers.stream()
        .flatMap(
            layer ->
                layer.modules.values().stream()
                    .flatMap(
                        module ->
                            module.api.values().stream()
                                .map(type -> new DocRef(this, layer, module, type))));
  }

  Stream<LayerDocs> streamLayers() {
    return layers.stream();
  }

  List<DocFile> getDocFiles() {
    return streamTypes()
        .filter(typeRef -> typeRef.getType().hasAnnotation(DocFile.ANNOTATION))
        .map(
            typeRef ->
                DocFile.from(typeRef, typeRef.getType().getAnnotation(DocFile.ANNOTATION).get()))
        .collect(Collectors.toList());
  }

  List<DocFilesTemplate> getDocFilesTemplates() {
    return streamTypes()
        .filter(typeRef -> typeRef.getType().hasAnnotation(DocFilesTemplate.ANNOTATION))
        .map(
            typeRef ->
                DocFilesTemplate.from(
                    typeRef, typeRef.getType().getAnnotation(DocFilesTemplate.ANNOTATION).get()))
        .collect(Collectors.toList());
  }

  Optional<DocFile> getDocDefs(DocRef typeRef) {
    if (typeRef.getType().hasAnnotation(DocFile.ANNOTATION_DEFS)) {
      return Optional.of(
          DocFile.from(typeRef, typeRef.getType().getAnnotation(DocFile.ANNOTATION_DEFS).get()));
    }

    return Optional.empty();
  }
}
