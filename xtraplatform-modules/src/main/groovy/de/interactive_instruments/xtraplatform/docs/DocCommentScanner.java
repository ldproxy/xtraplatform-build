package de.interactive_instruments.xtraplatform.docs;

import com.sun.source.doctree.DocCommentTree;
import com.sun.source.doctree.DocTree;
import com.sun.source.doctree.EndElementTree;
import com.sun.source.doctree.ErroneousTree;
import com.sun.source.doctree.LinkTree;
import com.sun.source.doctree.ParamTree;
import com.sun.source.doctree.ReferenceTree;
import com.sun.source.doctree.ReturnTree;
import com.sun.source.doctree.SeeTree;
import com.sun.source.doctree.StartElementTree;
import com.sun.source.doctree.TextTree;
import com.sun.source.doctree.ThrowsTree;
import com.sun.source.doctree.UnknownBlockTagTree;
import com.sun.source.doctree.UnknownInlineTagTree;
import com.sun.source.util.SimpleDocTreeVisitor;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import org.gradle.internal.impldep.org.apache.commons.lang.StringEscapeUtils;

/**
 * A visitor to gather the block tags found in a comment.
 */
class DocCommentScanner extends SimpleDocTreeVisitor<Void, Void> {

  private final Elements elementUtils;
  private final String enclosingElement;
  private final List<ElementDocs> types;
  private final List<Map<String, List<String>>> tags;
  private Map<String, List<String>> currentTags;

  DocCommentScanner(Elements elementUtils, String enclosingElement, List<ElementDocs> types, List<Map<String, List<String>>> tags) {
    this.elementUtils = elementUtils;
    this.enclosingElement = enclosingElement;
    this.types = types;
    this.tags = tags;
    this.currentTags = new LinkedHashMap<>();
    tags.add(currentTags);
  }

  @Override
  public Void visitDocComment(DocCommentTree tree, Void p) {
    tree.getFullBody().forEach(body -> addTag(ElementDocs.BODY, body.toString(), true));

    return visit(tree.getBlockTags(), null);
  }

  @Override
  public Void visitUnknownBlockTag(UnknownBlockTagTree tree,
      Void p) {
    String name = tree.getTagName();
    String content = parseContent(tree.getContent());

    addTag(name, content, false);

    return null;
  }

  private String parseContent(List<? extends DocTree> docTrees) {
    return docTrees.stream()
        .flatMap(docTree -> {
          if (docTree instanceof StartElementTree || docTree instanceof EndElementTree || docTree instanceof ErroneousTree) {
            return Stream.of(docTree.toString())
                .map(line -> line.startsWith(" ") ? line.substring(1) : line)
                .map(line -> {
                  switch (line) {
                    case ">":
                      return line + " ";
                    case "<p>":
                    case "</p>":
                    case "<code>":
                    case "</code>":
                      return "";
                    default:
                  }
                  return line;
                });
          }
          if (docTree instanceof LinkTree) {
            return Stream.of(resolveReference(((LinkTree) docTree).getReference()));
          }
          return docTree.toString()
              .lines()
              .map(line -> line.startsWith(" ") ? line.substring(1) : line)
              .map(line -> line + "\n");
        })
        .collect(Collectors.joining());
  }

  @Override
  public Void visitSee(SeeTree node, Void unused) {
    String name = node.getTagName();
    node.getReference()
        .stream()
        .filter(docTree -> docTree instanceof ReferenceTree)
        .forEach(docTree -> {
          String refName = resolveReference((ReferenceTree) docTree);
          addTag(name, refName, true);
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

  //TODO: <a href
  private String resolveReference(ReferenceTree referenceTree) {
    String refName = null;
    String ref = referenceTree.getSignature();
    TypeElement refElement = elementUtils.getTypeElement(ref);
    //TODO: types is incomplete here, maybe try to resolve see tags later when types is complete
    if (Objects.isNull(refElement)) {
      refName = types.stream()
          .filter(elementDocs -> ref.contains(".") ? Objects.equals(elementDocs.qualifiedName, ref) : Objects.equals(elementDocs.getName(), ref))
          .map(elementDocs -> elementDocs.qualifiedName)
          .findFirst()
          .or(() -> {
            if (!ref.contains(".")) {
              return Optional.ofNullable(elementUtils.getTypeElement(enclosingElement.substring(0, enclosingElement.lastIndexOf('.')) + "." + ref))
                  .map(typeElement -> typeElement.getQualifiedName().toString());
            }
            return Optional.empty();
          })
          .orElse(null);

    } else {
      refName = refElement.getQualifiedName().toString();
    }
    if (Objects.isNull(refName)) {
      types.forEach(elementDocs -> System.out.println(elementDocs.qualifiedName));
      throw new IllegalArgumentException(
          "Could not resolve '@see " + ref + "' in '" + enclosingElement  + "'. Try to use a fully qualified name.");
    }

    return refName;
  }
}
