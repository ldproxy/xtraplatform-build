package de.ii.xtraplatform.docs;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Additional definitions for template instances
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface DocDefs {

  DocTable[] tables() default {};

  DocVar[] vars() default {};
}
