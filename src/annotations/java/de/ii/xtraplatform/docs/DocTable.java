package de.ii.xtraplatform.docs;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.CLASS)
@Target(ElementType.ANNOTATION_TYPE)
public @interface DocTable {

  enum ColumnSet {NONE,JSON_PROPERTIES}

  String name();

  DocStep[] rows() default {};

  DocColumn[] columns() default {};

  ColumnSet columnSet() default ColumnSet.NONE;
}
