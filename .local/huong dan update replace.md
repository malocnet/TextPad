# Hướng Dẫn Tích Hợp Tính Năng Find & Replace

## Tổng Quan

Tính năng Find & Replace được tích hợp vào `EditorActivity`, cho phép người dùng tìm kiếm và thay thế văn bản trực tiếp trong editor.

---

## Các File Đã Chỉnh Sửa

| File | Thay đổi |
|------|----------|
| `EditorActivity.java` | Cập nhật `QueryTextListener` với logic replace |
| `res/layout/main.xml` | Thêm replace bar |
| `res/layout/main_simple_scrolling.xml` | Thêm replace bar |

---

## 1. Layout XML

### Thêm vào `main.xml` và `main_simple_scrolling.xml`

Chèn đoạn sau **giữa** `<include layout="@layout/editor_toolbar" />` và phần nội dung editor:

```xml
<LinearLayout
    android:id="@+id/replace_bar"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="horizontal"
    android:padding="4dp"
    android:visibility="gone">

    <EditText
        android:id="@+id/replace_input"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_weight="1"
        android:hint="Replace with..."
        android:inputType="text"
        android:padding="4dp" />

    <Button
        android:id="@+id/btn_replace"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="Replace"
        android:textSize="12sp" />

    <Button
        android:id="@+id/btn_replace_all"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="All"
        android:textSize="12sp" />

</LinearLayout>
```

---

## 2. EditorActivity.java

### 2.1 Thêm import

```java
import android.widget.Button;
```

### 2.2 Khôi phục method `getQueryTextListener()`

Bỏ comment hoặc thêm lại method này (đã bị comment out):

```java
private QueryTextListener getQueryTextListener() {
    if (queryTextListener == null) {
        queryTextListener = new QueryTextListener();
    }
    return queryTextListener;
}
```

### 2.3 Xóa class `QueryTextListener` cũ

Xóa class `QueryTextListener` cũ ở cuối file (không có replace logic), chỉ giữ lại class mới có đầy đủ các method sau:

- `setupReplaceBar()`
- `getReplaceText()`
- `replaceCurrentMatch()`
- `replaceAllMatches()`
- `onQueryTextChange()`
- `onQueryTextSubmit()`
- `doSearch()`
- `onMenuItemActionExpand()`
- `onMenuItemActionCollapse()`

### 2.4 Class `QueryTextListener` mới (đầy đủ)

