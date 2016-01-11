package gitfreenet.mercurial;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;

import java.io.DataInputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;

import java.util.Iterator;

import gitfreenet.Utilities;

// FIXME: make generic on NodeId type?
public class RevisionTree {
	private HashMap<NodeId, RevisionChunk> revisions = new HashMap<NodeId, RevisionChunk>();
	private HashSet<NodeId> roots = new HashSet<NodeId>();
	private HashSet<NodeId> heads = new HashSet<NodeId>();
	private HashSet<NodeId> nonRoot = new HashSet<NodeId>();
	private HashMap<NodeId, ArrayList<NodeId>> edges = new HashMap<NodeId, ArrayList<NodeId>>();

	public RevisionTree() {}
	public RevisionTree(Iterator<RevisionChunk> chunks) {
	    addChunks(chunks);
	}
	public void addChunks(Iterator<RevisionChunk> chunks) {
	    for (RevisionChunk chunk: revisions.values()) {
		    add(chunk);
	    }
	}

	// FIXME: should this be a constructor?
	// I think static initializer makes more sense but what's idiomatic?
	public static RevisionTree read(DataInputStream in) throws IOException {
		RevisionTree tree = new RevisionTree();
		for (RevisionChunk chunk = RevisionChunk.read(in);
		     chunk != null; chunk = RevisionChunk.read(in)) {
			tree.add(chunk);
		}
		return tree;
	}

	public void addAll(RevisionTree tree) {
		for (RevisionChunk chunk : tree.revisions.values()) {
			add(chunk);
		}	
	}

	public void addEdge(NodeId parent, NodeId child) {
		ArrayList<NodeId> children;
		if (edges.containsKey(parent)) {
			children = edges.get(parent);
		} else {
			children = new ArrayList<NodeId>();
			edges.put(parent, children);
		}

		children.add(child);

		if (!edges.containsKey(child)) {
			heads.add(child);
		}

		if (!nonRoot.contains(parent)) {
			roots.add(parent);
		}

		nonRoot.add(child);
		heads.remove(parent);
		roots.remove(child);
	}

	// FIXME: Make iterator instead? Can't throw exceptions though
	public ArrayList<NodeId> getPath(NodeId from, NodeId to) throws MissingNodeException {
		// Traverse up from newest to oldest node following primary links
		ArrayList<NodeId> nodes = new ArrayList<NodeId>();
		nodes.add(to);
		NodeId current = to;
		while (from != current) {
			Revision<NodeId> rev = revisions.get(current);
			if (rev == null) {
				throw new MissingNodeException(current);
			}
			current = rev.getPrimaryParent();
			nodes.add(current);
		}
		return nodes;
	}

	public void apply(NodeId from, NodeId to, InputStream in, OutputStream out, int pipeInitialSize) throws IOException, MissingNodeException {
		ArrayList<NodeId> path = getPath(from, to);
		for (NodeId id : path) {
			PipedOutputStream intermediateOutput = new PipedOutputStream();

			RevisionChunk revision = revisions.get(id);
			revision.apply(in, intermediateOutput);
			
			in = new PipedInputStream(intermediateOutput, pipeInitialSize + revision.sizeChange());
		}

		Utilities.copyStream(in, out);
	}
	public Set<NodeId> getHeads() {
		return (Set<NodeId>)heads.clone();
	}

	public Set<NodeId> getRoots() {
		return (Set<NodeId>)roots.clone();
	}

	public Set<NodeId> ids() {
		return revisions.keySet();
	}

	public RevisionChunk getChunk(NodeId id) {
		return revisions.get(id);
	}

	public void add(RevisionChunk chunk) {
		for (NodeId parent : chunk.getParents()) {
			addEdge(parent, chunk.node);
		}
		revisions.put(chunk.node, chunk);
		// NOTE: we assume the diffs are applied against the first parent, this is unverified currently
		/*if (!chunk.parents[0].equals(NodeId.NULL)) {
			addEdge(chunk.parents[0], chunk.node);
		}*/
	}
}
