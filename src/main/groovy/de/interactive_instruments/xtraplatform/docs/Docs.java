package de.interactive_instruments.xtraplatform.docs;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Docs {

  private final List<LayerDocs> layers;
  private final Map<String, DocTableGenerator> tableGenerators;
  private final Map<String, Map<String, String>> vars;

  Docs(List<LayerDocs> layers) {
    this.layers = layers;
    this.tableGenerators = new HashMap<>();
    this.vars = new HashMap<>();
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

  DocTableGenerator getTableGenerator(DocRef typeRef) {
    return tableGenerators.computeIfAbsent(
        typeRef.getType().qualifiedName, ignore -> new DocTableGenerator(typeRef, this));
  }

  Map<String, String> getVars(LayerDocs layer, ModuleDocs module) {
    return vars.computeIfAbsent(
        layer.id + module.id,
        ignore ->
            Map.of(
                "layer.name",
                layer.name,
                "layer.nameSuffix",
                layer.name.contains("-")
                    ? layer.name.substring(layer.name.lastIndexOf("-") + 1)
                    : layer.name,
                "module.name",
                module.name,
                // "module.version",
                // module.version,
                "module.description",
                module.description,
                Objects.nonNull(module.descriptionDe) ? "module.descriptionDe" : "module.dummy",
                Objects.requireNonNullElse(module.descriptionDe, ""),
                "module.maturity",
                module.maturity.name().toLowerCase(),
                "module.maturityBadge",
                module.maturity.toBadge().name().toLowerCase(),
                "module.maintenance",
                module.maintenance.name().toLowerCase(),
                "module.maintenanceBadge",
                module.maintenance.toBadge().name().toLowerCase(),
                "module.deprecated",
                Boolean.toString(module.deprecated)));
  }
}
