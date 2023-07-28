package de.interactive_instruments.xtraplatform.docs;

import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import de.interactive_instruments.xtraplatform.docs.DocStep.Step;
import java.lang.reflect.Type;
import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class JsonSchemaGenerator {

  static final Type LIST_MAP = new TypeToken<List<Map<String, Object>>>() {}.getType();

  private final Docs docs;
  private final Gson gson;
  private final Map<String, Object> nestedDefs;
  private final Set<String> inProgressDefs;
  private final Map<String, String> keys;

  public JsonSchemaGenerator(Docs docs, Gson gson) {
    this.docs = docs;
    this.gson = gson;
    this.nestedDefs = new LinkedHashMap<>();
    this.inProgressDefs = new HashSet<>();
    this.keys = new LinkedHashMap<>();
  }

  Map<String, Object> generate(
      List<DocRef> types, Map<String, List<Map<String, String>>> discriminators) {
    Map<String, Object> schema = new LinkedHashMap<>();
    Map<String, Object> defs = new LinkedHashMap<>();
    nestedDefs.clear();
    inProgressDefs.clear();
    keys.clear();

    schema.put("$schema", "https://json-schema.org/draft/2020-12/schema");

    if (types.size() >= 1) {
      defs.putAll(generateDefs(types));

      if (!discriminators.isEmpty()) {
        schema.putAll(generateSwitch(types, discriminators));
      } else if (defs.size() == 1) {
        schema.putAll(toRef(defs.keySet().iterator().next()));
      } else {
        schema.put("oneOf", toOneOf(defs.keySet()));
      }
    }

    defs.putAll(nestedDefs);
    schema.put("$defs", defs);

    return schema;
  }

  Map<String, Object> generateDefs(List<DocRef> types) {
    Map<String, Object> defs = new LinkedHashMap<>();

    types.stream()
        .map(type -> Map.entry(toKey(type), generateObject(type)))
        .forEach(entry -> defs.put(entry.getKey(), entry.getValue()));

    return defs;
  }

  Map<String, Object> generateSwitch(
      List<DocRef> types, Map<String, List<Map<String, String>>> discriminators) {
    Map<String, Object> sw = new LinkedHashMap<>();
    Map<String, Map<String, Set<String>>> props = new LinkedHashMap<>();
    Set<String> required = new HashSet<>();
    List<Map<String, Object>> anyOf = new ArrayList<>();
    Map<String, Map<String, Set<String>>> anyOfProps = new LinkedHashMap<>();
    List<Map<String, Object>> allOf = new ArrayList<>();

    sw.put("type", "object");
    sw.put("properties", props);
    sw.put("required", required);
    if (discriminators.values().stream()
        .flatMap(discSets -> discSets.stream().flatMap(discSet -> discSet.keySet().stream()))
        .anyMatch(key -> key.contains("|"))) {
      sw.put("anyOf", anyOf);
    }
    sw.put("allOf", allOf);

    types.forEach(
        type -> {
          discriminators
              .getOrDefault(type.getType().qualifiedName, List.of())
              .forEach(
                  discSet -> {
                    Map<String, Object> ifProps = new LinkedHashMap<>();

                    discSet.forEach(
                        (key, val) -> {
                          if (key.contains("|")) {
                            anyOfProps
                                .computeIfAbsent(
                                    key.substring(0, key.indexOf("|")),
                                    (k) -> ImmutableMap.of("enum", new LinkedHashSet<>()))
                                .get("enum")
                                .add(val);
                            ifProps.put(
                                key.substring(0, key.indexOf("|")), ImmutableMap.of("const", val));
                          } else {
                            props
                                .computeIfAbsent(
                                    key, (k) -> ImmutableMap.of("enum", new LinkedHashSet<>()))
                                .get("enum")
                                .add(val);
                            required.add(key);
                            ifProps.put(key, ImmutableMap.of("const", val));
                          }
                        });

                    allOf.add(
                        ImmutableMap.of(
                            "if",
                                ImmutableMap.of(
                                    "properties", ifProps, "required", ifProps.keySet()),
                            "then", ImmutableMap.of("$ref", "#/$defs/" + toKey(type))));
                  });
        });

    anyOfProps.forEach(
        (key, val) -> {
          if (props.containsKey(key)) {
            Set<String> enumValues =
                Stream.concat(
                        props.get(key).values().stream().flatMap(Collection::stream),
                        val.values().stream().flatMap(Collection::stream))
                    .collect(Collectors.toSet());
            anyOf.add(
                ImmutableMap.of(
                    "properties",
                    ImmutableMap.of(key, ImmutableMap.of("enum", enumValues)),
                    "required",
                    Set.of(key)));

            props.remove(key);
            required.remove(key);
          } else {
            anyOf.add(Map.of("properties", ImmutableMap.of(key, val), "required", Set.of(key)));
          }
        });

    return sw;
  }

  Map<String, Object> generateObject(DocRef type) {
    return generateObject(type, ImmutableMap.of());
  }

  Map<String, Object> generateObject(DocRef type, Map<String, List<String>> discriminators) {
    Map<String, Object> object = new LinkedHashMap<>();
    Map<String, Object> properties = new LinkedHashMap<>();
    Set<String> required = new LinkedHashSet<>();

    resolveRowSteps(
            docs, type, List.of(new DocStep(Step.JSON_PROPERTIES, List.of("skipDocIgnore"))))
        .filter(DocRef::isMethod)
        .forEach(
            prop -> {
              Entry<String, Map<String, Object>> property =
                  generateProp(docs, prop, prop.getMethod().get(), type.getType().qualifiedName);

              // TODO: move into generateProp
              if (discriminators.containsKey(property.getKey())) {
                List<String> discriminator = discriminators.get(property.getKey());
                if (discriminator.size() == 1) {
                  property.getValue().put("const", discriminator.get(0));
                } else if (discriminator.size() > 1) {
                  property.getValue().put("enum", new HashSet<>(discriminator));
                }
              }

              properties.put(property.getKey(), property.getValue());
            });

    // TODO: json subtypes
    try {
      /*if (properties.containsKey("buildingBlock")
          && properties.get("buildingBlock") instanceof Map) {
        required.add("buildingBlock");
        Map<String, Object> bb = (Map<String, Object>) properties.get("buildingBlock");
        if (bb.containsKey("description") && bb.get("description") instanceof String) {
          String d = ((String) bb.get("description"));
          if (d.contains("`")) {
            List<String> values = new ArrayList<>();
            String[] split = d.split("`");
            if (split.length > 1) values.add(split[1]);
            if (split.length > 3) values.add(split[3]);
            if (values.size() == 1) {
              bb.put("const", values.get(0));
              if (properties.containsKey("extensionType")) {
                ((Map<String, Object>) properties.get("extensionType")).put("const", values.get(0));
              }
            } else if (values.size() > 1) {
              bb.put("enum", values);
              if (properties.containsKey("extensionType")) {
                ((Map<String, Object>) properties.get("extensionType")).put("enum", values);
              }
            }
          }
        }
      }
      if (Objects.nonNull(type.getType().superClass)
          && type.getType()
              .superClass
              .qualifiedName
              .startsWith("de.ii.ogcapi.tiles.domain.TileProvider")) {
        if (properties.containsKey("type") && properties.get("type") instanceof Map) {
          Map<String, Object> t = (Map<String, Object>) properties.get("type");
          switch (type.getType().superClass.qualifiedName) {
            case "de.ii.ogcapi.tiles.domain.TileProviderFeatures":
              t.put("const", "FEATURES");
              break;
            case "de.ii.ogcapi.tiles.domain.TileProviderMbtiles":
              t.put("const", "MBTILES");
              break;
            case "de.ii.ogcapi.tiles.domain.TileProviderTileServer":
              t.put("const", "TILESERVER");
              break;
          }
        }
      }*/
    } catch (Throwable e) {
      e.printStackTrace();
    }

    object.put("title", type.getType().getName().replace("Immutable", ""));

    DocRef descriptionType =
        Objects.nonNull(type.getType().superClass)
            ? docs.findTypeRef(type.getType().superClass.qualifiedName)
            : type.getType().hasInterfaces()
                ? docs.findTypeRef(type.getType().interfaces.get(0).qualifiedName)
                : type;
    resolveColumnSteps(docs, descriptionType, List.of(new DocStep(Step.TAG, List.of("{@body}"))))
        .ifPresent(description -> object.put("description", description));

    object.put("type", "object");
    object.put("properties", properties);
    if (!required.isEmpty()) {
      object.put("required", required);
    }
    object.put("additionalProperties", false);

    return object;
  }

  Map.Entry<String, Map<String, Object>> generateProp(
      Docs docs, DocRef prop, MethodDocs method, String typeQN) {
    String name =
        resolveColumnSteps(docs, prop, List.of(new DocStep(Step.JSON_NAME))).orElseThrow();

    Map<String, Object> def = new LinkedHashMap<>();

    def.put("title", name);

    resolveColumnSteps(docs, prop, List.of(new DocStep(Step.TAG, List.of("{@body}"))))
        .ifPresent(description -> def.put("description", description));

    String typeJson =
        resolveColumnSteps(docs, prop, List.of(new DocStep(Step.JSON_TYPE))).orElseThrow();

    if (!Objects.equals(typeJson, "array")) {
      def.put("type", typeJson);
    }

    if (Objects.nonNull(prop.getAlias())
        || method.getAnnotation("java.lang.Deprecated").isPresent()) {
      def.put("deprecated", true);
    }

    def.putAll(getNestedDefs(method.returnType, typeJson, typeQN + "." + method.qualifiedName));

    return Map.entry(name, def);
  }

  private Map<String, Object> getNestedDefs(String type, String typeJson, String context) {
    Map<String, Object> def = new LinkedHashMap<>();

    if (Objects.equals(typeJson, "object")) {
      if (type.contains("Map<")) {
        String mapType = cleanMap(type);
        String mapTypeJson = StepResolver.getJsonType(mapType, docs);
        Map<String, Object> entries = new LinkedHashMap<>();

        entries.putAll(getNestedDefs(mapType, mapTypeJson, context));
        if (entries.isEmpty()) {
          entries.put("type", mapTypeJson);
        }

        def.put("additionalProperties", entries);
      } else {
        getRefForType(type, context).ifPresent(ref -> def.put("$ref", ref));
      }
    } else if (Objects.equals(typeJson, "array")) {
      String arrayType = cleanArray(type);
      String arrayTypeJson = StepResolver.getJsonType(arrayType, docs);
      Map<String, Object> items = new LinkedHashMap<>();

      items.putAll(getNestedDefs(arrayType, arrayTypeJson, context));
      if (items.isEmpty()) {
        items.put("type", arrayTypeJson);
      }
      // allow single values instead of list like yaml parser
      List<Map<String, Object>> allOf = new ArrayList<>();
      allOf.add(
          ImmutableMap.of(
              "if", ImmutableMap.of("type", "array"), "then", ImmutableMap.of("items", items)));
      allOf.add(ImmutableMap.of("if", ImmutableMap.of("type", "object"), "then", items));

      def.put("allOf", allOf);
    }

    return def;
  }

  private Optional<String> getRefForType(String type, String context) {
    return getRefForType(type, context, ImmutableMap.of());
  }

  private boolean hasJsonTypes(DocRef typeRef) {
    return typeRef.hasAnnotation("com.fasterxml.jackson.annotation.JsonTypeInfo")
        && typeRef
            .getType()
            .getAnnotation("com.fasterxml.jackson.annotation.JsonTypeInfo")
            .get()
            .hasAttribute("property");
  }

  private Map.Entry<String, List<String>> getJsonTypeProperties(DocRef typeRef) {
    if (!hasJsonTypes(typeRef)) {
      return null;
    }
    String property =
        typeRef
            .getType()
            .getAnnotation("com.fasterxml.jackson.annotation.JsonTypeInfo")
            .get()
            .getAttribute("property")
            .get();
    List<String> aliases =
        typeRef
            .getType()
            .getAnnotation("de.ii.xtraplatform.docs.JsonTypeInfoAlias")
            .flatMap(a -> a.getAttribute("value"))
            .map(
                value ->
                    Arrays.stream(value.split(","))
                        .map(alias -> alias.trim().replaceAll("\"", ""))
                        .collect(Collectors.toList()))
            .orElse(List.of());

    return Map.entry(property, aliases);
  }

  private Map<String, List<Map<String, String>>> getJsonTypes(DocRef typeRef, String context) {
    Map<String, List<Map<String, String>>> discriminators = new LinkedHashMap<>();

    if (!hasJsonTypes(typeRef)) {
      return discriminators;
    }

    if (typeRef.hasAnnotation("com.fasterxml.jackson.databind.annotation.JsonTypeIdResolver")) {
      return getJsonTypesDynamic(typeRef, context);
    }

    Entry<String, List<String>> properties = getJsonTypeProperties(typeRef);

    typeRef
        .getType()
        .getAnnotation("com.fasterxml.jackson.annotation.JsonSubTypes")
        .flatMap(a -> a.getAttributeAsJson("value", false))
        .map(t -> (List<Map<String, Object>>) gson.fromJson(t, LIST_MAP))
        .orElse(List.of())
        .forEach(
            s -> {
              String subType =
                  ((String) s.get("value")).endsWith(".class")
                      ? ((String) s.get("value")).replaceFirst(".class$", "")
                      : (String) s.get("value");
              String value = (String) s.get("name");

              List<String> propertyValues = new ArrayList<>();
              List<Map<String, String>> discSets =
                  discriminators.computeIfAbsent(subType, k -> new ArrayList<>());

              propertyValues.add(value);
              discSets.add(
                  Map.of(
                      properties.getKey() + (properties.getValue().isEmpty() ? "" : "|"), value));
              properties
                  .getValue()
                  .forEach(property -> discSets.add(ImmutableMap.of(property + "|", value)));

              Map<String, List<String>> discSet = new LinkedHashMap<>();
              discSet.put(properties.getKey(), propertyValues);
              properties.getValue().forEach(property -> discSet.put(property, propertyValues));

              getRefForType(subType, context, discSet);
            });

    return discriminators;
  }

  private Map<String, List<Map<String, String>>> getJsonTypesDynamic(
      DocRef typeRef, String context) {
    Map<String, List<Map<String, String>>> discriminators = new LinkedHashMap<>();

    if (!hasJsonTypes(typeRef)
        || !typeRef.hasAnnotation("com.fasterxml.jackson.databind.annotation.JsonTypeIdResolver")) {
      return discriminators;
    }

    Entry<String, List<String>> properties = getJsonTypeProperties(typeRef);

    resolveRowSteps(docs, typeRef, List.of(new DocStep(Step.IMPLEMENTATIONS)))
        .filter(subType -> subType.hasAnnotation("de.ii.xtraplatform.docs.JsonDynamicSubType"))
        .forEach(
            subType -> {
              AnnotationDocs ann =
                  subType
                      .getType()
                      .getAnnotation("de.ii.xtraplatform.docs.JsonDynamicSubType")
                      .get();
              List<String> propertyValues = new ArrayList<>();
              List<Map<String, String>> discSets =
                  discriminators.computeIfAbsent(
                      subType.getType().qualifiedName, k -> new ArrayList<>());

              ann.getAttribute("id")
                  .ifPresent(
                      id -> {
                        propertyValues.add(id);
                        discSets.add(
                            Map.of(
                                properties.getKey() + (properties.getValue().isEmpty() ? "" : "|"),
                                id));
                        properties
                            .getValue()
                            .forEach(property -> discSets.add(ImmutableMap.of(property + "|", id)));
                      });

              ann.getAttribute("aliases")
                  .ifPresent(
                      aliases ->
                          Arrays.stream(aliases.split(","))
                              .map(alias -> alias.trim().replaceAll("\"", ""))
                              .forEach(
                                  alias -> {
                                    propertyValues.add(alias);
                                    discSets.add(
                                        ImmutableMap.of(
                                            properties.getKey()
                                                + (properties.getValue().isEmpty() ? "" : "|"),
                                            alias));
                                    properties
                                        .getValue()
                                        .forEach(
                                            property ->
                                                discSets.add(
                                                    ImmutableMap.of(property + "|", alias)));
                                  }));

              Map<String, List<String>> discSet = new LinkedHashMap<>();
              discSet.put(properties.getKey(), propertyValues);
              properties.getValue().forEach(property -> discSet.put(property, propertyValues));

              getRefForType(subType.getType().qualifiedName, context, discSet);
            });

    return discriminators;
  }

  private Optional<String> getRefForType(
      String type, String context, Map<String, List<String>> discriminators) {
    String ref;
    try {
      String returnType = clean(type);
      DocRef typeRef = docs.findTypeRef(returnType);
      ref = toKey(typeRef);

      if (!nestedDefs.containsKey(ref) && !inProgressDefs.contains(ref)) {
        inProgressDefs.add(ref);
        nestedDefs.put(ref, generateObject(typeRef, discriminators));
        inProgressDefs.remove(ref);
      }
    } catch (Throwable e) {
      try {
        String returnType = clean(type, false);
        DocRef typeRef = docs.findTypeRef(returnType);
        ref = toKey(typeRef);

        if (!nestedDefs.containsKey(ref)) {
          if (hasJsonTypes(typeRef)) {
            Map<String, List<Map<String, String>>> discriminators2 = getJsonTypes(typeRef, context);
            List<DocRef> types =
                discriminators2.keySet().stream()
                    .map(docs::findTypeRef)
                    .collect(Collectors.toList());

            //TODO: use allOf/if/then instead of anyOf
            nestedDefs.put(ref, generateSwitch(types, discriminators2));
          } else {
            System.out.println("WARN type for " + context + " not found: " + type);
            System.out.println(
                "WARN no json subtypes found for super type " + typeRef.getType().qualifiedName);

            return Optional.empty();
          }
        }
      } catch (Throwable e2) {
        System.out.println("WARN type for " + context + " not found: " + type);

        return Optional.empty();
      }
    }

    return Optional.of("#/$defs/" + ref);
  }

  static String clean(String type) {
    return clean(type, true);
  }

  static String clean(String type, boolean immutable) {
    if (!type.contains(".")) {
      return type;
    }

    String cleaned =
        type.startsWith("java.util.Optional<")
            ? type.replaceFirst("java.util.Optional<(.*?)>", "$1")
            : type;

    String pkg = cleaned.substring(0, cleaned.lastIndexOf("."));
    String name = cleaned.substring(cleaned.lastIndexOf(".") + 1);
    String parentPkg = pkg.substring(0, pkg.lastIndexOf("."));
    String parentName = pkg.substring(pkg.lastIndexOf(".") + 1);

    String cleanedPkg = Character.isUpperCase(parentName.charAt(0)) ? parentPkg : pkg;
    String prefix = !immutable || name.startsWith("Immutable") ? "." : ".Immutable";

    return cleanedPkg + prefix + name;
  }

  static String cleanArray(String type) {
    if (type.startsWith("java.util.List<")
        || type.startsWith("java.util.Set<")
        || type.startsWith("java.util.Collection<")
        || type.startsWith("com.google.common.collect.ImmutableList<")
        || type.startsWith("com.google.common.collect.ImmutableSet<")) {

      return type.substring(type.indexOf("<") + 1, type.length() - 1);
    } else if (type.endsWith("[]")) {
      return type.substring(0, type.length() - 2);
    }

    return type;
  }

  static String cleanMap(String type) {
    if (type.startsWith("java.util.Map<")
        || type.startsWith("com.google.common.collect.ImmutableMap<")) {
      return type.substring(type.indexOf(",") + 1, type.length() - 1);
    } else if (type.startsWith(
        "de.ii.xtraplatform.store.domain.entities.maptobuilder.BuildableMap<")) {
      return type.substring(type.indexOf("<") + 1, type.indexOf(","));
    }

    return type;
  }

  static List<Map<String, String>> toOneOf(Collection<String> defs) {
    return defs.stream().map(JsonSchemaGenerator::toRef).collect(Collectors.toList());
  }

  static Map<String, String> toRef(String ref) {
    return ImmutableMap.of("$ref", ref.startsWith("#/$defs/") ? ref : "#/$defs/" + ref);
  }

  private String toKey(DocRef type) {
    return keys.computeIfAbsent(
        clean(type.getType().qualifiedName, true),
        k -> {
          String key = type.getType().getName().replaceFirst("^Immutable", "");
          String key2 = key;
          int i = 2;
          while (keys.containsValue(key2)) {
            System.out.println("DUPLICATE " + k + " - " + keys);
            key2 = String.format("%s_%d", key, i++);
          }
          return key2;
        });
  }

  static Stream<DocRef> resolveRowSteps(Docs docs, DocRef root, List<DocStep> steps) {
    Entry<Class<?>, Stream<?>> result = StepResolver.resolve(docs, root, steps, "en");

    if (result.getKey() != DocRef.class) {
      throw new IllegalArgumentException();
    }

    return result.getValue().map(DocRef.class::cast);
  }

  static Optional<String> resolveColumnSteps(Docs docs, DocRef root, List<DocStep> steps) {
    Entry<Class<?>, Stream<?>> result = StepResolver.resolve(docs, root, steps, "en");

    if (result.getKey() != String.class) {
      throw new IllegalArgumentException();
    }

    return result.getValue().map(String.class::cast).findFirst();
  }
}
