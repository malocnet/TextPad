package com.maxistar.textpad;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TabManager {

    public static final int MAX_TABS = 5;

    public interface Listener {
        void onTabsChanged();
    }

    public static class TabInfo {
        public String filename;
        public boolean isMainTab;
        public String content;
        public int cursorPosition;
        public boolean changed;

        public TabInfo(String filename, boolean isMainTab) {
            this.filename = filename;
            this.isMainTab = isMainTab;
            this.content = "";
            this.cursorPosition = 0;
            this.changed = false;
        }
    }

    private static TabManager instance;

    public static synchronized TabManager getInstance() {
        if (instance == null) {
            instance = new TabManager();
        }
        return instance;
    }

    private final List<TabInfo> tabs = new ArrayList<>();
    private final List<Listener> listeners = new ArrayList<>();
    private int activeTabIndex = 0;

    private TabManager() {}

    public synchronized boolean addTab(String filename, boolean isMainTab) {
        for (TabInfo tab : tabs) {
            if (tab.filename.equals(filename)) {
                return true; // đã có rồi
            }
        }
        if (tabs.size() >= MAX_TABS) {
            return false;
        }
        tabs.add(new TabInfo(filename, isMainTab));
        activeTabIndex = tabs.size() - 1;
        notifyListeners();
        return true;
    }

    public synchronized void removeTab(String filename) {
        int index = indexOfTab(filename);
        if (index < 0) return;
        tabs.remove(index);
        if (activeTabIndex >= tabs.size()) {
            activeTabIndex = tabs.size() - 1;
        }
        if (activeTabIndex < 0) activeTabIndex = 0;
        notifyListeners();
    }

    public synchronized void saveTabState(String filename, String content, int cursorPosition, boolean changed) {
        for (TabInfo tab : tabs) {
            if (tab.filename.equals(filename)) {
                tab.content = content;
                tab.cursorPosition = cursorPosition;
                tab.changed = changed;
                return;
            }
        }
    }

    public synchronized TabInfo getActiveTab() {
        if (tabs.isEmpty()) return null;
        return tabs.get(activeTabIndex);
    }

    public synchronized void setActiveTab(String filename) {
        int index = indexOfTab(filename);
        if (index >= 0) {
            activeTabIndex = index;
            notifyListeners();
        }
    }

    public synchronized int getActiveTabIndex() {
        return activeTabIndex;
    }

    public synchronized List<TabInfo> getTabs() {
        return Collections.unmodifiableList(new ArrayList<>(tabs));
    }

    public synchronized int getTabCount() {
        return tabs.size();
    }

    public synchronized boolean hasTab(String filename) {
        return indexOfTab(filename) >= 0;
    }

    private int indexOfTab(String filename) {
        for (int i = 0; i < tabs.size(); i++) {
            if (tabs.get(i).filename.equals(filename)) return i;
        }
        return -1;
    }

    public synchronized void addListener(Listener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public synchronized void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    private void notifyListeners() {
        for (Listener listener : new ArrayList<>(listeners)) {
            listener.onTabsChanged();
        }
    }
}