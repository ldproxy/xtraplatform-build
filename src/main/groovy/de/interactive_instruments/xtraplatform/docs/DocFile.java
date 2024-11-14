package de.interactive_instruments.xtraplatform.docs;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.List;

public class DocFile {
  static final String ANNOTATION = "de.ii.xtraplatform.docs.DocFile";
  static final String ANNOTATION_DEFS = "de.ii.xtraplatform.docs.DocDefs";

  private static final Gson GSON = new Gson();
  private static final Type LIST_TABLE = new TypeToken<List<DocTable>>() {
  }.getType();
  private static final Type LIST_VAR = new TypeToken<List<DocVar>>() {
  }.getType();
  final DocRef docRef;
  final String path;
  final String name;

  final List<DocTable> tables;

  final List<DocVar> vars;

  private DocFile(DocRef docRef, String path, String name, List<DocTable> tables, List<DocVar> vars) {
    this.docRef = docRef;
    this.path = path;
    this.name = name;
    this.tables = tables;
    this.vars = vars;
  }

  static DocFile from(DocRef docRef, AnnotationDocs docFileDef) {
    String path = docFileDef.getAttribute("path")
        .orElse("");
    String name = docFileDef.getAttribute("name")
        .orElse(docRef.getType().qualifiedName.replaceAll("\\.", "/") + ".md");
    List<DocTable> tables = docFileDef.getAttributeAsJson("tables")
        .map(t -> (List<DocTable>) GSON.fromJson(t, LIST_TABLE))
        .orElse(List.of());
    List<DocVar> vars = docFileDef.getAttributeAsJson("vars")
        .map(t -> (List<DocVar>) GSON.fromJson(t, LIST_VAR))
        .orElse(List.of());
    return new DocFile(docRef, path, name, tables, vars);
  }
}
