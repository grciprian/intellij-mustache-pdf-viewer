package exceptions;

public class FileNotUnderTemplatesFolderException extends GenericMustacheException {

  public FileNotUnderTemplatesFolderException(String message) {
    super(message);
  }

}
