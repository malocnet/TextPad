package com.maxistar.textpad.activities;

import java.io.DataInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.text.DateFormat;

import android.provider.DocumentsContract;
import android.content.UriPermission;
import android.widget.Button;
import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.Insets;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.ParcelFileDescriptor;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintJob;
import android.print.PrintManager;
import android.text.Editable;
import android.text.InputType;
import android.text.Layout;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.BackgroundColorSpan;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.ViewParent;
import android.view.WindowInsets;
import android.view.inputmethod.EditorInfo;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SearchView;
import android.widget.Toast;

import com.maxistar.textpad.FileDialog;
import com.maxistar.textpad.R;
import com.maxistar.textpad.SelectionMode;
import com.maxistar.textpad.ServiceLocator;
import com.maxistar.textpad.service.SettingsService;
import com.maxistar.textpad.TPStrings;
import com.maxistar.textpad.service.AlternativeUrlsService;
import com.maxistar.textpad.service.RecentFilesService;
import com.maxistar.textpad.service.ThemeService;
import com.maxistar.textpad.recovery.RecoveryDraft;
import com.maxistar.textpad.recovery.RecoveryKeys;
import com.maxistar.textpad.recovery.RecoveryMetadata;
import com.maxistar.textpad.recovery.RecoveryRepository;
import com.maxistar.textpad.recovery.RecoveryWriter;
import com.maxistar.textpad.utils.EditTextUndoRedo;
import com.maxistar.textpad.utils.DocumentSaveValidator;
import com.maxistar.textpad.utils.FileNameHelper;
import com.maxistar.textpad.utils.System;
import com.maxistar.textpad.utils.TextConverter;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.menu.MenuBuilder;
import androidx.appcompat.widget.Toolbar;

import android.content.DialogInterface;
import android.text.SpannableString;
import android.text.method.LinkMovementMethod;
import android.text.style.UnderlineSpan;
import android.text.style.ClickableSpan;
import android.view.View;
import android.widget.TextView;

public class EditorActivity extends AppCompatActivity {

    private static final String STATE_FILENAME = "filename";
    private static final String STATE_CHANGED = "changed";
    private static final String STATE_CURSOR_POSITION = "cursor-position";
    private static final String STATE_CURSOR_END = "cursor-end";
    private static final String STATE_RECOVERY_KEY = "recovery-key";
    private static final String STATE_IS_TEMP_FILE = "is-temp-file";

    private static final int REQUEST_OPEN = 1;
    private static final int REQUEST_SAVE = 2;
    private static final int REQUEST_SETTINGS = 3;
    private static final int ACTION_CREATE_FILE = 4;
    private static final int ACTION_OPEN_FILE = 5;

    private static final int DO_NOTHING = 0;
    private static final int DO_OPEN = 1;
    private static final int DO_NEW = 2;
    private static final int DO_EXIT = 3;
    private static final int DO_OPEN_RECENT = 4;
    private static final int DO_SHOW_SETTINGS = 5;
    private static final int RECENT_MENU_BASE_ID = 0x00e00000;
    private static final int RECENT_MENU_MAX = 15;  
    private static final int DRAFT_MENU_BASE_ID = 0x00f00000;
    private static final int DRAFT_MENU_MAX = 50;

    String[] mimeTypes = {
            "*/*",
            "text/*",
            "plain/*",
            "text/javascript",
            "application/ecmascript",
            "application/javascript"
    };

    private EditText mText;
    private ScrollView scrollView;
    private LinearLayout linearLayout;

    String urlFilename = TPStrings.EMPTY;

    Uri lastTriedSystemUri = null;

    boolean changed = false;
    boolean exitDialogShown = false;

    private boolean isTempFile = false;

    private int next_action = DO_NOTHING;
    private String next_action_filename = "";

    static int selectionStart = 0;
    private int selectionEnd = 0;

    private RecoveryRepository recoveryRepository;
    private RecoveryWriter recoveryWriter;
    private String recoveryKey;
    private long editorGeneration = 0;
    private boolean suppressRecoveryTracking = false;
    private boolean recoveryDecisionPending = false;
    private boolean recoveryDialogShowing = false;
    private RecoveryDraft pendingRecoveryDraft;
    private Runnable pendingRecoveryDiscardLoader;
    private Long originalSize;
    private Long originalLastModified;
    private String originalContentSha256;
    private SaveRequest pendingExternalConflict;
    private boolean nextSaveCreatesDocument;
    private boolean hasEnteredForeground;
    private Menu optionsMenu;

    private static final class SaveRequest {
        final long generation;
        final String recoveryKey;
        final byte[] bytes;

        SaveRequest(long generation, String recoveryKey, byte[] bytes) {
            this.generation = generation;
            this.recoveryKey = recoveryKey;
            this.bytes = bytes;
        }
    }

    SettingsService settingsService;
    RecentFilesService recentFilesService;
    AlternativeUrlsService alternativeUrlsService;

    private QueryTextListener queryTextListener;
    private TextWatcher textWatcher;
    EditTextUndoRedo editTextUndoRedo;
    WebView mWebView;

    private int cachedMaxHorizontalScroll = 0;
    private int cachedHorizontalScrollEditorWidth = -1;
    private boolean horizontalScrollBoundsDirty = true;
    private int horizontalScrollBoundsCalculationCount = 0;

    // ─────────────────────────────────────────────────────────────
    // Temp / AutoSave file helpers
    // ─────────────────────────────────────────────────────────────

    private File getSimpleTextEditorDir() {
        // Dùng thư mục private của app, không cần xin quyền
        File dir = new File(getFilesDir(), "TextPad");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    // private File getSimpleTextEditorDir() {
    //     File documents = new File(
    //         android.os.Environment.getExternalStoragePublicDirectory(
    //             android.os.Environment.DIRECTORY_DOCUMENTS
    //         ),
    //         "TextPad"
    //     );
    //     if (documents.mkdirs() || documents.exists()) return documents;
    //     File base = getExternalFilesDir(null);
    //     if (base == null) base = getFilesDir();
    //     return new File(base, "TextPad");
    // }

    private File createNewTempFile() {
        String timestamp = new SimpleDateFormat("yyyyMMddHHmm", Locale.US).format(new Date());
        String uuid = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String name = "newfile-" + timestamp + "-" + uuid + ".txt";
        File dir = getSimpleTextEditorDir();
        dir.mkdirs();
        return new File(dir, name);
    }

    private String buildTimestampedName(String originalName) {
        String timestamp = new SimpleDateFormat("yyyyMMddHHmm", Locale.US).format(new Date());
        String uuid = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        int dot = originalName.lastIndexOf('.');
        if (dot > 0) {
            String base = originalName.substring(0, dot);
            String ext = originalName.substring(dot + 1);
            return base + "-" + timestamp + "-" + uuid + "." + ext;
        } else {
            return originalName + "-" + timestamp + "-" + uuid;
        }
    }

    private boolean isInTempDir(String path) {
        if (path == null || path.isEmpty()) return false;
        try {
            File dir = getSimpleTextEditorDir().getCanonicalFile();
            File f = new File(path).getCanonicalFile();
            return f.getParentFile() != null &&
                f.getParentFile().getCanonicalPath().equals(dir.getCanonicalPath());
        } catch (IOException e) {
            return false;
        }
    }

    private void deleteTempFile() {
        if (!isTempFile || urlFilename == null || urlFilename.isEmpty()) return;
        try {
            String path = Uri.parse(urlFilename).getPath();
            if (path != null) new File(path).delete();
        } catch (Exception ignored) {}
        isTempFile = false;
    }

    private boolean isFileInTextPadDir(Uri uri) {
        try {
            String path = uri.getPath();
            if (path == null) return false;
            File f = new File(path).getCanonicalFile();
            File dir = getSimpleTextEditorDir().getCanonicalFile();
            return f.getParentFile() != null &&
                f.getParentFile().getCanonicalPath().equals(dir.getCanonicalPath());
        } catch (IOException e) { return false; }
    }

    private boolean isNewfileName(Uri uri) {
        String path = uri.getPath();
        if (path == null) return false;
        return new File(path).getName().startsWith("newfile-");
    }

    private void updateRecentFiles(Menu menu) {
        MenuItem recentMenuItem = menu.findItem(R.id.menu_document_open_last);
        if (!settingsService.isShowLastEditedFiles()) {
            recentMenuItem.setVisible(false);
            return;
        }
        ArrayList<String> recentFiles = recentFilesService.getLastFiles(0, getApplicationContext());
        recentMenuItem.setVisible(!recentFiles.isEmpty());
        android.view.SubMenu sub = recentMenuItem.getSubMenu();
        if (sub == null) return;
        sub.clear();
        for (int i = 0; i < recentFiles.size(); i++) {
            String label = FileNameHelper.getFilenameByUri(getApplicationContext(), recentFiles.get(i));
            sub.add(Menu.NONE, RECENT_MENU_BASE_ID + i, i, label);
        }
    }

    private void autoSaveToNotsave() {
        if (!changed) return;
        String content = mText.getText().toString();
        if (content.trim().isEmpty()) return;
        try {
            String displayName = currentDisplayName();
            if (displayName == null || displayName.isEmpty() || displayName.startsWith("content://")) {
                displayName = "unsaved.txt";
            }
            String fileName = buildTimestampedName(displayName);
            File dir = getSimpleTextEditorDir();
            dir.mkdirs();
            File outFile = new File(dir, fileName);
            String encoded = applyEndings(content);
            try (FileOutputStream fos = new FileOutputStream(outFile)) {
                fos.write(encoded.getBytes(settingsService.getFileEncoding()));
            }
            recentFilesService.addRecentFile(outFile.toURI().toString(), getApplicationContext());
        } catch (Exception ignored) {}
    }

    private void autoSaveTempBeforeNew() {
        if (!isTempFile) return;
        String content = mText.getText().toString();
        if (content.trim().isEmpty()) {
            deleteTempFile();
            return;
        }
        try {
            String path = Uri.parse(urlFilename).getPath();
            if (path == null) return;
            File f = new File(path);
            String encoded = applyEndings(content);
            try (FileOutputStream fos = new FileOutputStream(f)) {
                fos.write(encoded.getBytes(settingsService.getFileEncoding()));
            }
        } catch (Exception ignored) {}
    }

    // public static void verifyPermissions(Activity activity) {
    //     if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
    //         if (!android.os.Environment.isExternalStorageManager()) {
    //             new AlertDialog.Builder(activity)
    //                 .setTitle(R.string.permission_storage_title)
    //                 .setMessage(R.string.permission_storage_message)
    //                 .setPositiveButton(R.string.permission_storage_button, (dialog, which) -> {
    //                     Intent intent = new Intent(
    //                         android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
    //                         Uri.parse("package:" + activity.getPackageName())
    //                     );
    //                     activity.startActivity(intent);
    //                 })
    //                 .setNegativeButton(R.string.Cancel, null)
    //                 .show();
    //         }
    //         return;
    //     }

    //     if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return;
    //     int permission = activity.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE);
    //     if (permission != PackageManager.PERMISSION_GRANTED) {
    //         activity.requestPermissions(new String[]{
    //             Manifest.permission.READ_EXTERNAL_STORAGE,
    //             Manifest.permission.WRITE_EXTERNAL_STORAGE,
    //             Manifest.permission.WAKE_LOCK,
    //         }, 1);
    //     }
    // }

    // ─────────────────────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────────────────────

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        settingsService = ServiceLocator.getInstance().getSettingsService(this.getApplicationContext());
        recentFilesService = ServiceLocator.getInstance().getRecentFilesService();
        alternativeUrlsService = ServiceLocator.getInstance().getAlternativeUrlsService();
        recoveryRepository = new RecoveryRepository(getApplicationContext());
        recoveryRepository.cleanupIncompleteArtifacts();
        recoveryWriter = new RecoveryWriter(recoveryRepository);

        if (simpleScrolling()) {
            setContentView(R.layout.main_simple_scrolling);
        } else {
            setContentView(R.layout.main);
        }
        Toolbar toolbar = findViewById(R.id.editor_toolbar);
        setSupportActionBar(toolbar);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            applyEdgeToEdgeInsets();
        }
        mText = this.findViewById(R.id.editText1);
        mText.setBackgroundResource(android.R.color.transparent);
        mText.setOnTouchListener(new TwoFingerPanTouchListener());
        mText.addOnLayoutChangeListener((view, left, top, right, bottom,
                                         oldLeft, oldTop, oldRight, oldBottom) -> {
            if ((right - left) != (oldRight - oldLeft)
                    || (bottom - top) != (oldBottom - oldTop)) {
                invalidateHorizontalScrollBounds();
            }
        });
        editTextUndoRedo = new EditTextUndoRedo(mText, this);
        setTextWatcher();

