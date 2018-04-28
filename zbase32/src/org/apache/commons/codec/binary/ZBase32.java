package org.apache.commons.codec.binary;

public class ZBase32 extends BaseNCodec {

    /**
     * BASE32 characters are 5 bits in length.
     * They are formed by taking a block of five octets to form a 40-bit string,
     * which is converted into eight BASE32 characters.
     */
    private static final int BITS_PER_ENCODED_BYTE = 5;
    private static final int BYTES_PER_ENCODED_BLOCK = 8;
    private static final int BYTES_PER_UNENCODED_BLOCK = 5;

    /**
     * Chunk separator per RFC 2045 section 2.1.
     *
     * @see <a href="http://www.ietf.org/rfc/rfc2045.txt">RFC 2045 section 2.1</a>
     */
    private static final byte[] CHUNK_SEPARATOR = {'\r', '\n'};

    /**
     * compute from scala code:
     * <pre>{@code
     * def fill(decodeTable: Array[Byte], encodeTable: String) = {
     *   encodeTable.zipWithIndex.foreach {
     *     case (c, i) => decodeTable(c) = i.toByte
     *   }
     * }
     * def toDecodeTable(encodeTable: String): Array[Byte] = {
     *   val t = Array.fill[Byte](128)(-1)
     *   fill(t, encodeTable.toLowerCase)
     *   fill(t, encodeTable.toUpperCase)
     *   t.reverse.dropWhile(_ == -1).reverse
     * }
     * def fmt[T](v: T): String = {
     *   var s = s"$v,"
     *   while(s.length < 4) s = " " + s
     *   s
     * }
     * def pretty[T](t: Array[T]) = t.zipWithIndex.foreach {
     *   case (v, i) =>
     *     print(fmt(v))
     *     if (i % 16 == 15) println()
     * }
     * val tbl = "ybndrfg8ejkmcpqxot1uwisza345h769"
     * pretty(toDecodeTable(tbl))
     * pretty(tbl.map(c => s"'$c'").toArray)
     * }</pre>
     */
    private static final byte[] decodeTable = {
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, 18, -1, 25, 26, 27, 30, 29,  7, 31, -1, -1, -1, -1, -1, -1,
            -1, 24,  1, 12,  3,  8,  5,  6, 28, 21,  9, 10, -1, 11,  2, 16,
            13, 14,  4, 22, 17, 19, -1, 20, 15,  0, 23, -1, -1, -1, -1, -1,
            -1, 24,  1, 12,  3,  8,  5,  6, 28, 21,  9, 10, -1, 11,  2, 16,
            13, 14,  4, 22, 17, 19, -1, 20, 15,  0, 23,
    };
    private static final byte[] encodeTable = {
            'y','b','n','d','r','f','g','8','e','j','k','m','c','p','q','x',
            'o','t','1','u','w','i','s','z','a','3','4','5','h','7','6','9',
    };

    /** Mask used to extract 5 bits, used when encoding Base32 bytes */
    private static final int MASK_5BITS = 0x1f;

    // The static final fields above are used for the original static byte[] methods on Base32.
    // The private member fields below are used with the new streaming approach, which requires
    // some state be preserved between calls of encode() and decode().

    /**
     * Place holder for the bytes we're dealing with for our based logic.
     * Bitwise operations store and extract the encoding or decoding from this variable.
     */

    /**
     * Convenience variable to help us determine when our buffer is going to run out of room and needs resizing.
     * <code>decodeSize = {@link #BYTES_PER_ENCODED_BLOCK} - 1 + lineSeparator.length;</code>
     */
    private final int decodeSize;

    /**
     * Convenience variable to help us determine when our buffer is going to run out of room and needs resizing.
     * <code>encodeSize = {@link #BYTES_PER_ENCODED_BLOCK} + lineSeparator.length;</code>
     */
    private final int encodeSize;

    /**
     * Line separator for encoding. Not used when decoding. Only used if lineLength &gt; 0.
     */
    private final byte[] lineSeparator;

    /**
     * Creates a Base32 codec used for decoding and encoding.
     * <p>
     * When encoding the line length is 0 (no chunking).
     * </p>
     */
    public ZBase32() {
        this(0, null, PAD_DEFAULT);
    }

