package de.interactive_instruments.xtraplatform.docs;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import de.interactive_instruments.xtraplatform.docs.DocStep.Step;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Objects;

class DocTable {

  private static final Gson GSON = new Gson();
  private static final Type LIST_COL_TYPE = new TypeToken<List<Col>>() {
  }.getType();
  private static final Type LIST_STEP_TYPE = new TypeToken<List<DocStep>>() {
  }.getType();

  enum ColumnSet {NONE, JSON_PROPERTIES}

  static class Col {

    List<DocStep> value;
    String valueDefault = "";
    List<DocI18n> header;

    public Col(List<DocStep> value, List<DocI18n> header) {
      this.value = value;
      this.header = header;
    }
  }

  final String name;
  final List<DocStep> rows;
  final List<Col> columns;
  final ColumnSet columnSet;

  private DocTable(String name, List<DocStep> rows, List<Col> columns, ColumnSet columnSet) {
    this.name = name;
    this.rows = rows;
    this.columns = columns;
    this.columnSet = columnSet;
  }

  public List<Col> getColumns() {
    if (Objects.isNull(columnSet)) {
      return columns;
    }

    switch (columnSet) {
      case JSON_PROPERTIES:
        return JSON_PROPERTIES;
      case NONE:
      default:
        return columns;
    }
  }

  static DocTable from(AnnotationDocs def) {
    String name = def.getAttribute("name")
        .orElseThrow();
    String rows = def.getAttributeAsJson("rows")
        .orElseThrow();
    List<DocStep> rowDef = GSON.fromJson(rows, LIST_STEP_TYPE);
    String cols = def.getAttributeAsJson("columns")
        .orElseThrow();
    List<DocTable.Col> colDef = GSON.fromJson(cols, LIST_COL_TYPE);
    String columnSet = def.getAttribute("columnSet")
        .orElseThrow();

    return new DocTable(name, rowDef, colDef, ColumnSet.valueOf(columnSet));
  }

  static List<Col> JSON_PROPERTIES = List.of(
      new Col(List.of(
          new DocStep(Step.JSON_NAME),
          new DocStep(Step.FORMAT, List.of("`%s`"))
      ), List.of(
          new DocI18n("en", "Name"),
          new DocI18n("de", "Name")
      )),
      new Col(List.of(
          new DocStep(Step.JSON_TYPE)
      ), List.of(
          new DocI18n("en", "Type"),
          new DocI18n("de", "Typ")
      )),
      new Col(List.of(
          new DocStep(Step.TAG, List.of("{@default |||}")),
          new DocStep(Step.FORMAT, List.of("`%s`"))
      ), List.of(
          new DocI18n("en", "Default"),
          new DocI18n("de", "Default")
      )),
      new Col(List.of(
          new DocStep(Step.TAG, List.of("{@since |||v2.0}"))
      ), List.of(
          new DocI18n("en", "Since"),
          new DocI18n("de", "Seit")
      )),
      new Col(List.of(
          new DocStep(Step.TAG, List.of("{@body}"))
      ), List.of(
          new DocI18n("en", "Description"),
          new DocI18n("de", "Beschreibung")
      ))
  );
}
