package generate;

import com.samskivert.mustache.DefaultCollector;
import com.samskivert.mustache.Escapers;
import com.samskivert.mustache.Mustache;
import com.samskivert.mustache.Template;

import java.io.File;
import java.io.FileReader;
import java.io.StringReader;
import java.util.function.BiFunction;
import java.util.regex.Pattern;

import static com.firsttimeinforever.intellij.pdf.viewer.ui.editor.PdfFileEditorProviderKt.FILE_RESOURCES_PATH_WITH_MUSTACHE_PREFIX;
import static generate.Utils.MUSTACHE_DEFAULT_SUFFIX;

public class CustomMustacheCompiler {

  private static final BiFunction<CustomMustacheCompiler.FaultyType, String, String> DO_FAULTY_HTML_MESSAGE = (type, name) ->
    "<span style=\"color: red !important;\">[" + type.name() + ">" + name + "]</span>";
  private static final String FAULTY_HTML_REGEX_MATCHER = "<span style=\"color: red !important;\">\\[FAULTY_VAR>.*?\\]<\\/span>";
  private static final Mustache.TemplateLoader TEMPLATE_LOADER = name -> {
    var file = new File(FILE_RESOURCES_PATH_WITH_MUSTACHE_PREFIX, name + "." + MUSTACHE_DEFAULT_SUFFIX);
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