    /**
     * Creates a Base32 codec used for decoding and encoding.
     * <p>
     * When encoding the line length is given in the constructor, the line separator is CRLF.
     * </p>
     *
     * @param lineLength
     *            Each line of encoded data will be at most of the given length (rounded down to nearest multiple of
     *            8). If lineLength &lt;= 0, then the output will not be divided into lines (chunks). Ignored when
     *            decoding.
     */
    public ZBase32(final int lineLength) {
        this(lineLength, CHUNK_SEPARATOR, PAD_DEFAULT);
    }

    /**
     * Creates a Base32 / Base32 Hex codec used for decoding and encoding.
     * <p>
     * When encoding the line length and line separator are given in the constructor.
     * </p>
     * <p>
     * Line lengths that aren't multiples of 8 will still essentially end up being multiples of 8 in the encoded data.
     * </p>
     *
     * @param lineLength
     *            Each line of encoded data will be at most of the given length (rounded down to nearest multiple of
     *            8). If lineLength &lt;= 0, then the output will not be divided into lines (chunks). Ignored when
     *            decoding.
     * @param lineSeparator
     *            Each line of encoded data will end with this sequence of bytes.
     * @throws IllegalArgumentException
     *             The provided lineSeparator included some Base32 characters. That's not going to work! Or the
     *             lineLength &gt; 0 and lineSeparator is null.
     */
    public ZBase32(final int lineLength, final byte[] lineSeparator) {
        this(lineLength, lineSeparator, PAD_DEFAULT);
    }

    /**
     * Creates a Base32 / Base32 Hex codec used for decoding and encoding.
     * <p>
     * When encoding the line length and line separator are given in the constructor.
     * </p>
     * <p>
     * Line lengths that aren't multiples of 8 will still essentially end up being multiples of 8 in the encoded data.
     * </p>
     *
     * @param lineLength
     *            Each line of encoded data will be at most of the given length (rounded down to nearest multiple of
     *            8). If lineLength &lt;= 0, then the output will not be divided into lines (chunks). Ignored when
     *            decoding.
     * @param lineSeparator
     *            Each line of encoded data will end with this sequence of bytes.
     * @param pad byte used as padding byte.
     * @throws IllegalArgumentException
     *             The provided lineSeparator included some Base32 characters. That's not going to work! Or the
     *             lineLength &gt; 0 and lineSeparator is null.
     */
    public ZBase32(final int lineLength, final byte[] lineSeparator, final byte pad) {
        super(BYTES_PER_UNENCODED_BLOCK, BYTES_PER_ENCODED_BLOCK, lineLength,
                lineSeparator == null ? 0 : lineSeparator.length, pad);
        if (lineLength > 0) {
            if (lineSeparator == null) {
                throw new IllegalArgumentException("lineLength " + lineLength + " > 0, but lineSeparator is null");
            }
            // Must be done after initializing the tables
            if (containsAlphabetOrPad(lineSeparator)) {
                final String sep = StringUtils.newStringUtf8(lineSeparator);
                throw new IllegalArgumentException("lineSeparator must not contain Base32 characters: [" + sep + "]");
            }
            this.encodeSize = BYTES_PER_ENCODED_BLOCK + lineSeparator.length;
            this.lineSeparator = new byte[lineSeparator.length];
            System.arraycopy(lineSeparator, 0, this.lineSeparator, 0, lineSeparator.length);
        } else {
            this.encodeSize = BYTES_PER_ENCODED_BLOCK;
            this.lineSeparator = null;
        }
        this.decodeSize = this.encodeSize - 1;

        if (isInAlphabet(pad) || isWhiteSpace(pad)) {
            throw new IllegalArgumentException("pad must not be in alphabet or whitespace");
        }
    }

