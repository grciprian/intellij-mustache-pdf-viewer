package generate;

import com.intellij.openapi.util.io.FileUtil;
import com.samskivert.mustache.Template;

import java.io.File;
import java.lang.reflect.Field;
import java.util.*;

public class PdfStructureService {

  private static final String SEGS = "_segs";
  private static final String NAME = "_name";
  private static final String LINE = "_line";
  private static final String TEMPLATE = "_template";
  private static final Map<String, SEG_TYPE> identifierSegTypeMapper = Map.of(
    "IncludedTemplateSegment", SEG_TYPE.INCLUDED_TEMPLATE_SEGMENT,
    "SectionSegment", SEG_TYPE.SECTION_SEGMENT,
    "InvertedSegment", SEG_TYPE.INVERTED_SEGMENT,
    "VariableSegment", SEG_TYPE.VARIABLE_SEGMENT
  );
  private static String templatesPath;
  private static String mustacheSuffix;

  private PdfStructureService() {
    // this class should not be initialized
  }

  public static List<Structure> getStructure(String relativePath, Template template, String templatesPath, String mustacheSuffix) {
    PdfStructureService.templatesPath = templatesPath;
    PdfStructureService.mustacheSuffix = mustacheSuffix;
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
      return new Structure(parentFragment, (String) nameField.get(seg), (int) lineField.get(seg), identifierSegTypeMapper.getOrDefault(clazz.getSimpleName(), null));
    } catch (NoSuchFieldException | IllegalAccessException e) {
//      System.out.println("No field with the name provided could be found or could not be accessed.");
      return Structure.createBlank();
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

  public enum SEG_TYPE {
    INCLUDED_TEMPLATE_SEGMENT(">"), SECTION_SEGMENT("#"), INVERTED_SEGMENT("^"), VARIABLE_SEGMENT("");

    private final String value;

    SEG_TYPE(String value) {
      this.value = value;
    }

    public String getValue() {
      return value;
    }
  }

  public static final class Structure {
    private final String parentFragment;
    private final String name;
    private final String nameNormalized;
    private final int line;
    private final SEG_TYPE segType;
    private final List<Structure> structures;
    // only has meaning if SEG_TYPE is INCLUDED_TEMPLATE_SEGMENT
    private final boolean isIncludedTemplateSegmentValid;
    private String customToString;

    public Structure(String parentFragment, String name, int line, SEG_TYPE segType, List<Structure> structures) {
      this.parentFragment = parentFragment;
      this.name = name;
      this.nameNormalized = Optional.ofNullable(name)
        .map(nam -> FileUtil.normalize(nam).replaceAll("^/+", ""))
        .orElse(null);
      this.line = line;
      this.segType = segType;
      this.structures = structures;
      this.isIncludedTemplateSegmentValid = SEG_TYPE.INCLUDED_TEMPLATE_SEGMENT.equals(segType) && new File(templatesPath, name + "." + mustacheSuffix).exists();
    }

    Structure(String parentFragment, String name, int line, SEG_TYPE segType) {
      this(parentFragment, name, line, segType, null);
    }

    public static Structure createBlank() {
      return new Structure(null, null, -1, null, null);
    }

    public static Structure createRoot(String name, List<Structure> structures) {
      return new Structure(name, name, -1, null, structures);
    }

    public Boolean isRoot() {
      return Objects.equals(parentFragment, name) && line == -1 && segType == null; // sufficient, structures may or may not exist under root
    }

    @Override
    public String toString() {
      var segT = Optional.ofNullable(segType).map(SEG_TYPE::getValue).orElse("");
      var nam = SEG_TYPE.INCLUDED_TEMPLATE_SEGMENT.equals(segType) ? "/" + name : name;
      return Optional.ofNullable(customToString)
        .orElse("%s%s, line=%d".formatted(segT, nam, line));
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

    public String parentFragment() {
      return parentFragment;
    }

    public String normalizedName() {
      return nameNormalized;
    }

    public int line() {
      return line;
    }

    public SEG_TYPE segType() {
      return segType;
    }

    public List<Structure> structures() {
      return structures;
    }

    public boolean isIncludedTemplateSegmentValid() {
      return isIncludedTemplateSegmentValid;
    }

    public String getCustomToString() {
      return customToString;
    }

    public void setCustomToString(String customToString) {
      this.customToString = customToString;
    }
  }

}