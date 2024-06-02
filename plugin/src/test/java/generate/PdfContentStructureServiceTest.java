package generate;

import com.samskivert.mustache.Mustache;
import org.junit.Test;

public class PdfContentStructureServiceTest {

  Mustache.Compiler customMustacheCompiler = CustomMustacheCompiler.getInstance();

  @Test
  public void testGetSimpleStructure() {
    var template = customMustacheCompiler.compile("{{#one}}1{{/one}} {{^two}}2{{three}}{{/two}}{{four}}");
    var structure = PdfStructureService.getStructure(template);
    System.out.println(structure);
  }

  @Test
  public void testGetComplexStructure() {
    var template = customMustacheCompiler.compile("""
      {{#one}}
        1
        {{#five}}
          ceva text
          {{{varOne}}}
          {{#six}}
            {{varTwo}}
            {{{varThree}}}
          {{/six}}
        {{/five}}
      {{/one}}
      {{^two}}
        2 {{three}}
      {{/two}}
      {{four}}
      """);
    var structure = PdfStructureService.getStructure(template);
    System.out.println(structure);
  }

}
