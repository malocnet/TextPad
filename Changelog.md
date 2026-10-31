diff --git a/app/src/main/java/com/maxistar/textpad/EditorActivity.java b/app/src/main/java/com/maxistar/textpad/EditorActivity.java
index edcade7..c655ab8 100644
--- a/app/src/main/java/com/maxistar/textpad/EditorActivity.java
+++ b/app/src/main/java/com/maxistar/textpad/EditorActivity.java
@@ -1380,11 +1380,91 @@ public class EditorActivity extends AppCompatActivity {
         } else if (itemId == R.id.menu_document_print)            { printText();
         } else if (itemId == R.id.menu_document_settings)         { showSettings();
         } else if (itemId == R.id.menu_discard_draft)             { discardDraft();
+        } else if (itemId == R.id.action_cut)                     { mText.onTextContextMenuItem(android.R.id.cut);
+        } else if (itemId == R.id.action_copy)                    { mText.onTextContextMenuItem(android.R.id.copy);
+        } else if (itemId == R.id.action_paste)                   { mText.onTextContextMenuItem(android.R.id.paste);
+        } else if (itemId == R.id.action_select_all)              { mText.selectAll();
+        } else if (itemId == R.id.action_share)                   { shareText();
+        } else if (itemId == R.id.action_clipboard)               { showClipboardDialog();
+        } else if (itemId == R.id.action_extend_up_1)             { extendSelection(-1);
+        } else if (itemId == R.id.action_extend_down_1)           { extendSelection(1);
+        } else if (itemId == R.id.action_extend_up_5)             { extendSelection(-5);
+        } else if (itemId == R.id.action_extend_down_5)           { extendSelection(5);
         } else if (itemId == R.id.menu_exit)                      { exitApplication();
         }
         return super.onOptionsItemSelected(item);
     }
 
