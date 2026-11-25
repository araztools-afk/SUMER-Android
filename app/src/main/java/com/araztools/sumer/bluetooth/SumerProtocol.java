package com.araztools.sumer.bluetooth;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Strict text protocol helpers for SUMER Bluetooth Classic / SPP.
 *
 * Wire format:
 * - one ASCII decimal integer per line, in the range 0..5000;
 * - a standalone lowercase "a" marks matrix-scan start/end;
 * - status lines are text and never enter the numeric sample stream.
 *
 * Incoming Bluetooth reads are byte-stream chunks, not message boundaries.
 * LineFramer therefore reassembles partial reads and splits coalesced lines.
 */
public final class SumerProtocol {
    public static final int MAX_LINE_BYTES = 128;

    public enum Type {
        EMPTY,
        MATRIX_MARKER,
        NUMBER,
        STATUS,
        OUT_OF_RANGE,
        INVALID_NUMBER,
        UNKNOWN
    }

    public static final class Message {
        public final Type type;
        public final String text;
        public final int number;

        private Message(Type type, String text, int number) {
            this.type = type;
            this.text = text;
            this.number = number;
        }
    }

    public interface LineListener {
        void onLine(String line);
        void onLineTooLong();
    }

    private static final Set<String> STATUS_LINES = new HashSet<>(Arrays.asList(
        "READY",
        "SCANNING",
        "STOPPED",
        "SENSOR_ERROR",
        "LOW_BATTERY"
    ));

    private SumerProtocol() { }

    public static Message parseLine(String raw) {
        String line = raw == null ? "" : raw.trim();
        if (line.isEmpty()) {
            return new Message(Type.EMPTY, "", -1);
        }

        if ("a".equals(line)) {
            return new Message(Type.MATRIX_MARKER, line, -1);
        }

        int firstDigit = 0;
        boolean hasSign = line.charAt(0) == '+' || line.charAt(0) == '-';
        if (hasSign) firstDigit = 1;

        boolean numericLike = firstDigit < line.length();
        for (int i = firstDigit; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c < '0' || c > '9') {
                numericLike = false;
                break;
            }
        }

        if (numericLike) {
            if (hasSign) {
                return new Message(Type.INVALID_NUMBER, line, -1);
            }

            int value = 0;
            for (int i = 0; i < line.length(); i++) {
                int digit = line.charAt(i) - '0';
                if (value > (5000 - digit) / 10) {
                    return new Message(Type.OUT_OF_RANGE, line, -1);
                }
                value = value * 10 + digit;
            }
            return new Message(Type.NUMBER, line, value);
        }

        if (STATUS_LINES.contains(line)) {
            return new Message(Type.STATUS, line, -1);
        }

        return new Message(Type.UNKNOWN, line, -1);
    }

    /**
     * Encodes exactly one outgoing text line. Embedded CR/LF is rejected so a
     * terminal input cannot accidentally inject additional commands.
     */
    public static byte[] encodeLine(String value) {
        if (value == null) throw new IllegalArgumentException("Line is null");
        if (value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
            throw new IllegalArgumentException("A command must contain exactly one line");
        }

        String line = value.trim();
        if (line.isEmpty()) throw new IllegalArgumentException("Line is empty");
        byte[] frame = (line + "\n").getBytes(StandardCharsets.UTF_8);
        if (frame.length - 1 > MAX_LINE_BYTES) {
            throw new IllegalArgumentException("Line exceeds " + MAX_LINE_BYTES + " bytes");
        }
        return frame;
    }

    public static final class LineFramer {
        private final byte[] buffer = new byte[MAX_LINE_BYTES];
        private int size;
        private boolean droppingOverlongLine;

        /**
         * Feed any portion of the SPP byte stream. The caller may pass partial
         * lines or several lines in one call.
         */
        public void accept(byte[] bytes, int offset, int length, LineListener listener) {
            if (bytes == null) throw new IllegalArgumentException("bytes is null");
            if (listener == null) throw new IllegalArgumentException("listener is null");
            if (offset < 0 || length < 0 || offset + length > bytes.length) {
                throw new IndexOutOfBoundsException("Invalid byte range");
            }

            for (int i = offset; i < offset + length; i++) {
                int value = bytes[i] & 0xff;

                if (value == '\n' || value == '\r') {
                    if (droppingOverlongLine) {
                        listener.onLineTooLong();
                    } else if (size > 0) {
                        listener.onLine(new String(buffer, 0, size, StandardCharsets.UTF_8));
                    }
                    size = 0;
                    droppingOverlongLine = false;
                    continue;
                }

                if (droppingOverlongLine) continue;

                if (size == buffer.length) {
                    size = 0;
                    droppingOverlongLine = true;
                    continue;
                }

                buffer[size++] = bytes[i];
            }
        }
    }
}
