package exceptions;

public class ModuleNotFoundException extends GenericMustacheException {

  public ModuleNotFoundException(String message) {
    super(message);
  }

}