+    // ─────────────────────────────────────────────────────────────
+    // Editor menu
+    // ─────────────────────────────────────────────────────────────
+
+    private void showClipboardDialog() {
+        android.content.ClipboardManager clipboard =
+                (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
+        if (clipboard == null || !clipboard.hasPrimaryClip()) {
+            showToast("Clipboard is empty");
+            return;
+        }
+        android.content.ClipData clip = clipboard.getPrimaryClip();
+        if (clip == null || clip.getItemCount() == 0) {
+            showToast("Clipboard is empty");
+            return;
+        }
+        CharSequence text = clip.getItemAt(0).coerceToText(this);
+        String preview = text.length() > 300
+                ? text.subSequence(0, 300).toString() + "…"
+                : text.toString();
+
+        new AlertDialog.Builder(this)
+                .setTitle("Clipboard")
+                .setMessage(preview)
+                .setPositiveButton("Paste", (dialog, which) ->
+                        mText.onTextContextMenuItem(android.R.id.paste))
+                .setNegativeButton(R.string.Cancel, null)
+                .show();
+    }
+
+    private void extendSelection(int lines) {
+        android.text.Layout layout = mText.getLayout();
+        if (layout == null) return;
+
+        int selStart = mText.getSelectionStart();
+        int selEnd   = mText.getSelectionEnd();
+        int textLength = mText.getText().length();
+
+        if (lines > 0) {
+            int currentLine = layout.getLineForOffset(selEnd);
+            int targetLine  = Math.min(currentLine + lines, layout.getLineCount() - 1);
+            int newSelEnd   = Math.min(layout.getLineEnd(targetLine), textLength);
+            mText.setSelection(selStart, newSelEnd);
+            scrollToOffset(newSelEnd);
+        } else {
+            int currentLine  = layout.getLineForOffset(selStart);
+            int targetLine   = Math.max(currentLine + lines, 0);
+            int newSelStart  = layout.getLineStart(targetLine);
+            mText.setSelection(newSelStart, selEnd);
+            scrollToOffset(newSelStart);
+        }
+    }
+
+    private void scrollToOffset(int offset) {
+        if (mText.getLayout() == null) return;
+        int line       = mText.getLayout().getLineForOffset(offset);
+        int lineTop    = mText.getLayout().getLineTop(line);
+        int lineBottom = mText.getLayout().getLineBottom(line);
+
+        if (simpleScrolling()) {
+            mText.scrollTo(0, Math.max(0, lineTop - mText.getHeight() / 2));
+        } else {
+            if (scrollView == null) return;
+            scrollView.requestChildRectangleOnScreen(
+                    mText,
+                    new Rect(0, lineTop, mText.getWidth(), lineBottom),
+                    false);
+        }
+    }
+
     private void printText() {
         if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
             WebView webView = new WebView(this);
diff --git a/app/src/main/res/drawable/ic_edit.xml b/app/src/main/res/drawable/ic_edit.xml
new file mode 100644
index 0000000..fa34253
--- /dev/null
+++ b/app/src/main/res/drawable/ic_edit.xml
@@ -0,0 +1,10 @@
+<?xml version="1.0" encoding="utf-8"?>
+<vector xmlns:android="http://schemas.android.com/apk/res/android"
+    android:width="24dp"
+    android:height="24dp"
+    android:viewportWidth="24"
+    android:viewportHeight="24">
+    <path
+        android:fillColor="@android:color/white"
+        android:pathData="M3,17.25V21H6.75L17.81,9.94L14.06,6.19L3,17.25ZM20.71,7.04C21.1,6.65 21.1,6.02 20.71,5.63L18.37,3.29C17.98,2.9 17.35,2.9 16.96,3.29L15.13,5.12L18.88,8.87L20.71,7.04Z"/>
+</vector>
\ No newline at end of file
diff --git a/app/src/main/res/menu/main_menu.xml b/app/src/main/res/menu/main_menu.xml
index 22dc0bd..aade0eb 100644
--- a/app/src/main/res/menu/main_menu.xml
+++ b/app/src/main/res/menu/main_menu.xml
@@ -17,6 +17,26 @@
         app:iconTint="@color/colorIcon"
         app:showAsAction="ifRoom|collapseActionView" />
 
+    <item
+        android:id="@+id/menu_editor"
+        android:title="Editor"
+        android:icon="@drawable/ic_edit"
+        app:iconTint="@color/colorIcon"
+        app:showAsAction="always">
+        <menu>
+            <item android:id="@+id/action_cut"           android:title="Cut" />
+            <item android:id="@+id/action_copy"          android:title="Copy" />
+            <item android:id="@+id/action_paste"         android:title="Paste" />
+            <item android:id="@+id/action_select_all"    android:title="Select All" />
+            <item android:id="@+id/action_share"         android:title="Share" />
+            <item android:id="@+id/action_clipboard"     android:title="Clipboard" />
+            <item android:id="@+id/action_extend_up_1"   android:title="Extend ↑" />
+            <item android:id="@+id/action_extend_down_1" android:title="Extend ↓" />
+            <item android:id="@+id/action_extend_up_5"   android:title="Extend 5 line ↑" />
+            <item android:id="@+id/action_extend_down_5" android:title="Extend 5 line ↓" />
+        </menu>
+    </item>
+
     <item
         android:id="@+id/menu_edit_undo"
         android:title="@string/action_undo"
diff --git a/app/src/main/res/values/ids.xml b/app/src/main/res/values/ids.xml
new file mode 100644
index 0000000..2a17995
--- /dev/null
+++ b/app/src/main/res/values/ids.xml
@@ -0,0 +1,13 @@
+<?xml version="1.0" encoding="utf-8"?>
+<resources>
+    <item name="action_cut"           type="id" />
+    <item name="action_copy"          type="id" />
+    <item name="action_paste"         type="id" />
+    <item name="action_select_all"    type="id" />
+    <item name="action_share"         type="id" />
+    <item name="action_clipboard"     type="id" />
+    <item name="action_extend_up_1"   type="id" />
+    <item name="action_extend_down_1" type="id" />
+    <item name="action_extend_up_5"   type="id" />
+    <item name="action_extend_down_5" type="id" />
+</resources>
\ No newline at end of file
