package com.maxistar.textpad.service;

import android.app.UiModeManager;
import android.content.Context;
import android.os.Build;

import androidx.appcompat.app.AppCompatDelegate;

public class ThemeService {

    SettingsService settingsService;

    public ThemeService(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    public void applyColorTheme(Context context) {
        String theme = settingsService.getColorThemeType();
        if (SettingsService.COLOR_THEME_DARK.equals(theme)) {
            applyDarkTheme(context);
        } else if (SettingsService.COLOR_THEME_LIGHT.equals(theme)) {
            applyLightTheme(context);
        } else {
            applyAutoTheme(context);
        }
    }

    private void applyAutoTheme(Context context) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
    }

    private void applyDarkTheme(Context context) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
    }

    private void applyLightTheme(Context context) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
    }
}
