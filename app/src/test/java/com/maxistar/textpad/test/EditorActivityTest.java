package com.maxistar.textpad.activities;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class EditorActivityTest {

    // ─── clampScroll ──────────────────────────────────────────────

    private int clampScroll(int value, int max) {
        return Math.max(0, Math.min(value, Math.max(0, max)));
    }

    @Test
    public void clampScroll_valueWithinRange_returnsValue() {
        assertEquals(5, clampScroll(5, 10));
    }

    @Test
    public void clampScroll_valueBelowZero_returnsZero() {
        assertEquals(0, clampScroll(-3, 10));
    }

    @Test
    public void clampScroll_valueAboveMax_returnsMax() {
        assertEquals(10, clampScroll(15, 10));
    }

    @Test
    public void clampScroll_negativeMax_returnsZero() {
        assertEquals(0, clampScroll(5, -5));
    }

    @Test
    public void clampScroll_zeroMax_returnsZero() {
        assertEquals(0, clampScroll(5, 0));
    }

    // ─── buildTimestampedName ─────────────────────────────────────

    private String buildTimestampedName(String originalName, String timestamp, String uuid) {
        int dot = originalName.lastIndexOf('.');
        if (dot > 0) {
            String base = originalName.substring(0, dot);
            String ext  = originalName.substring(dot + 1);
            return base + "-" + timestamp + "-" + uuid + "." + ext;
        }
        return originalName + "-" + timestamp + "-" + uuid;
    }

    @Test
    public void buildTimestampedName_withExtension() {
        assertEquals("notes-202410311200-abc12345.txt",
                buildTimestampedName("notes.txt", "202410311200", "abc12345"));
    }

    @Test
    public void buildTimestampedName_withoutExtension() {
        assertEquals("notes-202410311200-abc12345",
                buildTimestampedName("notes", "202410311200", "abc12345"));
    }

    @Test
    public void buildTimestampedName_multiDot_usesLastDot() {
        assertEquals("my.notes-202410311200-abc12345.txt",
                buildTimestampedName("my.notes.txt", "202410311200", "abc12345"));
    }

    @Test
    public void buildTimestampedName_dotAtStart_noExtension() {
        // dot ở vị trí 0 → dot > 0 false → không tính extension
        assertEquals(".gitignore-202410311200-abc12345",
                buildTimestampedName(".gitignore", "202410311200", "abc12345"));
    }

    // ─── isNewfileName ────────────────────────────────────────────

    private boolean isNewfileName(String filePath) {
        if (filePath == null) return false;
        return new java.io.File(filePath).getName().startsWith("newfile-");
    }

    @Test
    public void isNewfileName_newfilePrefix_returnsTrue() {
        assertTrue(isNewfileName("/data/TextPad/newfile-202410311200-abc12345.txt"));
    }

    @Test
    public void isNewfileName_regularFile_returnsFalse() {
        assertFalse(isNewfileName("/storage/emulated/0/notes.txt"));
    }

    @Test
    public void isNewfileName_null_returnsFalse() {
        assertFalse(isNewfileName(null));
    }

    @Test
    public void isNewfileName_dirContainsNewfile_checkFilenameOnly() {
        assertFalse(isNewfileName("/newfile-123/notes.txt"));
    }

    // ─── readFilePreview ──────────────────────────────────────────

    private String readFilePreview(String text, int maxChars) {
        if (text == null || text.trim().isEmpty()) return "";
        text = text.trim();
        int newline = text.indexOf('\n');
        if (newline > 0) text = text.substring(0, newline).trim();
        if (text.length() > maxChars) text = text.substring(0, maxChars) + "…";
        return text;
    }

    @Test
    public void readFilePreview_shortText_returnsAsIs() {
        assertEquals("hello", readFilePreview("hello", 40));
    }

    @Test
    public void readFilePreview_longText_truncatesWithEllipsis() {
        assertEquals("a".repeat(40) + "…", readFilePreview("a".repeat(41), 40));
    }

    @Test
    public void readFilePreview_multiline_returnsFirstLineOnly() {
        assertEquals("first line", readFilePreview("first line\nsecond line", 40));
    }

    @Test
    public void readFilePreview_emptyText_returnsEmpty() {
        assertEquals("", readFilePreview("", 40));
    }

    @Test
    public void readFilePreview_whitespaceOnly_returnsEmpty() {
        assertEquals("", readFilePreview("   \n   ", 40));
    }

    @Test
    public void readFilePreview_null_returnsEmpty() {
        assertEquals("", readFilePreview(null, 40));
    }
}