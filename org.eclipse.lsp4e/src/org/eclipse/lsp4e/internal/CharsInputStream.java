/*******************************************************************************
 * Copyright (c) 2024 Sebastian Thomschke and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 * Sebastian Thomschke - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.internal;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.util.Objects;
import java.util.function.IntSupplier;

public class CharsInputStream extends InputStream {

	/**
	 * Functional interface for supplying characters at a specified index.
	 * Implementations can define how characters are fetched.
	 */
	@FunctionalInterface
	public interface CharsSupplier {
		char charAt(int index) throws Exception;
	}

	private enum EncoderState {
		/**
		 * The {@link #encoder} is actively encoding characters into bytes. This is the
		 * initial state of the encoder.
		 */
		ENCODING, //

		/**
		 * The {@link #encoder} has finished processing all characters and is now
		 * flushing any remaining bytes in its internal buffer.
		 */
		FLUSHING, //

		/**
		 * The {@link #encoder} has completed both the encoding and flushing processes.
		 * No more data is left to be read from the encoder.
		 */
		DONE
	}

	public static final char UNICODE_REPLACEMENT_CHAR = '\uFFFD';

	/** 512 surrogate character pairs */
	private static final int DEFAULT_BUFFER_SIZE = 512;
	private static final int EOF = -1;

	private final int bufferSize;
	private final CharBuffer charBuffer;
	private final ByteBuffer byteBuffer;
	private final CharsetEncoder encoder;
	private EncoderState encoderState = EncoderState.ENCODING;

	private int charIndex = 0;
	private final CharsSupplier chars;
	private final IntSupplier charsLength;

	public CharsInputStream(final CharSequence chars) {
		this(chars, Charset.defaultCharset());
	}

	public CharsInputStream(final CharSequence chars, final Charset charset) {
		this(chars, charset, DEFAULT_BUFFER_SIZE);
	}

	public CharsInputStream(final CharSequence chars, final Charset charset, final int bufferSize) {
		this(chars::charAt, chars::length, charset, bufferSize);
	}

	public CharsInputStream(final CharsSupplier chars, final IntSupplier charsLength) {
		this(chars, charsLength, Charset.defaultCharset());
	}

	/**
	 * @param chars
	 *            function to access indexed chars.
	 * @param charsLength
	 *            function to get the number of indexed chars provided by the
	 *            <code>chars</code> parameter.
	 */
	public CharsInputStream(final CharsSupplier chars, final IntSupplier charsLength, final Charset charset) {
		this(chars, charsLength, charset, DEFAULT_BUFFER_SIZE);
	}

	/**
	 * @param chars
	 *            function to access indexed chars.
	 * @param charsLength
	 *            function to get the number of indexed chars provided by the
	 *            <code>chars</code> parameter.
	 * @param bufferSize
	 *            number of surrogate character pairs to encode at once.
	 */
	public CharsInputStream(final CharsSupplier chars, final IntSupplier charsLength, final Charset charset,
			final int bufferSize) {
		if (bufferSize < 1)
			throw new IllegalArgumentException("[bufferSize] must be 1 or larger"); //$NON-NLS-1$
		encoder = charset.newEncoder();

		this.bufferSize = bufferSize;
		charBuffer = CharBuffer.allocate(bufferSize * 2); // buffer for 2 chars (high/low surrogate)
		byteBuffer = ByteBuffer.allocate(bufferSize * 4); // buffer for one UTF character (up to 4 bytes)
		byteBuffer.flip();
		charBuffer.flip();

		this.chars = chars;
		this.charsLength = charsLength;
	}

	@Override
	public int available() {
		final int remaining = byteBuffer.remaining();
		return remaining == 0 ? charsLength.getAsInt() - charIndex : remaining;
	}

	/**
	 * This method is called by {@link #refillByteBuffer()} to encode characters
	 * from the given {@link CharBuffer} into bytes and stores them in the
	 * {@link #byteBuffer}.
	 *
	 * <p>
	 * The method can be used either to encode characters in the middle of input
	 * (with {@code isEndOfInput=false}) or to finalize the encoding process at the
	 * end of input (with {@code isEndOfInput=true}).
	 * </p>
	 *
	 * @param in
	 *            the {@link CharBuffer} containing characters to encode.
	 * @param isEndOfInput
	 *            if {@code true}, signals that no more input will be provided,
	 *            allowing the encoder to complete its final encoding steps.
	 */
	private void encodeChars(final CharBuffer in, final boolean isEndOfInput) throws CharacterCodingException {
		byteBuffer.clear();
		final CoderResult result = encoder.encode(in, byteBuffer, isEndOfInput);
		byteBuffer.flip();
		if (result.isError()) {
			result.throwException();
		}
	}

	/**
	 * Flushes the remaining bytes from the encoder to the {@link #byteBuffer}.
	 *
	 * <p>
	 * This method is called by {@link #refillByteBuffer()} when all characters have
	 * been processed, and the encoder needs to output any remaining bytes. It
	 * transitions the encoder state from {@link EncoderState#ENCODING} to
	 * {@link EncoderState#FLUSHING}, and eventually to {@link EncoderState#DONE}
	 * once all bytes have been flushed.
	 * </p>
	 *
	 * @return {@code true} if there are still bytes left in the {@link #byteBuffer}
	 *         after flushing, or if the encoder still has more bytes to flush;
	 *         {@code false} if the flush is complete and no bytes remain.
	 */
	private boolean flushEncoder() throws IOException {
		if (encoderState == EncoderState.DONE)
			return false;

		if (encoderState == EncoderState.ENCODING) {
			encoderState = EncoderState.FLUSHING;
		}

		// flush
		byteBuffer.clear();
		final CoderResult result = encoder.flush(byteBuffer);
		byteBuffer.flip();

		if (result.isOverflow()) {
			// the byteBuffer has been filled, but there are more bytes to be flushed.
			// after reading all available bytes from byteBuffer, flushEncoder() needs to
			// be called again to process the remaining data.
			return true;
		}

		if (result.isError()) {
			result.throwException();
		}

		encoderState = EncoderState.DONE;
		return byteBuffer.hasRemaining();
	}

	public Charset getCharset() {
		return encoder.charset();
	}

	@Override
	public int read() throws IOException {
		if (!byteBuffer.hasRemaining() && !refillByteBuffer())
			return EOF;
		return byteBuffer.get() & 0xFF; // next byte as an unsigned integer (0 to 255)
	}

	@Override
	public int read(final byte[] buf, final int off, final int bytesToRead) throws IOException {
		Objects.checkFromIndexSize(off, bytesToRead, buf.length);
		if (bytesToRead == 0)
			return 0;

		int bytesRead = 0;
		int bytesReadable = byteBuffer.remaining();

		while (bytesRead < bytesToRead) {
			if (bytesReadable == 0) {
				if (refillByteBuffer()) {
					bytesReadable = byteBuffer.remaining();
				} else
					return bytesRead == 0 ? EOF : bytesRead;
			}

			final int bytesToReadNow = Math.min(bytesToRead - bytesRead, bytesReadable);
			byteBuffer.get(buf, off + bytesRead, bytesToReadNow);
			bytesRead += bytesToReadNow;
			bytesReadable -= bytesToReadNow;
		}

		return bytesRead;
	}

	/**
	 * Refills the {@link #byteBuffer} by reading characters from the character
	 * supplier, encoding them, and storing the resulting bytes into the
	 * {@link #byteBuffer}.
	 *
	 * @return {@code true} if the buffer was successfully refilled and has bytes
	 *         available for reading, {@code false} if the end of the stream is
	 *         reached and there are no more bytes to read.
	 */
	private boolean refillByteBuffer() throws IOException {
		if (encoderState == EncoderState.DONE)
			return false;

		if (encoderState == EncoderState.FLUSHING)
			return flushEncoder();

		final int charsLen = charsLength.getAsInt();

		// if EOF is reached transition to flushing
		if (charIndex >= charsLen) {
			// finalize encoding before switching to flushing
			encodeChars(CharBuffer.allocate(0), true /* signal EOF */);
			return flushEncoder();
		}

		try {
			charBuffer.clear();
			for (int i = 0; i < bufferSize && charIndex < charsLen; i++) {
				final char nextChar = chars.charAt(charIndex++);
				if (Character.isHighSurrogate(nextChar)) { // handle surrogate pairs
					if (charIndex < charsLen) {
						final char lowSurrogate = chars.charAt(charIndex);
						if (Character.isLowSurrogate(lowSurrogate)) {
							charIndex++;
							charBuffer.put(nextChar);
							charBuffer.put(lowSurrogate);
						} else {
							// missing low surrogate - fallback to replacement character
							charBuffer.put(UNICODE_REPLACEMENT_CHAR);
						}
					} else {
						// missing low surrogate - fallback to replacement character
						charBuffer.put(UNICODE_REPLACEMENT_CHAR);
						break;
					}
				} else {
					charBuffer.put(nextChar);
				}
			}
			charBuffer.flip();

			// encode chars into bytes
			encodeChars(charBuffer, false);
		} catch (final Exception ex) {
			throw new IOException(ex);
		}

		return true;
	}
}
