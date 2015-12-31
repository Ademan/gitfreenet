package gitfreenet.mercurial;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import javax.xml.bind.DatatypeConverter;

import java.nio.ByteBuffer;

import java.util.Arrays;

public class NodeId {
	public static class InvalidNodeIdException extends IOException {}

	public static final int SIZE = 20;
	public final byte[] bytes;

	public NodeId(byte[] bytes) throws IOException {
		if (bytes.length != SIZE) {
			throw new InvalidNodeIdException();
		}
		this.bytes = bytes;
	}

	public NodeId(DataInput in) throws IOException {
		bytes = new byte[SIZE];
		in.readFully(bytes);
	}

	public void write(DataOutput out) throws IOException {
		out.write(bytes);
	}

	public String toString() {
		return DatatypeConverter.printHexBinary(bytes);
	}

	@Override
	public int hashCode() {
		// FIXME: may be stupidly slow
		return Arrays.hashCode(bytes);
	}

	@Override
	public boolean equals(Object o) {
		if (o instanceof NodeId) {
			return Arrays.equals(bytes, ((NodeId)o).bytes);
		} else {
			return false;
		}
	}

	public static final NodeId NULL;
	static {
		try {
			NULL = new NodeId(new byte[20]); // XXX: supposedly a new byte array
	                                         //      must be all zeros
		} catch (IOException e) {
			throw new ExceptionInInitializerError(e);
		}
	}
}
