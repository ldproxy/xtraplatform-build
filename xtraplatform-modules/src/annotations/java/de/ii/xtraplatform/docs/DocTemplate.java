package de.ii.xtraplatform.docs;

import de.ii.xtraplatform.docs.DocFileTemplate.CaseFormat;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.CLASS)
@Target(ElementType.ANNOTATION_TYPE)
public @interface DocTemplate {

  String language();

  String template();
}
