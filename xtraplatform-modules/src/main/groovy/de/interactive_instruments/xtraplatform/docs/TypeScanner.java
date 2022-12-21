package de.interactive_instruments.xtraplatform.docs;

import com.sun.source.doctree.DocCommentTree;
import com.sun.source.util.DocTrees;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementScanner9;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

/**
 * A scanner to display the structure of a series of elements and their documentation comments.
 */
class TypeScanner extends ElementScanner9<List<ElementDocs>, Integer> {


  private final DocTrees treeUtils;
  private final Elements elementUtils;
  private final Types typeUtils;

  TypeScanner(DocTrees treeUtils, Elements elementUtils, Types typeUtils) {
    super(new ArrayList<>());
    this.treeUtils = treeUtils;
    this.elementUtils = elementUtils;
    this.typeUtils = typeUtils;
  }

  List<ElementDocs> show(Set<? extends Element> elements) {
    return scan(elements, 0);
  }

  @Override
  public List<ElementDocs> scan(Element e, Integer depth) {
    return super.scan(e, depth + 1);
  }

  @Override
  public List<ElementDocs> visitType(TypeElement e, Integer integer) {
    //TODO: ancestors of superClass and interfaces
    if (integer == 1) {
      TypeDocs c = new TypeDocs();

      c.qualifiedName = e.getQualifiedName().toString();
      //c.name = e.getSimpleName().toString();
      c.modifiers = e.getModifiers()
          .stream()
          .map(m -> m.toString())
          .collect(Collectors.toSet());
      c.types = e.getTypeParameters()
          .stream()
          .map(typeParameterElement -> typeParameterElement.toString()
              + typeParameterElement.getBounds().toString())//TODO
          .collect(Collectors.toList());
      c.superClass = scanElementRef(e.getSuperclass());
      c.interfaces = e.getInterfaces()
          .stream()
          .map(typeMirror -> scanElementRef(typeMirror))
          .collect(Collectors.toList());
      c.doc = scanDocComment(e, c.qualifiedName);
      c.annotations = scanAnnotations(e);

      if (shouldInclude(c)) {
        DEFAULT_VALUE.add(c);
        super.visitType(e, integer);
      }
    }

    return DEFAULT_VALUE;
  }

  private boolean shouldInclude(TypeDocs typeDocs) {
    if (typeDocs.hasAnnotation("dagger.internal.DaggerGenerated")
        //TODO: Generated in dagger-auto
        || typeDocs.getName().startsWith("AutoBindings")) {
      //System.out.println("EXCLUDE " + typeDocs.qualifiedName);
      return false;
    }
    return true;
  }

  @Override
  public List<ElementDocs> visitExecutable(ExecutableElement e, Integer integer) {
    if (e.getKind() == ElementKind.CONSTRUCTOR || e.getKind() == ElementKind.METHOD) {
      TypeDocs typeDocs = getCurrentTypeDocs();
      MethodDocs m = new MethodDocs();
      m.qualifiedName = e.getSimpleName().toString();
      m.isConstructor = e.getKind() == ElementKind.CONSTRUCTOR;
      m.modifiers = e.getModifiers()
          .stream()
          .map(Modifier::toString)
          .collect(Collectors.toSet());
      m.types = e.getTypeParameters()
          .stream()
          .map(typeParameterElement -> typeParameterElement.toString()
              + typeParameterElement.getBounds().toString())//TODO
          .collect(Collectors.toList());
      m.annotations = scanAnnotations(e);
      m.doc = scanDocComment(e,typeDocs.qualifiedName + "::" + m.qualifiedName);
      //TODO: always add for JsonProperty?
      if (/*m.doc.stream().flatMap(d -> d.keySet().stream()).allMatch(k -> k.equals("return"))
          &&*/ m.hasAnnotation(StepResolver.JSON_PROPERTY)
          /*&& typeDocs.hasInterfaces()*/) {
        List<String> parentDocs = findParentDocs(e, ((TypeElement) e.getEnclosingElement()).getInterfaces());
        m.doc.add(Map.of("_overrides_", parentDocs));
      }
      //TODO: exceptions, returnType
      m.returnType = e.getReturnType().toString();

      if (Objects.isNull(typeDocs.methods)) {
        typeDocs.methods = new ArrayList<>();
      }
      typeDocs.methods.add(m);

      super.visitExecutable(e, integer);
    }
    //return super.visitExecutable(e, integer);
    return DEFAULT_VALUE;
  }

