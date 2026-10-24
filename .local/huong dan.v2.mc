# Find & Replace – Hướng Dẫn Tích Hợp

Tính năng Find & Replace hiện dưới dạng **inline bar** ngay bên trên text editor, không dùng popup che màn hình. Bar tự hiện khi mở Search và tự ẩn khi đóng.

---

## Các File Cần Chỉnh Sửa

| File | Thay đổi |
| --- | --- |
| `EditorActivity.java` | Thêm import, cập nhật `QueryTextListener`, thêm `Ctrl+H` |
| `res/layout/main.xml` | Thêm replace bar, fix `colorSurface` |
| `res/layout/main_simple_scrolling.xml` | Thêm replace bar |
| `res/menu/main_menu.xml` | Thêm menu item |
| `res/drawable/ic_arrow_up.xml` | File mới – mũi tên lên `32dp` |
| `res/drawable/ic_arrow_down.xml` | File mới – mũi tên xuống `32dp` |
| `res/values/strings.xml` | Thêm string resources *(tuỳ chọn)* |

---

## Bước 1 — Fix `colorSurface` Build Error

`?attr/colorSurface` không khả dụng trong `Theme.AppCompat`. Thay bằng `?android:attr/colorBackground` trong:

- `res/layout/main.xml` (line 17)
- `res/layout/main_simple_scrolling.xml`

---

## Bước 2 — Layout XML

### 2.1 Thêm vào `main.xml` và `main_simple_scrolling.xml`

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

## Bước 3 — Menu Item

Thêm vào `res/menu/main_menu.xml` gần item Search:

```xml
<item
    android:id="@+id/menu_document_find_replace"
    android:title="Find &amp; Replace"
    app:showAsAction="never" />
```

---

## Bước 4 — String Resources *(tuỳ chọn)*

Thêm vào `res/values/strings.xml`:

```xml
<string name="find_replace">Find &amp; Replace</string>
<string name="replace_hint">Replace with</string>
<string name="replace_all">Replace All</string>
<string name="replace_next">Replace</string>
<string name="replaced_all">Replaced all occurrences</string>
```

---

## Bước 5 — `EditorActivity.java`

### 5.1 Thêm import

```java
import android.widget.Button;
```

### 5.2 Khôi phục `getQueryTextListener()`

Bỏ comment hoặc thêm lại method này:

```java
private QueryTextListener getQueryTextListener() {
    if (queryTextListener == null) {
        queryTextListener = new QueryTextListener();
    }
    return queryTextListener;
}
```

### 5.3 Thêm case vào `onOptionsItemSelected()`

Thêm **trước** dòng `menu_exit`:

```java
} else if (itemId == R.id.menu_document_find_replace) {
    // SearchView expand tự trigger QueryTextListener → replace bar hiện
}
```

### 5.4 Thêm phím tắt `Ctrl+H` vào `onKeyDown()`

Thêm sau đoạn xử lý `KEYCODE_Y`:

```java
} else if (keyCode == KeyEvent.KEYCODE_H) {
    showFindReplaceBar();
    return true;
}
```

### 5.5 Class `QueryTextListener` mới (thay thế class cũ)

Xóa toàn bộ class `QueryTextListener` cũ, thay bằng:

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
        if (replaceBar != null) replaceBar.setVisibility(View.VISIBLE);
        Button btnReplace = findViewById(R.id.btn_replace);
        if (btnReplace != null) btnReplace.setOnClickListener(v -> replaceCurrentMatch());
        Button btnReplaceAll = findViewById(R.id.btn_replace_all);
        if (btnReplaceAll != null) btnReplaceAll.setOnClickListener(v -> replaceAllMatches());
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

## Cách Sử Dụng

| Bước | Thao tác |
| --- | --- |
| 1 | Mở menu → chọn **Find & Replace** (hoặc nhấn `Ctrl+H`) |
| 2 | Replace bar hiện **bên trên text**, ngay dưới toolbar |
| 3 | Gõ từ cần tìm vào ô search — match được highlight |
| 4 | Gõ từ thay thế vào ô **"Replace with..."** |
| 5 | Nhấn **Replace** để thay thế từng lần một |
| 6 | Nhấn **All** để thay thế tất cả |
| 7 | Đóng search → replace bar tự ẩn |

