package de.ii.xtraplatform.docs;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Generate a markdown file for this interface or class
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface DocFile {

  /**
   * relative markdown directory
   * @return
   */
  String path() default "";

  /**
   * markdown file name (default is derived from class name with com.google.common.base.CaseFormat#LOWER_HYPHEN)
   * @return
   */
  String name() default "";

  DocTable[] tables() default {};

  DocVar[] vars() default {};
}
