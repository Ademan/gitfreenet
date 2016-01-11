package gitfreenet.freenet;

public class RepositoryUpdateException extends Exception {
	public final Exception wrappedException;
	public RepositoryUpdateException(Exception exception) {
		wrappedException = exception;
	}
	public String getMessage() {
		return "RepositoryUpdateTransactionException: " + wrappedException.getMessage();
	}
}
