package de.ii.xtraplatform.docs;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.CLASS)
@Target(ElementType.ANNOTATION_TYPE)
public @interface DocStep {

  enum Step {
    IMPLEMENTATIONS,
    TAG_REFS,
    METHODS,
    JSON_PROPERTIES,
    MARKED,
    UNMARKED,
    SORTED,
    ANNOTATIONS,
    TAG,
    JSON_NAME,
    JSON_TYPE,
    CONSTANT,
    FILTER,
    FORMAT,
    COLLECT
  }

  Step type();

  String[] params() default {};
}
