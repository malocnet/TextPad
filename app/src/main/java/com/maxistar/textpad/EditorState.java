package com.maxistar.textpad;

/**
 * Holds the complete state of one editor tab.
 * Tab 0 is always the edit tab; tabs 1-4 are read-only.
 */
public class EditorState {

    // --- Identity ---
    public String urlFilename;
    public boolean isReadOnly;

    // --- Content ---
    public String content;
    public int cursorStart;
    public int cursorEnd;
    public int scrollX;
    public int scrollY;

    // --- Change tracking ---
    public boolean changed;

    // --- Save validation ---
    public String originalContentSha256;
    public Long originalSize;
    public Long originalLastModified;

    // --- Recovery ---
    public String recoveryKey;
    public long editorGeneration;

    // --- Display ---
    public String displayName;

    /**
     * Creates a blank edit tab with a generated filename.
     */
    public static EditorState newEditTab() {
        EditorState state = new EditorState();
        state.urlFilename = TPStrings.EMPTY;
        state.isReadOnly = false;
        state.content = TPStrings.EMPTY;
        state.cursorStart = 0;
        state.cursorEnd = 0;
        state.scrollX = 0;
        state.scrollY = 0;
        state.changed = false;
        state.originalContentSha256 = null;
        state.originalSize = null;
        state.originalLastModified = null;
        state.recoveryKey = null;
        state.editorGeneration = 0;
        state.displayName = generateNewFileName();
        return state;
    }

    /**
     * Creates a read-only tab for the given file.
     */
    public static EditorState newReadOnlyTab(String urlFilename, String displayName, String content) {
        EditorState state = new EditorState();
        state.urlFilename = urlFilename;
        state.isReadOnly = true;
        state.content = content;
        state.cursorStart = 0;
        state.cursorEnd = 0;
        state.scrollX = 0;
        state.scrollY = 0;
        state.changed = false;
        state.originalContentSha256 = null;
        state.originalSize = null;
        state.originalLastModified = null;
        state.recoveryKey = null;
        state.editorGeneration = 0;
        state.displayName = displayName;
        return state;
    }

    /**
     * Generates a default filename: newfile-yyyyMMddHHmmss.txt
     */
    public static String generateNewFileName() {
        String timestamp = new java.text.SimpleDateFormat(
            "yyyyMMddHHmmss",
            java.util.Locale.US
        ).format(new java.util.Date());
        return "newfile-" + timestamp + ".txt";
    }
}