```java
private class QueryTextListener
        implements SearchView.OnQueryTextListener, MenuItem.OnActionExpandListener {
    private final BackgroundColorSpan span = new BackgroundColorSpan(getSearchSelectionColor());
    private final Editable editable;
    private Matcher matcher;
    private int index;
    private final int height;
    private String currentQuery = "";

    public QueryTextListener() {
        if (simpleScrolling()) {
            height = linearLayout.getHeight();
        } else {
            height = scrollView.getHeight();
        }
        editable = mText.getEditableText();
        setupReplaceBar();
    }

    private void setupReplaceBar() {
        View replaceBar = findViewById(R.id.replace_bar);
        if (replaceBar != null) {
            replaceBar.setVisibility(View.VISIBLE);
        }
        Button btnReplace = findViewById(R.id.btn_replace);
        if (btnReplace != null) {
            btnReplace.setOnClickListener(v -> replaceCurrentMatch());
        }
        Button btnReplaceAll = findViewById(R.id.btn_replace_all);
        if (btnReplaceAll != null) {
            btnReplaceAll.setOnClickListener(v -> replaceAllMatches());
        }
    }

    private String getReplaceText() {
        EditText replaceInput = findViewById(R.id.replace_input);
        if (replaceInput == null) return "";
        return replaceInput.getText().toString();
    }

    private void replaceCurrentMatch() {
        if (matcher == null || currentQuery.isEmpty()) return;
        String currentText = editable.toString();
        try {
            Pattern pattern = Pattern.compile(
                Pattern.quote(currentQuery),
                Pattern.MULTILINE | Pattern.CASE_INSENSITIVE
            );
            Matcher freshMatcher = pattern.matcher(currentText);
            if (freshMatcher.find(index)) {
                int start = freshMatcher.start();
                int end = freshMatcher.end();
                String replacement = getReplaceText();
                editable.removeSpan(span);
                editable.replace(start, end, replacement);
                String newText = editable.toString();
                matcher = pattern.matcher(newText);
                index = start + replacement.length();
                if (matcher.find(index)) {
                    doSearch();
                } else {
                    index = 0;
                    matcher.reset();
                    if (matcher.find()) {
                        doSearch();
                    } else {
                        showToast(R.string.s_not_found);
                    }
                }
            } else {
                onQueryTextSubmit(currentQuery);
            }
        } catch (Exception e) {
            showToast(R.string.Can_not_write_file);
        }
    }

    private void replaceAllMatches() {
        if (currentQuery.isEmpty()) return;
        String replacement = getReplaceText();
        String currentText = editable.toString();
        try {
            Pattern pattern = Pattern.compile(
                Pattern.quote(currentQuery),
                Pattern.MULTILINE | Pattern.CASE_INSENSITIVE
            );
            String newText = pattern.matcher(currentText)
                .replaceAll(Matcher.quoteReplacement(replacement));
            if (!newText.equals(currentText)) {
                editable.removeSpan(span);
                int cursorPos = mText.getSelectionStart();
                mText.setText(newText);
                mText.setSelection(Math.min(cursorPos, newText.length()));
                index = 0;
                matcher = null;
                showToast("Replaced all occurrences");
            } else {
                showToast(R.string.s_not_found);
            }
        } catch (Exception e) {
            showToast(R.string.Can_not_write_file);
        }
    }

    @Override
    public boolean onQueryTextChange(String newText) {
        currentQuery = newText;
        if (newText.length() == 0) {
            index = 0;
            editable.removeSpan(span);
            return false;
        }
        try {
            matcher = Pattern.compile(
                Pattern.quote(newText),
                Pattern.MULTILINE | Pattern.CASE_INSENSITIVE
            ).matcher(editable);
        } catch (Exception e) {
            return false;
        }
        if (matcher.find(index)) {
            if (mText.getLayout() == null) return false;
            doSearch();
        } else {
            index = 0;
        }
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
                Toast.makeText(
                    EditorActivity.this,
                    formatString(R.string.s_not_found, query),
                    Toast.LENGTH_SHORT
                ).show();
                matcher.reset();
                index = 0;
                editable.removeSpan(span);
            }
        }
        return true;
    }

    private void doSearch() {
        index = matcher.start();
        int line = mText.getLayout().getLineForOffset(index);
        int pos = mText.getLayout().getLineBaseline(line);
        if (simpleScrolling()) {
            mText.scrollTo(0, pos - height / 2);
        } else {
            scrollView.smoothScrollTo(0, pos - height / 2);
        }
        editable.setSpan(span, matcher.start(), matcher.end(),
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    @Override
    public boolean onMenuItemActionExpand(MenuItem menuItem) {
        return true;
    }

    @Override
    public boolean onMenuItemActionCollapse(MenuItem menuItem) {
        editable.removeSpan(span);
        mText.requestFocus();
        queryTextListener = null;
        View replaceBar = findViewById(R.id.replace_bar);
        if (replaceBar != null) replaceBar.setVisibility(View.GONE);
        return true;
    }
}
```

---

## 3. Cách Sử Dụng

| Bước | Thao tác |
|------|----------|
| 1 | Mở menu → chọn **Search** |
| 2 | Gõ từ cần tìm vào ô search |
| 3 | Replace bar hiện ra bên dưới toolbar |
| 4 | Gõ từ thay thế vào ô **"Replace with..."** |
| 5 | Nhấn **Replace** để thay thế từng lần |
| 6 | Nhấn **All** để thay thế tất cả |
| 7 | Đóng search → replace bar tự ẩn |

---

## 4. Lưu Ý

