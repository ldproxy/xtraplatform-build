package de.interactive_instruments.xtraplatform.docs;

import com.sun.source.doctree.DocCommentTree;
import com.sun.source.util.DocTrees;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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

/** A scanner to display the structure of a series of elements and their documentation comments. */
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
    if (integer == 1) {
      TypeDocs c = new TypeDocs();

      c.qualifiedName = e.getQualifiedName().toString();
      // c.name = e.getSimpleName().toString();
      c.modifiers = e.getModifiers().stream().map(m -> m.toString()).collect(Collectors.toSet());
      c.types =
          e.getTypeParameters().stream()
              .map(
                  typeParameterElement ->
                      typeParameterElement.toString()
                          + typeParameterElement.getBounds().toString()) // TODO
              .collect(Collectors.toList());
      c.superClass = scanElementRef(e.getSuperclass());
      c.interfaces =
          getAllInterfaces(e).stream()
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

  private List<TypeMirror> getAllInterfaces(Element e) {
    if (!(e instanceof TypeElement)) {
      return List.of();
    }
    return Stream.concat(
            Objects.nonNull(((TypeElement) e).getSuperclass())
                ? getAllInterfaces(typeUtils.asElement(((TypeElement) e).getSuperclass())).stream()
                : Stream.empty(),
            ((TypeElement) e)
                .getInterfaces().stream()
                    .flatMap(
                        typeMirror ->
                            Stream.concat(
                                Stream.of(typeMirror),
                                getAllInterfaces(typeUtils.asElement(typeMirror)).stream())))
        .collect(Collectors.toList());
  }

  private boolean shouldInclude(TypeDocs typeDocs) {
    if (typeDocs.hasAnnotation("dagger.internal.DaggerGenerated")
        // TODO: Generated in dagger-auto
        || typeDocs.getName().startsWith("AutoBindings")) {
      // System.out.println("EXCLUDE " + typeDocs.qualifiedName);
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
      m.modifiers = e.getModifiers().stream().map(Modifier::toString).collect(Collectors.toSet());
      m.types =
          e.getTypeParameters().stream()
              .map(
                  typeParameterElement ->
                      typeParameterElement.toString()
                          + typeParameterElement.getBounds().toString()) // TODO
              .collect(Collectors.toList());
      m.annotations = scanAnnotations(e);
      m.doc = scanDocComment(e, typeDocs.qualifiedName + "::" + m.qualifiedName);

      List<String> parentDocs =
          findParentDocs(
              e,
              ((TypeElement) e.getEnclosingElement()).getInterfaces(),
              Optional.ofNullable(((TypeElement) e.getEnclosingElement()).getSuperclass()));
      m.doc.add(Map.of("_overrides_", parentDocs));

      // TODO: exceptions, returnType
      m.returnType = e.getReturnType().toString();

      if (Objects.isNull(typeDocs.methods)) {
        typeDocs.methods = new ArrayList<>();
      }
      typeDocs.methods.add(m);

      super.visitExecutable(e, integer);
    }
    // return super.visitExecutable(e, integer);
    return DEFAULT_VALUE;
  }

  private List<String> findParentDocs(
      ExecutableElement e, List<? extends TypeMirror> interfaces, Optional<TypeMirror> superclass) {
    List<String> p = new ArrayList<>();
    for (TypeMirror typeMirror : interfaces) {
      p.addAll(
          findParentDocs(
              e,
              ((TypeElement) typeUtils.asElement(typeMirror)).getInterfaces(),
              Optional.empty()));
    }
    for (TypeMirror typeMirror : interfaces) {
      p.addAll(findParentDocs(e, (TypeElement) typeUtils.asElement(typeMirror)));
    }
    superclass
        .flatMap(typeMirror -> Optional.ofNullable((TypeElement) typeUtils.asElement(typeMirror)))
        .ifPresent(
            superType -> {
              p.addAll(findParentDocs(e, superType));
            });
    return p.stream().filter(qn -> qn.startsWith("de.ii.")).collect(Collectors.toList());
  }

  private List<String> findParentDocs(ExecutableElement e, TypeElement parent) {
    List<String> p = new ArrayList<>();
    for (Element element : parent.getEnclosedElements()) {
      if (element instanceof ExecutableElement && methodEquals(e, (ExecutableElement) element)) {
        p.add(parent.getQualifiedName().toString());
      }
    }
    return p;
  }

  private static boolean methodEquals(ExecutableElement e1, ExecutableElement e2) {
    if (!Objects.equals(e1.getSimpleName(), e2.getSimpleName())
        || e1.getParameters().size() != e2.getParameters().size()) {
      return false;
    }
    for (int i = 0; i < e1.getParameters().size(); i++) {
      if (!Objects.equals(
              e1.getParameters().get(i).getSimpleName().toString(),
              e2.getParameters().get(i).getSimpleName().toString())
          || !Objects.equals(
              e1.getParameters().get(i).asType().toString(),
              e2.getParameters().get(i).asType().toString())) {
        return false;
      }
    }

    return true;
  }

  private TypeDocs getCurrentTypeDocs() {
    return (TypeDocs) DEFAULT_VALUE.get(DEFAULT_VALUE.size() - 1);
  }

  @Override
  public List<ElementDocs> visitVariable(VariableElement e, Integer integer) {
    TypeDocs typeDocs = getCurrentTypeDocs();
    VariableDocs v = new VariableDocs();
    v.qualifiedName = e.getSimpleName().toString();
    v.modifiers = e.getModifiers().stream().map(Modifier::toString).collect(Collectors.toSet());
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
      // TODO: enums
      // System.out.println("UNHANDLED VARIABLE " + e.getKind() + e.getSimpleName());
    }

    return null;
  }

  // TODO
  @Override
  public List<ElementDocs> visitTypeParameter(TypeParameterElement e, Integer integer) {
    return super.visitTypeParameter(e, integer);
  }

  private List<AnnotationDocs> scanAnnotations(Element e) {
    return e.getAnnotationMirrors().stream()
        .map(
            annotationMirror -> {
              AnnotationDocs a = new AnnotationDocs();
              a.qualifiedName = annotationMirror.getAnnotationType().toString();
              // a.name =
              // annotationMirror.getAnnotationType().asElement().getSimpleName().toString();
              a.attributes = new LinkedHashMap<>();
              annotationMirror
                  .getElementValues()
                  .forEach(
                      (k, v) -> {
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
      new DocCommentScanner(elementUtils, enclosingElement, DEFAULT_VALUE, tags)
          .visit(docCommentTree, null);
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
    // ref.name = e.getSimpleName().toString();

    return ref;
  }
}
