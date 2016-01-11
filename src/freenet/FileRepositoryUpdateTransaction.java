package gitfreenet.freenet;

import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;
import java.util.Collections;
import java.util.Iterator;

import java.nio.file.Path;
import java.io.File;

import java.io.BufferedInputStream;
import java.io.FileInputStream;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.FileNotFoundException;

import freenet.client.HighLevelSimpleClient;
import freenet.client.async.ClientGetCallback;
import freenet.client.async.ClientGetter;
import freenet.client.async.ClientContext;

import freenet.client.async.USKManager;
import freenet.client.async.USKFetcherCallback;

import freenet.client.FetchException;
import freenet.client.FetchResult;

import freenet.support.api.Bucket;
import freenet.node.RequestStarter;

import freenet.keys.USK;
import freenet.keys.FreenetURI;
import freenet.keys.ClientCHK;

import freenet.node.RequestClient;
import freenet.support.io.ResumeFailedException;

import freenet.support.Logger;

import gitfreenet.Utilities;

import gitfreenet.mercurial.NodeId;
import gitfreenet.mercurial.Bundle;
import gitfreenet.mercurial.TreeBuilder;

import gitfreenet.infocalypse.TopKey;
import gitfreenet.infocalypse.Update;
import gitfreenet.infocalypse.InvalidMagicNumberException;

public class FileRepositoryUpdateTransaction implements RepositoryUpdateTransaction {
	static {
		Logger.registerClass(FileRepositoryUpdateTransaction.class);
		Logger.registerClass(GetTopKeyCallback.class);
		Logger.registerClass(GetBundleCallback.class);
	}

	public final FreenetURI uri;

	public FreenetURI getURI() { return uri; }

	ArrayList<Bundle> bundles = new ArrayList<Bundle>();
	TopKey top = null;
	HighLevelSimpleClient client;

	HashSet<NodeId> parents = new HashSet<NodeId>();
	HashSet<NodeId> heads = new HashSet<NodeId>();
	TreeBuilder<NodeId> tree = new TreeBuilder<NodeId>();
	FileRepository repository;
	USKManager uskManager;

	// Convert to synchronized...
	List<RepositoryGetCallback> pendingOperations;
	List<RepositoryGetCallback> completedOperations;
	List<RepositoryGetCallback> failedOperations;
	List<Path> bundlePaths;

	RequestClient requestClient;

	Path root;

	short priority = RequestStarter.PREFETCH_PRIORITY_CLASS;

	public boolean failed() {
		synchronized(pendingOperations) {
			// FIXME: is this logic consistent with how we're doing things?
			return pendingOperations.size() < 1 && tree.getRoots().size() > 1;
		}
	}

	public void apply() {
		throw new UnsupportedOperationException();
	}

	public void cancel() {
		throw new UnsupportedOperationException();
	}

	public FileRepositoryUpdateTransaction(FileRepository repo, RequestClient requestClient, HighLevelSimpleClient client, USKManager uskManager, FreenetURI topKey) throws FetchException {
		this.uri = topKey;
		this.client = client;
		this.requestClient = requestClient;
		this.repository = repo;
		this.uskManager = uskManager;

		pendingOperations = Collections.synchronizedList(new ArrayList<RepositoryGetCallback>());
		completedOperations = Collections.synchronizedList(new ArrayList<RepositoryGetCallback>());
		failedOperations = Collections.synchronizedList(new ArrayList<RepositoryGetCallback>());
		failedOperations = Collections.synchronizedList(new ArrayList<RepositoryGetCallback>());
		bundlePaths = Collections.synchronizedList(new ArrayList<Path>());

		fetchTopKey(topKey);
	}

	public List<RepositoryUpdateTransaction.RepositoryGetCallback> getPendingOperations() {
		return new ArrayList<RepositoryUpdateTransaction.RepositoryGetCallback>(pendingOperations);
	}

	public abstract class RepositoryGetCallback extends RepositoryUpdateTransaction.RepositoryGetCallback implements ClientGetCallback {
		public RepositoryGetCallback(FreenetURI uri) {
			super(uri);
		}

		@Override
		public void onResume(ClientContext context) throws ResumeFailedException {

		}

		@Override
		public void onFailure(FetchException exception, ClientGetter state) {
			failedOperations.add(this);
			fail(exception);
		}

		public void start() throws FetchException {
			pendingOperations.add(this);
			client.fetch(uri, this, client.getFetchContext(), priority);
		}

		protected void finish() {
			Logger.debug(this, this.toString() + " success.");
			pendingOperations.remove(this);
			completedOperations.add(this);
		}

		protected void fail(Exception exception) {
			Logger.error(this, this.toString() + " failed.", exception);
			pendingOperations.remove(this);
			failedOperations.add(this);

			this.exception = exception;
		}

		public RequestClient getRequestClient() { return requestClient; }

		//public abstract String getType();

