package de.ii.xtraplatform.docs;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Generate a markdown file for this interface or class */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface JsonDynamicSubType {

  /**
   * super type with @JsonTypeInfo
   *
   * @return
   */
  Class<?> superType();

  /**
   * value of property set in super type with @JsonTypeInfo
   *
   * @return
   */
  String id();

  /**
   * alternative values of property set in super type with @JsonTypeInfo
   *
   * @return
   */
  String[] aliases() default {};
}
