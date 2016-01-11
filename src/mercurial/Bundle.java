package gitfreenet.mercurial;

import gitfreenet.Utilities;

import java.util.Arrays;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;

import java.util.zip.InflaterInputStream;
import java.util.HashMap;
import java.util.Set;
import java.io.ByteArrayInputStream;

import java.util.Map;

import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;

public class Bundle {
	public static final byte[] magic;
	public static final byte[] magicVersion1;
	public static final byte[] magicUncompressed;
	public static final byte[] magicBZ2;
	public static final byte[] magicDEFLATE;
    static {
		magic = Utilities.tryEncodeASCII("HG");
		magicVersion1 = Utilities.tryEncodeASCII("10");
		magicUncompressed = Utilities.tryEncodeASCII("UN");
		magicBZ2 = Utilities.tryEncodeASCII("BZ");
		magicDEFLATE = Utilities.tryEncodeASCII("GZ");
    }

	HashMap<String, RevisionTree> files = new HashMap<String, RevisionTree>();
	RevisionTree manifest = new RevisionTree();
	RevisionTree changelog = new RevisionTree();

	// TODO: sane exception parent?
	public static class InvalidMagicNumberException extends IOException {
		public InvalidMagicNumberException(String s) {super(s);}
	} 

	public Bundle() {}

	// FIXME: if you patch upstream you can change this back to DataInput
	public Bundle(DataInputStream in) throws IOException {
		byte[] magicStart = new byte[2];
		byte[] magicVersion = new byte[2];
		byte[] magicCompression = new byte[2];
		
		in.readFully(magicStart);
		if (!Arrays.equals(magicStart, magic)) {
			throw new InvalidMagicNumberException("Invalid start to Bundle magic number, expected \"HG\"");
		}

		in.readFully(magicVersion);
		if (!Arrays.equals(magicVersion, magicVersion1)) {
			throw new InvalidMagicNumberException("Invalid Bundle version, expected \"10\"");
		}

		in.readFully(magicCompression);
		if (Arrays.equals(magicCompression, magicUncompressed)) {
			throw new UnsupportedOperationException("Don't currently support uncompressed data.");
		} else if (Arrays.equals(magicCompression, magicDEFLATE)) {
			in = new DataInputStream(new InflaterInputStream(in)); 
		} else if (Arrays.equals(magicCompression, magicBZ2)) {
			// FIXME: Wrap in buffered reader rather than this solution?
			in = new DataInputStream(new BZip2CompressorInputStream(Utilities.prependBytes(magicBZ2, in)));
		} else {
			throw new InvalidMagicNumberException("Invalid Bundle compression, expected either UN, BZ, GZ");
		}

		for (RevisionChunk chunk = RevisionChunk.read(in);
		     chunk != null; chunk = RevisionChunk.read(in)) {
			changelog.add(chunk);
		}
		for (RevisionChunk chunk = RevisionChunk.read(in);
		     chunk != null; chunk = RevisionChunk.read(in)) {
			manifest.add(chunk);
		}
		for (byte[] pathChunk = readChunk(in);
		     pathChunk != null; pathChunk = readChunk(in)) {
			String path = new String(pathChunk, "UTF-8");
			RevisionTree tree = RevisionTree.read(in);

			files.put(path, tree);
		}
	}

	public void addAll(Bundle bundle) {
		manifest.addAll(bundle.manifest);
		changelog.addAll(bundle.changelog);
		// FIXME: Seems suspiciously like we're building a set that we then
		//        iterate over. Check if java does something smarter
		for (Map.Entry<String, RevisionTree> entry : bundle.files.entrySet()) {
			String path = entry.getKey();
			if (files.containsKey(path)) {
				files.get(path).addAll(entry.getValue());
			} else {
				RevisionTree fileTree = new RevisionTree();
				fileTree.addAll(entry.getValue());
				files.put(path, fileTree);
			}
		}
	}

	public Set<NodeId> getManifestNodes() {
		return manifest.ids();
	}

	public RevisionChunk getManifestRevision(NodeId node) {
		return manifest.getChunk(node);
	}

	public Set<NodeId> changelogIds() {
		return changelog.ids();
	}

	public Set<NodeId> changelogRoots() {
		return changelog.getRoots();
	}

	public Set<NodeId> changelogHeads() {
		return changelog.getHeads();
	}

	public void manifestForId(NodeId id) throws MissingNodeException {
		// FIXME: gross
		ByteArrayInputStream s = new ByteArrayInputStream(new byte[1]);
		try {
			byte[] buf = new byte[1];
			s.read(buf);
			
			System.out.println("Manifest for id: " + id);
			manifest.apply(NodeId.NULL, manifest.getRoots().iterator().next(), s, System.out, 4096);
		} catch (IOException e) {
		}
	}

	public static byte[] readChunk(DataInputStream in) throws IOException {
		int chunkLength = in.readInt();
		if (chunkLength <= 4) {
			return null;
		}
		byte[] chunk = new byte[chunkLength-4];
		in.readFully(chunk);
		return chunk;
	}

	public static void main(String argv[]) {
		Bundle result = new Bundle();
        for (int i = 0; i < argv.length; i++) {
			System.out.println("Attempting to open \"" + argv[i] + "\"");
            DataInputStream in;
            try {
                in = new DataInputStream(new BufferedInputStream(new FileInputStream(argv[i])));
            } catch (FileNotFoundException e) {
                System.err.println("Can't find file " + argv[i]);
				in = null;
				System.exit(-1);
            }
            try {
                Bundle bundle = new Bundle(in);
				result.addAll(bundle);
			} catch(InvalidMagicNumberException e) {
                System.err.println(e.getMessage());
				System.exit(-1);
            } catch (IOException e) {
				System.err.println(argv[i] + ": " + e.getMessage());
				e.printStackTrace();
				System.exit(-1);
            }
        }

		for (NodeId node : result.manifest.ids()) {
			System.out.println("Node: " + node);
		}
		
		try {
			result.manifestForId(Utilities.one(result.manifest.getHeads()));
		} catch(MissingNodeException e) {
			System.err.println("Couldn't find node: " + e.getMessage());
		} catch(Utilities.UnpackException e) {
			e.printStackTrace();
		}

		System.exit(0);
	}
}