		public String toString() {
			return "Fetch " + getType() + " " + uri;
		}

		public FreenetURI getURI() {
			return uri;
		}

		public boolean equals(Object o) {
			if (o instanceof RepositoryGetCallback) {
				return uri.equals(((RepositoryGetCallback)o).uri);
			} else {
				return false;
			}
		}
	}

	public class GetTopKeyCallback extends RepositoryGetCallback implements ClientGetCallback {
		public GetTopKeyCallback(FreenetURI uri) { super(uri); }
		public String getType() { return "TopKey"; }

		@Override
		public void onFailure(FetchException exception, ClientGetter state) {
			if (exception.getMode() == FetchException.FetchExceptionMode.PERMANENT_REDIRECT) {
				GetTopKeyCallback cb = new GetTopKeyCallback(exception.newURI);
				try {
					cb.start();
					pendingOperations.remove(this);
				} catch (FetchException e) {
					fail(exception);
				}
			} else {
				fail(exception);
			}
		}

		@Override
		public void onSuccess(FetchResult result, ClientGetter state) {
			Bucket bucket = result.asBucket();

			Path topKeyPath = repository.getTopKeyPath(uri);

			if (topKeyPath == null) {
				// FIXME: error handling
				return;
			}

			try (BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(topKeyPath.toFile()))) {
				Utilities.copyStream(bucket.getInputStream(), out);
			} catch (FileNotFoundException e) {
				fail(e);
				return;
				// FIXME
			} catch (IOException e) {
				fail(e);
				return;
				// FIXME
			} finally {
				bucket.free();
			}

			try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(topKeyPath.toFile())))) {
				top = new TopKey(in);

				for (Update update : top.getUpdates()) {
					for (ClientCHK chk : update.getCHKs()) {
						fetchBundle(chk);
					}
				}

				finish();
			} catch (FetchException e) {
				fail(e);
			} catch (InvalidMagicNumberException e) {
				fail(e);
			} catch (IOException e) {
				fail(e);
			}
		}
	}

	public class GetBundleCallback extends RepositoryGetCallback implements ClientGetCallback {
		public GetBundleCallback(FreenetURI uri) { super(uri); }
		public String getType() { return "Bundle"; }

		public void onSuccess(FetchResult result, ClientGetter state) {
			Bucket bucket = result.asBucket();

			try {
				DataInputStream in = new DataInputStream(bucket.getInputStream());

				ClientCHK chk = new ClientCHK(uri);
				Path bundlePath = repository.bundlePath(chk);

				File tempBundle = repository.tempBundlePath();

				if (tempBundle == null) {
					fail(new Exception("Failed to create temporary file"));
					return;
				}

				try (BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(tempBundle))) {
					Utilities.copyStream(bucket.getInputStream(), out);
				} catch (FileNotFoundException e) {
					fail(e);
					return;
					// FIXME
				} catch (IOException e) {
					fail(e);
					return;
					// FIXME
				} finally {
					bucket.free();
				}

				if (!tempBundle.renameTo(bundlePath.toFile())) {
					fail(new Exception("Failed to rename temporary file to " + bundlePath.toString()));
					return;
				} else {

				}

				processBundle(bundlePath);

				finish();
			} catch (IOException e) {
				fail(e);
			} finally {
				bucket.free();
			}
		}
	}

	public boolean topKeyFetched() { return top != null; }

	public double getCompletionPercentage() throws ArithmeticException {
		return completedOperations.size() / (pendingOperations.size() + completedOperations.size());
	}

	public void fetchTopKey(FreenetURI uri) throws FetchException {
		Logger.debug(this, "Fetching top key at " + uri.toString());
		RepositoryGetCallback callback = new GetTopKeyCallback(uri);
		callback.start();
	}

	public void fetchBundle(FreenetURI uri) throws FetchException {
		Logger.debug(this, "Fetching bundle at " + uri.toString());
		RepositoryGetCallback callback = new GetBundleCallback(uri);
		callback.start();
	}

	public void processBundle(Path bundlePath) {
		try (DataInputStream in = new DataInputStream(new FileInputStream(bundlePath.toFile()))) {
			Bundle bundle = new Bundle(in);
			bundles.add(bundle);

			synchronized (tree) {
				for (NodeId root : bundle.changelogRoots()) {
					for (NodeId head : bundle.changelogHeads()) {
						tree.addEdge(root, head);
					}
				}
			}
		} catch (FileNotFoundException e) {
			// FIXME: something very seriously is wrong
		} catch (IOException e) {
			// FIXME: what should we do?
		}
	}

	// FIXME: allow more urgent priorities?
	public void fetchBundle(ClientCHK chk) throws FetchException {
		if (repository.hasBundle(chk)) {
			bundlePaths.add(repository.bundlePath(chk));
		} else {
			fetchBundle(chk.getURI());
		}
	}
}
