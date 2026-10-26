package com.maxistar.textpad.service;

import android.content.Context;

import androidx.appcompat.app.AppCompatDelegate;

public class ThemeService {

    SettingsService settingsService;

    public ThemeService(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    public void applyColorTheme(Context context) {
        // Always use dark mode regardless of theme setting
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
    }
}
