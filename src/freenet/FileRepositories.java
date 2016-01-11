package gitfreenet.freenet;

import java.io.File;
import java.io.FileFilter;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.io.OutputStream;

import java.io.FileInputStream;
import java.io.FileOutputStream;

import java.io.FileReader;

import java.io.IOException;
import java.io.FileNotFoundException;
import java.io.UnsupportedEncodingException;

import java.util.Random;

import java.net.URI;
import java.net.MalformedURLException;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import freenet.support.HexUtil;

import freenet.keys.FreenetURI;
import freenet.keys.ClientCHK;

import freenet.client.FetchException;

import java.nio.file.Path;

import gitfreenet.Utilities;

public class FileRepositories {
	public final Path root;
	public final Path bundles;

	private final Random fastWeakRandom;

	static final int SALT_SIZE = 512;
	// We salt meta strings before hashing them to avoid collisions
	private byte[] metaStrSalt;

	public static final String BUNDLESDIR = "bundles";
	public static final String TEMPFILEPATH = "temp";

	public FileRepositories(Path root, Random fastWeakRandom) {
		this.fastWeakRandom = fastWeakRandom;
		this.root = root;

		if (!root.toFile().isDirectory() && !root.toFile().mkdirs()) {
			throw new RuntimeException("Failed to create root \"" + root.toString() + "\"");
		}

		bundles = root.resolve(BUNDLESDIR);

		if (!bundles.toFile().isDirectory() && !bundles.toFile().mkdirs()) {
			throw new RuntimeException("Failed to create bundles directory \"" + bundles.toString() + "\"");
		}

		metaStrSalt = new byte[SALT_SIZE];
		
		try (InputStream in = new FileInputStream(root.resolve("salt").toFile())) {
			if (in.read(metaStrSalt) != SALT_SIZE) {
				// TODO: log warning( error?)
				metaStrSalt = initializeSaltFile();
			}
		} catch (IOException e) {
			// TODO: log warning( error?)
			metaStrSalt = initializeSaltFile();
		}
	}

	private byte[] initializeSaltFile() {
		byte[] salt = new byte[SALT_SIZE];
		fastWeakRandom.nextBytes(salt);

		try (OutputStream out = new FileOutputStream(root.resolve("salt").toFile())) {
			out.write(salt);
		} catch (IOException e) {
			// TODO: log warning
		}
		return salt;
	}

	public String repositoryDirectoryName(FreenetURI uri) throws UnsupportedEncodingException {
		final byte metaStrSeparator = 0x2F; // Forward-slash

		StringBuilder sb = new StringBuilder();
		sb.append(uri.getKeyType());
		sb.append('-');
		sb.append(HexUtil.bytesToHex(uri.getRoutingKey()));
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			// TODO: add salt

			if (uri.hasMetaStrings()) {
				for (String s : uri.getAllMetaStrings()) {
					digest.update(metaStrSeparator);
					digest.update(s.getBytes());
				}
				sb.append(HexUtil.bytesToHex(digest.digest()));
			}

			return sb.toString();
		} catch (NoSuchAlgorithmException e) {
			// FIXME: is this a good approach? We're boned in this case, plus this should never happen
			// docs guarantee presence of SHA-256
			//throw new RuntimeException(e);
			throw new RuntimeException("No such algorithm SHA-256");
		}
	}

	public RepositoryStore newRepository(FreenetURI repositoryURI) {
		// XXX: assumes we only have FileRepository	
		
		FreenetURI repositoryIdUri = repositoryURI.setSuggestedEdition(0);

		if (repositoryIdUri == null) {
			return null;
		}
		
		try {
			String repoName = repositoryDirectoryName(repositoryIdUri);

			FileRepository repository = FileRepository.initialize(this, root.resolve(repoName), repositoryURI);

			return repository;
		} catch (UnsupportedEncodingException e) {
			return null;
	 	} catch (IOException e) {
			return null;
		}
	}

	public Path repositoryPath(FreenetURI uri) throws SecurityException, UnsupportedEncodingException {
		return repositoryPath(uri, false);
	}

	public Path findRepositoryPath(FreenetURI uri) throws UnsupportedEncodingException {
		Path expectedPath = root.resolve(repositoryDirectoryName(uri));
		File dir = expectedPath.toFile();

		if (FileRepository.validate(expectedPath, uri)) {
			return expectedPath;
		} else if (dir.exists() && !dir.isDirectory()) {
			// FIXME: what to do?
			throw new RuntimeException("data directory in inconsistent state");
		} else {
			FileFilter directories = new FileFilter() {
				@Override
				public boolean accept(File f) {
					return f.isDirectory();
				}
			};
			// Search for desired respository's directory
			for (File candidateDirectory : root.toFile().listFiles(directories)) {
				// FIXME: delegate to validate()
				Path uriPath = candidateDirectory.toPath().resolve("uri"); 

				try {
					FreenetURI candidateUri = new FreenetURI(Utilities.read(uriPath));

					if (candidateUri.equals(uri)) {
						return candidateDirectory.toPath();
					}
				} catch (IOException e) {
				}
			}
			return null;
		}
	}

	public Path repositoryPath(FreenetURI uri, boolean create) throws SecurityException, UnsupportedEncodingException {
		Path foundPath = findRepositoryPath(uri);

		if (foundPath == null && !create) {
			return null;
		}

		try {
			Path expectedPath = root.resolve(repositoryDirectoryName(uri));
			FileRepository repo = FileRepository.initialize(this, expectedPath, uri);

			return expectedPath;
		} catch (IOException e) {
			return null;
		}
	}

	public boolean hasBundle(ClientCHK bundleCHK) {
		Path bundle = bundlePath(bundleCHK);

		return bundle.toFile().isFile();
	}

	// FIXME: throw exception instead of returning null?
	// would be better for automatic resource closing
	public InputStream getBundleInputStream(ClientCHK bundleCHK) throws FileNotFoundException {
		Path bundle = bundlePath(bundleCHK);

		return new BufferedInputStream(new FileInputStream(bundle.toFile()));
	}

	public Path bundlePath(ClientCHK bundleChk) {
		return bundles.resolve(bundleFilename(bundleChk));
	}

	public File tempFilePath(String prefix, String suffix) {
		File tempFileDir = root.resolve(TEMPFILEPATH).toFile(); 

		if (!tempFileDir.isDirectory() && !tempFileDir.mkdir()) {
			return null;
		} else {
			try {
				return File.createTempFile(prefix, suffix, tempFileDir);
			} catch (IOException e) {
				return null;
			}
		}
	}

	public String bundleFilename(ClientCHK bundleChk) {
		return HexUtil.bytesToHex(bundleChk.getRoutingKey())	+
		       "-" +
		       HexUtil.bytesToHex(bundleChk.getCryptoKey()) +
			   "-" +
			   HexUtil.bytesToHex(bundleChk.getExtra());
	}
}
