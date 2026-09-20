package net.coreprotect.database.clickhouse;

import com.clickhouse.data.format.BinaryStreamUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.*;

final class ClickHouseRowBinaryBuffer implements AutoCloseable {

    private static final UUID ZERO_UUID = new UUID(0, 0);

    private static final TimeZone UTC = TimeZone.getTimeZone("UTC");

    private static final int TYPE_UUID = 0;
    private static final int TYPE_STRING = 1;
    private static final int TYPE_UINT8 = 2;
    private static final int TYPE_UINT32 = 3;
    private static final int TYPE_UINT64 = 4;
    private static final int TYPE_INT32 = 5;
    private static final int TYPE_INT64 = 6;
    private static final int TYPE_FLOAT32 = 7;
    private static final int TYPE_FLOAT64 = 8;
    private static final int TYPE_DATETIME64_UTC = 9;

    private final RowBuffer rows = new RowBuffer();
    private final Map<String, Integer> columnIndexes = new HashMap<>();
    private final String[] types;
    private final int[] typeCodes;
    private final boolean[] nullable;
    private final Object[] defaults;
    private final Object[] values;
    private final List<RowSpan> rowSpans = new ArrayList<>();
    private boolean sealed;
    private boolean failed;
    private boolean rowStarted;

    ClickHouseRowBinaryBuffer(List<String> columns, List<String> types) {
        if (columns.size() != types.size() || columns.isEmpty()) {
            throw new IllegalArgumentException("ClickHouse RowBinary columns and types must have the same non-zero size");
        }
        this.types = types.toArray(new String[0]);
        defaults = new Object[types.size()];
        values = new Object[types.size()];
        typeCodes = new int[types.size()];
        nullable = new boolean[types.size()];
        for (int index = 0; index < columns.size(); index++) {
            String column = columns.get(index);
            if (columnIndexes.put(column, index) != null) {
                throw new IllegalArgumentException("Duplicate ClickHouse RowBinary column: " + column);
            }
            defaults[index] = defaultValue(types.get(index));

            String type = types.get(index);
            if (type.startsWith("Nullable(") && type.endsWith(")")) {
                nullable[index] = true;
                type = type.substring(9, type.length() - 1);
            }
            if (type.startsWith("LowCardinality(") && type.endsWith(")")) {
                type = type.substring(15, type.length() - 1);
            }
            typeCodes[index] = typeCode(type, this.types[index]);
        }
    }

    private static int typeCode(String type, String declaredType) {
        switch (type) {
            case "UUID":
                return TYPE_UUID;
            case "String":
                return TYPE_STRING;
            case "UInt8":
                return TYPE_UINT8;
            case "UInt32":
                return TYPE_UINT32;
            case "UInt64":
                return TYPE_UINT64;
            case "Int32":
                return TYPE_INT32;
            case "Int64":
                return TYPE_INT64;
            case "Float32":
                return TYPE_FLOAT32;
            case "Float64":
                return TYPE_FLOAT64;
            case "DateTime64(3, 'UTC')":
                return TYPE_DATETIME64_UTC;
            default:
                throw new IllegalArgumentException("Unsupported ClickHouse RowBinary type: " + declaredType);
        }
    }

    void beginRow() {
        ensureWritable();
        if (rowStarted) {
            throw new IllegalStateException("ClickHouse RowBinary row is already active");
        }
        System.arraycopy(defaults, 0, values, 0, defaults.length);
        rowStarted = true;
    }

    void set(String column, Object value) {
        ensureWritable();
        requireActiveRow();
        Integer index = columnIndexes.get(column);
        if (index == null) {
            throw new IllegalArgumentException("Unknown ClickHouse RowBinary column: " + column);
        }
        values[index] = value;
    }

    void commitRow(String description) throws SQLException {
        commitRow(description, 0);
    }

