package generate;

import com.samskivert.mustache.DefaultCollector;
import com.samskivert.mustache.Escapers;
import com.samskivert.mustache.Mustache;
import com.samskivert.mustache.Template;

import java.io.File;
import java.io.FileReader;
import java.io.StringReader;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.regex.Pattern;

public class CustomMustacheCompiler {

  // maybe make this customizable in settings?
  public static final int RECURSION_THRESHOLD = 500;
  private static final BiFunction<CustomMustacheCompiler.FaultyType, String, String> DO_FAULTY_HTML_MESSAGE = (type, name) ->
    "<span style=\"color: red !important;\">[" + type.name() + ">" + name + "]</span>";
  private static final String FAULTY_HTML_REGEX_MATCHER = "<span style=\"color: red !important;\">\\[FAULTY_VAR>.*?\\]<\\/span>";
  private static final Mustache.Escaper CUSTOM_ESCAPER = text -> {
    if (Pattern.compile(FAULTY_HTML_REGEX_MATCHER).matcher(text).find()) {
      return text;
    }
    return Escapers.HTML.escape(text);
  };
  private static final RecurringSequenceDetector detector = new RecurringSequenceDetector(RECURSION_THRESHOLD);
  private static final BiFunction<String, String, Mustache.TemplateLoader> TEMPLATE_LOADER =
    (templatesPath, mustacheSuffix) -> name -> {

      if (detector.checkForRecursion(name)) {
        throw new RuntimeException("Recursion found for included template segment: " + name);
      }

      var file = new File(templatesPath, name + "." + mustacheSuffix);
      if (!file.exists()) {
        return new StringReader(DO_FAULTY_HTML_MESSAGE.apply(CustomMustacheCompiler.FaultyType.FAULTY_PARTIAL, name));
      }
      return new FileReader(file);
    };
  private static CustomMustacheCompiler instance;
  private final String templatesPath;
  private final String mustacheSuffix;
  private final Mustache.Compiler mustacheCompiler;

  private CustomMustacheCompiler(String templatesPath, String mustacheSuffix) {
    this.templatesPath = templatesPath;
    this.mustacheSuffix = mustacheSuffix;
    this.mustacheCompiler = Mustache.compiler()
      .withEscaper(CUSTOM_ESCAPER)
      .withCollector(new CustomCollector())
      .withLoader(TEMPLATE_LOADER.apply(templatesPath, mustacheSuffix));
  }

  public static CustomMustacheCompiler getInstance(String templatesPath, String mustacheSuffix) {
    if (instance != null
      && Objects.equals(instance.templatesPath, templatesPath)
      && Objects.equals(instance.mustacheSuffix, mustacheSuffix)) return instance;
    return instance = new CustomMustacheCompiler(templatesPath, mustacheSuffix);
  }

  public Mustache.Compiler getMustacheCompiler() {
    return mustacheCompiler;
  }

  private enum FaultyType {
    FAULTY_PARTIAL,
    FAULTY_VAR
  }

  private static final class CustomCollector extends DefaultCollector {
    @Override
    public Mustache.VariableFetcher createFetcher(Object ctx, String name) {
      var errorFetcher = (Mustache.VariableFetcher) (ctx1, name1) -> DO_FAULTY_HTML_MESSAGE.apply(FaultyType.FAULTY_VAR, name1);
      try {
        var fetcher = super.createFetcher(ctx, name);
        if (Template.NO_FETCHER_FOUND == fetcher.get(ctx, name)) {
          return errorFetcher;
        }
        return fetcher;
      } catch (Exception e) {
        return errorFetcher;
      }
    }
  }

}
