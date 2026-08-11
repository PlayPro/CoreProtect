package net.coreprotect.utility.serialize;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;

final class BinaryCodecSupport {

    enum CompactDoubleType {
        ZERO,
        ONE,
        INTEGER,
        FLOAT,
        RAW
    }

    static final int MAX_ENCODED_LENGTH = 32 * 1024 * 1024;
    static final int MAX_CONTAINER_LENGTH = 4 * 1024 * 1024;
    private static final int MAX_DEPTH = 64;

    private BinaryCodecSupport() {
        throw new IllegalStateException("Support class");
    }

    static int compareBytes(byte[] first, byte[] second) {
        int length = Math.min(first.length, second.length);
        for (int index = 0; index < length; index++) {
            int difference = Byte.toUnsignedInt(first[index]) - Byte.toUnsignedInt(second[index]);
            if (difference != 0) {
                return difference;
            }
        }
        return Integer.compare(first.length, second.length);
    }

    static CompactDoubleType compactDoubleType(double value) {
        long bits = Double.doubleToLongBits(value);
        if (bits == Double.doubleToLongBits(0.0)) {
            return CompactDoubleType.ZERO;
        }
        if (bits == Double.doubleToLongBits(1.0)) {
            return CompactDoubleType.ONE;
        }
        if (value != 0.0 && value >= Long.MIN_VALUE && value <= Long.MAX_VALUE && (double) (long) value == value) {
            return CompactDoubleType.INTEGER;
        }
        if ((double) (float) value == value) {
            return CompactDoubleType.FLOAT;
        }
        return CompactDoubleType.RAW;
    }

    static void requireDepth(String description, int depth) {
        if (depth > MAX_DEPTH) {
            throw new IllegalArgumentException(description + " exceeds the maximum nesting depth");
        }
    }

    static int unsignedVarSize(long value) {
        int size = 1;
        while ((value & ~0x7fL) != 0) {
            size++;
            value >>>= 7;
        }
        return size;
    }

