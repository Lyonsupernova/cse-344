package flightapp.wrapper.exception;

public class CSE344NotAllowAccessException extends RuntimeException {
  public CSE344NotAllowAccessException() {
    super();
  }

  public CSE344NotAllowAccessException(String message) {
    super(message);
  }

  public CSE344NotAllowAccessException(String message, Throwable cause) {
    super(message, cause);
  }

  public CSE344NotAllowAccessException(Throwable cause) {
    super(cause);
  }
}