---

## Lưu Ý

- **Replace** tự động tìm match tiếp theo sau khi thay thế, wrap around về đầu file nếu hết
- **All** giữ nguyên vị trí cursor trong bounds mới
- Replace bar tự **ẩn** khi đóng search
- Tìm kiếm **không phân biệt hoa thường** (`CASE_INSENSITIVE`)
- Tích hợp sẵn với **Undo/Redo** vì dùng `mText.getEditableText().replace()`
- Build yêu cầu `Theme.AppCompat.DayNight` – không cần thêm Material dependency

## Tổng Quan

Tính năng Find & Replace được tích hợp vào `EditorActivity`, cho phép người dùng tìm kiếm và thay thế văn bản trực tiếp trong editor.

---

## Các File Cần Chỉnh Sửa

| File | Thay đổi |
| --- | --- |
| `EditorActivity.java` | Thêm import, cập nhật `QueryTextListener`, thêm phím tắt `Ctrl+H` |
| `res/layout/main.xml` | Thêm replace bar, fix `colorSurface` |
| `res/layout/main_simple_scrolling.xml` | Thêm replace bar |
| `res/layout/find_replace_bar.xml` | File mới |
| `res/menu/main_menu.xml` | Thêm menu item |
| `res/drawable/ic_arrow_up.xml` | File mới |
| `res/drawable/ic_arrow_down.xml` | File mới |
| `res/values/strings.xml` | Thêm string resources *(tuỳ chọn)* |

| File | Thay đổi |
| --- | --- |
| `EditorActivity.java` | Thêm fields, methods, cập nhật `QueryTextListener`, thêm phím tắt `Ctrl+H` |
| `res/layout/main.xml` | Thêm replace bar, fix `colorSurface` |
| `res/layout/main_simple_scrolling.xml` | Thêm replace bar |
| `res/layout/find_replace_bar.xml` | File mới |
| `res/menu/main_menu.xml` | Thêm menu item |
| `res/drawable/ic_arrow_up.xml` | File mới |
| `res/drawable/ic_arrow_down.xml` | File mới |
| `res/values/strings.xml` | Thêm string resources *(tuỳ chọn)* |

---

## Bước 1 — Fix `colorSurface` Build Error

`?attr/colorSurface` không khả dụng trong `Theme.AppCompat`. Thay bằng `?android:attr/colorBackground` trong:

- `res/layout/find_replace_bar.xml` (line 7)
- `res/layout/main.xml` (line 17)

---

## Bước 2 — Thêm Drawables

Tạo 2 vector drawable:

**`res/drawable/ic_arrow_up.xml`** — mũi tên lên, size `32dp`

**`res/drawable/ic_arrow_down.xml`** — mũi tên xuống, size `32dp`

---

## Bước 3 — Layout XML

### 3.1 File mới: `res/layout/find_replace_bar.xml`

Bao gồm:

- Ô nhập find với counter `0/0`
- Nút Prev / Next (touch target `40dp`)
- Ô nhập replace
- Nút Replace và Replace All

### 3.2 Thêm vào `main.xml` và `main_simple_scrolling.xml`

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

## Bước 4 — Menu Item

Thêm vào `res/menu/main_menu.xml` gần item Search:

```xml
<item
    android:id="@+id/menu_document_find_replace"
    android:title="Find &amp; Replace"
    app:showAsAction="never" />
```

---

## Bước 5 — String Resources *(tuỳ chọn)*

Thêm vào `res/values/strings.xml`:

```xml
<string name="find_replace">Find &amp; Replace</string>
<string name="find_hint">Find</string>
<string name="replace_hint">Replace with</string>
<string name="replace_all">Replace All</string>
<string name="replace_next">Replace</string>
<string name="replaced_all">Replaced all occurrences</string>
<string name="text_not_found">not found</string>
```

---

## Bước 6 — `EditorActivity.java`

### 6.1 Thêm import

```java
import android.widget.Button;
```

### 6.2 Khôi phục method `getQueryTextListener()`

Bỏ comment hoặc thêm lại method này (đã bị comment out):

```java
private QueryTextListener getQueryTextListener() {
    if (queryTextListener == null) {
        queryTextListener = new QueryTextListener();
    }
    return queryTextListener;
}
```

