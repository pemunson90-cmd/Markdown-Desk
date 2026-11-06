package com.example.markdowndesk;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Read-only SAF JSON explorer; the original JSON file is never opened for writing. */
public final class JsonExplorerActivity extends Activity {
    private static final int REQUEST_OPEN=6101;
    private static final int REQUEST_EXPORT=6102;
    private static final int REQUEST_OPEN_OTHER=6103;
    private Uri document;
    private final ArrayList<JsonExplorerEngine.Step> path=new ArrayList<>();
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private int offset=0;
    private int revision=0;
    private UiTheme.Palette palette;
    private LinearLayout root, entries;
    private TextView heading, location, status;
    private EditText filter;
    private JsonExplorerEngine.Page page;
    private String pendingExport;

    static boolean looksLikeJson(Context context, Uri uri) {
        if (uri==null || !"content".equalsIgnoreCase(uri.getScheme())) return false;
        try {
            String type=context.getContentResolver().getType(uri);
            if (type!=null && ("application/json".equalsIgnoreCase(type)
                    || "text/json".equalsIgnoreCase(type)
                    || type.toLowerCase(Locale.ROOT).endsWith("+json"))) return true;
        } catch (RuntimeException providerUnavailable) {
            // A provider may reject MIME inspection despite a valid read grant.
        }
        try (Cursor cursor=context.getContentResolver().query(
                uri,new String[]{"_display_name"},null,null,null)) {
            if(cursor!=null && cursor.moveToFirst()) {
                int index=cursor.getColumnIndex("_display_name");
                if(index>=0 && !cursor.isNull(index)) {
                    String name=cursor.getString(index);
                    if(name!=null && name.toLowerCase(Locale.ROOT).endsWith(".json")) return true;
                }
            }
        } catch (RuntimeException providerUnavailable) {
            // Content URI display-name inspection is optional; do not probe writes.
        }
        String fallback=uri.getLastPathSegment();
        return fallback!=null && fallback.toLowerCase(Locale.ROOT).endsWith(".json");
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        SharedPreferences prefs=getSharedPreferences("markdown_desk",MODE_PRIVATE);
        palette=UiTheme.palette(prefs.getInt("ui_theme",0),prefs.getBoolean("dark_theme",false));
        document=getIntent()==null?null:getIntent().getData();
        if (state!=null && document==null && state.getString("jsonDocumentUri")!=null)
            document=Uri.parse(state.getString("jsonDocumentUri"));
        if (document!=null && !"content".equalsIgnoreCase(document.getScheme())) document=null;
        if (state!=null) {
            ArrayList<String> names=state.getStringArrayList("jsonPathNames");
            int[] indices=state.getIntArray("jsonPathIndices");
            if (names!=null && indices!=null && names.size()==indices.length) {
                for(int i=0;i<indices.length;i++)
                    path.add(indices[i]<0 ? JsonExplorerEngine.Step.field(names.get(i))
                            : JsonExplorerEngine.Step.element(indices[i]));
            }
            offset=Math.max(0,state.getInt("jsonOffset",0));
        }
        buildUi();
        if (document==null) showOpenChoices(); else loadPage();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        if(document!=null) state.putString("jsonDocumentUri",document.toString());
        ArrayList<String> names=new ArrayList<>();
        int[] indices=new int[path.size()];
        for(int i=0;i<path.size();i++) {
            JsonExplorerEngine.Step step=path.get(i);
            names.add(step.name==null?"":step.name);
            indices[i]=step.index;
        }
        state.putStringArrayList("jsonPathNames",names);
        state.putIntArray("jsonPathIndices",indices);
        state.putInt("jsonOffset",offset);
    }

    private int dp(int n) { return Math.round(n*getResources().getDisplayMetrics().density); }

    private LinearLayout vertical() {
        LinearLayout view=new LinearLayout(this);
        view.setOrientation(LinearLayout.VERTICAL);
        return view;
    }

    private Button button(String text, View.OnClickListener action) {
        Button b=new Button(this);
        b.setText(text);
        b.setTextSize(13);
        b.setAllCaps(false);
        b.setTextColor(palette.text);
        b.setBackground(UiTheme.rounded(palette.raised,dp(9),palette.border,dp(1)));
        b.setOnClickListener(action);
        b.setMinHeight(dp(48));
        b.setPadding(dp(11),dp(7),dp(11),dp(7));
        return b;
    }

    private TextView text(String value, int size) {
        TextView v=new TextView(this);
        v.setText(value);
        v.setTextColor(palette.text);
        v.setTextSize(size);
        v.setPadding(dp(10),dp(7),dp(10),dp(7));
        return v;
    }

