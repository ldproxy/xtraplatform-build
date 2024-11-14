package de.interactive_instruments.xtraplatform.docs;

import java.util.List;
import java.util.Objects;

public class DocStep {
  enum Step {
    MODULES(DocRef.class, DocRef.class, false, true),
    IMPLEMENTATIONS(DocRef.class, DocRef.class, false, true),
    TAG_REFS(DocRef.class, DocRef.class, false, true),
    METHODS(DocRef.class, DocRef.class, false, true),
    JSON_PROPERTIES(DocRef.class, DocRef.class, false, true),
    MARKED(DocRef.class, DocRef.class, false, true),
    UNMARKED(DocRef.class, DocRef.class, false, true),
    SORTED(DocRef.class, DocRef.class, true, true),
    ANNOTATIONS(DocRef.class, String.class, false, true),
    TAG(DocRef.class, String.class, false, false),
    JSON_NAME(DocRef.class, String.class, false, false),
    JSON_TYPE(DocRef.class, String.class, false, false),
    CONSTANT(DocRef.class, String.class, false, false),
    FILTER(String.class, String.class, false, false),
    FORMAT(String.class, String.class, false, false),
    COLLECT(String.class, String.class, true, false);

    final Class<?> in;
    final Class<?> out;
    final boolean multipleIn;
    final boolean multipleOut;

    Step(Class<?> in, Class<?> out, boolean multipleIn, boolean multipleOut) {
      this.in = in;
      this.out = out;
      this.multipleIn = multipleIn;
      this.multipleOut = multipleOut;
    }
  }

  Step type;
  private List<String> params;

  DocStep(Step type) {
    this(type, List.of());
  }

  DocStep(Step type, List<String> params) {
    this.type = type;
    this.params = params;
  }

  // needed because gson always sets params=null
  List<String> params() {
    return Objects.requireNonNullElse(params, List.of());
  }
}
