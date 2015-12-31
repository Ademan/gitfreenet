package gitfreenet.infocalypse;

import java.io.IOException;

public class InvalidMagicNumberException extends IOException {
	public final byte[] magicNumber;
	public InvalidMagicNumberException(byte[] foundMagicNumber) {
		this.magicNumber = foundMagicNumber;
	}

	// http://stackoverflow.com/questions/7487917/convert-byte-array-to-escaped-string
	// I could write my own... but why?
	public static String escape(byte[] data) {
		StringBuilder cbuf = new StringBuilder();
		for (byte b : data) {
		  if (b >= 0x20 && b <= 0x7e) {
			cbuf.append((char) b);
		  } else {
			cbuf.append(String.format("\\0x%02x", b & 0x255));
		  }
		}
		return cbuf.toString();
	}

	public String getMessage() {
		return "Found invalid magic number \"" + escape(magicNumber) + "\"";
	}
}
