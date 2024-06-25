package generate;

import com.intellij.openapi.util.Pair;
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

  private static final BiFunction<CustomMustacheCompiler.FaultyType, String, String> DO_FAULTY_HTML_MESSAGE = (type, name) ->
    "<span style=\"color: red !important;\">[" + type.name() + ">" + name + "]</span>";
  private static final String FAULTY_HTML_REGEX_MATCHER = "<span style=\"color: red !important;\">\\[FAULTY_VAR>.*?\\]<\\/span>";
  // maybe make this customizable in settings?
  private static final Long RECURSION_THRESHOLD = 500L;
  private static final Mustache.Escaper CUSTOM_ESCAPER = text -> {
    if (Pattern.compile(FAULTY_HTML_REGEX_MATCHER).matcher(text).find()) {
      return text;
    }
    return Escapers.HTML.escape(text);
  };
  private static Pair<String, Long> recursionCounter = Pair.empty();
  private static final BiFunction<String, String, Mustache.TemplateLoader> TEMPLATE_LOADER =
    (templatesPath, mustacheSuffix) -> name -> {
      if (!Objects.equals(name, recursionCounter.first)) {
        recursionCounter = new Pair<>(name, 1L);
      } else {
        recursionCounter = new Pair<>(recursionCounter.first, recursionCounter.second + 1);
      }
      if (recursionCounter.second > RECURSION_THRESHOLD) {
        throw new RuntimeException("Recursion found for included template segment: " + name);
      }
      var file = new File(templatesPath, name + "." + mustacheSuffix);
      if (!file.exists()) {
        return new StringReader(DO_FAULTY_HTML_MESSAGE.apply(CustomMustacheCompiler.FaultyType.FAULTY_PARTIAL, name));
      }
      return new FileReader(file);
    };
  private static Mustache.Compiler instance;

  private CustomMustacheCompiler() {
  }

  public static Mustache.Compiler getInstance(String templatesPath, String mustacheSuffix) {
    if (instance != null) return instance;
    instance = Mustache.compiler()
      .withLoader(TEMPLATE_LOADER.apply(templatesPath, mustacheSuffix))
      .withEscaper(CUSTOM_ESCAPER)
      .withCollector(new CustomCollector());
    return instance;
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
