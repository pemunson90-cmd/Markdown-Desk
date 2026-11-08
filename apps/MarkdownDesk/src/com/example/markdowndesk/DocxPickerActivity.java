package com.example.markdowndesk;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import java.util.UUID;

/** Private result trampoline: provider extras never supply the operation identity. */
public final class DocxPickerActivity extends Activity {
    static final String EXTRA_NONCE = "com.example.markdowndesk.docx.NONCE";
    private static final String STARTED = "docx.picker.started";
    private static final int REQUEST_PICKER = 1;
    private String nonce;
    private boolean started, returned;

    static boolean validNonce(String value) {
        if (value == null || value.length() != 36) return false;
        try { return UUID.fromString(value).toString().equals(value); }
        catch (IllegalArgumentException invalid) { return false; }
    }

    private static boolean validFilename(String value) {
        if (value == null || value.length() < 6 || value.length() > 128 || !value.endsWith(".docx")) return false;
        for (int index = 0; index < value.length(); index++) {
            char ch = value.charAt(index);
            if (ch == '/' || ch == '\\' || Character.isISOControl(ch)) return false;
            if (Character.isHighSurrogate(ch)) {
                if (++index >= value.length() || !Character.isLowSurrogate(value.charAt(index))) return false;
            } else if (Character.isLowSurrogate(ch)) return false;
        }
        return true;
    }

    private Intent wrapper() {
        Intent result = new Intent();
        if (validNonce(this.nonce)) result.putExtra(EXTRA_NONCE, this.nonce);
        return result;
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        Intent original = getIntent();
        this.nonce = original == null ? null : original.getStringExtra(EXTRA_NONCE);
        // Re-establish a bound default even after Android recreates this instance.
        setResult(RESULT_CANCELED, wrapper());
        String filename = original == null ? null : original.getStringExtra(Intent.EXTRA_TITLE);
        if (!validNonce(this.nonce) || !validFilename(filename)) { this.returned = true; finish(); return; }
        this.started = state != null && state.getBoolean(STARTED, false);
        if (this.started) return;
        this.started = true;
        Intent picker = new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                .setType("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                .putExtra(Intent.EXTRA_TITLE, filename).addFlags(3);
        try { startActivityForResult(picker, REQUEST_PICKER); }
        catch (RuntimeException denied) { this.returned = true; finish(); }
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putBoolean(STARTED, this.started);
        super.onSaveInstanceState(state);
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != REQUEST_PICKER || !this.started || this.returned) return;
        this.returned = true;
        Intent bound = wrapper();
        int outcome = RESULT_CANCELED;
        if (result == RESULT_OK && data != null && data.getData() != null) {
            bound.setData(data.getData()).addFlags(data.getFlags() & 3);
            outcome = RESULT_OK;
        }
        setResult(outcome, bound);
        finish();
    }
}