- **Replace** tự động tìm match tiếp theo sau khi thay thế
- **Replace** wrap around về đầu file nếu không còn match phía sau
- **All** giữ nguyên vị trí cursor trong bounds mới
- Replace bar tự **ẩn** khi đóng search
- Tìm kiếm **không phân biệt hoa thường** (`CASE_INSENSITIVE`)


# Hướng dẫn thêm chức năng Find & Replace

## Tổng quan

Tài liệu hướng dẫn từng bước thêm **Find & Replace** vào app `com.maxistar.textpad`.

## Các file cần chỉnh sửa

- `EditorActivity.java`
- `res/menu/main_menu.xml`
- `res/values/strings.xml` *(tuỳ chọn)*

---

## Bước 1 — Thêm 3 method vào `EditorActivity.java`

Thêm sau method `initSearch()`:

```java
private void showFindReplaceDialog() {
    LinearLayout layout = new LinearLayout(this);
    layout.setOrientation(LinearLayout.VERTICAL);
    layout.setPadding(50, 30, 50, 10);

    final EditText findInput = new EditText(this);
    findInput.setHint("Find");
    findInput.setSingleLine(true);
    layout.addView(findInput);

    final EditText replaceInput = new EditText(this);
    replaceInput.setHint("Replace with");
    replaceInput.setSingleLine(true);
    layout.addView(replaceInput);

    new AlertDialog.Builder(this)
        .setTitle("Find & Replace")
        .setView(layout)
        .setPositiveButton("Replace All", (dialog, which) -> {
            String find = findInput.getText().toString();
            String replace = replaceInput.getText().toString();
            if (!find.isEmpty()) replaceAll(find, replace);
        })
        .setNeutralButton("Replace", (dialog, which) -> {
            String find = findInput.getText().toString();
            String replace = replaceInput.getText().toString();
            if (!find.isEmpty()) replaceNext(find, replace);
        })
        .setNegativeButton("Cancel", null)
        .show();
}

private void replaceAll(String findText, String replaceText) {
    String content = mText.getText().toString();
    String newContent = content.replace(findText, replaceText);
    if (!content.equals(newContent)) {
        mText.setText(newContent);
        showToast("Replaced all occurrences");
    } else {
        showToast(findText + " not found");
    }
}

private void replaceNext(String findText, String replaceText) {
    String content = mText.getText().toString();
    int cursorPos = mText.getSelectionStart();
    int index = content.indexOf(findText, cursorPos);
    if (index == -1) index = content.indexOf(findText); // wrap around
    if (index != -1) {
        mText.getEditableText().replace(index, index + findText.length(), replaceText);
        mText.setSelection(index + replaceText.length());
    } else {
        showToast(findText + " not found");
    }
}
```

---

## Bước 2 — Thêm case vào `onOptionsItemSelected()`

Tìm đoạn:

```java
} else if (itemId == R.id.menu_exit) {
    exitApplication();
}
```

Thêm **trước** dòng `menu_exit`:

```java
} else if (itemId == R.id.menu_document_find_replace) {
    showFindReplaceDialog();
```

---

## Bước 3 — Thêm phím tắt `Ctrl+H` vào `onKeyDown()`

Tìm đoạn:

```java
} else if (keyCode == KeyEvent.KEYCODE_Y) {
    editRedo();
    return true;
}
```

Thêm ngay sau:

```java
} else if (keyCode == KeyEvent.KEYCODE_H) {
    showFindReplaceDialog();
    return true;
}
```

---

## Bước 4 — Thêm menu item vào `res/menu/main_menu.xml`

Thêm gần item Search:

```xml
<item
    android:id="@+id/menu_document_find_replace"
    android:title="Find &amp; Replace"
    app:showAsAction="never" />
```

---

## Bước 5 — `res/values/strings.xml` *(tuỳ chọn)*

Nếu muốn dùng string resource thay vì hardcode:

```xml
<string name="find_replace">Find &amp; Replace</string>
<string name="find_hint">Find</string>
<string name="replace_hint">Replace with</string>
<string name="replace_all">Replace All</string>
<string name="replace_next">Replace</string>
<string name="replaced_all">Replaced all occurrences</string>
<string name="text_not_found">not found</string>
```