    private LinearLayout row() {
        LinearLayout row=new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        return row;
    }

    private void buildUi() {
        root=vertical();
        root.setBackgroundColor(palette.canvas);
        root.setPadding(dp(10),dp(8),dp(10),dp(8));

        LinearLayout top=row();
        top.addView(button("Back",v->back()),new LinearLayout.LayoutParams(dp(70),dp(48)));
        heading=text("JSON Explorer",18);
        heading.setTypeface(null,Typeface.BOLD);
        top.addView(heading,new LinearLayout.LayoutParams(0,dp(52),1));
        top.addView(button("Open",v->showOpenChoices()),new LinearLayout.LayoutParams(dp(76),dp(48)));
        root.addView(top);

        location=text("$",12);
        location.setTextColor(palette.muted);
        location.setTextIsSelectable(true);
        root.addView(location);

        LinearLayout actions=row();
        actions.addView(button("Copy path",v->copy(JsonExplorerEngine.jsonPath(path))),
                new LinearLayout.LayoutParams(0,dp(48),1));
        actions.addView(button("Copy list",v->copyVisible()),
                new LinearLayout.LayoutParams(0,dp(48),1));
        actions.addView(button("Share list",v->shareVisible()),
                new LinearLayout.LayoutParams(0,dp(48),1));
        root.addView(actions);

        filter=new EditText(this);
        filter.setSingleLine(true);
        filter.setHint("Filter this page by title or value");
        filter.setHintTextColor(palette.muted);
        filter.setTextColor(palette.text);
        filter.setTextSize(14);
        filter.setPadding(dp(12),dp(8),dp(12),dp(8));
        filter.setBackground(UiTheme.rounded(palette.surface,dp(9),palette.border,dp(1)));
        root.addView(filter,new LinearLayout.LayoutParams(-1,dp(50)));
        filter.addTextChangedListener(new TextWatcher(){
            @Override public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            @Override public void onTextChanged(CharSequence s,int start,int before,int count){renderEntries();}
            @Override public void afterTextChanged(Editable e){}
        });

        status=text("Select a JSON document.",12);
        status.setTextColor(palette.muted);
        root.addView(status);

        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);
        entries=vertical();
        scroll.addView(entries);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        // Recovery: use normal Android window fitting. The custom Android 16
        // insets callback introduced in code32 is isolated as a crash suspect.
        // Keep all JSON navigation, picker and transcript improvements.
        setContentView(root);
    }

    private void showOpenChoices() {
        new AlertDialog.Builder(this).setTitle("Choose how to open JSON")
                .setItems(new String[]{
                        "Browse device files (keep access)",
                        "Browse with another app (My Files, if available)"
                },(dialog,which)->{
                    if(which==0)chooseDocument();
                    else chooseOtherApp();
                })
                .setNegativeButton("Cancel",null).show();
    }

    /** Import-style picker can expose Samsung My Files and other apps that do not
     * appear as persistent DocumentsProvider roots. Never use it to edit originals. */
    private void chooseOtherApp() {
        Intent browse=new Intent(Intent.ACTION_GET_CONTENT)
                .addCategory(Intent.CATEGORY_OPENABLE).setType("*/*");
        browse.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivityForResult(Intent.createChooser(browse,"Choose a file app"),
                    REQUEST_OPEN_OTHER);
        } catch (ActivityNotFoundException missing) {
            showFailure("No compatible file app was found. Use Browse device files.");
        }
    }

    private void chooseDocument() {
        Intent open=new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE).setType("*/*");
        open.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                |Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try {startActivityForResult(open,REQUEST_OPEN);}
        catch(ActivityNotFoundException missing){showFailure("Android's document picker is unavailable.");}
    }

    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if ((request==REQUEST_OPEN || request==REQUEST_OPEN_OTHER)
                && result==RESULT_OK && data!=null && data.getData()!=null) {
            Uri chosen=data.getData();
            if (!"content".equalsIgnoreCase(chosen.getScheme())) {
                showFailure("Only files from Android's document picker can be opened.");
                return;
            }
            if(request==REQUEST_OPEN) {
                try {
                    getContentResolver().takePersistableUriPermission(chosen,
                            data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } catch (SecurityException | IllegalArgumentException unavailable) {
                    // The transient read grant is sufficient for this session.
                }
            }
            document=chosen;
            path.clear(); offset=0; page=null; filter.setText("");
            loadPage();
        } else if(request==REQUEST_EXPORT) {
            String value=pendingExport;
            pendingExport=null;
            if (result==RESULT_OK && data!=null && data.getData()!=null && value!=null) {
                Uri out=data.getData();
                if (!"content".equalsIgnoreCase(out.getScheme())) {
                    showFailure("Choose a new document using Android Files.");
                    return;
                }
                worker.execute(()->{
                    try(OutputStream stream=getContentResolver().openOutputStream(out,"w")) {
                        if(stream==null) throw new IOException("Could not create the selected document.");
                        stream.write(value.getBytes(StandardCharsets.UTF_8));
                        stream.flush();
                        runOnUiThread(()->Toast.makeText(this,"Markdown copy saved",Toast.LENGTH_LONG).show());
                    }catch(Exception failure){runOnUiThread(()->showFailure("Export failed: "+safeError(failure)));}
                });
            }
        }
    }

    private String safeError(Exception err) {
        String message=err.getMessage();
        return message==null ? err.getClass().getSimpleName()
                : (message.length()>180 ? message.substring(0,180) : message);
    }

    private void showFailure(String msg) {
        new AlertDialog.Builder(this).setTitle("JSON Explorer").setMessage(msg)
                .setPositiveButton("OK",null).show();
    }

    private void loadPage() {
        if(document==null)return;
        final int token=++revision;
        final Uri current=document;
        final ArrayList<JsonExplorerEngine.Step> target=new ArrayList<>(path);
        final int start=offset;
        heading.setText("JSON Explorer");
        location.setText(JsonExplorerEngine.jsonPath(target));
        status.setText("Reading JSON · page "+(start/JsonExplorerEngine.PAGE_SIZE+1)+"…");
        entries.removeAllViews();
        worker.execute(()->{
            try {
                JsonExplorerEngine.Page next=JsonExplorerEngine.scan(
                        ()->getContentResolver().openInputStream(current),target,start);
                runOnUiThread(()->{
                    if(isFinishing()||isDestroyed()||token!=revision)return;
                    page=next;
                    heading.setText(next.type+" Explorer");
                    status.setText(next.type+" · "+(next.type.equals("Value") ? "read only"
                            : "entries "+(start+1)+"–"+(start+next.entries.size())
                              +(next.hasMore?" · more pages available":""))
                            +" · filter searches this page");
                    renderEntries();
                });
            }catch(Exception error){
                runOnUiThread(()->{
                    if(isFinishing()||isDestroyed()||token!=revision)return;
                    status.setText("Unable to read this JSON selection.");
                    showFailure(safeError(error));
                });
            }
        });
    }

    private void renderEntries() {
        if(entries==null||page==null)return;
        entries.removeAllViews();
        String term=filter.getText().toString().toLowerCase(Locale.ROOT).trim();
        if("Value".equals(page.type)) {
            TextView value=text(page.value==null?"":page.value,15);
            value.setTextIsSelectable(true);
            value.setTypeface(Typeface.MONOSPACE);
            entries.addView(value);
            return;
        }
        if(page.conversation && page.messagesKey!=null) {
            final String messages=page.messagesKey;
            entries.addView(button("Read as Claude conversation",v->showTranscript(messages)),
                    new LinearLayout.LayoutParams(-1,dp(54)));
            entries.addView(text("A readable, read-only preview of the first 200 messages. "
                    +"You can copy, share, or save that preview as Markdown.",12));
        }
        int shown=0;
        for (JsonExplorerEngine.Entry e: page.entries) {
            if(!term.isEmpty() && !(e.title+" "+e.detail).toLowerCase(Locale.ROOT).contains(term))
                continue;
            Button b=button(e.title+"\n"+e.detail,v->{
                path.add(e.step);offset=0; page=null;filter.setText("");loadPage();
            });
            b.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);
            b.setTextSize(14);
            b.setMinHeight(dp(62));
            b.setContentDescription("Explore "+e.title+": "+e.detail);
            entries.addView(b,new LinearLayout.LayoutParams(-1,-2));
            View divider=new View(this);divider.setBackgroundColor(palette.border);
            entries.addView(divider,new LinearLayout.LayoutParams(-1,dp(1)));
            shown++;
        }
        if(shown==0)entries.addView(text("No entries on this page match the filter.",13));
        if(page.offset>0 || page.hasMore) {
            LinearLayout pager=row();
            if(page.offset>0)pager.addView(button("Previous 100",v->{
                offset=Math.max(0,offset-JsonExplorerEngine.PAGE_SIZE);
                page=null;filter.setText("");loadPage();
            }),new LinearLayout.LayoutParams(0,dp(50),1));
            if(page.hasMore)pager.addView(button("Next 100",v->{
                offset+=JsonExplorerEngine.PAGE_SIZE;
                page=null;filter.setText("");loadPage();
            }),new LinearLayout.LayoutParams(0,dp(50),1));
            entries.addView(pager);
        }
    }

    private String visibleText() {
        StringBuilder out=new StringBuilder(JsonExplorerEngine.jsonPath(path)).append("\n");
        if(page!=null) {
            if("Value".equals(page.type)) return out.append(page.value).toString();
            for(JsonExplorerEngine.Entry e:page.entries)
                out.append(e.title).append("  ").append(e.detail).append("\n");
        }
        return out.toString();
    }

    private void copyVisible(){copy(visibleText());}
    private void shareVisible(){share(visibleText());}

    private void copy(String value) {
        ClipboardManager manager=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
        if(manager!=null) manager.setPrimaryClip(ClipData.newPlainText("JSON Explorer",value));
        Toast.makeText(this,"Copied",Toast.LENGTH_SHORT).show();
    }

    private void share(String value) {
        Intent send=new Intent(Intent.ACTION_SEND).setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT,value);
        try{startActivity(Intent.createChooser(send,"Share JSON excerpt"));}
        catch(ActivityNotFoundException missing){showFailure("No sharing app is available.");}
    }

    private void showTranscript(String messagesKey) {
        final int token=revision;
        final Uri uri=document;
        final ArrayList<JsonExplorerEngine.Step> selected=new ArrayList<>(path);
        status.setText("Reading conversation preview…");
        worker.execute(()->{
            try {
                String md=JsonExplorerEngine.transcript(
                        ()->getContentResolver().openInputStream(uri),selected,messagesKey);
                runOnUiThread(()->{
                    if(isFinishing()||isDestroyed()||token!=revision)return;
                    status.setText("Conversation preview ready · original JSON unchanged");
                    showTranscriptDialog(md);
                });
            }catch(Exception failure){
                runOnUiThread(()->{
                    if(isFinishing()||isDestroyed()||token!=revision)return;
                    showFailure(safeError(failure));
                });
            }
        });
    }

    private void showTranscriptDialog(String md) {
        // Do not mix the dark palette's light text with Android's default white
        // AlertDialog surface (reported unreadable on the Fold7).
        ScrollView scroll=new ScrollView(this);
        TextView body=text(md.length()>110000 ? md.substring(0,110000)
                +"\n\n[On-screen preview limited; Copy and Save include the full prepared excerpt.]":md,14);
        body.setTextIsSelectable(true);
        body.setPadding(dp(18),dp(8),dp(18),dp(16));
        scroll.addView(body);
        TextView title=text("Claude conversation · read only",18);
        title.setTypeface(null,Typeface.BOLD);
        title.setPadding(dp(20),dp(16),dp(20),dp(4));
        AlertDialog dialog=new AlertDialog.Builder(this)
                .setCustomTitle(title)
                .setView(scroll)
                .setNeutralButton("Copy", (ignored,which)->copy(md))
                .setNegativeButton("Close",null)
                .setPositiveButton("Save .md",(ignored,which)->saveMarkdown(md))
                .create();
        dialog.setOnShowListener(ignored -> {
            if(dialog.getWindow()!=null) {
                dialog.getWindow().setBackgroundDrawable(
                        UiTheme.rounded(palette.surface,dp(14),palette.border,dp(1)));
                int width=Math.min(getResources().getDisplayMetrics().widthPixels-dp(28),dp(760));
                int height=Math.min(getResources().getDisplayMetrics().heightPixels-dp(110),dp(640));
                dialog.getWindow().setLayout(Math.max(dp(260),width),Math.max(dp(270),height));
            }
            int buttonColor=UiTheme.readableForeground(palette.accent,palette.surface);
            for(int id:new int[]{AlertDialog.BUTTON_POSITIVE,AlertDialog.BUTTON_NEGATIVE,
                    AlertDialog.BUTTON_NEUTRAL}) {
                Button button=dialog.getButton(id);
                if(button!=null) button.setTextColor(buttonColor);
            }
        });
        dialog.show();
    }

    private void saveMarkdown(String md) {
        pendingExport=md;
        Intent create=new Intent(Intent.ACTION_CREATE_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("text/markdown")
                .putExtra(Intent.EXTRA_TITLE,"Claude-conversation.md");
        try{startActivityForResult(create,REQUEST_EXPORT);}
        catch(ActivityNotFoundException missing){pendingExport=null;showFailure("No document picker is available.");}
    }

    private void back() {
        if(!path.isEmpty()) {
            path.remove(path.size()-1); offset=0;page=null;
            filter.setText("");loadPage();
        }else finish();
    }

    @Override public void onBackPressed(){back();}

    @Override protected void onDestroy(){
        revision++;
        worker.shutdownNow();
        super.onDestroy();
    }
}