    void commitRow(String description, int partitionId) throws SQLException {
        ensureWritable();
        requireActiveRow();
        if (partitionId < 0) {
            throw new IllegalArgumentException("ClickHouse row partition IDs cannot be negative");
        }
        int start = rows.size();
        try {
            for (int index = 0; index < values.length; index++) {
                writeValue(index, values[index]);
            }
            rowSpans.add(new RowSpan(partitionId, start, rows.size() - start));
            rowStarted = false;
        }
        catch (IOException | RuntimeException exception) {
            failed = true;
            throw new SQLException("Failed to encode ClickHouse " + description, exception);
        }
    }

    void seal() {
        ensureWritable();
        if (rowStarted) {
            throw new IllegalStateException("ClickHouse RowBinary row is incomplete");
        }
        sealed = true;
    }

    InputStream openStream() {
        if (!sealed) {
            throw new IllegalStateException("ClickHouse RowBinary buffer is not sealed");
        }
        return rows.openStream();
    }

    InputStream openStream(int partitionId) {
        if (!sealed) {
            throw new IllegalStateException("ClickHouse RowBinary buffer is not sealed");
        }
        if (partitionId < 0) {
            throw new IllegalArgumentException("ClickHouse row partition IDs cannot be negative");
        }
        return rows.openStream(rowSpans, partitionId);
    }

    int checkpoint() {
        ensureWritable();
        if (rowStarted) {
            throw new IllegalStateException("Cannot checkpoint an incomplete ClickHouse RowBinary row");
        }
        return rows.size();
    }

    void restore(int checkpoint) {
        if (sealed) {
            throw new IllegalStateException("ClickHouse RowBinary buffer is sealed");
        }
        if (checkpoint < 0 || checkpoint > rows.size()) {
            throw new IllegalArgumentException("Invalid ClickHouse RowBinary checkpoint");
        }
        rows.truncate(checkpoint);
        while (!rowSpans.isEmpty()) {
            RowSpan span = rowSpans.get(rowSpans.size() - 1);
            if (span.start + span.length <= checkpoint) {
                break;
            }
            rowSpans.remove(rowSpans.size() - 1);
        }
        failed = false;
        rowStarted = false;
    }

    @Override
    public void close() {
        sealed = true;
        rows.reset();
        rowSpans.clear();
    }

    private void ensureWritable() {
        if (sealed) {
            throw new IllegalStateException("ClickHouse RowBinary buffer is sealed");
        }
        if (failed) {
            throw new IllegalStateException("ClickHouse RowBinary buffer is invalid after an encoding failure");
        }
    }

    private void requireActiveRow() {
        if (!rowStarted) {
            throw new IllegalStateException("ClickHouse RowBinary row is not active");
        }
    }

    private void writeValue(int index, Object value) throws IOException {
        if (nullable[index]) {
            if (value == null) {
                BinaryStreamUtils.writeNull(rows);
                return;
            }
            BinaryStreamUtils.writeNonNull(rows);
        }
        else if (value == null) {
            throw new IllegalArgumentException("Non-nullable ClickHouse value cannot be null");
        }

        switch (typeCodes[index]) {
            case TYPE_UUID:
                BinaryStreamUtils.writeUuid(rows, (UUID) value);
                return;
            case TYPE_STRING:
                if (value instanceof byte[]) {
                    BinaryStreamUtils.writeString(rows, (byte[]) value);
                }
                else {
                    BinaryStreamUtils.writeString(rows, (String) value);
                }
                return;
            case TYPE_UINT8:
                BinaryStreamUtils.writeUnsignedInt8(rows, unsignedInt8(value));
                return;
            case TYPE_UINT32:
                BinaryStreamUtils.writeUnsignedInt32(rows, unsignedInt32(value));
                return;
            case TYPE_UINT64:
                BinaryStreamUtils.writeUnsignedInt64(rows, unsignedInt64(value));
                return;
            case TYPE_INT32:
                BinaryStreamUtils.writeInt32(rows, int32(value));
                return;
            case TYPE_INT64:
                BinaryStreamUtils.writeInt64(rows, ((Number) value).longValue());
                return;
            case TYPE_FLOAT32:
                BinaryStreamUtils.writeFloat32(rows, ((Number) value).floatValue());
                return;
            case TYPE_FLOAT64:
                BinaryStreamUtils.writeFloat64(rows, ((Number) value).doubleValue());
                return;
            case TYPE_DATETIME64_UTC:
                BinaryStreamUtils.writeDateTime64(rows, (LocalDateTime) value, 3, UTC);
                return;
            default:
                throw new IllegalArgumentException("Unsupported ClickHouse RowBinary type: " + types[index]);
        }
    }

