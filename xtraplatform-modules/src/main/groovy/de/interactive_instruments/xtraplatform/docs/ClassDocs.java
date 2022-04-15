package de.interactive_instruments.xtraplatform.docs;

import java.util.List;

class ClassDocs extends ElementDocs {

  String typeName;
  String modifiers;
  ElementDocs superClass;
  List<AnnotationDocs> annotations;
  List<ElementDocs> constructors;

  ClassDocs() {
  }

  @Override
  public String toString() {
    return "ClassDocs{" +
        "typeName='" + typeName + '\'' +
        ", modifiers='" + modifiers + '\'' +
        //", superClass=" + superClass +
        //", annotations=" + annotations +
        ", constructors=" + constructors +
        ", qualifiedName='" + qualifiedName + '\'' +
        ", name='" + name + '\'' +
        ", doc='" + doc + '\'' +
        '}';
  }
}
