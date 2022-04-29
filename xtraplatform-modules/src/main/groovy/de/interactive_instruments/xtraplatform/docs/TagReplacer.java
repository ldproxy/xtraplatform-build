package de.interactive_instruments.xtraplatform.docs;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TagReplacer {

  enum Mode {PREFIX_AND_IF_EMPTY}

  private final Mode mode;
  private final Map<String, String> vars;
  private final ElementDocs element;
  private final String language;

  public TagReplacer(ElementDocs element, Map<String, String> vars, String language) {
    this.language = language;
    this.mode = Mode.PREFIX_AND_IF_EMPTY;
    this.vars = vars;
    this.element = element;
  }

  String replaceStrings(String text) {
    return replace(text, PREFIX_AND_IF_EMPTY_PATTERN, this::prefixAndIfEmptyReplacer);
  }

  static String replace(String text, Pattern pattern, Function<Matcher, String> replacer) {
    int lastIndex = 0;
    StringBuilder output = new StringBuilder();
    Matcher matcher = pattern.matcher(text);
    while (matcher.find()) {
      output.append(text, lastIndex, matcher.start())
          .append(replacer.apply(matcher));

      lastIndex = matcher.end();
    }
    if (lastIndex < text.length()) {
      output.append(text, lastIndex, text.length());
    }
    return output.toString();
  }

  static final Pattern PREFIX_AND_IF_EMPTY_PATTERN = Pattern.compile("\\{@(?<tag>[\\w.:]+?)(\\s+(?<prefix>.*?)(\\|\\|\\|(?<ifempty>.*?))?)?}", Pattern.DOTALL);

  private String prefixAndIfEmptyReplacer(Matcher matcher) {
      String tag = matcher.group("tag");
      String prefix = Optional.ofNullable(matcher.group("prefix")).orElse("");
      String ifempty = Optional.ofNullable(matcher.group("ifempty")).orElse(matcher.group());

      return Optional.ofNullable(vars.get(tag))
          .or(() -> findTag(tag))
          .map(text -> prefix + text)
          .orElse(ifempty);
  }

  private Optional<String> findTag(String tag) {
    if (Objects.equals(tag, "body")) {
      return Optional.of(element.getDocText(language)
          .replaceAll("\n", " ")
          .trim());
    }
    return element.getDocTag(tag)
        .findFirst();
  }
}