Và thay chuỗi hardcode trong các method bằng `getString(R.string.xxx)`.

---

## Tóm tắt

| Bước | File | Thay đổi |
|------|------|----------|
| 1 | `EditorActivity.java` | Thêm 3 method: `showFindReplaceDialog`, `replaceAll`, `replaceNext` |
| 2 | `EditorActivity.java` | Thêm case `menu_document_find_replace` trong `onOptionsItemSelected` |
| 3 | `EditorActivity.java` | Thêm phím tắt `Ctrl+H` trong `onKeyDown` |
| 4 | `main_menu.xml` | Thêm menu item `menu_document_find_replace` |
| 5 | `strings.xml` | Thêm string resources *(tuỳ chọn)* |

---

## Lưu ý

- `replaceNext` tự **wrap around** về đầu file nếu không tìm thấy từ vị trí cursor trở đi.
- `replaceAll` mặc định **case-sensitive**. Muốn case-insensitive dùng:
  ```java
  content.replaceAll("(?i)" + Pattern.quote(findText), Matcher.quoteReplacement(replaceText))
  ```
- Tích hợp sẵn với **Undo/Redo** vì dùng `mText.getEditableText().replace()`.

# TextPad – Update Guide

## Version: Find & Replace Bar

### Changes

#### 1. Fixed `colorSurface` build error
`?attr/colorSurface` is not available in `Theme.AppCompat`. Replaced with `?android:attr/colorBackground` in:
- `res/layout/find_replace_bar.xml` (line 7)
- `res/layout/main.xml` (line 17)

#### 2. Added missing drawables
Created 2 vector drawable files used by `main.xml`:
- `res/drawable/ic_arrow_up.xml` — arrow pointing up, size `32dp`
- `res/drawable/ic_arrow_down.xml` — arrow pointing down, size `32dp`

#### 3. Added Find & Replace bar UI
New layout file: `res/layout/find_replace_bar.xml`

Includes:
- Find input field with match counter (`0/0`)
- Prev / Next navigation buttons (`40dp` touch target)
- Replace input field
- Replace and Replace All buttons

#### 4. Added Find & Replace logic in `EditorActivity.java`
New fields:
```java
private LinearLayout findReplaceBar;
private EditText findInput;
private EditText replaceInput;
private TextView findCount;
private List<Integer> findResults;
private int findCurrentIndex;
```

New methods:
- `setupFindReplaceBar()` — wires up all button listeners
- `showFindReplaceBar()` / `hideFindReplaceBar()` — show/hide the bar
- `runFindAll()` — case-insensitive search, highlights first result
- `findMove(int delta)` — navigate between results
- `highlightAndScroll()` — highlights current match and scrolls to it
- `updateFindCount()` — updates the `x/y` counter label
- `replaceAll(String, String)` — replaces all occurrences

#### 5. Added keyboard shortcut
`Ctrl+H` opens the Find & Replace bar (handled in `onKeyDown`).

#### 6. Wired menu item
`R.id.menu_document_find_replace` in `main_menu.xml` calls `showFindReplaceDialog()`.

---

### Files Modified
| File | Change |
|---|---|
| `res/layout/find_replace_bar.xml` | New file |
| `res/layout/main.xml` | Added `find_replace_bar` include, fixed `colorSurface` |
| `res/drawable/ic_arrow_up.xml` | New file |
| `res/drawable/ic_arrow_down.xml` | New file |
| `res/menu/main_menu.xml` | Added `menu_document_find_replace` item |
| `java/.../EditorActivity.java` | Added Find & Replace logic |

---

### Build Requirements
- `Theme.AppCompat.DayNight` (already in use — no Material dependency needed)
- `minSdk` unchanged

# Hướng Dẫn Tích Hợp Tính Năng Find & Replace

## Tổng Quan

Tính năng Find & Replace được tích hợp vào `EditorActivity`, cho phép người dùng tìm kiếm và thay thế văn bản trực tiếp trong editor.

