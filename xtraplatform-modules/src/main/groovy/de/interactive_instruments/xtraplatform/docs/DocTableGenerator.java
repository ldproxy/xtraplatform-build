package de.interactive_instruments.xtraplatform.docs;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.AbstractMap.SimpleEntry;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class DocTableGenerator {

  private static final Gson GSON = new Gson();
  private static final Type LIST_COL_TYPE = new TypeToken<List<Col>>() {
  }.getType();

  enum From {
    TAG
  }

  enum ForEach {
    IMPLEMENTATION, PROPERTY
  }

  static class I18n {

    String language;
    String value;

    @Override
    public String toString() {
      return "I18n{" +
          "language='" + language + '\'' +
          ", value='" + value + '\'' +
          '}';
    }
  }

  static class Col {

    From value;
    String valueRef;
    String valueDefault = "";
    List<I18n> header;

    @Override
    public String toString() {
      return "Col{" +
          "value=" + value +
          ", valueRef='" + valueRef + '\'' +
          ", valueDefault='" + valueDefault + '\'' +
          ", header=" + header +
          '}';
    }
  }

  static class Table {

    Table(String name, ForEach rows, List<Col> cols) {
      this.name = name;
      this.rows = rows;
      this.cols = cols;
    }

    String name;
    ForEach rows;
    List<Col> cols;

    @Override
    public String toString() {
      return "Table{" +
          "name='" + name + '\'' +
          ", rows=" + rows +
          ", cols=" + cols +
          '}';
    }
  }

  static Table parse(AnnotationDocs def) {
    String name = def.getAttribute("name")
        .orElseThrow();
    String rows = def.getAttribute("rows")
        .orElseThrow();
    String cols = def.getAttributeAsJson("columns")
        .orElseThrow();
    List<Col> colDef = GSON.fromJson(cols, LIST_COL_TYPE);

    return new Table(name, ForEach.valueOf(rows), colDef);
  }

  static Map.Entry<String, String> generate(AnnotationDocs def, ElementDocs elementDocs,
      TypeFinder typeFinder, Map<String, String> vars) {
    Table table = parse(def);
    String md = "TODO";

    if (table.rows == ForEach.IMPLEMENTATION) {
      md = generateHeader(table.cols, "en") + generateImplementationRows(elementDocs.qualifiedName,
          table.cols, typeFinder, vars);
    }

    return new SimpleEntry<>("docTable:" + table.name, md);
  }

  static String generateImplementationRows(String intrfc, List<Col> cols, TypeFinder typeFinder,
      Map<String, String> vars) {
    return typeFinder.findByInterface(intrfc)
        .stream()
        .map(typeDocs -> generateImplementationRow(typeDocs, cols, typeFinder, vars))
        .collect(Collectors.joining("", "", "\n\n"));
  }

  static String generateImplementationRow(TypeDocs typeDocs, List<Col> cols,
      TypeFinder typeFinder, Map<String, String> vars) {
    return cols.stream()
        .map(col -> {
          switch (col.value) {
            case TAG:
            default:
              return resolveValueTag(typeDocs, col, vars);
          }
        })
        .collect(Collectors.joining(" | ", "| ", " |\n"));
  }

  static String generateHeader(List<Col> cols, String language) {
    return cols.stream()
        .map(col -> col.header
            .stream()
            .filter(i18n -> i18n.language.equals(language))
            .map(i18n -> i18n.value)
            .findFirst()
            .orElse(col.header.get(0).value))
        .collect(Collectors.joining(" | ", "| ", " |\n"))
        + cols.stream()
        .map(col -> "---")
        .collect(Collectors.joining(" | ", "| ", " |\n"));
  }

  //TODO i18n
  static String resolveValueTag(TypeDocs typeDocs, Col col, Map<String, String> vars) {
    TagReplacer tagReplacer = new TagReplacer(typeDocs, vars, "en");
    return tagReplacer.replaceStrings(col.valueRef);
    /*return typeDocs.getDocTag(col.valueRef)
        .findFirst()
        .orElse(col.valueDefault);*/
  }
}
