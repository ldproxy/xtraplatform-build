package de.interactive_instruments.xtraplatform.docs;

class ElementDocs {
  String qualifiedName;
  String name;
  String doc;

  ElementDocs() {
  }

  @Override
  public String toString() {
    return "ElementDocs{" +
        "qualifiedName='" + qualifiedName + '\'' +
        ", name='" + name + '\'' +
        ", doc='" + doc + '\'' +
        '}';
  }
}
