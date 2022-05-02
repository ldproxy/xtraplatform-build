package de.interactive_instruments.xtraplatform.docs;

import com.google.common.base.CaseFormat;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Optional;

class DocFilesTemplate {

  static final String ANNOTATION = "de.ii.xtraplatform.docs.DocFilesTemplate";

  private static final Gson GSON = new Gson();
  private static final Type LIST_I18N = new TypeToken<List<DocI18n>>() {
  }.getType();
  private static final Type LIST_TABLE = new TypeToken<List<DocTable>>() {
  }.getType();
  private static final Type LIST_VAR = new TypeToken<List<DocVar>>() {
  }.getType();

  final DocRef docRef;
  final String path;
  final Optional<String> stripPrefix;
  final Optional<String> stripSuffix;
  final CaseFormat caseFormat;
  final List<DocI18n> template;
  final List<DocTable> tables;

  final List<DocVar> vars;

  private DocFilesTemplate(DocRef docRef, String path, Optional<String> stripPrefix,
      Optional<String> stripSuffix, CaseFormat caseFormat, List<DocI18n> template,
      List<DocTable> tables, List<DocVar> vars) {
    this.docRef = docRef;
    this.path = path;
    this.stripPrefix = stripPrefix;
    this.stripSuffix = stripSuffix;
    this.caseFormat = caseFormat;
    this.template = template;
    this.tables = tables;
    this.vars = vars;
  }

  static DocFilesTemplate from(DocRef docRef, AnnotationDocs docFilesTemplateDef) {
    TypeDocs type = docRef.getType();

    String path = docFilesTemplateDef.getAttribute("path")
        .orElse(type.qualifiedName.substring(0, type.qualifiedName.lastIndexOf('.'))
            .replaceAll("\\.", "/"));
    Optional<String> stripPrefix = docFilesTemplateDef.getAttribute("stripPrefix");
    Optional<String> stripSuffix = docFilesTemplateDef.getAttribute("stripSuffix");
    CaseFormat caseFormat = docFilesTemplateDef.getAttribute("caseFormat")
        .map(CaseFormat::valueOf)
        .orElse(CaseFormat.LOWER_UNDERSCORE);
    List<DocI18n> template = docFilesTemplateDef.getAttributeAsJson("template")
        .map(t -> (List<DocI18n>) GSON.fromJson(t, LIST_I18N))
        .orElse(List.of());
    List<DocTable> tables = docFilesTemplateDef.getAttributeAsJson("tables")
        .map(t -> (List<DocTable>) GSON.fromJson(t, LIST_TABLE))
        .orElse(List.of());
    List<DocVar> vars = docFilesTemplateDef.getAttributeAsJson("vars")
        .map(t -> (List<DocVar>) GSON.fromJson(t, LIST_VAR))
        .orElse(List.of());

    return new DocFilesTemplate(docRef, path, stripPrefix, stripSuffix, caseFormat, template,
        tables, vars);
  }

  String getName(DocRef docRef) {
    String name = docRef.getType().getName();

    if (stripPrefix.isPresent() && name.startsWith(stripPrefix.get())) {
      name = name.substring(stripPrefix.get().length());
    }
    if (stripSuffix.isPresent() && name.endsWith(stripSuffix.get())) {
      name = name.substring(0, name.length() - stripSuffix.get().length());
    }

    name = CaseFormat.UPPER_CAMEL.to(caseFormat, name) + ".md";

    return name;
  }

  String getTypeName() {
    return docRef.getType().qualifiedName;
  }
}
