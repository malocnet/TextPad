package com.maxistar.textpad.service;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

public class RecentFilesServiceTest {

    // ─── addRecentFile ────────────────────────────────────────────

    @Test
    public void addingNewUrlAppendsToEnd() {
        ArrayList<String> items = list("url1", "url2");
        ArrayList<String> result = RecentFilesService.addRecentFile(items, "url3");
        assertEquals(list("url1", "url2", "url3"), result);
    }

    @Test
    public void addingToEmptyListWorks() {
        ArrayList<String> result = RecentFilesService.addRecentFile(list(), "url1");
        assertEquals(list("url1"), result);
    }

    @Test
    public void addingDuplicateMovesItToNewestPosition() {
        ArrayList<String> result = RecentFilesService.addRecentFile(
                list("url1", "url2", "url3"), "url2");
        assertEquals(list("url1", "url3", "url2"), result);
    }

    @Test
    public void addingDuplicateLeavesOnlyOneOccurrence() {
        ArrayList<String> result = RecentFilesService.addRecentFile(
                list("url1", "url2", "url3", "url2"), "url2");
        assertEquals(list("url1", "url3", "url2"), result);
    }

    @Test
    public void addingFirstItemToEmptyDoesNotTrim() {
        ArrayList<String> result = RecentFilesService.addRecentFile(list(), "url1");
        assertEquals(1, result.size());
    }

    @Test
    public void trimmingRemovesOnlyOldestEntries() {
        ArrayList<String> result = RecentFilesService.addRecentFile(
                list("url1","url2","url3","url4","url5",
                     "url6","url7","url8","url9","url10"),
                "url11");
        assertEquals(list("url2","url3","url4","url5","url6",
                          "url7","url8","url9","url10","url11"), result);
    }

    @Test
    public void trimmingKeepsExactlyMaxElements() {
        ArrayList<String> result = RecentFilesService.addRecentFile(
                list("url1","url2","url3","url4","url5",
                     "url6","url7","url8","url9","url10"),
                "url11");
        assertEquals(RecentFilesService.MAX_ELEMENTS_STORED, result.size());
    }

    @Test
    public void duplicateRemovalHappensBeforeTrimming() {
        ArrayList<String> result = RecentFilesService.addRecentFile(
                list("url1","url2","url3","url4","url5",
                     "url6","url7","url8","url9","url10"),
                "url2");
        // duplicate removed trước → size 9 → thêm url2 → size 10, không trim
        assertEquals(list("url1","url3","url4","url5","url6",
                          "url7","url8","url9","url10","url2"), result);
    }

    @Test
    public void originalListIsNotMutated() {
        ArrayList<String> items = list("url1", "url2", "url3");
        ArrayList<String> copy  = new ArrayList<>(items);
        RecentFilesService.addRecentFile(items, "url2");
        assertEquals(copy, items); // items gốc không bị thay đổi
    }

    @Test
    public void addingSameUrlToListOfOneKeepsOnlyOne() {
        ArrayList<String> result = RecentFilesService.addRecentFile(
                list("url1"), "url1");
        assertEquals(list("url1"), result);
        assertEquals(1, result.size());
    }

    // ─── getLastFiles ─────────────────────────────────────────────

    @Test
    public void lastFilesAreReturnedNewestFirst() {
        ArrayList<String> result = RecentFilesService.getLastFiles(
                list("url1","url2","url3","url4"), 0);
        assertEquals(list("url4","url3","url2","url1"), result);
    }

    @Test
    public void lastFilesRespectSkipArgument() {
        ArrayList<String> result = RecentFilesService.getLastFiles(
                list("url1","url2","url3","url4"), 1);
        assertEquals(list("url3","url2","url1"), result);
    }

    @Test
    public void lastFilesRespectDisplayLimit() {
        ArrayList<String> result = RecentFilesService.getLastFiles(
                list("url1","url2","url3","url4","url5","url6","url7"), 0);
        assertEquals(list("url7","url6","url5","url4","url3"), result);
        assertEquals(RecentFilesService.MAX_ELEMENTS_SHOWN, result.size());
    }

    @Test
    public void getLastFilesOnEmptyListReturnsEmpty() {
        ArrayList<String> result = RecentFilesService.getLastFiles(list(), 0);
        assertEquals(list(), result);
    }

    @Test
    public void getLastFilesSkipBeyondSizeReturnsEmpty() {
        ArrayList<String> result = RecentFilesService.getLastFiles(
                list("url1","url2"), 10);
        assertEquals(list(), result);
    }

    @Test
    public void getLastFilesSkipZeroWithExactlyMaxShown() {
        // đúng MAX_ELEMENTS_SHOWN items → trả về hết, không bị cắt
        ArrayList<String> result = RecentFilesService.getLastFiles(
                list("url1","url2","url3","url4","url5"), 0);
        assertEquals(list("url5","url4","url3","url2","url1"), result);
        assertEquals(RecentFilesService.MAX_ELEMENTS_SHOWN, result.size());
    }

    @Test
    public void getLastFilesSkipAndLimitCombined() {
        // 7 items, skip 2, limit 5 → chỉ còn 5 items sau skip
        ArrayList<String> result = RecentFilesService.getLastFiles(
                list("url1","url2","url3","url4","url5","url6","url7"), 2);
        assertEquals(list("url5","url4","url3","url2","url1"), result);
    }

    // ─── helper ───────────────────────────────────────────────────

    private ArrayList<String> list(String... values) {
        return new ArrayList<>(Arrays.asList(values));
    }
}