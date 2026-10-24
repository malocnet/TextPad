package com.maxistar.textpad;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Manages all editor tabs. Tab 0 is always the edit tab.
 * Tabs 1-4 are read-only. Max 5 tabs total.
 */
public class TabManager {

    public static final int MAX_READ_ONLY_TABS = 4;
    public static final int MAX_TABS = 5; // 1 edit + 4 read-only

    // --- Listener ---

    public interface Listener {
        void onTabsChanged();
    }

    // --- Singleton ---

    private static TabManager instance;

    public static synchronized TabManager getInstance() {
        if (instance == null) {
            instance = new TabManager();
        }
        return instance;
    }

    private TabManager() {
        // Tab 0: edit tab always exists
        tabs.add(EditorState.newEditTab());
    }

    // --- State ---

    private final List<EditorState> tabs = new ArrayList<>();
    private final List<Listener> listeners = new ArrayList<>();
    private int activeIndex = 0;

    // --- Tab access ---

    public synchronized EditorState getActiveTab() {
        return tabs.get(activeIndex);
    }

    public synchronized EditorState getEditTab() {
        return tabs.get(0);
    }

    public synchronized int getActiveIndex() {
        return activeIndex;
    }

    public synchronized List<EditorState> getTabs() {
        return Collections.unmodifiableList(new ArrayList<>(tabs));
    }

    public synchronized List<EditorState> getReadOnlyTabs() {
        List<EditorState> result = new ArrayList<>();
        for (int i = 1; i < tabs.size(); i++) {
            result.add(tabs.get(i));
        }
        return Collections.unmodifiableList(result);
    }

    public synchronized int getReadOnlyTabCount() {
        return tabs.size() - 1;
    }

    public synchronized boolean isReadOnlyFull() {
        return getReadOnlyTabCount() >= MAX_READ_ONLY_TABS;
    }

    // --- Switch tab ---

    public synchronized void setActiveIndex(int index) {
        if (index < 0 || index >= tabs.size()) return;
        activeIndex = index;
        notifyListeners();
    }

    // --- Add / remove ---

    /**
     * Adds a read-only tab. Returns false if already at max read-only tabs,
     * or if the file is already open.
     */
    public synchronized boolean addReadOnlyTab(EditorState state) {
        if (isReadOnlyFull()) return false;
        if (findByFilename(state.urlFilename) >= 0) return false;
        tabs.add(state);
        activeIndex = tabs.size() - 1;
        notifyListeners();
        return true;
    }

    /**
     * Removes a read-only tab by index. Edit tab (index 0) cannot be removed.
     */
    public synchronized void removeReadOnlyTab(int index) {
        if (index <= 0 || index >= tabs.size()) return;
        tabs.remove(index);
        if (activeIndex >= tabs.size()) {
            activeIndex = tabs.size() - 1;
        }
        notifyListeners();
    }

    /**
     * Removes a read-only tab by filename.
     */
    public synchronized void removeReadOnlyTabByFilename(String filename) {
        int index = findByFilename(filename);
        if (index > 0) {
            removeReadOnlyTab(index);
        }
    }

    // --- State snapshot ---

    /**
     * Saves current UI state back into the tab's EditorState.
     * Called before switching away from a tab.
     */
    public synchronized void snapshotTab(
            int index,
            String content,
            int cursorStart,
            int cursorEnd,
            int scrollX,
            int scrollY,
            boolean changed
    ) {
        if (index < 0 || index >= tabs.size()) return;
        EditorState state = tabs.get(index);
        state.content = content;
        state.cursorStart = cursorStart;
        state.cursorEnd = cursorEnd;
        state.scrollX = scrollX;
        state.scrollY = scrollY;
        state.changed = changed;
    }

    // --- Lookup ---

    public synchronized int findByFilename(String filename) {
        if (filename == null || filename.isEmpty()) return -1;
        for (int i = 0; i < tabs.size(); i++) {
            if (filename.equals(tabs.get(i).urlFilename)) return i;
        }
        return -1;
    }

    public synchronized boolean isAlreadyOpen(String filename) {
        return findByFilename(filename) >= 0;
    }

    // --- Listener ---

    public synchronized void addListener(Listener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public synchronized void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    private void notifyListeners() {
        for (Listener l : new ArrayList<>(listeners)) {
            l.onTabsChanged();
        }
    }
}