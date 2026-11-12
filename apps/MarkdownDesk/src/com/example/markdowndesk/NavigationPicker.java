package com.example.markdowndesk;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import java.util.List;
import java.util.function.IntConsumer;

/** Native measured/recycled dialog list. No document I/O or permission changes. */
final class NavigationPicker {
    private final Activity activity;
    private final UiTheme.Palette palette;
    private final NavigationChoices choices;
    private final int total;
    private NavigationChoices.Snapshot displayed;
    private IntConsumer selection;
    private AlertDialog dialog;
    private EditText search;
    private TextView status;
    private boolean closed;
    private static final class Binding {
        final NavigationChoices.Snapshot snapshot; final int position;
        Binding(NavigationChoices.Snapshot snapshot,int position){this.snapshot=snapshot;this.position=position;}
    }
    private final BaseAdapter adapter = new BaseAdapter() {
        @Override public int getCount() { return displayed == null ? 0 : displayed.rows.size(); }
        @Override public NavigationChoices.Row getItem(int position) { return displayed.rows.get(position); }
        @Override public long getItemId(int position) { return getItem(position).id; }
        @Override public boolean hasStableIds() { return true; }
        @Override public View getView(int position, View recycled, ViewGroup parent) {
            TextView row = recycled instanceof TextView ? (TextView) recycled : new TextView(activity);
            NavigationChoices.Row item = getItem(position);
            String label = item.title + (item.detail.isEmpty() ? "" : "\n" + item.detail);
            row.setTag(new Binding(displayed, position));
            row.setText(label);
            row.setContentDescription(label);
            row.setTextColor(UiTheme.readableForeground(palette.text, palette.raised));
            row.setTextSize(16);
            row.setSingleLine(false);
            row.setMinHeight(dp(48));
            row.setPadding(dp(18), dp(12), dp(18), dp(12));
            // Keep ListView's native pressed/focus selector visible.
            return row;
        }
    };

    private NavigationPicker(Activity activity, UiTheme.Palette palette, List<NavigationChoices.Row> rows,
                             IntConsumer selection) {
        this.activity = activity;
        this.palette = palette;
        this.choices = new NavigationChoices(rows);
        this.total = rows.size();
        this.selection = selection;
        this.displayed = choices.filter("");
    }
    static NavigationPicker show(Activity activity, UiTheme.Palette palette, String title, String hint,
                                 List<NavigationChoices.Row> rows, IntConsumer selection) {
        NavigationPicker picker = new NavigationPicker(activity, palette, rows, selection);
        picker.open(title, hint);
        return picker;
    }
    private int dp(int value) { return (int)(activity.getResources().getDisplayMetrics().density * value + 0.5f); }
    private void open(String title, String hint) {
        int text = UiTheme.readableForeground(palette.text, palette.raised);
        int secondary = UiTheme.readableForeground(palette.muted, palette.raised);
        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(20), dp(14), dp(20), dp(4));
        header.setBackgroundColor(palette.raised);
        TextView heading = new TextView(activity);
        heading.setText(title); heading.setTextSize(20); heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        heading.setTextColor(text);
        header.addView(heading);
        search = new EditText(activity);
        search.setSingleLine(true); search.setTextColor(text); search.setHintTextColor(secondary);
        search.setHint(hint); search.setContentDescription(hint);
        search.setMinHeight(dp(48));
        search.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        search.setImeOptions(EditorInfo.IME_ACTION_GO | EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
        search.setFilters(new InputFilter[]{new InputFilter.LengthFilter(256)});
        search.setBackgroundTintList(ColorStateList.valueOf(UiTheme.readableForeground(palette.accent, palette.raised)));
        header.addView(search, new LinearLayout.LayoutParams(-1, -2));
        status = new TextView(activity); status.setTextSize(13); status.setTextColor(secondary);
        status.setPadding(0, dp(4), 0, dp(4)); status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        header.addView(status);
        dialog = new AlertDialog.Builder(activity).setCustomTitle(header)
                .setAdapter(adapter, null)
                .setNeutralButton("Clear", null).setNegativeButton("Close", null).create();
        dialog.setOnDismissListener(ignored -> retire());
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s,int start,int count,int after) {}
            @Override public void onTextChanged(CharSequence s,int start,int before,int count) {}
            @Override public void afterTextChanged(Editable value) {
                if (closed) return;
                displayed = choices.filter(value.toString());
                adapter.notifyDataSetChanged();
                ListView list = dialog.getListView();
                if (list != null) { list.clearChoices(); list.setSelection(0); }
                updateStatus();
            }
        });
        search.setOnEditorActionListener((view, action, event) -> {
            if (closed || (action != EditorInfo.IME_ACTION_GO
                    && (event == null || event.getKeyCode() != KeyEvent.KEYCODE_ENTER))) return false;
            if (event != null && event.getAction() != KeyEvent.ACTION_UP) return true;
            if (adapter.getCount() == 1) select(displayed, 0);
            else if (adapter.getCount() > 1) focusResults();
            return true;
        });
        search.setOnKeyListener((view, code, event) -> {
            if (code == KeyEvent.KEYCODE_DPAD_DOWN && event.getAction() == KeyEvent.ACTION_DOWN && adapter.getCount() > 0) {
                focusResults(); return true;
            }
            return false;
        });
        dialog.setOnShowListener(ignored -> {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(palette.raised));
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                    | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
            dialog.getListView().setBackgroundColor(palette.raised);
            dialog.getListView().setOnItemClickListener((parent, view, position, id) -> {
                Object tag = view.getTag();
                if (tag instanceof Binding) {
                    Binding binding = (Binding) tag;
                    select(binding.snapshot, binding.position);
                }
            });
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(text);
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setTextColor(text);
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(view -> {
                search.setText(""); search.requestFocus();
            });
            updateStatus();
        });
        dialog.show();
    }
    void setBackAction(String label,Runnable back){
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setText(label);
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(view->{
            dismiss();
            back.run();
        });
        dialog.setOnCancelListener(ignored->back.run());
    }
    private void focusResults() {
        ListView list = dialog.getListView();
        list.requestFocus(); list.setSelection(0);
    }
    private void updateStatus() {
        int count = adapter.getCount();
        status.setText(count == 0 ? "No matching results" : count + " of " + total + " · type to filter");
    }
    private void select(NavigationChoices.Snapshot snapshot, int position) {
        if (closed) return;
        NavigationChoices.Row row = choices.choose(snapshot, position);
        if (row == null) return;
        IntConsumer callback = selection;
        dismiss(); // Retire this picker before a command opens another dialog.
        if (callback != null && !activity.isFinishing() && !activity.isDestroyed()) callback.accept(row.id);
    }
    private void retire() {
        if (closed) return;
        closed = true; choices.cancel(); selection = null; displayed = null;
        adapter.notifyDataSetChanged();
    }
    void dismiss() { retire(); if (dialog != null) dialog.dismiss(); }
}
