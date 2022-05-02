package de.interactive_instruments.xtraplatform.docs;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.List;

class DocVar {

  private static final Gson GSON = new Gson();
  private static final Type LIST_STEP_TYPE = new TypeToken<List<DocStep>>() {
  }.getType();

  final String name;
  final List<DocStep> value;

  DocVar(String name, List<DocStep> value) {
    this.name = name;
    this.value = value;
  }

  static DocVar from(AnnotationDocs def) {
    String name = def.getAttribute("name")
        .orElseThrow();
    String value = def.getAttributeAsJson("value")
        .orElseThrow();
    List<DocStep> valueDef = GSON.fromJson(value, LIST_STEP_TYPE);

    return new DocVar(name, valueDef);
  }
}