        if (simpleScrolling()) {
            linearLayout = findViewById(R.id.linear_layout);
        } else {
            scrollView = findViewById(R.id.vscroll);
        }
        applyPreferences();
        if (!settingsService.isAutoWrapping()) {
            disableEditorAutowrapping();
        }

        if (savedInstanceState != null) {
            restoreState(savedInstanceState);
            restoreEditorContent();
        } else {
            // verifyPermissions(this);

            Intent i = this.getIntent();
            if (TPStrings.ACTION_VIEW.equals(i.getAction())) {
                Uri u = i.getData();
                if (u != null) {
                    openFileByUri(u);
                }
            } else {
                if (isFilenameEmpty()) {
                    if (settingsService.isOpenLastFile()) {
                        openLastFile();
                    } else {
                        offerActiveUntitledRecovery();
                    }
                }
            }
        }

        updateTitle();
        mText.requestFocus();
        settingsService.applyLocale(this.getBaseContext());
    }

    private boolean simpleScrolling() {
        return settingsService.isUseSimpleScrolling();
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private void applyEdgeToEdgeInsets() {
        View editorRoot = findViewById(R.id.editor_root);
        int initialPaddingLeft = editorRoot.getPaddingLeft();
        int initialPaddingTop = editorRoot.getPaddingTop();
        int initialPaddingRight = editorRoot.getPaddingRight();
        int initialPaddingBottom = editorRoot.getPaddingBottom();
        editorRoot.setOnApplyWindowInsetsListener((view, windowInsets) -> {
            Insets bars = windowInsets.getInsets(
                    WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout()
            );
            Insets ime = windowInsets.getInsets(WindowInsets.Type.ime());
            view.setPadding(
                    initialPaddingLeft + bars.left,
                    initialPaddingTop + bars.top,
                    initialPaddingRight + bars.right,
                    initialPaddingBottom + Math.max(bars.bottom, ime.bottom)
            );
            view.post(() -> requestFocusedCaretOnScreen(view));
            return windowInsets;
        });
        editorRoot.requestApplyInsets();
    }

    private void requestFocusedCaretOnScreen(View editorRoot) {
        EditText editor = editorRoot.findViewById(R.id.editText1);
        if (editor == null || !editor.isFocused() || editor.getLayout() == null) {
            return;
        }
        int selection = editor.getSelectionStart();
        if (selection < 0) return;
        int line = editor.getLayout().getLineForOffset(selection);
        Rect caret = new Rect(
                editor.getTotalPaddingLeft(),
                editor.getTotalPaddingTop() + editor.getLayout().getLineTop(line),
                Math.max(editor.getTotalPaddingLeft() + 1,
                        editor.getWidth() - editor.getTotalPaddingRight()),
                editor.getTotalPaddingTop() + editor.getLayout().getLineBottom(line)
        );
        editor.requestRectangleOnScreen(caret, false);
    }

    protected void onResume() {
        super.onResume();

        if (hasEnteredForeground && pendingExternalConflict == null) {
            validateOpenDocumentOnForeground();
        }
        hasEnteredForeground = true;

        mText.addTextChangedListener(textWatcher);
        applyStoredSelection();

        if (pendingExternalConflict != null) {
            mText.post(() -> {
                if (pendingExternalConflict != null && !isFinishing()) {
                    showExternalChangeDialog(pendingExternalConflict);
                }
            });
        }

        if (recoveryDecisionPending && !recoveryDialogShowing && pendingRecoveryDraft != null) {
            mText.post(this::showPendingRecoveryDialog);
        }

        if (SettingsService.isLanguageWasChanged()) {
            Intent intent = getIntent();
            finish();
            startActivity(intent);
        }

        if (settingsService.useWakeLock()) {
            ServiceLocator.getInstance().getWakeLockService().acquireLock(this.getApplicationContext());
        }
    }

    protected void onPause() {
        if (settingsService.isAutosavingActive() && !isFilenameEmpty() && !isTempFile && isChanged()) {
            this.saveFileIfNamed(true);
        }

        mText.removeTextChangedListener(textWatcher);
        selectionStart = mText.getSelectionStart();
        selectionEnd = mText.getSelectionEnd();
        if (changed) {
            flushRecoverySnapshot();
        }
        if (settingsService.useWakeLock()) {
            ServiceLocator.getInstance().getWakeLockService().releaseLock();
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (isTempFile && changed) {
            String content = mText.getText().toString();
            if (content.trim().isEmpty()) {
                deleteTempFile();
            } else {
                autoSaveTempBeforeNew();
            }
        }
        recoveryWriter.shutdown();
        editTextUndoRedo.disconnect();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (this.changed && !exitDialogShown) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.You_have_made_some_changes)
                    .setMessage(R.string.Are_you_sure_to_quit)
                    .setNegativeButton(R.string.Yes, (arg0, arg1) -> {
                        discardCurrentRecovery();
                        if (isTempFile && mText.getText().toString().trim().isEmpty()) {
                            deleteTempFile();
                        }
                        EditorActivity.super.onBackPressed();
                        exitDialogShown = false;
                    })
                    .setPositiveButton(R.string.No, (arg0, arg1) -> {
                        exitDialogShown = false;
                    })
                    .setOnCancelListener(arg0 -> EditorActivity.super.onBackPressed())
                    .create()
                    .show();
            exitDialogShown = true;
        } else {
            super.onBackPressed();
        }
    }

    // ─────────────────────────────────────────────────────────────
    // State save/restore
    // ─────────────────────────────────────────────────────────────

    private void restoreState(Bundle state) {
        urlFilename = state.getString(STATE_FILENAME);
        changed = state.getBoolean(STATE_CHANGED);
        selectionStart = state.getInt(STATE_CURSOR_POSITION);
        selectionEnd = state.getInt(STATE_CURSOR_END, selectionStart);
        recoveryKey = state.getString(STATE_RECOVERY_KEY);
        isTempFile = state.getBoolean(STATE_IS_TEMP_FILE, false);
    }

    public void onSaveInstanceState(@NonNull Bundle outState) {
        if (changed) {
            flushRecoverySnapshot();
        }
        super.onSaveInstanceState(outState);
        outState.putString(STATE_FILENAME, urlFilename);
        outState.putBoolean(STATE_CHANGED, changed);
        outState.putInt(STATE_CURSOR_POSITION, mText.getSelectionStart());
        outState.putInt(STATE_CURSOR_END, mText.getSelectionEnd());
        outState.putString(STATE_RECOVERY_KEY, recoveryKey);
        outState.putBoolean(STATE_IS_TEMP_FILE, isTempFile);
    }

    protected void onStop() {
        super.onStop();
    }

    // ─────────────────────────────────────────────────────────────
    // New / Clear file
    // ─────────────────────────────────────────────────────────────

    public void newFile() {
        handleCurrentFileBeforeSwitch();
        clearFile();
    }

    private void handleCurrentFileBeforeSwitch() {
        if (isTempFile) {
            autoSaveTempBeforeNew();
        } else if (changed) {
            autoSaveToNotsave();
        }
    }

    public void clearFile() {
        discardCurrentRecovery();
        editorGeneration = 0;
        originalSize = null;
        originalLastModified = null;
        originalContentSha256 = null;
        selectionStart = 0;
        selectionEnd = 0;
        setEditorText(TPStrings.EMPTY, false);

        File tempFile = createNewTempFile();
        try { tempFile.createNewFile(); } catch (IOException ignored) {}
        urlFilename = Uri.fromFile(tempFile).toString();
        isTempFile = true;

        initEditor();
        updateTitle();
    }

    // ─────────────────────────────────────────────────────────────
    // Open file
    // ─────────────────────────────────────────────────────────────

    protected void openFile() {
        handleCurrentFileBeforeSwitch();
        openNewFile();
    }

    private void openFileByName(String filename) {
        if (useAndroidManager()) {
            Uri uri = Uri.parse(filename);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                this.openNamedFile(uri);
            }
        } else {
            this.openNamedFileLegacy(filename);
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Save file
    // ─────────────────────────────────────────────────────────────

    protected void saveFile() {
        if (isFilenameEmpty() || isTempFile) {
            saveAs();
        } else {
            saveFileIfNamed();
        }
    }

    protected void saveAs() {
        if (useAndroidManager()) {
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
            String suggestedName = TPStrings.NEW_FILE_TXT;
            if (isTempFile && !urlFilename.isEmpty()) {
                try {
                    String path = Uri.parse(urlFilename).getPath();
                    if (path != null) suggestedName = new File(path).getName();
                } catch (Exception ignored) {}
            }
            intent.putExtra(Intent.EXTRA_TITLE, suggestedName);
            intent.putExtra("android.content.extra.SHOW_ADVANCED", true);
            startActivityForResult(intent, ACTION_CREATE_FILE);
        } else {
            Intent intent = new Intent(this.getBaseContext(), FileDialog.class);
            this.startActivityForResult(intent, REQUEST_SAVE);
        }
    }

    protected void saveFileIfNamed() {
        saveFileIfNamed(false);
    }

    private void saveFileIfNamed(boolean autosave) {
        guardedSaveNamedFile(autosave);
    }

    // ─────────────────────────────────────────────────────────────
    // onActivityResult
    // ─────────────────────────────────────────────────────────────

    @SuppressLint("WrongConstant")
    public synchronized void onActivityResult(
            final int requestCode,
            int resultCode,
            final Intent data
    ) {
        if (requestCode == REQUEST_SAVE) {
            if (resultCode == Activity.RESULT_OK) {
                setFilename(data.getStringExtra(TPStrings.RESULT_PATH));
                nextSaveCreatesDocument = true;
                this.saveFileWithConfirmation();
            }
        } else if (requestCode == REQUEST_OPEN) {
            if (resultCode == Activity.RESULT_OK) {
                this.openNamedFileLegacy(data.getStringExtra(TPStrings.RESULT_PATH));
            }
        } else if (requestCode == REQUEST_SETTINGS) {
            applyPreferences();
            recreate();
        } else if (requestCode == ACTION_OPEN_FILE && resultCode == Activity.RESULT_OK) {
            if (data != null) {
                Uri uri = data.getData();
                if (uri != null) {
                    persistUriPermissions(data);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                        openNamedFile(uri);
                    }
                }
            }
        } else if (requestCode == ACTION_CREATE_FILE) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                persistUriPermissions(data);
                Uri uri = data.getData();
                if (uri != null) {
                    deleteTempFile();
                    setFilename(uri.toString());
                    nextSaveCreatesDocument = true;
                    this.saveFileWithConfirmation();
                }
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    // ─────────────────────────────────────────────────────────────
    // Filename helpers
    // ─────────────────────────────────────────────────────────────

    private void setFilename(String value) {
        this.urlFilename = value;
        isTempFile = false;
        storeLastFileName(value);
    }

    private void storeLastFileName(String value) {
        if (isFilenameEmpty()) return;
        if (isTempFile) return;
        if (!settingsService.isShowLastEditedFiles()) return;
        recentFilesService.addRecentFile(value, getApplicationContext());
    }

    private boolean suppressOpenToast = false;

    private boolean isFilenameEmpty() {
        return urlFilename == null || urlFilename.equals(TPStrings.EMPTY);
    }

    private String getFilename() {
        return urlFilename;
    }

    // ─────────────────────────────────────────────────────────────
    // Exit
    // ─────────────────────────────────────────────────────────────

    protected void exitApplication() {
        if (isTempFile && mText.getText().toString().trim().isEmpty()) {
            deleteTempFile();
        }
        discardCurrentRecovery();
        System.exitFromApp(EditorActivity.this);
    }

    // ─────────────────────────────────────────────────────────────
    // Draft files
    // ─────────────────────────────────────────────────────────────

    private List<File> loadAllDraftFiles() {
        List<File> drafts = new ArrayList<>();
        File dir = getSimpleTextEditorDir();
        if (!dir.exists()) return drafts;
        File[] files = dir.listFiles(f -> f.isFile() && f.getName().startsWith("newfile-"));
        if (files == null) return drafts;
        java.util.Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        for (File f : files) drafts.add(f);
        return drafts;
    }

    private void updateDraftFiles(Menu menu) {
        MenuItem draftsMenuItem = menu.findItem(R.id.menu_drafts);
        if (draftsMenuItem == null) return;
        List<File> drafts = loadAllDraftFiles();
        draftsMenuItem.setVisible(!drafts.isEmpty());
        android.view.SubMenu sub = draftsMenuItem.getSubMenu();
        if (sub == null) return;
        sub.clear();
        int limit = Math.min(drafts.size(), DRAFT_MENU_MAX);
        for (int i = 0; i < limit; i++) {
            sub.add(Menu.NONE, DRAFT_MENU_BASE_ID + i, i, formatDraftLabel(drafts.get(i)));
        }
    }

    private String formatDraftLabel(File f) {
        try {
            // Tên file: newfile-yyyyMMddHHmm-uuid.txt
            // split với limit=3 → ["newfile", "yyyyMMddHHmm", "uuid.txt"]
            String[] parts = f.getName().split("-", 3);
            if (parts.length == 3) {
                Date date = new SimpleDateFormat("yyyyMMddHHmm", Locale.US).parse(parts[1]);
                String formatted = new SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(date);
                String preview = readFilePreview(f, 40);
                return preview.isEmpty() ? formatted + "  (empty)" : formatted + "  " + preview;
            }
        } catch (Exception ignored) {}
        return f.getName();
    }

    private String readFilePreview(File f, int maxChars) {
        if (f.length() == 0) return "";
        try (FileInputStream fis = new FileInputStream(f)) {
            byte[] buf = new byte[Math.min((int) f.length(), 256)];
            int read = fis.read(buf);
            if (read <= 0) return "";
            String text = new String(buf, 0, read, settingsService.getFileEncoding()).trim();
            // Lấy dòng đầu tiên
            int newline = text.indexOf('\n');
            if (newline > 0) text = text.substring(0, newline).trim();
            if (text.length() > maxChars) text = text.substring(0, maxChars) + "…";
            return text;
        } catch (Exception e) { return ""; }
    }

    // ─────────────────────────────────────────────────────────────
    // File open helpers
    // ─────────────────────────────────────────────────────────────

    private void openFileByUri(Uri u) {
        if (useAndroidManager()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                if (settingsService.isAlternativeFileAccess() &&
                        alternativeUrlsService.hasAlternativeUrl(u, getApplicationContext())) {
                    openNamedFile(alternativeUrlsService.getAlternativeUrl(u, getApplicationContext()));
                } else {
                    openNamedFile(u);
                }
            }
        } else {
            openNamedFileLegacy(u.getPath());
        }
    }

    private void setTextWatcher() {
        textWatcher = new TextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                if (!suppressRecoveryTracking && changed) {
                    scheduleRecoverySnapshot();
                }
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }
            
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                invalidateHorizontalScrollBounds();
                if (suppressRecoveryTracking) return;
                editorGeneration++;
                if (!changed) {
                    changed = true;
                    updateTitle();
                }
                updateUndoRedoMenuItems();
            }
        };
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (event.isCtrlPressed()) {
            if (keyCode == KeyEvent.KEYCODE_S) { saveFile(); return true; }
            else if (keyCode == KeyEvent.KEYCODE_Z) { editUndo(); return true; }
            else if (keyCode == KeyEvent.KEYCODE_Y) { editRedo(); return true; }
            else if (keyCode == KeyEvent.KEYCODE_H) { showFindReplaceBar(); return true; }
        }
        return super.onKeyDown(keyCode, event);
    }

    private boolean isChanged() { return changed; }

    private void restoreEditorContent() {
        if (changed && recoveryKey != null) {
            RecoveryDraft draft = recoveryRepository.load(recoveryKey, documentIdentityUri());
            if (draft != null) {
                showRecoveryDialog(draft, this::loadStoredDocumentAfterDiscard);
                return;
            }
        }
        loadStoredDocumentAfterDiscard();
    }

    private void loadStoredDocumentAfterDiscard() {
        if (isFilenameEmpty()) {
            setEditorText(TPStrings.EMPTY, false);
            return;
        }
        if (useAndroidManager()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                openNamedFileDirect(Uri.parse(urlFilename));
            }
        } else {
            openNamedFileLegacyDirect(urlFilename);
        }
    }

    private void offerActiveUntitledRecovery() {
        RecoveryDraft draft = recoveryRepository.loadActive();
        if (draft != null && draft.metadata.untitled) {
            recoveryKey = draft.metadata.recoveryKey;
            showRecoveryDialog(draft, this::clearFile);
        }
        else {
            clearFile();
        }
    }

    private void showRecoveryDialog(RecoveryDraft draft, Runnable discardLoader) {
        recoveryDecisionPending = true;
        pendingRecoveryDraft = draft;
        pendingRecoveryDiscardLoader = discardLoader;
        recoveryDialogShowing = true;
        String timestamp = DateFormat.getDateTimeInstance().format(new Date(draft.metadata.draftUpdatedAt));
        String name = draft.metadata.displayName == null || draft.metadata.displayName.isEmpty()
                ? TPStrings.NEW_FILE_TXT : draft.metadata.displayName;
        new AlertDialog.Builder(this)
                .setTitle(R.string.Recovery_draft_found)
                .setMessage(getString(R.string.Recovery_draft_message, name, timestamp))
                .setPositiveButton(R.string.Restore, (dialog, which) -> restoreDraft(draft))
                .setNegativeButton(R.string.Discard_draft, (dialog, which) -> {
                    clearPendingRecoveryDecision();
                    recoveryRepository.delete(draft.metadata.recoveryKey);
                    if (draft.metadata.recoveryKey.equals(recoveryKey)) recoveryKey = null;
                    discardLoader.run();
                })
                .setCancelable(false)
                .setOnDismissListener(dialog -> recoveryDialogShowing = false)
                .show();
    }

    private void showPendingRecoveryDialog() {
        if (recoveryDecisionPending && !recoveryDialogShowing && pendingRecoveryDraft != null) {
            showRecoveryDialog(pendingRecoveryDraft, pendingRecoveryDiscardLoader);
        }
    }

    private void restoreDraft(RecoveryDraft draft) {
        clearPendingRecoveryDecision();
        recoveryKey = draft.metadata.recoveryKey;
        urlFilename = draft.metadata.documentUri == null ? TPStrings.EMPTY : filenameFromIdentity(draft.metadata.documentUri);
        editorGeneration = draft.metadata.generation;
        selectionStart = draft.metadata.cursorStart;
        selectionEnd = draft.metadata.cursorEnd;
        originalSize = draft.metadata.originalSize;
        originalLastModified = draft.metadata.originalLastModified;
        originalContentSha256 = draft.metadata.originalContentSha256;
        setEditorText(draft.text, true);
        updateTitle();
        if (!draft.metadata.untitled) {
            mText.post(this::validateRestoredDraft);
        }
    }

    private void clearPendingRecoveryDecision() {
        recoveryDecisionPending = false;
        pendingRecoveryDraft = null;
        pendingRecoveryDiscardLoader = null;
    }

    private void applyStoredSelection() {
        int length = mText.length();
        int start = Math.max(0, Math.min(selectionStart, length));
        int end = Math.max(0, Math.min(selectionEnd, length));
        mText.setSelection(Math.min(start, end), Math.max(start, end));
    }

    private void setEditorText(String text, boolean markChanged) {
        suppressRecoveryTracking = true;
        mText.setText(text);
        editTextUndoRedo.clearHistory();
        suppressRecoveryTracking = false;
        changed = markChanged;
        applyStoredSelection();
    }

    private void scheduleRecoverySnapshot() {
        RecoveryWriter.Snapshot snapshot = createRecoverySnapshot();
        if (snapshot != null) recoveryWriter.schedule(snapshot);
    }

    private void flushRecoverySnapshot() {
        RecoveryWriter.Snapshot snapshot = createRecoverySnapshot();
        if (snapshot != null) recoveryWriter.flushAndWait(snapshot, 2000);
    }

    private RecoveryWriter.Snapshot createRecoverySnapshot() {
        if (!changed || recoveryDecisionPending) return null;
        String identity = documentIdentityUri();
        if (recoveryKey == null) {
            recoveryKey = identity == null
                    ? RecoveryKeys.forUntitledDocument()
                    : RecoveryKeys.forDocumentUri(identity);
        }
        int start = Math.max(0, mText.getSelectionStart());
        int end = Math.max(0, mText.getSelectionEnd());
        RecoveryMetadata metadata = new RecoveryMetadata(
                recoveryKey, identity, currentDisplayName(), identity == null,
                settingsService.getFileEncoding(), false,
                originalSize, originalLastModified, originalContentSha256,
                0, 0, start, end, editorGeneration
        );
        return new RecoveryWriter.Snapshot(metadata, mText.getText().toString());
    }

    private String documentIdentityUri() {
        if (isFilenameEmpty()) return null;
        if (useAndroidManager()) return urlFilename;
        return Uri.fromFile(new File(urlFilename)).toString();
    }

    private String filenameFromIdentity(String identity) {
        if (useAndroidManager()) return identity;
        String path = Uri.parse(identity).getPath();
        return path == null ? TPStrings.EMPTY : path;
    }

    private String currentDisplayName() {
        if (isFilenameEmpty()) return TPStrings.NEW_FILE_TXT;
        if (useAndroidManager()) return FileNameHelper.getFilenameByUri(this, Uri.parse(urlFilename));
        return new File(urlFilename).getName();
    }

    private void recordLoadedDocument(byte[] originalBytes, Long lastModified) {
        originalSize = (long) originalBytes.length;
        originalLastModified = lastModified;
        originalContentSha256 = DocumentSaveValidator.sha256(originalBytes);
        String identity = documentIdentityUri();
        recoveryKey = identity == null ? null : RecoveryKeys.forDocumentUri(identity);
        editorGeneration = 0;
        selectionStart = 0;
        selectionEnd = 0;
    }

    String formatString(int stringId, String parameter) {
        return this.getResources().getString(stringId, parameter);
    }

    boolean useAndroidManager() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) return false;
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) return true;
        return !settingsService.isLegacyFilePicker();
    }

    void openLastFile() {
        if (!settingsService.getLastFilename().equals(TPStrings.EMPTY)) {
            if (useAndroidManager()) {
                Uri uri = Uri.parse(settingsService.getLastFilename());
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    this.openNamedFile(uri);
                }
            } else {
                this.openNamedFileLegacy(settingsService.getLastFilename());
            }
        } else {
            offerActiveUntitledRecovery();
        }
    }

    void updateTitle() {
        this.setTitle(getEditingTitle());
    }

    private String getEditingTitle() {
        String title;
        if (isFilenameEmpty()) {
            title = TPStrings.NEW_FILE_TXT;
        } else {
            Uri uri = Uri.parse(getFilename());
            title = FileNameHelper.getFilenameByUri(getApplicationContext(), uri);
        }
        if (changed) title = title + TPStrings.STAR;
        return title;
    }

    void applyPreferences() {
        invalidateHorizontalScrollBounds();
        applyFontFace();
        applyFontSize();
        applyColors();
    }

    private void disableEditorAutowrapping() {
        invalidateHorizontalScrollBounds();
        mText.setHorizontallyScrolling(true);
        mText.setHorizontalScrollBarEnabled(true);
        mText.setMaxLines(Integer.MAX_VALUE);
    }

    private void applyFontFace() {
        mText.setInputType(InputType.TYPE_TEXT_FLAG_MULTI_LINE |
                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS |
                InputType.TYPE_TEXT_VARIATION_NORMAL |
                InputType.TYPE_CLASS_TEXT);
        String font = settingsService.getFont();
        if (font.equals(TPStrings.FONT_SERIF)) mText.setTypeface(Typeface.SERIF);
        else if (font.equals(TPStrings.FONT_SANS_SERIF)) mText.setTypeface(Typeface.SANS_SERIF);
        else mText.setTypeface(Typeface.MONOSPACE);
    }

    private void applyFontSize() {
        String fontsize = settingsService.getFontSize();
        switch (fontsize) {
            case (SettingsService.SETTING_EXTRA_SMALL): mText.setTextSize(12.0f); break;
            case (SettingsService.SETTING_SMALL): mText.setTextSize(16.0f); break;
            case (SettingsService.SETTING_MEDIUM_SMALL): mText.setTextSize(18.0f); break;
            case (SettingsService.SETTING_LARGE): mText.setTextSize(24.0f); break;
            case (SettingsService.SETTING_HUGE): mText.setTextSize(28.0f); break;
            case (SettingsService.SETTING_EXTRA_HUGE): mText.setTextSize(56.0f); break;
            case (SettingsService.SETTING_MEDIUM):
            default: mText.setTextSize(20.0f);
        }
    }

    private void applyColors() {
        mText.setHighlightColor(settingsService.getTextSelectionColor());
        if (settingsService.isThemeForced()) {
            ThemeService themeService = ServiceLocator.getInstance().getThemeService(this);
            themeService.applyColorTheme(this);
        }
        if (settingsService.isCustomTheme()) {
            if (simpleScrolling()) linearLayout.setBackgroundColor(settingsService.getBgColor());
            else scrollView.setBackgroundColor(settingsService.getBgColor());
            mText.setTextColor(settingsService.getFontColor());
        }
    }

    private QueryTextListener getQueryTextListener() {
        if (queryTextListener == null) queryTextListener = new QueryTextListener();
        return queryTextListener;
    }

    private void initSearch(MenuItem searchItem) {
        SearchView searchView = (SearchView) searchItem.getActionView();
        if (searchView != null) {
            searchView.setSubmitButtonEnabled(true);
            searchView.setIconified(false);
            searchView.setImeOptions(EditorInfo.IME_ACTION_GO);
            searchView.setOnQueryTextListener(getQueryTextListener());
            searchItem.setOnActionExpandListener(getQueryTextListener());
        }
    }

    private String lastSearchQuery = "";
    private void showFindReplaceBar() {
        View bar = findViewById(R.id.find_replace_bar);
        if (bar == null) return;
        bar.setVisibility(View.VISIBLE);
        EditText findInput = findViewById(R.id.find_input);
        if (findInput != null) findInput.requestFocus();
        
        findViewById(R.id.btn_find_prev).setOnClickListener(v -> findMove(-1));
        findViewById(R.id.btn_find_next).setOnClickListener(v -> {
            String query = findInput.getText().toString();
            if (!query.equals(lastSearchQuery)) {
                lastSearchQuery = query;
                runFindAll(query);
            } else if (findResults.isEmpty()) {
                runFindAll(query);
            } else if (findCurrentIndex >= findResults.size() - 1) {
                runFindAll(query);
            } else {
                findMove(1);
            }
        });
        findViewById(R.id.btn_find_close).setOnClickListener(v -> hideFindReplaceBar());
        findViewById(R.id.btn_replace_one).setOnClickListener(v -> replaceNext());
        findViewById(R.id.btn_replace_all).setOnClickListener(v -> replaceAll());
    }

    private void hideFindReplaceBar() {
        View bar = findViewById(R.id.find_replace_bar);
        if (bar != null) bar.setVisibility(View.GONE);
        lastSearchQuery = "";
        findResults.clear();
        findCurrentIndex = -1;
        mText.requestFocus();
    }

    private List<Integer> findResults = new ArrayList<>();
    private int findCurrentIndex = -1;

    private void updateUndoRedoMenuItems() {
        if (optionsMenu == null) return;
        MenuItem undo = optionsMenu.findItem(R.id.menu_edit_undo);
        MenuItem redo = optionsMenu.findItem(R.id.menu_edit_redo);
        if (undo != null) undo.setEnabled(editTextUndoRedo.getCanUndo());
        if (redo != null) redo.setEnabled(editTextUndoRedo.getCanRedo());
    }

    private void runFindAll(String query) {
        findResults.clear();
        findCurrentIndex = -1;
        if (query.isEmpty()) { updateFindCount(); return; }
        String content = mText.getText().toString();
        String lower = content.toLowerCase();
        String queryLower = query.toLowerCase();
        int idx = 0;
        while ((idx = lower.indexOf(queryLower, idx)) != -1) {
            findResults.add(idx);
            idx += query.length();
        }
        if (!findResults.isEmpty()) {
            findCurrentIndex = 0;
            highlightAndScroll(query.length());
        }
        updateFindCount();
    }

    private void findMove(int delta) {
        if (findResults.isEmpty()) return;
        findCurrentIndex = (findCurrentIndex + delta + findResults.size()) % findResults.size();
        EditText findInput = findViewById(R.id.find_input);
        if (findInput != null) highlightAndScroll(findInput.getText().length());
        updateFindCount();
    }

    private void highlightAndScroll(int queryLen) {
        if (findCurrentIndex < 0 || findCurrentIndex >= findResults.size()) return;
        int start = findResults.get(findCurrentIndex);
        int end = start + queryLen;
        mText.requestFocus();
        mText.setSelection(start, end);
        if (mText.getLayout() != null) {
            int line = mText.getLayout().getLineForOffset(start);
            int pos = mText.getLayout().getLineBaseline(line);
            if (scrollView != null) scrollView.smoothScrollTo(0, pos - scrollView.getHeight() / 2);
        }
    }

    private void updateFindCount() {
        TextView findCount = findViewById(R.id.find_count);
        if (findCount == null) return;
        if (findResults.isEmpty()) findCount.setText("0/0");
        else findCount.setText((findCurrentIndex + 1) + "/" + findResults.size());
    }

    private void replaceNext() {
        EditText findInput = findViewById(R.id.find_input);
        EditText replaceInput = findViewById(R.id.replace_input);
        if (findInput == null || replaceInput == null) return;
        String find = findInput.getText().toString();
        String replace = replaceInput.getText().toString();
        if (find.isEmpty() || findResults.isEmpty()) return;
        int start = findResults.get(findCurrentIndex);
        mText.getEditableText().replace(start, start + find.length(), replace);
        runFindAll(find);
    }

    private void replaceAll() {
        EditText findInput = findViewById(R.id.find_input);
        EditText replaceInput = findViewById(R.id.replace_input);
        if (findInput == null || replaceInput == null) return;
        String find = findInput.getText().toString();
        String replace = replaceInput.getText().toString();
        if (find.isEmpty()) return;
        String content = mText.getText().toString();
        String newContent = content.replace(find, replace);
        if (!content.equals(newContent)) {
            mText.setText(newContent);
            showToast("Replaced all occurrences");
        } else {
            showToast(find + " not found");
        }
        runFindAll(find);
    }

    // ─────────────────────────────────────────────────────────────
    // Menu
    // ─────────────────────────────────────────────────────────────

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem searchItem = menu.findItem(R.id.menu_document_search);
        if (searchItem.isActionViewExpanded()) searchItem.collapseActionView();
        menu.findItem(R.id.menu_discard_draft).setVisible(isTempFile || changed);
        menu.findItem(R.id.menu_edit_undo).setEnabled(editTextUndoRedo.getCanUndo());
        menu.findItem(R.id.menu_edit_redo).setEnabled(editTextUndoRedo.getCanRedo());
        menu.findItem(R.id.menu_document_go_to).setEnabled(mText.length() > 0);
        updateRecentFiles(menu);
        updateDraftFiles(menu);
        if (android.os.Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            menu.findItem(R.id.menu_document_print).setVisible(false);
        }
        return true;
    }

    @SuppressLint("RestrictedApi")
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        optionsMenu = menu;
        if (menu instanceof MenuBuilder) {
            ((MenuBuilder) menu).setOptionalIconsVisible(true);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int itemId = item.getItemId();

        // Draft menu items — dùng DRAFT_MENU_BASE_ID, không dùng R.id
        if (itemId >= DRAFT_MENU_BASE_ID && itemId < DRAFT_MENU_BASE_ID + DRAFT_MENU_MAX) {
            int index = itemId - DRAFT_MENU_BASE_ID;
            List<File> drafts = loadAllDraftFiles();
            if (index < drafts.size()) {
                handleCurrentFileBeforeSwitch();
                suppressOpenToast = true;
                openNamedFileLegacyDirect(drafts.get(index).getAbsolutePath());
            }
            return true;
        }
        if (itemId >= RECENT_MENU_BASE_ID && itemId < RECENT_MENU_BASE_ID + RECENT_MENU_MAX) {
            int index = itemId - RECENT_MENU_BASE_ID;
            ArrayList<String> recentFiles = recentFilesService.getLastFiles(0, getApplicationContext());
            if (index < recentFiles.size()) {
                handleCurrentFileBeforeSwitch();
                suppressOpenToast = true;
                openFileByName(recentFiles.get(index));
            }
            return true;
        }

        if (itemId == R.id.menu_document_open) {
            openFile();
        } else if (itemId == R.id.menu_document_search) {
            initSearch(item);
        } else if (itemId == R.id.menu_document_find_replace) {
            showFindReplaceBar();
        } else if (itemId == R.id.menu_document_new)              { newFile();
        } else if (itemId == R.id.menu_document_save)             { saveFile();
        } else if (itemId == R.id.menu_document_save_as)          { saveAs();
        } else if (itemId == R.id.menu_document_go_to_beginning)  { moveCaretPosition(0);
        } else if (itemId == R.id.menu_document_go_to_end)        { moveCaretPosition(mText.length());
        } else if (itemId == R.id.menu_edit_undo)                 { editUndo();
        } else if (itemId == R.id.menu_edit_redo)                 { editRedo();
        } else if (itemId == R.id.menu_document_share)            { shareText();
        } else if (itemId == R.id.menu_document_print)            { printText();
        } else if (itemId == R.id.menu_document_settings)         { showSettings();
        } else if (itemId == R.id.menu_discard_draft)             { discardDraft();
        } else if (itemId == R.id.menu_exit)                      { exitApplication();
        }
        return super.onOptionsItemSelected(item);
    }

    private void printText() {
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            WebView webView = new WebView(this);
            webView.setWebViewClient(new WebViewClient() {
                public boolean shouldOverrideUrlLoading(WebView view, String url) { return false; }
                @Override
                public void onPageFinished(WebView view, String url) {
                    createWebPrintJob(view);
                    mWebView = null;
                }
            });
            String htmlDocument = "<html><body><pre style='padding:1.5cm 1cm 1.5cm 2cm'>" +
                    mText.getText() + "</pre></body></html>";
            webView.loadDataWithBaseURL(null, htmlDocument, "text/HTML", "UTF-8", null);
            mWebView = webView;
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void createWebPrintJob(WebView webView) {
        PrintManager printManager = (PrintManager) this.getSystemService(Context.PRINT_SERVICE);
        String jobName = getString(R.string.app_name) + " Document";
        PrintDocumentAdapter printAdapter = webView.createPrintDocumentAdapter(jobName);
        PrintJob printJob = printManager.print(jobName, printAdapter, new PrintAttributes.Builder().build());
        List<PrintJob> printJobs = new ArrayList<>();
        printJobs.add(printJob);
    }

    private void discardDraft() {
        if (isTempFile) {
            new AlertDialog.Builder(this)
                .setTitle("Discard Draft")
                .setMessage("Delete this draft permanently?")
                .setPositiveButton(R.string.Yes, (dialog, which) -> {
                    deleteTempFile();
                    discardCurrentRecovery();
                    clearFile();
                })
                .setNegativeButton(R.string.No, null)
                .show();
        } else {
            new AlertDialog.Builder(this)
                .setTitle("Discard Changes")
                .setMessage("Reload file and lose all unsaved changes?")
                .setPositiveButton(R.string.Yes, (dialog, which) -> {
                    discardCurrentRecovery();
                    loadStoredDocumentAfterDiscard();
                })
                .setNegativeButton(R.string.No, null)
                .show();
        }
    }

    private void shareText() {
        Intent sendIntent = new Intent();
        sendIntent.setAction(Intent.ACTION_SEND);
        sendIntent.putExtra(Intent.EXTRA_TEXT, this.mText.getText().toString());
        sendIntent.setType("text/plain");
        startActivity(Intent.createChooser(sendIntent, null));
    }

    private void showSettings() {
        if (changed) {
            new AlertDialog.Builder(this)
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setTitle(R.string.File_not_saved)
                    .setMessage(R.string.Save_current_file)
                    .setPositiveButton(R.string.Yes, (dialog, which) -> {
                        next_action = DO_SHOW_SETTINGS;
                        saveFile();
                    })
                    .setNegativeButton(R.string.No, (dialog, which) -> showSettingsActivity())
                    .show();
        } else {
            showSettingsActivity();
        }
    }

    private void showSettingsActivity() {
        Intent intent = new Intent(this.getBaseContext(), SettingsActivity.class);
        this.startActivityForResult(intent, REQUEST_SETTINGS);
    }

    protected void initEditor() {
        changed = false;
        editTextUndoRedo.clearHistory();
        queryTextListener = null;
    }

    protected void editRedo() { editTextUndoRedo.redo(); }
    protected void editUndo() { editTextUndoRedo.undo(); }

    protected void selectFileUsingAndroidSystemPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
        intent.putExtra(Intent.EXTRA_TITLE, TPStrings.NEW_FILE_TXT);
        intent.putExtra("android.content.extra.SHOW_ADVANCED", true);
        startActivityForResult(intent, ACTION_OPEN_FILE);
    }

    protected void openNewFile() {
        if (useAndroidManager()) {
            selectFileUsingAndroidSystemPicker();
        } else {
            Intent intent = new Intent(this.getBaseContext(), FileDialog.class);
            intent.putExtra(TPStrings.SELECTION_MODE, SelectionMode.MODE_OPEN);
            this.startActivityForResult(intent, REQUEST_OPEN);
        }
    }

    protected void saveFileWithConfirmation() {
        if (this.fileAlreadyExists()) {
            new AlertDialog.Builder(this)
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setTitle(R.string.File_already_exists)
                    .setMessage(R.string.Existing_file_will_be_overwritten)
                    .setPositiveButton(R.string.Yes, (dialog, which) -> {
                        next_action = DO_OPEN;
                        EditorActivity.this.saveFileIfNamed();
                    })
                    .setNegativeButton(R.string.No, (dialog, which) -> nextSaveCreatesDocument = false)
                    .setOnCancelListener(dialog -> nextSaveCreatesDocument = false)
                    .show();
        } else {
            saveFileIfNamed();
        }
    }

    protected boolean fileAlreadyExists() {
        if (useAndroidManager()) {
            try {
                Uri uri = Uri.parse(getFilename());
                InputStream is = getContentResolver().openInputStream(uri);
                if (is != null) { is.close(); return true; }
            } catch (Exception e) { return false; }
            return false;
        }
        return new File(getFilename()).exists();
    }

    protected void saveNamedFileLegacy() { guardedSaveNamedFile(false); }

    protected void saveFile(Uri uri) throws IOException {
        ContentResolver contentResolver = getContentResolver();
        OutputStream outputStream = contentResolver.openOutputStream(uri, "wt");
        if (outputStream == null) throw new IOException();
        try {
            String s = applyEndings(this.mText.getText().toString());
            outputStream.write(s.getBytes(settingsService.getFileEncoding()));
        } finally {
            outputStream.close();
        }
    }

    protected void saveNamedFile() { guardedSaveNamedFile(false); }

    private void guardedSaveNamedFile(boolean autosave) {
        try {
            String persistedText = applyEndings(mText.getText().toString());
            SaveRequest request = new SaveRequest(
                    editorGeneration, recoveryKey,
                    persistedText.getBytes(settingsService.getFileEncoding())
            );
            boolean creatingDocument = nextSaveCreatesDocument || originalContentSha256 == null;
            nextSaveCreatesDocument = false;
            if (creatingDocument) {
                writeAndComplete(request);
                return;
            }
            byte[] currentBytes = readNamedDocumentBytes();
            DocumentSaveValidator.Outcome outcome = DocumentSaveValidator.classify(
                    currentBytes, originalContentSha256, request.bytes);
            if (outcome == DocumentSaveValidator.Outcome.BASELINE_MATCH) {
                writeAndComplete(request);
            } else if (outcome == DocumentSaveValidator.Outcome.INTENDED_CONTENT_MATCH) {
                completeEquivalentSave(request, currentBytes);
            } else if (outcome == DocumentSaveValidator.Outcome.CONFLICT) {
                pendingExternalConflict = request;
                flushRecoverySnapshot();
                if (!autosave) showExternalChangeDialog(request);
            } else {
                showToast(R.string.Can_not_read_file);
            }
        } catch (FileNotFoundException e) {
            this.showToast(R.string.File_not_found);
        } catch (IOException e) {
            this.showToast(R.string.Can_not_write_file);
        } catch (Exception e) {
            this.showToast(R.string.Can_not_write_file);
        }
    }

    private byte[] readNamedDocumentBytes() throws IOException {
        InputStream inputStream;
        if (useAndroidManager()) {
            inputStream = getContentResolver().openInputStream(Uri.parse(getFilename()));
        } else {
            inputStream = new FileInputStream(new File(getFilename()));
        }
        if (inputStream == null) throw new IOException("Document cannot be opened for validation");
        try (InputStream input = inputStream; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            return output.toByteArray();
        }
    }

    private void writeNamedDocumentBytes(byte[] bytes) throws IOException {
        if (useAndroidManager()) {
            OutputStream output = getContentResolver().openOutputStream(Uri.parse(getFilename()), "wt");
            if (output == null) throw new IOException("Document cannot be opened for writing");
            try (OutputStream closeable = output) { closeable.write(bytes); }
            return;
        }
        File file = new File(getFilename());
        if (!file.exists() && !file.createNewFile()) throw new IOException("Document cannot be created");
        try (FileOutputStream output = new FileOutputStream(file)) { output.write(bytes); }
    }

    private void writeAndComplete(SaveRequest request) throws IOException {
        writeNamedDocumentBytes(request.bytes);
        Long lastModified = useAndroidManager() ? null : new File(getFilename()).lastModified();
        completeSuccessfulSave(request.generation, request.recoveryKey, request.bytes, lastModified);
        pendingExternalConflict = null;
        finishSuccessfulSave(request.generation);
    }

    private void completeEquivalentSave(SaveRequest request, byte[] currentBytes) {
        Long lastModified = useAndroidManager() ? null : new File(getFilename()).lastModified();
        completeSuccessfulSave(request.generation, request.recoveryKey, currentBytes, lastModified);
        pendingExternalConflict = null;
        finishSuccessfulSave(request.generation);
    }

    private void finishSuccessfulSave(long savedGeneration) {
        showToast(R.string.File_Written);
        if (editorGeneration == savedGeneration) initEditor();
        updateTitle();
        if (next_action == DO_OPEN)          { next_action = DO_NOTHING; openNewFile();
        } else if (next_action == DO_NEW)    { next_action = DO_NOTHING; clearFile();
        } else if (next_action == DO_SHOW_SETTINGS) { next_action = DO_NOTHING; showSettingsActivity();
        } else if (next_action == DO_OPEN_RECENT)   { next_action = DO_NOTHING; openFileByName(next_action_filename);
        } else if (next_action == DO_EXIT)   { next_action = DO_NOTHING; exitApplication();
        }
    }

    private void showExternalChangeDialog(SaveRequest request) {
        if (isFinishing() || request != pendingExternalConflict) return;
        new AlertDialog.Builder(this)
                .setTitle(R.string.External_change_detected)
                .setMessage(getString(R.string.External_change_message, currentDisplayName()))
                .setPositiveButton(R.string.Overwrite, (dialog, which) -> overwriteAfterConflict(request))
                .setNegativeButton(R.string.Reload, (dialog, which) -> confirmReloadExternal(request))
                .setNeutralButton(R.string.Save_As, (dialog, which) -> saveAs())
                .setOnCancelListener(dialog -> { pendingExternalConflict = request; flushRecoverySnapshot(); })
                .show();
    }

    private void overwriteAfterConflict(SaveRequest request) {
        try {
            readNamedDocumentBytes();
            writeAndComplete(request);
        } catch (FileNotFoundException error) { showToast(R.string.File_not_found);
        } catch (IOException error) { showToast(R.string.Can_not_write_file); }
    }

    private void confirmReloadExternal(SaveRequest request) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.Reload_external_title)
                .setMessage(R.string.Reload_external_message)
                .setPositiveButton(R.string.Reload, (dialog, which) -> reloadExternalDocument(request))
                .setNegativeButton(R.string.Cancel, (dialog, which) -> { pendingExternalConflict = request; flushRecoverySnapshot(); })
                .setOnCancelListener(dialog -> { pendingExternalConflict = request; flushRecoverySnapshot(); })
                .show();
    }

    private void reloadExternalDocument(SaveRequest request) {
        try {
            byte[] externalBytes = readNamedDocumentBytes();
            recoveryWriter.cancelAndWait(request.recoveryKey, editorGeneration, 2000);
            recoveryRepository.delete(request.recoveryKey);
            pendingExternalConflict = null;
            applyExternalDocument(externalBytes);
        } catch (FileNotFoundException error) { showToast(R.string.File_not_found); pendingExternalConflict = request;
        } catch (Exception error) { showToast(R.string.Can_not_read_file); pendingExternalConflict = request; }
    }

    private void validateOpenDocumentOnForeground() {
        if (isFilenameEmpty() || originalContentSha256 == null || pendingExternalConflict != null) return;
        try {
            byte[] currentBytes = readNamedDocumentBytes();
            if (originalContentSha256.equals(DocumentSaveValidator.sha256(currentBytes))) return;
            if (!changed) {
                applyExternalDocument(currentBytes);
                showToast(R.string.File_reloaded_after_external_change);
                return;
            }
            byte[] intendedBytes = applyEndings(mText.getText().toString())
                    .getBytes(settingsService.getFileEncoding());
            SaveRequest request = new SaveRequest(editorGeneration, recoveryKey, intendedBytes);
            DocumentSaveValidator.Outcome outcome = DocumentSaveValidator.classify(
                    currentBytes, originalContentSha256, intendedBytes);
            if (outcome == DocumentSaveValidator.Outcome.INTENDED_CONTENT_MATCH) {
                completeEquivalentSave(request, currentBytes);
            } else if (outcome == DocumentSaveValidator.Outcome.CONFLICT) {
                pendingExternalConflict = request;
                flushRecoverySnapshot();
                mText.post(() -> showExternalChangeDialog(request));
            }
        } catch (Exception error) { /* best effort */ }
    }

    private void applyExternalDocument(byte[] externalBytes) throws Exception {
        String externalText = new String(externalBytes, settingsService.getFileEncoding());
        externalText = toUnixEndings(externalText);
        setEditorText(externalText, false);
        initEditor();
        recordLoadedDocument(externalBytes,
                useAndroidManager() ? null : new File(getFilename()).lastModified());
        updateTitle();
    }

    private void validateRestoredDraft() {
        if (!changed || isFilenameEmpty() || originalContentSha256 == null) return;
        try {
            byte[] intendedBytes = applyEndings(mText.getText().toString())
                    .getBytes(settingsService.getFileEncoding());
            SaveRequest request = new SaveRequest(editorGeneration, recoveryKey, intendedBytes);
            byte[] currentBytes = readNamedDocumentBytes();
            DocumentSaveValidator.Outcome outcome = DocumentSaveValidator.classify(
                    currentBytes, originalContentSha256, intendedBytes);
            if (outcome == DocumentSaveValidator.Outcome.INTENDED_CONTENT_MATCH) {
                completeEquivalentSave(request, currentBytes);
            } else if (outcome == DocumentSaveValidator.Outcome.CONFLICT) {
                pendingExternalConflict = request;
                showExternalChangeDialog(request);
            }
        } catch (Exception error) { /* keep restored draft */ }
    }

    private void completeSuccessfulSave(long savedGeneration, String previousRecoveryKey,
            byte[] persistedBytes, Long lastModified) {
        String identity = documentIdentityUri();
        String targetKey = identity == null ? null : RecoveryKeys.forDocumentUri(identity);
        originalSize = (long) persistedBytes.length;
        originalLastModified = lastModified;
        originalContentSha256 = DocumentSaveValidator.sha256(persistedBytes);
        if (editorGeneration == savedGeneration) {
            recoveryWriter.cancelAndWait(previousRecoveryKey, savedGeneration, 2000);
            recoveryRepository.delete(previousRecoveryKey);
            if (targetKey != null && !targetKey.equals(previousRecoveryKey))
                recoveryRepository.delete(targetKey);
            recoveryKey = targetKey;
            return;
        }
        if (previousRecoveryKey != null && identity != null && !targetKey.equals(previousRecoveryKey)) {
            try { recoveryRepository.migrate(previousRecoveryKey, identity, currentDisplayName());
            } catch (Exception ignored) {}
        }
        recoveryKey = targetKey;
        scheduleRecoverySnapshot();
    }

    private void discardCurrentRecovery() {
        recoveryWriter.cancelAndWait(recoveryKey, editorGeneration, 2000);
        recoveryRepository.delete(recoveryKey);
        recoveryKey = null;
    }

    protected void openNamedFileLegacy(String filename) {
        String identity = Uri.fromFile(new File(filename)).toString();
        RecoveryDraft draft = recoveryRepository.load(RecoveryKeys.forDocumentUri(identity), identity);
        if (draft != null) showRecoveryDialog(draft, () -> openNamedFileLegacyDirect(filename));
        else openNamedFileLegacyDirect(filename);
    }

    private void openNamedFileLegacyDirect(String filename) {
        try {
            File f = new File(filename);
            FileInputStream fis = new FileInputStream(f);
            long size = f.length();
            DataInputStream dis = new DataInputStream(fis);
            byte[] b = new byte[(int) size];
            dis.readFully(b, 0, (int) size);
            dis.close(); fis.close();
            String ttt = toUnixEndings(new String(b, settingsService.getFileEncoding()));
            setEditorText(ttt, false);
            suppressOpenToast = false;
            initEditor();
            Uri fileUri = Uri.fromFile(new File(filename));
            boolean isTemp = isFileInTextPadDir(fileUri) && isNewfileName(fileUri);
            if (isTemp) {
                urlFilename = filename;
                isTempFile = true;
            } else {
                this.setFilename(filename);
            
            }
            recordLoadedDocument(b, f.lastModified());
            if (!settingsService.getLastFilename().equals(filename))
                settingsService.setLastFilename(filename, this.getApplicationContext());
            updateTitle();
        } catch (FileNotFoundException e) { this.showToast(R.string.File_not_found);
        } catch (IOException e) { this.showToast(R.string.Can_not_read_file);
        } catch (Exception e) { this.showToast(R.string.Can_not_read_file); }
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    protected void openNamedFile(final Uri uri) {
        String identity = uri.toString();
        RecoveryDraft draft = recoveryRepository.load(RecoveryKeys.forDocumentUri(identity), identity);
        if (draft != null) showRecoveryDialog(draft, () -> openNamedFileDirect(uri));
        else openNamedFileDirect(uri);
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    private void openNamedFileDirect(final Uri uri) {
        try {
            ContentResolver contentResolver = getContentResolver();
            InputStream inputStream = contentResolver.openInputStream(uri);
            if (inputStream == null) throw new IOException();
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(Math.max(8192, inputStream.available()));
            byte[] buffer = new byte[8192];
            int count;
            while ((count = inputStream.read(buffer)) != -1) bytes.write(buffer, 0, count);
            byte[] b = bytes.toByteArray();
            String ttt = toUnixEndings(new String(b, settingsService.getFileEncoding()));
            inputStream.close();
            setEditorText(ttt, false);
            suppressOpenToast = false;
            initEditor();
            boolean isTemp = isFileInTextPadDir(uri) && isNewfileName(uri);
            if (isTemp) {
                urlFilename = uri.toString();
                isTempFile = true;
            } else {
                setFilename(uri.toString());
            }
            recordLoadedDocument(b, null);
            if (!settingsService.getLastFilename().equals(getFilename()))
                settingsService.setLastFilename(getFilename(), this.getApplicationContext());
            if (lastTriedSystemUri != null) {
                alternativeUrlsService.addAlternativeUrl(lastTriedSystemUri, uri, getApplicationContext());
                lastTriedSystemUri = null;
            }
            updateTitle();
            detectReadOnlyAccess(uri);
        } catch (FileNotFoundException e) {
            suppressOpenToast = false;
            if (isAccessDeniedException(e)) showAlternativeFileDialog(uri);
            else this.showToast(R.string.File_not_found);
        } catch (Exception e) { suppressOpenToast = false; this.showToast(R.string.Can_not_read_file); }
    }

    private void detectReadOnlyAccess(final Uri uri) {
        boolean isReadOnly = false;
        try {
            ParcelFileDescriptor pfdWrite = getContentResolver().openFileDescriptor(uri, "rw");
            if (pfdWrite == null) isReadOnly = true;
            else pfdWrite.close();
        } catch (Exception e) { isReadOnly = true; }
        if (isReadOnly) new Handler().postDelayed(this::showReadOnlyDialog, 1000);
    }

    public void showReadOnlyDialog() {
        Context context = this;
        SpannableString spannableMessage = new SpannableString(
                getString(R.string.readOnlyDialogMessage) + ' ' + getString(R.string.readOnlyDialogClickHere));
        ClickableSpan clickableSpan = new ClickableSpan() {
            @Override public void onClick(View textView) {
                context.startActivity(new Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://simpleditor.org/faq/")));
            }
        };
        int linkStart = spannableMessage.length() - getString(R.string.readOnlyDialogClickHere).length();
        spannableMessage.setSpan(clickableSpan, linkStart, spannableMessage.length(), 0);
        spannableMessage.setSpan(new UnderlineSpan(), linkStart, spannableMessage.length(), 0);
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(R.string.readOnlyDialogTitle);
        builder.setMessage(spannableMessage);
        builder.setCancelable(true);
        builder.setPositiveButton(R.string.readOnlyDialogButtonOpenAgain,
                (dialog, which) -> openFile());
        builder.setNegativeButton(R.string.readOnlyDialogButtonContinue,
                (dialog, which) -> dialog.dismiss());
        AlertDialog dialog = builder.create();
        dialog.show();
        ((TextView) dialog.findViewById(android.R.id.message)).setMovementMethod(LinkMovementMethod.getInstance());
    }

    private void showAlternativeFileDialog(final Uri uri) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.AlternativeFileAccessTitle)
                .setMessage(R.string.SelectAlternativeLocationForFile)
                .setNegativeButton(R.string.Yes, (arg0, arg1) -> {
                    lastTriedSystemUri = uri;
                    selectFileUsingAndroidSystemPicker();
                })
                .setPositiveButton(R.string.No, (arg0, arg1) -> lastTriedSystemUri = null)
                .setOnCancelListener(arg0 -> {
                    lastTriedSystemUri = null;
                    EditorActivity.super.onBackPressed();
                })
                .create().show();
    }

    private boolean isAccessDeniedException(FileNotFoundException e) {
        if (!settingsService.isAlternativeFileAccess()) return false;
        String message = e.getMessage();
        return message != null && message.contains("EACCES");
    }

    String applyEndings(String value) {
        return TextConverter.getInstance().applyEndings(value, settingsService.getDelimiters());
    }

    String toUnixEndings(String value) {
        String from = settingsService.getDelimiters();
        if (TPStrings.DEFAULT.equals(from)) return value;
        return TextConverter.getInstance().applyEndings(value, TextConverter.UNIX);
    }

    int getSearchSelectionColor() {
        return settingsService.getSearchSelectionColor();
    }

    @SuppressLint("WrongConstant")
    private void persistUriPermissions(Intent data) {
        Uri uri = data.getData();
        if (uri == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            int takeFlags = data.getFlags()
                    & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            getContentResolver().takePersistableUriPermission(uri, takeFlags);
        }
    }

    protected void showToast(int toast_str) {
        Toast.makeText(getApplicationContext(), toast_str, Toast.LENGTH_SHORT).show();
    }

    protected void showToast(String toast_str) {
        Toast.makeText(getApplicationContext(), toast_str, Toast.LENGTH_SHORT).show();
    }

    // ─────────────────────────────────────────────────────────────
    // TwoFingerPanTouchListener
    // ─────────────────────────────────────────────────────────────

    private class TwoFingerPanTouchListener implements View.OnTouchListener {
        private boolean panningActive = false;
        private boolean panGestureOwned = false;
        private float lastFocalX, lastFocalY;
        private int maxScrollX, maxScrollY;

        @Override
        public boolean onTouch(View view, MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN: endPanGesture(view); return false;
                case MotionEvent.ACTION_POINTER_DOWN:
                    if (event.getPointerCount() == 2) startPanning(view, event);
                    else stopActivePanning(view);
                    return panGestureOwned;
                case MotionEvent.ACTION_MOVE:
                    if (!panGestureOwned) return false;
                    if (!panningActive) return true;
                    if (event.getPointerCount() != 2) { stopActivePanning(view); return true; }
                    float focalX = focalX(event), focalY = focalY(event);
                    panContent(Math.round(focalX - lastFocalX), Math.round(focalY - lastFocalY),
                            maxScrollX, maxScrollY);
                    lastFocalX = focalX; lastFocalY = focalY;
                    return true;
                case MotionEvent.ACTION_POINTER_UP:
                    if (panGestureOwned) { stopActivePanning(view); return true; }
                    return false;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    return endPanGesture(view);
                default: return panGestureOwned;
            }
        }

        private void startPanning(View view, MotionEvent event) {
            panningActive = true; panGestureOwned = true;
            requestParentInterception(view, true);
            lastFocalX = focalX(event); lastFocalY = focalY(event);
            Layout layout = mText.getLayout();
            maxScrollX = getMaxHorizontalScroll(layout);
            maxScrollY = calculateMaxVerticalScroll(layout);
        }

        private void stopActivePanning(View view) {
            if (panningActive) requestParentInterception(view, false);
            panningActive = false;
        }

        private boolean endPanGesture(View view) {
            boolean shouldConsume = panGestureOwned || panningActive;
            stopActivePanning(view); panGestureOwned = false;
            return shouldConsume;
        }

        private void requestParentInterception(View view, boolean disallow) {
            ViewParent parent = view.getParent();
            if (parent != null) parent.requestDisallowInterceptTouchEvent(disallow);
        }

        private float focalX(MotionEvent event) {
            float sum = 0;
            for (int i = 0; i < event.getPointerCount(); i++) sum += event.getX(i);
            return sum / event.getPointerCount();
        }

        private float focalY(MotionEvent event) {
            float sum = 0;
            for (int i = 0; i < event.getPointerCount(); i++) sum += event.getY(i);
            return sum / event.getPointerCount();
        }
    }

    private void panContent(int deltaX, int deltaY, int maxScrollX, int maxScrollY) {
        int newX = clampScroll(mText.getScrollX() - deltaX, maxScrollX);
        if (simpleScrolling()) {
            mText.scrollTo(newX, clampScroll(mText.getScrollY() - deltaY, maxScrollY));
            return;
        }
        if (scrollView != null) scrollView.scrollBy(0, -deltaY);
        mText.scrollTo(newX, mText.getScrollY());
    }

    private int getMaxHorizontalScroll(Layout layout) {
        int editorWidth = mText.getWidth();
        if (layout == null || editorWidth <= 0) return 0;
        if (horizontalScrollBoundsDirty || cachedHorizontalScrollEditorWidth != editorWidth) {
            cachedMaxHorizontalScroll = calculateMaxHorizontalScroll(layout, editorWidth);
            cachedHorizontalScrollEditorWidth = editorWidth;
            horizontalScrollBoundsDirty = false;
        }
        return cachedMaxHorizontalScroll;
    }

    private void invalidateHorizontalScrollBounds() { horizontalScrollBoundsDirty = true; }

    public int getHorizontalScrollBoundsCalculationCountForTests() {
        return horizontalScrollBoundsCalculationCount;
    }

    private int calculateMaxHorizontalScroll(Layout layout, int editorWidth) {
        horizontalScrollBoundsCalculationCount++;
        float maxLineWidth = 0;
        for (int line = 0; line < layout.getLineCount(); line++)
            maxLineWidth = Math.max(maxLineWidth, layout.getLineWidth(line));
        int contentWidth = (int) Math.ceil(maxLineWidth)
                + mText.getTotalPaddingLeft() + mText.getTotalPaddingRight();
        return Math.max(0, contentWidth - editorWidth);
    }

    private int calculateMaxVerticalScroll(Layout layout) {
        if (layout == null) return 0;
        int contentHeight = layout.getHeight()
                + mText.getTotalPaddingTop() + mText.getTotalPaddingBottom();
        return Math.max(0, contentHeight - mText.getHeight());
    }

    private int clampScroll(int value, int max) {
        return Math.max(0, Math.min(value, Math.max(0, max)));
    }

    // ─────────────────────────────────────────────────────────────
    // QueryTextListener
    // ─────────────────────────────────────────────────────────────

    private class QueryTextListener
            implements SearchView.OnQueryTextListener, MenuItem.OnActionExpandListener {
        private final BackgroundColorSpan span = new BackgroundColorSpan(getSearchSelectionColor());
        private final Editable editable;
        private Matcher matcher;
        private int index;
        private final int height;
        private String currentQuery = "";

        public QueryTextListener() {
            height = simpleScrolling() ? linearLayout.getHeight() : scrollView.getHeight();
            editable = mText.getEditableText();
        }

        @Override
        public boolean onQueryTextChange(String newText) {
            currentQuery = newText;
            if (newText.length() == 0) { index = 0; editable.removeSpan(span); return false; }
            try {
                matcher = Pattern.compile(Pattern.quote(newText),
                        Pattern.MULTILINE | Pattern.CASE_INSENSITIVE).matcher(editable);
            } catch (Exception e) { return false; }
            if (matcher.find(index)) {
                if (mText.getLayout() == null) return false;
                doSearch();
            } else { index = 0; }
            return true;
        }

        @Override
        public boolean onQueryTextSubmit(String query) {
            currentQuery = query;
            if (matcher != null) {
                if (matcher.find()) {
                    if (mText.getLayout() == null) return false;
                    doSearch();
                } else {
                    Toast.makeText(EditorActivity.this,
                            formatString(R.string.s_not_found, query), Toast.LENGTH_SHORT).show();
                    matcher.reset(); index = 0; editable.removeSpan(span);
                }
            }
            return true;
        }

        private void doSearch() {
            index = matcher.start();
            int line = mText.getLayout().getLineForOffset(index);
            int pos = mText.getLayout().getLineBaseline(line);
            if (simpleScrolling()) mText.scrollTo(0, pos - height / 2);
            else scrollView.smoothScrollTo(0, pos - height / 2);
            editable.setSpan(span, matcher.start(), matcher.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        @Override public boolean onMenuItemActionExpand(MenuItem menuItem) { return true; }

        @Override
        public boolean onMenuItemActionCollapse(MenuItem menuItem) {
            editable.removeSpan(span);
            mText.requestFocus();
            queryTextListener = null;
            return true;
        }
    }

    private void moveCaretPosition(int position) {
        mText.requestFocus();
        mText.setSelection(position);
        View editorRoot = findViewById(R.id.editor_root);
        mText.post(() -> requestFocusedCaretOnScreen(editorRoot));
    }
}