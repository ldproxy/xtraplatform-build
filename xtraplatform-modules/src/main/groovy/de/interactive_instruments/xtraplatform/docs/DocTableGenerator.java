package de.interactive_instruments.xtraplatform.docs;

import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DocTableGenerator {

  private final DocRef docRef;
  private final Docs docs;

  public DocTableGenerator(DocRef docRef, Docs docs) {
    this.docRef = docRef;
    this.docs = docs;
  }

  Optional<String> generate(DocTable docTable, String language) {
    String rows = generateRows(docTable, language);

    if (rows.isBlank()) {
      return Optional.empty();
    }

    String table = generateHeader(docTable.getColumns(), language) + rows;

    return Optional.of(table);
  }

  String generateRows(DocTable docTable, String language) {
    return resolveRowSteps(docs, docRef, docTable.rows, language)
        .map(typeRef2 -> generateRow(docs, typeRef2, docTable.getColumns(), language))
        .collect(Collectors.joining("", "", "\n\n"));
  }

  static String generateRow(Docs docs, DocRef docRef, List<DocTable.Col> cols, String language) {
    return cols.stream()
        .map(col -> resolveColumnSteps(docs, docRef, col.value, language).orElse(""))
        .collect(Collectors.joining(" | ", "| ", " |\n"));
  }

  static String generateHeader(List<DocTable.Col> cols, String language) {
    return cols.stream()
            .map(
                col ->
                    col.header.stream()
                        .filter(i18n -> i18n.language.equals(language))
                        .map(i18n -> i18n.value)
                        .findFirst()
                        .orElse(col.header.get(0).value))
            .collect(Collectors.joining(" | ", "| ", " |\n"))
        + cols.stream().map(col -> "---").collect(Collectors.joining(" | ", "| ", " |\n"));
  }

  static Stream<DocRef> resolveRowSteps(
      Docs docs, DocRef root, List<DocStep> steps, String language) {
    Entry<Class<?>, Stream<?>> result = StepResolver.resolve(docs, root, steps, language);

    if (result.getKey() != DocRef.class) {
      throw new IllegalArgumentException();
    }

    return result.getValue().map(DocRef.class::cast);
  }

  static Optional<String> resolveColumnSteps(
      Docs docs, DocRef root, List<DocStep> steps, String language) {
    Entry<Class<?>, Stream<?>> result = StepResolver.resolve(docs, root, steps, language);

    if (result.getKey() != String.class) {
      throw new IllegalArgumentException();
    }

    return result.getValue().map(String.class::cast).findFirst();
  }
}
