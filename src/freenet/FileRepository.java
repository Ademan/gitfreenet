package gitfreenet.freenet;

import java.nio.file.Path;
import java.io.File;

import java.net.MalformedURLException;

import java.io.FileWriter;
import java.io.IOException;

import java.io.OutputStream;
import java.io.FileOutputStream;

import java.io.InputStream;
import java.io.FileInputStream;
import java.io.BufferedInputStream;
import java.io.DataInputStream;

import java.io.FileReader;
import java.io.BufferedReader;

import java.util.Set;
import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;
import java.util.Collection;

import freenet.keys.ClientCHK;
import freenet.keys.FreenetURI;

import freenet.support.Base64;
import freenet.support.IllegalBase64Exception;

import freenet.client.HighLevelSimpleClient;

import freenet.client.async.USKManager;
import freenet.client.async.USKFetcherCallback;

import gitfreenet.mercurial.Bundle;
import gitfreenet.infocalypse.TopKey;
import gitfreenet.Utilities;

public class FileRepository implements RepositoryStore {
	final Path       root;
	final FreenetURI uri;
	final FileRepositories repositories;

	Set<ClientCHK> bundles = new HashSet<ClientCHK>();
	TopKey topKey = null;

	static final String URIPATH = "uri";
	static final String TOPKEYSPATH = "top";
	static final String TOPKEYSREFPATH = "top";
	static final String BUNDLESPATH = "bundles";
	static final String BUNDLESLISTPATH = "bundles.list";

	public FileRepository(FileRepositories repositories, Path root) throws IOException, SecurityException {
		this.root = root;
		this.repositories = repositories;

		uri = new FreenetURI(Utilities.read(root.resolve(URIPATH)));

		loadTopKey();
		loadBundles();
	}

	void loadTopKey() throws IOException {
		Path topDirectory = root.resolve(TOPKEYSPATH);

		if (!topDirectory.toFile().isDirectory()) {
			return; // We tolerate this
		}

		Path topReferencePath = topDirectory.resolve(TOPKEYSREFPATH);

		if (!topReferencePath.toFile().isFile()) {
			return;
		}

		String topKeyReference = Utilities.read(topReferencePath);

		if (topKeyReference == null || topKeyReference.length() < 1) {
			return;
		}

		Path topKeyPath = root.resolve(topKeyReference);

		try (BufferedInputStream topKeyStream = new BufferedInputStream(new FileInputStream(topKeyPath.toFile()))) {
			topKey = new TopKey(new DataInputStream(topKeyStream));
		}
	}

	public RepositoryUpdateTransaction beginUpdate(Plugin plugin, HighLevelSimpleClient client, USKManager uskManager, FreenetURI uri) throws RepositoryUpdateException {
		try {
			return new FileRepositoryUpdateTransaction(this, plugin, client, uskManager, uri); // FIXME
		} catch (Exception e) {
			throw new RepositoryUpdateException(e);
		}
	}
	public RepositoryUpdateTransaction beginUpdate(Plugin plugin, HighLevelSimpleClient client, USKManager uskManager) throws RepositoryUpdateException {
		return beginUpdate(plugin, client, uskManager, uri);
	}

	// FIXME: error handling
	public Path getTopKeyPath(FreenetURI topKeyUri) {
		return root.resolve(TOPKEYSPATH).resolve(Long.toString(topKeyUri.getSuggestedEdition()));
	}

	/**
	 * Parses a CHK string with only the base64 encoded triple of routing key
	 *     symmetric key, and encryption info.
	 */
	static ClientCHK parseCHK(String s) throws MalformedURLException {
		String[] triple = s.split(",");
		try {
			return new ClientCHK(Base64.decode(triple[0]), Base64.decode(triple[1]), Base64.decode(triple[2]));
		} catch (IllegalBase64Exception e) {
			throw new MalformedURLException("Cannot parse CHK \"" + s + "\"");
		}
	}

