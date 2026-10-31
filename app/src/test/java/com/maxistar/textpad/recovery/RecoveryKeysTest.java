package com.maxistar.textpad.recovery;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class RecoveryKeysTest {

    // ─── forDocumentUri ───────────────────────────────────────────

    @Test
    public void uriKeyIsStableSha256() {
        String uri = "content://provider/document/notes.txt";
        assertEquals(RecoveryKeys.forDocumentUri(uri),
                     RecoveryKeys.forDocumentUri(uri));
        assertTrue(RecoveryKeys.forDocumentUri(uri)
                     .matches("uri-[0-9a-f]{64}"));
    }

    @Test
    public void differentUrisHaveDifferentKeys() {
        assertNotEquals(
            RecoveryKeys.forDocumentUri("content://one/folder/notes.txt"),
            RecoveryKeys.forDocumentUri("content://two/folder/notes.txt")
        );
    }

    @Test
    public void uriKeyLengthIsCorrect() {
        // "uri-" (4) + SHA-256 hex (64) = 68
        String key = RecoveryKeys.forDocumentUri("content://any/path/file.txt");
        assertEquals(68, key.length());
        assertTrue(key.startsWith("uri-"));
    }

    @Test
    public void nullUriThrowsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
            () -> RecoveryKeys.forDocumentUri(null));
    }

    @Test
    public void emptyUriThrowsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
            () -> RecoveryKeys.forDocumentUri(""));
    }

    @Test
    public void uriKeyIsValid() {
        String key = RecoveryKeys.forDocumentUri("content://any/path");
        assertTrue(RecoveryKeys.isValid(key));
    }

    // ─── forUntitledDocument ──────────────────────────────────────

    @Test
    public void untitledKeysAreUnique() {
        String first  = RecoveryKeys.forUntitledDocument();
        String second = RecoveryKeys.forUntitledDocument();
        assertNotEquals(first, second);
    }

    @Test
    public void untitledKeyHasNewPrefix() {
        String key = RecoveryKeys.forUntitledDocument();
        assertTrue(key.startsWith("new-"));
    }

    @Test
    public void untitledKeyLengthIsCorrect() {
        // "new-" (4) + UUID (36) = 40
        String key = RecoveryKeys.forUntitledDocument();
        assertEquals(40, key.length());
    }

    @Test
    public void untitledKeyIsValid() {
        String key = RecoveryKeys.forUntitledDocument();
        assertTrue(RecoveryKeys.isValid(key));
    }

    // ─── isValid ──────────────────────────────────────────────────

    @Test
    public void nullKeyIsInvalid() {
        assertFalse(RecoveryKeys.isValid(null));
    }

    @Test
    public void emptyKeyIsInvalid() {
        assertFalse(RecoveryKeys.isValid(""));
    }

    @Test
    public void randomStringIsInvalid() {
        assertFalse(RecoveryKeys.isValid("random-string"));
    }

    @Test
    public void wrongPrefixIsInvalid() {
        // đúng format SHA-256 nhưng sai prefix
        assertFalse(RecoveryKeys.isValid(
            "doc-" + "a".repeat(64)));
    }

    @Test
    public void uriKeyWithUppercaseIsInvalid() {
        // isValid chỉ accept lowercase hex cho uri-
        assertFalse(RecoveryKeys.isValid(
            "uri-" + "A".repeat(64)));
    }

    @Test
    public void uriKeyWithWrongLengthIsInvalid() {
        assertFalse(RecoveryKeys.isValid("uri-" + "a".repeat(63)));
        assertFalse(RecoveryKeys.isValid("uri-" + "a".repeat(65)));
    }

    @Test
    public void newKeyWithWrongUuidLengthIsInvalid() {
        assertFalse(RecoveryKeys.isValid("new-" + "a".repeat(35)));
        assertFalse(RecoveryKeys.isValid("new-" + "a".repeat(37)));
    }
}