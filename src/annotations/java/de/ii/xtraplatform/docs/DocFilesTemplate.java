package de.ii.xtraplatform.docs;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Generate a markdown file for every implementation of this interface or class
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface DocFilesTemplate {

  enum CaseFormat {
    /** Hyphenated variable naming convention, e.g., "lower-hyphen". */
    LOWER_HYPHEN,
    /** C++ variable naming convention, e.g., "lower_underscore". */
    LOWER_UNDERSCORE,
    /** Java variable naming convention, e.g., "lowerCamel". */
    LOWER_CAMEL,
    /** Java and C++ class naming convention, e.g., "UpperCamel". */
    UPPER_CAMEL,
    /** Java and C++ constant naming convention, e.g., "UPPER_UNDERSCORE". */
    UPPER_UNDERSCORE;
  }

  enum ForEach {IMPLEMENTATION}

  ForEach files();
  /**
   * relative markdown file path (default is derived from class name with com.google.common.base.CaseFormat#LOWER_HYPHEN)
   * @return
   */
  String path() default "";

  String stripPrefix() default "";

  String stripSuffix() default "";

  CaseFormat caseFormat() default CaseFormat.LOWER_UNDERSCORE;

  DocI18n[] template() default {};

  DocTable[] tables() default {};

  DocVar[] vars() default {};
}
