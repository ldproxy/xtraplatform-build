package de.ii.xtraplatform.docs;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface DocTable {

  enum ForEach {PROPERTIES, IMPLEMENTATION}

  String name();

  ForEach rows();

  DocColumn[] columns();
}
