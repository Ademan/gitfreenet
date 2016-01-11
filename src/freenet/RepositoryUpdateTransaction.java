package gitfreenet.freenet;

import freenet.client.async.ClientGetCallback;
import java.util.List;

import freenet.keys.FreenetURI;

public interface RepositoryUpdateTransaction {
	public FreenetURI getURI();
	public void apply();
	public void cancel();

	public List<RepositoryGetCallback> getPendingOperations();

	public abstract class RepositoryGetCallback {
		public final FreenetURI uri;
		Exception exception = null;

		public RepositoryGetCallback(FreenetURI uri) {
			this.uri = uri;
		}

		public abstract String getType();

		public Exception getException() { return exception; }

		public FreenetURI getURI() {
			return uri;
		}
	}
}