    /**
     * <p>
     * Decodes all of the provided data, starting at inPos, for inAvail bytes. Should be called at least twice: once
     * with the data to decode, and once with inAvail set to "-1" to alert decoder that EOF has been reached. The "-1"
     * call is not necessary when decoding, but it doesn't hurt, either.
     * </p>
     * <p>
     * Ignores all non-Base32 characters. This is how chunked (e.g. 76 character) data is handled, since CR and LF are
     * silently ignored, but has implications for other bytes, too. This method subscribes to the garbage-in,
     * garbage-out philosophy: it will not check the provided data for validity.
     * </p>
     *
     * @param in
     *            byte[] array of ascii data to Base32 decode.
     * @param inPos
     *            Position to start reading data from.
     * @param inAvail
     *            Amount of bytes available from input for encoding.
     * @param context the context to be used
     *
     * Output is written to {@link Context#buffer} as 8-bit octets, using {@link Context#pos} as the buffer position
     */
    @Override
    void decode(final byte[] in, int inPos, final int inAvail, final Context context) {
        // package protected for access from I/O streams

        if (context.eof) {
            return;
        }
        if (inAvail < 0) {
            context.eof = true;
        }
        for (int i = 0; i < inAvail; i++) {
            final byte b = in[inPos++];
            if (b == pad) {
                // We're done.
                context.eof = true;
                break;
            }
            final byte[] buffer = ensureBufferSize(decodeSize, context);
            if (b >= 0 && b < this.decodeTable.length) {
                final int result = this.decodeTable[b];
                if (result >= 0) {
                    context.modulus = (context.modulus+1) % BYTES_PER_ENCODED_BLOCK;
                    // collect decoded bytes
                    context.lbitWorkArea = (context.lbitWorkArea << BITS_PER_ENCODED_BYTE) + result;
                    if (context.modulus == 0) { // we can output the 5 bytes
                        buffer[context.pos++] = (byte) ((context.lbitWorkArea >> 32) & MASK_8BITS);
                        buffer[context.pos++] = (byte) ((context.lbitWorkArea >> 24) & MASK_8BITS);
                        buffer[context.pos++] = (byte) ((context.lbitWorkArea >> 16) & MASK_8BITS);
                        buffer[context.pos++] = (byte) ((context.lbitWorkArea >> 8) & MASK_8BITS);
                        buffer[context.pos++] = (byte) (context.lbitWorkArea & MASK_8BITS);
                    }
                }
            }
        }

        // Two forms of EOF as far as Base32 decoder is concerned: actual
        // EOF (-1) and first time '=' character is encountered in stream.
        // This approach makes the '=' padding characters completely optional.
        if (context.eof && context.modulus >= 2) { // if modulus < 2, nothing to do
            final byte[] buffer = ensureBufferSize(decodeSize, context);

            //  we ignore partial bytes, i.e. only multiples of 8 count
            switch (context.modulus) {
                case 2 : // 10 bits, drop 2 and output one byte
                    buffer[context.pos++] = (byte) ((context.lbitWorkArea >> 2) & MASK_8BITS);
                    break;
                case 3 : // 15 bits, drop 7 and output 1 byte
                    buffer[context.pos++] = (byte) ((context.lbitWorkArea >> 7) & MASK_8BITS);
                    break;
                case 4 : // 20 bits = 2*8 + 4
                    context.lbitWorkArea = context.lbitWorkArea >> 4; // drop 4 bits
                    buffer[context.pos++] = (byte) ((context.lbitWorkArea >> 8) & MASK_8BITS);
                    buffer[context.pos++] = (byte) ((context.lbitWorkArea) & MASK_8BITS);
                    break;
                case 5 : // 25bits = 3*8 + 1
                    context.lbitWorkArea = context.lbitWorkArea >> 1;
                    buffer[context.pos++] = (byte) ((context.lbitWorkArea >> 16) & MASK_8BITS);
                    buffer[context.pos++] = (byte) ((context.lbitWorkArea >> 8) & MASK_8BITS);
                    buffer[context.pos++] = (byte) ((context.lbitWorkArea) & MASK_8BITS);
                    break;
                case 6 : // 30bits = 3*8 + 6
                    context.lbitWorkArea = context.lbitWorkArea >> 6;
                    buffer[context.pos++] = (byte) ((context.lbitWorkArea >> 16) & MASK_8BITS);
                    buffer[context.pos++] = (byte) ((context.lbitWorkArea >> 8) & MASK_8BITS);
                    buffer[context.pos++] = (byte) ((context.lbitWorkArea) & MASK_8BITS);
                    break;
                case 7 : // 35 = 4*8 +3
                    context.lbitWorkArea = context.lbitWorkArea >> 3;
                    buffer[context.pos++] = (byte) ((context.lbitWorkArea >> 24) & MASK_8BITS);
                    buffer[context.pos++] = (byte) ((context.lbitWorkArea >> 16) & MASK_8BITS);
                    buffer[context.pos++] = (byte) ((context.lbitWorkArea >> 8) & MASK_8BITS);
                    buffer[context.pos++] = (byte) ((context.lbitWorkArea) & MASK_8BITS);
                    break;
                default:
                    // modulus can be 0-7, and we excluded 0,1 already
                    throw new IllegalStateException("Impossible modulus "+context.modulus);
            }
        }
    }