---

## Các File Đã Chỉnh Sửa

| File | Thay đổi |
|------|----------|
| `EditorActivity.java` | Cập nhật `QueryTextListener` với logic replace |
| `res/layout/main.xml` | Thêm replace bar |
| `res/layout/main_simple_scrolling.xml` | Thêm replace bar |

---

## 1. Layout XML

### Thêm vào `main.xml` và `main_simple_scrolling.xml`

Chèn đoạn sau **giữa** `<include layout="@layout/editor_toolbar" />` và phần nội dung editor:

```xml
<LinearLayout
    android:id="@+id/replace_bar"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="horizontal"
    android:padding="4dp"
    android:visibility="gone">

    <EditText
        android:id="@+id/replace_input"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_weight="1"
        android:hint="Replace with..."
        android:inputType="text"
        android:padding="4dp" />

    <Button
        android:id="@+id/btn_replace"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="Replace"
        android:textSize="12sp" />

    <Button
        android:id="@+id/btn_replace_all"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="All"
        android:textSize="12sp" />

</LinearLayout>
```

---

## 2. EditorActivity.java

### 2.1 Thêm import

```java
import android.widget.Button;
```

### 2.2 Khôi phục method `getQueryTextListener()`

Bỏ comment hoặc thêm lại method này (đã bị comment out):

```java
private QueryTextListener getQueryTextListener() {
    if (queryTextListener == null) {
        queryTextListener = new QueryTextListener();
    }
    return queryTextListener;
}
```

### 2.3 Xóa class `QueryTextListener` cũ

Xóa class `QueryTextListener` cũ ở cuối file (không có replace logic), chỉ giữ lại class mới có đầy đủ các method sau:

- `setupReplaceBar()`
- `getReplaceText()`
- `replaceCurrentMatch()`
- `replaceAllMatches()`
- `onQueryTextChange()`
- `onQueryTextSubmit()`
- `doSearch()`
- `onMenuItemActionExpand()`
- `onMenuItemActionCollapse()`

### 2.4 Class `QueryTextListener` mới (đầy đủ)

