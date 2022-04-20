package de.interactive_instruments.xtraplatform.docs;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

public class MethodDocs extends ElementDocs {

  List<VariableDocs> parameters;
  boolean isConstructor;

  //TODO: return value
  //TODO: JsonAlias -> deprecated
  //TODO: since ?
  String getPropertyRow(String language, Function<String, TypeDocs> typeFinder) {
    if (Objects.isNull(doc)
        || hasAnnotation("com.fasterxml.jackson.annotation.JsonIgnore")) {
      return "";
    }

    Optional<Object> name = getAnnotation("com.fasterxml.jackson.annotation.JsonProperty").flatMap(annotationDocs -> annotationDocs.getAttribute("value"));

    if (name.isEmpty()) {
      return "";
    }

    String dataType = "TODO";
    String defaultValue = doc.get(0).getOrDefault("default", List.of("")).get(0).replaceAll("\n", " ");
    String description = getDocText(language, typeFinder, Optional.empty()).replaceAll("\n", " ");

    return String.format("| `%s` | %s | `%s` | %s |\n", name.get(), dataType, defaultValue, description);
  }
}