    private static int unsignedInt8(Object value) {
        long number = ((Number) value).longValue();
        if (number < 0 || number > 0xffL) {
            throw new IllegalArgumentException("ClickHouse UInt8 value is out of range: " + number);
        }
        return (int) number;
    }

    private static long unsignedInt32(Object value) {
        long number = value instanceof Integer ? Integer.toUnsignedLong((Integer) value) : ((Number) value).longValue();
        if (number < 0 || number > 0xffff_ffffL) {
            throw new IllegalArgumentException("ClickHouse UInt32 value is out of range: " + number);
        }
        return number;
    }

    private static long unsignedInt64(Object value) {
        long number = ((Number) value).longValue();
        if (number < 0) {
            throw new IllegalArgumentException("ClickHouse UInt64 value is out of range: " + number);
        }
        return number;
    }

    private static int int32(Object value) {
        long number = ((Number) value).longValue();
        if (number < Integer.MIN_VALUE || number > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("ClickHouse Int32 value is out of range: " + number);
        }
        return (int) number;
    }

    private static Object defaultValue(String type) {
        if (type.startsWith("Nullable(")) {
            return null;
        }
        if (type.equals("UUID")) {
            return ZERO_UUID;
        }
        if (type.contains("String")) {
            return "";
        }
        if (type.equals("Float32")) {
            return 0.0f;
        }
        if (type.equals("Float64")) {
            return 0.0d;
        }
        if (type.equals("DateTime64(3, 'UTC')")) {
            return LocalDateTime.of(1970, 1, 1, 0, 0);
        }
        if (type.equals("UInt64") || type.equals("Int64")) {
            return 0L;
        }
        return 0;
    }

    private static final class RowBuffer extends ByteArrayOutputStream {

        private InputStream openStream() {
            return new ByteArrayInputStream(buf, 0, count);
        }

        private InputStream openStream(List<RowSpan> spans, int partitionId) {
            return new PartitionInputStream(buf, spans, partitionId);
        }

        private void truncate(int size) {
            count = size;
        }
    }

    private static final class RowSpan {

        private final int partitionId;
        private final int start;
        private final int length;

        private RowSpan(int partitionId, int start, int length) {
            this.partitionId = partitionId;
            this.start = start;
            this.length = length;
        }
    }

    private static final class PartitionInputStream extends InputStream {

        private final byte[] data;
        private final List<RowSpan> spans;
        private final int partitionId;
        private int spanIndex;
        private int spanOffset;

        private PartitionInputStream(byte[] data, List<RowSpan> spans, int partitionId) {
            this.data = data;
            this.spans = spans;
            this.partitionId = partitionId;
        }

        @Override
        public int read() {
            RowSpan span = currentSpan();
            if (span == null) {
                return -1;
            }
            return data[span.start + spanOffset++] & 0xff;
        }

        @Override
        public int read(byte[] output, int offset, int length) {
            if (output == null) {
                throw new NullPointerException("output");
            }
            if (offset < 0 || length < 0 || length > output.length - offset) {
                throw new IndexOutOfBoundsException();
            }
            if (length == 0) {
                return 0;
            }

            int copied = 0;
            while (length > 0) {
                RowSpan span = currentSpan();
                if (span == null) {
                    return copied == 0 ? -1 : copied;
                }
                int count = Math.min(length, span.length - spanOffset);
                System.arraycopy(data, span.start + spanOffset, output, offset, count);
                spanOffset += count;
                offset += count;
                length -= count;
                copied += count;
            }
            return copied;
        }

        private RowSpan currentSpan() {
            while (spanIndex < spans.size()) {
                RowSpan span = spans.get(spanIndex);
                if (span.partitionId != partitionId || spanOffset >= span.length) {
                    spanIndex++;
                    spanOffset = 0;
                    continue;
                }
                return span;
            }
            return null;
        }
    }

}
