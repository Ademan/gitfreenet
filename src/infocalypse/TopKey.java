package gitfreenet.infocalypse;

import java.io.DataInputStream;
import java.io.DataOutput;

import java.io.DataInputStream;
import java.io.BufferedInputStream;
import java.io.FileInputStream;

import java.util.Collection;
import java.util.ArrayList;
import java.util.Arrays;

import java.io.IOException;
import java.io.FileNotFoundException;

import freenet.keys.ClientCHK;

import gitfreenet.Utilities;

public class TopKey {
    public static final byte[] knownMagic;
    static {
		knownMagic = Utilities.tryEncodeASCII("HGINF200");
    }

    ArrayList<ClientCHK> chks = new ArrayList<ClientCHK>();
    ArrayList<Update> updates = new ArrayList<Update>();
    public TopKey(DataInputStream in) throws IOException {
        byte[] magic = new byte[8];
        int bytesRead;
        
        in.readFully(magic);

        if (!Arrays.equals(magic, knownMagic)) {
            throw new InvalidMagicNumberException(magic);
        }

        int salt = in.readUnsignedByte();
        int graphChks = in.readUnsignedByte();
        int numUpdates = in.readUnsignedByte();

        chks.ensureCapacity(graphChks);
        for (int i = 0; i < graphChks; i++) {
            chks.add(new ClientCHK(in));
        }

        updates.ensureCapacity(numUpdates);
        for (int i = 0; i < numUpdates; i++) {
            updates.add(new Update(in));
        }
    }

	public Collection<Update> getUpdates() { return (Collection<Update>)updates.clone(); }

    void print() {
		System.out.println("CHKs:");
		for (int i = 0; i < chks.size(); i++) {
			System.out.println("\t: " + chks.get(i).getURI().toString());
		}
		System.out.println("Updates");
		System.out.println("=======");
		System.out.println();
		for (int i = 0; i < updates.size(); i++) {
			updates.get(i).print();
		}
    }

    public static void main(String[] argv) {
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
                TopKey top = new TopKey(in);
				top.print();
            } catch (IOException e) {
                System.err.println("Error reading from " + argv[i]);
				System.exit(-1);
            }
        }
		System.exit(0);
    }
}

