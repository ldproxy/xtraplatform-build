package de.ii.xtraplatform.docs;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Generate a markdown file for this interface or class */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface JsonTypeInfoAlias {

  /**
   * alternative values for property set in @JsonTypeInfo
   *
   * @return
   */
  String[] value() default {};
}
