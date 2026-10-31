package com.maxistar.textpad.utils;

import static com.maxistar.textpad.utils.DocumentSaveValidator.Outcome.BASELINE_MATCH;
import static com.maxistar.textpad.utils.DocumentSaveValidator.Outcome.CONFLICT;
import static com.maxistar.textpad.utils.DocumentSaveValidator.Outcome.INTENDED_CONTENT_MATCH;
import static com.maxistar.textpad.utils.DocumentSaveValidator.Outcome.UNREADABLE;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;

public class DocumentSaveValidatorTest {

    // ─── classify: BASELINE_MATCH ─────────────────────────────────

    @Test
    public void unchangedStoredBytesMatchBaseline() {
        byte[] original = bytes("original");
        assertEquals(BASELINE_MATCH, DocumentSaveValidator.classify(
                original,
                DocumentSaveValidator.sha256(original),
                bytes("local edit")
        ));
    }

    @Test
    public void validationDoesNotRequireProviderMetadata() {
        byte[] original = bytes("same content");
        assertEquals(BASELINE_MATCH, DocumentSaveValidator.classify(
                original,
                DocumentSaveValidator.sha256(original),
                bytes("new content")
        ));
    }

    @Test
    public void emptyFileMatchesBaseline() {
        byte[] empty = bytes("");
        assertEquals(BASELINE_MATCH, DocumentSaveValidator.classify(
                empty,
                DocumentSaveValidator.sha256(empty),
                bytes("new content")
        ));
    }

    @Test
    public void baselineMatchWhenIntendedSameAsCurrentAndBaseline() {
        byte[] original = bytes("abc");
        assertEquals(BASELINE_MATCH, DocumentSaveValidator.classify(
                original,
                DocumentSaveValidator.sha256(original),
                original
        ));
    }

    // ─── classify: INTENDED_CONTENT_MATCH ────────────────────────

    @Test
    public void externallyWrittenIntendedBytesAreEquivalent() {
        byte[] original = bytes("original");
        byte[] intended = bytes("local edit");
        assertEquals(INTENDED_CONTENT_MATCH, DocumentSaveValidator.classify(
                intended,
                DocumentSaveValidator.sha256(original),
                intended
        ));
    }

    @Test
    public void intendedMatchWhenCurrentEqualsIntended() {
        byte[] intended = bytes("my changes");
        assertEquals(INTENDED_CONTENT_MATCH, DocumentSaveValidator.classify(
                intended,
                DocumentSaveValidator.sha256(bytes("original")),
                intended
        ));
    }

    // ─── classify: CONFLICT ───────────────────────────────────────

    @Test
    public void independentlyChangedVersionsConflict() {
        assertEquals(CONFLICT, DocumentSaveValidator.classify(
                bytes("external edit"),
                DocumentSaveValidator.sha256(bytes("original")),
                bytes("local edit")
        ));
    }

    @Test
    public void bomAndEncodingChangesAreByteChanges() {
        byte[] plain    = bytes("note");
        byte[] withBom  = new byte[]{(byte) 0xef, (byte) 0xbb, (byte) 0xbf, 'n', 'o', 't', 'e'};
        assertNotEquals(DocumentSaveValidator.sha256(plain),
                        DocumentSaveValidator.sha256(withBom));
        assertEquals(CONFLICT, DocumentSaveValidator.classify(
                withBom,
                DocumentSaveValidator.sha256(plain),
                bytes("local edit")
        ));
    }

    @Test
    public void whitespaceOnlyDifferenceIsConflict() {
        assertEquals(CONFLICT, DocumentSaveValidator.classify(
                bytes("hello\r\n"),
                DocumentSaveValidator.sha256(bytes("hello\n")),
                bytes("local edit")
        ));
    }

    @Test
    public void caseChangedExternallyIsConflict() {
        assertEquals(CONFLICT, DocumentSaveValidator.classify(
                bytes("HELLO"),
                DocumentSaveValidator.sha256(bytes("hello")),
                bytes("local edit")
        ));
    }

    // ─── classify: UNREADABLE ─────────────────────────────────────

    @Test
    public void nullCurrentBytesIsUnreadable() {
        assertEquals(UNREADABLE, DocumentSaveValidator.classify(
                null, "somehash", bytes("local")));
    }

    @Test
    public void nullBaselineIsUnreadable() {
        assertEquals(UNREADABLE, DocumentSaveValidator.classify(
                bytes("current"), null, bytes("local")));
    }

    @Test
    public void nullIntendedBytesIsUnreadable() {
        assertEquals(UNREADABLE, DocumentSaveValidator.classify(
                bytes("current"), "somehash", null));
    }

    @Test
    public void allNullIsUnreadable() {
        assertEquals(UNREADABLE, DocumentSaveValidator.classify(null, null, null));
    }

    // ─── sha256 ───────────────────────────────────────────────────

    @Test
    public void sha256_sameInput_sameOutput() {
        assertEquals(
                DocumentSaveValidator.sha256(bytes("hello")),
                DocumentSaveValidator.sha256(bytes("hello"))
        );
    }

    @Test
    public void sha256_differentInput_differentOutput() {
        assertNotEquals(
                DocumentSaveValidator.sha256(bytes("hello")),
                DocumentSaveValidator.sha256(bytes("world"))
        );
    }

    @Test
    public void sha256_emptyBytes_returnsHash() {
        String hash = DocumentSaveValidator.sha256(bytes(""));
        assertNotNull(hash);
        assertEquals(64, hash.length()); // SHA-256 = 32 bytes = 64 hex chars
        assertTrue(hash.matches("[0-9a-f]{64}"));
    }

    @Test
    public void sha256_knownValue() {
        assertEquals(
                "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                DocumentSaveValidator.sha256(bytes("abc"))
        );
    }

    @Test
    public void sha256_nullInput_returnsNull() {
        assertNull(DocumentSaveValidator.sha256(null));
    }

    @Test
    public void sha256_outputIsLowercase() {
        String hash = DocumentSaveValidator.sha256(bytes("test"));
        assertEquals(hash, hash.toLowerCase());
    }

    // ─── helper ───────────────────────────────────────────────────

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}