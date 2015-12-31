package gitfreenet.infocalypse;

import java.util.List;
import java.util.ArrayList;
import java.util.Collection;

import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.IOException;

import freenet.keys.ClientCHK;

import gitfreenet.mercurial.NodeId;

public class Update {
	ArrayList<ClientCHK> chks = new ArrayList<ClientCHK>();
	ArrayList<NodeId> parents = new ArrayList<NodeId>();
	ArrayList<NodeId> heads = new ArrayList<NodeId>();

	public Update(DataInputStream in) throws IOException {
		long bundleLength = in.readLong();

		int flags = in.readUnsignedByte();
		int parentCount = in.readUnsignedByte();
		int headCount = in.readUnsignedByte();
		int chkCount = in.readUnsignedByte();

		parents.ensureCapacity(parentCount);
		for (int i = 0; i < parentCount; i++) {
			parents.add(new NodeId(in));
		}

		heads.ensureCapacity(headCount);
		for (int i = 0; i < headCount; i++) {
			heads.add(new NodeId(in));
		}

		chks.ensureCapacity(chkCount);
		for (int i = 0; i < chkCount; i++) {
			chks.add(new ClientCHK(in));
		}
	}

	public List<ClientCHK> getBundles() {
		return chks;
	}

	public Collection<ClientCHK> getCHKs() {
		return (Collection<ClientCHK>)chks.clone();
	}

	public void print() {
		System.out.println("Parents:");
		for (int i = 0; i < parents.size(); i++) {
			System.out.println("\t: " + parents.get(i).toString());
		}
		System.out.println("Heads:");
		for (int i = 0; i < heads.size(); i++) {
			System.out.println("\t: " + heads.get(i).toString());
		}
		System.out.println("CHKs:");
		for (int i = 0; i < chks.size(); i++) {
			System.out.println("\t: " + chks.get(i).getURI().toString());
		}
	}
}
