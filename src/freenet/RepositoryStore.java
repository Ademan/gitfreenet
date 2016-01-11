package gitfreenet.freenet;

import java.io.OutputStream;
import java.io.IOException;

import java.io.InputStream;

import freenet.keys.ClientCHK;
import freenet.keys.FreenetURI;

import freenet.client.HighLevelSimpleClient;

import freenet.client.async.USKManager;
import freenet.client.async.USKFetcherCallback;

public interface RepositoryStore {
	public OutputStream outputStream(ClientCHK chk) throws IOException;
	public void store(ClientCHK chk, InputStream in) throws IOException;
	public RepositoryUpdateTransaction beginUpdate(Plugin plugin,
	                                               HighLevelSimpleClient client, USKManager uskManager, FreenetURI uri) throws RepositoryUpdateException;
	public RepositoryUpdateTransaction beginUpdate(Plugin plugin,
	                                               HighLevelSimpleClient client, USKManager uskManager) throws RepositoryUpdateException;
}