	void loadBundles() throws IOException {
		if (root.resolve(BUNDLESLISTPATH).toFile().isFile()) {
			try (BufferedReader reader = new BufferedReader(new FileReader(root.resolve(BUNDLESLISTPATH).toFile()))) {
				Path bundlePath;
				for (int lineNumber = 1; (bundlePath = root.resolve(reader.readLine())) != null; lineNumber++) {
					String bundleFileName = bundlePath.getName(bundlePath.getNameCount() - 1).toString();
					try {
						ClientCHK bundleCHK = parseCHK(bundleFileName);

						bundles.add(bundleCHK);
					} catch (MalformedURLException e) {
						throw new IOException("E:" + bundlePath.toString() + ":" + lineNumber + " " + e.getMessage()); // FIXME: Throw better exception?
					}
				}
			}
		} else {
			// TODO: shouldn't we lazily load bundles?
		}
	}

	public boolean hasBundle(ClientCHK chk) {
		return repositories.hasBundle(chk);
	}

	public Path bundlePath(ClientCHK chk) {
		return repositories.bundlePath(chk);
	}

	public File tempBundlePath() {
		return repositories.tempFilePath("bundle", "temp");
	}

	public OutputStream outputStream(ClientCHK chk) throws IOException {
		Path path = bundlePath(chk);
		return new FileOutputStream(path.toFile());
	}

	public void setBundles(Collection<ClientCHK> bundles) {
		synchronized (this) {
			this.bundles = new HashSet<ClientCHK>(bundles);
		}
	}

	public Collection<ClientCHK> getBundleCHKs() {
		synchronized (this) {
			return new ArrayList<ClientCHK>(bundles);
		}
	}

	public void store(ClientCHK chk, InputStream in) throws IOException {
		try (OutputStream out = outputStream(chk)) {
			Utilities.copyStream(in, out);
		}
	}

	public InputStream inputStream(ClientCHK chk) throws IOException {
		Path path = bundlePath(chk);
		return new FileInputStream(path.toFile());
	}

	public Bundle getBundle(ClientCHK chk) throws IOException {
		try (DataInputStream bundleStream = new DataInputStream(new BufferedInputStream(inputStream(chk)))) {
			return new Bundle(bundleStream);
		}
	}

	public List<Bundle> getBundles() throws IOException {
		List<Bundle> results = new ArrayList<Bundle>();
		for (ClientCHK bundleCHK : getBundleCHKs()) {
			results.add(getBundle(bundleCHK));
		}
		return results;
	}

	public static FileRepository initialize(FileRepositories repositories, Path root, FreenetURI uri) throws SecurityException, IOException {
		if (!root.toFile().mkdirs()) {
			return null;
		}

		Path uriPath = root.resolve(URIPATH);

		Utilities.write(uriPath, uri.toString());

		Path topKeys = root.resolve(TOPKEYSPATH);
		Path topKeysRef = topKeys.resolve(TOPKEYSREFPATH);
		Path bundlesList = root.resolve(BUNDLESLISTPATH);

		if (!topKeys.toFile().isDirectory() && !topKeys.toFile().mkdir()) {
			return null; // FIXME: throw exception?
		}

		Utilities.write(topKeysRef, "");

		// TODO: recall what bundles.list was intended for

		return new FileRepository(repositories, root);
	}

	public static boolean isRepository(Path root) {
		if (!root.toFile().isDirectory()) {
			return false;
		}

		Path uriPath = root.resolve(URIPATH);

		if (!uriPath.toFile().isFile()) {
			return false;
		}

		// TODO: ensure top key directory, bundle directory

		return true;
	}

	public static boolean validate(Path root, FreenetURI uri) {
		if (!isRepository(root)) {
			return false;
		}

		Path uriPath = root.resolve(URIPATH);

		try {
			FreenetURI foundUri = new FreenetURI(Utilities.read(uriPath));

			return uri.equals(foundUri);
		} catch (MalformedURLException e) {
			return false;
		} catch (IOException e) {
			return false;
		}
	}
}
