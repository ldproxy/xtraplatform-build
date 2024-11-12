package de.interactive_instruments.xtraplatform.docs;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import de.interactive_instruments.xtraplatform.docs.DocStep.Step;
import java.lang.reflect.Type;
import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ReflectionConfigGenerator {

  static final Type LIST_MAP = new TypeToken<List<Map<String, Object>>>() {}.getType();

  private final Docs docs;
  private final Gson gson;
  private final Map<String, Map<String, Object>> nestedDefs;
  private final Set<String> inProgressDefs;
  private final Map<String, String> keys;

  public ReflectionConfigGenerator(Docs docs, Gson gson) {
    this.docs = docs;
    this.gson = gson;
    this.nestedDefs = new LinkedHashMap<>();
    this.inProgressDefs = new HashSet<>();
    this.keys = new LinkedHashMap<>();
  }

  List<Map<String, Object>> generate(List<DocRef> types, List<String> extraTypes) {
    System.out.println("TYPES: " + types);

    List<Map<String, Object>> schema = new ArrayList<>();
    nestedDefs.clear();
    inProgressDefs.clear();
    keys.clear();

    extraTypes.forEach(this::resolveType);

    if (types.size() >= 1) {
      schema.addAll(generateDefs(types));
    }

    schema.addAll(nestedDefs.values());

    return schema.stream().filter(map -> !map.isEmpty()).collect(Collectors.toList());
  }

  List<Map<String, Object>> generateDefs(List<DocRef> types) {
    List<Map<String, Object>> defs = new ArrayList<>();

    types.stream().forEach(type -> defs.add(generateObject(type)));

    return defs;
  }

  Map<String, Object> generateObject(DocRef type) {
    return generateObject(type, List.of());
  }

  Map<String, Object> generateObject(DocRef type, List<Map<String, Object>> extraProps) {
    Map<String, Object> object = new LinkedHashMap<>();
    List<Map<String, Object>> properties = new ArrayList<>(extraProps);

    resolveRowSteps(
            docs, type, List.of(new DocStep(Step.JSON_PROPERTIES, List.of("skipDocIgnore"))))
        .filter(DocRef::isMethod)
        .forEach(
            prop -> {
              Map<String, Object> property =
                  generateProp(docs, prop, prop.getMethod().get(), type.getType().qualifiedName);

              properties.add(property);
            });

    if (properties.isEmpty()) {
      properties.add(Map.of("name", "<init>", "parameterTypes", List.of()));
    }

    object.put("name", restoreInnerClassName(type.getType().qualifiedName));
    object.put("allDeclaredFields", true);
    object.put("queryAllDeclaredMethods", true);
    object.put("queryAllDeclaredConstructors", true);
    object.put("methods", properties);

    resolveInterfaces(type);

    resolveJsonSubTypes(type);

    resolveImmutables(type);

    return object;
  }

  Map<String, Object> generateProp(Docs docs, DocRef prop, MethodDocs method, String typeQN) {
    String name = method.getName();

    List<String> def =
        Objects.nonNull(method.parameters)
            ? method.parameters.stream().map(p -> p.type).collect(Collectors.toList())
            : List.of();

    List<String> types =
        Stream.concat(
                def.stream(),
                Objects.nonNull(method.returnType) ? Stream.of(method.returnType) : Stream.empty())
            .flatMap(t -> resolveTypes(t).stream())
            .collect(Collectors.toList());

    for (String type : types) {
      resolveType(type);
    }

    if (method.hasAnnotation("com.fasterxml.jackson.databind.annotation.JsonSerialize")) {
      Optional<String> converter =
          method
              .getAnnotation("com.fasterxml.jackson.databind.annotation.JsonSerialize")
              .flatMap(a -> a.getAttribute("converter"));

      if (converter.isPresent()) {
        resolveType(converter.get());
      }
    }

    List<String> cleanDef =
        def.stream()
            .map(d -> d.contains("<") ? d.substring(0, d.indexOf("<")) : d)
            .map(ReflectionConfigGenerator::restoreInnerClassName)
            .collect(Collectors.toList());

    return Map.of("name", name, "parameterTypes", cleanDef);
  }

  private void resolveType(String type) {
    if (!nestedDefs.containsKey(type)) {
      if (!type.contains(".")) {
        nestedDefs.put(type, Map.of("name", type, "queryAllDeclaredMethods", true));
      } else if (!type.startsWith("de.ii")) {
        String name = type.startsWith("java.") ? type : "shadow." + type;
        nestedDefs.put(
            type,
            Map.of(
                "name",
                name,
                "queryAllDeclaredMethods",
                true,
                "queryAllDeclaredConstructors",
                true,
                "methods",
                List.of(Map.of("name", "<init>", "parameterTypes", List.of()))));
      } else {
        DocRef typeRef = docs.findTypeRef(type);
        nestedDefs.put(type, Map.of());
        nestedDefs.put(type, generateObject(typeRef));
      }
    }
  }

  private void resolveInterfaces(DocRef typeRef) {
    typeRef.getType().interfaces.forEach(it -> resolveType(it.qualifiedName));
  }

  private void resolveJsonSubTypes(DocRef typeRef) {
    if (typeRef
        .getType()
        .getAnnotation("com.fasterxml.jackson.annotation.JsonTypeInfo")
        .isPresent()) {
      List<DocRef> impls =
          docs.findTypeByInterface(typeRef.getType().qualifiedName).stream()
              .filter(it -> !it.getType().qualifiedName.contains(".Immutable"))
              .collect(Collectors.toList());

      for (DocRef impl : impls) {
        nestedDefs.put(impl.getType().qualifiedName, Map.of());
        nestedDefs.put(impl.getType().qualifiedName, generateObject(impl));

        resolveImmutables(impl);

        /*Optional<String> builderClass =
            impl.getType()
                .getAnnotation("com.fasterxml.jackson.databind.annotation.JsonDeserialize")
                .flatMap(a -> a.getAttribute("builder"));
        if (builderClass.isPresent()) {
          DocRef builder = docs.findTypeRef(builderClass.get());

          nestedDefs.put(builder.getType().qualifiedName, Map.of());
          nestedDefs.put(builder.getType().qualifiedName, generateObject(builder));

          DocRef builder2 = docs.findTypeRef(impl.getType().qualifiedName + ".Builder");
          if (Objects.nonNull(builder2)) {
            nestedDefs.put(builder2.getType().qualifiedName, Map.of());
            nestedDefs.put(builder2.getType().qualifiedName, generateObject(builder2));
          }
        }*/
      }
    }
  }

  /* TODO
  { "name": "<init>", "parameterTypes": [] },
      { "name": "build", "parameterTypes": [] },
   */
  private void resolveImmutables(DocRef typeRef) {
    if (typeRef.getType().getAnnotation("org.immutables.value.Value.Immutable").isPresent()) {
      List<DocRef> impls =
          docs.findTypeByInterface(typeRef.getType().qualifiedName).stream()
              .filter(it -> it.getType().qualifiedName.contains(".Immutable"))
              .collect(Collectors.toList());

      if (impls.isEmpty()) {
        impls =
            docs.findTypeBySuperClass(typeRef.getType().qualifiedName).stream()
                .filter(it -> it.getType().qualifiedName.contains(".Immutable"))
                .collect(Collectors.toList());
      }

      for (DocRef impl : impls) {
        nestedDefs.put(impl.getType().qualifiedName, Map.of());
        nestedDefs.put(impl.getType().qualifiedName, generateObject(impl));
      }

      Optional<String> builderClass =
          typeRef
              .getType()
              .getAnnotation("com.fasterxml.jackson.databind.annotation.JsonDeserialize")
              .flatMap(a -> a.getAttribute("builder"));

      if (builderClass.isPresent()) {
        DocRef builder = docs.findTypeRef(builderClass.get());

        nestedDefs.put(builder.getType().qualifiedName, Map.of());
        nestedDefs.put(
            builder.getType().qualifiedName,
            generateObject(
                builder,
                List.of(
                    Map.of("name", "<init>", "parameterTypes", List.of()),
                    Map.of("name", "build", "parameterTypes", List.of()))));

        try {
          DocRef builder2 = docs.findTypeRef(typeRef.getType().qualifiedName + ".Builder");

          nestedDefs.put(builder2.getType().qualifiedName, Map.of());
          nestedDefs.put(builder2.getType().qualifiedName, generateObject(builder2));
        } catch (Throwable e) {
        }
      }
    }
  }

  private static List<String> resolveTypes(String type) {
    List<String> types = new ArrayList<>();

    if (type.contains("<")) {
      types.add(type.substring(0, type.indexOf("<")));
      int start = type.indexOf("<") + 1;
      int end = type.contains(">") ? type.lastIndexOf(">") : type.length();

      for (String g : type.substring(start, end).split(",")) {
        String t = g.replace("? extends ", "").trim();
        if (t.contains("<")) {
          types.addAll(resolveTypes(t));
        } else if (t.contains(">")) {
          types.add(t.substring(0, t.lastIndexOf(">")));
        } else {
          types.add(t);
        }
      }
    } else {
      types.add(type);
    }
    return types;
  }

  static String restoreInnerClassName(String type) {
    if (!type.contains(".")) {
      return type;
    }

    String pkg = type.substring(0, type.lastIndexOf("."));
    String name = type.substring(type.lastIndexOf(".") + 1);
    String parentName = pkg.substring(pkg.lastIndexOf(".") + 1);

    boolean isInnerClass = Character.isUpperCase(parentName.charAt(0));
    String separator = isInnerClass ? "$" : ".";

    return pkg + separator + name;
  }

  static Stream<DocRef> resolveRowSteps(Docs docs, DocRef root, List<DocStep> steps) {
    Entry<Class<?>, Stream<?>> result = StepResolver.resolve(docs, root, steps, "en");

    if (result.getKey() != DocRef.class) {
      throw new IllegalArgumentException();
    }

    return result.getValue().map(DocRef.class::cast);
  }
}