```java
private class QueryTextListener
        implements SearchView.OnQueryTextListener, MenuItem.OnActionExpandListener {
    private final BackgroundColorSpan span = new BackgroundColorSpan(getSearchSelectionColor());
    private final Editable editable;
    private Matcher matcher;
    private int index;
    private final int height;
    private String currentQuery = "";

    public QueryTextListener() {
        if (simpleScrolling()) {
            height = linearLayout.getHeight();
        } else {
            height = scrollView.getHeight();
        }
        editable = mText.getEditableText();
        setupReplaceBar();
    }

    private void setupReplaceBar() {
        View replaceBar = findViewById(R.id.replace_bar);
        if (replaceBar != null) {
            replaceBar.setVisibility(View.VISIBLE);
        }
        Button btnReplace = findViewById(R.id.btn_replace);
        if (btnReplace != null) {
            btnReplace.setOnClickListener(v -> replaceCurrentMatch());
        }
        Button btnReplaceAll = findViewById(R.id.btn_replace_all);
        if (btnReplaceAll != null) {
            btnReplaceAll.setOnClickListener(v -> replaceAllMatches());
        }
    }

    private String getReplaceText() {
        EditText replaceInput = findViewById(R.id.replace_input);
        if (replaceInput == null) return "";
        return replaceInput.getText().toString();
    }

    private void replaceCurrentMatch() {
        if (matcher == null || currentQuery.isEmpty()) return;
        String currentText = editable.toString();
        try {
            Pattern pattern = Pattern.compile(
                Pattern.quote(currentQuery),
                Pattern.MULTILINE | Pattern.CASE_INSENSITIVE
            );
            Matcher freshMatcher = pattern.matcher(currentText);
            if (freshMatcher.find(index)) {
                int start = freshMatcher.start();
                int end = freshMatcher.end();
                String replacement = getReplaceText();
                editable.removeSpan(span);
                editable.replace(start, end, replacement);
                String newText = editable.toString();
                matcher = pattern.matcher(newText);
                index = start + replacement.length();
                if (matcher.find(index)) {
                    doSearch();
                } else {
                    index = 0;
                    matcher.reset();
                    if (matcher.find()) {
                        doSearch();
                    } else {
                        showToast(R.string.s_not_found);
                    }
                }
            } else {
                onQueryTextSubmit(currentQuery);
            }
        } catch (Exception e) {
            showToast(R.string.Can_not_write_file);
        }
    }

    private void replaceAllMatches() {
        if (currentQuery.isEmpty()) return;
        String replacement = getReplaceText();
        String currentText = editable.toString();
        try {
            Pattern pattern = Pattern.compile(
                Pattern.quote(currentQuery),
                Pattern.MULTILINE | Pattern.CASE_INSENSITIVE
            );
            String newText = pattern.matcher(currentText)
                .replaceAll(Matcher.quoteReplacement(replacement));
            if (!newText.equals(currentText)) {
                editable.removeSpan(span);
                int cursorPos = mText.getSelectionStart();
                mText.setText(newText);
                mText.setSelection(Math.min(cursorPos, newText.length()));
                index = 0;
                matcher = null;
                showToast("Replaced all occurrences");
            } else {
                showToast(R.string.s_not_found);
            }
        } catch (Exception e) {
            showToast(R.string.Can_not_write_file);
        }
    }

    @Override
    public boolean onQueryTextChange(String newText) {
        currentQuery = newText;
        if (newText.length() == 0) {
            index = 0;
            editable.removeSpan(span);
            return false;
        }
        try {
            matcher = Pattern.compile(
                Pattern.quote(newText),
                Pattern.MULTILINE | Pattern.CASE_INSENSITIVE
            ).matcher(editable);
        } catch (Exception e) {
            return false;
        }
        if (matcher.find(index)) {
            if (mText.getLayout() == null) return false;
            doSearch();
        } else {
            index = 0;
        }
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
                Toast.makeText(
                    EditorActivity.this,
                    formatString(R.string.s_not_found, query),
                    Toast.LENGTH_SHORT
                ).show();
                matcher.reset();
                index = 0;
                editable.removeSpan(span);
            }
        }
        return true;
    }

    private void doSearch() {
        index = matcher.start();
        int line = mText.getLayout().getLineForOffset(index);
        int pos = mText.getLayout().getLineBaseline(line);
        if (simpleScrolling()) {
            mText.scrollTo(0, pos - height / 2);
        } else {
            scrollView.smoothScrollTo(0, pos - height / 2);
        }
        editable.setSpan(span, matcher.start(), matcher.end(),
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    @Override
    public boolean onMenuItemActionExpand(MenuItem menuItem) {
        return true;
    }

    @Override
    public boolean onMenuItemActionCollapse(MenuItem menuItem) {
        editable.removeSpan(span);
        mText.requestFocus();
        queryTextListener = null;
        View replaceBar = findViewById(R.id.replace_bar);
        if (replaceBar != null) replaceBar.setVisibility(View.GONE);
        return true;
    }
}
```

---

## 3. Cách Sử Dụng

| Bước | Thao tác |
|------|----------|
| 1 | Mở menu → chọn **Search** |
| 2 | Gõ từ cần tìm vào ô search |
| 3 | Replace bar hiện ra bên dưới toolbar |
| 4 | Gõ từ thay thế vào ô **"Replace with..."** |
| 5 | Nhấn **Replace** để thay thế từng lần |
| 6 | Nhấn **All** để thay thế tất cả |
| 7 | Đóng search → replace bar tự ẩn |

---

## 4. Lưu Ý

- **Replace** tự động tìm match tiếp theo sau khi thay thế
- **Replace** wrap around về đầu file nếu không còn match phía sau
- **All** giữ nguyên vị trí cursor trong bounds mới
- Replace bar tự **ẩn** khi đóng search
- Tìm kiếm **không phân biệt hoa thường** (`CASE_INSENSITIVE`)
