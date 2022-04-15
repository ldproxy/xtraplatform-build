package de.interactive_instruments.xtraplatform.docs;

import java.util.List;

class AnnotationDocs extends ElementDocs{

  List<ValueDocs> values;

  AnnotationDocs() {
  }

  static class ValueDocs extends ElementDocs {
    String value;

    ValueDocs() {
    }
  }
}
