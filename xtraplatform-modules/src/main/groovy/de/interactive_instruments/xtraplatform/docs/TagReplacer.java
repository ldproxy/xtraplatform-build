package de.interactive_instruments.xtraplatform.docs;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TagReplacer {

  private static final Pattern PREFIX_AND_IF_EMPTY_PATTERN =
      Pattern.compile(
          "\\{@(?<tag>[\\w.:]+?)(\\s+(?<prefix>.*?)(\\|\\|\\|(?<ifempty>.*?))?)?}", Pattern.DOTALL);

  private final DocRef docRef;
  private final String language;
  private final String languageSuffix;
  private final Map<String, String> vars;

  public TagReplacer(DocRef docRef, String language, Map<String, String> vars) {
    this.language = language;
    this.languageSuffix =
        language.length() > 1
            ? language.substring(0, 1).toUpperCase(Locale.ROOT) + language.substring(1)
            : language;
    this.docRef = docRef;
    this.vars = vars;
  }

  String replaceStrings(String text) {
    String replaced = replace(text, PREFIX_AND_IF_EMPTY_PATTERN, this::prefixAndIfEmptyReplacer);

    // run a second time but NOT recursive, unresolvable tags would cause endless loop
    if (PREFIX_AND_IF_EMPTY_PATTERN.matcher(replaced).find()) {
      return replace(replaced, PREFIX_AND_IF_EMPTY_PATTERN, this::prefixAndIfEmptyReplacer);
    }

    return replaced;
  }

  static String replace(String text, Pattern pattern, Function<Matcher, String> replacer) {
    int lastIndex = 0;
    StringBuilder output = new StringBuilder();
    Matcher matcher = pattern.matcher(text);
    while (matcher.find()) {
      output.append(text, lastIndex, matcher.start()).append(replacer.apply(matcher));

      lastIndex = matcher.end();
    }
    if (lastIndex < text.length()) {
      output.append(text, lastIndex, text.length());
    }

    return output.toString();
  }

  private String prefixAndIfEmptyReplacer(Matcher matcher) {
    String tag = matcher.group("tag");
    String prefix = Optional.ofNullable(matcher.group("prefix")).orElse("");
    String ifempty = Optional.ofNullable(matcher.group("ifempty")).orElse(matcher.group());

    return Optional.ofNullable(docRef.getVars().get(tag + languageSuffix))
        .or(() -> Optional.ofNullable(docRef.getVars().get(tag)))
        .or(() -> Optional.ofNullable(vars.get(tag + languageSuffix)))
        .or(() -> Optional.ofNullable(vars.get(tag)))
        .or(() -> findTag(tag))
        .map(text -> prefix + text)
        .orElse(ifempty);
  }

  private Optional<String> findTag(String tag) {
    if (Objects.equals(tag, "body")) {
      return Optional.of(docRef.getDocText(language).replaceAll("\n", " ").trim());
    }
    if (Objects.equals(tag, "bodyBlock")) {
      return Optional.of(docRef.getDocText(language));
    }

    return docRef
        .getDocTag(tag)
        .findFirst()
        .or(() -> docRef.getDocTag(tag + languageSuffix).findFirst())
        .or(() -> docRef.getDocTag(tag + "All").findFirst());
  }
}
