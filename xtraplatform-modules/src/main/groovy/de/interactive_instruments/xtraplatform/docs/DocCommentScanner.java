package de.interactive_instruments.xtraplatform.docs;

import com.sun.source.doctree.DocCommentTree;
import com.sun.source.doctree.ParamTree;
import com.sun.source.doctree.ReferenceTree;
import com.sun.source.doctree.ReturnTree;
import com.sun.source.doctree.SeeTree;
import com.sun.source.doctree.ThrowsTree;
import com.sun.source.doctree.UnknownBlockTagTree;
import com.sun.source.util.SimpleDocTreeVisitor;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;

/**
 * A visitor to gather the block tags found in a comment.
 */
class DocCommentScanner extends SimpleDocTreeVisitor<Void, Void> {

  private final Elements elementUtils;
  private final String enclosingPackage;
  private final List<Map<String, List<String>>> tags;
  private Map<String, List<String>> currentTags;

  DocCommentScanner(Elements elementUtils, String enclosingPackage, List<Map<String, List<String>>> tags) {
    this.elementUtils = elementUtils;
    this.enclosingPackage = enclosingPackage;
    this.tags = tags;
    this.currentTags = new LinkedHashMap<>();
    tags.add(currentTags);
  }

  @Override
  public Void visitDocComment(DocCommentTree tree, Void p) {
    tree.getFullBody().forEach(body -> addTag("_BODY_", body.toString(), true));

    return visit(tree.getBlockTags(), null);
  }

  @Override
  public Void visitUnknownBlockTag(UnknownBlockTagTree tree,
      Void p) {
    String name = tree.getTagName();
    String content = tree.getContent().toString();
    addTag(name, content, false);
    return null;
  }

  @Override
  public Void visitSee(SeeTree node, Void unused) {
    String name = node.getTagName();
    node.getReference()
        .stream()
        .filter(docTree -> docTree instanceof ReferenceTree) //TODO: <a href
        .forEach(docTree -> {
          String ref = ((ReferenceTree) docTree).getSignature();
          if (!ref.contains(".")) {
            ref = enclosingPackage + "." + ref;
          }
          TypeElement refElement = elementUtils.getTypeElement(ref);
          if (Objects.isNull(refElement)) {
            throw new IllegalArgumentException(
                "Could not resolve '@see " + ref + "'. Try to use a fully qualified name.");
          }
          addTag(name, refElement.getQualifiedName().toString(), true);
        });
    return null;
  }

  @Override
  public Void visitParam(ParamTree node, Void unused) {
    String name = node.getTagName();
    String content = node.getName().toString() + ": " + node.getDescription()
        .stream()
        .map(Object::toString)
        .collect(Collectors.joining("\n\n"));
    addTag(name, content, true);
    return super.visitParam(node, unused);
  }

  @Override
  public Void visitReturn(ReturnTree node, Void unused) {
    String name = node.getTagName();
    String content = node.getDescription()
        .stream()
        .map(Object::toString)
        .collect(Collectors.joining("\n\n"));
    addTag(name, content, true);
    return super.visitReturn(node, unused);
  }

  @Override
  public Void visitThrows(ThrowsTree node, Void unused) {
    String name = node.getTagName();
    String content = node.getExceptionName().getSignature() + ": " + node.getDescription()
        .stream()
        .map(Object::toString)
        .collect(Collectors.joining("\n\n"));
    addTag(name, content, true);
    return super.visitThrows(node, unused);
  }

  private void addTag(String name, String content, boolean append) {
    if (!append && currentTags.containsKey(name)) {
      this.currentTags = new LinkedHashMap<>();
      tags.add(currentTags);
    }
    currentTags.computeIfAbsent(name,
        n -> new ArrayList<>()).add(content);
  }
}
