package com.maxistar.textpad.test;

import com.maxistar.textpad.ServiceLocator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ServiceLocatorTest {

    @BeforeEach
    public void resetSingleton() throws Exception {
        // Reset singleton giữa các test để tránh ảnh hưởng lẫn nhau
        java.lang.reflect.Field field = ServiceLocator.class.getDeclaredField("instance");
        field.setAccessible(true);
        field.set(null, null);
    }

    // ─── getInstance ──────────────────────────────────────────────

    @Test
    public void getInstance_returnsNonNull() {
        ServiceLocator locator = ServiceLocator.getInstance();
        assertNotNull(locator);
    }

    @Test
    public void getInstance_returnsSameInstance() {
        ServiceLocator first  = ServiceLocator.getInstance();
        ServiceLocator second = ServiceLocator.getInstance();
        assertSame(first, second);
    }

    @Test
    public void getInstance_calledMultipleTimes_alwaysSame() {
        ServiceLocator a = ServiceLocator.getInstance();
        ServiceLocator b = ServiceLocator.getInstance();
        ServiceLocator c = ServiceLocator.getInstance();
        assertSame(a, b);
        assertSame(b, c);
    }

    // ─── getRecentFilesService ────────────────────────────────────

    @Test
    public void getRecentFilesService_returnsNonNull() {
        assertNotNull(ServiceLocator.getInstance().getRecentFilesService());
    }

    @Test
    public void getRecentFilesService_returnsSameInstance() {
        ServiceLocator locator = ServiceLocator.getInstance();
        assertSame(locator.getRecentFilesService(),
                   locator.getRecentFilesService());
    }

    // ─── getAlternativeUrlsService ────────────────────────────────

    @Test
    public void getAlternativeUrlsService_returnsNonNull() {
        assertNotNull(ServiceLocator.getInstance().getAlternativeUrlsService());
    }

    @Test
    public void getAlternativeUrlsService_returnsSameInstance() {
        ServiceLocator locator = ServiceLocator.getInstance();
        assertSame(locator.getAlternativeUrlsService(),
                   locator.getAlternativeUrlsService());
    }

    // ─── getWakeLockService ───────────────────────────────────────

    @Test
    public void getWakeLockService_returnsNonNull() {
        assertNotNull(ServiceLocator.getInstance().getWakeLockService());
    }

    @Test
    public void getWakeLockService_returnsSameInstance() {
        ServiceLocator locator = ServiceLocator.getInstance();
        assertSame(locator.getWakeLockService(),
                   locator.getWakeLockService());
    }
}