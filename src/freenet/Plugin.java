package gitfreenet.freenet;

import java.util.Map;
import java.util.HashMap;
import java.util.Collections;

import java.io.InputStream;
import java.io.OutputStream;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.UnsupportedEncodingException;
import java.io.File;
import java.io.FileReader;
import java.io.Reader;
import java.io.BufferedReader;
import java.io.FileFilter;
import java.io.IOException;

import java.nio.file.Paths;
import java.nio.file.Path;

import java.net.URI;
import java.net.MalformedURLException;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import freenet.node.Node;

import freenet.client.HighLevelSimpleClient;
import freenet.client.FetchException;

import freenet.clients.http.Toadlet;
import freenet.clients.http.ToadletContainer;
import freenet.clients.http.ToadletContext;
import freenet.clients.http.ToadletContextClosedException;

import freenet.pluginmanager.*;

import freenet.support.Logger;
import freenet.support.SimpleFieldSet;
import freenet.support.HexUtil;
import freenet.support.api.HTTPRequest;

import freenet.node.RequestClient;

import freenet.keys.FreenetURI;

import freenet.clients.fcp.FCPPluginMessage;
import freenet.clients.fcp.FCPPluginConnection;
import freenet.pluginmanager.FredPluginFCPMessageHandler;

import gitfreenet.Utilities;

public class Plugin implements FredPlugin, FredPluginThreadless, RequestClient, FredPluginFCPMessageHandler.ServerSideFCPMessageHandler {
	static {
		Logger.registerClass(Plugin.class);
	}

	// Overview page
	public static final String encoding = "UTF-8";

	public final Path pluginRoot;

	private HighLevelSimpleClient client;
	private PluginRespirator respirator;

	private Map<FreenetURI, RepositoryUpdateTransaction> repositoryUpdates = new HashMap<FreenetURI, RepositoryUpdateTransaction>();

	private Map<FreenetURI, RepositoryStore> repositories = new HashMap<FreenetURI, RepositoryStore>();

	private FileRepositories fileRepositories;

	public Plugin() {
		Path cwd = Paths.get("dvcs");
		pluginRoot = cwd.toAbsolutePath();
	}

	public Map<FreenetURI, RepositoryUpdateTransaction> getUpdates() {
		return Collections.unmodifiableMap(repositoryUpdates);
	}

	public String getVersion() {
		return "0.0.1";
	}

	static {
		Logger.registerClass(Plugin.class);
	}

	@Override
	public void runPlugin(PluginRespirator respirator)
	{
		this.respirator = respirator;
		this.fileRepositories = new FileRepositories(pluginRoot, respirator.getNode().fastWeakRandom);
		this.client = respirator.getHLSimpleClient();
	}

	public RepositoryUpdateTransaction update(FreenetURI repositoryURI) throws RepositoryUpdateException {
		RepositoryStore repository = repositories.get(repositoryURI);

		return update(repository, repositoryURI);
	}

	public RepositoryUpdateTransaction update(RepositoryStore repository, FreenetURI repositoryURI) throws RepositoryUpdateException {
		FreenetURI repositoryIdUri = repositoryURI.setSuggestedEdition(0);

		synchronized (repositoryUpdates) {
			if (repositoryUpdates.containsKey(repositoryIdUri)) {
				return null;
			} else {
				Node node = respirator.getNode();
				RepositoryUpdateTransaction transaction = repository.beginUpdate(this, client, node.clientCore.uskManager, repositoryURI);
				repositoryUpdates.put(repositoryIdUri, transaction);
				return transaction;
			}
		}
	}

	public void completeUpdate(RepositoryUpdateTransaction transaction) {
		synchronized (repositoryUpdates) {
			repositoryUpdates.remove(transaction.getURI());
		}
	}

	void loadFileRepositories() throws IOException {
	}

	@Override
	public void terminate()
	{
		
	}

	// Consequence of being persistent?
	@Override
	public boolean persistent() { return false; }
		
	@Override
	public boolean realTimeFlag() { return false; }

	@Override
	public FCPPluginMessage handlePluginFCPMessage(FCPPluginConnection connection, FCPPluginMessage message) {
		SimpleFieldSet params = new SimpleFieldSet(false); // FIXME: verify constructor

		String command = message.params.get("Command");
		String keyStr = message.params.get("Key");

		try {
			if ("AddRepository".equals(command)) {
				FreenetURI repositoryUri = new FreenetURI(keyStr);
				FreenetURI repositoryIdUri = repositoryUri.setSuggestedEdition(0);

				if (repositories.containsKey(repositoryUri)) {
					RepositoryStore repository = repositories.get(repositoryUri); // XXX: retrieve some information?
					return FCPPluginMessage.constructErrorReply(message, "RepositoryExists", "Repository for " + repositoryUri.toString() + " already exists."); // TODO
				} else {
					try {
						RepositoryStore repository = fileRepositories.newRepository(repositoryUri);

						if (repository != null) {
							repositories.put(repositoryIdUri, repository);

							update(repository, repositoryUri);

							return FCPPluginMessage.constructSuccessReply(message);
						} else {
							return FCPPluginMessage.constructErrorReply(message, "AddRepositoryFailed", "Failed to add repository");
						}
					} catch (NullPointerException e) {
						return FCPPluginMessage.constructErrorReply(message, "NullPointerException (addRepository): " + e.getMessage() + e.getStackTrace()[0].getFileName() + ":" + e.getStackTrace()[0].getLineNumber(), e.getMessage()); // FIXME: giving user raw exception info is dangerous
					} catch (RuntimeException e) {
						return FCPPluginMessage.constructErrorReply(message, "UncaughtException (" + e.getClass().getName() + ")", e.getMessage() + "\n" + Utilities.throwableInfo(e)); // FIXME: giving user raw exception info is dangerous
					}
				}
			} else if ("UpdateRepository".equals(command)) {
				FreenetURI repositoryUri = new FreenetURI(keyStr);
				FreenetURI repositoryIdURI = repositoryUri.setSuggestedEdition(0);

				if (repositoryIdURI == null) {
					return FCPPluginMessage.constructErrorReply(message, "MalformedURL", ""); // TODO
				}

				RepositoryStore repository = repositories.get(repositoryIdURI);

				update(repository, repositoryUri);

				return FCPPluginMessage.constructSuccessReply(message);
			} else {
				return FCPPluginMessage.constructErrorReply(message, "UnknownMessage", ""); // TODO
			}
		} catch (MalformedURLException e) {
			return FCPPluginMessage.constructErrorReply(message, "MalformedURL", e.getMessage()); // FIXME: giving user raw exception info is dangerous
		} catch (RepositoryUpdateException e) {
			return FCPPluginMessage.constructErrorReply(message, "UpdateFailed", e.getMessage()); // FIXME: giving user raw exception info is dangerous
		} catch (NullPointerException e) {
			return FCPPluginMessage.constructErrorReply(message, "NullPointerException", e.getMessage() + e.getStackTrace()); // FIXME: giving user raw exception info is dangerous
		} catch (RuntimeException e) {
			return FCPPluginMessage.constructErrorReply(message, "UncaughtException (" + e.getClass().getName() + ")", e.getMessage() + "\n" + Utilities.throwableInfo(e)); // FIXME: giving user raw exception info is dangerous
		}
	}
}
