package generate;

import com.samskivert.mustache.Template;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public class PdfStructureService {

  private static final String SEGS = "_segs";
  private static final String NAME = "_name";
  private static final String LINE = "_line";
  private static final String TEMPLATE = "_template";

  private PdfStructureService() {
    // this class should not be initialized
  }

  public static List<Structure> getStructure(Template template) {
    var structure = new ArrayList<Structure>();
    try {
      var segsField = getField(Template.class, SEGS);
      segsField.setAccessible(true);
      var segs = (Object[]) segsField.get(template);
      structure.addAll(processSegments(segs));
    } catch (NoSuchFieldException e) {
      System.out.println("No field with the name provided could be found: " + e.getMessage());
      e.printStackTrace();
    } catch (Exception e) {
      e.printStackTrace();
    }
    return structure;
  }

  private static List<Structure> processSegments(Object[] segs) {
    var partialStructure = new ArrayList<Structure>();
    for (var seg : segs) {
      var struct = getNameLine(seg, seg.getClass());
      try {
        var segsFromObject = seg;
        try {
          var insideTemplateField = getField(seg.getClass(), TEMPLATE);
          insideTemplateField.setAccessible(true);
          segsFromObject = insideTemplateField.get(seg);
        } catch (NoSuchFieldException | IllegalAccessException e) {
          // seg is not a template so access _segs directly and not from _template
        }
        var insideSegsField = getField(segsFromObject.getClass(), SEGS);
        insideSegsField.setAccessible(true);
        var insideSegs = (Object[]) insideSegsField.get(segsFromObject);
        struct = new Structure(struct.name, struct.line, processSegments(insideSegs));
      } catch (NoSuchFieldException | IllegalAccessException e) {
        // its a terminal segment
      }
      if (struct.line != -1) {
        partialStructure.add(struct);
      }
    }
    return partialStructure;
  }

  private static Structure getNameLine(Object seg, Class<?> clazz) {
    try {
      var nameField = getField(clazz, NAME);
      var lineField = getField(clazz, LINE);
      nameField.setAccessible(true);
      lineField.setAccessible(true);
      return new Structure((String) nameField.get(seg), (int) lineField.get(seg));
    } catch (NoSuchFieldException | IllegalAccessException e) {
//      System.out.println("No field with the name provided could be found or could not be accessed.");
      return new Structure(null, -1, null);
    }
  }

  private static Field getField(Class<?> clazz, String fieldName) throws NoSuchFieldException {
    try {
      return clazz.getDeclaredField(fieldName);
    } catch (NoSuchFieldException e) {
      var superClass = clazz.getSuperclass();
      if (superClass == null) {
        throw e;
      } else {
        return getField(superClass, fieldName);
      }
    }
  }

  public record Structure(String name, int line, List<Structure> structures) {
    public Structure(String name, int line) {
      this(name, line, null);
    }

    @Override
    public String toString() {
      return "%s, line=%d".formatted(name, line);
    }
  }

}
