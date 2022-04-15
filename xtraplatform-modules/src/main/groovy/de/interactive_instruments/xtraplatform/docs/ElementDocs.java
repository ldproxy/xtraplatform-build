package de.interactive_instruments.xtraplatform.docs;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

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
}
