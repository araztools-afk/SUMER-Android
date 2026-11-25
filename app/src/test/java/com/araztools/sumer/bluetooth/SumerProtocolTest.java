package com.araztools.sumer.bluetooth;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

public class SumerProtocolTest {

    @Test
    public void parsesOnlyStandaloneLowercaseMarker() {
        assertEquals(SumerProtocol.Type.MATRIX_MARKER, SumerProtocol.parseLine("a").type);
        assertEquals(SumerProtocol.Type.MATRIX_MARKER, SumerProtocol.parseLine("  a  ").type);
        assertEquals(SumerProtocol.Type.UNKNOWN, SumerProtocol.parseLine("A").type);
        assertEquals(SumerProtocol.Type.UNKNOWN, SumerProtocol.parseLine("status a").type);
    }

    @Test
    public void acceptsOnlyUnsignedValuesFromZeroThroughFiveThousand() {
        assertEquals(0, SumerProtocol.parseLine("0").number);
        assertEquals(5000, SumerProtocol.parseLine("5000").number);
        assertEquals(SumerProtocol.Type.OUT_OF_RANGE, SumerProtocol.parseLine("5001").type);
        assertEquals(SumerProtocol.Type.OUT_OF_RANGE, SumerProtocol.parseLine("999999999999999999999").type);
        assertEquals(SumerProtocol.Type.INVALID_NUMBER, SumerProtocol.parseLine("-1").type);
        assertEquals(SumerProtocol.Type.INVALID_NUMBER, SumerProtocol.parseLine("+1").type);
    }

    @Test
    public void recognizesStatusWithoutTreatingItAsASample() {
        assertEquals(SumerProtocol.Type.STATUS, SumerProtocol.parseLine("READY").type);
        assertEquals(SumerProtocol.Type.STATUS, SumerProtocol.parseLine("SENSOR_ERROR").type);
        assertEquals(SumerProtocol.Type.UNKNOWN, SumerProtocol.parseLine("custom status").type);
        assertEquals(SumerProtocol.Type.EMPTY, SumerProtocol.parseLine("  ").type);
    }

    @Test
    public void reconstructsFragmentedLinesAndSplitsCoalescedLines() {
        SumerProtocol.LineFramer framer = new SumerProtocol.LineFramer();
        List<String> lines = new ArrayList<>();
        int[] tooLong = {0};
        SumerProtocol.LineListener listener = listener(lines, tooLong);

        feed(framer, listener, "12");
        feed(framer, listener, "50\n13");
        feed(framer, listener, "00\r\nREADY\n");

        assertEquals(Arrays.asList("1250", "1300", "READY"), lines);
        assertEquals(0, tooLong[0]);
    }

    @Test
    public void discardsAnOverlongLineAndRecoversAtTheNextDelimiter() {
        SumerProtocol.LineFramer framer = new SumerProtocol.LineFramer();
        List<String> lines = new ArrayList<>();
        int[] tooLong = {0};
        SumerProtocol.LineListener listener = listener(lines, tooLong);

        byte[] oversized = new byte[SumerProtocol.MAX_LINE_BYTES + 20];
        Arrays.fill(oversized, (byte) '7');
        framer.accept(oversized, 0, oversized.length, listener);
        feed(framer, listener, "\n42\n");

        assertEquals(1, tooLong[0]);
        assertEquals(Arrays.asList("42"), lines);
    }

    @Test
    public void outgoingLineHasOneLfAndRejectsLineInjection() {
        assertEquals("!PING\n", new String(
            SumerProtocol.encodeLine(" !PING "),
            StandardCharsets.UTF_8
        ));

        try {
            SumerProtocol.encodeLine("!START\n!STOP");
            fail("Embedded newline must be rejected");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    private static SumerProtocol.LineListener listener(List<String> lines, int[] tooLong) {
        return new SumerProtocol.LineListener() {
            @Override public void onLine(String line) { lines.add(line); }
            @Override public void onLineTooLong() { tooLong[0]++; }
        };
    }

    private static void feed(
        SumerProtocol.LineFramer framer,
        SumerProtocol.LineListener listener,
        String text
    ) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        framer.accept(bytes, 0, bytes.length, listener);
    }
}
