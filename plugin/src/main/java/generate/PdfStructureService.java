package generate;

import com.samskivert.mustache.Template;

import java.lang.reflect.Field;
import java.util.*;

public class PdfStructureService {

  private static final String SEGS = "_segs";
  private static final String NAME = "_name";
  private static final String LINE = "_line";
  private static final String TEMPLATE = "_template";

  private PdfStructureService() {
    // this class should not be initialized
  }

  public static List<Structure> getStructure(String relativePath, Template template) {
    var structure = new ArrayList<Structure>();
    try {
      var segsField = getField(Template.class, SEGS);
      segsField.setAccessible(true);
      var segs = (Object[]) segsField.get(template);
      structure.addAll(processSegments(relativePath, segs));
    } catch (NoSuchFieldException e) { //IllegalAccessException e
      System.out.println("No field with the name provided could be found: " + e.getMessage());
      e.printStackTrace();
    } catch (Exception e) {
      e.printStackTrace();
    }
    return structure;
  }

  private static List<Structure> processSegments(String parentFragment, Object[] segs) {
    var partialStructure = new ArrayList<Structure>();
    for (var seg : segs) {
      var struct = getBasicStruct(parentFragment, seg);
      var newParentFragment = parentFragment;
      try {
        var segsFromObject = seg;
        try {
          var insideTemplateField = getField(seg.getClass(), TEMPLATE);
          insideTemplateField.setAccessible(true);
          segsFromObject = insideTemplateField.get(seg);
          newParentFragment = struct.name;
        } catch (NoSuchFieldException | IllegalAccessException e) {
          // seg is not a template so access _segs directly and not from _template
        }
        var insideSegsField = getField(segsFromObject.getClass(), SEGS);
        insideSegsField.setAccessible(true);
        var insideSegs = (Object[]) insideSegsField.get(segsFromObject);
        struct = new Structure(parentFragment, struct.name, struct.line, struct.segType, processSegments(newParentFragment, insideSegs));
      } catch (NoSuchFieldException | IllegalAccessException e) {
        // its a terminal segment
      }
      if (struct.line != -1) {
        partialStructure.add(struct);
      }
    }
    return partialStructure;
  }

  private static Structure getBasicStruct(String parentFragment, Object seg) {
    try {
      var clazz = seg.getClass();
      var nameField = getField(clazz, NAME);
      var lineField = getField(clazz, LINE);
      nameField.setAccessible(true);
      lineField.setAccessible(true);
      return new Structure(parentFragment, (String) nameField.get(seg), (int) lineField.get(seg), SEG_TYPE.getByValue(clazz.getSimpleName()));
    } catch (NoSuchFieldException | IllegalAccessException e) {
//      System.out.println("No field with the name provided could be found or could not be accessed.");
      return new Structure(null, null, -1, SEG_TYPE.WHATEVER, null);
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

  private enum SEG_TYPE {
    INCLUDED_TEMPLATE_SEGMENT("IncludedTemplateSegment"),
    SECTION_SEGMENT("SectionSegment"),
    INVERTED_SEGMENT("InvertedSegment"),
    VARIABLE_SEGMENT("VariableSegment"),
    WHATEVER("");
    private final String value;

    SEG_TYPE(String value) {
      this.value = value;
    }

    public static SEG_TYPE getByValue(String value) {
      return Arrays.stream(values()).filter(v -> v.value.equals(value)).findFirst().orElse(WHATEVER);
    }
  }

  public record Structure(String parentFragment, String name, int line, SEG_TYPE segType, List<Structure> structures) {

    private static final Map<SEG_TYPE, String> segTypeMapper = Map.of(
      SEG_TYPE.INCLUDED_TEMPLATE_SEGMENT, ">",
      SEG_TYPE.SECTION_SEGMENT, "#",
      SEG_TYPE.INVERTED_SEGMENT, "^",
      SEG_TYPE.VARIABLE_SEGMENT, "",
      SEG_TYPE.WHATEVER, ""
    );

    public Structure(String parentFragment, String name, int line, SEG_TYPE segType) {
      this(parentFragment, name, line, segType, null);
    }

    public static Structure createRootStructure(String root, String selectedNodeName) {
      var name = selectedNodeName + "@" + root;
      return new Structure(name, name, -1, SEG_TYPE.WHATEVER);
    }

    @Override
    public String toString() {
      if (line == -1) return name;
      var segT = Optional.ofNullable(segType).map(segTypeMapper::get).orElse("");
      return "%s%s, line=%d".formatted(segT, name, line);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      Structure structure = (Structure) o;
      return line == structure.line && Objects.equals(parentFragment, structure.parentFragment) && Objects.equals(name, structure.name) && segType == structure.segType && Objects.equals(structures, structure.structures);
    }

    @Override
    public int hashCode() {
      return Objects.hash(parentFragment, name, line, segType, structures);
    }
  }

}