```java
private LinearLayout findReplaceBar;
private EditText findInput;
private EditText replaceInput;
private TextView findCount;
private List<Integer> findResults;
private int findCurrentIndex;
```

### 6.3 Thêm case vào `onOptionsItemSelected()`

Thêm **trước** dòng `menu_exit`:

```java
} else if (itemId == R.id.menu_document_find_replace) {
    // Tự động mở search view — replace bar hiện khi search expand
    // (không cần showFindReplaceDialog() nữa)
```

Thêm sau method `initSearch()`:

```java
private void setupFindReplaceBar() { /* wire up all button listeners */ }
private void showFindReplaceBar() { /* show the bar */ }
private void hideFindReplaceBar() { /* hide the bar */ }
private void runFindAll() { /* case-insensitive search, highlight first result */ }
private void findMove(int delta) { /* navigate between results */ }
private void highlightAndScroll() { /* highlight current match and scroll */ }
private void updateFindCount() { /* update x/y counter label */ }

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

### 6.4 Thêm phím tắt `Ctrl+H` vào `onKeyDown()`

Thêm sau đoạn xử lý `KEYCODE_Y`:

```java
} else if (keyCode == KeyEvent.KEYCODE_H) {
    showFindReplaceBar();
    return true;
}
```

```java
private QueryTextListener getQueryTextListener() {
    if (queryTextListener == null) {
        queryTextListener = new QueryTextListener();
    }
    return queryTextListener;
}
```

### 6.5 Class `QueryTextListener` mới (thay thế class cũ)

Xóa class `QueryTextListener` cũ, thay bằng class mới ở mục 6.6.

Thêm **trước** dòng `menu_exit`:

```java
} else if (itemId == R.id.menu_document_find_replace) {
    showFindReplaceDialog();
```

### 6.6 Class `QueryTextListener` mới (đầy đủ)

Xóa class `QueryTextListener` cũ, thay bằng:

Xóa class `QueryTextListener` cũ, thay bằng:

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
        if (replaceBar != null) replaceBar.setVisibility(View.VISIBLE);
        Button btnReplace = findViewById(R.id.btn_replace);
        if (btnReplace != null) btnReplace.setOnClickListener(v -> replaceCurrentMatch());
        Button btnReplaceAll = findViewById(R.id.btn_replace_all);
        if (btnReplaceAll != null) btnReplaceAll.setOnClickListener(v -> replaceAllMatches());
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

## Cách Sử Dụng

| Bước | Thao tác |
| --- | --- |
| 1 | Mở menu → chọn **Find & Replace** (hoặc nhấn `Ctrl+H`) |
| 2 | Replace bar hiện ra **bên trên text**, ngay dưới toolbar |
| 3 | Gõ từ cần tìm vào ô search — match được highlight |
| 4 | Gõ từ thay thế vào ô **"Replace with..."** |
| 5 | Nhấn **Replace** để thay thế từng lần một |
| 6 | Nhấn **All** để thay thế tất cả |
| 7 | Đóng search → replace bar tự ẩn |

| Bước | Thao tác |
| --- | --- |
| 1 | Mở menu → chọn **Find & Replace** (hoặc nhấn `Ctrl+H`) |
| 2 | Gõ từ cần tìm vào ô search |
| 3 | Replace bar hiện ra bên dưới toolbar |
| 4 | Gõ từ thay thế vào ô **"Replace with..."** |
| 5 | Nhấn **Replace** để thay thế từng lần |
| 6 | Nhấn **All** để thay thế tất cả |
| 7 | Đóng search → replace bar tự ẩn |

---

## Lưu Ý

- **Replace** tự động tìm match tiếp theo sau khi thay thế
- **Replace** wrap around về đầu file nếu không còn match phía sau
- **All** giữ nguyên vị trí cursor trong bounds mới
- Replace bar tự **ẩn** khi đóng search
- Tìm kiếm **không phân biệt hoa thường** (`CASE_INSENSITIVE`)
- Tích hợp sẵn với **Undo/Redo** vì dùng `mText.getEditableText().replace()`
- Build yêu cầu `Theme.AppCompat.DayNight` (đã có sẵn — không cần thêm Material dependency)