    /**
     * <p>
     * Encodes all of the provided data, starting at inPos, for inAvail bytes. Must be called at least twice: once with
     * the data to encode, and once with inAvail set to "-1" to alert encoder that EOF has been reached, so flush last
     * remaining bytes (if not multiple of 5).
     * </p>
     *
     * @param in
     *            byte[] array of binary data to Base32 encode.
     * @param inPos
     *            Position to start reading data from.
     * @param inAvail
     *            Amount of bytes available from input for encoding.
     * @param context the context to be used
     */
    @Override
    void encode(final byte[] in, int inPos, final int inAvail, final Context context) {
        // package protected for access from I/O streams

        if (context.eof) {
            return;
        }
        // inAvail < 0 is how we're informed of EOF in the underlying data we're
        // encoding.
        if (inAvail < 0) {
            context.eof = true;
            if (0 == context.modulus && lineLength == 0) {
                return; // no leftovers to process and not using chunking
            }
            final byte[] buffer = ensureBufferSize(encodeSize, context);
            final int savedPos = context.pos;
            switch (context.modulus) { // % 5
                case 0 :
                    break;
                case 1 : // Only 1 octet; take top 5 bits then remainder
                    buffer[context.pos++] = encodeTable[(int)(context.lbitWorkArea >> 3) & MASK_5BITS]; // 8-1*5 = 3
                    buffer[context.pos++] = encodeTable[(int)(context.lbitWorkArea << 2) & MASK_5BITS]; // 5-3=2
                    buffer[context.pos++] = pad;
                    buffer[context.pos++] = pad;
                    buffer[context.pos++] = pad;
                    buffer[context.pos++] = pad;
                    buffer[context.pos++] = pad;
                    buffer[context.pos++] = pad;
                    break;
                case 2 : // 2 octets = 16 bits to use
                    buffer[context.pos++] = encodeTable[(int)(context.lbitWorkArea >> 11) & MASK_5BITS]; // 16-1*5 = 11
                    buffer[context.pos++] = encodeTable[(int)(context.lbitWorkArea >>  6) & MASK_5BITS]; // 16-2*5 = 6
                    buffer[context.pos++] = encodeTable[(int)(context.lbitWorkArea >>  1) & MASK_5BITS]; // 16-3*5 = 1
                    buffer[context.pos++] = encodeTable[(int)(context.lbitWorkArea <<  4) & MASK_5BITS]; // 5-1 = 4
                    buffer[context.pos++] = pad;
                    buffer[context.pos++] = pad;
                    buffer[context.pos++] = pad;
                    buffer[context.pos++] = pad;
                    break;
                case 3 : // 3 octets = 24 bits to use
                    buffer[context.pos++] = encodeTable[(int)(context.lbitWorkArea >> 19) & MASK_5BITS]; // 24-1*5 = 19
                    buffer[context.pos++] = encodeTable[(int)(context.lbitWorkArea >> 14) & MASK_5BITS]; // 24-2*5 = 14
                    buffer[context.pos++] = encodeTable[(int)(context.lbitWorkArea >>  9) & MASK_5BITS]; // 24-3*5 = 9
                    buffer[context.pos++] = encodeTable[(int)(context.lbitWorkArea >>  4) & MASK_5BITS]; // 24-4*5 = 4
                    buffer[context.pos++] = encodeTable[(int)(context.lbitWorkArea <<  1) & MASK_5BITS]; // 5-4 = 1
                    buffer[context.pos++] = pad;
                    buffer[context.pos++] = pad;
                    buffer[context.pos++] = pad;
                    break;
                case 4 : // 4 octets = 32 bits to use
                    buffer[context.pos++] = encodeTable[(int)(context.lbitWorkArea >> 27) & MASK_5BITS]; // 32-1*5 = 27
                    buffer[context.pos++] = encodeTable[(int)(context.lbitWorkArea >> 22) & MASK_5BITS]; // 32-2*5 = 22
                    buffer[context.pos++] = encodeTable[(int)(context.lbitWorkArea >> 17) & MASK_5BITS]; // 32-3*5 = 17
                    buffer[context.pos++] = encodeTable[(int)(context.lbitWorkArea >> 12) & MASK_5BITS]; // 32-4*5 = 12
                    buffer[context.pos++] = encodeTable[(int)(context.lbitWorkArea >>  7) & MASK_5BITS]; // 32-5*5 =  7
                    buffer[context.pos++] = encodeTable[(int)(context.lbitWorkArea >>  2) & MASK_5BITS]; // 32-6*5 =  2
                    buffer[context.pos++] = encodeTable[(int)(context.lbitWorkArea <<  3) & MASK_5BITS]; // 5-2 = 3
                    buffer[context.pos++] = pad;
                    break;
                default:
                    throw new IllegalStateException("Impossible modulus "+context.modulus);
            }
            context.currentLinePos += context.pos - savedPos; // keep track of current line position
            // if currentPos == 0 we are at the start of a line, so don't add CRLF
            if (lineLength > 0 && context.currentLinePos > 0){ // add chunk separator if required
                System.arraycopy(lineSeparator, 0, buffer, context.pos, lineSeparator.length);
                context.pos += lineSeparator.length;
            }
        } else {
            for (int i = 0; i < inAvail; i++) {
                final byte[] buffer = ensureBufferSize(encodeSize, context);
                context.modulus = (context.modulus+1) % BYTES_PER_UNENCODED_BLOCK;
                int b = in[inPos++];
                if (b < 0) {
                    b += 256;
                }
                context.lbitWorkArea = (context.lbitWorkArea << 8) + b; // BITS_PER_BYTE
                if (0 == context.modulus) { // we have enough bytes to create our output
                    buffer[context.pos++] = encodeTable[(int)(context.lbitWorkArea >> 35) & MASK_5BITS];
                    buffer[context.pos++] = encodeTable[(int)(context.lbitWorkArea >> 30) & MASK_5BITS];
                    buffer[context.pos++] = encodeTable[(int)(context.lbitWorkArea >> 25) & MASK_5BITS];
                    buffer[context.pos++] = encodeTable[(int)(context.lbitWorkArea >> 20) & MASK_5BITS];
                    buffer[context.pos++] = encodeTable[(int)(context.lbitWorkArea >> 15) & MASK_5BITS];
                    buffer[context.pos++] = encodeTable[(int)(context.lbitWorkArea >> 10) & MASK_5BITS];
                    buffer[context.pos++] = encodeTable[(int)(context.lbitWorkArea >> 5) & MASK_5BITS];
                    buffer[context.pos++] = encodeTable[(int)context.lbitWorkArea & MASK_5BITS];
                    context.currentLinePos += BYTES_PER_ENCODED_BLOCK;
                    if (lineLength > 0 && lineLength <= context.currentLinePos) {
                        System.arraycopy(lineSeparator, 0, buffer, context.pos, lineSeparator.length);
                        context.pos += lineSeparator.length;
                        context.currentLinePos = 0;
                    }
                }
            }
        }
    }

    /**
     * Returns whether or not the {@code octet} is in the Base32 alphabet.
     *
     * @param octet
     *            The value to test
     * @return {@code true} if the value is defined in the the Base32 alphabet {@code false} otherwise.
     */
    @Override
    public boolean isInAlphabet(final byte octet) {
        return octet >= 0 && octet < decodeTable.length && decodeTable[octet] != -1;
    }
}
