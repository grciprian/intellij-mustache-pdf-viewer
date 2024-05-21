package generate;

import com.firsttimeinforever.intellij.pdf.viewer.settings.PdfViewerSettings;
import com.samskivert.mustache.DefaultCollector;
import com.samskivert.mustache.Escapers;
import com.samskivert.mustache.Mustache;
import com.samskivert.mustache.Template;
import org.apache.commons.lang3.RandomStringUtils;

import java.io.File;
import java.io.FileReader;
import java.io.StringReader;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.regex.Pattern;

import static generate.Utils.DEFAULT_SUFFIX;
import static generate.Utils.FILE_RESOURCES_PATH_WITH_PREFIX;

public class CustomMustacheCompiler {

  private static final BiFunction<CustomMustacheCompiler.FaultyType, String, String> DO_FAULTY_HTML_MESSAGE = (type, name) ->
    "<span style=\"color: red !important;\">[" + type.name() + ">" + name + "]</span>";
  private static final Function<String, String> DO_MOCK_HTML_MESSAGE = (name) ->
    "<span style=\"color: red !important;\">" + name + "</span>";
  private static final String FAULTY_HTML_REGEX_MATCHER = "<span style=\"color: red !important;\">\\[FAULTY_VAR>.*?\\]<\\/span>";
  private static final Mustache.TemplateLoader TEMPLATE_LOADER = name -> {
    var file = new File(FILE_RESOURCES_PATH_WITH_PREFIX, name + DEFAULT_SUFFIX);
    if (!file.exists()) {
      return new StringReader(DO_FAULTY_HTML_MESSAGE.apply(CustomMustacheCompiler.FaultyType.FAULTY_PARTIAL, name));
    }
    return new FileReader(file);
  };
  private static final Mustache.Escaper CUSTOM_ESCAPER = text -> {
    if (Pattern.compile(FAULTY_HTML_REGEX_MATCHER).matcher(text).find()) {
      return text;
    }
    return Escapers.HTML.escape(text);
  };
  private static Mustache.Compiler instance;

  private CustomMustacheCompiler() {
  }

  public static Mustache.Compiler getInstance() {
    if (CustomMustacheCompiler.instance != null) return instance;
    CustomMustacheCompiler.instance = Mustache.compiler()
      .withLoader(TEMPLATE_LOADER)
      .withEscaper(CUSTOM_ESCAPER)
      .withCollector(new CustomCollector());
    return CustomMustacheCompiler.instance;
  }

  private enum FaultyType {
    FAULTY_PARTIAL,
    FAULTY_VAR
  }

  private static final class CustomCollector extends DefaultCollector {
    @Override
    public Mustache.VariableFetcher createFetcher(Object ctx, String name) {
      var errorFetcher = (Mustache.VariableFetcher) (ctx1, name1) -> DO_FAULTY_HTML_MESSAGE.apply(FaultyType.FAULTY_VAR, name1);
      var mockFetcher = (Mustache.VariableFetcher) (ctx1, name1) -> DO_MOCK_HTML_MESSAGE.apply(name1 + "[" + RandomStringUtils.randomAlphanumeric(5) + "]");
      try {
        var fetcher = super.createFetcher(ctx, name);
        if (Template.NO_FETCHER_FOUND == fetcher.get(ctx, name)) {
          if (PdfViewerSettings.Companion.getInstance().getHasMockVars()) {
            return mockFetcher;
          }
          return errorFetcher;
        }
        return fetcher;
      } catch (Exception e) {
        return errorFetcher;
      }
    }
  }

}