    static void validateUnicode(String description, String value) {
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (Character.isHighSurrogate(character)) {
                if (++index >= value.length() || !Character.isLowSurrogate(value.charAt(index))) {
                    throw new IllegalArgumentException(description + " contains an unpaired Unicode surrogate");
                }
            }
            else if (Character.isLowSurrogate(character)) {
                throw new IllegalArgumentException(description + " contains an unpaired Unicode surrogate");
            }
        }
    }

    static class Output {

        private final String description;
        private byte[] data;
        private int size;

        Output(String description) {
            this(description, 128);
        }

        Output(String description, int initialCapacity) {
            this.description = description;
            this.data = new byte[initialCapacity];
        }

        final void write(int value) {
            requireCapacity(1);
            data[size++] = (byte) value;
        }

        final void write(byte[] value, int offset, int length) {
            Objects.checkFromIndexSize(offset, length, value.length);
            requireCapacity(length);
            System.arraycopy(value, offset, data, size, length);
            size += length;
        }

        final byte[] toByteArray() {
            return Arrays.copyOf(data, size);
        }

        final void writeInt(int value) {
            write(value >>> 24);
            write(value >>> 16);
            write(value >>> 8);
            write(value);
        }

        final void writeLong(long value) {
            write((int) (value >>> 56));
            write((int) (value >>> 48));
            write((int) (value >>> 40));
            write((int) (value >>> 32));
            write((int) (value >>> 24));
            write((int) (value >>> 16));
            write((int) (value >>> 8));
            write((int) value);
        }

        final void writeLength(int value) {
            if (value < 0 || value > MAX_CONTAINER_LENGTH) {
                throw new IllegalArgumentException(description + " collection exceeds the maximum size");
            }
            writeVarUnsigned(value);
        }

        final void writeByteLength(int value) {
            if (value < 0 || value > MAX_ENCODED_LENGTH) {
                throw new IllegalArgumentException(description + " binary value exceeds the maximum size");
            }
            writeVarUnsigned(value);
        }

        final void writeZigZag(long value) {
            writeVarUnsigned((value << 1) ^ (value >> 63));
        }

        final void writeVarUnsigned(long value) {
            while ((value & ~0x7fL) != 0) {
                write(((int) value & 0x7f) | 0x80);
                value >>>= 7;
            }
            write((int) value);
        }

        private void requireCapacity(int additional) {
            if (additional < 0 || size > MAX_ENCODED_LENGTH - additional) {
                throw new IllegalArgumentException(description + " exceeds the maximum encoded size");
            }
            int required = size + additional;
            if (required > data.length) {
                data = Arrays.copyOf(data, Math.max(required, Math.min(MAX_ENCODED_LENGTH, data.length << 1)));
            }
        }
    }

    static class Input {

        private final byte[] data;
        private final String description;
        private final String valueDescription;
        private int position;

        Input(byte[] data, String description) {
            this.data = data;
            this.description = description;
            this.valueDescription = description.toLowerCase(Locale.ROOT);
        }

        final int[] readIntArray() {
            int length = readLength("integer array");
            int[] values = new int[length];
            for (int index = 0; index < length; index++) {
                values[index] = checkedInteger(readZigZag());
            }
            return values;
        }

        final long[] readLongArray() {
            int length = readLength("long array");
            long[] values = new long[length];
            for (int index = 0; index < length; index++) {
                values[index] = readZigZag();
            }
            return values;
        }

        final float readFloat() {
            int bits = readInt();
            float value = Float.intBitsToFloat(bits);
            if (Float.floatToIntBits(value) != bits) {
                throw new IllegalArgumentException(description + " contains a noncanonical float");
            }
            return value;
        }

        final double readRawDouble() {
            long bits = readLong();
            double value = Double.longBitsToDouble(bits);
            if (!Double.isFinite(value) || compactDoubleType(value) != CompactDoubleType.RAW) {
                throw new IllegalArgumentException(description + " contains a noncanonical double");
            }
            return value;
        }

        final double readIntegralDouble() {
            long integer = readZigZag();
            double value = integer;
            if ((long) value != integer || compactDoubleType(value) != CompactDoubleType.INTEGER) {
                throw new IllegalArgumentException(description + " contains a noncanonical integral double");
            }
            return value;
        }

        final double readFloatDouble() {
            float value = readFloat();
            double result = value;
            if (!Double.isFinite(result) || compactDoubleType(result) != CompactDoubleType.FLOAT) {
                throw new IllegalArgumentException(description + " contains a noncanonical float-backed double");
            }
            return result;
        }

        final double readSpecialDouble() {
            switch (readUnsignedByte()) {
                case 0:
                    return Double.NaN;
                case 1:
                    return Double.POSITIVE_INFINITY;
                case 2:
                    return Double.NEGATIVE_INFINITY;
                default:
                    throw new IllegalArgumentException("Invalid special double " + valueDescription + " value");
            }
        }

        final int readLength(String valueType) {
            long value = readVarUnsigned();
            if (value < 0 || value > MAX_CONTAINER_LENGTH) {
                throw new IllegalArgumentException(description + " " + valueType + " exceeds the maximum size");
            }
            return (int) value;
        }

        final int readByteLength() {
            long value = readVarUnsigned();
            if (value < 0 || value > MAX_ENCODED_LENGTH) {
                throw new IllegalArgumentException(description + " binary value exceeds the maximum size");
            }
            return (int) value;
        }

        final byte[] readBytes(int length) {
            if (length > remaining()) {
                throw new IllegalArgumentException(description + " value exceeds the remaining input");
            }
            byte[] value = new byte[length];
            System.arraycopy(data, position, value, 0, length);
            position += length;
            return value;
        }

        final String readUtf8(int length) {
            if (length > remaining()) {
                throw new IllegalArgumentException(description + " string exceeds the remaining input");
            }
            try {
                String value = StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(data, position, length))
                        .toString();
                position += length;
                return value;
            }
            catch (CharacterCodingException exception) {
                throw new IllegalArgumentException(description + " string is not valid UTF-8", exception);
            }
        }

        final int readUnsignedByte() {
            if (position >= data.length) {
                throw new IllegalArgumentException(description + " ended unexpectedly");
            }
            return Byte.toUnsignedInt(data[position++]);
        }

        final int readInt() {
            return readUnsignedByte() << 24
                    | readUnsignedByte() << 16
                    | readUnsignedByte() << 8
                    | readUnsignedByte();
        }

        final long readLong() {
            return (long) readUnsignedByte() << 56
                    | (long) readUnsignedByte() << 48
                    | (long) readUnsignedByte() << 40
                    | (long) readUnsignedByte() << 32
                    | (long) readUnsignedByte() << 24
                    | (long) readUnsignedByte() << 16
                    | (long) readUnsignedByte() << 8
                    | readUnsignedByte();
        }

        final long readZigZag() {
            long value = readVarUnsigned();
            return (value >>> 1) ^ -(value & 1);
        }

        final long readVarUnsigned() {
            long value = 0;
            for (int index = 0, shift = 0; index < 10; index++, shift += 7) {
                int current = readUnsignedByte();
                if (index == 9 && (current & 0xfe) != 0) {
                    throw new IllegalArgumentException(description + " variable integer is out of range");
                }
                value |= (long) (current & 0x7f) << shift;
                if ((current & 0x80) == 0) {
                    if (unsignedVarSize(value) != index + 1) {
                        throw new IllegalArgumentException(description + " variable integer is not canonical");
                    }
                    return value;
                }
            }
            throw new IllegalArgumentException(description + " variable integer is too long");
        }

        final int remaining() {
            return data.length - position;
        }

        final int position() {
            return position;
        }

        final byte[] bytesSince(int start) {
            if (start < 0 || start > position) {
                throw new IllegalArgumentException("Invalid binary input range");
            }
            return Arrays.copyOfRange(data, start, position);
        }

        final short checkedShort(long value) {
            if (value < Short.MIN_VALUE || value > Short.MAX_VALUE) {
                throw new IllegalArgumentException(description + " short is out of range");
            }
            return (short) value;
        }

        final int checkedInteger(long value) {
            if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
                throw new IllegalArgumentException(description + " integer is out of range");
            }
            return (int) value;
        }

        final void requireEnd() {
            if (position != data.length) {
                throw new IllegalArgumentException(description + " contains trailing bytes");
            }
        }
    }
}
