package gitfreenet;

import java.util.Set;

import java.io.Reader;
import java.io.FileReader;
import java.io.BufferedReader;

import java.io.Writer;
import java.io.FileWriter;
import java.io.BufferedWriter;

import java.io.InputStream;
import java.io.OutputStream;

import java.io.SequenceInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;

import java.io.ByteArrayInputStream;

import java.io.File;

import java.io.IOException;
import java.io.FileNotFoundException;
import java.io.UnsupportedEncodingException;

import java.nio.file.Path;

public class Utilities {
	public static byte[] tryEncodeASCII(String s) {
		return tryEncode(s, "US-ASCII");
	}
	public static byte[] tryEncode(String s, String encoding) {
        try {
            return s.getBytes(encoding);
        } catch (UnsupportedEncodingException e) {
            System.err.println("Unable to encode \"" + s + "\" in encoding " + encoding + " Critical Error.");
			throw new ExceptionInInitializerError(e);
        }
	}

	public static DataInputStream dataInputStream(byte[] data) {
		return new DataInputStream(new ByteArrayInputStream(data));
	}

	public static InputStream prependBytes(byte[] data, InputStream s) {
		return new SequenceInputStream(new ByteArrayInputStream(data), s);
	}

	public static void copyStream(InputStream in, OutputStream out, int bufferSize) throws IOException {
		byte[] copyBuffer = new byte[bufferSize];
		int readBytes = 1;
		while (readBytes > 0) {
			readBytes = in.read(copyBuffer);
			out.write(copyBuffer, 0, readBytes);
		}
	}
	public static void copyStream(InputStream in, OutputStream out) throws IOException {
		copyStream(in, out, 4096);
	}
	public static Reader reader(Path p) throws FileNotFoundException {
		return reader(p.toFile());
	}
	public static Reader reader(File file) throws FileNotFoundException {
		return new BufferedReader(new FileReader(file));
	}
	public static Writer writer(File file) throws IOException, SecurityException {
		return new BufferedWriter(new FileWriter(file));
	}
	public static String read(Path p) throws IOException, FileNotFoundException {
		return read(p.toFile());
	}
	public static String read(File file) throws IOException, FileNotFoundException {
		final int BUFFER_LENGTH = 2048; // XXX: pick a better number?

		StringBuilder sb = new StringBuilder();
		char[] cb = new char[BUFFER_LENGTH];
		try (Reader r = reader(file)) {
			int readCount;
			while ((readCount = r.read(cb, 0, BUFFER_LENGTH)) > 0) {
				sb.append(cb, 0, readCount);
			}
		}

		return sb.toString();
	}
	public static void write(File file, String data) {
		
	}

	public static class UnpackException extends Exception {
		public final int found;
		public final int expected;
		public UnpackException(int found, int expected) {
			this.found = found;
			this.expected = expected;
		}
		public String toString() {
			return "Expected to unpack " + expected + " items but found " + found + " items";
		}
	}
	public static <T> T one(Set<T> set) throws UnpackException {
		if (set.size() != 1) {
			throw new UnpackException(set.size(), 1);
		} else {
			return set.iterator().next();
		}
	}
}