  private List<String> findParentDocs(ExecutableElement e, List<? extends TypeMirror> interfaces) {
    List<String> p = new ArrayList<>();
    for (TypeMirror typeMirror : interfaces) {
      p.addAll(findParentDocs(e, ((TypeElement) typeUtils.asElement(typeMirror)).getInterfaces()));
    }
    for (TypeMirror typeMirror : interfaces) {
      p.addAll(findParentDocs(e, (TypeElement) typeUtils.asElement(typeMirror)));
    }
    return p;
  }
  private List<String> findParentDocs(ExecutableElement e, TypeElement parent) {
    List<String> p = new ArrayList<>();
    for (Element element : parent.getEnclosedElements()) {
      if (element instanceof ExecutableElement
          && element.getSimpleName().equals(e.getSimpleName())
          && ((ExecutableElement) element).getParameters().equals(e.getParameters())) {
        p.add(parent.getQualifiedName().toString());
      }
    }
    return p;
  }

  private TypeDocs getCurrentTypeDocs() {
    return (TypeDocs) DEFAULT_VALUE.get(DEFAULT_VALUE.size() - 1);
  }

  @Override
  public List<ElementDocs> visitVariable(VariableElement e, Integer integer) {
    TypeDocs typeDocs = getCurrentTypeDocs();
    VariableDocs v = new VariableDocs();
    v.qualifiedName = e.getSimpleName().toString();
    v.modifiers = e.getModifiers()
        .stream()
        .map(Modifier::toString)
        .collect(Collectors.toSet());
    v.doc = scanDocComment(e, typeDocs.qualifiedName);
    v.annotations = scanAnnotations(e);
    v.type = e.asType().toString();

    if (e.getKind() == ElementKind.FIELD) {
      if (Objects.isNull(typeDocs.fields)) {
        typeDocs.fields = new ArrayList<>();
      }
      typeDocs.fields.add(v);
    } else if (e.getKind() == ElementKind.PARAMETER) {
      if (Objects.nonNull(typeDocs.methods) && !typeDocs.methods.isEmpty()) {
        MethodDocs methodDocs = typeDocs.methods.get(typeDocs.methods.size() - 1);
        if (Objects.isNull(methodDocs.parameters)) {
          methodDocs.parameters = new ArrayList<>();
        }
        methodDocs.parameters.add(v);
      }
    } else {
      //TODO: enums
      //System.out.println("UNHANDLED VARIABLE " + e.getKind() + e.getSimpleName());
    }

    return null;
  }

  //TODO
  @Override
  public List<ElementDocs> visitTypeParameter(TypeParameterElement e, Integer integer) {
    return super.visitTypeParameter(e, integer);
  }

  private List<AnnotationDocs> scanAnnotations(Element e) {
    return e.getAnnotationMirrors()
        .stream()
        .map(annotationMirror -> {
          AnnotationDocs a = new AnnotationDocs();
          a.qualifiedName = annotationMirror.getAnnotationType().toString();
          //a.name = annotationMirror.getAnnotationType().asElement().getSimpleName().toString();
          a.attributes = new LinkedHashMap<>();
          annotationMirror.getElementValues().forEach((k, v) -> {
            a.attributes.put(k.getSimpleName().toString(), v.getValue().toString());
          });
          return a;
        })
        .collect(Collectors.toList());
  }

  private List<Map<String, List<String>>> scanDocComment(Element e, String enclosingElement) {
    List<Map<String, List<String>>> tags = new ArrayList<>();
    DocCommentTree docCommentTree = treeUtils.getDocCommentTree(e);

    if (docCommentTree != null) {
      new DocCommentScanner(elementUtils, enclosingElement, DEFAULT_VALUE, tags).visit(docCommentTree, null);
    }

    return tags;
  }

  private ElementDocs scanElementRef(TypeMirror typeMirror) {
    TypeElement e = (TypeElement) typeUtils.asElement(typeMirror);

    if (Objects.isNull(e) || Objects.equals(e.getQualifiedName().toString(), "java.lang.Object")) {
      return null;
    }

    ElementDocs ref = new ElementDocs();
    ref.qualifiedName = e.getQualifiedName().toString();
    //ref.name = e.getSimpleName().toString();

    return ref;
  }
}
