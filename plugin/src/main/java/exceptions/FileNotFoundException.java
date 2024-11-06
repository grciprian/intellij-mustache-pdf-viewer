package exceptions;

public class FileNotFoundException extends GenericMustacheException {

  public FileNotFoundException(String message) {
    super(message);
  }

}
