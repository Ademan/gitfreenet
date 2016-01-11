package gitfreenet.mercurial;

import java.util.ArrayList;
import java.util.Collections;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;

import java.io.IOException;
import java.io.EOFException;

import gitfreenet.Utilities;

public class RevisionChunk implements Revision<NodeId> {
	public final NodeId node;
	public final NodeId[] parents;
	public final NodeId changeset;
	public final RevisionDiff[] revisionData;

	public static final int FIXED_SIZE = 84; // Minimum size of chunk

	public RevisionChunk(DataInputStream in) throws IOException {
		this(in, in.readInt());
	}
	public RevisionChunk(DataInputStream in, int length) throws IOException {
		node = new NodeId(in);
		NodeId parent1 = new NodeId(in);
		NodeId parent2 = new NodeId(in);

		parents = new NodeId[]{parent1, parent2};

		changeset = new NodeId(in);

		byte[] data = new byte[length - FIXED_SIZE];
		in.readFully(data);

		DataInputStream revisionDataStream = Utilities.dataInputStream(data);

		ArrayList<RevisionDiff> revisionData = new ArrayList<RevisionDiff>();
		while (true) {
			try {
				revisionData.add(new RevisionDiff(revisionDataStream));
			} catch (EOFException e) {
				break;
			}
		}
		// FIXME: do we actually want to sort them, or must we apply them in the
		// order we receive them?
		Collections.sort(revisionData, new RevisionDiff.Comparator());
		this.revisionData = revisionData.toArray(new RevisionDiff[revisionData.size()]);
	}

	public static RevisionChunk read(DataInputStream in) throws IOException {
		int chunkLength = in.readInt();
		if (chunkLength <= 4) {
			return null;
		}
		return new RevisionChunk(in, chunkLength);
	}

	public RevisionChunk(NodeId node, NodeId[] parents, NodeId changeset, RevisionDiff[] revisionData) {
		this.node = node;
		this.parents = parents;
		this.changeset = changeset;
		this.revisionData = revisionData;
	}

	public ArrayList<NodeId> getParents() {
		ArrayList<NodeId> parents = new ArrayList<NodeId>();
		for (int i = 0; i < this.parents.length; i++) {
			if (!this.parents[i].equals(NodeId.NULL)) {
				parents.add(this.parents[i]);
			}
		}
		return parents;
	}

	// XXX: Assumes revisions are in-order
	// Ensures all revisions are non-overlapping.
	public boolean validChanges() {
		if (revisionData.length < 1) {
			return true;
		}
		for (int i = 1; i < revisionData.length; i++) {
			RevisionDiff a = revisionData[i-1];
			RevisionDiff b = revisionData[i];
			// If there exists a deterministic way to apply multiple diffs with the same
			// start, I'm unaware of it. For now just assume it's invalid, I think
			// that's safe enough.
			if (a.start == b.start) {
				return false;
			} else if (b.end <= a.end) {
				return false;
			}
		}
		return true;
	}

	public void write(DataOutputStream out) throws IOException {
		int revisionDataLength = 0;

		for (int i = 0; i < revisionData.length; i++) {
			revisionDataLength += revisionData[i].length();
		}
		
		out.writeInt(FIXED_SIZE + revisionDataLength);

		node.write(out);
		parents[0].write(out);
		parents[1].write(out);
		changeset.write(out);

		for (int i = 0; i < revisionData.length; i++) {
			revisionData[i].write(out);
		}
	}
	
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append(node);
		sb.append(':');
		sb.append(parents[0]);
		sb.append(',');
		sb.append(parents[1]);
		sb.append(':');
		sb.append(changeset);

		if (revisionData.length > 0) {
			sb.append(revisionData[0]);
		}
		for (int i = 1; i < revisionData.length; i++) {
			sb.append(',');
			sb.append(revisionData[i]);
		}

		return sb.toString();
	}

	/**
	 * @return The size of this diff's payload minus the number of bytes that would be removed
	 */
	@Override
	public int sizeChange() {
		int size = 0;
		for (RevisionDiff diff : revisionData) {
			size += diff.sizeChange();
		}
		return size;
	}

	public static class IncompleteReadException extends IOException {}

	// FIXME: Must determine if start,end are in bytes or characters. Assuming
	//        bytes for now
	// FIXME: What do we do if we get diffs with the same start points? Or diffs
	//        that claim to affect the same area, or enclose another? Must be
	//        robust, trust nobody.
	@Override
	public void apply(InputStream in, OutputStream out) throws IOException {
		int sourceStart = 0;
		int sourceOffset = 0;
		for (RevisionDiff diff : revisionData) {
			int sourceLen = diff.start - sourceOffset;
			if (sourceLen > 0) {
				// FIXME: profile and possibly cache chunk across iterations
				byte[] chunk = new byte[sourceLen];
				int bytesRead = in.read(chunk);

				if (bytesRead < sourceLen) {
					throw new IncompleteReadException();
				}

				sourceOffset += sourceLen;

				out.write(chunk);
			}

			out.write(diff.data);
			in.skip(diff.end - diff.start);

			sourceOffset += diff.end - diff.start;
		}

		// Copy the rest of in to out
		// FIXME: java have some builtin util for this?
		byte[] buffer = new byte[4096];
		for (int bytesRead = in.read(buffer);
		     bytesRead > 0; bytesRead = in.read(buffer)) {
			out.write(buffer, 0, bytesRead);
		}
	}

	public boolean isMerge() {
		if (NodeId.NULL.equals(parents[0]) || NodeId.NULL.equals(parents[1])) {
			return false;
		} else {
			return true;
		}
	}

	public NodeId getPrimaryParent() {
		return parents[0];
	}
}
