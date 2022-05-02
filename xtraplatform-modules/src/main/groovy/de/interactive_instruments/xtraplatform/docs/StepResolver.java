package de.interactive_instruments.xtraplatform.docs;

import com.google.common.base.Splitter;
import java.util.AbstractMap.SimpleEntry;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class StepResolver {

  static final String JSON_PROPERTY = "com.fasterxml.jackson.annotation.JsonProperty";
  static final String JSON_IGNORE = "com.fasterxml.jackson.annotation.JsonIgnore";
  static final String JSON_ALIAS = "com.fasterxml.jackson.annotation.JsonAlias";
  static final String DOC_IGNORE = "de.ii.xtraplatform.docs.DocIgnore";
  static final String DOC_MARKER = "de.ii.xtraplatform.docs.DocMarker";

  private static final Splitter REF_SPLITTER = Splitter.on(Pattern.compile("[, ]"))
      .omitEmptyStrings()
      .trimResults();
  private static final Splitter PARAM_SPLITTER = Splitter.on(":")
      .omitEmptyStrings()
      .trimResults();
  private static final Splitter OPERANDS_SPLITTER = Splitter.on(",")
      .omitEmptyStrings()
      .trimResults();

  static Map.Entry<Class<?>, Stream<?>> resolve(Docs docs, DocRef root, List<DocStep> steps,
      String language) {
    Stream<?> result = Stream.of(root);
    Class<?> currentOut = DocRef.class;

    for (DocStep step : steps) {
      if (step.type.in != currentOut) {
        throw new IllegalArgumentException();
      }
      if (step.type.in == DocRef.class && !step.type.multipleIn && step.type.multipleOut) {
        result = result.flatMap(
            typeRef -> flatMapType(docs, (DocRef) typeRef, step, step.type.out));
        currentOut = step.type.out;
      } else if (step.type.in == DocRef.class && !step.type.multipleIn && !step.type.multipleOut) {
        result = result.map(typeRef -> mapType((DocRef) typeRef, step, language, step.type.out));
        currentOut = step.type.out;
      } else if (step.type.in == DocRef.class && step.type.out == DocRef.class
          && step.type.multipleIn && step.type.multipleOut) {
        result = transformTypes(result.map(DocRef.class::cast), step, language);
        currentOut = step.type.out;
      } else if (step.type.in == String.class && !step.type.multipleIn && !step.type.multipleOut) {
        result = result.map(string -> mapString((String) string, step, language, step.type.out));
        currentOut = step.type.out;
      } else if (step.type.in == String.class && step.type.out == String.class
          && step.type.multipleIn && !step.type.multipleOut) {
        result = collectString(result.map(String.class::cast), step, language);
        currentOut = step.type.out;
      } else {
        throw new IllegalArgumentException();
      }
    }

    return new SimpleEntry<>(currentOut, result);
  }

  private static <T> Stream<T> flatMapType(Docs docs, DocRef in, DocStep step, Class<T> out) {
    switch (step.type) {
      case IMPLEMENTATIONS:
        return Stream.of(in)
            .flatMap(typeRef -> {
              Stream<DocRef> children = docs.findTypeByInterface(typeRef.getType().qualifiedName).stream();

              if (typeRef.hasAnnotation(DocFilesTemplate.ANNOTATION)) {
                DocFilesTemplate docFilesTemplate = DocFilesTemplate.from(typeRef, typeRef.getType()
                    .getAnnotation(DocFilesTemplate.ANNOTATION)
                    .get());
                return children
                    .map(childRef -> childRef
                        .addVar("docFile:path", docFilesTemplate.path)
                        .addVar("docFile:name", docFilesTemplate.getName(childRef)));
              }
              return children;
            })
            .map(out::cast);
      case TAG_REFS:
        return Stream.of(in)
            .flatMap(typeRef -> typeRef.getDocTag(fromTag(step.params.get(0)))
                .flatMap(REF_SPLITTER::splitToStream)
                .map(docs::findTypeRef))
            .map(out::cast);
      case METHODS:
        return Stream.of(in)
            .flatMap(DocRef::getMethods)
            .map(out::cast);
      case JSON_PROPERTIES:
        return Stream.of(in)
            .flatMap(DocRef::getMethods)
            .filter(typeRef -> typeRef.getMethod()
                .filter(methodDocs -> Objects.nonNull(methodDocs.doc)
                    && methodDocs.hasAnnotation(JSON_PROPERTY)
                    && !(methodDocs.hasAnnotation(JSON_IGNORE)
                    || methodDocs.hasAnnotation(DOC_IGNORE)))
                .isPresent())
            .flatMap(typeRef -> {
              if (typeRef.getMethod().get().hasAnnotation(JSON_ALIAS)) {
                Stream<String> aliases = OPERANDS_SPLITTER.splitToStream(typeRef.getMethod()
                    .get()
                    .getAnnotation(JSON_ALIAS)
                    .flatMap(annotationDocs -> annotationDocs.getAttribute("value"))
                    .get()
                    .replaceAll("\"", ""));
                String property = typeRef.getMethod()
                    .get()
                    .getAnnotation(JSON_PROPERTY)
                    .flatMap(annotationDocs -> annotationDocs.getAttribute("value"))
                    .get();
                Map<String, String> aliasDescription = Map.of(
                    "en", String.format("*Deprecated* See `%s`.", property),
                    "de", String.format("*Deprecated* Siehe `%s`.", property)
                );
                return Stream.concat(
                    Stream.of(typeRef),
                    aliases.map(alias -> new DocRef(typeRef, alias, aliasDescription))
                );
              }
              return Stream.of(typeRef);
            })
            .map(out::cast);
      case ANNOTATIONS:
        return Stream.of(in)
            .flatMap(DocRef::getAnnotations)
            .map(ElementDocs::getName)
            .map(out::cast);
      case MARKED:
        return Stream.of(in)
            .filter(docRef -> docRef.hasAnnotation(DOC_MARKER, Map.of("value", step.params.get(0))))
            .map(out::cast);
      case UNMARKED:
        return Stream.of(in)
            .filter(docRef -> !docRef.hasAnnotation(DOC_MARKER))
            .map(out::cast);
      default:
        throw new IllegalArgumentException();
    }
  }

  private static <T> T mapType(DocRef docRef, DocStep step, String language, Class<T> out) {
    switch (step.type) {
      case TAG:
        TagReplacer tagReplacer = new TagReplacer(docRef, language, Map.of());
        return out.cast(tagReplacer.replaceStrings(step.params.get(0)));
      case CONSTANT:
        return out.cast(step.params.get(0));
      case JSON_NAME:
        if (docRef.isMethod()) {
          if (docRef.hasAlias()) {
            return out.cast(docRef.getAlias());
          }
          return out.cast(docRef.getMethod()
              .get()
              .getAnnotation(JSON_PROPERTY)
              .flatMap(annotationDocs -> annotationDocs.getAttribute("value"))
              .orElse(""));
        }
      case JSON_TYPE:
        if (docRef.isMethod()) {
          return out.cast(getJsonType(docRef.getMethod()
              .get()
              .returnType));
        }
      default:
        throw new IllegalArgumentException();
    }
  }

  private static Stream<DocRef> transformTypes(Stream<DocRef> in, DocStep step, String language) {
    switch (step.type) {
      case SORTED:
        return in.sorted(Comparator.comparing(docRef -> docRef
            .getDocTag(fromTag(step.params.get(0)))
            .findFirst()
            .orElse(null),
            Comparator.nullsLast(Comparator.naturalOrder())));
      default:
        throw new IllegalArgumentException();
    }
  }

  private static <T> T mapString(String in, DocStep step, String language, Class<T> out) {
    switch (step.type) {
      case FILTER:
        for (String param : step.params) {
          List<String> op_par = PARAM_SPLITTER.splitToList(param);
          if (Objects.equals(op_par.get(0), "ISIN")) {
            boolean matches = OPERANDS_SPLITTER.splitToStream(op_par.get(1))
                .anyMatch(operand -> Objects.equals(in, operand));
            if (matches) {
              return out.cast(in);
            }
          }
        }
        return out.cast("");
      case FORMAT:
        if (in.isBlank()) {
          return out.cast(in);
        }
        return out.cast(String.format(step.params.get(0), in));
      default:
        throw new IllegalArgumentException();
    }
  }

  private static Stream<String> collectString(Stream<String> in, DocStep step, String language) {
    switch (step.type) {
      case COLLECT:
        Stream<String> result = in.filter(s -> !s.isBlank());
        if (step.params.contains("DISTINCT")) {
          result = result.distinct();
        }
        if (step.params.contains("SORTED")) {
          result = result.sorted();
        }
        String separator = step.params
            .stream()
            .filter(p -> p.startsWith("SEPARATED:"))
            .map(p -> p.replace("SEPARATED:", ""))
            .findFirst()
            .orElse("");
        return Stream.of(result.collect(Collectors.joining(separator)));
      default:
        throw new IllegalArgumentException();
    }
  }

  private static String getJsonType(String type) {
    if (type.startsWith("java.util.List")
        || type.startsWith("java.util.Set")
        || type.startsWith("java.util.Collection")
        || type.endsWith("[]")) {
      return "array";
    }
    if (hasSimpleType(type, "string")) {
      return "string";
    }
    if (hasSimpleType(type, "boolean")) {
      return "boolean";
    }
    if (hasSimpleType(type, "int", "integer", "long", "double", "float")) {
      return "number";
    }

    return "object";
  }

  private static boolean hasSimpleType(String type, String... matches) {
    for (String match : matches) {
      if (type.equalsIgnoreCase(match)
          || type.equalsIgnoreCase(String.format("java.lang.%s", match))
          || type.equalsIgnoreCase(String.format("java.util.Optional<java.lang.%s>", match))) {
        return true;
      }
    }
    return false;
  }

  private static String fromTag(String name) {
    if (name.startsWith("{@")) {
      return name.substring(2, name.length() - 1);
    }
    return name;
  }

}
