package com.example.markdowndesk;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.graphics.Typeface;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.util.DisplayMetrics;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.Layout;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.method.KeyListener;
import android.text.style.BackgroundColorSpan;
import android.view.KeyEvent;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.ValueCallback;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.IOException;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/* JADX INFO: loaded from: classes.dex */
public class MainActivity extends Activity {
    private static final int MAX_TABS = 12;
    private static final int MODE_EDIT = 0;
    private static final int MODE_PREVIEW = 1;
    private static final int MODE_SPLIT = 2;
    private static final String PREFS = "markdown_desk";
    private static final String PREF_DARK = "dark_theme";
    private static final String PREF_FONT = "font_size";
    private static final String PREF_READER_FAMILY="reader_font_family", PREF_READER_LINE="reader_line_height",
            PREF_READER_GAP="reader_paragraph_gap", PREF_READER_MARGIN="reader_margin_px", PREF_READER_MEASURE="reader_measure_px";
    private static final String PREF_PREVIEW_FONT = "preview_font_size";
    private static final String PREF_GOAL = "word_goal";
    private static final String PREF_LAUNCH_SPLASH = "launch_splash";
    private static final String PREF_LINES = "line_numbers";
    private static final String PREF_THEME = "ui_theme";
    private static final String PREF_TYPEWRITER = "typewriter";
    private static final String PREF_SPLIT_RATIO = "split_ratio";
    private static final int REQUEST_PROTECTED_COPY = 1004;
    private static final int REQUEST_CREATE = 1002;
    private static final int REQUEST_OPEN = 1001;
    private static final int REQUEST_SPLASH_IMPORT = 1003;
    // Accessed only by the single I/O executor; the trusted Android parent may be an OS alias.
    private ProtectionStore protectionStore;
    private HistoryController historyController;
    private long historyRequest;
    private AlertDialog historyDialog;
    private long capturedSaveRevision;
    private String restoreSnapshotId;
    private volatile boolean legacyRecoveryProtected;
    private volatile boolean legacyRecoveryProtectionFailed;
    private LinearLayout body;
    private Uri currentUri;
    private boolean dark;
    private boolean dirty;
    private Button editModeButton;
    private KineticEditText editor;
    private Runnable editorSyncRunnable;
    private TextView eyebrowView;
    private Button focusButton;
    private boolean focusMode;
    private Button lockButton;
    private boolean editorLocked;
    private KeyListener editorKeyListener;
    private float fontSp;
    private float previewFontSp;
    private ReaderTypography readerTypography=ReaderTypography.defaults();
    private HorizontalScrollView formatScroll;
    private LinearLayout formats;
    private HorizontalScrollView readToolsScroll;
    private LinearLayout readTools;
    private Button writeFavoriteButton;
    private Button readFavoriteButton;
    private String favoriteIndicatorUri;
    private boolean favoriteIndicatorPinned;
    private long favoriteIndicatorGeneration;
    private Button readOutlineButton;
    private Button readAppearanceButton;
    private LinearLayout header;
    private Runnable highlightRunnable;
    private final MarkdownSyntaxHighlighter.State syntaxHighlight = new MarkdownSyntaxHighlighter.State();
    private View launchSplash;
    private Runnable launchSplashDismissRunnable;
    private boolean launchSplashEnabled;
    private boolean lineNumbers;
    private int loadToken;
    private LinearLayout modeBar;
    private UiTheme.Palette palette;
    private Runnable pendingAfterSave;
    private SharedPreferences preferences;
    private PreviewWebView preview;
    private Button previewModeButton;
    private boolean previewReady;
    private PreviewPage previewPage;
    private PreviewPage previewProbePending;
    private boolean previewProbeRunning;
    private long previewProbeSequence;
    private long previewScrollOperation;
    private Runnable previewScrollRelease;
    private Runnable previewEditorScroll;
    private long sourceNavigationRevision;
    private long sourceRouteRevision;
    private long explicitNavigationSequence;
    private SourceNavigation pendingOutlineNavigation;
    private SourceNavigation sourceEditorNavigation;
    private Runnable sourceEditorScroll;
    private long previewRevision;
    private boolean previewStopped;
    private int previewRestoreY;
    private Runnable previewRunnable;
    private Runnable previewSyncRunnable;
    private boolean readOnly;
    private Runnable recoveryRunnable;
    private LinearLayout root;
    // The existing editor/preview instances stay mounted in this shell when Files opens.
    private LinearLayout editorShell;
    private ScrollView filesRail;
    private View filesRailDivider;
    private boolean filesRailRequested;
    private String saveAsTabId;
    private Button filesButton;
    private Button helpButton;
    private Button saveButton;
    private boolean saveInProgress;
    private String savingTabId;
    private Runnable sessionRunnable;
    private AlertDialog splashDialog;
    private boolean smartEditing;
    private float splitRatio;
    private Button splitModeButton;
    private TextView statusView;
    private boolean suppressChanges;
    private boolean syncingFromEditor;
    private boolean syncingFromPreview;
    private LinearLayout tabsRow;
    private HorizontalScrollView tabsScroll;
    private int themeId;
    private TextView titleView;
    private LinearLayout topBar;
    private boolean typewriterMode;
    private boolean utf8Bom;
    private int wordGoal;
    private AlertDialog workspaceDialog;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final ExecutorService renderIo = Executors.newSingleThreadExecutor();
    private final LatestPreview previewWork = new LatestPreview(this.renderIo,
            runnable -> this.main.post(runnable), this::acceptPreview);
    private final ExecutorService statisticsIo = Executors.newSingleThreadExecutor();
    private final LatestStatistics statistics = new LatestStatistics(this.statisticsIo,
            runnable -> this.main.post(runnable), this::acceptStatistics);
    private Runnable statisticsRunnable;
    private long statisticsRevision;
    private long statisticsRequestedRevision = -1;
    private String statisticsRequestedDocument;
    private Locale statisticsRequestedLocale;
    private LatestStatistics.Result statisticsResult;
    private boolean statisticsStopped;
    private final List<Button> buttons = new ArrayList();
    private final List<Button> tabButtons = new ArrayList();
    private final List<Button> modeButtons = new ArrayList();
    private final List<DocumentTab> tabs = new ArrayList();
    private String displayName = "Untitled.md";
    private String baselineHash = EditorTools.sha256(new byte[MODE_EDIT]);
    private long baselineModified = -1;
    private int mode = MODE_EDIT;
    private int activeTab = -1;

    private static final class SearchHighlightSpan extends BackgroundColorSpan {
        SearchHighlightSpan(int i) {
            super(i);
        }
    }

    @Override // android.app.Activity
    protected void onCreate(Bundle bundle) {
        int i;
        super.onCreate(bundle);
        this.preferences = getSharedPreferences(PREFS, MODE_EDIT);
        if (this.preferences.contains(PREF_THEME)) {
            i = this.preferences.getInt(PREF_THEME, MODE_EDIT);
        } else {
            i = systemDark() ? MODE_PREVIEW : MODE_EDIT;
        }
        this.themeId = i;
        this.palette = UiTheme.palette(this.themeId, systemDark());
        this.dark = this.palette.dark;
        this.fontSp = this.preferences.getFloat(PREF_FONT, 18.0f);
        this.previewFontSp = this.preferences.getFloat(PREF_PREVIEW_FONT, 18.0f);
        this.readerTypography=readReaderTypography();
        this.lineNumbers = this.preferences.getBoolean(PREF_LINES, false);
        this.typewriterMode = this.preferences.getBoolean(PREF_TYPEWRITER, false);
        this.wordGoal = this.preferences.getInt(PREF_GOAL, MODE_EDIT);
        this.splitRatio = Math.max(0.28f, Math.min(0.72f, this.preferences.getFloat(PREF_SPLIT_RATIO, 0.5f)));
        this.launchSplashEnabled = this.preferences.getBoolean(PREF_LAUNCH_SPLASH, true);
        getWindow().setSoftInputMode(16);
        buildInterface();
        showLaunchSplash();
        restoreSessionOrRecovery(getIntent());
    }

    @Override // android.app.Activity
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    @Override // android.app.Activity, android.content.ComponentCallbacks
    public void onConfigurationChanged(Configuration configuration) {
        super.onConfigurationChanged(configuration);
        captureActiveTab();
        rebuildBody();
        applyResponsiveChrome();
        refreshFilesRail();
    }

    @Override // android.app.Activity
    protected void onPause() {
        captureActiveTab();
        if (this.dirty) {
            writeRecoveryNow();
        }
        writeSessionNow();
        pauseDocxExport();
        invalidateProtectedSaves();
        cancelWorkspaceFavorites();
        invalidateReaderActions();
        invalidateProtectionStatus();
        cancelWorkspaceNavigation();
        super.onPause();
    }

    @Override // android.app.Activity
    protected void onDestroy() {
        cancelDocxExport();
        invalidateProtectedSaves();
        cancelWorkspaceFavorites();
        invalidateReaderActions();
        invalidateProtectionStatus();
        cancelWorkspaceNavigation();
        DocumentPrint.cancel(this);
        stopStatistics();
        stopPreview();
        this.main.removeCallbacksAndMessages(null);
        if (this.preview != null) {
            this.preview.destroy();
        }
        this.io.shutdown();
        super.onDestroy();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$onBackPressed$0() {
        captureActiveTab();
        writeSessionNow();
        finish();
    }

    @Override // android.app.Activity
    public void onBackPressed() {
        if (this.launchSplash != null) {
            dismissLaunchSplash();
        } else if (this.focusMode) {
            toggleFocusMode();
        } else if (this.filesRail != null) {
            // Back closes expanded navigation before considering an unsaved-document exit.
            this.filesRailRequested = false;
            refreshFilesRail();
        } else {
            confirmUnsaved(new Runnable() { // from class: com.example.markdowndesk.MainActivity$$ExternalSyntheticLambda59
                @Override // java.lang.Runnable
                public final void run() {
                    MainActivity.this.lambda$onBackPressed$0();
                }
            });
        }
    }

    @Override // android.app.Activity
    public boolean onKeyShortcut(int i, KeyEvent keyEvent) {
        String str;
        if (!keyEvent.isCtrlPressed()) {
            return super.onKeyShortcut(i, keyEvent);
        }
        if(keyEvent.hasModifiers(KeyEvent.META_CTRL_ON)&&(i==KeyEvent.KEYCODE_Z||i==KeyEvent.KEYCODE_Y))
            return runEditorHistoryAction(i==KeyEvent.KEYCODE_Z?android.R.id.undo:android.R.id.redo);
        if(keyEvent.hasModifiers(KeyEvent.META_CTRL_ON|KeyEvent.META_SHIFT_ON)&&i==KeyEvent.KEYCODE_Z)
            return runEditorHistoryAction(android.R.id.redo);
        if (keyEvent.isShiftPressed() && i == 44) {
            showCommandPalette();
            return true;
        }
        switch (i) {
            case 30:
                str = "**";
                break;
            case 34:
                showFindReplace();
                return true;
            case 37:
                str = "_";
                break;
            case 39:
                insertLink();
                return true;
            case 42:
                showNewDocumentMenu();
                return true;
            case 43:
                launchOpen();
                return true;
            case 44:
                setMode(this.mode == MODE_PREVIEW ? MODE_EDIT : MODE_PREVIEW, false);
                return true;
            case 47:
                saveDocument();
                return true;
            default:
                return super.onKeyShortcut(i, keyEvent);
        }
        wrap(str, str);
        return true;
    }

    private void buildInterface() {
        this.root = new LinearLayout(this);
        this.root.setOrientation(MODE_PREVIEW);
        this.root.setFitsSystemWindows(true);
        this.header = new LinearLayout(this);
        this.header.setOrientation(MODE_PREVIEW);
        this.header.setPadding(dp(14), dp(8), dp(14), dp(8));
        this.topBar = new LinearLayout(this);
        this.topBar.setOrientation(MODE_EDIT);
        this.topBar.setGravity(16);
        this.filesButton = createTopButton("Files", "Browse files, recent documents and favorites", new View.OnClickListener() { // from class: com.example.markdowndesk.MainActivity$ExternalSyntheticLambda27
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.lambda$buildInterface$1(view);
            }
        });
        this.filesButton.setSingleLine(true);
        this.filesButton.setMaxLines(1);
        this.filesButton.setEllipsize(TextUtils.TruncateAt.END);
        this.filesButton.setPadding(dp(6), MODE_EDIT, dp(6), MODE_EDIT);
        this.topBar.addView(this.filesButton, new LinearLayout.LayoutParams(dp(72), dp(48)));
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(MODE_PREVIEW);
        linearLayout.setPadding(dp(MAX_TABS), MODE_EDIT, dp(8), MODE_EDIT);
        this.eyebrowView = new TextView(this);
        this.eyebrowView.setText("MARKDOWN DESK");
        this.eyebrowView.setTextSize(10.0f);
        this.eyebrowView.setLetterSpacing(0.16f);
        this.titleView = new TextView(this);
        this.titleView.setTextSize(20.0f);
        this.titleView.setTypeface(Typeface.create("sans-serif", MODE_PREVIEW));
        this.titleView.setSingleLine(true);
        this.titleView.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        linearLayout.addView(this.eyebrowView, matchWrap());
        linearLayout.addView(this.titleView, matchWrap());
        this.topBar.addView(linearLayout, new LinearLayout.LayoutParams(MODE_EDIT, -2, 1.0f));
        this.saveButton = createTopButton("SAVE", "Save document", new View.OnClickListener() { // from class: com.example.markdowndesk.MainActivity$$ExternalSyntheticLambda38
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.lambda$buildInterface$2(view);
            }
        });
        this.saveButton.setSingleLine(true);
        this.saveButton.setMaxLines(1);
        this.saveButton.setPadding(dp(6), MODE_EDIT, dp(6), MODE_EDIT);
        this.topBar.addView(this.saveButton, new LinearLayout.LayoutParams(dp(68), dp(48)));
        this.helpButton = createTopButton("?", "Guide and shortcuts", new View.OnClickListener() { // from class: com.example.markdowndesk.MainActivity$ExternalSyntheticLambda39
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.lambda$buildInterface$3(view);
            }
        });
        this.topBar.addView(this.helpButton, new LinearLayout.LayoutParams(dp(48), dp(48)));
        this.topBar.addView(createTopButton("•••", "More actions", new View.OnClickListener() { // from class: com.example.markdowndesk.MainActivity$$ExternalSyntheticLambda40
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.showOverflow(view);
            }
        }), new LinearLayout.LayoutParams(dp(48), dp(48)));
        this.header.addView(this.topBar, matchWrap());
        this.tabsScroll = new HorizontalScrollView(this);
        this.tabsScroll.setHorizontalScrollBarEnabled(false);
        this.tabsScroll.setFillViewport(true);
        this.tabsRow = new LinearLayout(this);
        this.tabsRow.setOrientation(MODE_EDIT);
        this.tabsRow.setPadding(MODE_EDIT, dp(8), MODE_EDIT, dp(4));
        this.tabsScroll.addView(this.tabsRow, wrapWrap());
        this.header.addView(this.tabsScroll, matchWrap());
        this.modeBar = new LinearLayout(this);
        this.modeBar.setOrientation(MODE_EDIT);
        this.modeBar.setGravity(16);
        this.modeBar.setPadding(dp(6), dp(4), dp(6), MODE_EDIT);
        this.editModeButton = addModeButton("Write", MODE_EDIT);
        this.previewModeButton = addModeButton("Read", MODE_PREVIEW);
        this.splitModeButton = addModeButton("Split", MODE_SPLIT);
        this.focusButton = createPillButton("", "Focus mode", new View.OnClickListener() { // from class: com.example.markdowndesk.MainActivity$$ExternalSyntheticLambda41
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.lambda$buildInterface$4(view);
            }
        });
        this.focusButton.setContentDescription("Enter focus mode");
        this.modeBar.addView(this.focusButton, utilityModeParams());
        this.lockButton = createPillButton("", "Lock editing", new View.OnClickListener() {
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                MainActivity.this.toggleEditorLock();
            }
        });
        this.lockButton.setContentDescription("Lock editing");
        this.modeBar.addView(this.lockButton, utilityModeParams());
        this.header.addView(this.modeBar, matchWrap());
        this.root.addView(this.header, matchWrap());
        this.editor = new KineticEditText(this);
        this.editor.setGravity(8388659);
        this.editor.setTypeface(Typeface.MONOSPACE);
        this.editor.setTextSize(this.fontSp);
        this.editor.setLineSpacing(dp(3), 1.08f);
        this.editor.setPadding(dp(22), dp(22), dp(22), dp(24));
        this.editor.setInputType(147457);
        this.editorKeyListener = this.editor.getKeyListener();
        this.editor.setImeOptions(268435456);
        this.editor.setHorizontallyScrolling(false);
        this.editor.setVerticalScrollBarEnabled(true);
        this.editor.setSaveEnabled(false);
        this.editor.setOverScrollMode(MODE_PREVIEW);
        this.editor.setOnTextScaleListener(new KineticEditText.OnTextScaleListener() { // from class: com.example.markdowndesk.MainActivity$$ExternalSyntheticLambda42
            @Override // com.example.markdowndesk.KineticEditText.OnTextScaleListener
            public final void onTextScale(float f) {
                MainActivity.this.lambda$buildInterface$5(f);
            }
        });
        this.editor.addTextChangedListener(new TextWatcher() { // from class: com.example.markdowndesk.MainActivity.1
            @Override // android.text.TextWatcher
            public void beforeTextChanged(CharSequence charSequence, int i, int i2, int i3) {
            }

            @Override // android.text.TextWatcher
            public void afterTextChanged(Editable editable) {
                MainActivity.this.continueMarkdownLine(editable);
            }

            @Override // android.text.TextWatcher
            public void onTextChanged(CharSequence charSequence, int i, int i2, int i3) {
                MainActivity.this.sourceNavigationRevision++;
                MainActivity.this.searchContentChanged();
                MainActivity.this.invalidatePreview();
                MainActivity.this.invalidateStatistics();
                MainActivity.this.syntaxHighlight.changed(charSequence, i, i2, i3);
                if (MainActivity.this.suppressChanges) {
                    return;
                }
                boolean z = !MainActivity.this.dirty;
                MainActivity.this.dirty = true;
                if (MainActivity.this.activeTab >= 0 && MainActivity.this.activeTab < MainActivity.this.tabs.size()) {
                    ((DocumentTab) MainActivity.this.tabs.get(MainActivity.this.activeTab)).dirty = true;
                }
                MainActivity.this.updateStatus();
                if (z) {
                    MainActivity.this.updateTabsUi();
                }
                MainActivity.this.scheduleRecovery();
                MainActivity.this.schedulePreview();
                MainActivity.this.scheduleHighlightEdit();
                MainActivity.this.scheduleSession();
            }
        });
        this.editor.setOnScrollChangeListener(new View.OnScrollChangeListener() { // from class: com.example.markdowndesk.MainActivity$$ExternalSyntheticLambda43
            @Override // android.view.View.OnScrollChangeListener
            public final void onScrollChange(View view, int i, int i2, int i3, int i4) {
                MainActivity.this.lambda$buildInterface$6(view, i, i2, i3, i4);
            }
        });
        this.preview = new PreviewWebView(this);
        this.preview.setTextSp(this.previewFontSp);
        this.preview.setOnTextScaleListener(new PreviewWebView.OnTextScaleListener() {
            @Override public void onTextScale(float sp) {
                MainActivity.this.previewFontSp = sp;
                MainActivity.this.preferences.edit().putFloat(PREF_PREVIEW_FONT, sp).apply();
                MainActivity.this.renderPreview();
            }
        });
        this.preview.setOverScrollMode(MODE_PREVIEW);
        WebSettings settings = this.preview.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(false);
        settings.setAllowContentAccess(false);
        settings.setAllowFileAccess(false);
        settings.setBlockNetworkLoads(true);
        settings.setLoadsImagesAutomatically(false);
        this.preview.setWebViewClient(new AnonymousClass2());
        this.preview.setOnScrollChangeListener(new View.OnScrollChangeListener() { // from class: com.example.markdowndesk.MainActivity$$ExternalSyntheticLambda44
            @Override // android.view.View.OnScrollChangeListener
            public final void onScrollChange(View view, int i, int i2, int i3, int i4) {
                MainActivity.this.lambda$buildInterface$7(view, i, i2, i3, i4);
            }
        });
        this.editorShell = new LinearLayout(this);
        this.editorShell.setOrientation(LinearLayout.HORIZONTAL);
        this.root.addView(this.editorShell, new LinearLayout.LayoutParams(-1, MODE_EDIT, 1.0f));
        this.body = new LinearLayout(this);
        this.editorShell.addView(this.body, new LinearLayout.LayoutParams(MODE_EDIT, -1, 1.0f));
        this.formatScroll = new HorizontalScrollView(this);
        this.formatScroll.setHorizontalScrollBarEnabled(false);
        this.formats = new LinearLayout(this);
        this.formats.setOrientation(MODE_EDIT);
        this.formats.setPadding(dp(MAX_TABS), dp(7), dp(MAX_TABS), dp(7));
        this.formatScroll.addView(this.formats, wrapWrap());
        this.root.addView(this.formatScroll, matchWrap());
        this.readToolsScroll = new HorizontalScrollView(this);
        this.readToolsScroll.setHorizontalScrollBarEnabled(false);
        this.readTools = new LinearLayout(this);
        this.readTools.setOrientation(MODE_EDIT);
        this.readTools.setPadding(dp(MAX_TABS), dp(7), dp(MAX_TABS), dp(7));
        this.readToolsScroll.addView(this.readTools, wrapWrap());
        this.root.addView(this.readToolsScroll, matchWrap());
        this.readFavoriteButton = addReadTool("☆", "Toggle current document favorite", new View.OnClickListener() {
            @Override public void onClick(View view) { MainActivity.this.pinCurrentDocumentFavorite(); }
        });
        addReadTool("↪", "Go to saved bookmark", new View.OnClickListener() {
            @Override public void onClick(View view) { MainActivity.this.showReaderPlaces(); }
        });
        addReadTool("A−", "Decrease reading size", new View.OnClickListener() { @Override public void onClick(View v) { changePreviewFont(-1); } });
        addReadTool("A+", "Increase reading size", new View.OnClickListener() { @Override public void onClick(View v) { changePreviewFont(1); } });
        this.readOutlineButton = addReadTool("Outline", "Document outline", new View.OnClickListener() { @Override public void onClick(View v) { showOutline(); } });
        this.readAppearanceButton = addReadTool("Appearance", "Reading appearance", new View.OnClickListener() { @Override public void onClick(View v) { showAppearance(); } });
        configureEditorHistoryActions();
        this.writeFavoriteButton = addFormat("☆", "Toggle current document favorite", new View.OnClickListener() {
            @Override public void onClick(View view) { MainActivity.this.pinCurrentDocumentFavorite(); }
        });
        addFormat("Mark", "Bookmark the current cursor position", new View.OnClickListener() {
            @Override public void onClick(View view) { MainActivity.this.promptReaderBookmark(); }
        });
        addFormat("Go", "Go to a saved bookmark", new View.OnClickListener() {
            @Override public void onClick(View view) { MainActivity.this.showReaderPlaces(); }
        });
        addFormat("H1", "Heading", new View.OnClickListener() { // from class: com.example.markdowndesk.MainActivity$$ExternalSyntheticLambda45
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.lambda$buildInterface$8(view);
            }
        });
        addFormat("B", "Bold", new View.OnClickListener() { // from class: com.example.markdowndesk.MainActivity$$ExternalSyntheticLambda46
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.lambda$buildInterface$9(view);
            }
        });
        addFormat("I", "Italic", new View.OnClickListener() { // from class: com.example.markdowndesk.MainActivity$$ExternalSyntheticLambda28
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.lambda$buildInterface$10(view);
            }
        });
        addFormat("S", "Strikethrough", new View.OnClickListener() { // from class: com.example.markdowndesk.MainActivity$$ExternalSyntheticLambda29
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.lambda$buildInterface$11(view);
            }
        });
        addFormat("•", "Bulleted list", new View.OnClickListener() { // from class: com.example.markdowndesk.MainActivity$$ExternalSyntheticLambda30
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.lambda$buildInterface$12(view);
            }
        });
        addFormat("☐", "Task", new View.OnClickListener() { // from class: com.example.markdowndesk.MainActivity$$ExternalSyntheticLambda31
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.lambda$buildInterface$13(view);
            }
        });
        addFormat("❯", "Quote", new View.OnClickListener() { // from class: com.example.markdowndesk.MainActivity$$ExternalSyntheticLambda32
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.lambda$buildInterface$14(view);
            }
        });
        addFormat("↗", "Link", new View.OnClickListener() { // from class: com.example.markdowndesk.MainActivity$$ExternalSyntheticLambda33
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.lambda$buildInterface$15(view);
            }
        });
        addFormat("</>", "Code", new View.OnClickListener() { // from class: com.example.markdowndesk.MainActivity$$ExternalSyntheticLambda34
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.lambda$buildInterface$16(view);
            }
        });
        addFormat("▦", "Table", new View.OnClickListener() { // from class: com.example.markdowndesk.MainActivity$$ExternalSyntheticLambda35
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.lambda$buildInterface$17(view);
            }
        });
        addFormat("—", "Divider", new View.OnClickListener() { // from class: com.example.markdowndesk.MainActivity$$ExternalSyntheticLambda36
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.lambda$buildInterface$18(view);
            }
        });
        addFormat("Today", "Insert date", new View.OnClickListener() { // from class: com.example.markdowndesk.MainActivity$$ExternalSyntheticLambda37
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.lambda$buildInterface$19(view);
            }
        });
        this.statusView = new TextView(this);
        this.statusView.setTextSize(13.0f);
        this.statusView.setSingleLine(true);
        this.statusView.setEllipsize(TextUtils.TruncateAt.END);
        this.statusView.setPadding(dp(18), dp(8), dp(18), dp(10));
        this.statusView.setContentDescription("Document statistics. Tap for details.");
        this.statusView.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { showDocumentInfo(); } });
        this.root.addView(this.statusView, matchWrap());
        applyResponsiveChrome();
        setContentView(this.root);
        applyEditorLockState(false);
        rebuildBody();
        applyColors();
        updateStatus();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$buildInterface$1(View view) {
        showFilesNavigator();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$buildInterface$2(View view) {
        saveDocument();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$buildInterface$3(View view) {
        showTutorial();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$buildInterface$4(View view) {
        toggleFocusMode();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$buildInterface$5(float f) {
        this.fontSp = f;
        this.preferences.edit().putFloat(PREF_FONT, this.fontSp).apply();
        updateStatus();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$buildInterface$6(View view, int i, int i2, int i3, int i4) {
        if (this.activeTab >= 0 && this.activeTab < this.tabs.size()) {
            this.tabs.get(this.activeTab).editorScrollY = i2;
        }
        scheduleSession();
        if (this.mode != MODE_SPLIT || this.syncingFromPreview) {
            return;
        }
        schedulePreviewSyncFromEditor();
    }

    /* JADX INFO: renamed from: com.example.markdowndesk.MainActivity$2, reason: invalid class name */
    class AnonymousClass2 extends WebViewClient {
        AnonymousClass2() {
        }

        @Override // android.webkit.WebViewClient
        public boolean shouldOverrideUrlLoading(WebView webView, WebResourceRequest webResourceRequest) {
            return MainActivity.this.handlePreviewUri(webResourceRequest.getUrl());
        }

        @Override // android.webkit.WebViewClient
        public boolean shouldOverrideUrlLoading(WebView webView, String str) {
            return MainActivity.this.handlePreviewUri(Uri.parse(str));
        }

        @Override // android.webkit.WebViewClient
        public void onPageFinished(WebView webView, String str) {
            MainActivity.this.acknowledgePreviewPage(webView);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$buildInterface$7(View view, int i, int i2, int i3, int i4) {
        if (view != this.preview || !previewScrollIsCurrent()) return;
        if (this.activeTab >= 0 && this.activeTab < this.tabs.size()) {
            this.tabs.get(this.activeTab).previewScrollY = i2;
        }
        scheduleSession();
        if (this.mode != MODE_SPLIT || this.syncingFromEditor) {
            return;
        }
        scheduleEditorSyncFromPreview();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$buildInterface$10(View view) {
        wrap("_", "_");
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$buildInterface$8(View view) {
        prefixLines("# ");
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$buildInterface$9(View view) {
        wrap("**", "**");
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$buildInterface$11(View view) {
        wrap("~~", "~~");
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$buildInterface$12(View view) {
        prefixLines("- ");
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$buildInterface$13(View view) {
        prefixLines("- [ ] ");
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$buildInterface$14(View view) {
        prefixLines("> ");
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$buildInterface$15(View view) {
        insertLink();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$buildInterface$16(View view) {
        insertCode();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$buildInterface$17(View view) {
        insertTable();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$buildInterface$18(View view) {
        insertBlock("\n---\n");
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$buildInterface$19(View view) {
        insertBlock(new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date()));
    }

    private void showLaunchSplash() {
        if (this.launchSplashEnabled) {
            SplashStore.Item item = SplashStore.random(this);
            if (item == null) {
                return;
            }
            DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
            Bitmap bitmap = SplashStore.load(this, item, displayMetrics.widthPixels,
                    displayMetrics.heightPixels);
            if (bitmap == null) {
                return;
            }
            FrameLayout frameLayout = new FrameLayout(this);
            frameLayout.setBackgroundColor(this.palette.canvas);
            frameLayout.setClickable(true);
            frameLayout.setFocusable(true);
            ImageView imageView = new ImageView(this);
            imageView.setImageBitmap(bitmap);
            imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            imageView.setAdjustViewBounds(true);
            frameLayout.addView(imageView, new FrameLayout.LayoutParams(-1, -1));
            TextView textView = new TextView(this);
            textView.setText("Tap anywhere to begin");
            textView.setTextSize(13.0f);
            textView.setTextColor(-1);
            textView.setGravity(17);
            textView.setPadding(dp(16), dp(9), dp(16), dp(9));
            textView.setBackground(UiTheme.rounded(UiTheme.SPLASH_HINT_BACKGROUND, dp(18), withAlpha(-1, 45), MODE_PREVIEW));
            FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(-2, -2, 81);
            layoutParams.setMargins(MODE_EDIT, MODE_EDIT, MODE_EDIT, dp(24));
            frameLayout.addView(textView, layoutParams);
            frameLayout.setOnClickListener(new View.OnClickListener() { // from class: com.example.markdowndesk.MainActivity$$ExternalSyntheticLambda89
                @Override // android.view.View.OnClickListener
                public final void onClick(View view) {
                    MainActivity.this.lambda$showLaunchSplash$20(view);
                }
            });
            this.launchSplash = frameLayout;
            addContentView(frameLayout, new ViewGroup.LayoutParams(-1, -1));
            this.launchSplashDismissRunnable = new Runnable() { // from class: com.example.markdowndesk.MainActivity$$ExternalSyntheticLambda90
                @Override // java.lang.Runnable
                public final void run() {
                    MainActivity.this.dismissLaunchSplash();
                }
            };
            this.main.postDelayed(this.launchSplashDismissRunnable, 5000L);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$showLaunchSplash$20(View view) {
        dismissLaunchSplash();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void dismissLaunchSplash() {
        final View view = this.launchSplash;
        if (view == null) {
            return;
        }
        this.launchSplash = null;
        if (this.launchSplashDismissRunnable != null) {
            this.main.removeCallbacks(this.launchSplashDismissRunnable);
        }
        this.launchSplashDismissRunnable = null;
        view.animate().alpha(0.0f).setDuration(180L).withEndAction(new Runnable() { // from class: com.example.markdowndesk.MainActivity$$ExternalSyntheticLambda26
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.lambda$dismissLaunchSplash$21(view);
            }
        }).start();
    }

    static /* synthetic */ void lambda$dismissLaunchSplash$21(View view) {
        if (view.getParent() instanceof ViewGroup) {
            ((ViewGroup) view.getParent()).removeView(view);
        }
    }

    /**
     * A button for the transient splash gallery. It is styled once and kept out of
     * the activity's button list so that reopening the gallery does not pile up
     * references to dismissed dialogs.
     */
    private Button galleryButton(String label, View.OnClickListener onClickListener) {
        Button button = createButton(label, onClickListener);
        this.buttons.remove(button);
        styleButton(button, false, false);
        return button;
    }

    private void showSplashGallery() {
        final List<SplashStore.Item> items = SplashStore.all(this);
        int enabledCount = 0;
        for (SplashStore.Item item : items) {
            if (item.enabled) {
                enabledCount++;
            }
        }

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(1);
        column.setPadding(dp(20), dp(8), dp(20), dp(12));

        TextView blurb = new TextView(this);
        blurb.setText("Every image that is switched on takes its turn at random when the app opens. "
                + "Tap an image to switch it on or off, or use ✕ to remove it.");
        blurb.setTextSize(14.0f);
        blurb.setLineSpacing(dp(3), 1.0f);
        blurb.setTextColor(this.palette.muted);
        blurb.setPadding(0, 0, 0, dp(12));
        column.addView(blurb, matchWrap());

        if (!this.launchSplashEnabled) {
            TextView warning = new TextView(this);
            warning.setText("The launch splash is currently switched off under Appearance, so none of "
                    + "these will appear until you switch it back on.");
            warning.setTextSize(13.0f);
            warning.setTextColor(UiTheme.readableForeground(this.palette.accent, this.palette.raised));
            warning.setPadding(dp(2), 0, dp(2), dp(12));
            column.addView(warning, matchWrap());
        }

        Button addButton = galleryButton("＋  Add image…", new View.OnClickListener() {
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.launchSplashImport();
            }
        });
        addButton.setGravity(8388627);
        column.addView(addButton, matchWrap());

        LinearLayout bulkRow = new LinearLayout(this);
        bulkRow.setOrientation(0);
        Button allOn = galleryButton("All on", new View.OnClickListener() {
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.setAllSplashesEnabled(true);
            }
        });
        Button allOff = galleryButton("All off", new View.OnClickListener() {
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.setAllSplashesEnabled(false);
            }
        });
        bulkRow.addView(allOn, new LinearLayout.LayoutParams(0, dp(44), 1.0f));
        bulkRow.addView(allOff, new LinearLayout.LayoutParams(0, dp(44), 1.0f));
        column.addView(bulkRow, matchWrap());

        TextView sectionLabel = new TextView(this);
        sectionLabel.setText("IMAGES");
        sectionLabel.setLetterSpacing(0.14f);
        sectionLabel.setTextSize(11.0f);
        sectionLabel.setTextColor(UiTheme.readableForeground(this.palette.accent, this.palette.raised));
        sectionLabel.setPadding(dp(4), dp(18), 0, dp(8));
        column.addView(sectionLabel, matchWrap());

        if (items.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("No images left. Add one, or restore the images that came with the app.");
            empty.setTextSize(14.0f);
            empty.setTextColor(this.palette.muted);
            empty.setPadding(dp(4), 0, dp(4), dp(8));
            column.addView(empty, matchWrap());
        }

        for (final SplashStore.Item item : items) {
            column.addView(buildSplashRow(item));
        }

        if (SplashStore.removedCount(this) > 0) {
            Button restore = galleryButton("↺  Restore removed app images", new View.OnClickListener() {
                @Override // android.view.View.OnClickListener
                public final void onClick(View view) {
                    MainActivity.this.restoreBundledSplashes();
                }
            });
            restore.setGravity(8388627);
            LinearLayout.LayoutParams restoreParams = new LinearLayout.LayoutParams(-1, -2);
            restoreParams.setMargins(0, dp(10), 0, 0);
            column.addView(restore, restoreParams);
        }

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.addView(column, matchWrap());

        this.splashDialog = new AlertDialog.Builder(this)
                .setTitle("Splash images · " + enabledCount + " of " + items.size() + " on")
                .setView(scrollView)
                .setPositiveButton("Done", (DialogInterface.OnClickListener) null)
                .create();
        this.splashDialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override // android.content.DialogInterface.OnShowListener
            public final void onShow(DialogInterface dialogInterface) {
                MainActivity.this.styleDialog(MainActivity.this.splashDialog);
            }
        });
        this.splashDialog.show();
    }

    private View buildSplashRow(final SplashStore.Item item) {
        final LinearLayout row = new LinearLayout(this);
        row.setOrientation(0);
        row.setGravity(16);
        row.setPadding(dp(10), dp(8), dp(6), dp(8));
        row.setClickable(true);
        row.setFocusable(true);

        ImageView thumb = new ImageView(this);
        thumb.setScaleType(ImageView.ScaleType.FIT_CENTER);
        thumb.setBackground(UiTheme.rounded(withAlpha(this.palette.muted, 40), dp(9),
                this.palette.border, 1));
        Bitmap bitmap = SplashStore.load(this, item, dp(56), dp(56));
        if (bitmap != null) {
            thumb.setImageBitmap(bitmap);
        }
        LinearLayout.LayoutParams thumbParams = new LinearLayout.LayoutParams(dp(52), dp(52));
        thumbParams.setMargins(0, 0, dp(12), 0);
        row.addView(thumb, thumbParams);

        LinearLayout text = new LinearLayout(this);
        text.setOrientation(1);
        final TextView title = new TextView(this);
        title.setText(item.label);
        title.setTextSize(15.0f);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setTextColor(this.palette.text);
        final TextView state = new TextView(this);
        state.setTextSize(12.0f);
        text.addView(title, matchWrap());
        text.addView(state, matchWrap());
        row.addView(text, new LinearLayout.LayoutParams(0, -2, 1.0f));

        Button remove = new Button(this);
        remove.setText("✕");
        remove.setAllCaps(false);
        remove.setTextSize(15.0f);
        remove.setPadding(0, 0, 0, 0);
        remove.setMinWidth(dp(40));
        remove.setMinHeight(dp(40));
        remove.setTextColor(this.palette.muted);
        remove.setBackground(UiTheme.rounded(0, dp(11), this.palette.border, 1));
        remove.setStateListAnimator(null);
        remove.setTooltipText("Remove " + item.label);
        remove.setContentDescription("Remove " + item.label);
        remove.setOnClickListener(new View.OnClickListener() {
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.confirmSplashRemoval(item);
            }
        });
        row.addView(remove, new LinearLayout.LayoutParams(dp(40), dp(40)));

        paintSplashRow(row, state, item);
        row.setOnClickListener(new View.OnClickListener() {
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                SplashStore.setEnabled(MainActivity.this, item, !item.enabled);
                MainActivity.this.paintSplashRow(row, state, item);
                MainActivity.this.updateSplashDialogTitle();
            }
        });

        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
        rowParams.setMargins(0, 0, 0, dp(8));
        row.setLayoutParams(rowParams);
        return row;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void paintSplashRow(LinearLayout row, TextView state, SplashStore.Item item) {
        row.setBackground(UiTheme.rounded(item.enabled ? this.palette.accentSoft : 0, dp(13),
                item.enabled ? this.palette.accent : this.palette.border, 1));
        state.setText(item.enabled
                ? (item.bundled ? "On · in the rotation" : "On · imported")
                : (item.bundled ? "Off" : "Off · imported"));
        state.setTextColor(item.enabled ? UiTheme.readableForeground(this.palette.accent, this.palette.accentSoft) : this.palette.muted);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void updateSplashDialogTitle() {
        if (this.splashDialog == null) {
            return;
        }
        List<SplashStore.Item> items = SplashStore.all(this);
        int enabledCount = 0;
        for (SplashStore.Item item : items) {
            if (item.enabled) {
                enabledCount++;
            }
        }
        this.splashDialog.setTitle("Splash images · " + enabledCount + " of " + items.size() + " on");
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void setAllSplashesEnabled(boolean enabled) {
        SplashStore.setAllEnabled(this, enabled);
        reopenSplashGallery();
        Toast.makeText(this, enabled ? "All splash images on" : "All splash images off", 0).show();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void restoreBundledSplashes() {
        SplashStore.restoreBundled(this);
        reopenSplashGallery();
        Toast.makeText(this, "Restored the images that came with the app", 0).show();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void confirmSplashRemoval(final SplashStore.Item item) {
        new AlertDialog.Builder(this)
                .setTitle("Remove " + item.label + "?")
                .setMessage(item.bundled
                        ? "This image came with the app. It will leave the gallery, and you can bring it back with Restore removed app images."
                        : "This image was imported, so removing it deletes the copy Markdown Desk keeps. The original file on your device is untouched.")
                .setPositiveButton("Remove", new DialogInterface.OnClickListener() {
                    @Override // android.content.DialogInterface.OnClickListener
                    public final void onClick(DialogInterface dialogInterface, int i) {
                        SplashStore.remove(MainActivity.this, item);
                        MainActivity.this.reopenSplashGallery();
                    }
                })
                .setNegativeButton("Keep", (DialogInterface.OnClickListener) null)
                .show();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void reopenSplashGallery() {
        if (this.splashDialog != null) {
            this.splashDialog.dismiss();
            this.splashDialog = null;
        }
        showSplashGallery();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void launchSplashImport() {
        Intent intent = new Intent("android.intent.action.OPEN_DOCUMENT")
                .addCategory("android.intent.category.OPENABLE")
                .setType("image/*");
        intent.putExtra("android.intent.extra.ALLOW_MULTIPLE", true);
        intent.addFlags(1);
        try {
            startActivityForResult(intent, REQUEST_SPLASH_IMPORT);
        } catch (ActivityNotFoundException e) {
            showError("No image picker is available on this device.");
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void importSplashImages(Intent intent) {
        List<Uri> sources = new ArrayList<Uri>();
        ClipData clipData = intent.getClipData();
        if (clipData != null) {
            for (int i = 0; i < clipData.getItemCount(); i++) {
                Uri uri = clipData.getItemAt(i).getUri();
                if (uri != null) {
                    sources.add(uri);
                }
            }
        } else if (intent.getData() != null) {
            sources.add(intent.getData());
        }
        if (sources.isEmpty()) {
            return;
        }
        int added = 0;
        String failure = null;
        for (Uri uri : sources) {
            try {
                SplashStore.importImage(this, uri);
                added++;
            } catch (Exception e) {
                failure = message(e);
            }
        }
        if (added > 0) {
            reopenSplashGallery();
            Toast.makeText(this, added == 1 ? "Added 1 splash image" : "Added " + added + " splash images", 0).show();
        }
        if (failure != null) {
            showError(added > 0
                    ? "Some images could not be added. " + failure
                    : "That image could not be added. " + failure);
        }
    }

    private Button createButton(String str, View.OnClickListener onClickListener) {
        Button button = new Button(this);
        button.setText(str);
        button.setAllCaps(false);
        button.setTextSize(14.0f);
        button.setGravity(17);
        button.setMinHeight(dp(40));
        button.setMinWidth(dp(40));
        button.setPadding(dp(13), MODE_EDIT, dp(13), MODE_EDIT);
        button.setOnClickListener(onClickListener);
        button.setStateListAnimator(null);
        this.buttons.add(button);
        return button;
    }

    private Button createTopButton(String str, String str2, View.OnClickListener onClickListener) {
        Button buttonCreateButton = createButton(str, onClickListener);
        buttonCreateButton.setTextSize(str.length() > MODE_SPLIT ? 11.0f : 18.0f);
        buttonCreateButton.setTypeface(Typeface.DEFAULT, MODE_PREVIEW);
        buttonCreateButton.setTooltipText(str2);
        buttonCreateButton.setContentDescription(str2);
        return buttonCreateButton;
    }

    private Button createPillButton(String str, String str2, View.OnClickListener onClickListener) {
        Button buttonCreateButton = createButton(str, onClickListener);
        buttonCreateButton.setTooltipText(str2);
        buttonCreateButton.setContentDescription(str2);
        return buttonCreateButton;
    }

    private LinearLayout.LayoutParams utilityModeParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(48), dp(48));
        params.setMargins(dp(4), 0, 0, 0);
        return params;
    }

    private void setButtonIcon(Button button, int resId, int color) {
        if (button == null) return;
        Drawable drawable = getDrawable(resId);
        if (drawable != null) {
            drawable = drawable.mutate();
            drawable.setTint(color);
            drawable.setBounds(0, 0, dp(21), dp(21));
        }
        button.setText("");
        button.setCompoundDrawablePadding(0);
        button.setCompoundDrawables(null, null, null, null);
        button.setGravity(Gravity.CENTER);
        button.setPadding(0, 0, 0, 0);
        button.setForeground(drawable);
        button.setForegroundGravity(Gravity.CENTER);
    }

    private Button addModeButton(String str, final int i) {
        Button buttonCreatePillButton = createPillButton(str, str + " mode", new View.OnClickListener() { // from class: com.example.markdowndesk.MainActivity$$ExternalSyntheticLambda21
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.lambda$addModeButton$22(i, view);
            }
        });
        this.modeButtons.add(buttonCreatePillButton);
        this.modeBar.addView(buttonCreatePillButton, new LinearLayout.LayoutParams(MODE_EDIT, dp(48), 1.0f));
        return buttonCreatePillButton;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$addModeButton$22(int i, View view) {
        setMode(i, i == 0);
    }

    private void configureEditorHistoryActions() {
        this.editor.setHistoryActionListener(this::runEditorHistoryAction);
        addFormat("Undo", "Undo last edit", view -> runEditorHistoryAction(android.R.id.undo));
        addFormat("Redo", "Redo last undone edit", view -> runEditorHistoryAction(android.R.id.redo));
    }

    private boolean runEditorHistoryAction(int actionId) {
        if(actionId!=android.R.id.undo&&actionId!=android.R.id.redo)return false;
        // Consume blocked native keyboard/context actions too: never fall through the lock.
        if(this.editor==null||this.activeTab<0||this.activeTab>=this.tabs.size()||!canMutate())return true;
        ensureEditor(false);
        boolean previousSmartEditing=this.smartEditing;
        this.smartEditing=true;
        try {return this.editor.performNativeHistoryAction(actionId);}
        finally {this.smartEditing=previousSmartEditing;}
    }

    private Button addFormat(String str, String str2, View.OnClickListener onClickListener) {
        Button buttonCreatePillButton = createPillButton(str, str2, onClickListener);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(-2, dp(48));
        layoutParams.setMargins(MODE_EDIT, MODE_EDIT, dp(7), MODE_EDIT);
        this.formats.addView(buttonCreatePillButton, layoutParams);
        return buttonCreatePillButton;
    }

    private Button addReadTool(String label, String description, View.OnClickListener listener) {
        Button button = createPillButton(label, description, listener);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, dp(48));
        params.setMargins(MODE_EDIT, MODE_EDIT, dp(7), MODE_EDIT);
        this.readTools.addView(button, params);
        return button;
    }

    private void updateFormatAvailability() {
        if (this.formats == null) return;
        boolean enabled = !this.editorLocked;
        for (int i = 0; i < this.formats.getChildCount(); i++) {
            View child = this.formats.getChildAt(i);
            child.setEnabled(enabled);
            child.setAlpha(enabled ? 1.0f : 0.38f);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void rebuildBody() {
        invalidatePreview();
        if (this.body == null) {
            return;
        }
        detach(this.editor);
        detach(this.preview);
        this.body.removeAllViews();
        int i = getResources().getConfiguration().screenWidthDp;
        int i2 = MODE_EDIT;
        this.body.setOrientation((this.mode == MODE_SPLIT && i >= 600) ? MODE_EDIT : MODE_PREVIEW);
        if (this.mode == 0) {
            this.body.addView(this.editor, paneParams());
        } else if (this.mode == MODE_PREVIEW) {
            this.body.addView(this.preview, paneParams());
        } else {
            if (this.body.getOrientation() == LinearLayout.HORIZONTAL) {
                this.body.addView(this.editor, paneParams(this.splitRatio));
                final View divider = new View(this);
                divider.setBackgroundColor(this.palette != null ? this.palette.border : 0xFF444444);
                divider.setContentDescription("Resize Split panes");
                divider.setFocusable(true);
                divider.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
                divider.setAccessibilityDelegate(new View.AccessibilityDelegate() {
                    @Override public void onInitializeAccessibilityNodeInfo(View host, android.view.accessibility.AccessibilityNodeInfo info) {
                        super.onInitializeAccessibilityNodeInfo(host, info);
                        info.setClassName("android.widget.SeekBar");
                        info.setRangeInfo(android.view.accessibility.AccessibilityNodeInfo.RangeInfo.obtain(
                                android.view.accessibility.AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_FLOAT,
                                0.28f, 0.72f, splitRatio));
                        info.addAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
                        info.addAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD);
                        info.addAction(new android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction(
                                android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK, "Center Split panes"));
                    }
                    @Override public boolean performAccessibilityAction(View host, int action, android.os.Bundle arguments) {
                        if (action == android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) {
                            adjustSplitRatio(1);
                            return true;
                        }
                        if (action == android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) {
                            adjustSplitRatio(-1);
                            return true;
                        }
                        if (action == android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK) {
                            adjustSplitRatio(0);
                            return true;
                        }
                        return super.performAccessibilityAction(host, action, arguments);
                    }
                });
                divider.setOnKeyListener(new View.OnKeyListener() {
                    @Override public boolean onKey(View host, int keyCode, KeyEvent event) {
                        if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
                        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                            adjustSplitRatio(-1);
                            return true;
                        }
                        if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                            adjustSplitRatio(1);
                            return true;
                        }
                        if (keyCode == KeyEvent.KEYCODE_MOVE_HOME) {
                            adjustSplitRatio(0);
                            return true;
                        }
                        return false;
                    }
                });
                divider.setOnTouchListener(new View.OnTouchListener() {
                    @Override public boolean onTouch(View v, MotionEvent event) {
                        if (event.getActionMasked() == MotionEvent.ACTION_DOWN || event.getActionMasked() == MotionEvent.ACTION_MOVE) {
                            int[] loc = new int[2]; body.getLocationOnScreen(loc);
                            if (body.getWidth() > 0) {
                                splitRatio = Math.max(0.28f, Math.min(0.72f, (event.getRawX() - loc[0]) / body.getWidth()));
                                editor.setLayoutParams(paneParams(splitRatio));
                                preview.setLayoutParams(paneParams(1.0f - splitRatio));
                            }
                            return true;
                        }
                        if (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                            preferences.edit().putFloat(PREF_SPLIT_RATIO, splitRatio).apply();
                            return true;
                        }
                        return false;
                    }
                });
                this.body.addView(divider, new LinearLayout.LayoutParams(dp(6), -1));
                this.body.addView(this.preview, paneParams(1.0f - this.splitRatio));
            } else {
                this.body.addView(this.editor, paneParams());
                this.body.addView(this.preview, paneParams());
            }
        }
        this.formatScroll.setVisibility((this.mode == MODE_PREVIEW || this.focusMode) ? View.GONE : View.VISIBLE);
        if (this.readToolsScroll != null) this.readToolsScroll.setVisibility((this.mode == MODE_PREVIEW && !this.focusMode) ? View.VISIBLE : View.GONE);
        updateFormatAvailability();
        if (this.mode != 0) {
            renderPreview();
        }
        this.body.setAlpha(0.0f);
        this.body.animate().alpha(1.0f).setDuration(150L).start();
        updateModeChrome();
    }

    private void adjustSplitRatio(int direction) {
        this.splitRatio = SplitResizeController.adjust(this.splitRatio, direction);
        this.editor.setLayoutParams(paneParams(this.splitRatio));
        this.preview.setLayoutParams(paneParams(1.0f - this.splitRatio));
        this.preferences.edit().putFloat(PREF_SPLIT_RATIO, this.splitRatio).apply();
    }

    private void setMode(int i, boolean z) {
        if (this.mode != i) { this.sourceRouteRevision++; cancelSearchLocation(); }
        this.mode = i;
        if (i == MODE_SPLIT && !this.preferences.getBoolean("split_hint_seen", false)) {
            Toast.makeText(this, "Split: Markdown source + rendered Read view", MODE_PREVIEW).show();
            this.preferences.edit().putBoolean("split_hint_seen", true).apply();
        }
        if (this.activeTab >= 0 && this.activeTab < this.tabs.size()) {
            this.tabs.get(this.activeTab).mode = this.mode;
        }
        rebuildBody();
        updateStatus();
        scheduleSession();
        if (!z || this.mode == MODE_PREVIEW) {
            hideKeyboard();
        } else {
            this.editor.requestFocus();
            showKeyboard(this.editor);
        }
    }

    private void applyColors() {
        int i;
        this.palette = UiTheme.palette(this.themeId, systemDark());
        this.dark = this.palette.dark;
        this.root.setBackgroundColor(this.palette.canvas);
        this.header.setBackgroundColor(this.palette.surface);
        this.formatScroll.setBackgroundColor(this.palette.surface);
        this.formats.setBackgroundColor(this.palette.surface);
        if (this.readToolsScroll != null) this.readToolsScroll.setBackgroundColor(this.palette.surface);
        if (this.readTools != null) this.readTools.setBackgroundColor(this.palette.surface);
        this.statusView.setBackgroundColor(this.palette.surface);
        this.body.setBackgroundColor(this.palette.canvas);
        refreshFilesRail();
        this.titleView.setTextColor(this.palette.text);
        this.eyebrowView.setTextColor(UiTheme.readableForeground(this.palette.accent, this.palette.surface));
        this.statusView.setTextColor(this.palette.muted);
        this.editor.setTextColor(this.palette.text);
        this.editor.setHintTextColor(this.palette.muted);
        this.editor.setBackgroundColor(this.palette.canvas);
        this.editor.setHighlightColor(withAlpha(this.palette.accent, 72));
        this.editor.setEditorChrome(this.lineNumbers, true, withAlpha(this.palette.muted, 170), withAlpha(this.palette.accent, 20));
        this.editor.setTypewriterMode(this.typewriterMode);
        this.preview.setBackgroundColor(this.palette.canvas);
        Iterator<Button> it = this.buttons.iterator();
        while (true) {
            boolean zHasNext = it.hasNext();
            i = MODE_EDIT;
            if (!zHasNext) {
                break;
            } else {
                styleButton(it.next(), false, false);
            }
        }
        this.saveButton.setTextColor(UiTheme.readableForeground(-1, this.palette.accent));
        this.saveButton.setBackground(UiTheme.rounded(this.palette.accent, dp(13), this.palette.accent, MODE_EDIT));
        updateModeChrome();
        updateTabsUi();
        updateFavoriteButtons(this.favoriteIndicatorPinned);
        getWindow().setStatusBarColor(this.palette.surface);
        getWindow().setNavigationBarColor(this.palette.surface);
        if (!this.palette.dark) {
            i = 8208;
        }
        getWindow().getDecorView().setSystemUiVisibility(i);
        scheduleHighlight();
        schedulePreview();
    }

    private void styleButton(Button button, boolean z, boolean z2) {
        int i;
        int i2;
        if (z2) {
            i = this.palette.accent;
        } else {
            i = z ? this.palette.accentSoft : MODE_EDIT;
        }
        UiTheme.Palette palette = this.palette;
        int i3 = z ? palette.accent : palette.border;
        if (z2) {
            i2 = UiTheme.readableForeground(-1, this.palette.accent);
        } else {
            i2 = z ? UiTheme.readableForeground(this.palette.accent, this.palette.accentSoft) : this.palette.text;
        }
        button.setTextColor(i2);
        button.setBackground(UiTheme.rounded(i, dp(13), i3, !z2 ? 1 : 0));
    }

    private void updateModeChrome() {
        if (this.modeButtons.isEmpty() || this.palette == null) {
            return;
        }
        styleButton(this.editModeButton, this.mode == MODE_EDIT, false);
        styleButton(this.previewModeButton, this.mode == MODE_PREVIEW, false);
        styleButton(this.splitModeButton, this.mode == MODE_SPLIT, false);
        this.editModeButton.setSelected(this.mode == MODE_EDIT);
        this.previewModeButton.setSelected(this.mode == MODE_PREVIEW);
        this.splitModeButton.setSelected(this.mode == MODE_SPLIT);
        styleButton(this.focusButton, this.focusMode, false);
        this.focusButton.setContentDescription(this.focusMode ? "Exit focus mode" : "Enter focus mode");
        this.focusButton.setSelected(this.focusMode);
        setButtonIcon(this.focusButton, R.drawable.ic_visibility, this.focusButton.getCurrentTextColor());
        if (this.lockButton != null) {
            styleButton(this.lockButton, this.editorLocked, false);
            boolean sourceVisible = this.mode != MODE_PREVIEW;
            this.lockButton.setEnabled(sourceVisible && !this.readOnly);
            this.lockButton.setAlpha((sourceVisible && !this.readOnly) ? 1.0f : 0.45f);
            int iconColor = this.lockButton.getCurrentTextColor();
            setButtonIcon(this.lockButton, this.editorLocked ? R.drawable.ic_lock : R.drawable.ic_lock_open, iconColor);
        }
    }

    private void applyResponsiveChrome() {
        if (this.modeBar == null || this.saveButton == null) {
            return;
        }
        int widthDp = getResources().getConfiguration().screenWidthDp;
        boolean compact = widthDp > 0 && widthDp < 500;
        // Reserve space for Save, More, filename and header margins. The guide is in More
        // on compact widths so Files remains readable without growing the top bar.
        if (this.helpButton != null) this.helpButton.setVisibility(compact ? View.GONE : View.VISIBLE);
        if (this.filesButton != null) {
            this.filesButton.setSingleLine(true);
            this.filesButton.setMaxLines(1);
            this.filesButton.setEllipsize(TextUtils.TruncateAt.END);
            this.filesButton.setTextSize(compact ? 12.0f : 13.0f);
            int reservedDp = compact ? 28 + 62 + 48 + 56 : 28 + 68 + 44 + 48 + 64;
            int maximumDp = widthDp > 0 ? Math.max(72, widthDp - reservedDp) : 96;
            int labelWidth = (int) Math.ceil(this.filesButton.getPaint().measureText("Files"));
            int desiredWidth = labelWidth + this.filesButton.getPaddingLeft()
                    + this.filesButton.getPaddingRight() + dp(10);
            ViewGroup.LayoutParams fileParams = this.filesButton.getLayoutParams();
            if (fileParams != null) {
                fileParams.width = Math.max(dp(72), Math.min(dp(maximumDp), desiredWidth));
                fileParams.height = dp(48);
                this.filesButton.setLayoutParams(fileParams);
            }
        }
        this.saveButton.setSingleLine(true);
        this.saveButton.setMaxLines(1);
        this.saveButton.setTextSize(compact ? 10.0f : 11.0f);
        ViewGroup.LayoutParams saveParams = this.saveButton.getLayoutParams();
        if (saveParams != null) {
            saveParams.width = dp(compact ? 62 : 68);
            saveParams.height = dp(48);
            this.saveButton.setLayoutParams(saveParams);
        }
        for (Button button : this.modeButtons) {
            button.setSingleLine(true);
            button.setMaxLines(1);
            button.setTextSize(compact ? 13.0f : 14.0f);
            button.setPadding(dp(compact ? 7 : 13), MODE_EDIT, dp(compact ? 7 : 13), MODE_EDIT);
        }
        if (this.splitModeButton != null) this.splitModeButton.setVisibility(compact ? View.GONE : View.VISIBLE);
        if (this.focusButton != null) {
            ViewGroup.LayoutParams p = this.focusButton.getLayoutParams();
            if (p != null) { p.width = dp(48); p.height = dp(48); this.focusButton.setLayoutParams(p); }
        }
        if (this.lockButton != null) {
            ViewGroup.LayoutParams p = this.lockButton.getLayoutParams();
            if (p != null) { p.width = dp(48); p.height = dp(48); this.lockButton.setLayoutParams(p); }
        }
        // Never identify actions by child index: Favorites/Go and font buttons precede these.
        if (this.readOutlineButton != null) this.readOutlineButton.setText("Outline");
        if (this.readAppearanceButton != null) this.readAppearanceButton.setText(compact ? "Style" : "Appearance");
    }

    private void toggleEditorLock() {
        this.editorLocked = !this.editorLocked;
        if (this.activeTab >= 0 && this.activeTab < this.tabs.size()) {
            this.tabs.get(this.activeTab).editorLocked = this.editorLocked;
        }
        applyEditorLockState(true);
        scheduleSession();
    }

    private void applyEditorLockState(boolean showToast) {
        if (this.editor == null) return;
        if (this.editorKeyListener == null) this.editorKeyListener = this.editor.getKeyListener();
        if (this.editorLocked) {
            this.editor.setKeyListener(null);
            this.editor.setCursorVisible(false);
            hideKeyboard();
            if (this.lockButton != null) this.lockButton.setContentDescription("Unlock editing");
        } else {
            this.editor.setKeyListener(this.editorKeyListener);
            this.editor.setCursorVisible(true);
            if (this.lockButton != null) this.lockButton.setContentDescription("Lock editing");
        }
        applyResponsiveChrome();
        updateModeChrome();
        updateFormatAvailability();
        if (showToast) Toast.makeText(this, this.editorLocked ? "Editing locked" : "Editing unlocked", MODE_EDIT).show();
    }

    private boolean canMutate() {
        if (!this.editorLocked) return true;
        Toast.makeText(this, "Unlock this document to edit it.", MODE_EDIT).show();
        return false;
    }

    private void toggleTheme() {
        this.themeId = (this.palette == null || !this.palette.dark) ? MODE_PREVIEW : MODE_EDIT;
        this.preferences.edit().putInt(PREF_THEME, this.themeId).apply();
        applyColors();
    }

    private void changeFont(int delta) {
        this.fontSp = Math.max(12.0f, Math.min(32.0f, this.fontSp + delta));
        this.editor.setTextSize(this.fontSp);
        this.preferences.edit().putFloat(PREF_FONT, this.fontSp).apply();
        updateStatus();
    }

    private void changePreviewFont(int delta) {
        this.previewFontSp = Math.max(12.0f, Math.min(32.0f, this.previewFontSp + delta));
        this.preview.setTextSp(this.previewFontSp);
        this.preferences.edit().putFloat(PREF_PREVIEW_FONT, this.previewFontSp).apply();
        if (this.mode != MODE_EDIT) renderPreview();
    }

    private void changeActiveFont(int delta) {
        if (this.mode == MODE_PREVIEW || (this.mode == MODE_SPLIT && this.preview.hasFocus())) changePreviewFont(delta);
        else changeFont(delta);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void updateTabsUi() {
        if (this.tabsRow == null || this.palette == null) {
            return;
        }
        this.buttons.removeAll(this.tabButtons);
        this.tabButtons.clear();
        this.tabsRow.removeAllViews();
        for (int tabIndex = MODE_EDIT; tabIndex < this.tabs.size(); tabIndex += MODE_PREVIEW) {
            final int i = tabIndex;
            DocumentTab documentTab = this.tabs.get(i);
            String fullName = documentTab.displayName == null ? "Untitled" : documentTab.displayName;
            String str = fullName;
            if (str.length() > 22) {
                str = str.substring(0, 10) + "…" + str.substring(str.length() - 9);
            }
            Button buttonCreatePillButton = createPillButton((documentTab.dirty ? "●  " : "") + str, "Switch to " + fullName, new View.OnClickListener() { // from class: com.example.markdowndesk.MainActivity$$ExternalSyntheticLambda65
                @Override // android.view.View.OnClickListener
                public final void onClick(View view) {
                    MainActivity.this.lambda$updateTabsUi$23(i, view);
                }
            });
            buttonCreatePillButton.setTextSize(12.0f);
            boolean z = true;
            buttonCreatePillButton.setSingleLine(true);
            buttonCreatePillButton.setEllipsize(TextUtils.TruncateAt.END);
            if (i != this.activeTab) {
                z = false;
            }
            styleButton(buttonCreatePillButton, z, false);
            buttonCreatePillButton.setSelected(z);
            buttonCreatePillButton.setOnLongClickListener(new View.OnLongClickListener() { // from class: com.example.markdowndesk.MainActivity$$ExternalSyntheticLambda66
                @Override // android.view.View.OnLongClickListener
                public final boolean onLongClick(View view) {
                    return MainActivity.this.lambda$updateTabsUi$24(i, view);
                }
            });
            LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(-2, dp(48));
            layoutParams.setMargins(MODE_EDIT, MODE_EDIT, dp(7), MODE_EDIT);
            this.tabsRow.addView(buttonCreatePillButton, layoutParams);
            this.tabButtons.add(buttonCreatePillButton);
        }
        Button buttonCreatePillButton2 = createPillButton("＋", "New document", new View.OnClickListener() { // from class: com.example.markdowndesk.MainActivity$$ExternalSyntheticLambda67
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.lambda$updateTabsUi$25(view);
            }
        });
        buttonCreatePillButton2.setTextSize(18.0f);
        styleButton(buttonCreatePillButton2, false, false);
        this.tabsRow.addView(buttonCreatePillButton2, new LinearLayout.LayoutParams(dp(48), dp(48)));
        this.tabButtons.add(buttonCreatePillButton2);
        refreshFavoriteIndicators();
        if (this.activeTab >= 0) {
            this.tabsScroll.post(new Runnable() { // from class: com.example.markdowndesk.MainActivity$$ExternalSyntheticLambda68
                @Override // java.lang.Runnable
                public final void run() {
                    MainActivity.this.lambda$updateTabsUi$26();
                }
            });
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$updateTabsUi$23(int i, View view) {
        switchToTab(i, false);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ boolean lambda$updateTabsUi$24(int i, View view) {
        if (i != this.activeTab) {
            switchToTab(i, false);
        }
        closeCurrentTab();
        return true;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$updateTabsUi$25(View view) {
        showNewDocumentMenu();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$updateTabsUi$26() {
        this.tabsScroll.smoothScrollTo(Math.max(MODE_EDIT, (this.activeTab * dp(116)) - dp(30)), MODE_EDIT);
    }

    private void createUntitledTab(boolean z) {
        if (this.tabs.size() >= MAX_TABS) {
            Toast.makeText(this, "Close a tab before opening another.", MODE_PREVIEW).show();
            return;
        }
        captureActiveTab();
        DocumentTab documentTab = new DocumentTab();
        documentTab.mode = MODE_EDIT;
        this.tabs.add(documentTab);
        switchToTab(this.tabs.size() - MODE_PREVIEW, z);
        updateTabsUi();
        scheduleSession();
    }

    private void showTabs() {
        if (this.tabs.isEmpty()) {
            return;
        }
        captureActiveTab();
        String[] strArr = new String[this.tabs.size()];
        int i = MODE_EDIT;
        while (i < this.tabs.size()) {
            DocumentTab documentTab = this.tabs.get(i);
            String str = "";
            String str2 = i == this.activeTab ? "▶ " : "";
            String str3 = documentTab.displayName;
            if (documentTab.dirty) {
                str = "  •";
            }
            strArr[i] = str2 + str3 + str;
            i += MODE_PREVIEW;
        }
        new AlertDialog.Builder(this).setTitle("Open documents").setItems(strArr, new DialogInterface.OnClickListener() { // from class: com.example.markdowndesk.MainActivity$$ExternalSyntheticLambda87
            @Override // android.content.DialogInterface.OnClickListener
            public final void onClick(DialogInterface dialogInterface, int i2) {
                MainActivity.this.lambda$showTabs$27(dialogInterface, i2);
            }
        }).setNegativeButton("Close", (DialogInterface.OnClickListener) null).show();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$showTabs$27(DialogInterface dialogInterface, int i) {
        switchToTab(i, false);
    }

    private void closeCurrentTab() {
        if (this.activeTab < 0 || this.activeTab >= this.tabs.size()) {
            return;
        }
        confirmUnsaved(new Runnable() { // from class: com.example.markdowndesk.MainActivity$$ExternalSyntheticLambda96
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.this.removeActiveTab();
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void removeActiveTab() {
        if (this.activeTab < 0 || this.activeTab >= this.tabs.size()) {
            return;
        }
        this.tabs.remove(this.activeTab);
        if (this.tabs.isEmpty()) {
            this.activeTab = -1;
            createUntitledTab(false);
        } else {
            this.activeTab = Math.min(this.activeTab, this.tabs.size() - 1);
            restoreActiveTab(false);
            updateTabsUi();
            scheduleSession();
        }
    }

    private void captureActiveTab() {
        if (this.activeTab < 0 || this.activeTab >= this.tabs.size() || this.editor == null) {
            return;
        }
        DocumentTab documentTab = this.tabs.get(this.activeTab);
        documentTab.uri = this.currentUri == null ? null : this.currentUri.toString();
        documentTab.displayName = this.displayName;
        documentTab.text = this.editor.getText().toString();
        documentTab.baselineHash = this.baselineHash;
        documentTab.baselineModified = this.baselineModified;
        documentTab.utf8Bom = this.utf8Bom;
        documentTab.readOnly = this.readOnly;
        documentTab.editorLocked = this.editorLocked;
        documentTab.dirty = this.dirty;
        documentTab.selectionStart = Math.max(MODE_EDIT, this.editor.getSelectionStart());
        documentTab.selectionEnd = Math.max(documentTab.selectionStart, this.editor.getSelectionEnd());
        documentTab.editorScrollY = Math.max(MODE_EDIT, this.editor.getScrollY());
        documentTab.previewScrollY = previewScrollIsCurrent() ? Math.max(MODE_EDIT, this.preview.getScrollY()) : documentTab.previewScrollY;
        documentTab.mode = this.mode;
    }

    private void switchToTab(int i, boolean z) {
        if (i < 0 || i >= this.tabs.size()) {
            return;
        }
        captureActiveTab();
        this.activeTab = i;
        restoreActiveTab(z);
        updateTabsUi();
        scheduleSession();
    }

    private void restoreActiveTab(boolean z) {
        searchRestoreChanged();
        invalidateHistory();
        if (this.activeTab < 0 || this.activeTab >= this.tabs.size()) {
            return;
        }
        final DocumentTab documentTab = this.tabs.get(this.activeTab);
        this.currentUri = documentTab.uri == null ? null : Uri.parse(documentTab.uri);
        this.displayName = documentTab.displayName;
        this.baselineHash = documentTab.baselineHash;
        this.baselineModified = documentTab.baselineModified;
        this.utf8Bom = documentTab.utf8Bom;
        this.readOnly = documentTab.readOnly;
        this.editorLocked = documentTab.editorLocked;
        this.dirty = documentTab.dirty;
        this.mode = documentTab.mode;
        this.suppressChanges = true;
        beginSearchRestoreText();
        try{this.editor.setText(documentTab.text);}finally{endSearchRestoreText();}
        int iClamp = clamp(documentTab.selectionStart, MODE_EDIT, documentTab.text.length());
        this.editor.setSelection(iClamp, clamp(documentTab.selectionEnd, iClamp, documentTab.text.length()));
        this.suppressChanges = false;
        this.previewRestoreY = documentTab.previewScrollY;
        this.previewReady = false;
        applyEditorLockState(false);
        rebuildBody();
        applyColors();
        updateStatus();
        this.editor.post(new Runnable() { // from class: com.example.markdowndesk.MainActivity$$ExternalSyntheticLambda86
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.this.lambda$restoreActiveTab$28(documentTab);
            }
        });
        if (!z || this.mode == MODE_PREVIEW) {
            hideKeyboard();
        } else {
            this.editor.requestFocus();
            showKeyboard(this.editor);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$restoreActiveTab$28(DocumentTab documentTab) {
        this.editor.scrollTo(MODE_EDIT, Math.max(MODE_EDIT, documentTab.editorScrollY));
    }

    private int findTabByUri(Uri uri) {
        String string = uri == null ? null : uri.toString();
        if (string == null) {
            return -1;
        }
        for (int i = MODE_EDIT; i < this.tabs.size(); i += MODE_PREVIEW) {
            if (string.equals(this.tabs.get(i).uri)) {
                return i;
            }
        }
        return -1;
    }

    private int findTabById(String str) {
        if (str == null) {
            return -1;
        }
        for (int i = MODE_EDIT; i < this.tabs.size(); i += MODE_PREVIEW) {
            if (str.equals(this.tabs.get(i).id)) {
                return i;
            }
        }
        return -1;
    }

    private void launchOpen() {
        Intent type = new Intent("android.intent.action.OPEN_DOCUMENT").addCategory("android.intent.category.OPENABLE").setType("text/*");
        type.putExtra("android.intent.extra.MIME_TYPES", new String[]{"text/markdown", "text/x-markdown", "text/plain", "application/markdown"});
        type.addFlags(67);
        try {
            startActivityForResult(type, REQUEST_OPEN);
        } catch (ActivityNotFoundException e) {
            showError("No document picker is available on this device.");
        }
    }

    private void launchSaveAs() {
        if (this.activeTab < 0 || this.activeTab >= this.tabs.size()) {
            return;
        }
        captureActiveTab();
        this.saveAsTabId = this.tabs.get(this.activeTab).id;
        Intent type = new Intent("android.intent.action.CREATE_DOCUMENT").addCategory("android.intent.category.OPENABLE").setType("text/markdown");
        type.putExtra("android.intent.extra.TITLE", markdownName(this.displayName));
        type.addFlags(67);
        try {
            startActivityForResult(type, REQUEST_CREATE);
        } catch (ActivityNotFoundException e) {
            this.pendingAfterSave = null;
            showError("No document picker is available on this device.");
        }
    }

    @Override // android.app.Activity
    protected void onActivityResult(int i, int i2, Intent intent) {
        super.onActivityResult(i, i2, intent);
        if (i == REQUEST_DOCX_COPY) { handleDocxResult(i2, intent); return; }
        if(i==REQUEST_WORKSPACE_TREE){handleWorkspaceTreeResult(i2,intent);return;}
        if (i == REQUEST_SPLASH_IMPORT) {
            if (i2 == -1 && intent != null) {
                importSplashImages(intent);
            }
            return;
        }
        if (i2 != -1 || intent == null || intent.getData() == null) {
            if (i == REQUEST_PROTECTED_COPY) this.restoreSnapshotId = null;
            if (i == REQUEST_CREATE) {
                this.pendingAfterSave = null;
                this.saveAsTabId = null;
                return;
            }
            return;
        }
        Uri data = intent.getData();
        takePermission(data, intent.getFlags());
        if (i == REQUEST_PROTECTED_COPY) {
            String snapshotId = this.restoreSnapshotId;
            this.restoreSnapshotId = null;
            if (snapshotId != null && !this.saveInProgress) restoreProtectedCopy(snapshotId, data);
            return;
        }
        if (i != REQUEST_OPEN) {
            if (i == REQUEST_CREATE) {
                int iFindTabById = findTabById(this.saveAsTabId);
                this.saveAsTabId = null;
                if (iFindTabById < 0 || this.saveInProgress) {
                    this.pendingAfterSave = null;
                    return;
                }
                switchToTab(iFindTabById, false);
                saveInternal(true, data);
                return;
            }
            return;
        }
        openDocument(data, intent.getFlags(), false);
    }

    private void restoreProtectedCopy(final String snapshotId, final Uri destination) {
        this.saveInProgress = true;
        this.savingTabId = null;
        this.saveButton.setEnabled(false);
        this.statusView.setText(R.string.saving);
        this.io.execute(() -> {
            try {
                // Revalidate the selected immutable record on disk, never trust dialog-cached bytes.
                ProtectionStore readyStore = getHistoryController().protectionStore();
                ProtectionStore.Snapshot snapshot = readyStore.snapshot(snapshotId);
                if (destination.toString().equals(snapshot.uri)) throw new IOException("Choose a new copy; the original document URI cannot be used for restore.");
                SaveCoordinator.Request request = new SaveCoordinator.Request(snapshot.documentId, destination.toString(), snapshot.title,
                        snapshot.revision, snapshot.bytes(), null, null, true);
                new SaveCoordinator(readyStore).save(request, new SaveCoordinator.Provider() {
                    @Override public byte[] read() throws IOException { return DocumentIO.read(getContentResolver(), destination).sourceBytes; }
                    @Override public void write(byte[] bytes) throws IOException { DocumentIO.write(getContentResolver(), destination, bytes); }
                });
                this.main.post(() -> {
                    this.saveInProgress = false;
                    this.saveButton.setEnabled(true);
                    updateStatus();
                    Toast.makeText(this, "Restored to a new copy. Protected versions were kept.", MODE_PREVIEW).show();
                });
            } catch (Exception failure) {
                this.main.post(() -> lambda$saveInternal$34(failure));
            }
        });
    }

    private void handleIntent(Intent intent) {
        if (intent == null) {
            return;
        }
        String action = intent.getAction();
        if ("android.intent.action.VIEW".equals(action) || "android.intent.action.EDIT".equals(action)) {
            if (intent.getData() != null) {
                takePermission(intent.getData(), intent.getFlags());
                openDocument(intent.getData(), intent.getFlags(), false, Intent.ACTION_VIEW.equals(action));
                return;
            }
            return;
        }
        if ("android.intent.action.SEND".equals(action)) {
            Uri uriStreamFrom = streamFrom(intent);
            if (uriStreamFrom != null) {
                takePermission(uriStreamFrom, intent.getFlags());
                openDocument(uriStreamFrom, intent.getFlags(), false);
                return;
            }
            CharSequence charSequenceExtra = intent.getCharSequenceExtra("android.intent.extra.TEXT");
            if (charSequenceExtra != null) {
                if (charSequenceExtra.length() > 8388608) {
                    showError("The shared text is larger than the 8 MB safety limit.");
                    return;
                }
                createUntitledTab(false);
                this.suppressChanges = true;
                this.editor.setText(charSequenceExtra);
                this.suppressChanges = false;
                this.dirty = true;
                this.displayName = "Shared text.md";
                captureActiveTab();
                scheduleRecovery();
                scheduleSession();
                updateStatus();
                scheduleHighlight();
            }
        }
    }

    private boolean isExternalIntent(Intent intent) {
        if (intent == null) {
            return false;
        }
        String action = intent.getAction();
        return "android.intent.action.VIEW".equals(action) || "android.intent.action.EDIT".equals(action) || "android.intent.action.SEND".equals(action);
    }

    private Uri streamFrom(Intent intent) {
        if (Build.VERSION.SDK_INT >= 33) {
            Uri uri = (Uri) intent.getParcelableExtra("android.intent.extra.STREAM", Uri.class);
            if (uri != null) {
                return uri;
            }
        } else {
            Uri uri2 = (Uri) intent.getParcelableExtra("android.intent.extra.STREAM");
            if (uri2 != null) {
                return uri2;
            }
        }
        ClipData clipData = intent.getClipData();
        if (clipData == null || clipData.getItemCount() <= 0) {
            return null;
        }
        return clipData.getItemAt(MODE_EDIT).getUri();
    }

    private void openDocument(final Uri uri, final int i, final boolean z) {
        openDocument(uri, i, z, false);
    }

    private void openDocument(final Uri uri, final int i, final boolean z, final boolean viewOnly) {
        SearchLocation opening=this.searchOpenCall;
        if(opening==null||opening!=this.searchLocation||!opening.awaitingOpen||uri==null||!opening.hit.uri.toString().equals(uri.toString()))cancelSearchLocation();
        else opening.load=this.loadToken;
        if (uri == null || !"content".equalsIgnoreCase(uri.getScheme())) {
            cancelSearchLocation();
            showError("For safety, Markdown Desk can only open documents supplied through Android's document system.");
            return;
        }
        int iFindTabByUri = findTabByUri(uri);
        if (iFindTabByUri >= 0) {
            switchToTab(iFindTabByUri, false);
            if (viewOnly) setMode(MODE_PREVIEW, false);
            searchOpenFinished(uri,this.loadToken);
            return;
        }
        if (this.tabs.size() >= MAX_TABS) {
            cancelSearchLocation();
            Toast.makeText(this, "Close a tab before opening another.", MODE_PREVIEW).show();
            return;
        }
        final int i2 = this.loadToken + MODE_PREVIEW;
        this.loadToken = i2;
        if(opening!=null&&opening==this.searchLocation&&opening.awaitingOpen)opening.load=i2;
        this.statusView.setText(R.string.opening);
        this.io.execute(new Runnable() { // from class: com.example.markdowndesk.MainActivity$$ExternalSyntheticLambda64
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.this.lambda$openDocument$31(uri, i2, i, z, viewOnly);
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$openDocument$31(final Uri uri, final int i, final int i2, final boolean z, final boolean viewOnly) {
        try {
            final DocumentIO.ReadResult readResult = DocumentIO.read(getContentResolver(), uri);
            final DocumentIO.Metadata metadata = DocumentIO.metadata(getContentResolver(), uri);
            final String strSha256 = EditorTools.sha256(readResult.sourceBytes);
            this.main.post(new Runnable() { // from class: com.example.markdowndesk.MainActivity$$ExternalSyntheticLambda93
                @Override // java.lang.Runnable
                public final void run() {
                    MainActivity.this.lambda$openDocument$29(i, uri, metadata, readResult, strSha256, i2, viewOnly);
                }
            });
        } catch (Exception e) {
            this.main.post(new Runnable() { // from class: com.example.markdowndesk.MainActivity$$ExternalSyntheticLambda94
                @Override // java.lang.Runnable
                public final void run() {
                    MainActivity.this.lambda$openDocument$30(i, z, uri, e);
                }
            });
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$openDocument$29(int i, Uri uri, DocumentIO.Metadata metadata, DocumentIO.ReadResult readResult, String str, int i2, boolean viewOnly) {
        if (i != this.loadToken || isFinishing()) {
            return;
        }
        captureActiveTab();
        DocumentTab documentTab = new DocumentTab();
        documentTab.uri = uri.toString();
        documentTab.displayName = metadata.displayName;
        documentTab.text = readResult.text;
        documentTab.baselineHash = str;
        documentTab.baselineModified = metadata.lastModified;
        documentTab.utf8Bom = readResult.utf8Bom;
        documentTab.readOnly = viewOnly || !(metadata.writable || (i2 & MODE_SPLIT) != 0);
        documentTab.mode = viewOnly ? MODE_PREVIEW : MODE_EDIT;
        this.tabs.add(documentTab);
        this.activeTab = this.tabs.size() - MODE_PREVIEW;
        RecentStore.touch(this, uri.toString(), documentTab.displayName);
        SearchLocation opening=this.searchLocation;
        if(opening==null||!opening.awaitingOpen||opening.load!=i||!opening.hit.uri.toString().equals(uri.toString()))opening=null;
        this.searchOpenCall=opening;
        try{restoreActiveTab(false);}catch(RuntimeException|Error failed){cancelSearchLocation();throw failed;}finally{this.searchOpenCall=null;}
        searchOpenFinished(uri,i);
        scheduleSession();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$openDocument$30(int i, boolean z, Uri uri, Exception exc) {
        if (i != this.loadToken || isFinishing()) {
            return;
        }
        searchOpenFailed(uri,i);
        updateStatus();
        if (z) {
            showRecentFailure(uri, exc);
        } else {
            showError(message(exc));
        }
    }

    private void saveDocument() {
        if (this.saveInProgress || this.activeTab < 0 || this.activeTab >= this.tabs.size()) {
            return;
        }
        if (this.currentUri == null || this.readOnly) {
            launchSaveAs();
        } else {
            saveInternal(false);
        }
    }

    private ProtectionStore getProtectionStore() throws IOException {
        if (this.protectionStore == null) {
            File trustedParent = getFilesDir().getCanonicalFile();
            this.protectionStore = new ProtectionStore(new File(trustedParent, "protected_saves"), new AndroidDurability(), 64L * 1024L * 1024L);
        }
        return this.protectionStore;
    }

    // I/O queue only. Failed initialization never publishes a writable fallback.
    private HistoryController getHistoryController() throws IOException {
        if (this.historyController == null) {
            this.historyController = HistoryController.open(getProtectionStore(), new AndroidDurability());
        }
        return this.historyController;
    }

    private void saveInternal(final boolean z) {
        // Historical force boolean is never overwrite authorization.
        saveInternal(false, this.currentUri);
    }

    private void saveInternal(final boolean newDestination, final Uri destination) {
        if (destination == null || this.saveInProgress || this.activeTab < 0 || this.activeTab >= this.tabs.size()) return;
        captureActiveTab();
        final DocumentTab tab = this.tabs.get(this.activeTab);
        final String text = tab.text;
        try {
            final SaveCoordinator.Request request = new SaveCoordinator.Request(tab.id, destination.toString(), tab.displayName,
                    ++this.capturedSaveRevision, DocumentIO.encodeStrict(text, tab.utf8Bom), tab.baselineHash, null,
                    newDestination && !destination.toString().equals(tab.uri));
            HistoryController.SaveRoute route = new HistoryController.SaveRoute(tab.id, tab.uri,
                    destination.toString(), request.revision);
            submitCapturedSave(request, destination, text, route);
        } catch (IOException invalidEdit) {
            this.pendingAfterSave = null;
            showError("The edited text could not be safely encoded. No document was written.\n\n" + message(invalidEdit));
        }
    }

    private void submitCapturedSave(final SaveCoordinator.Request request, final Uri destination, final String text,
                                    final HistoryController.SaveRoute route) {
        if (this.saveInProgress || findTabById(request.documentId) < 0) { this.pendingAfterSave = null; return; }
        this.saveInProgress = true;
        this.savingTabId = request.documentId;
        this.saveButton.setEnabled(false);
        this.statusView.setText(R.string.saving);
        this.io.execute(() -> lambda$saveInternal$35(request, destination, text, route));
    }

    public void lambda$saveInternal$35(final SaveCoordinator.Request request, final Uri uri, final String capturedText,
                                     final HistoryController.SaveRoute route) {
        try {
            HistoryController history = getHistoryController();
            SaveCoordinator.Result result = new SaveCoordinator(history.protectionStore()).save(request, new SaveCoordinator.Provider() {
                @Override public byte[] read() throws IOException { return DocumentIO.read(getContentResolver(), uri).sourceBytes; }
                @Override public void write(byte[] bytes) throws IOException { DocumentIO.write(getContentResolver(), uri, bytes); }
            });
            // Optional provenance cannot turn an actually verified provider save into failure.
            final HistoryController.IndexResult indexed = history.recordVerifiedSave(route, result.transaction.id);
            final DocumentIO.Metadata metadata = DocumentIO.metadata(getContentResolver(), uri);
            this.main.post(() -> {
                lambda$saveInternal$33(request.documentId, uri, capturedText, result.hash, metadata);
                if (!indexed.indexed && !isFinishing() && this.activeTab >= 0 && this.activeTab < this.tabs.size()
                        && request.documentId.equals(this.tabs.get(this.activeTab).id)
                        && uri.toString().equals(this.tabs.get(this.activeTab).uri)) {
                    Toast.makeText(this, "Saved. Document history metadata could not be confirmed; protected versions remain in Workspace.", Toast.LENGTH_LONG).show();
                }
            });
        } catch (SaveCoordinator.ConflictException conflict) {
            this.main.post(() -> lambda$saveInternal$32(request, uri, capturedText, conflict.observedHash, route));
        } catch (Exception failure) {
            this.main.post(() -> lambda$saveInternal$34(failure));
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* JADX INFO: renamed from: saveSucceeded, reason: merged with bridge method [inline-methods] */
    public void lambda$saveInternal$33(String str, Uri uri, String str2, String str3, DocumentIO.Metadata metadata) {
        int iFindTabById = findTabById(str);
        this.saveInProgress = false;
        this.savingTabId = null;
        this.saveButton.setEnabled(true);
        if (iFindTabById < 0) {
            this.pendingAfterSave = null;
            updateStatus();
            return;
        }
        if (iFindTabById == this.activeTab) {
            captureActiveTab();
        }
        DocumentTab documentTab = this.tabs.get(iFindTabById);
        if (iFindTabById == this.activeTab && !java.util.Objects.equals(documentTab.uri, uri.toString())) invalidateHistory();
        documentTab.uri = uri.toString();
        documentTab.baselineHash = str3;
        documentTab.baselineModified = metadata.lastModified;
        documentTab.displayName = metadata.displayName;
        documentTab.readOnly = false;
        documentTab.dirty = !documentTab.text.equals(str2);
        RecentStore.touch(this, uri.toString(), documentTab.displayName);
        if (iFindTabById == this.activeTab) {
            this.currentUri = uri;
            this.baselineHash = str3;
            this.baselineModified = metadata.lastModified;
            this.displayName = metadata.displayName;
            this.readOnly = false;
            this.dirty = documentTab.dirty;
            if (this.dirty) {
                scheduleRecovery();
                Toast.makeText(this, "Saved that version; newer edits remain unsaved.", MODE_PREVIEW).show();
            } else {
                clearRecoveryAsync();
                Toast.makeText(this, "Saved", MODE_EDIT).show();
            }
            updateStatus();
        }
        scheduleSession();
        Runnable runnable = this.pendingAfterSave;
        this.pendingAfterSave = null;
        updateTabsUi();
        if (runnable == null || iFindTabById != this.activeTab || documentTab.dirty) {
            return;
        }
        runnable.run();
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* JADX INFO: renamed from: saveFailed, reason: merged with bridge method [inline-methods] */
    public void lambda$saveInternal$34(Exception exc) {
        this.saveInProgress = false;
        this.savingTabId = null;
        this.saveButton.setEnabled(true);
        this.pendingAfterSave = null;
        updateStatus();
        boolean uncertain = exc instanceof SaveCoordinator.SaveFailure && ((SaveCoordinator.SaveFailure) exc).writeMayHaveOccurred;
        showError((uncertain ? "Save outcome is uncertain. The provider document may be incomplete. Protected versions are available in Workspace → Protected saves."
                : "Save stopped. Check Workspace → Protected saves for any retained versions; resolve storage or access errors before retrying.") + "\n\n" + message(exc));
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* JADX INFO: renamed from: showConflict, reason: merged with bridge method [inline-methods] */
    public void lambda$saveInternal$32(final SaveCoordinator.Request request, final Uri destination, final String capturedText, final String observedHash,
                                     final HistoryController.SaveRoute route) {
        final String tabId = request.documentId;
        this.saveInProgress = false;
        this.savingTabId = null;
        this.saveButton.setEnabled(true);
        updateStatus();
        if (findTabById(tabId) < 0) { this.pendingAfterSave = null; return; }
        new AlertDialog.Builder(this).setTitle("File changed elsewhere")
                .setMessage("The source changed or its original version is unknown. Both versions will be protected before an approved overwrite.")
                .setPositiveButton("Overwrite", (dialog, which) -> resumeConflictSave(request, destination, capturedText, observedHash, route))
                .setNegativeButton("Save as", (dialog, which) -> {
                    int index = findTabById(tabId);
                    if (index < 0 || this.saveInProgress) { this.pendingAfterSave = null; return; }
                    switchToTab(index, false);
                    launchSaveAs();
                })
                .setNeutralButton("Cancel", (dialog, which) -> this.pendingAfterSave = null).show();
    }

    private void resumeConflictSave(SaveCoordinator.Request request, Uri destination, String capturedText, String observedHash,
                                    HistoryController.SaveRoute route) {
        if (findTabById(request.documentId) < 0 || this.saveInProgress) { this.pendingAfterSave = null; return; }
        // Consent authorizes only the originally captured identity, revision and bytes.
        submitCapturedSave(request.withConsent(observedHash), destination, capturedText, route);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$showConflict$38(DialogInterface dialogInterface, int i) {
        this.pendingAfterSave = null;
    }

    private void confirmUnsaved(final Runnable runnable) {
        if (this.dirty) {
            new AlertDialog.Builder(this).setTitle("Unsaved changes").setMessage("Save your changes before continuing?").setPositiveButton("Save", new DialogInterface.OnClickListener() { // from class: com.example.markdowndesk.MainActivity$$ExternalSyntheticLambda78
                @Override // android.content.DialogInterface.OnClickListener
                public final void onClick(DialogInterface dialogInterface, int i) {
                    MainActivity.this.lambda$confirmUnsaved$39(runnable, dialogInterface, i);
                }
            }).setNegativeButton("Discard", new DialogInterface.OnClickListener() { // from class: com.example.markdowndesk.MainActivity$$ExternalSyntheticLambda79
                @Override // android.content.DialogInterface.OnClickListener
                public final void onClick(DialogInterface dialogInterface, int i) {
                    MainActivity.this.lambda$confirmUnsaved$40(runnable, dialogInterface, i);
                }
            }).setNeutralButton("Cancel", (DialogInterface.OnClickListener) null).show();
        } else {
            runnable.run();
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$confirmUnsaved$39(Runnable runnable, DialogInterface dialogInterface, int i) {
        this.pendingAfterSave = runnable;
        saveDocument();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$confirmUnsaved$40(Runnable runnable, DialogInterface dialogInterface, int i) {
        this.dirty = false;
        if (this.activeTab >= 0 && this.activeTab < this.tabs.size()) {
            this.tabs.get(this.activeTab).dirty = false;
        }
        this.pendingAfterSave = null;
        clearRecoveryAsync();
        runnable.run();
    }

    private void restoreSessionOrRecovery(final Intent intent) {
        this.io.execute(new Runnable() { // from class: com.example.markdowndesk.MainActivity$$ExternalSyntheticLambda3
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.this.lambda$restoreSessionOrRecovery$42(intent);
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$restoreSessionOrRecovery$42(final Intent intent) {
        RecoveryStore.Draft draft = null;
        SessionStore.State state = null;
        Exception e = null;
        // A malformed session must not hide an independently recoverable draft.
        try {
            state = SessionStore.read(this);
        } catch (Exception sessionError) {
            e = sessionError;
        }
        try {
            draft = RecoveryStore.read(this);
            protectLegacyRecovery(draft);
        } catch (Exception recoveryError) {
            this.legacyRecoveryProtectionFailed = true;
            if (e == null) e = recoveryError;
            else e.addSuppressed(recoveryError);
        }
        if (state != null && draft != null && draft.timestamp > state.timestamp) {
            try {
                mergeNewerRecovery(state, draft);
            } catch (Exception mergeError) {
                if (e == null) e = mergeError;
                else e.addSuppressed(mergeError);
            }
        }
        final Exception exc = e;
        final SessionStore.State state2 = state;
        final RecoveryStore.Draft draft2 = draft;
        this.main.post(new Runnable() { // from class: com.example.markdowndesk.MainActivity$$ExternalSyntheticLambda82
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.this.lambda$restoreSessionOrRecovery$41(state2, intent, exc, draft2);
            }
        });
    }

    private void protectLegacyRecovery(RecoveryStore.Draft draft) throws IOException {
        if (draft != null) {
            byte[] bytes = DocumentIO.encodeStrict(draft.text, draft.utf8Bom);
            String hash = EditorTools.sha256(bytes);
            long revision = Math.max(0L, draft.timestamp);
            boolean found = false;
            for (ProtectionStore.Snapshot snapshot : getProtectionStore().snapshots()) {
                if ("legacy-recovery".equals(snapshot.documentId) && "attempted".equals(snapshot.role)
                        && java.util.Objects.equals(draft.uri, snapshot.uri) && java.util.Objects.equals(draft.displayName, snapshot.title)
                        && snapshot.revision == revision && hash.equals(snapshot.hash)) { found = true; break; }
            }
            if (!found) getHistoryController().protectionStore().putSnapshot("legacy-recovery", draft.uri, draft.displayName, revision, bytes, "attempted");
        }
        this.legacyRecoveryProtected = true;
        this.legacyRecoveryProtectionFailed = false;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$restoreSessionOrRecovery$41(SessionStore.State state, Intent intent, Exception exc, RecoveryStore.Draft draft) {
        if (exc != null) showError("Previous editor state or draft protection could not be read: " + message(exc)
                + "\nKeep the recovery draft. Resolve storage or access errors before retrying; Workspace → Protected saves lists committed versions.");
        if (state != null && !state.tabs.isEmpty()) {
            this.tabs.clear();
            this.tabs.addAll(state.tabs);
            this.activeTab = state.activeIndex;
            pruneRedundantUntitledTabs();
            restoreActiveTab(false);
            scheduleSession();
            if (draft != null && !recoveryRepresented(state, draft)) showPendingProtectedRecovery(draft);
            if (isExternalIntent(intent)) {
                handleIntent(intent);
                return;
            }
            return;
        }
        if (draft == null) {
            if (!isExternalIntent(intent)) {
                createUntitledTab(false);
                return;
            } else {
                handleIntent(intent);
                return;
            }
        }
        showRecovery(draft, intent);
    }

    private static boolean recoveryRepresented(SessionStore.State state, RecoveryStore.Draft draft) {
        for (DocumentTab tab : state.tabs) {
            if (java.util.Objects.equals(tab.uri, draft.uri) && java.util.Objects.equals(tab.displayName, draft.displayName)
                    && java.util.Objects.equals(tab.text, draft.text) && tab.utf8Bom == draft.utf8Bom) return true;
        }
        return false;
    }

    private void showPendingProtectedRecovery(RecoveryStore.Draft draft) {
        new AlertDialog.Builder(this).setTitle("Recovery version available")
                .setMessage("Unsaved changes from " + draft.displayName + " are available separately from your open tabs. Use Protected saves to restore to a new copy."
                        + (this.legacyRecoveryProtectionFailed ? " Draft protection failed; the existing recovery draft will be kept until the storage or access error is resolved." : ""))
                .setPositiveButton("Protected saves", (dialog, which) -> showProtectedSaves())
                .setNegativeButton("Keep for later", null).show();
    }

    private void pruneRedundantUntitledTabs() {
        if (this.tabs.size() <= 1) return;
        for (int i = this.tabs.size() - 1; i >= 0; i--) {
            DocumentTab tab = this.tabs.get(i);
            boolean pristine = tab.uri == null && !tab.dirty && (tab.text == null || tab.text.isEmpty())
                    && (tab.displayName == null || "Untitled.md".equals(tab.displayName));
            if (pristine && this.tabs.size() > 1) {
                this.tabs.remove(i);
                if (i < this.activeTab) this.activeTab--;
                else if (i == this.activeTab) this.activeTab = Math.min(this.activeTab, this.tabs.size() - 1);
            }
        }
        if (this.activeTab < 0 && !this.tabs.isEmpty()) this.activeTab = 0;
    }

    private static void mergeNewerRecovery(SessionStore.State state, RecoveryStore.Draft draft) {
        int size;
        DocumentTab documentTab;
        if (draft.uri != null) {
            size = MODE_EDIT;
            while (true) {
                if (size >= state.tabs.size()) {
                    size = -1;
                    break;
                } else if (draft.uri.equals(state.tabs.get(size).uri)) {
                    break;
                } else {
                    size += MODE_PREVIEW;
                }
            }
        } else {
            size = -1;
        }
        if (size < 0 && state.activeIndex >= 0 && state.activeIndex < state.tabs.size()) {
            DocumentTab documentTab2 = state.tabs.get(state.activeIndex);
            if (draft.uri == null && documentTab2.uri == null && draft.displayName.equals(documentTab2.displayName)) {
                size = state.activeIndex;
            }
        }
        if (size >= 0) {
            documentTab = state.tabs.get(size);
        } else {
            if (state.tabs.size() >= MAX_TABS) {
                return;
            }
            documentTab = new DocumentTab();
            state.tabs.add(documentTab);
            size = state.tabs.size() - MODE_PREVIEW;
        }
        documentTab.text = draft.text;
        documentTab.displayName = draft.displayName;
        documentTab.uri = draft.uri;
        documentTab.utf8Bom = draft.utf8Bom;
        documentTab.baselineHash = draft.baselineHash;
        documentTab.baselineModified = draft.baselineModified;
        documentTab.readOnly = draft.readOnly;
        documentTab.dirty = true;
        documentTab.selectionStart = draft.selectionStart;
        documentTab.selectionEnd = draft.selectionEnd;
        documentTab.mode = MODE_EDIT;
        state.activeIndex = size;
        state.timestamp = draft.timestamp;
    }

    private void showRecovery(final RecoveryStore.Draft draft, final Intent intent) {
        new AlertDialog.Builder(this).setTitle("Recover unsaved draft?").setMessage("Markdown Desk preserved unsaved changes from " + draft.displayName + ".").setPositiveButton("Recover", new DialogInterface.OnClickListener() { // from class: com.example.markdowndesk.MainActivity$$ExternalSyntheticLambda4
            @Override // android.content.DialogInterface.OnClickListener
            public final void onClick(DialogInterface dialogInterface, int i) {
                MainActivity.this.lambda$showRecovery$43(draft, intent, dialogInterface, i);
            }
        }).setNegativeButton("Discard", new DialogInterface.OnClickListener() { // from class: com.example.markdowndesk.MainActivity$$ExternalSyntheticLambda5
            @Override // android.content.DialogInterface.OnClickListener
            public final void onClick(DialogInterface dialogInterface, int i) {
                MainActivity.this.lambda$showRecovery$44(intent, dialogInterface, i);
            }
        }).setCancelable(false).show();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$showRecovery$43(RecoveryStore.Draft draft, Intent intent, DialogInterface dialogInterface, int i) {
        recover(draft, intent);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$showRecovery$44(Intent intent, DialogInterface dialogInterface, int i) {
        clearRecoveryAsync();
        if (isExternalIntent(intent)) {
            handleIntent(intent);
        } else {
            createUntitledTab(false);
        }
    }

    private void recover(RecoveryStore.Draft draft, Intent intent) {
        DocumentTab documentTab = new DocumentTab();
        documentTab.text = draft.text;
        documentTab.displayName = draft.displayName;
        documentTab.uri = draft.uri;
        documentTab.utf8Bom = draft.utf8Bom;
        documentTab.baselineHash = draft.baselineHash;
        documentTab.baselineModified = draft.baselineModified;
        documentTab.readOnly = draft.readOnly;
        documentTab.dirty = true;
        documentTab.selectionStart = draft.selectionStart;
        documentTab.selectionEnd = draft.selectionEnd;
        documentTab.mode = MODE_EDIT;
        this.tabs.add(documentTab);
        this.activeTab = this.tabs.size() - MODE_PREVIEW;
        restoreActiveTab(false);
        if (this.currentUri != null) {
            this.readOnly = !DocumentIO.metadata(getContentResolver(), this.currentUri).writable;
        }
        captureActiveTab();
        scheduleSession();
        if (isExternalIntent(intent)) {
            handleIntent(intent);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void scheduleRecovery() {
        if (this.dirty) {
            if (this.recoveryRunnable != null) {
                this.main.removeCallbacks(this.recoveryRunnable);
            }
            this.recoveryRunnable = new Runnable() { // from class: com.example.markdowndesk.MainActivity$$ExternalSyntheticLambda18
                @Override // java.lang.Runnable
                public final void run() {
                    MainActivity.this.writeRecoveryNow();
                }
            };
            this.main.postDelayed(this.recoveryRunnable, 700L);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void writeRecoveryNow() {
        if (!this.dirty || this.editor == null) {
            return;
        }
        if (this.recoveryRunnable != null) {
            this.main.removeCallbacks(this.recoveryRunnable);
        }
        final RecoveryStore.Draft draft = new RecoveryStore.Draft();
        draft.text = this.editor.getText().toString();
        draft.displayName = this.displayName;
        draft.uri = this.currentUri == null ? null : this.currentUri.toString();
        draft.utf8Bom = this.utf8Bom;
        draft.baselineHash = this.baselineHash;
        draft.baselineModified = this.baselineModified;
        draft.readOnly = this.readOnly;
        draft.selectionStart = this.editor.getSelectionStart();
        draft.selectionEnd = this.editor.getSelectionEnd();
        draft.timestamp = System.currentTimeMillis();
        this.io.execute(new Runnable() { // from class: com.example.markdowndesk.MainActivity$$ExternalSyntheticLambda47
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.this.lambda$writeRecoveryNow$46(draft);
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$writeRecoveryNow$45() {
        showError("Could not update recovery. The previous recovery draft is being kept. Resolve storage or access errors before retrying; see Workspace → Protected saves.");
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$writeRecoveryNow$46(RecoveryStore.Draft draft) {
        try {
            if (!this.legacyRecoveryProtected) protectLegacyRecovery(RecoveryStore.read(this));
            RecoveryStore.write(this, draft);
        } catch (IOException e) {
            this.main.post(new Runnable() { // from class: com.example.markdowndesk.MainActivity$$ExternalSyntheticLambda91
                @Override // java.lang.Runnable
                public final void run() {
                    MainActivity.this.lambda$writeRecoveryNow$45();
                }
            });
        }
    }

    private void clearRecoveryAsync() {
        if (this.recoveryRunnable != null) {
            this.main.removeCallbacks(this.recoveryRunnable);
        }
        this.io.execute(new Runnable() { // from class: com.example.markdowndesk.MainActivity$$ExternalSyntheticLambda62
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.this.lambda$clearRecoveryAsync$47();
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$clearRecoveryAsync$47() {
        try {
            if (!this.legacyRecoveryProtected) protectLegacyRecovery(RecoveryStore.read(this));
            RecoveryStore.clear(this);
        } catch (IOException failure) {
            this.main.post(() -> lambda$writeRecoveryNow$45());
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void scheduleSession() {
        if (this.sessionRunnable != null) {
            this.main.removeCallbacks(this.sessionRunnable);
        }
        this.sessionRunnable = new Runnable() { // from class: com.example.markdowndesk.MainActivity$$ExternalSyntheticLambda69
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.this.writeSessionNow();
            }
        };
        this.main.postDelayed(this.sessionRunnable, 900L);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void writeSessionNow() {
        if (this.editor == null) {
            return;
        }
        if (this.sessionRunnable != null) {
            this.main.removeCallbacks(this.sessionRunnable);
        }
        captureActiveTab();
        final ArrayList arrayList = new ArrayList();
        Iterator<DocumentTab> it = this.tabs.iterator();
        while (it.hasNext()) {
            arrayList.add(it.next().copy());
        }
        final int i = this.activeTab;
        final long jCurrentTimeMillis = System.currentTimeMillis();
        this.io.execute(new Runnable() { // from class: com.example.markdowndesk.MainActivity$$ExternalSyntheticLambda49
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.this.lambda$writeSessionNow$49(arrayList, i, jCurrentTimeMillis);
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$writeSessionNow$49(ArrayList arrayList, int i, long j) {
        try {
            if (arrayList.isEmpty()) {
                SessionStore.clear(this);
            } else {
                SessionStore.write(this, arrayList, i, j);
            }
        } catch (IOException e) {
            this.main.post(new Runnable() { // from class: com.example.markdowndesk.MainActivity$$ExternalSyntheticLambda88
                @Override // java.lang.Runnable
                public final void run() {
                    MainActivity.this.lambda$writeSessionNow$48();
                }
            });
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$writeSessionNow$48() {
        Toast.makeText(this, "Could not persist open tabs", MODE_PREVIEW).show();
    }

    private void showRecents() {
        final List<RecentStore.Item> list = RecentStore.read(this);
        boolean zIsEmpty = list.isEmpty();
        if (zIsEmpty) {
            Toast.makeText(this, "No recent documents", MODE_EDIT).show();
            return;
        }
        String[] strArr = new String[list.size()];
        for (int i = MODE_EDIT; i < list.size(); i += MODE_PREVIEW) {
            strArr[i] = list.get(i).name;
        }
        new AlertDialog.Builder(this).setTitle("Recent documents").setItems(strArr, new DialogInterface.OnClickListener() { // from class: com.example.markdowndesk.MainActivity$$ExternalSyntheticLambda61
            @Override // android.content.DialogInterface.OnClickListener
            public final void onClick(DialogInterface dialogInterface, int i2) {
                MainActivity.this.lambda$showRecents$50(list, dialogInterface, i2);
            }
        }).setNegativeButton("Cancel", (DialogInterface.OnClickListener) null).show();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$showRecents$50(List list, DialogInterface dialogInterface, int i) {
        openDocument(Uri.parse(((RecentStore.Item) list.get(i)).uri), 3, true);
    }

    private volatile long protectedSavesRequest;
    private ProtectedSavesSession protectedSavesSession;

    private static final class ProtectedSavesSession {
        final long request;
        List<ProtectionStore.Snapshot> versions;
        AlertDialog dialog;
        Object token;
        int start;
        ProtectedSavesSession(long request, List<ProtectionStore.Snapshot> versions) {
            this.request = request; this.versions = versions;
        }
    }

    private void invalidateProtectedSaves() {
        ++this.protectedSavesRequest;
        ProtectedSavesSession session = this.protectedSavesSession;
        this.protectedSavesSession = null;
        if (session == null) return;
        AlertDialog dialog = session.dialog;
        session.versions = null; session.dialog = null; session.token = null;
        if (dialog != null) dialog.dismiss();
    }

    private boolean protectedSavesCurrent(ProtectedSavesSession session, AlertDialog dialog, Object token) {
        return session == this.protectedSavesSession && session.request == this.protectedSavesRequest
                && !isFinishing() && !isDestroyed() && session.versions != null
                && session.dialog == dialog && session.token == token && dialog != null && dialog.isShowing();
    }

    private void showProtectedSaves() {
        invalidateProtectedSaves();
        if (isFinishing() || isDestroyed()) return;
        final long request = this.protectedSavesRequest;
        this.io.execute(() -> {
            if (request != this.protectedSavesRequest) return;
            try {
                final List<ProtectionStore.Snapshot> versions = getProtectionStore().snapshots();
                if (request != this.protectedSavesRequest) return;
                versions.sort((left, right) -> Long.compare(right.timestamp, left.timestamp));
                this.main.post(() -> {
                    if (request != this.protectedSavesRequest || isFinishing() || isDestroyed()) return;
                    ProtectedSavesSession session = new ProtectedSavesSession(request, versions);
                    this.protectedSavesSession = session;
                    showProtectedSavesPage(session, 0);
                });
            } catch (Exception failure) {
                this.main.post(() -> {
                    if (request == this.protectedSavesRequest && !isFinishing() && !isDestroyed())
                        showError("Protected saves could not be read. Keep the existing recovery draft and resolve storage or access errors.\n\n" + message(failure));
                });
            }
        });
    }

    private void presentProtectedSavesDialog(ProtectedSavesSession session, AlertDialog dialog, Object token) {
        AlertDialog previous = session.dialog;
        session.dialog = dialog; session.token = token;
        if (previous != null) previous.dismiss();
        dialog.setOnDismissListener(ignored -> {
            if (session == this.protectedSavesSession && session.dialog == dialog && session.token == token)
                invalidateProtectedSaves();
        });
        dialog.setOnShowListener(ignored -> workspaceDialogShown(dialog));
        dialog.show();
    }

    private void showProtectedSavesPage(ProtectedSavesSession session, int requestedStart) {
        if (session != this.protectedSavesSession || session.request != this.protectedSavesRequest
                || session.versions == null || isFinishing() || isDestroyed()) return;
        int size = session.versions.size();
        final int start = size == 0 ? 0 : Math.min(Math.max(0, requestedStart), ((size - 1) / 100) * 100);
        session.start = start;
        int end = start + Math.min(100, size - start);
        String[] labels = new String[end - start];
        SimpleDateFormat dates = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        for (int index = start; index < end; index++) {
            ProtectionStore.Snapshot snapshot = session.versions.get(index);
            labels[index - start] = (snapshot.title == null ? "Untitled.md" : snapshot.title) + " · "
                    + ("original".equals(snapshot.role) ? "Original" : "Edited version") + "\n" + dates.format(new Date(snapshot.timestamp));
        }
        final Object token = new Object();
        final AlertDialog[] owned = new AlertDialog[1];
        AlertDialog.Builder builder = historyDialogBuilder("Protected saves" + (size == 0 ? "" : " · " + (start + 1) + "–" + end + " of " + size))
                .setNegativeButton("Close", null);
        if (size == 0) builder.setMessage("No protected saves are available yet.");
        else builder.setAdapter(workspaceRootRows(labels), (ignored, index) -> {
            AlertDialog dialog = owned[0];
            if (!protectedSavesCurrent(session, dialog, token) || index < 0 || index >= labels.length) return;
            ProtectionStore.Snapshot snapshot = session.versions.get(start + index);
            showProtectedSaveActions(session, start, snapshot.id, snapshot.title);
        });
        if (start > 0) builder.setNeutralButton("Previous", (ignored, which) -> {
            if (protectedSavesCurrent(session, owned[0], token)) showProtectedSavesPage(session, start - 100);
        });
        if (end < size) builder.setPositiveButton("Next", (ignored, which) -> {
            if (protectedSavesCurrent(session, owned[0], token)) showProtectedSavesPage(session, start + 100);
        });
        owned[0] = builder.create();
        presentProtectedSavesDialog(session, owned[0], token);
    }

    private void showProtectedSaveActions(ProtectedSavesSession session, int start, String id, String title) {
        final Object token = new Object();
        final AlertDialog[] owned = new AlertDialog[1];
        owned[0] = historyDialogBuilder("Restore protected version?")
                .setMessage("Restore " + (title == null ? "this version" : title) + " to a new copy. The original and protected versions will be kept.")
                .setPositiveButton("Restore to new copy", (ignored, which) -> {
                    if (!protectedSavesCurrent(session, owned[0], token)) return;
                    invalidateProtectedSaves();
                    launchProtectedCopy(id, title);
                })
                .setNegativeButton("Back", (ignored, which) -> {
                    if (protectedSavesCurrent(session, owned[0], token)) showProtectedSavesPage(session, start);
                }).create();
        presentProtectedSavesDialog(session, owned[0], token);
    }

    private void invalidateHistory() {
        ++this.historyRequest;
        if (this.historyDialog != null) {
            this.historyDialog.dismiss();
            this.historyDialog = null;
        }
    }

    private boolean historyCurrent(long request, String tabId, String exactUri) {
        return request == this.historyRequest && !isFinishing() && !isDestroyed()
                && this.activeTab >= 0 && this.activeTab < this.tabs.size()
                && tabId.equals(this.tabs.get(this.activeTab).id)
                && java.util.Objects.equals(exactUri, this.tabs.get(this.activeTab).uri);
    }

    private void presentHistoryDialog(AlertDialog dialog, long request) {
        if (this.historyDialog != null) this.historyDialog.dismiss();
        this.historyDialog = dialog;
        dialog.setOnCancelListener(ignored -> { if (request == this.historyRequest) invalidateHistory(); });
        dialog.setOnShowListener(ignored -> {
            styleDialog(dialog);
            TextView message = dialog.findViewById(android.R.id.message);
            if (message != null) message.setTextColor(this.palette.text);
        });
        dialog.show();
    }

    private AlertDialog.Builder historyDialogBuilder(String title) {
        TextView heading = new TextView(this);
        heading.setText(title); heading.setTextColor(this.palette.text); heading.setTextSize(20);
        heading.setPadding(dp(24), dp(20), dp(24), dp(12));
        if (Build.VERSION.SDK_INT >= 28) heading.setAccessibilityHeading(true);
        return new AlertDialog.Builder(this).setCustomTitle(heading);
    }

    private String historyVersionStatus(HistoryIndex.Status status) {
        switch (status) {
            case INDEXED_VERIFIED: return "Verified save";
            case UNINDEXED_VERIFIED: return "Verified save · history indexing unconfirmed";
            case PENDING: return "Save not confirmed";
            default: return "Protected snapshot · no completed save";
        }
    }

    private void showDocumentHistory() {
        if (this.activeTab < 0 || this.activeTab >= this.tabs.size() || isFinishing() || isDestroyed()) return;
        captureActiveTab();
        final DocumentTab tab = this.tabs.get(this.activeTab);
        final String tabId = tab.id, exactUri = tab.uri;
        invalidateHistory();
        final long request = this.historyRequest;
        this.io.execute(() -> {
            try {
                HistoryController history = getHistoryController();
                final String historyId = history.resolveDocument(exactUri, tabId);
                final List<HistoryIndex.Version> versions = history.list(historyId);
                this.main.post(() -> {
                    if (historyCurrent(request, tabId, exactUri))
                        showHistoryPage(request, tabId, exactUri, historyId, versions, 0, null);
                });
            } catch (Exception failure) {
                this.main.post(() -> {
                    if (historyCurrent(request, tabId, exactUri)) showError("Document history could not be read. Protected saves and recovery remain available.\n\n" + message(failure));
                });
            }
        });
    }

    private void showHistoryPage(long request, String tabId, String exactUri, String historyId,
                                 List<HistoryIndex.Version> versions, int start, String compareLeft) {
        if (!historyCurrent(request, tabId, exactUri)) return;
        final int end = Math.min(versions.size(), start + 100);
        final boolean previous = start > 0, next = end < versions.size();
        final int offset = previous ? 1 : 0;
        String[] labels = new String[end - start + offset + (next ? 1 : 0)];
        if (previous) labels[0] = "Previous 100 versions";
        SimpleDateFormat dates = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        for (int i = start; i < end; i++) {
            HistoryIndex.Version version = versions.get(i);
            StringBuilder label = new StringBuilder();
            for (int n = 0; n < Math.min(3, version.names.size()); n++) {
                HistoryCatalog.Checkpoint name = version.names.get(n);
                if (label.length() > 0) label.append(" · ");
                label.append(name.label);
            }
            if (version.names.size() > 3) label.append(" · ").append(version.names.size() - 3).append(" more names");
            if (label.length() == 0) label.append("original".equals(version.sourceKind) ? "Original" : "Edited version");
            labels[offset + i - start] = label + "\n" + dates.format(new Date(version.timestamp))
                    + " · " + version.byteCount + " bytes · "
                    + historyVersionStatus(version.status);
        }
        if (next) labels[labels.length - 1] = "Next 100 versions";
        android.widget.ArrayAdapter<String> rows = new android.widget.ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, labels) {
            @Override public View getView(int position, View recycled, ViewGroup parent) {
                TextView row = (TextView) super.getView(position, recycled, parent);
                row.setTextColor(MainActivity.this.palette.text);
                return row;
            }
        };
        AlertDialog dialog = historyDialogBuilder((compareLeft == null ? "Document history" : "Choose a version to compare") + " (" + versions.size() + ")")
                .setAdapter(rows, (ignored, position) -> {
                    if (!historyCurrent(request, tabId, exactUri)) return;
                    if (previous && position == 0) { showHistoryPage(request, tabId, exactUri, historyId, versions, Math.max(0, start - 100), compareLeft); return; }
                    if (next && position == labels.length - 1) { showHistoryPage(request, tabId, exactUri, historyId, versions, end, compareLeft); return; }
                    HistoryIndex.Version version = versions.get(start + position - offset);
                    if (compareLeft != null) {
                        compareHistory(request, tabId, exactUri, historyId, compareLeft, version.snapshotId);
                    } else {
                        AlertDialog actions = historyDialogBuilder("Protected document version")
                                .setMessage("Restore to a new copy keeps the original and all protected versions.")
                                .setPositiveButton("Restore to new copy", (confirm, which) -> restoreHistoryVersion(request, tabId, exactUri, historyId, version.snapshotId))
                                .setNeutralButton("Compare", (confirm, which) -> showHistoryPage(request, tabId, exactUri, historyId, versions, 0, version.snapshotId))
                                .setNegativeButton("Back", (confirm, which) -> showHistoryPage(request, tabId, exactUri, historyId, versions, start, null)).create();
                        presentHistoryDialog(actions, request);
                    }
                })
                .setPositiveButton("Name current revision", (ignored, which) -> nameHistoryRevision(request, tabId, exactUri, historyId))
                .setNegativeButton("Close", (ignored, which) -> { if (request == this.historyRequest) invalidateHistory(); }).create();
        presentHistoryDialog(dialog, request);
    }

    private void nameHistoryRevision(long request, String tabId, String exactUri, String historyId) {
        if (!historyCurrent(request, tabId, exactUri)) return;
        final EditText label = new EditText(this);
        label.setSingleLine(true);
        label.setHint("Checkpoint name (up to 256 UTF-8 bytes)");
        label.setTextColor(this.palette.text);
        label.setHintTextColor(this.palette.muted);
        AlertDialog dialog = historyDialogBuilder("Name current revision").setView(label)
                .setPositiveButton("Protect revision", (ignored, which) -> {
                    if (!historyCurrent(request, tabId, exactUri)) return;
                    captureActiveTab();
                    DocumentTab tab = this.tabs.get(this.activeTab);
                    try {
                        final HistoryController.CapturedRevision capture = new HistoryController.CapturedRevision(tabId, historyId, exactUri,
                                tab.displayName, ++this.capturedSaveRevision, DocumentIO.encodeStrict(tab.text, tab.utf8Bom));
                        final String name = label.getText().toString();
                        this.io.execute(() -> {
                            try {
                                HistoryController.NameResult result = getHistoryController().nameCapturedRevision(capture, name);
                                this.main.post(() -> {
                                    if (!historyCurrent(request, tabId, exactUri)) return;
                                    if (!result.named()) showError(result.diagnostic);
                                    else showDocumentHistory();
                                });
                            } catch (Exception failure) {
                                this.main.post(() -> { if (historyCurrent(request, tabId, exactUri)) showError("Revision could not be named. Existing protected versions were kept.\n\n" + message(failure)); });
                            }
                        });
                    } catch (IOException invalid) { showError("The revision could not be safely captured.\n\n" + message(invalid)); }
                }).setNegativeButton("Cancel", (ignored, which) -> { if (historyCurrent(request, tabId, exactUri)) showDocumentHistory(); }).create();
        presentHistoryDialog(dialog, request);
    }

    private void restoreHistoryVersion(long request, String tabId, String exactUri, String historyId, String snapshotId) {
        if (!historyCurrent(request, tabId, exactUri)) return;
        this.io.execute(() -> {
            try {
                boolean member = false;
                for (HistoryIndex.Version version : getHistoryController().list(historyId)) if (snapshotId.equals(version.snapshotId)) member = true;
                if (!member) throw new IOException("The selected version is no longer in this document history");
                final ProtectionStore.Snapshot snapshot = getProtectionStore().snapshot(snapshotId);
                this.main.post(() -> { if (historyCurrent(request, tabId, exactUri)) launchProtectedCopy(snapshotId, snapshot.title); });
            } catch (Exception failure) {
                this.main.post(() -> { if (historyCurrent(request, tabId, exactUri)) showError("Version could not be selected.\n\n" + message(failure)); });
            }
        });
    }

    private void compareHistory(long request, String tabId, String exactUri, String historyId, String leftId, String rightId) {
        if (!historyCurrent(request, tabId, exactUri)) return;
        this.io.execute(() -> {
            try {
                final HistoryDiff.Preview preview = getHistoryController().preview(historyId, leftId, rightId);
                this.main.post(() -> {
                    if (!historyCurrent(request, tabId, exactUri)) return;
                    StringBuilder text = new StringBuilder("Left: ").append(preview.leftByteCount).append(" bytes, BOM ").append(preview.leftBom)
                            .append("\nRight: ").append(preview.rightByteCount).append(" bytes, BOM ").append(preview.rightBom).append('\n');
                    if (!preview.complete) text.append(preview.reason).append('\n');
                    for (HistoryDiff.DisplayLine line : preview.lines) text.append(line.display()).append('\n');
                    TextView content = new TextView(this);
                    content.setText(text); content.setTextColor(this.palette.text); content.setTextSize(14);
                    content.setTypeface(Typeface.MONOSPACE); content.setTextIsSelectable(true); content.setPadding(dp(16), dp(12), dp(16), dp(12));
                    ScrollView scroll = new ScrollView(this); scroll.addView(content);
                    AlertDialog dialog = historyDialogBuilder(preview.complete ? "Exact version comparison" : "Incomplete version comparison")
                            .setView(scroll).setPositiveButton("Back to history", (ignored, which) -> { if (historyCurrent(request, tabId, exactUri)) showDocumentHistory(); })
                            .setNegativeButton("Close", (ignored, which) -> { if (request == this.historyRequest) invalidateHistory(); }).create();
                    presentHistoryDialog(dialog, request);
                });
            } catch (Exception failure) {
                this.main.post(() -> { if (historyCurrent(request, tabId, exactUri)) showError("Versions could not be compared.\n\n" + message(failure)); });
            }
        });
    }

    private void launchProtectedCopy(String snapshotId, String title) {
        if (this.saveInProgress) { showError("Wait for the current save to finish before restoring a copy."); return; }
        this.restoreSnapshotId = snapshotId;
        Intent picker = new Intent("android.intent.action.CREATE_DOCUMENT").addCategory("android.intent.category.OPENABLE").setType("text/markdown");
        picker.putExtra("android.intent.extra.TITLE", markdownName(title == null ? "Restored.md" : title));
        picker.addFlags(67);
        try { startActivityForResult(picker, REQUEST_PROTECTED_COPY); }
        catch (ActivityNotFoundException failure) { this.restoreSnapshotId = null; showError("No document picker is available on this device."); }
    }

    private static final Object SEARCH_GRANT_GUARD = new Object();
    private static SearchGrantJob searchGrantOwner;
    private long searchGeneration;
    private SearchScreen searchScreen;
    private AlertDialog searchNotice;
    private Object searchNoticeToken;
    private SearchLocation searchLocation;
    private SearchLocation searchOpenCall;

    private static final class SearchScreen {
        final long generation; final WorkspaceRootStore.Root root;
        String literal; AlertDialog dialog; Object token; EditText input; LinearLayout rows;
        SearchGrantJob grant; WorkspaceSearchLoader.Ticket ticket; WorkspaceLiteralSearch.Result result;
        SearchScreen(long generation, WorkspaceRootStore.Root root, String literal) { this.generation=generation;this.root=root;this.literal=literal; }
    }
    private static final class SearchLocation {
        final WorkspaceLiteralSearch.FileHit hit; final WorkspaceLiteralSearch.Match match; final String literal;
        boolean awaitingOpen=true, allowRestore=true, allowContent; int load; String tabId; long revision,route; boolean bom;
        SearchHitVerifier.Ticket ticket;
        SearchLocation(WorkspaceLiteralSearch.FileHit hit, WorkspaceLiteralSearch.Match match, String literal) { this.hit=hit;this.match=match;this.literal=literal; }
    }
    /** One process-wide preflight slot; cancellation drops the only screen callback.
     * Its worker owns application metadata only, never the Activity or editor. */
    private static final class SearchGrantJob implements Runnable {
        final android.content.Context application; final Uri root;
        private java.util.function.Consumer<Boolean> callback;
        SearchGrantJob(android.content.Context application,Uri root,java.util.function.Consumer<Boolean> callback){this.application=application;this.root=root;this.callback=callback;}
        static SearchGrantJob reserve(android.content.Context app,Uri root,java.util.function.Consumer<Boolean> callback){
            synchronized(SEARCH_GRANT_GUARD){if(searchGrantOwner!=null)return null;return searchGrantOwner=new SearchGrantJob(app,root,callback);}
        }
        void cancel(){synchronized(SEARCH_GRANT_GUARD){callback=null;}}
        void release(){synchronized(SEARCH_GRANT_GUARD){callback=null;if(searchGrantOwner==this)searchGrantOwner=null;}}
        public void run(){
            boolean posted=false;
            try {
                boolean readable=false;
                try { for(WorkspaceRootStore.RootState state:new WorkspaceRootStore(application).list(application.getContentResolver()))
                    if(state.root.uri.toString().equals(root.toString())&&state.access==WorkspaceRootStore.Access.CURRENT)readable=true;
                }catch(Exception unavailable){readable=false;}
                final boolean allowed=readable;
                posted=WORKSPACE_MAIN.post(()->{
                    try {java.util.function.Consumer<Boolean> target;synchronized(SEARCH_GRANT_GUARD){target=callback;callback=null;}if(target!=null)target.accept(allowed);}
                    finally{release();}
                });
            }finally{if(!posted)release();}
        }
    }
    private void cancelSearchLocation(){
        SearchLocation location=this.searchLocation;this.searchLocation=null;
        if(location!=null){SearchHitVerifier.Ticket ticket=location.ticket;location.ticket=null;if(ticket!=null)ticket.cancel();}
    }
    private void searchContentChanged(){
        SearchLocation location=this.searchLocation;
        if(location!=null&&location==this.searchOpenCall&&location.awaitingOpen&&location.allowContent){location.allowContent=false;return;}
        cancelSearchLocation();
    }
    private void searchRestoreChanged(){
        SearchLocation location=this.searchLocation;
        if(location!=null&&location==this.searchOpenCall&&location.awaitingOpen&&location.allowRestore){location.allowRestore=false;return;}
        cancelSearchLocation();
    }
    private void beginSearchRestoreText(){
        SearchLocation location=this.searchLocation;
        if(location!=null&&location==this.searchOpenCall&&location.awaitingOpen&&!location.allowRestore)location.allowContent=true;
    }
    private void endSearchRestoreText(){if(this.searchOpenCall!=null)this.searchOpenCall.allowContent=false;}
    private void cancelWorkspaceSearch(){
        closeWorkspaceSearchNotice();
        ++this.searchGeneration;
        SearchScreen screen=this.searchScreen;this.searchScreen=null;
        cancelSearchLocation();
        if(screen==null)return;
        AlertDialog dialog=screen.dialog;SearchGrantJob grant=screen.grant;WorkspaceSearchLoader.Ticket ticket=screen.ticket;
        screen.dialog=null;screen.token=null;screen.input=null;screen.grant=null;screen.ticket=null;screen.result=null;
        if(screen.rows!=null)screen.rows.removeAllViews();screen.rows=null;
        if(grant!=null)grant.cancel();
        if(dialog!=null)dialog.dismiss();
        if(ticket!=null)ticket.cancel();
    }
    private void closeWorkspaceSearchNotice(){
        AlertDialog notice=this.searchNotice;this.searchNotice=null;this.searchNoticeToken=null;
        if(notice!=null)notice.dismiss();
    }
    private void showWorkspaceSearchNotice(String message){
        if(isFinishing()||isDestroyed())return;
        SearchScreen screen=this.searchScreen;
        if(screen!=null&&searchCurrent(screen,screen.dialog,screen.token)){
            screen.dialog.setMessage(message);workspaceDialogShown(screen.dialog);return;
        }
        final Object token=new Object();
        AlertDialog notice=historyDialogBuilder("Folder search").setMessage(message).setPositiveButton("OK",null).create();
        AlertDialog previous=this.searchNotice;this.searchNotice=notice;this.searchNoticeToken=token;
        if(previous!=null)previous.dismiss();
        notice.setOnDismissListener(ignored->{if(this.searchNotice==notice&&this.searchNoticeToken==token){this.searchNotice=null;this.searchNoticeToken=null;}});
        notice.setOnShowListener(ignored->workspaceDialogShown(notice));notice.show();
    }
    private boolean searchCurrent(SearchScreen screen,AlertDialog dialog,Object token){
        return screen==this.searchScreen&&screen.generation==this.searchGeneration&&!isFinishing()&&!isDestroyed()
                &&screen.dialog==dialog&&screen.token==token&&dialog!=null&&dialog.isShowing();
    }
    private void presentSearchDialog(SearchScreen screen,AlertDialog dialog,Object token){
        AlertDialog previous=screen.dialog;screen.dialog=dialog;screen.token=token;
        if(previous!=null)previous.dismiss();
        dialog.setOnDismissListener(ignored->{if(this.searchScreen==screen&&screen.dialog==dialog&&screen.token==token)cancelWorkspaceSearch();});
        dialog.setOnKeyListener((ignored,key,event)->{if(key!=KeyEvent.KEYCODE_BACK||!searchCurrent(screen,dialog,token))return false;if(event.getAction()==KeyEvent.ACTION_UP)openWorkspaceRoot(screen.root);return true;});
        dialog.setOnShowListener(ignored->workspaceDialogShown(dialog));dialog.show();
    }
    private void showWorkspaceSearch(WorkspaceRootStore.Root root,String literal){
        cancelWorkspaceNavigation();
        if(isFinishing()||isDestroyed())return;
        SearchScreen screen=new SearchScreen(this.searchGeneration,root,literal);this.searchScreen=screen;
        EditText input=new EditText(this);screen.input=input;
        input.setText(literal);input.setSingleLine(true);input.setTextColor(this.palette.text);input.setHintTextColor(this.palette.muted);
        input.setHint("Exact, case-sensitive text (up to 128 characters)");input.setContentDescription("Case-sensitive literal query in saved folder "+root.label);
        input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(128)});
        final Object token=new Object();final AlertDialog[] owned=new AlertDialog[1];
        owned[0]=historyDialogBuilder("Search this saved folder: "+root.label).setMessage("Search Markdown and text in this saved root and its subfolders, even when browsing a nested folder. Exact case-sensitive text; spaces are preserved. No regular expressions.")
                .setView(input).setPositiveButton("Search",null)
                .setNegativeButton("Cancel",(ignored,which)->{if(searchCurrent(screen,owned[0],token))cancelWorkspaceSearch();})
                .setNeutralButton("Back to folder",(ignored,which)->{if(searchCurrent(screen,owned[0],token))openWorkspaceRoot(root);}).create();
        presentSearchDialog(screen,owned[0],token);
        owned[0].getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(view->{if(searchCurrent(screen,owned[0],token))submitWorkspaceSearch(screen,input.getText());});
    }
    private void submitWorkspaceSearch(SearchScreen screen,CharSequence value){
        if(!searchCurrent(screen,screen.dialog,screen.token)||screen.grant!=null)return;
        if(value==null||value.length()==0||value.length()>128){showWorkspaceSearchNotice("Enter 1–128 UTF-16 units of literal text.");return;}
        for(int index=0;index<value.length();index++){
            char ch=value.charAt(index);if(Character.isHighSurrogate(ch)){if(++index>=value.length()||!Character.isLowSurrogate(value.charAt(index))){showWorkspaceSearchNotice("Search requires valid Unicode.");return;}}
            else if(Character.isLowSurrogate(ch)){showWorkspaceSearchNotice("Search requires valid Unicode.");return;}
        }
        screen.literal=value.toString();
        SearchGrantJob job=SearchGrantJob.reserve(getApplicationContext(),screen.root.uri,allowed->{
            if(!searchCurrent(screen,screen.dialog,screen.token))return;
            screen.grant=null;
            if(!allowed){WorkspaceRootStore.Root root=screen.root;cancelWorkspaceSearch();showWorkspaceRootOptions(root,true);return;}
            startWorkspaceSearch(screen);
        });
        if(job==null){showWorkspaceSearchNotice("A saved-folder access check is still active. Wait for it to finish before searching again.");return;}
        screen.grant=job;screen.dialog.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(false);
        try{this.io.execute(job);}
        catch(java.util.concurrent.RejectedExecutionException rejected){screen.grant=null;job.release();screen.dialog.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(true);showWorkspaceSearchNotice("Folder access could not be checked.");}
        catch(RuntimeException uncertain){job.cancel();cancelWorkspaceSearch();showWorkspaceSearchNotice("Folder access check could not be confirmed. Wait for any active check; if it cannot finish, restart the app.");}
        catch(Error fatal){job.cancel();cancelWorkspaceSearch();throw fatal;}
    }
    private void startWorkspaceSearch(SearchScreen screen){
        if(!searchCurrent(screen,screen.dialog,screen.token))return;
        screen.input=null;
        final Object token=new Object();final AlertDialog[] owned=new AlertDialog[1];
        owned[0]=historyDialogBuilder("Searching saved folder: "+screen.root.label).setMessage("Searching Markdown and text in this saved root and its subfolders…")
                .setNegativeButton("Cancel",(ignored,which)->{if(searchCurrent(screen,owned[0],token))cancelWorkspaceSearch();})
                .setNeutralButton("Back to folder",(ignored,which)->{if(searchCurrent(screen,owned[0],token))openWorkspaceRoot(screen.root);}).create();
        presentSearchDialog(screen,owned[0],token);
        try {
            screen.ticket=WorkspaceSearchLoader.trySearch(getApplicationContext(),screen.root.uri,screen.literal,WorkspaceLiteralSearch.Limits.defaults(),delivery->{
                if(searchCurrent(screen,owned[0],token))acceptWorkspaceSearch(screen,delivery);
            });
            if(screen.ticket==null){cancelWorkspaceSearch();showWorkspaceSearchNotice("Folder search is busy with an outstanding request or an unconfirmed close. Wait for an active request; after a failed close, restart the app. Editing and saving remain available.");}
        }catch(RuntimeException failed){cancelWorkspaceSearch();showWorkspaceSearchNotice("Folder search could not start. Editing and saving remain available.");}
    }
    private String searchReasons(java.util.Set<WorkspaceLiteralSearch.Reason> reasons){
        StringBuilder text=new StringBuilder();
        for(WorkspaceLiteralSearch.Reason reason:reasons){
            if(text.length()>0)text.append("; ");
            switch(reason){
                case DIRECTORIES:text.append("directory limit");break;
                case FILES:text.append("file limit");break;
                case ROWS:text.append("folder row limit");break;
                case DEPTH:text.append("folder depth limit");break;
                case BYTES:text.append("read-byte limit");break;
                case MATCHES:text.append("match limit");break;
                case METADATA:text.append("result-label limit");break;
                case DIRECTORY_PARTIAL:text.append("partial folder listing");break;
                case UNREADABLE:text.append("unreadable files");break;
                case ACCESS_REVOKED:text.append("folder access revoked");break;
                case CLOSE_UNCERTAIN:text.append("provider close not confirmed; restart the app before searching again");break;
                default:text.append("repeated folder skipped");break;
            }
        }
        return text.toString();
    }
    private String searchSummary(WorkspaceLiteralSearch.Result result){
        int matches=0;for(WorkspaceLiteralSearch.FileHit hit:result.hits)matches+=hit.matches.size();
        String state=result.state==WorkspaceLiteralSearch.State.COMPLETE?(matches==0?"No matches.":"Search complete.")
                :result.state==WorkspaceLiteralSearch.State.CANCELLED?"Search cancelled; this is not a complete search."
                :(matches==0?"No matches in the files searched; search incomplete.":"Search incomplete; validated matches are shown.");
        return state+"\n"+matches+" matches in "+result.hits.size()+" validated files; "+result.candidateFiles+" candidate files; "+result.skippedTypes+" nontext types skipped; "+result.diagnostics.size()+" unreadable or skipped diagnostics.\n"
                +result.directoryAttempts+" directory attempts, "+result.scannedRows+" rows, "+result.bytesRead+" bytes read."
                +(result.reasons.isEmpty()?"":"\nLimits and diagnostics: "+searchReasons(result.reasons))
                +(result.reasons.contains(WorkspaceLiteralSearch.Reason.ACCESS_REVOKED)?"\nFolder access was revoked. Search again after choosing the folder again; these results cannot be opened.":"");
    }
    private void acceptWorkspaceSearch(SearchScreen screen,WorkspaceSearchLoader.Delivery delivery){
        screen.ticket=null;
        if(delivery.result==null||!screen.root.uri.toString().equals(delivery.result.tree.toString())||!screen.literal.equals(delivery.result.literal)){cancelWorkspaceSearch();showWorkspaceSearchNotice("Folder search failed without a complete result. Search again explicitly; editing and saving remain available.");return;}
        screen.result=delivery.result;
        LinearLayout rows=new LinearLayout(this);rows.setOrientation(MODE_PREVIEW);screen.rows=rows;
        final Object token=new Object();final AlertDialog[] owned=new AlertDialog[1];
        boolean enabled=!delivery.result.reasons.contains(WorkspaceLiteralSearch.Reason.ACCESS_REVOKED)&&delivery.result.state!=WorkspaceLiteralSearch.State.CANCELLED;
        int count=0;
        for(WorkspaceLiteralSearch.FileHit hit:delivery.result.hits)for(WorkspaceLiteralSearch.Match match:hit.matches){
            if(count++>=100)break;
            Button button=searchMatchButton(hit,match,view->{if(searchCurrent(screen,owned[0],token))selectWorkspaceMatch(screen,hit,match);});
            button.setEnabled(enabled);rows.addView(button,matchWrap());
        }
        ScrollView scroll=new ScrollView(this);scroll.addView(rows,matchWrap());
        owned[0]=historyDialogBuilder("Search results: "+screen.root.label).setMessage(searchSummary(delivery.result)).setView(scroll)
                .setPositiveButton("Search again",(ignored,which)->{if(searchCurrent(screen,owned[0],token))showWorkspaceSearch(screen.root,screen.literal);})
                .setNegativeButton("Close",(ignored,which)->{if(searchCurrent(screen,owned[0],token))cancelWorkspaceSearch();})
                .setNeutralButton("Back to folder",(ignored,which)->{if(searchCurrent(screen,owned[0],token))openWorkspaceRoot(screen.root);}).create();
        presentSearchDialog(screen,owned[0],token);
    }
    private Button searchMatchButton(WorkspaceLiteralSearch.FileHit hit,WorkspaceLiteralSearch.Match match,View.OnClickListener action){
        String snippet=match.snippet.replace("\r","\\r").replace("\n","\\n").replace("\t","\\t");
        String label=hit.displayName+" · line "+match.line+"\n"+snippet;
        Button button=workspaceLocalButton(label,"Open "+label,action);
        button.setTextColor(this.palette.text);button.setBackground(UiTheme.rounded(this.palette.raised,dp(12),this.palette.border,1));
        return button;
    }
    private void selectWorkspaceMatch(SearchScreen screen,WorkspaceLiteralSearch.FileHit hit,WorkspaceLiteralSearch.Match match){
        if(!searchCurrent(screen,screen.dialog,screen.token)||screen.result==null||screen.result.reasons.contains(WorkspaceLiteralSearch.Reason.ACCESS_REVOKED)||screen.result.state==WorkspaceLiteralSearch.State.CANCELLED)return;
        String literal=screen.literal;cancelWorkspaceSearch();
        SearchLocation location=new SearchLocation(hit,match,literal);this.searchLocation=location;
        this.searchOpenCall=location;
        try{openDocument(hit.uri,0,true);}catch(RuntimeException|Error failed){cancelSearchLocation();throw failed;}finally{this.searchOpenCall=null;}
    }
    private void searchOpenFailed(Uri uri,int load){
        SearchLocation location=this.searchLocation;
        if(location!=null&&location.awaitingOpen&&location.hit.uri.toString().equals(uri.toString())&&(load<0||location.load==load))cancelSearchLocation();
    }
    private void searchOpenFinished(Uri uri,int load){
        SearchLocation location=this.searchLocation;
        if(location!=null&&location.awaitingOpen&&location.load==load&&location.hit.uri.toString().equals(uri.toString()))completeSearchOpen(location);
    }
    private void completeSearchOpen(SearchLocation location){
        if(location!=this.searchLocation||this.activeTab<0||this.activeTab>=this.tabs.size())return;
        location.awaitingOpen=false;location.tabId=this.tabs.get(this.activeTab).id;location.revision=this.sourceNavigationRevision;
        location.route=this.sourceRouteRevision;location.bom=this.utf8Bom;location.load=this.loadToken;
        verifySearchLocation(location);
    }
    private boolean searchLocationCurrent(SearchLocation location){
        return this.searchLocation==location&&!location.awaitingOpen&&!isFinishing()&&!isDestroyed()
                &&this.activeTab>=0&&this.activeTab<this.tabs.size()&&location.tabId.equals(this.tabs.get(this.activeTab).id)
                &&this.currentUri!=null&&location.hit.uri.toString().equals(this.currentUri.toString())&&location.hit.uri.toString().equals(this.tabs.get(this.activeTab).uri)&&location.load==this.loadToken
                &&location.revision==this.sourceNavigationRevision&&location.route==this.sourceRouteRevision&&location.bom==this.utf8Bom;
    }
    private void verifySearchLocation(SearchLocation location){
        if(!searchLocationCurrent(location)){cancelSearchLocation();return;}
        try {
            location.ticket=SearchHitVerifier.tryVerify(()->{
                if(!searchLocationCurrent(location))throw new IllegalStateException("Retired search location");
                return new SearchHitVerifier.Capture(this.editor.getText(),this.utf8Bom);
            },outcome->{if(searchLocationCurrent(location))finishSearchLocation(location,outcome);else if(this.searchLocation==location)cancelSearchLocation();});
            if(location.ticket==null){cancelSearchLocation();showWorkspaceSearchNotice("The file was opened, but its search location could not be checked while an earlier check is still active. Search again explicitly; no automatic retry was made.");}
        }catch(RuntimeException failed){cancelSearchLocation();showSearchChanged();}
    }
    private void showSearchChanged(){showWorkspaceSearchNotice("File changed since search or could not be checked; search again. The normally opened document was kept.");}
    private void finishSearchLocation(SearchLocation location,SearchHitVerifier.Outcome outcome){
        WorkspaceLiteralSearch.Match match=location.match;
        int offset=match.offsetUtf16,length=match.lengthUtf16;
        boolean valid=outcome.result!=null&&outcome.result.byteLength==location.hit.byteLength&&outcome.result.sha256.equals(location.hit.hash)
                &&offset>=0&&length==location.literal.length()&&offset<=this.editor.length()&&length<=this.editor.length()-offset;
        if(valid)for(int index=0;index<length;index++)if(this.editor.getText().charAt(offset+index)!=location.literal.charAt(index)){valid=false;break;}
        cancelSearchLocation();
        if(!valid){showSearchChanged();return;}
        jumpToSourceOffset(offset);
        this.editor.setSelection(offset,offset+length);
    }

    private static final int REQUEST_WORKSPACE_TREE=1005;
    // Separate from Activity.main: destroying an Activity must not remove a physical delivery.
    private static final Handler WORKSPACE_MAIN=new Handler(Looper.getMainLooper());
    private static final java.util.concurrent.Executor WORKSPACE_DELIVERY=command -> {
        if(!WORKSPACE_MAIN.post(command))throw new java.util.concurrent.RejectedExecutionException("Workspace delivery unavailable");
    };
    private long workspaceGeneration;
    private boolean workspaceRegistryBusy;
    private WorkspaceRootList workspaceRootList;
    private WorkspaceBrowser workspaceBrowser;
    private static final class WorkspaceFrame {
        final WorkspaceTreeReader.Directory directory;final String label;final Uri uri;
        WorkspaceFrame(WorkspaceTreeReader.Directory directory,String label,Uri uri){this.directory=directory;this.label=label;this.uri=uri;}
    }
    private static final class WorkspaceLoad {
        final WorkspaceFrame frame;WorkspaceDirectoryLoader.Ticket ticket;
        WorkspaceLoad(WorkspaceFrame frame){this.frame=frame;}
    }
    private static final class WorkspaceRootList {
        final long generation;AlertDialog dialog;
        WorkspaceRootList(long generation){this.generation=generation;}
    }
    private static final class WorkspaceBrowser {
        final long generation;final WorkspaceRootStore.Root root;final WorkspaceTreeReader reader;
        final List<WorkspaceFrame> frames=new ArrayList<>();
        AlertDialog dialog,confirmation;TextView titleView;LinearLayout rows;WorkspaceLoad load;WorkspaceTreeReader.Page page;long rowGeneration;
        WorkspaceBrowser(long generation,WorkspaceRootStore.Root root,WorkspaceTreeReader reader){this.generation=generation;this.root=root;this.reader=reader;}
    }
    private boolean workspaceLive(long generation){return generation==this.workspaceGeneration&&!isFinishing()&&!isDestroyed();}
    private void cancelWorkspaceNavigation() {
        cancelWorkspaceSearch();
        ++this.workspaceGeneration;
        WorkspaceRootList roots=this.workspaceRootList;this.workspaceRootList=null;
        if(roots!=null&&roots.dialog!=null)roots.dialog.dismiss();
        WorkspaceBrowser browser=this.workspaceBrowser;this.workspaceBrowser=null;
        if(browser!=null) {
            WorkspaceLoad load=browser.load;browser.load=null;browser.page=null;++browser.rowGeneration;
            if(load!=null&&load.ticket!=null)load.ticket.cancel();
            if(browser.rows!=null)browser.rows.removeAllViews();browser.frames.clear();
            if(browser.confirmation!=null)browser.confirmation.dismiss();
            if(browser.dialog!=null)browser.dialog.dismiss();
        }
    }
    // Favorites are optional local shortcuts. They never grant provider permissions or mutate files.
    // All persistence runs on the existing I/O lane, and discarded Activity results are never rendered.
    private long favoriteRequest;
    private AlertDialog favoriteDialog;

    private void cancelWorkspaceFavorites() {
        ++this.favoriteRequest;
        AlertDialog previous=this.favoriteDialog;
        this.favoriteDialog=null;
        if(previous!=null)previous.dismiss();
    }

    private boolean favoriteCurrent(long request) {
        return request==this.favoriteRequest&&!isFinishing()&&!isDestroyed();
    }

    private void pinWorkspaceFavorite(Uri uri,String label,WorkspaceFavoritesStore.Kind kind) {
        if(uri==null||label==null||label.isEmpty()){showError("Choose an existing saved document or folder first.");return;}
        cancelWorkspaceFavorites();
        final long request=this.favoriteRequest;
        final android.content.Context application=getApplicationContext();
        final String safeLabel=label.length()>256?label.substring(0,256):label;
        this.io.execute(()->{
            try {
                new WorkspaceFavoritesStore(application).pin(uri,safeLabel,kind);
                this.main.post(()->{if(favoriteCurrent(request))Toast.makeText(this,"Added to Favorites",Toast.LENGTH_SHORT).show();});
            }catch(Exception failure){
                this.main.post(()->{if(favoriteCurrent(request))showError("Favorite could not be saved. No documents were changed.\n\n"+message(failure));});
            }
        });
    }

    private void updateFavoriteButtons(boolean pinned) {
        for(Button button:new Button[]{this.writeFavoriteButton,this.readFavoriteButton}){
            if(button==null)continue;
            button.setText(pinned?"★":"☆");
            button.setSelected(pinned);
            button.setContentDescription(pinned?
                "Remove current document from Favorites (file stays intact)":
                "Add current document to Favorites");
            if(this.palette!=null)styleButton(button,pinned,false);
        }
    }
    private void refreshFavoriteIndicators() {
        final String uri=this.currentUri==null?null:this.currentUri.toString();
        if(java.util.Objects.equals(uri,this.favoriteIndicatorUri))return;
        this.favoriteIndicatorUri=uri;
        final long generation=++this.favoriteIndicatorGeneration;
        this.favoriteIndicatorPinned=false;
        updateFavoriteButtons(false);
        if(uri==null)return;
        final android.content.Context app=getApplicationContext();
        this.io.execute(()->{
            try{
                boolean pinned=false;
                for(WorkspaceFavoritesStore.Favorite item:new WorkspaceFavoritesStore(app).read())
                    if(item.kind==WorkspaceFavoritesStore.Kind.DOCUMENT &&
                       item.uri.toString().equals(uri)){pinned=true;break;}
                final boolean selected=pinned;
                this.main.post(()->{
                    if(generation!=this.favoriteIndicatorGeneration||isFinishing()||isDestroyed())return;
                    if(this.currentUri==null||!uri.equals(this.currentUri.toString()))return;
                    this.favoriteIndicatorPinned=selected;
                    updateFavoriteButtons(selected);
                });
            }catch(Exception ignored){ /* Never misreport a failed read as a changed shortcut. */ }
        });
    }

    private void pinCurrentDocumentFavorite() {
        if(this.currentUri==null||this.activeTab<0||this.activeTab>=this.tabs.size()){
            showError("Save or open a document before adding it to Favorites.");return;
        }
        DocumentTab tab=this.tabs.get(this.activeTab);
        String name=tab.displayName;
        if(name==null||name.isEmpty())name=this.currentUri.getLastPathSegment();
        if(name==null||name.isEmpty())name="Saved document";
        cancelWorkspaceFavorites();
        final long request=this.favoriteRequest;
        final Uri exactUri=this.currentUri;
        final String label=name;
        final android.content.Context app=getApplicationContext();
        this.io.execute(()->{
            try {
                final boolean pinned=new WorkspaceFavoritesStore(app).toggleDocument(exactUri,label);
                this.main.post(()->{
                    if(!favoriteCurrent(request))return;
                    Toast.makeText(this,pinned?"Added to Favorites":
                        "Favorite removed; original file kept",Toast.LENGTH_SHORT).show();
                    this.favoriteIndicatorUri=null;
                    refreshFavoriteIndicators();
                });
            }catch(Exception failure){
                this.main.post(()->{
                    if(favoriteCurrent(request))showError(
                        "Favorite could not be saved or removed. No documents were changed.\n\n"+message(failure));
                });
            }
        });
    }

    private void removeWorkspaceFavorite(WorkspaceFavoritesStore.Favorite favorite) {
        cancelWorkspaceFavorites();
        final long request=this.favoriteRequest;
        final android.content.Context application=getApplicationContext();
        this.io.execute(()->{
            try{
                new WorkspaceFavoritesStore(application).unpin(favorite.uri);
                this.main.post(()->{if(favoriteCurrent(request))Toast.makeText(this,"Favorite removed; original file kept",Toast.LENGTH_SHORT).show();});
            }catch(Exception failure){
                this.main.post(()->{if(favoriteCurrent(request))showError("Favorite could not be removed; the shortcut was preserved.\n\n"+message(failure));});
            }
        });
    }

    private void openWorkspaceFavorite(WorkspaceFavoritesStore.Favorite favorite) {
        if(favorite.kind==WorkspaceFavoritesStore.Kind.DOCUMENT){
            openDocument(favorite.uri,3,true);return;
        }
        final long request=this.favoriteRequest;
        final android.content.Context application=getApplicationContext();
        this.io.execute(()->{
            try{
                WorkspaceRootStore.Root root=null;
                for(WorkspaceRootStore.RootState state:new WorkspaceRootStore(application).list(application.getContentResolver())){
                    if(state.root.uri.equals(favorite.uri)&&state.access==WorkspaceRootStore.Access.CURRENT){root=state.root;break;}
                }
                final WorkspaceRootStore.Root allowed=root;
                this.main.post(()->{
                    if(!favoriteCurrent(request))return;
                    if(allowed==null)showError("This folder needs access again. Open Folders and choose it again.");
                    else openWorkspaceRoot(allowed);
                });
            }catch(Exception failure){
                this.main.post(()->{if(favoriteCurrent(request))showError("Folder access could not be verified.\n\n"+message(failure));});
            }
        });
    }

    private void showWorkspaceFavoriteOptions(WorkspaceFavoritesStore.Favorite favorite) {
        if(isFinishing()||isDestroyed())return;
        final long request=this.favoriteRequest;
        final AlertDialog dialog=historyDialogBuilder(favorite.label)
            .setMessage(favorite.kind==WorkspaceFavoritesStore.Kind.TREE
                ?"Saved folder shortcut; opening requires a current folder permission."
                :"Saved document shortcut; opening requires current document permission.")
            .setPositiveButton("Open",(d,which)->{if(favoriteCurrent(request))openWorkspaceFavorite(favorite);})
            .setNeutralButton("Remove shortcut",(d,which)->{if(favoriteCurrent(request))removeWorkspaceFavorite(favorite);})
            .setNegativeButton("Back",(d,which)->{if(favoriteCurrent(request))showWorkspaceFavorites();})
            .create();
        this.favoriteDialog=dialog;
        dialog.setOnDismissListener(ignored->{if(this.favoriteDialog==dialog)this.favoriteDialog=null;});
        dialog.setOnShowListener(ignored->workspaceDialogShown(dialog));
        dialog.show();
    }

    // Shortcut removal is a separate, deliberate menu from one-tap open.
    private void showRemoveWorkspaceFavorite() {
        if(isFinishing()||isDestroyed())return;
        cancelWorkspaceFavorites();
        final long request=this.favoriteRequest;
        final android.content.Context application=getApplicationContext();
        this.io.execute(()->{
            try{
                final List<WorkspaceFavoritesStore.Favorite> saved=new WorkspaceFavoritesStore(application).read();
                this.main.post(()->{
                    if(!favoriteCurrent(request))return;
                    if(saved.isEmpty()){Toast.makeText(this,"No favorites to remove",Toast.LENGTH_SHORT).show();return;}
                    String[] labels=new String[saved.size()];
                    for(int i=0;i<saved.size();i++)
                        labels[i]=(saved.get(i).kind==WorkspaceFavoritesStore.Kind.TREE?"Folder · ":"Document · ")+saved.get(i).label;
                    AlertDialog dialog=historyDialogBuilder("Remove favorite")
                        .setItems(labels,(d,index)->{
                            if(favoriteCurrent(request))removeWorkspaceFavorite(saved.get(index));
                        })
                        .setNegativeButton("Cancel",null).create();
                    this.favoriteDialog=dialog;
                    dialog.setOnDismissListener(ignored->{
                        if(this.favoriteDialog==dialog)this.favoriteDialog=null;
                    });
                    dialog.setOnShowListener(ignored->workspaceDialogShown(dialog));
                    dialog.show();
                });
            }catch(Exception failure){
                this.main.post(()->{
                    if(favoriteCurrent(request))showError("Favorites could not be read. Existing shortcuts were kept.\n\n"+message(failure));
                });
            }
        });
    }

    private void showWorkspaceFavorites() {
        if(isFinishing()||isDestroyed())return;
        cancelWorkspaceFavorites();
        final long request=this.favoriteRequest;
        final android.content.Context application=getApplicationContext();
        this.io.execute(()->{
            try{
                final List<WorkspaceFavoritesStore.Favorite> saved=new WorkspaceFavoritesStore(application).read();
                this.main.post(()->{
                    if(!favoriteCurrent(request))return;
                    String[] names=new String[saved.size()];
                    for(int i=0;i<saved.size();i++)names[i]=
                        (saved.get(i).kind==WorkspaceFavoritesStore.Kind.TREE?"Folder · ":"Document · ")+saved.get(i).label;
                    AlertDialog.Builder builder=historyDialogBuilder("Favorites")
                        .setPositiveButton("☆ Add current document",(d,which)->{
                            if(favoriteCurrent(request))pinCurrentDocumentFavorite();
                        })
                        .setNegativeButton("Close",null);
                    if(saved.isEmpty())builder.setMessage("No favorites yet. Use ☆ Favorite current document in the Workspace for one-tap saving.");
                    else {
                        builder.setItems(names,(d,index)->{
                            if(favoriteCurrent(request))openWorkspaceFavorite(saved.get(index));
                        });
                        builder.setNeutralButton("Remove favorite…",(d,which)->{
                            if(favoriteCurrent(request))showRemoveWorkspaceFavorite();
                        });
                    }
                    AlertDialog dialog=builder.create();this.favoriteDialog=dialog;
                    dialog.setOnDismissListener(ignored->{if(this.favoriteDialog==dialog)this.favoriteDialog=null;});
                    dialog.setOnShowListener(ignored->workspaceDialogShown(dialog));
                    dialog.show();
                });
            }catch(Exception failure){
                this.main.post(()->{if(favoriteCurrent(request))showError("Favorites could not be read. Existing shortcuts were kept.\n\n"+message(failure));});
            }
        });
    }


    // Explicit local reading places. Source positions are verified against immutable
    // snapshots; any changed/ambiguous text refuses navigation rather than guessing.
    private volatile long readerRequest;
    private AlertDialog readerDialog;

    private void invalidateReaderActions() {
        ++this.readerRequest;
        AlertDialog old=this.readerDialog;
        this.readerDialog=null;
        if(old!=null)old.dismiss();
    }

    private boolean readerActionCurrent(long request,String tabId,String uri) {
        return !isFinishing()&&!isDestroyed()&&this.readerRequest==request
            &&this.activeTab>=0&&this.activeTab<this.tabs.size()
            &&tabId.equals(this.tabs.get(this.activeTab).id)
            &&this.currentUri!=null&&uri.equals(this.currentUri.toString());
    }

    private void readerDialogShow(AlertDialog dialog) {
        this.readerDialog=dialog;
        dialog.setOnDismissListener(ignored->{if(this.readerDialog==dialog)this.readerDialog=null;});
        dialog.setOnShowListener(ignored->workspaceDialogShown(dialog));
        dialog.show();
    }

    private void saveReaderAnchor(boolean bookmark,String label) {
        if(this.currentUri==null||this.activeTab<0||this.activeTab>=this.tabs.size()){
            showError("Open a saved document before saving a reading place.");return;
        }
        if(this.mode==MODE_PREVIEW){
            showError("Switch to Write or Split, place the cursor at the text you want, then save your place.");return;
        }
        invalidateReaderActions();
        final long request=this.readerRequest;
        final DocumentTab tab=this.tabs.get(this.activeTab);
        final String tabId=tab.id,uri=this.currentUri.toString();
        final String source=this.editor.getText().toString();
        final int offset=Math.max(0,Math.min(source.length(),this.editor.getSelectionStart()));
        final android.content.Context application=getApplicationContext();
        this.io.execute(()->{
            try{
                ReaderAnchor.Anchor anchor=ReaderAnchor.capture(source,offset,ReaderAnchor.Kind.TEXT,()->{
                    if(Thread.currentThread().isInterrupted())throw new IOException("Reading-place capture interrupted");
                });
                ReaderPositionStore store=new ReaderPositionStore(application);
                if(bookmark)store.addBookmark(uri,label,anchor);
                else store.saveLocation(uri,anchor);
                this.main.post(()->{
                    if(readerActionCurrent(request,tabId,uri))
                        Toast.makeText(this,bookmark?"Bookmark saved":"Reading place saved",Toast.LENGTH_SHORT).show();
                });
            }catch(Exception failure){
                this.main.post(()->{
                    if(readerActionCurrent(request,tabId,uri))
                        showError("Reading place could not be saved. The document was not changed.\\n\\n"+message(failure));
                });
            }
        });
    }

    private void promptReaderBookmark() {
        if(this.currentUri==null||this.activeTab<0||this.activeTab>=this.tabs.size()){
            showError("Open a saved document first.");return;
        }
        if(this.mode==MODE_PREVIEW){
            showError("Switch to Write or Split and position the cursor before bookmarking.");return;
        }
        invalidateReaderActions();
        final long request=this.readerRequest;
        final EditText input=new EditText(this);
        input.setSingleLine(true);
        input.setTextColor(this.palette.text);
        input.setHintTextColor(this.palette.muted);
        input.setHint("Bookmark name");
        input.setContentDescription("Name for reading bookmark");
        input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(80)});
        final String tabId=this.tabs.get(this.activeTab).id,uri=this.currentUri.toString();
        AlertDialog dialog=historyDialogBuilder("Bookmark this place")
            .setMessage("Bookmarks use the source cursor, not an estimated reading-screen scroll position.")
            .setView(input)
            .setPositiveButton("Save",null)
            .setNegativeButton("Cancel",null).create();
        readerDialogShow(dialog);
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(view->{
            if(!readerActionCurrent(request,tabId,uri))return;
            String label=input.getText().toString().trim();
            if(label.isEmpty()){input.setError("Enter a bookmark name");return;}
            dialog.dismiss();
            saveReaderAnchor(true,label);
        });
    }

    private void removeReaderBookmark(ReaderPositionStore.Bookmark bookmark,long request,String tabId,String uri) {
        if(!readerActionCurrent(request,tabId,uri))return;
        invalidateReaderActions();
        final long next=this.readerRequest;
        final android.content.Context application=getApplicationContext();
        this.io.execute(()->{
            try{
                new ReaderPositionStore(application).removeBookmark(uri,bookmark.id);
                this.main.post(()->{if(readerActionCurrent(next,tabId,uri))Toast.makeText(this,"Bookmark removed",Toast.LENGTH_SHORT).show();});
            }catch(Exception failure){
                this.main.post(()->{if(readerActionCurrent(next,tabId,uri))
                    showError("Bookmark could not be removed; the source document was not changed.\\n\\n"+message(failure));});
            }
        });
    }

    private void navigateReaderAnchor(ReaderAnchor.Anchor anchor,long request,String tabId,String uri) {
        if(!readerActionCurrent(request,tabId,uri))return;
        final String source=this.editor.getText().toString();
        this.io.execute(()->{
            try{
                ReaderAnchor.Resolution result=ReaderAnchor.resolve(anchor,source,()->{
                    if(Thread.currentThread().isInterrupted())throw new IOException("Reading-place search interrupted");
                });
                this.main.post(()->{
                    if(!readerActionCurrent(request,tabId,uri))return;
                    if(!this.editor.getText().toString().equals(source)){
                        showError("The document changed. Open Reading places again to verify the new text.");return;
                    }
                    if(result.status==ReaderAnchor.Status.UNRESOLVED){
                        showError("That passage moved or changed too much to locate safely.");return;
                    }
                    if(this.mode==MODE_PREVIEW)setMode(MODE_EDIT,false);
                    int offset=result.offset;
                    this.editor.setSelection(offset);
                    this.editor.post(()->{
                        if(readerActionCurrent(request,tabId,uri)
                            &&this.editor.getText().toString().equals(source))
                            this.editor.bringPointIntoView(offset);
                    });
                });
            }catch(Exception failure){
                this.main.post(()->{if(readerActionCurrent(request,tabId,uri))
                    showError("Reading place could not be verified.\\n\\n"+message(failure));});
            }
        });
    }

    private void showReaderBookmarkOptions(ReaderPositionStore.Bookmark bookmark,long request,String tabId,String uri) {
        if(!readerActionCurrent(request,tabId,uri))return;
        AlertDialog dialog=historyDialogBuilder(bookmark.label)
            .setMessage("Jump to the verified text, or remove only this bookmark.")
            .setPositiveButton("Go to text",(d,which)->{
                if(readerActionCurrent(request,tabId,uri))navigateReaderAnchor(bookmark.anchor,request,tabId,uri);
            })
            .setNeutralButton("Remove bookmark",(d,which)->removeReaderBookmark(bookmark,request,tabId,uri))
            .setNegativeButton("Back",(d,which)->{
                if(readerActionCurrent(request,tabId,uri))showReaderPlaces();
            }).create();
        readerDialogShow(dialog);
    }


    // Local protection inspection only: no policy update, maintenance or deletion.
    // A fresh underlying sample is required; unavailable accounting is never shown
    // as a fabricated amount of free storage.
    private volatile long protectionStatusRequest;
    private AlertDialog protectionStatusDialog;
    private void invalidateProtectionStatus() {
        ++this.protectionStatusRequest;
        AlertDialog old=this.protectionStatusDialog;
        this.protectionStatusDialog=null;
        if(old!=null)old.dismiss();
    }
    private static String protectionMiB(long bytes) {
        return String.format(Locale.ROOT,"%.2f MiB",bytes/1048576.0d);
    }
    private void showProtectionUsage() {
        if(isFinishing()||isDestroyed())return;
        invalidateProtectionStatus();
        final long request=this.protectionStatusRequest;
        this.io.execute(()->{
            try{
                final HistoryController.LocalProtectionStatus status=getHistoryController().inspectLocalProtection();
                this.main.post(()->{
                    if(request!=this.protectionStatusRequest||isFinishing()||isDestroyed())return;
                    StringBuilder details=new StringBuilder();
                    details.append("Local document protection and history (this phone only).\n");
                    if(!status.usageComplete||status.mutationBlocked){
                        details.append("\nStorage accounting is incomplete or temporarily blocked. Do not treat these readings as available capacity.");
                    }else{
                        details.append("\nProtected data: ").append(protectionMiB(status.dataActualBytes));
                        details.append("\nReserved for pending saves/operations: ")
                            .append(protectionMiB(status.completionReservedBytes+status.operationReservedBytes));
                        details.append("\nCharged data: ").append(protectionMiB(status.dataChargedBytes));
                        details.append("\nConfigured history limit: ").append(protectionMiB(status.quotaBytes));
                        if(status.overQuota)details.append("\nHistory usage exceeds the configured limit.");
                    }
                    if(!status.issues.isEmpty())details.append("\n\nThe protection system reports conditions that need review before storage changes.");
                    details.append("\n\nThis screen never cleans up, deletes, exports or transfers your document history.");
                    details.append(" Copying an .md file does not carry its private version history.");
                    AlertDialog dialog=historyDialogBuilder("Protected storage")
                        .setMessage(details.toString())
                        .setPositiveButton("Refresh",(d,which)->showProtectionUsage())
                        .setNegativeButton("Close",null).create();
                    this.protectionStatusDialog=dialog;
                    dialog.setOnDismissListener(ignored->{
                        if(this.protectionStatusDialog==dialog)this.protectionStatusDialog=null;
                    });
                    dialog.setOnShowListener(ignored->workspaceDialogShown(dialog));
                    dialog.show();
                });
            }catch(Exception failure){
                this.main.post(()->{
                    if(request==this.protectionStatusRequest&&!isFinishing()&&!isDestroyed())
                        showError("Protected storage status is unavailable. Your documents and recovery data were left untouched.");
                });
            }
        });
    }

    private void showRemoveReaderBookmark() {
        if(this.currentUri==null||this.activeTab<0||this.activeTab>=this.tabs.size()){
            showError("Open a saved document before managing bookmarks.");return;
        }
        invalidateReaderActions();
        final long request=this.readerRequest;
        final String tabId=this.tabs.get(this.activeTab).id,uri=this.currentUri.toString();
        final android.content.Context application=getApplicationContext();
        this.io.execute(()->{
            try{
                final ReaderPositionStore.DocumentState state=new ReaderPositionStore(application).read(uri);
                this.main.post(()->{
                    if(!readerActionCurrent(request,tabId,uri))return;
                    if(state==null||state.bookmarks.isEmpty()){
                        Toast.makeText(this,"No bookmarks to remove",Toast.LENGTH_SHORT).show();return;
                    }
                    String[] names=new String[state.bookmarks.size()];
                    for(int i=0;i<names.length;i++)names[i]=state.bookmarks.get(i).label;
                    AlertDialog dialog=historyDialogBuilder("Remove bookmark")
                        .setItems(names,(d,index)->{
                            if(readerActionCurrent(request,tabId,uri))
                                removeReaderBookmark(state.bookmarks.get(index),request,tabId,uri);
                        })
                        .setNegativeButton("Cancel",null).create();
                    readerDialogShow(dialog);
                });
            }catch(Exception failure){
                this.main.post(()->{if(readerActionCurrent(request,tabId,uri))
                    showError("Bookmarks could not be read; existing data was kept.\n\n"+message(failure));});
            }
        });
    }

    private void showReaderPlaces() {
        if(this.currentUri==null||this.activeTab<0||this.activeTab>=this.tabs.size()){
            showError("Open a saved document to see its reading places.");return;
        }
        invalidateReaderActions();
        final long request=this.readerRequest;
        final String tabId=this.tabs.get(this.activeTab).id,uri=this.currentUri.toString();
        final android.content.Context application=getApplicationContext();
        this.io.execute(()->{
            try{
                final ReaderPositionStore.DocumentState state=new ReaderPositionStore(application).read(uri);
                this.main.post(()->{
                    if(!readerActionCurrent(request,tabId,uri))return;
                    int count=state==null?0:state.bookmarks.size(),extra=state!=null&&state.location!=null?1:0;
                    final String[] labels=new String[extra+count];
                    if(extra!=0)labels[0]="↪ Previously saved reading place";
                    for(int i=0;i<count;i++)labels[i+extra]="↪ "+state.bookmarks.get(i).label;
                    AlertDialog.Builder builder=historyDialogBuilder("Bookmarks")
                        .setPositiveButton("☆ Bookmark this spot",(d,which)->{
                            if(readerActionCurrent(request,tabId,uri))promptReaderBookmark();
                        })
                        .setNegativeButton("Close",null);
                    if(labels.length==0)builder.setMessage("No bookmarks yet. In Write or Split, place the cursor and tap ☆ Bookmark this spot.");
                    else {
                        builder.setItems(labels,(d,index)->{
                            if(!readerActionCurrent(request,tabId,uri))return;
                            if(extra!=0&&index==0)navigateReaderAnchor(state.location,request,tabId,uri);
                            else navigateReaderAnchor(state.bookmarks.get(index-extra).anchor,request,tabId,uri);
                        });
                        if(count>0)builder.setNeutralButton("Remove bookmark…",(d,which)->{
                            if(readerActionCurrent(request,tabId,uri))showRemoveReaderBookmark();
                        });
                    }
                    readerDialogShow(builder.create());
                });
            }catch(Exception failure){
                this.main.post(()->{if(readerActionCurrent(request,tabId,uri))
                    showError("Reading places could not be loaded. Existing data was left intact.\n\n"+message(failure));});
            }
        });
    }

    private void addWorkspaceFolderActions(LinearLayout parent) {
        parent.addView(workspaceAction("Add folder","Choose a folder with Android's document picker",view -> {
            if(this.workspaceDialog!=null)this.workspaceDialog.dismiss();launchWorkspaceFolderPicker();
        }),matchWrap());
        parent.addView(workspaceAction("Folders","Browse your saved folder shortcuts",view -> {
            if(this.workspaceDialog!=null)this.workspaceDialog.dismiss();showWorkspaceFolders();
        }),matchWrap());
        parent.addView(workspaceAction("☆ Favorite current document","One tap to save this document shortcut",view -> {
            if(this.workspaceDialog!=null)this.workspaceDialog.dismiss();pinCurrentDocumentFavorite();
        }),matchWrap());
        parent.addView(workspaceAction("☆ Favorites","Tap a favorite to open it, or manage shortcuts",view -> {
            if(this.workspaceDialog!=null)this.workspaceDialog.dismiss();showWorkspaceFavorites();
        }),matchWrap());
    }
    private void launchWorkspaceFolderPicker() {
        if(isFinishing()||isDestroyed())return;
        cancelWorkspaceNavigation();
        Intent picker=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        picker.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                |Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION|Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        try{startActivityForResult(picker,REQUEST_WORKSPACE_TREE);}
        catch(ActivityNotFoundException failure){showError("No folder picker is available on this device.");}
    }
    private void handleWorkspaceTreeResult(int result,Intent intent) {
        if(isFinishing()||isDestroyed())return;
        if(result!=RESULT_OK||intent==null||intent.getData()==null)return;
        int flags=intent.getFlags();
        if((flags&Intent.FLAG_GRANT_READ_URI_PERMISSION)==0||(flags&Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)==0) {
            showError("Folder access could not be saved. Choose the folder again.");return;
        }
        if(this.workspaceRegistryBusy){showError("A saved-folder update is still active. Try again shortly.");return;}
        cancelWorkspaceNavigation();final long generation=this.workspaceGeneration;
        final Uri tree=intent.getData();final android.content.Context application=getApplicationContext();
        final int grants=flags&(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        this.workspaceRegistryBusy=true;
        this.io.execute(() -> {
            try {
                // Validate the exact tree before permission mutation; no provider query for a label.
                new WorkspaceTreeReader(application.getContentResolver(),tree);
                String id=DocumentsContract.getTreeDocumentId(tree);int end=Math.min(256,id.length());
                if(end<id.length()&&end>0&&Character.isHighSurrogate(id.charAt(end-1)))end--;
                String label=id.substring(0,end);
                application.getContentResolver().takePersistableUriPermission(tree,grants);
                WorkspaceRootStore.Root root=new WorkspaceRootStore(application).rememberGranted(tree,label,application.getContentResolver());
                this.main.post(() -> {this.workspaceRegistryBusy=false;if(workspaceLive(generation))openWorkspaceRoot(root);});
            }catch(Exception failure){this.main.post(() -> {this.workspaceRegistryBusy=false;if(workspaceLive(generation))showError("Folder access could not be saved. Choose the folder again.\n\n"+message(failure));});}
        });
    }
    private void showWorkspaceFolders() {
        if(isFinishing()||isDestroyed())return;
        if(this.workspaceRegistryBusy){showError("A saved-folder update is still active. Try again shortly.");return;}
        cancelWorkspaceNavigation();final long generation=this.workspaceGeneration;
        final android.content.Context application=getApplicationContext();this.workspaceRegistryBusy=true;
        this.io.execute(() -> {
            try {
                List<WorkspaceRootStore.RootState> roots=new WorkspaceRootStore(application).list(application.getContentResolver());
                this.main.post(() -> {
                    this.workspaceRegistryBusy=false;if(!workspaceLive(generation))return;
                    WorkspaceRootList list=new WorkspaceRootList(generation);this.workspaceRootList=list;
                    String[] labels=new String[roots.size()];for(int n=0;n<roots.size();n++)labels[n]=roots.get(n).root.label+(roots.get(n).access==WorkspaceRootStore.Access.ACCESS_NEEDED?" · Access needed":"");
                    AlertDialog.Builder builder=historyDialogBuilder("Folders").setPositiveButton("Add folder",(dialog,which)->{if(workspaceRootListCurrent(list))launchWorkspaceFolderPicker();}).setNegativeButton("Close",null);
                    if(roots.isEmpty())builder.setMessage("No folders saved yet. Add a folder to browse its files.");
                    else builder.setAdapter(workspaceRootRows(labels),(dialog,index)->{if(!workspaceRootListCurrent(list))return;WorkspaceRootStore.RootState selected=roots.get(index);if(selected.access==WorkspaceRootStore.Access.CURRENT)openWorkspaceRoot(selected.root);else showWorkspaceRootOptions(selected.root,true);});
                    list.dialog=builder.create();list.dialog.setOnDismissListener(dialog->{if(this.workspaceRootList==list){this.workspaceRootList=null;++this.workspaceGeneration;}});
                    list.dialog.setOnShowListener(dialog->workspaceDialogShown(list.dialog));list.dialog.show();
                });
            }catch(Exception failure){this.main.post(() -> {this.workspaceRegistryBusy=false;if(workspaceLive(generation))showError("Saved folders could not be read; existing shortcuts were kept.\n\n"+message(failure));});}
        });
    }
    private boolean workspaceRootListCurrent(WorkspaceRootList list){return list!=null&&this.workspaceRootList==list&&workspaceLive(list.generation)&&list.dialog!=null&&list.dialog.isShowing();}
    private void showWorkspaceRootOptions(WorkspaceRootStore.Root root,boolean needsAccess) {
        cancelWorkspaceNavigation();WorkspaceRootList list=new WorkspaceRootList(this.workspaceGeneration);this.workspaceRootList=list;
        list.dialog=historyDialogBuilder(root.label).setMessage(needsAccess?"Access is needed. Choose this folder again to restore access, or remove its shortcut.":"Removing this shortcut does not delete files or release existing document permissions.")
                .setPositiveButton("Choose folder again",(dialog,which)->{if(workspaceRootListCurrent(list))launchWorkspaceFolderPicker();})
                .setNeutralButton("Remove from list",(dialog,which)->{if(workspaceRootListCurrent(list))removeWorkspaceRoot(root);})
                .setNegativeButton("Cancel",null).create();
        list.dialog.setOnDismissListener(dialog->{if(this.workspaceRootList==list){this.workspaceRootList=null;++this.workspaceGeneration;}});
        list.dialog.setOnShowListener(dialog->workspaceDialogShown(list.dialog));list.dialog.show();
    }
    private void removeWorkspaceRoot(WorkspaceRootStore.Root root) {
        if(this.workspaceRegistryBusy)return;cancelWorkspaceNavigation();final long generation=this.workspaceGeneration;
        final android.content.Context application=getApplicationContext();this.workspaceRegistryBusy=true;
        this.io.execute(() -> {try {
            new WorkspaceRootStore(application).remove(root.uri);
            this.main.post(() -> {this.workspaceRegistryBusy=false;if(workspaceLive(generation))showWorkspaceFolders();});
        }catch(Exception failure){this.main.post(() -> {this.workspaceRegistryBusy=false;if(workspaceLive(generation))showError("Folder shortcut could not be removed.\n\n"+message(failure));});}});
    }
    private void openWorkspaceRoot(WorkspaceRootStore.Root root) {
        cancelWorkspaceNavigation();
        try {
            WorkspaceTreeReader reader=new WorkspaceTreeReader(getApplicationContext().getContentResolver(),root.uri);
            WorkspaceBrowser browser=new WorkspaceBrowser(this.workspaceGeneration,root,reader);this.workspaceBrowser=browser;
            browser.frames.add(new WorkspaceFrame(reader.root(),root.label,DocumentsContract.buildDocumentUriUsingTree(root.uri,DocumentsContract.getTreeDocumentId(root.uri))));
            LinearLayout content=new LinearLayout(this);content.setOrientation(MODE_PREVIEW);content.setPadding(dp(16),dp(8),dp(16),dp(8));
            content.addView(workspaceLocalButton("Retry","Reload this bounded folder listing",view->{if(workspaceBrowserCurrent(browser))loadWorkspaceFrame(browser);}),matchWrap());
            content.addView(workspaceLocalButton("Search this saved folder","Search Markdown and text in this saved root and its subfolders",view->{if(workspaceBrowserCurrent(browser))showWorkspaceSearch(browser.root,"");}),matchWrap());
            content.addView(workspaceLocalButton("Folder options","Choose this folder again or remove its shortcut",view->{if(workspaceBrowserCurrent(browser))showWorkspaceRootOptions(root,false);}),matchWrap());
            content.addView(workspaceLocalButton("☆ Favorite this saved folder","Save a shortcut, without changing folder access or files",view->{
                if(workspaceBrowserCurrent(browser))pinWorkspaceFavorite(root.uri,root.label,WorkspaceFavoritesStore.Kind.TREE);
            }),matchWrap());
            browser.rows=new LinearLayout(this);browser.rows.setOrientation(MODE_PREVIEW);content.addView(browser.rows,matchWrap());
            ScrollView scroll=new ScrollView(this);scroll.addView(content,matchWrap());
            browser.titleView=workspaceTitle(root.label);
            browser.dialog=new AlertDialog.Builder(this).setCustomTitle(browser.titleView).setView(scroll)
                    .setNegativeButton("Close",(dialog,which)->{if(workspaceBrowserCurrent(browser))cancelWorkspaceNavigation();})
                    .setNeutralButton("Up",(dialog,which)->{})
                    .setPositiveButton("Folders",(dialog,which)->{if(workspaceBrowserCurrent(browser))showWorkspaceFolders();}).create();
            browser.dialog.setOnDismissListener(dialog->{if(this.workspaceBrowser==browser)cancelWorkspaceNavigation();});
            browser.dialog.setOnKeyListener((dialog,key,event)->{if(key!=KeyEvent.KEYCODE_BACK||!workspaceBrowserCurrent(browser))return false;if(event.getAction()==KeyEvent.ACTION_UP)workspaceUp(browser);return true;});
            browser.dialog.setOnShowListener(dialog->{workspaceDialogShown(browser.dialog);browser.dialog.getButton(DialogInterface.BUTTON_NEUTRAL).setOnClickListener(view->{if(workspaceBrowserCurrent(browser))workspaceUp(browser);});});
            browser.dialog.show();loadWorkspaceFrame(browser);
        }catch(RuntimeException failure){cancelWorkspaceNavigation();showError("Folder could not be opened.\n\n"+message(failure));}
    }
    private boolean workspaceBrowserCurrent(WorkspaceBrowser browser){return browser!=null&&this.workspaceBrowser==browser&&workspaceLive(browser.generation)&&browser.dialog!=null&&browser.dialog.isShowing()&&!browser.frames.isEmpty();}
    private void loadWorkspaceFrame(WorkspaceBrowser browser) {
        if(!workspaceBrowserCurrent(browser))return;
        WorkspaceLoad previous=browser.load;browser.load=null;if(previous!=null&&previous.ticket!=null)previous.ticket.cancel();
        browser.page=null;++browser.rowGeneration;browser.rows.removeAllViews();
        WorkspaceFrame frame=browser.frames.get(browser.frames.size()-1);browser.titleView.setText(frame.label);browser.titleView.setTextColor(this.palette.text);
        browser.dialog.getButton(DialogInterface.BUTTON_NEUTRAL).setEnabled(browser.frames.size()>1);
        workspaceMessage(browser,"Loading folder…");WorkspaceLoad load=new WorkspaceLoad(frame);browser.load=load;
        load.ticket=WorkspaceDirectoryLoader.tryLoad(browser.reader,frame.directory,WORKSPACE_DELIVERY,page->acceptWorkspacePage(browser,load,page));
        if(load.ticket==null){browser.load=null;browser.rows.removeAllViews();workspaceMessage(browser,"Folder browsing is busy with an outstanding request or an earlier unconfirmed close. Wait if a request is active; if a close failed, restart the app. Editing and saving remain available.");}
    }
    private void acceptWorkspacePage(WorkspaceBrowser browser,WorkspaceLoad load,WorkspaceTreeReader.Page page) {
        if(!workspaceBrowserCurrent(browser)||browser.load!=load||load.ticket==null||browser.frames.get(browser.frames.size()-1)!=load.frame)return;
        browser.load=null;browser.rows.removeAllViews();if(page.state==WorkspaceTreeReader.State.CANCELLED){workspaceMessage(browser,"Folder listing stopped. Retry to load this folder.");return;}
        browser.page=page;final long rows=++browser.rowGeneration;final WorkspaceFrame frame=load.frame;
        boolean revoked=page.error==WorkspaceTreeReader.Error.ACCESS_REVOKED;
        if(revoked)workspaceMessage(browser,"Access was revoked. Choose the folder again; these rows cannot be opened.");
        else if(page.closeDisposition()==WorkspaceTreeReader.CloseDisposition.UNCERTAIN)workspaceMessage(browser,"The provider could not confirm closing this folder listing. Folder browsing is unavailable until the app restarts. Editing and saving remain available.");
        else if(page.state==WorkspaceTreeReader.State.FAILED)workspaceMessage(browser,"Folder could not be fully read. Partial results may be shown. Retry or go Back.");
        else if(page.state==WorkspaceTreeReader.State.LIMITED)workspaceMessage(browser,"Folder list is limited; not all items are shown. Retry reloads this same listing.");
        else if(page.entries.isEmpty())workspaceMessage(browser,"This folder is empty.");
        if(revoked)browser.rows.addView(workspaceLocalButton("Choose folder again","Restore access with Android's folder picker",view->{if(workspaceRowCurrent(browser,frame,rows))launchWorkspaceFolderPicker();}),matchWrap());
        for(WorkspaceTreeReader.Entry entry:page.entries) {
            Button button=workspaceLocalButton((entry.directory==null?"File · ":"Folder · ")+entry.displayName,entry.directory==null?"Open "+entry.displayName:"Browse "+entry.displayName,
                    view->selectWorkspaceEntry(browser,frame,rows,entry));
            button.setEnabled(!revoked);browser.rows.addView(button,matchWrap());
        }
    }
    private boolean workspaceRowCurrent(WorkspaceBrowser browser,WorkspaceFrame frame,long rows){return workspaceBrowserCurrent(browser)&&browser.rowGeneration==rows&&browser.frames.get(browser.frames.size()-1)==frame;}
    private void selectWorkspaceEntry(WorkspaceBrowser browser,WorkspaceFrame frame,long rows,WorkspaceTreeReader.Entry entry) {
        if(!workspaceRowCurrent(browser,frame,rows)||browser.page==null||browser.page.error==WorkspaceTreeReader.Error.ACCESS_REVOKED)return;
        if(entry.directory!=null) {
            if(browser.frames.size()>=32){showError("Folder navigation is limited to 32 levels. Go Up before opening another folder.");return;}
            for(WorkspaceFrame ancestor:browser.frames)if(ancestor.uri.toString().equals(entry.uri.toString())){showError("This folder points back to a folder already in this path.");return;}
            browser.frames.add(new WorkspaceFrame(entry.directory,entry.displayName,entry.uri));loadWorkspaceFrame(browser);return;
        }
        String name=entry.displayName.toLowerCase(Locale.ROOT);
        if(entry.mimeType.startsWith("text/")||name.endsWith(".md")||name.endsWith(".markdown")||name.endsWith(".txt")){openWorkspaceFile(browser,frame,rows,entry);return;}
        if(browser.confirmation!=null)browser.confirmation.dismiss();
        AlertDialog confirmation=historyDialogBuilder("Open as text?").setMessage(entry.displayName+" may not be a text file. Normal UTF-8 and size checks still apply.")
                .setPositiveButton("Open as text",(dialog,which)->{
                    AlertDialog accepted=browser.confirmation;
                    if(accepted==null||accepted!=dialog||!accepted.isShowing())return;
                    browser.confirmation=null;accepted.dismiss();
                    openWorkspaceFile(browser,frame,rows,entry);
                }).setNegativeButton("Cancel",null).create();
        browser.confirmation=confirmation;
        confirmation.setOnDismissListener(dialog->{if(browser.confirmation==dialog)browser.confirmation=null;});
        confirmation.setOnShowListener(dialog->workspaceDialogShown(confirmation));confirmation.show();
    }
    private void openWorkspaceFile(WorkspaceBrowser browser,WorkspaceFrame frame,long rows,WorkspaceTreeReader.Entry entry) {
        if(!workspaceRowCurrent(browser,frame,rows)||browser.page==null||browser.page.error==WorkspaceTreeReader.Error.ACCESS_REVOKED)return;
        cancelWorkspaceNavigation();openDocument(entry.uri,0,true);
    }
    private void workspaceUp(WorkspaceBrowser browser) {
        if(!workspaceBrowserCurrent(browser))return;
        if(browser.frames.size()==1){cancelWorkspaceNavigation();return;}
        browser.frames.remove(browser.frames.size()-1);loadWorkspaceFrame(browser);
    }
    private android.widget.ArrayAdapter<String> workspaceRootRows(String[] labels) {
        return new android.widget.ArrayAdapter<String>(this,android.R.layout.simple_list_item_1,labels) {
            @Override public View getView(int position,View recycled,ViewGroup parent) {
                TextView row=(TextView)super.getView(position,recycled,parent);
                row.setTextColor(MainActivity.this.palette.text);
                return row;
            }
        };
    }
    private TextView workspaceTitle(String title) {
        TextView heading=new TextView(this);heading.setText(title);heading.setTextColor(this.palette.text);heading.setTextSize(20);
        heading.setPadding(dp(24),dp(20),dp(24),dp(12));
        if(Build.VERSION.SDK_INT>=28)heading.setAccessibilityHeading(true);
        return heading;
    }
    private void workspaceDialogShown(AlertDialog dialog) {
        styleDialog(dialog);
        TextView message=dialog.findViewById(android.R.id.message);
        if(message!=null)message.setTextColor(this.palette.text);
    }
    private Button workspaceLocalButton(String label,String description,View.OnClickListener listener) {
        Button button=createPillButton(label,description,listener);this.buttons.remove(button);return button;
    }
    private void workspaceMessage(WorkspaceBrowser browser,String message) {
        TextView text=new TextView(this);text.setText(message);text.setTextColor(this.palette.muted);text.setPadding(dp(4),dp(8),dp(4),dp(8));browser.rows.addView(text,matchWrap());
    }

    /** Compact file destination; document editing and recovery tools stay in More.
     * Reuses the existing picker, recent-store and SAF folder flow without changing
     * their permissions, tabs, history or save behavior. */

    private long filesSearchPickerRequest;
    private void showFilesSearchRootPicker() {
        if(isFinishing()||isDestroyed())return;
        if(this.workspaceDialog!=null)this.workspaceDialog.dismiss();
        final long request=++this.filesSearchPickerRequest;
        final android.content.Context application=getApplicationContext();
        this.io.execute(()->{
            try {
                final List<WorkspaceRootStore.RootState> roots=
                    new WorkspaceRootStore(application).list(application.getContentResolver());
                this.main.post(()->{
                    if(request!=this.filesSearchPickerRequest||isFinishing()||isDestroyed())return;
                    if(roots.isEmpty()){
                        showError("Add a saved folder under Files before searching across documents.");
                        return;
                    }
                    String[] labels=new String[roots.size()];
                    for(int i=0;i<roots.size();i++){
                        WorkspaceRootStore.RootState item=roots.get(i);
                        labels[i]=item.root.label+(item.access==WorkspaceRootStore.Access.CURRENT
                            ?"":" · Access needed");
                    }
                    AlertDialog chooser=historyDialogBuilder("Search files · choose a folder")
                        .setItems(labels,(d,index)->{
                            if(request!=this.filesSearchPickerRequest)return;
                            WorkspaceRootStore.RootState selected=roots.get(index);
                            if(selected.access==WorkspaceRootStore.Access.CURRENT)
                                showWorkspaceSearch(selected.root,"");
                            else showWorkspaceRootOptions(selected.root,true);
                        }).setNegativeButton("Cancel",null).create();
                    chooser.setOnShowListener(d->workspaceDialogShown(chooser));
                    chooser.show();
                });
            }catch(Exception failure){
                this.main.post(()->{
                    if(request==this.filesSearchPickerRequest&&!isFinishing()&&!isDestroyed())
                        showError("Could not inspect saved folders; no document was changed.");
                });
            }
        });
    }


    private Button filesAction(String label,String description,View.OnClickListener listener) {
        Button row=createPillButton(label,description,listener);
        this.buttons.remove(row);
        row.setTextSize(15.0f);
        row.setGravity(Gravity.CENTER_VERTICAL|Gravity.LEFT);
        row.setSingleLine(true);
        row.setMaxLines(1);
        row.setEllipsize(TextUtils.TruncateAt.END);
        row.setPadding(dp(14),0,dp(10),0);
        row.setMinHeight(dp(48));
        styleButton(row,false,false);
        return row;
    }

    private long filesRowsRequest;
    private void fillFilesFavorites(LinearLayout rows) {
        final long request=++this.filesRowsRequest;
        final android.content.Context app=getApplicationContext();
        this.io.execute(()->{
            try {
                final List<WorkspaceFavoritesStore.Favorite> items=
                    new WorkspaceFavoritesStore(app).read();
                this.main.post(()->{
                    if(request!=this.filesRowsRequest||isFinishing()||isDestroyed())return;
                    rows.removeAllViews();
                    if(items.isEmpty()){
                        rows.addView(filesAction("☆  Add current document",
                            "Save a favorite shortcut",v->{
                                if(this.workspaceDialog!=null)this.workspaceDialog.dismiss();
                                pinCurrentDocumentFavorite();
                            }),matchWrap());
                    }
                    for(int n=0;n<Math.min(7,items.size());n++){
                        WorkspaceFavoritesStore.Favorite item=items.get(n);
                        rows.addView(filesAction("★  "+item.label,"Open saved shortcut",v->{
                            if(request!=this.filesRowsRequest)return;
                            if(this.workspaceDialog!=null)this.workspaceDialog.dismiss();
                            cancelWorkspaceFavorites();
                            openWorkspaceFavorite(item);
                        }),matchWrap());
                    }
                });
            }catch(Exception error){this.main.post(()->{
                if(request==this.filesRowsRequest&&!isFinishing()&&!isDestroyed()){
                    rows.removeAllViews();
                    rows.addView(filesAction("Favorites unavailable",
                        "Stored shortcuts remain protected",v->showWorkspaceFavorites()),matchWrap());
                }
            });}
        });
    }

    private long filesFoldersRequest;
    private void fillFilesFolders(LinearLayout rows) {
        final long request=++this.filesFoldersRequest;
        final android.content.Context app=getApplicationContext();
        this.io.execute(()->{
            try {
                final List<WorkspaceRootStore.RootState> items=
                    new WorkspaceRootStore(app).list(app.getContentResolver());
                this.main.post(()->{
                    if(request!=this.filesFoldersRequest||isFinishing()||isDestroyed())return;
                    rows.removeAllViews();
                    if(items.isEmpty())rows.addView(filesAction("＋  Add saved folder",
                        "Use Android's folder picker",v->{
                            if(this.workspaceDialog!=null)this.workspaceDialog.dismiss();
                            launchWorkspaceFolderPicker();
                        }),matchWrap());
                    for(int n=0;n<Math.min(7,items.size());n++){
                        WorkspaceRootStore.RootState item=items.get(n);
                        boolean granted=item.access==WorkspaceRootStore.Access.CURRENT;
                        rows.addView(filesAction("▣  "+item.root.label+
                            (granted?"":" · Access needed"),"Browse saved folder",v->{
                            if(request!=this.filesFoldersRequest)return;
                            if(this.workspaceDialog!=null)this.workspaceDialog.dismiss();
                            if(granted)openWorkspaceRoot(item.root);
                            else showWorkspaceRootOptions(item.root,true);
                        }),matchWrap());
                    }
                });
            }catch(Exception error){this.main.post(()->{
                if(request==this.filesFoldersRequest&&!isFinishing()&&!isDestroyed()){
                    rows.removeAllViews();
                    rows.addView(filesAction("Saved folders unavailable",
                        "No source files were changed",v->showWorkspaceFolders()),matchWrap());
                }
            });}
        });
    }

    private ScrollView buildFilesNavigatorView() {
        ScrollView scroll=new ScrollView(this);
        LinearLayout content=new LinearLayout(this);
        content.setOrientation(MODE_PREVIEW);
        content.setPadding(dp(18),dp(10),dp(18),dp(14));
        TextView description=new TextView(this);
        description.setText("Open or switch documents. Editing tools are in the editor and More menu.");
        description.setTextSize(13.0f);
        description.setTextColor(this.palette.muted);
        description.setPadding(dp(4),dp(4),dp(4),dp(12));
        content.addView(description,matchWrap());
        final List<RecentStore.Item> recent=RecentStore.read(this);
        if(!recent.isEmpty()){
            TextView label=new TextView(this);
            label.setText("RECENT DOCUMENTS · tap to open");
            label.setTextSize(12.0f);
            label.setLetterSpacing(0.12f);
            label.setTextColor(this.palette.muted);
            label.setPadding(dp(4),dp(14),dp(4),dp(6));
            content.addView(label,matchWrap());
            final int shown=Math.min(8,recent.size());
            for(int i=0;i<shown;i++){
                final RecentStore.Item item=recent.get(i);
                content.addView(filesAction(item.name,"Open recent document",view->{
                    if(this.workspaceDialog!=null)this.workspaceDialog.dismiss();
                    openDocument(Uri.parse(item.uri),3,true);
                }),matchWrap());
            }
            if(recent.size()>shown)content.addView(filesAction("All recent documents","Browse every recent document",view->{
                if(this.workspaceDialog!=null)this.workspaceDialog.dismiss();showRecents();
            }),matchWrap());
        }

        TextView pinnedTitle=workspaceTitle("FAVORITES");
        pinnedTitle.setTextSize(12.0f);
        content.addView(pinnedTitle,matchWrap());
        LinearLayout pinnedRows=new LinearLayout(this);
        pinnedRows.setOrientation(MODE_PREVIEW);
        content.addView(pinnedRows,matchWrap());
        fillFilesFavorites(pinnedRows);
        TextView folderTitle=workspaceTitle("SAVED FOLDERS");
        folderTitle.setTextSize(12.0f);
        content.addView(folderTitle,matchWrap());
        LinearLayout folderRows=new LinearLayout(this);
        folderRows.setOrientation(MODE_PREVIEW);
        content.addView(folderRows,matchWrap());
        fillFilesFolders(folderRows);
        TextView destinations=workspaceTitle("BROWSE & CREATE");
        destinations.setTextSize(12.0f);
        destinations.setPadding(dp(4),dp(8),dp(4),dp(6));
        content.addView(destinations,matchWrap());
        content.addView(filesAction("Open a file","Choose a Markdown or text document",view->{
            if(this.workspaceDialog!=null)this.workspaceDialog.dismiss();launchOpen();
        }),matchWrap());
        content.addView(filesAction("New document","Start writing a new document",view->{
            if(this.workspaceDialog!=null)this.workspaceDialog.dismiss();showNewDocumentMenu();
        }),matchWrap());
        content.addView(filesAction("Open tabs","Switch to a document already open",view->{
            if(this.workspaceDialog!=null)this.workspaceDialog.dismiss();showTabs();
        }),matchWrap());
        content.addView(filesAction("Favorites","One tap to reopen saved documents or folders",view->{
            if(this.workspaceDialog!=null)this.workspaceDialog.dismiss();showWorkspaceFavorites();
        }),matchWrap());
        content.addView(filesAction("Folders","Browse saved folders or search inside a folder",view->{
            if(this.workspaceDialog!=null)this.workspaceDialog.dismiss();showWorkspaceFolders();
        }),matchWrap());
        content.addView(filesAction("Search files","Choose a saved folder, then search its text",view->{
            showFilesSearchRootPicker();
        }),matchWrap());
        content.addView(filesAction("Add folder","Use Android's folder picker to remember a folder",view->{
            if(this.workspaceDialog!=null)this.workspaceDialog.dismiss();launchWorkspaceFolderPicker();
        }),matchWrap());
        scroll.addView(content,matchWrap());
        return scroll;
    }

    private void refreshFilesRail() {
        if (this.editorShell == null) return;
        if (this.filesRail != null) {
            this.editorShell.removeView(this.filesRail);
            this.filesRail = null;
        }
        if (this.filesRailDivider != null) {
            this.editorShell.removeView(this.filesRailDivider);
            this.filesRailDivider = null;
        }
        // Use available window width, never the phone model. Focus keeps editing full width.
        if (!this.filesRailRequested || this.focusMode
                || getResources().getConfiguration().screenWidthDp < 840) return;
        this.filesRail = buildFilesNavigatorView();
        this.filesRail.setBackgroundColor(this.palette.surface);
        this.filesRail.setContentDescription("Files navigation");
        this.editorShell.addView(this.filesRail, 0,
                new LinearLayout.LayoutParams(dp(280), -1));
        this.filesRailDivider = new View(this);
        this.filesRailDivider.setBackgroundColor(this.palette.border);
        this.editorShell.addView(this.filesRailDivider, 1,
                new LinearLayout.LayoutParams(dp(1), -1));
    }

    private void showFilesNavigator() {
        if(isFinishing()||isDestroyed())return;
        if (!this.focusMode && getResources().getConfiguration().screenWidthDp >= 840) {
            this.filesRailRequested = !this.filesRailRequested;
            refreshFilesRail();
            return;
        }
        if(this.workspaceDialog!=null)this.workspaceDialog.dismiss();
        ScrollView scroll=buildFilesNavigatorView();
        final AlertDialog dialog=historyDialogBuilder("Files")
            .setView(scroll)
            .setNegativeButton("Back to editor",null).create();
        this.workspaceDialog=dialog;
        dialog.setOnDismissListener(ignored->{if(this.workspaceDialog==dialog)this.workspaceDialog=null;});
        dialog.setOnShowListener(ignored->workspaceDialogShown(dialog));
        dialog.show();
    }

    private void showWorkspace() {
        ScrollView scrollView = new ScrollView(this);
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(MODE_PREVIEW);
        linearLayout.setPadding(dp(22), dp(8), dp(22), dp(MAX_TABS));
        TextView textView = new TextView(this);
        textView.setText("Your writing desk");
        textView.setTextSize(26.0f);
        textView.setTypeface(Typeface.create("sans-serif", MODE_PREVIEW));
        textView.setTextColor(this.palette.text);
        TextView textView2 = new TextView(this);
        textView2.setText("Pick up where you left off, begin from a template, or open any Markdown file on your device.");
        textView2.setTextSize(15.0f);
        textView2.setLineSpacing(dp(3), 1.0f);
        textView2.setTextColor(this.palette.muted);
        textView2.setPadding(MODE_EDIT, dp(6), MODE_EDIT, dp(18));
        linearLayout.addView(textView, matchWrap());
        linearLayout.addView(textView2, matchWrap());
        linearLayout.addView(workspaceAction("＋  New document", "Blank page or template", new View.OnClickListener() { // from class: com.example.markdowndesk.MainActivity$$ExternalSyntheticLambda72
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.lambda$showWorkspace$51(view);
            }
        }), matchWrap());
        linearLayout.addView(workspaceAction("↗  Open a file", "Local storage or a document provider", new View.OnClickListener() { // from class: com.example.markdowndesk.MainActivity$$ExternalSyntheticLambda73
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.lambda$showWorkspace$52(view);
            }
        }), matchWrap());
        addWorkspaceFolderActions(linearLayout);
        linearLayout.addView(workspaceAction("Export this document as DOCX", "New copy · DOCX source up to 1 MiB; native text up to 8 MiB", view -> {
            if (this.workspaceDialog != null) this.workspaceDialog.dismiss();
            startDocxExport();
        }), matchWrap());
        linearLayout.addView(workspaceAction("↶  Protected saves", "Originals and edited versions · restore to a new copy", view -> {
            if (this.workspaceDialog != null) this.workspaceDialog.dismiss();
            showProtectedSaves();
        }), matchWrap());
        linearLayout.addView(workspaceAction("Protected storage status","Read current history storage usage without changing or deleting files",view->{
            if(this.workspaceDialog!=null)this.workspaceDialog.dismiss();
            showProtectionUsage();
        }),matchWrap());
        linearLayout.addView(workspaceAction("History for this document", "Named versions, comparison and restore to a new copy", view -> {
            if (this.workspaceDialog != null) this.workspaceDialog.dismiss();
            showDocumentHistory();
        }), matchWrap());
        linearLayout.addView(workspaceAction("☆ Bookmark this spot","Name your cursor position in Write or Split",view->{
            if(this.workspaceDialog!=null)this.workspaceDialog.dismiss();
            promptReaderBookmark();
        }),matchWrap());
        linearLayout.addView(workspaceAction("↪ Go to bookmark","Tap a saved bookmark to jump straight to its text",view->{
            if(this.workspaceDialog!=null)this.workspaceDialog.dismiss();
            showReaderPlaces();
        }),matchWrap());
        TextView historyLimit = new TextView(this);
        historyLimit.setText("Markdown and text files use UTF-8, up to 8 MiB. Current text files do not include private history. Uninstalling the app or clearing its storage can lose that history. Keep separate copies of text you need.");
        historyLimit.setTextColor(this.palette.muted);
        historyLimit.setTextSize(13.0f);
        historyLimit.setPadding(dp(4), dp(8), dp(4), dp(12));
        linearLayout.addView(historyLimit, matchWrap());
        List<RecentStore.Item> list = RecentStore.read(this);
        if (!list.isEmpty()) {
            TextView textView3 = new TextView(this);
            textView3.setText("RECENT");
            textView3.setLetterSpacing(0.14f);
            textView3.setTextSize(11.0f);
            textView3.setTextColor(UiTheme.readableForeground(this.palette.accent, this.palette.raised));
            textView3.setPadding(dp(4), dp(22), MODE_EDIT, dp(8));
            linearLayout.addView(textView3, matchWrap());
            for (final RecentStore.Item item : list) {
                linearLayout.addView(workspaceAction(item.name, "Tap to reopen", new View.OnClickListener() { // from class: com.example.markdowndesk.MainActivity$$ExternalSyntheticLambda74
                    @Override // android.view.View.OnClickListener
                    public final void onClick(View view) {
                        MainActivity.this.lambda$showWorkspace$53(item, view);
                    }
                }), matchWrap());
            }
        }
        scrollView.addView(linearLayout, matchWrap());
        this.workspaceDialog = new AlertDialog.Builder(this).setView(scrollView).setNegativeButton("Close", (DialogInterface.OnClickListener) null).create();
        this.workspaceDialog.setOnShowListener(new DialogInterface.OnShowListener() { // from class: com.example.markdowndesk.MainActivity$$ExternalSyntheticLambda75
            @Override // android.content.DialogInterface.OnShowListener
            public final void onShow(DialogInterface dialogInterface) {
                MainActivity.this.lambda$showWorkspace$54(dialogInterface);
            }
        });
        this.workspaceDialog.show();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$showWorkspace$51(View view) {
        if (this.workspaceDialog != null) {
            this.workspaceDialog.dismiss();
        }
        showNewDocumentMenu();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$showWorkspace$52(View view) {
        if (this.workspaceDialog != null) {
            this.workspaceDialog.dismiss();
        }
        launchOpen();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$showWorkspace$53(RecentStore.Item item, View view) {
        if (this.workspaceDialog != null) {
            this.workspaceDialog.dismiss();
        }
        openDocument(Uri.parse(item.uri), 3, true);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$showWorkspace$54(DialogInterface dialogInterface) {
        styleDialog(this.workspaceDialog);
    }

    private Button workspaceAction(String str, String str2, View.OnClickListener onClickListener) {
        Button buttonCreateButton = createButton(str + "\n" + str2, onClickListener);
        buttonCreateButton.setGravity(8388627);
        buttonCreateButton.setTextSize(15.0f);
        buttonCreateButton.setAllCaps(false);
        buttonCreateButton.setPadding(dp(16), dp(8), dp(16), dp(8));
        buttonCreateButton.setMinHeight(dp(64));
        styleButton(buttonCreateButton, false, false);
        // Ephemeral Files/dialog rows should not accumulate in the persistent toolbar registry.
        this.buttons.remove(buttonCreateButton);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(-1, -2);
        layoutParams.setMargins(MODE_EDIT, MODE_EDIT, MODE_EDIT, dp(8));
        buttonCreateButton.setLayoutParams(layoutParams);
        return buttonCreateButton;
    }

    private void showNewDocumentMenu() {
        final String[] strArr = {"Blank document", "Meeting notes", "Daily journal", "Project README", "Checklist"};
        String str = new SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()).format(new Date());
        final String[] strArr2 = {"", "# Meeting notes\n\n**Date:** " + str + "\n\n## Attendees\n\n- \n\n## Agenda\n\n- \n\n## Decisions\n\n- \n\n## Action items\n\n- [ ] ", "# " + str + "\n\n## Today\n\n\n## Notes\n\n\n## Tomorrow\n\n- [ ] ", "# Project name\n\nA short description of the project.\n\n## Getting started\n\n```\n\n```\n\n## Usage\n\n\n## License\n\n", "# Checklist\n\n- [ ] First item\n- [ ] Second item\n- [ ] Third item\n"};
        new AlertDialog.Builder(this).setTitle("Start something").setItems(strArr, new DialogInterface.OnClickListener() { // from class: com.example.markdowndesk.MainActivity$$ExternalSyntheticLambda20
            @Override // android.content.DialogInterface.OnClickListener
            public final void onClick(DialogInterface dialogInterface, int i) {
                MainActivity.this.lambda$showNewDocumentMenu$55(strArr, strArr2, dialogInterface, i);
            }
        }).setNegativeButton("Cancel", (DialogInterface.OnClickListener) null).show();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$showNewDocumentMenu$55(String[] strArr, String[] strArr2, DialogInterface dialogInterface, int i) {
        createTemplateDocument(strArr[i], strArr2[i]);
    }

    private void createTemplateDocument(String str, String str2) {
        String str3;
        if (this.tabs.size() >= MAX_TABS) {
            Toast.makeText(this, "Close a tab before opening another.", MODE_PREVIEW).show();
            return;
        }
        captureActiveTab();
        DocumentTab documentTab = new DocumentTab();
        documentTab.mode = MODE_EDIT;
        documentTab.text = str2;
        documentTab.dirty = !str2.isEmpty();
        if ("Blank document".equals(str)) {
            str3 = "Untitled.md";
        } else {
            str3 = str.replace(" ", "-").toLowerCase(Locale.ROOT) + ".md";
        }
        documentTab.displayName = str3;
        int length = str2.length();
        documentTab.selectionEnd = length;
        documentTab.selectionStart = length;
        this.tabs.add(documentTab);
        this.activeTab = this.tabs.size() - MODE_PREVIEW;
        restoreActiveTab(true);
        updateTabsUi();
        scheduleSession();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void showOverflow(View view) {
        PopupMenu popupMenu = new PopupMenu(this, view);
        popupMenu.getMenu().add(MODE_EDIT, MODE_PREVIEW, MODE_EDIT, "New document");
        popupMenu.getMenu().add(MODE_EDIT, MODE_SPLIT, MODE_PREVIEW, "Open file");
        popupMenu.getMenu().add(MODE_EDIT, 3, MODE_SPLIT, "Recent files");
        popupMenu.getMenu().add(MODE_EDIT, 4, 3, "Document outline");
        popupMenu.getMenu().add(MODE_EDIT, 5, 4, "Find and replace");
        popupMenu.getMenu().add(MODE_EDIT, 6, 5, "Command palette");

        SubMenu viewMenu = popupMenu.getMenu().addSubMenu("View and appearance");
        viewMenu.add(MODE_EDIT, 9, 0, this.focusMode ? "Exit focus mode" : "Focus mode");
        viewMenu.add(MODE_EDIT, 10, 1, "Appearance");
        viewMenu.add(MODE_EDIT, 16, 2, "Splash images");
        viewMenu.add(MODE_EDIT, 11, 3, "Writing goal");

        SubMenu exportMenu = popupMenu.getMenu().addSubMenu("Share and export");
        exportMenu.add(MODE_EDIT, MAX_TABS, 0, "Share Markdown");
        exportMenu.add(MODE_EDIT, 13, 1, "Share rendered HTML");
        exportMenu.add(MODE_EDIT, 14, 2, "Print / Save PDF");
        exportMenu.add(MODE_EDIT, 17, 3, "Export DOCX");

        popupMenu.getMenu().add(MODE_EDIT, 18, 19, "Tools and recovery");
        popupMenu.getMenu().add(MODE_EDIT, 8, 20, "Save as");
        popupMenu.getMenu().add(MODE_EDIT, 7, 21, "Guide and shortcuts");
        popupMenu.getMenu().add(MODE_EDIT, 15, 22, "Close document");
        popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override public boolean onMenuItemClick(MenuItem menuItem) {
                return MainActivity.this.lambda$showOverflow$56(menuItem);
            }
        });
        popupMenu.show();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ boolean lambda$showOverflow$56(MenuItem menuItem) {
        return runMenuAction(menuItem.getItemId());
    }

    private boolean runMenuAction(int i) {
        switch (i) {
            case MODE_PREVIEW /* 1 */:
                showNewDocumentMenu();
                return true;
            case MODE_SPLIT /* 2 */:
                launchOpen();
                return true;
            case 3:
                showRecents();
                return true;
            case 4:
                showOutline();
                return true;
            case 5:
                showFindReplace();
                return true;
            case 6:
                showCommandPalette();
                return true;
            case 7:
                showTutorial();
                return true;
            case 8:
                launchSaveAs();
                return true;
            case 9:
                toggleFocusMode();
                return true;
            case 10:
                showAppearance();
                return true;
            case 11:
                showWordGoal();
                return true;
            case MAX_TABS /* 12 */:
                shareDocument(false);
                return true;
            case 13:
                shareDocument(true);
                return true;
            case 14:
                printDocument();
                return true;
            case 15:
                closeCurrentTab();
                return true;
            case 16:
                showSplashGallery();
                return true;
            case 17:
                startDocxExport();
                return true;
            case 18:
                showWorkspace();
                return true;
            default:
                return false;
        }
    }

    private void showTutorial() {
        hideKeyboard();
        this.main.post(new Runnable() { // from class: com.example.markdowndesk.MainActivity$$ExternalSyntheticLambda9
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.this.lambda$showTutorial$57();
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$showTutorial$57() {
        if (isFinishing()) {
            return;
        }
        showTutorialPanel();
    }

    private void showTutorialPanel() {
        String[] strArr = {"The writing desk", "Tabs and documents", "Formatting buttons", "Write, Read, and Split", "Outline and search", "Focus, typewriter, and text size", "Saving and recovery", "Sharing and PDF", "Keyboard shortcuts", "Splash images"};
        String[] strArr2 = {"Tap Files to browse recent documents, Favorites and saved folders, open a file, or switch tabs. New document offers writing templates. SAVE changes its label to show whether the document is saved, dirty, read-only, or currently saving.", "Every open document appears in the tab strip. Tap a tab to switch to it. A dot marks unsaved changes. Long-press a tab to close it. The + tab creates a new document, and the app restores each tab's cursor, scroll position, mode, and dirty state after a restart.", "The bottom dock inserts Markdown without changing how your source is stored. H1 creates a heading; B, I, and S wrap selected text; •, ☐, and ❯ start lists, tasks, and quotes; ↗ inserts a link; </> inserts inline or fenced code; ▦ creates a table; — creates a divider; Today inserts the date.", "Write shows the source editor. Read shows the rendered page. Split shows both and keeps their positions synchronized. Tap a paragraph in Read or Split to jump to its source. Ordinary web links still open in your browser.", "Outline lists every real Markdown heading and jumps to the selected section. Find supports ordinary, case-sensitive, whole-word, and regular-expression searches. Matches highlight live in the document; Replace and Replace all support regex capture groups.", "Focus removes secondary chrome so the page can breathe. Typewriter mode keeps the active line near the center. Source and Read keep separate text sizes. Pinch in the editor to change source size, or pinch in Read to change reading size. Read also has A− and A+ controls, and Appearance exposes both sizes. Each choice is remembered.", "The app preserves UTF-8, Unicode, line endings, trailing whitespace, and an existing BOM. Saves are read back and verified. If another app changes the file, Markdown Desk stops before overwriting it. Dirty drafts and open tabs are recovered after interruption or restart.", "Share Markdown sends the original source. Share rendered HTML sends the styled document. Print / Save PDF opens Android's print service, where you can print or choose Save as PDF.", "Ctrl+S saves · Ctrl+O opens · Ctrl+N creates · Ctrl+F finds · Ctrl+P toggles Read · Ctrl+Shift+P opens the command palette · Ctrl+B bolds · Ctrl+I italicizes · Ctrl+K inserts a link.", "The app opens with a splash image drawn at random from the ones that are switched on, and it avoids repeating the last one you saw. Splash images under Appearance, or Splash images in the ••• menu, lists every image: tap one to switch it on or off, use ✕ to remove it, and Add image… to bring in your own from anywhere on your device. Imported images are copied into the app, so they keep working after the original moves. Images that came with the app can be brought back with Restore removed app images. Show launch splash under Appearance still turns the whole thing off."};
        StringBuilder sb = new StringBuilder("Everything in one place. You can reopen this guide from More > Guide and shortcuts.\n\n");
        int i = MODE_EDIT;
        while (i < strArr.length) {
            sb.append(strArr[i]).append("\n").append(strArr2[i]);
            i += MODE_PREVIEW;
            if (i < strArr.length) {
                sb.append("\n\n");
            }
        }
        TextView textView = new TextView(this);
        textView.setText(sb);
        textView.setTextSize(16.0f);
        textView.setTextColor(this.palette.text);
        textView.setLineSpacing(dp(5), 1.0f);
        textView.setPadding(dp(6), dp(MODE_SPLIT), dp(6), dp(8));
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setPadding(dp(18), dp(4), dp(18), dp(MODE_SPLIT));
        scrollView.addView(textView, matchWrap());
        final AlertDialog alertDialogCreate = new AlertDialog.Builder(this).setTitle("Markdown Desk guide").setView(scrollView).setPositiveButton("Done", (DialogInterface.OnClickListener) null).create();
        alertDialogCreate.setOnShowListener(new DialogInterface.OnShowListener() { // from class: com.example.markdowndesk.MainActivity$$ExternalSyntheticLambda50
            @Override // android.content.DialogInterface.OnShowListener
            public final void onShow(DialogInterface dialogInterface) {
                MainActivity.this.lambda$showTutorialPanel$58(alertDialogCreate, dialogInterface);
            }
        });
        alertDialogCreate.show();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$showTutorialPanel$58(AlertDialog alertDialog, DialogInterface dialogInterface) {
        styleDialog(alertDialog);
    }

    private void showCommandPalette() {
        new AlertDialog.Builder(this).setTitle("Command palette").setItems(new String[]{"New document", "Open file", "Save", "Switch document", "Document outline", "Find and replace", "Write mode", "Read mode", "Split mode", "Focus mode", "Appearance", "Splash images", "Increase text size", "Decrease text size", "Insert table", "Print / Save PDF", "Export DOCX"}, new DialogInterface.OnClickListener() { // from class: com.example.markdowndesk.MainActivity$$ExternalSyntheticLambda76
            @Override // android.content.DialogInterface.OnClickListener
            public final void onClick(DialogInterface dialogInterface, int i) {
                MainActivity.this.lambda$showCommandPalette$59(dialogInterface, i);
            }
        }).setNegativeButton("Close", (DialogInterface.OnClickListener) null).show();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$showCommandPalette$59(DialogInterface dialogInterface, int i) {
        int i2 = MODE_PREVIEW;
        switch (i) {
            case MODE_EDIT /* 0 */:
                showNewDocumentMenu();
                break;
            case MODE_PREVIEW /* 1 */:
                launchOpen();
                break;
            case MODE_SPLIT /* 2 */:
                saveDocument();
                break;
            case 3:
                showTabs();
                break;
            case 4:
                showOutline();
                break;
            case 5:
                showFindReplace();
                break;
            case 6:
                setMode(MODE_EDIT, true);
                break;
            case 8:
                i2 = MODE_SPLIT;
            case 7:
                setMode(i2, false);
                break;
            case 9:
                toggleFocusMode();
                break;
            case 10:
                showAppearance();
                break;
            case 11:
                showSplashGallery();
                break;
            case MAX_TABS /* 12 */:
                changeActiveFont(MODE_PREVIEW);
                break;
            case 13:
                changeActiveFont(-1);
                break;
            case 14:
                insertTable();
                break;
            case 15:
                printDocument();
                break;
            case 16:
                startDocxExport();
                break;
        }
    }

    /** The live Appearance dialog owns its controls; dismissed controls must not be retained
     * in the activity-wide toolbar button list. */
    private static final class AppearanceUi {
        AlertDialog dialog;
        TextView title;
        TextView description;
        TextView sourceLabel;
        TextView readLabel;
        Button linesButton;
        Button typewriterButton;
        Button splashButton;
        Button galleryButton;
        TextView typographyHeading;
        final String[] readerFamilyNames={"Serif","Sans serif","Monospace"};
        final Button[] readerFamilyButtons=new Button[3], readerMinus=new Button[4], readerPlus=new Button[4];
        final TextView[] readerLabels=new TextView[4];
        final String[] themeNames = {"Paper", "Midnight", "Sepia", "Forest", "Bubblegum", "Amethyst", "Black Rose", "Ocean", "Arcade"};
        final Button[] themeButtons = new Button[9];
        final List<Button> buttons = new ArrayList<>();
    }

    private ReaderTypography readReaderTypography() {
        Map<String,?> saved=this.preferences.getAll();
        Object family=saved.get(PREF_READER_FAMILY), line=saved.get(PREF_READER_LINE), gap=saved.get(PREF_READER_GAP),
                margin=saved.get(PREF_READER_MARGIN), measure=saved.get(PREF_READER_MEASURE);
        return ReaderTypography.fromValues(family instanceof String?(String)family:null,
                line instanceof Number?((Number)line).floatValue():Float.NaN,
                gap instanceof Number?((Number)gap).floatValue():Float.NaN,
                margin instanceof Integer?(Integer)margin:0, measure instanceof Integer?(Integer)measure:0);
    }

    private void updateReaderTypography(ReaderTypography value, AppearanceUi ui) {
        this.readerTypography=value;
        this.preferences.edit().putString(PREF_READER_FAMILY,value.family.name()).putFloat(PREF_READER_LINE,value.lineHeight)
                .putFloat(PREF_READER_GAP,value.paragraphGapEm).putInt(PREF_READER_MARGIN,value.horizontalMarginPx)
                .putInt(PREF_READER_MEASURE,value.measurePx).apply();
        renderPreview();
        refreshAppearance(ui);
    }

    private void adjustReaderTypography(int field, int delta, AppearanceUi ui) {
        ReaderTypography next=this.readerTypography;
        switch(field) {
            case 0: next=next.withLineHeight(Math.max(1.2f,Math.min(2.2f,Math.round((next.lineHeight+delta*.1f)*100f)/100f)));break;
            case 1: next=next.withParagraphGapEm(Math.max(.5f,Math.min(1.5f,Math.round((next.paragraphGapEm+delta*.1f)*100f)/100f)));break;
            case 2: next=next.withHorizontalMarginPx(Math.max(12,Math.min(44,next.horizontalMarginPx+delta*4)));break;
            case 3: next=next.withMeasurePx(Math.max(480,Math.min(960,next.measurePx+delta*40)));break;
            default: return;
        }
        updateReaderTypography(next,ui);
    }

    private void addReaderTypographyControls(AppearanceUi ui, LinearLayout parent) {
        ui.typographyHeading=new TextView(this);
        ui.typographyHeading.setText("Reading layout");ui.typographyHeading.setTextSize(16f);
        ui.typographyHeading.setPadding(dp(4),dp(16),dp(4),dp(4));parent.addView(ui.typographyHeading,matchWrap());
        for(int n=0;n<ui.readerFamilyButtons.length;n++) {
            final ReaderTypography.Family family=ReaderTypography.Family.values()[n];
            ui.readerFamilyButtons[n]=appearanceButton(ui,ui.readerFamilyNames[n],ui.readerFamilyNames[n],
                    view -> updateReaderTypography(readerTypography.withFamily(family),ui));
            parent.addView(ui.readerFamilyButtons[n],matchWrap());
        }
        String[] names={"line spacing","paragraph spacing","side margins","width"};
        for(int n=0;n<4;n++) {
            final int field=n;ui.readerLabels[n]=new TextView(this);
            ui.readerLabels[n].setPadding(dp(4),dp(10),dp(4),dp(4));parent.addView(ui.readerLabels[n],matchWrap());
            LinearLayout row=new LinearLayout(this);row.setOrientation(MODE_EDIT);
            ui.readerMinus[n]=appearanceButton(ui,"−","Decrease reading "+names[n],view -> adjustReaderTypography(field,-1,ui));
            ui.readerPlus[n]=appearanceButton(ui,"+","Increase reading "+names[n],view -> adjustReaderTypography(field,1,ui));
            row.addView(ui.readerMinus[n],new LinearLayout.LayoutParams(MODE_EDIT,dp(44),1f));
            row.addView(ui.readerPlus[n],new LinearLayout.LayoutParams(MODE_EDIT,dp(44),1f));parent.addView(row,matchWrap());
        }
        parent.addView(appearanceButton(ui,"Reset reading layout","Reset reading layout to defaults",
                view -> updateReaderTypography(ReaderTypography.defaults(),ui)),matchWrap());
    }

    private void refreshReaderTypography(AppearanceUi ui) {
        ui.typographyHeading.setTextColor(this.palette.text);
        for(int n=0;n<ui.readerFamilyButtons.length;n++) {
            Button button=ui.readerFamilyButtons[n];boolean selected=this.readerTypography.family==ReaderTypography.Family.values()[n];
            button.setText((selected?"●  ":"○  ")+ui.readerFamilyNames[n]);
            setAppearanceSelection(button,ui.readerFamilyNames[n],selected);
            button.setBackground(UiTheme.rounded(selected?this.palette.accentSoft:this.palette.surface,dp(13),selected?this.palette.accent:this.palette.border,1));
        }
        ui.readerLabels[0].setText(String.format(java.util.Locale.ROOT,"Line spacing · %.2f×",this.readerTypography.lineHeight));
        ui.readerLabels[1].setText(String.format(java.util.Locale.ROOT,"Paragraph spacing · %.2f em",this.readerTypography.paragraphGapEm));
        ui.readerLabels[2].setText("Side margins · "+this.readerTypography.horizontalMarginPx+" px");
        ui.readerLabels[3].setText("Reading width · "+this.readerTypography.measurePx+" px");
        boolean[] minus={readerTypography.lineHeight>1.2f,readerTypography.paragraphGapEm>.5f,readerTypography.horizontalMarginPx>12,readerTypography.measurePx>480};
        boolean[] plus={readerTypography.lineHeight<2.2f,readerTypography.paragraphGapEm<1.5f,readerTypography.horizontalMarginPx<44,readerTypography.measurePx<960};
        for(int n=0;n<4;n++) {
            ui.readerLabels[n].setTextColor(this.palette.muted);
            ui.readerMinus[n].setEnabled(minus[n]);ui.readerMinus[n].setAlpha(minus[n]?1f:.4f);
            ui.readerPlus[n].setEnabled(plus[n]);ui.readerPlus[n].setAlpha(plus[n]?1f:.4f);
        }
    }

    private Button appearanceButton(AppearanceUi ui, String label, View.OnClickListener listener) {
        Button button = createButton(label, listener);
        // The Activity's global applyColors() list is for persistent chrome only.
        // Dialog controls are owned and refreshed by their dialog instead.
        this.buttons.remove(button);
        ui.buttons.add(button);
        return button;
    }

    private Button appearanceButton(AppearanceUi ui, String label, String description, View.OnClickListener listener) {
        Button button = appearanceButton(ui, label, listener);
        button.setContentDescription(description);
        button.setTooltipText(description);
        return button;
    }

    /** Only actual Appearance choices expose checked state; action buttons keep their semantics. */
    private void setAppearanceSelection(Button button, String label, boolean selected) {
        button.setSelected(selected);
        button.setContentDescription(label);
        button.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.setCheckable(true);
                info.setChecked(host.isSelected());
            }
        });
    }

    /** Refresh the *existing* window, not just the main activity behind it. */
    private void refreshAppearance(AppearanceUi ui) {
        final UiTheme.Palette colors = this.palette;
        ui.title.setText("Appearance · " + colors.name);
        ui.title.setTextColor(colors.text);
        ui.description.setTextColor(colors.muted);
        ui.sourceLabel.setTextColor(colors.muted);
        ui.readLabel.setTextColor(colors.muted);
        ui.sourceLabel.setText("Source text · " + Math.round(this.fontSp) + " sp");
        ui.readLabel.setText("Reading text · " + Math.round(this.previewFontSp) + " sp");
        for (Button button : ui.buttons) {
            // Always use readable foreground/background pairs, even during live changes.
            button.setTextColor(colors.text);
            button.setBackground(UiTheme.rounded(colors.surface, dp(13), colors.border, 1));
        }
        for (int index = 0; index < ui.themeButtons.length; index++) {
            Button button = ui.themeButtons[index];
            boolean selected = index == this.themeId;
            button.setText((selected ? "●  " : "○  ") + ui.themeNames[index]);
            setAppearanceSelection(button, ui.themeNames[index], selected);
            button.setBackground(UiTheme.rounded(selected ? colors.accentSoft : colors.surface,
                    dp(13), selected ? colors.accent : colors.border, 1));
        }
        ui.linesButton.setText((this.lineNumbers ? "✓  " : "○  ") + "Line numbers");
        ui.typewriterButton.setText((this.typewriterMode ? "✓  " : "○  ") + "Typewriter mode");
        ui.splashButton.setText((this.launchSplashEnabled ? "✓  " : "○  ") + "Show launch splash");
        setAppearanceSelection(ui.linesButton, "Line numbers", this.lineNumbers);
        setAppearanceSelection(ui.typewriterButton, "Typewriter mode", this.typewriterMode);
        setAppearanceSelection(ui.splashButton, "Show launch splash", this.launchSplashEnabled);
        ui.galleryButton.setText("⋯  Splash images (" + SplashStore.enabled(this).size() + " on)");
        refreshReaderTypography(ui);
        if (ui.dialog != null && ui.dialog.isShowing()) {
            styleDialog(ui.dialog);
        }
    }

    private void showAppearance() {
        final AppearanceUi ui = new AppearanceUi();
        String[] strArr = ui.themeNames;
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(MODE_PREVIEW);
        linearLayout.setPadding(dp(20), dp(8), dp(20), MODE_EDIT);
        TextView textView = new TextView(this);
        ui.description = textView;
        textView.setText("Choose a complete reading and writing atmosphere.");
        textView.setTextColor(this.palette.muted);
        textView.setTextSize(14.0f);
        textView.setPadding(MODE_EDIT, MODE_EDIT, MODE_EDIT, dp(MAX_TABS));
        linearLayout.addView(textView, matchWrap());
        int themeIndex = MODE_EDIT;
        while (true) {
            final int i = themeIndex;
            String str = "○  ";
            if (i >= strArr.length) {
                break;
            }
            if (i == this.themeId) {
                str = "●  ";
            }
            Button buttonCreateButton = appearanceButton(ui, str + strArr[i], new View.OnClickListener() {
                @Override public void onClick(View view) {
                    MainActivity.this.lambda$showAppearance$60(i, view);
                    MainActivity.this.refreshAppearance(ui);
                }
            });
            ui.themeButtons[i] = buttonCreateButton;
            buttonCreateButton.setGravity(8388627);
            linearLayout.addView(buttonCreateButton, matchWrap());
            themeIndex = i + MODE_PREVIEW;
        }
        ui.linesButton = appearanceButton(ui, (this.lineNumbers ? "✓  " : "○  ") + "Line numbers", new View.OnClickListener() {
            @Override public void onClick(View view) {
                MainActivity.this.lambda$showAppearance$61(view);
                MainActivity.this.refreshAppearance(ui);
            }
        });
        linearLayout.addView(ui.linesButton, matchWrap());
        ui.typewriterButton = appearanceButton(ui, (this.typewriterMode ? "✓  " : "○  ") + "Typewriter mode", new View.OnClickListener() {
            @Override public void onClick(View view) {
                MainActivity.this.lambda$showAppearance$62(view);
                MainActivity.this.refreshAppearance(ui);
            }
        });
        linearLayout.addView(ui.typewriterButton, matchWrap());
        final Button buttonCreateButton2 = appearanceButton(ui, (this.launchSplashEnabled ? "✓  " : "○  ") + "Show launch splash", null);
        ui.splashButton = buttonCreateButton2;
        buttonCreateButton2.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                MainActivity.this.lambda$showAppearance$63(buttonCreateButton2, view);
                MainActivity.this.refreshAppearance(ui);
            }
        });
        linearLayout.addView(buttonCreateButton2, matchWrap());
        Button splashGalleryButton = appearanceButton(ui, "⋯  Splash images ("
                + SplashStore.enabled(this).size() + " on)", new View.OnClickListener() {
            @Override public void onClick(View view) {
                MainActivity.this.showSplashGallery();
            }
        });
        ui.galleryButton = splashGalleryButton;
        splashGalleryButton.setGravity(8388627);
        linearLayout.addView(splashGalleryButton, matchWrap());
        TextView sourceLabel = new TextView(this);
        ui.sourceLabel = sourceLabel;
        sourceLabel.setText("Source text · " + Math.round(this.fontSp) + " sp");
        sourceLabel.setTextColor(this.palette.muted);
        sourceLabel.setPadding(dp(4), dp(10), dp(4), dp(4));
        linearLayout.addView(sourceLabel, matchWrap());
        LinearLayout sourceRow = new LinearLayout(this);
        sourceRow.setOrientation(MODE_EDIT);
        sourceRow.addView(appearanceButton(ui, "A−", "Decrease source text size", new View.OnClickListener() { @Override public void onClick(View v) { changeFont(-1); sourceLabel.setText("Source text · " + Math.round(fontSp) + " sp"); } }), new LinearLayout.LayoutParams(MODE_EDIT, dp(44), 1.0f));
        sourceRow.addView(appearanceButton(ui, "A+", "Increase source text size", new View.OnClickListener() { @Override public void onClick(View v) { changeFont(1); sourceLabel.setText("Source text · " + Math.round(fontSp) + " sp"); } }), new LinearLayout.LayoutParams(MODE_EDIT, dp(44), 1.0f));
        linearLayout.addView(sourceRow, matchWrap());

        TextView readLabel = new TextView(this);
        ui.readLabel = readLabel;
        readLabel.setText("Reading text · " + Math.round(this.previewFontSp) + " sp");
        readLabel.setTextColor(this.palette.muted);
        readLabel.setPadding(dp(4), dp(10), dp(4), dp(4));
        linearLayout.addView(readLabel, matchWrap());
        LinearLayout readRow = new LinearLayout(this);
        readRow.setOrientation(MODE_EDIT);
        readRow.addView(appearanceButton(ui, "A−", "Decrease reading text size", new View.OnClickListener() { @Override public void onClick(View v) { changePreviewFont(-1); readLabel.setText("Reading text · " + Math.round(previewFontSp) + " sp"); } }), new LinearLayout.LayoutParams(MODE_EDIT, dp(44), 1.0f));
        readRow.addView(appearanceButton(ui, "A+", "Increase reading text size", new View.OnClickListener() { @Override public void onClick(View v) { changePreviewFont(1); readLabel.setText("Reading text · " + Math.round(previewFontSp) + " sp"); } }), new LinearLayout.LayoutParams(MODE_EDIT, dp(44), 1.0f));
        linearLayout.addView(readRow, matchWrap());
        linearLayout.addView(appearanceButton(ui, "Reset text sizes", new View.OnClickListener() { @Override public void onClick(View v) {
            fontSp = 18f; previewFontSp = 18f; editor.setTextSize(fontSp); preview.setTextSp(previewFontSp);
            preferences.edit().putFloat(PREF_FONT, fontSp).putFloat(PREF_PREVIEW_FONT, previewFontSp).apply();
            sourceLabel.setText("Source text · 18 sp"); readLabel.setText("Reading text · 18 sp"); if (mode != MODE_EDIT) renderPreview();
        } }), matchWrap());
        addReaderTypographyControls(ui,linearLayout);
        // Keep every setting reachable on cover displays, in landscape, and
        // with larger system fonts. Done stays outside scrolling content.
        ScrollView appearanceScroll = new ScrollView(this);
        appearanceScroll.setFillViewport(true);
        appearanceScroll.addView(linearLayout, matchWrap());

        TextView appearanceTitle = new TextView(this);
        ui.title = appearanceTitle;
        appearanceTitle.setText("Appearance · " + this.palette.name);
        appearanceTitle.setTextSize(20f);
        appearanceTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        appearanceTitle.setPadding(dp(24), dp(20), dp(24), dp(6));
        final AlertDialog appearanceDialog = new AlertDialog.Builder(this)
                .setCustomTitle(appearanceTitle)
                .setView(appearanceScroll)
                .setPositiveButton("Done", (DialogInterface.OnClickListener) null)
                .create();
        ui.dialog = appearanceDialog;
        appearanceDialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override public void onShow(DialogInterface dialog) {
                MainActivity.this.refreshAppearance(ui);
            }
        });
        appearanceDialog.show();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$showAppearance$60(int i, View view) {
        this.themeId = i;
        this.preferences.edit().putInt(PREF_THEME, this.themeId).apply();
        applyColors();
        Toast.makeText(this, UiTheme.palette(this.themeId, systemDark()).name + " theme", MODE_EDIT).show();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$showAppearance$61(View view) {
        this.lineNumbers = !this.lineNumbers;
        this.preferences.edit().putBoolean(PREF_LINES, this.lineNumbers).apply();
        applyColors();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$showAppearance$62(View view) {
        toggleTypewriter();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$showAppearance$63(Button button, View view) {
        this.launchSplashEnabled = !this.launchSplashEnabled;
        this.preferences.edit().putBoolean(PREF_LAUNCH_SPLASH, this.launchSplashEnabled).apply();
        button.setText((this.launchSplashEnabled ? "✓  " : "○  ") + "Show launch splash");
        Toast.makeText(this, this.launchSplashEnabled ? "Launch splash on" : "Launch splash off", MODE_EDIT).show();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$showAppearance$64(View view) {
        changeFont(-1);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$showAppearance$65(View view) {
        changeFont(MODE_PREVIEW);
    }

    private void toggleFocusMode() {
        this.focusMode = !this.focusMode;
        this.tabsScroll.setVisibility(this.focusMode ? 8 : MODE_EDIT);
        this.modeBar.setVisibility(this.focusMode ? 8 : MODE_EDIT);
        this.eyebrowView.setVisibility(this.focusMode ? 8 : MODE_EDIT);
        this.statusView.setVisibility(this.focusMode ? 8 : MODE_EDIT);
        this.formatScroll.setVisibility((this.focusMode || this.mode == MODE_PREVIEW) ? View.GONE : View.VISIBLE);
        if (this.readToolsScroll != null) this.readToolsScroll.setVisibility((!this.focusMode && this.mode == MODE_PREVIEW) ? View.VISIBLE : View.GONE);
        this.header.setPadding(dp(14), this.focusMode ? dp(4) : dp(8), dp(14), this.focusMode ? dp(4) : dp(8));
        refreshFilesRail();
        updateModeChrome();
        Toast.makeText(this, this.focusMode ? "Focus mode" : "Focus mode off", MODE_EDIT).show();
    }

    private void toggleTypewriter() {
        this.typewriterMode = !this.typewriterMode;
        this.preferences.edit().putBoolean(PREF_TYPEWRITER, this.typewriterMode).apply();
        this.editor.setTypewriterMode(this.typewriterMode);
        Toast.makeText(this, this.typewriterMode ? "Typewriter mode on" : "Typewriter mode off", MODE_EDIT).show();
    }

    private void showWordGoal() {
        final EditText editText = new EditText(this);
        editText.setInputType(MODE_SPLIT);
        editText.setHint("Words");
        if (this.wordGoal > 0) {
            editText.setText(Integer.toString(this.wordGoal));
        }
        editText.setSelectAllOnFocus(true);
        new AlertDialog.Builder(this).setTitle("Writing goal").setMessage("Set a word target for the active writing session. Use 0 to clear it.").setView(editText).setPositiveButton("Set", new DialogInterface.OnClickListener() { // from class: com.example.markdowndesk.MainActivity$$ExternalSyntheticLambda81
            @Override // android.content.DialogInterface.OnClickListener
            public final void onClick(DialogInterface dialogInterface, int i) {
                MainActivity.this.lambda$showWordGoal$66(editText, dialogInterface, i);
            }
        }).setNegativeButton("Cancel", (DialogInterface.OnClickListener) null).show();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$showWordGoal$66(EditText editText, DialogInterface dialogInterface, int i) {
        try {
            this.wordGoal = Math.max(MODE_EDIT, Integer.parseInt(editText.getText().toString().trim()));
        } catch (NumberFormatException e) {
            this.wordGoal = MODE_EDIT;
        }
        this.preferences.edit().putInt(PREF_GOAL, this.wordGoal).apply();
        updateStatus();
    }

    private void shareDocument(boolean z) {
        String string = this.editor.getText().toString();
        Intent intent = new Intent("android.intent.action.SEND");
        intent.setType(z ? "text/html" : "text/markdown");
        intent.putExtra("android.intent.extra.SUBJECT", this.displayName);
        if (z) {
            string = MarkdownRenderer.render(string, this.palette, this.previewFontSp, this.readerTypography);
        }
        intent.putExtra("android.intent.extra.TEXT", string);
        try {
            startActivity(Intent.createChooser(intent, z ? "Share rendered document" : "Share Markdown"));
        } catch (ActivityNotFoundException e) {
            showError("No app is available to share this document.");
        }
    }

    private static final int REQUEST_DOCX_COPY = 1006;
    private enum DocxPhase { GENERATING, OFFERED, AWAIT_PICKER, PUBLISHING, NOTICE }
    private static final class DocxUi {
        DocumentDocxExport.Ticket ticket;
        AlertDialog dialog;
        Object token;
        String pickerNonce;
        DocxPhase phase = DocxPhase.GENERATING;
    }
    private DocxUi docxUi;
    private boolean docxInterruptedCopy;

    private List<Uri> currentDocxUris() {
        if (this.tabs.size() > MAX_TABS) throw new IllegalArgumentException("Too many open tabs");
        List<Uri> uris = new ArrayList<>();
        for (DocumentTab tab : this.tabs) {
            if (tab.uri == null) continue;
            Uri uri = Uri.parse(tab.uri);
            if (tab.uri.length() > 4096 || !"content".equals(uri.getScheme())
                    || uri.getAuthority() == null || uri.getAuthority().isEmpty())
                throw new IllegalArgumentException("Invalid open document URI");
            uris.add(uri);
        }
        return uris;
    }

    private boolean docxCurrent(DocxUi ui, AlertDialog dialog, Object token) {
        return ui == this.docxUi && !isFinishing() && !isDestroyed()
                && ui.dialog == dialog && ui.token == token && dialog != null && dialog.isShowing();
    }

    private void cancelDocxExport() {
        DocxUi ui = this.docxUi;
        this.docxUi = null;
        if (ui == null) return;
        AlertDialog dialog = ui.dialog;
        DocumentDocxExport.Ticket ticket = ui.ticket;
        ui.dialog = null; ui.token = null; ui.ticket = null; ui.pickerNonce = null;
        if (dialog != null) dialog.dismiss();
        if (ticket != null) ticket.cancel();
    }

    private void pauseDocxExport() {
        if (this.docxUi == null || this.docxUi.phase == DocxPhase.AWAIT_PICKER) return;
        if (this.docxUi.phase == DocxPhase.PUBLISHING) this.docxInterruptedCopy = true;
        cancelDocxExport();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (this.docxInterruptedCopy) {
            this.docxInterruptedCopy = false;
            showDocxNotice("DOCX export interrupted", "DOCX cancellation was requested when the app left the screen. The provider may still be finishing, and the selected DOCX copy may be incomplete. Check it before using it. No automatic retry was made.");
        }
    }

    private void presentDocxDialog(DocxUi ui, String title, String message, String positive, Runnable action) {
        if (ui != this.docxUi || isFinishing() || isDestroyed()) return;
        final Object token = new Object();
        final AlertDialog[] owned = new AlertDialog[1];
        AlertDialog.Builder builder = historyDialogBuilder(title).setMessage(message)
                .setNegativeButton(ui.phase == DocxPhase.NOTICE ? "Close" : "Cancel", (ignored, which) -> {
                    if (docxCurrent(ui, owned[0], token)) cancelDocxExport();
                });
        if (positive != null) builder.setPositiveButton(positive, (ignored, which) -> {
            if (docxCurrent(ui, owned[0], token)) action.run();
        });
        owned[0] = builder.create();
        AlertDialog previous = ui.dialog;
        ui.dialog = owned[0]; ui.token = token;
        if (previous != null) previous.dismiss();
        owned[0].setOnDismissListener(ignored -> {
            if (this.docxUi == ui && ui.dialog == owned[0] && ui.token == token) cancelDocxExport();
        });
        owned[0].setOnShowListener(ignored -> workspaceDialogShown(owned[0]));
        owned[0].show();
    }

    private void showDocxNotice(String title, String message) {
        cancelDocxExport();
        if (isFinishing() || isDestroyed()) return;
        DocxUi ui = new DocxUi(); ui.phase = DocxPhase.NOTICE;
        this.docxUi = ui;
        presentDocxDialog(ui, title, message, null, null);
    }

    private void startDocxExport() {
        cancelDocxExport();
        if (isFinishing() || isDestroyed()) return;
        this.docxInterruptedCopy = false;
        final DocxUi ui = new DocxUi();
        this.docxUi = ui;
        try {
            ui.ticket = DocumentDocxExport.tryStart(getApplicationContext(),
                    () -> new DocumentDocxExport.Capture(this.editor.getText(),
                            this.displayName == null ? "Untitled.md" : this.displayName,
                            this.currentUri, currentDocxUris()),
                    offer -> {
                        if (docxCurrent(ui, ui.dialog, ui.token) && ui.phase == DocxPhase.GENERATING) offerDocx(ui, offer);
                    }, failure -> {
                        if (docxCurrent(ui, ui.dialog, ui.token) && ui.phase == DocxPhase.GENERATING)
                            showDocxNotice("DOCX export failed", docxFailureMessage(failure));
                    });
            if (ui.ticket == null) {
                showDocxNotice("DOCX export busy", "DOCX export is busy finishing an earlier operation. Try again after it finishes. If an earlier close could not be confirmed, restart the app before a fresh attempt.");
                return;
            }
            presentDocxDialog(ui, "Preparing DOCX", "Preparing a separate copy of the captured document. DOCX supports source up to 1 MiB of UTF-8; native Markdown and text files support up to 8 MiB. Your original document will not be changed.", null, null);
        } catch (RuntimeException failure) {
            showDocxNotice("DOCX export failed", "The document could not be captured for DOCX. DOCX supports source up to 1 MiB of valid UTF-8 and bounded document structure and title; native Markdown and text files still support up to 8 MiB. Check the document and try again.");
        }
    }

    private String docxWarnings(DocumentDocxExport.Offer offer) {
        StringBuilder text = new StringBuilder();
        for (java.util.Map.Entry<MarkdownDocx.Warning, Integer> entry : offer.warnings.entrySet()) {
            String label;
            switch (entry.getKey()) {
                case IMAGE_NOT_INCLUDED: label = "Images replaced with alt text"; break;
                case BLOCKED_LINK: label = "Unsupported links kept as labels"; break;
                case AMBIGUOUS_TABLE: label = "Ambiguous tables kept as literal text"; break;
                case AMBIGUOUS_LIST: label = "Ambiguous lists kept as literal text"; break;
                case UNTERMINATED_FENCE: label = "Unclosed code fences"; break;
                case UNSUPPORTED_INLINE: label = "Unsupported inline formatting kept literal"; break;
                default: label = "Unsupported blocks kept literal"; break;
            }
            text.append("\n• ").append(label).append(": ").append(entry.getValue());
        }
        return text.toString();
    }

    private void offerDocx(DocxUi ui, DocumentDocxExport.Offer offer) {
        ui.phase = DocxPhase.OFFERED;
        presentDocxDialog(ui, "Export DOCX", "Captured document: " + offer.title + "\n\nChoose a new DOCX copy. Formatting uses a limited Markdown subset: images become alt text and unsupported syntax stays literal. No DOCX import or round-trip is promised.\n\nDOCX supports source up to 1 MiB of UTF-8; native Markdown and text files support up to 8 MiB.\n\n" + (offer.warnings.isEmpty() ? "No conversion warnings were reported." : "Conversion warnings:" + docxWarnings(offer)),
                "Save new DOCX", () -> launchDocxPicker(ui, offer));
    }

    private void launchDocxPicker(DocxUi ui, DocumentDocxExport.Offer offer) {
        if (!docxCurrent(ui, ui.dialog, ui.token) || ui.phase != DocxPhase.OFFERED || ui.ticket == null) return;
        ui.phase = DocxPhase.AWAIT_PICKER;
        AlertDialog previous = ui.dialog; ui.dialog = null; ui.token = null;
        if (previous != null) previous.dismiss();
        ui.pickerNonce = java.util.UUID.randomUUID().toString();
        Intent picker = new Intent(this, DocxPickerActivity.class)
                .putExtra(DocxPickerActivity.EXTRA_NONCE, ui.pickerNonce)
                .putExtra("android.intent.extra.TITLE", offer.filename);
        try { startActivityForResult(picker, REQUEST_DOCX_COPY); }
        catch (RuntimeException failure) {
            showDocxNotice("DOCX export failed", "The document picker could not be opened. No verified DOCX copy is available. Try a fresh export when the picker is available.");
        }
    }

    private void handleDocxResult(int result, Intent data) {
        final DocxUi ui = this.docxUi;
        String nonce = data == null ? null : data.getStringExtra(DocxPickerActivity.EXTRA_NONCE);
        if (ui == null || !DocxPickerActivity.validNonce(nonce) || !nonce.equals(ui.pickerNonce)) return;
        if (ui.phase != DocxPhase.AWAIT_PICKER || ui.ticket == null || isFinishing() || isDestroyed()) return;
        if (result != -1 || data.getData() == null) { cancelDocxExport(); return; }
        ui.pickerNonce = null;
        ui.phase = DocxPhase.PUBLISHING;
        try {
            ui.ticket.publish(data.getData(), currentDocxUris(), completion -> {
                if (docxCurrent(ui, ui.dialog, ui.token) && ui.phase == DocxPhase.PUBLISHING) finishDocx(ui, completion);
            });
            presentDocxDialog(ui, "Writing DOCX copy", "Writing and checking the new copy. Success is reported only after binary readback verifies the DOCX. Cancel may leave an incomplete copy at the selected destination.", null, null);
        } catch (RuntimeException failure) {
            showDocxNotice("DOCX export failed", "The selected destination could not be used safely. No verified DOCX copy is available. Choose a fresh new destination for a deliberate retry; no automatic retry was made.");
        }
    }

    private String docxFailureMessage(DocumentDocxExport.Failure failure) {
        String partial = failure.destinationMayContainPartialCopy ? " The selected copy may be incomplete; check it before using it." : " No verified DOCX copy was created.";
        if (failure.problem == DocumentDocxExport.Problem.UNCERTAIN_IO)
            return "A stream close could not be confirmed. DOCX export cannot safely restart in this running app; restart the app before a fresh attempt." + partial;
        if (failure.problem == DocumentDocxExport.Problem.DESTINATION)
            return "The destination was not accepted as a safe, empty new copy. Choose a fresh new destination for a deliberate retry." + partial;
        if (failure.problem == DocumentDocxExport.Problem.GENERATION || failure.problem == DocumentDocxExport.Problem.STAGING)
            return "The DOCX could not be prepared or its private staging could not be confirmed. DOCX has a 1 MiB UTF-8 source limit and bounded document structure; native text still supports up to 8 MiB. Check the document and storage before a fresh attempt." + partial;
        return "DOCX export did not complete with verified binary readback. Choose a fresh new destination for a deliberate retry; no automatic retry was made." + partial;
    }

    private void finishDocx(DocxUi ui, DocumentDocxExport.Completion completion) {
        if (ui != this.docxUi) return;
        if (completion.verified) showDocxNotice("DOCX exported", "The new DOCX copy was verified by binary readback. The original document and its editing state are unchanged.");
        else showDocxNotice("DOCX export failed", docxFailureMessage(completion.failure));
    }

    private void printDocument() {
        DocumentPrint.start(this, () -> new DocumentPrint.Capture(this.editor.getText(), this.displayName,
                this.palette, this.previewFontSp, this.utf8Bom, this.readerTypography), this::showError);
    }

    private void styleDialog(AlertDialog alertDialog) {
        if (alertDialog.getWindow() != null) {
            alertDialog.getWindow().setBackgroundDrawable(UiTheme.rounded(this.palette.raised, dp(24), this.palette.border, MODE_PREVIEW));
        }
        Button positive = alertDialog.getButton(DialogInterface.BUTTON_POSITIVE);
        Button negative = alertDialog.getButton(DialogInterface.BUTTON_NEGATIVE);
        Button neutral = alertDialog.getButton(DialogInterface.BUTTON_NEUTRAL);
        if (positive != null) positive.setTextColor(UiTheme.readableForeground(this.palette.accent, this.palette.raised));
        if (negative != null) negative.setTextColor(UiTheme.readableForeground(this.palette.accent, this.palette.raised));
        if (neutral != null) neutral.setTextColor(UiTheme.readableForeground(this.palette.accent, this.palette.raised));
    }

    private void showRecentFailure(final Uri uri, Exception exc) {
        new AlertDialog.Builder(this).setTitle("Could not open recent file").setMessage(message(exc) + "\n\nIts permission may have been revoked, or the file may have moved.").setPositiveButton("Remove from recents", new DialogInterface.OnClickListener() { // from class: com.example.markdowndesk.MainActivity$$ExternalSyntheticLambda48
            @Override // android.content.DialogInterface.OnClickListener
            public final void onClick(DialogInterface dialogInterface, int i) {
                MainActivity.this.lambda$showRecentFailure$68(uri, dialogInterface, i);
            }
        }).setNegativeButton("Keep", (DialogInterface.OnClickListener) null).show();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$showRecentFailure$68(Uri uri, DialogInterface dialogInterface, int i) {
        RecentStore.remove(this, uri.toString());
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void schedulePreview() {
        invalidatePreview();
        if (this.previewStopped || this.mode == MODE_EDIT || this.preview == null || this.editor == null) return;
        this.previewRunnable = () -> {
            this.previewRunnable = null;
            renderPreview();
        };
        this.main.postDelayed(this.previewRunnable, 180L);
    }

    private void invalidatePreview() {
        retireStaleSourceNavigation();
        // Options can already have changed; preserve the outgoing acknowledged document's viewport.
        PreviewPage outgoing = this.previewPage;
        if (this.previewReady && outgoing != null && outgoing.acknowledged && outgoing.view == this.preview
                && outgoing.revision == this.previewRevision && outgoing.documentId.equals(previewDocumentId()))
            this.previewRestoreY = this.preview.getScrollY();
        this.previewReady = false;
        this.previewPage = null;
        this.previewProbePending = null; // Physical probe ownership survives logical invalidation.
        retirePreviewSynchronization();
        this.previewRevision++;
        this.previewWork.invalidate();
        if (this.previewRunnable != null) this.main.removeCallbacks(this.previewRunnable);
        this.previewRunnable = null;
    }

    private String previewDocumentId() {
        return this.activeTab >= 0 && this.activeTab < this.tabs.size()
                ? this.tabs.get(this.activeTab).id : "";
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void renderPreview() {
        invalidatePreview();
        if (this.previewStopped || this.mode == MODE_EDIT || this.preview == null || this.editor == null) return;
        // Capture on main only, after debounce (or an explicit view/appearance render).
        this.previewWork.request(new LatestPreview.Request(previewDocumentId(), this.previewRevision,
                this.editor.getText().toString(), this.palette, this.previewFontSp, this.mode, this.readerTypography));
    }

    private void acceptPreview(LatestPreview.Result result) {
        LatestPreview.Request request = result.request;
        if (this.previewStopped || this.preview == null || this.editor == null || this.isFinishing()
                || request.revision != this.previewRevision || !request.documentId.equals(previewDocumentId())
                || request.palette != this.palette || request.typography != this.readerTypography || Float.compare(request.fontSp, this.previewFontSp) != 0
                || request.mode != this.mode || this.mode == MODE_EDIT) return;
        PreviewPage page = new PreviewPage(this.preview, request);
        this.previewPage = page;
        this.preview.loadDataWithBaseURL("https://markdown.local/", previewPageHtml(result.html, page.token), "text/html", "UTF-8", null);
    }

    /** Main-owned navigation metadata; never retains the rendered source or HTML. */
    private static final class PreviewPage {
        final WebView view;
        final String token, documentId;
        final long revision;
        final UiTheme.Palette palette;
        final float fontSp;
        final int mode;
        final ReaderTypography typography;
        boolean acknowledged;

        PreviewPage(WebView view, LatestPreview.Request request) {
            this.view = view;
            this.token = Long.toString(request.revision);
            this.documentId = request.documentId;
            this.revision = request.revision;
            this.palette = request.palette;
            this.fontSp = request.fontSp;
            this.mode = request.mode;
            this.typography=request.typography;
        }
    }

    private static String previewPageHtml(String html, String token) {
        final String prefix = "<!doctype html><html>";
        if (!html.startsWith(prefix)) throw new IllegalArgumentException("Unexpected preview HTML framing");
        // Full-size builder plus result String at load time; no source/HTML is retained in PreviewPage.
        return new StringBuilder(html.length() + token.length() + 32)
                .append("<!doctype html><html data-md-preview=\"").append(token).append("\">")
                .append(html, prefix.length(), html.length()).toString();
    }

    private boolean isCurrentPreviewPage(PreviewPage page) {
        return page != null && page == this.previewPage && page.view == this.preview
                && !this.previewStopped && !isFinishing() && !isDestroyed()
                && page.revision == this.previewRevision && page.documentId.equals(previewDocumentId())
                && page.palette == this.palette && page.typography == this.readerTypography && Float.compare(page.fontSp, this.previewFontSp) == 0
                && page.mode == this.mode && this.mode != MODE_EDIT;
    }

    private String previewDomGuard(PreviewPage page) {
        return "document.readyState==='complete'&&document.documentElement.getAttribute('data-md-preview')==='" + page.token + "'";
    }

    private boolean previewScrollIsCurrent() {
        return this.previewReady && isCurrentPreviewPage(this.previewPage) && this.previewPage.acknowledged;
    }

    private void acknowledgePreviewPage(WebView view) {
        final PreviewPage page = this.previewPage;
        if (!isCurrentPreviewPage(page) || view != page.view || page.acknowledged) return;
        if (this.previewProbeRunning) { this.previewProbePending = page; return; }
        this.previewProbeRunning = true;
        this.previewProbePending = null;
        final long probe = ++this.previewProbeSequence;
        view.evaluateJavascript("document.readyState==='complete'?document.documentElement.getAttribute('data-md-preview'):null", value -> {
            // A lost callback intentionally holds this physical slot: invalidation cannot queue more probes.
            if (!this.previewProbeRunning || probe != this.previewProbeSequence) return;
            this.previewProbeRunning = false;
            PreviewPage pending = this.previewProbePending;
            this.previewProbePending = null;
            // Generated decimal token: accept only its exact JSON string, never a coerced value.
            if (isCurrentPreviewPage(page) && ("\"" + page.token + "\"").equals(value) && !page.acknowledged) {
                page.acknowledged = true;
                this.previewReady = true;
                long operation = beginPreviewScroll(true);
                view.scrollTo(MODE_EDIT, Math.max(MODE_EDIT, this.previewRestoreY));
                releasePreviewScroll(page, operation, 160L);
                if (this.mode == MODE_SPLIT) schedulePreviewSyncFromEditor();
                applyPendingOutlineNavigation();
            }
            PreviewPage current = this.previewPage;
            if (isCurrentPreviewPage(current) && !current.acknowledged && (pending == current || current != page))
                acknowledgePreviewPage(current.view);
        });
    }

    private long beginPreviewScroll(boolean fromEditor) {
        if (this.previewScrollRelease != null) this.main.removeCallbacks(this.previewScrollRelease);
        this.previewScrollRelease = null;
        this.syncingFromEditor = fromEditor;
        this.syncingFromPreview = !fromEditor;
        return ++this.previewScrollOperation;
    }

    private void releasePreviewScroll(PreviewPage page, long operation, long delay) {
        if (!isCurrentPreviewPage(page) || operation != this.previewScrollOperation) return;
        if (this.previewScrollRelease != null) this.main.removeCallbacks(this.previewScrollRelease);
        this.previewScrollRelease = () -> {
            if (!isCurrentPreviewPage(page) || operation != this.previewScrollOperation) return;
            this.previewScrollRelease = null;
            this.syncingFromEditor = false;
            this.syncingFromPreview = false;
        };
        this.main.postDelayed(this.previewScrollRelease, delay);
    }

    private void stopPreview() {
        this.previewStopped = true;
        invalidatePreview();
        this.previewWork.close();
        this.renderIo.shutdownNow();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void scheduleHighlight() {
        this.syntaxHighlight.reset();
        scheduleHighlightEdit();
    }

    private void scheduleHighlightEdit() {
        if (this.editor == null) {
            return;
        }
        if (this.highlightRunnable != null) {
            this.main.removeCallbacks(this.highlightRunnable);
        }
        this.highlightRunnable = new Runnable() { // from class: com.example.markdowndesk.MainActivity$$ExternalSyntheticLambda10
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.this.lambda$scheduleHighlight$69();
            }
        };
        this.main.postDelayed(this.highlightRunnable, 90L);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$scheduleHighlight$69() {
        this.syntaxHighlight.apply(this.editor.getText(), this.dark);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void schedulePreviewSyncFromEditor() {
        final PreviewPage page = this.previewPage;
        if (!this.previewReady || this.mode != MODE_SPLIT || !isCurrentPreviewPage(page)) return;
        if (this.editorSyncRunnable != null) this.main.removeCallbacks(this.editorSyncRunnable);
        this.editorSyncRunnable = () -> {
            this.editorSyncRunnable = null;
            if (isCurrentPreviewPage(page)) lambda$schedulePreviewSyncFromEditor$72();
        };
        this.main.postDelayed(this.editorSyncRunnable, 55L);
    }

    private void lambda$schedulePreviewSyncFromEditor$72() {
        final PreviewPage page = this.previewPage;
        if (!this.previewReady || this.syncingFromPreview || !isCurrentPreviewPage(page)) return;
        int offset = editorOffsetAtViewport();
        final long operation = beginPreviewScroll(true);
        this.preview.evaluateJavascript("if(" + previewDomGuard(page) + "){window.mdScrollToSource&&window.mdScrollToSource(" + offset + ")}",
                value -> releasePreviewScroll(page, operation, 120L));
    }

    private void scheduleEditorSyncFromPreview() {
        final PreviewPage page = this.previewPage;
        if (!this.previewReady || this.mode != MODE_SPLIT || !isCurrentPreviewPage(page)) return;
        if (this.previewSyncRunnable != null) this.main.removeCallbacks(this.previewSyncRunnable);
        this.previewSyncRunnable = () -> {
            this.previewSyncRunnable = null;
            if (isCurrentPreviewPage(page)) lambda$scheduleEditorSyncFromPreview$75();
        };
        this.main.postDelayed(this.previewSyncRunnable, 75L);
    }

    private void lambda$scheduleEditorSyncFromPreview$75() {
        final PreviewPage page = this.previewPage;
        if (!this.previewReady || this.syncingFromEditor || !isCurrentPreviewPage(page)) return;
        final long operation = beginPreviewScroll(false);
        this.preview.evaluateJavascript("(function(){if(" + previewDomGuard(page) + "){return window.mdSourceAtViewport?window.mdSourceAtViewport():0;}return null;})()", value -> {
            if (!isCurrentPreviewPage(page) || operation != this.previewScrollOperation) return;
            if (value == null || "null".equals(value)) {
                releasePreviewScroll(page, operation, 120L);
                return;
            }
            final int offset = parseJsInt(value);
            if (this.previewEditorScroll != null) this.editor.removeCallbacks(this.previewEditorScroll);
            this.previewEditorScroll = () -> {
                if (!isCurrentPreviewPage(page) || operation != this.previewScrollOperation) return;
                this.previewEditorScroll = null;
                lambda$scrollEditorToOffset$76(clamp(offset, MODE_EDIT, this.editor.length()));
                releasePreviewScroll(page, operation, 120L);
            };
            this.editor.post(this.previewEditorScroll);
        });
    }

    private int editorOffsetAtViewport() {
        Layout layout = this.editor.getLayout();
        return (layout == null || this.editor.length() == 0) ? MODE_EDIT : clamp(layout.getLineStart(clamp(layout.getLineForVertical(Math.max(MODE_EDIT, this.editor.getScrollY() + dp(8))), MODE_EDIT, layout.getLineCount() - 1)), MODE_EDIT, this.editor.length());
    }

    private int parseJsInt(String str) {
        if (str == null) {
            return MODE_EDIT;
        }
        try {
            return Math.max(MODE_EDIT, Integer.parseInt(str.replace("\"", "").trim()));
        } catch (NumberFormatException e) {
            return MODE_EDIT;
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public boolean handlePreviewUri(Uri uri) {
        if (uri != null && "mdsrc".equalsIgnoreCase(uri.getScheme())) {
            try {
                jumpToSourceOffset(Integer.parseInt(uri.toString().substring("mdsrc:".length())));
                return true;
            } catch (NumberFormatException e) {
                return true;
            }
        }
        return openExternal(uri);
    }

    private void jumpToSourceOffset(int i) {
        if (this.mode == MODE_PREVIEW) {
            setMode(MODE_EDIT, false);
        }
        scrollEditorToOffset(i, true);
    }

    private void scrollEditorToOffset(int i, boolean z) {
        final SourceNavigation navigation = new SourceNavigation(clamp(i, MODE_EDIT, this.editor.length()));
        if (!isCurrentSourceNavigation(navigation)) return;
        retireExplicitSourceNavigation();
        retirePreviewSynchronization();
        final long sequence = this.explicitNavigationSequence;
        this.sourceEditorNavigation = navigation;
        if (z) this.editor.setSelection(navigation.offset);
        this.sourceEditorScroll = () -> {
            if (sequence != this.explicitNavigationSequence || this.sourceEditorNavigation != navigation) return;
            this.sourceEditorScroll = null;
            this.sourceEditorNavigation = null;
            if (!isCurrentSourceNavigation(navigation)) return;
            // Sync may have started while this final effect waited in the editor queue.
            retirePreviewSynchronization();
            if (this.editor.getLayout() == null) return;
            final PreviewPage page = this.previewPage;
            boolean split = this.mode == MODE_SPLIT && previewScrollIsCurrent();
            long operation = split ? beginPreviewScroll(true) : this.previewScrollOperation;
            lambda$scrollEditorToOffset$76(clamp(navigation.offset, MODE_EDIT, this.editor.length()));
            if (split) {
                releasePreviewScroll(page, operation, 120L);
                schedulePreviewSyncFromEditor();
            }
        };
        this.editor.post(this.sourceEditorScroll);
    }

    /** No text snapshot or renderer result; the view references are compared only by identity. */
    private final class SourceNavigation {
        final String documentId = previewDocumentId();
        final long revision = sourceNavigationRevision, route = sourceRouteRevision;
        final Object editorView = editor, previewView = preview;
        final int requestedMode = mode;
        final int offset;
        SourceNavigation(int offset) { this.offset = offset; }
    }

    private boolean isCurrentSourceNavigation(SourceNavigation navigation) {
        return navigation != null && !this.previewStopped && !isFinishing() && !isDestroyed()
                && navigation.editorView == this.editor && navigation.previewView == this.preview
                && navigation.documentId.equals(previewDocumentId())
                && navigation.revision == this.sourceNavigationRevision
                && navigation.route == this.sourceRouteRevision && navigation.requestedMode == this.mode;
    }

    private void retireExplicitSourceNavigation() {
        this.explicitNavigationSequence++;
        this.pendingOutlineNavigation = null;
        this.sourceEditorNavigation = null;
        if (this.sourceEditorScroll != null && this.editor != null) this.editor.removeCallbacks(this.sourceEditorScroll);
        this.sourceEditorScroll = null;
    }

    private void retireStaleSourceNavigation() {
        if ((this.pendingOutlineNavigation != null && !isCurrentSourceNavigation(this.pendingOutlineNavigation))
                || (this.sourceEditorNavigation != null && !isCurrentSourceNavigation(this.sourceEditorNavigation)))
            retireExplicitSourceNavigation();
    }

    private void retirePreviewSynchronization() {
        this.previewScrollOperation++;
        this.syncingFromEditor = false;
        this.syncingFromPreview = false;
        if (this.previewScrollRelease != null) this.main.removeCallbacks(this.previewScrollRelease);
        if (this.editorSyncRunnable != null) this.main.removeCallbacks(this.editorSyncRunnable);
        if (this.previewSyncRunnable != null) this.main.removeCallbacks(this.previewSyncRunnable);
        if (this.previewEditorScroll != null && this.editor != null) this.editor.removeCallbacks(this.previewEditorScroll);
        this.previewScrollRelease = null;
        this.editorSyncRunnable = null;
        this.previewSyncRunnable = null;
        this.previewEditorScroll = null;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$scrollEditorToOffset$76(int i) {
        Layout layout = this.editor.getLayout();
        if (layout == null) {
            return;
        }
        this.editor.scrollTo(MODE_EDIT, Math.max(MODE_EDIT, layout.getLineTop(layout.getLineForOffset(i)) - dp(36)));
    }

    private void showOutline() {
        final SourceNavigation context = new SourceNavigation(0);
        final List<MarkdownStructure.Heading> headings = MarkdownStructure.headings(this.editor.getText().toString());
        if (headings.isEmpty()) {
            Toast.makeText(this, "No headings in this document", MODE_EDIT).show();
            return;
        }
        final AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Document outline").setNegativeButton("Close", null).create();
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(10), dp(4), dp(10), dp(72));
        for (final MarkdownStructure.Heading heading : headings) {
            TextView row = new TextView(this);
            row.setText(heading.text + "   ·   L" + heading.line);
            row.setTextColor(this.palette.text);
            row.setTextSize(heading.level == 1 ? 17.0f : heading.level == 2 ? 15.5f : 14.0f);
            row.setTypeface(Typeface.DEFAULT, heading.level <= 2 ? Typeface.BOLD : Typeface.NORMAL);
            row.setPadding(dp(10 + Math.max(0, heading.level - 1) * 16), dp(10), dp(8), dp(10));
            row.setBackground(UiTheme.rounded(0, dp(8), this.palette.border, 0));
            row.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    int offset = heading.offset;
                    dialog.dismiss();
                    if (!isCurrentSourceNavigation(context)) return;
                    if (mode == MODE_PREVIEW) scrollPreviewToSourceOffset(offset);
                    else jumpToSourceOffset(offset);
                }
            });
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2);
            rp.setMargins(0, 0, 0, dp(3));
            column.addView(row, rp);
        }
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(column, matchWrap());
        dialog.setView(scroll);
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override public void onShow(DialogInterface d) { styleDialog(dialog); }
        });
        dialog.show();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$showOutline$77(List list, DialogInterface dialogInterface, int i) {
        int offset = ((MarkdownStructure.Heading) list.get(i)).offset;
        if (this.mode == MODE_PREVIEW) {
            scrollPreviewToSourceOffset(offset);
        } else {
            jumpToSourceOffset(offset);
        }
    }

    private void scrollPreviewToSourceOffset(int offset) {
        SourceNavigation navigation = new SourceNavigation(Math.max(0, offset));
        if (this.mode != MODE_PREVIEW || !isCurrentSourceNavigation(navigation)) return;
        retireExplicitSourceNavigation();
        retirePreviewSynchronization();
        this.pendingOutlineNavigation = navigation;
        applyPendingOutlineNavigation();
    }

    private void applyPendingOutlineNavigation() {
        SourceNavigation navigation = this.pendingOutlineNavigation;
        if (navigation == null) return;
        if (!isCurrentSourceNavigation(navigation)) { this.pendingOutlineNavigation = null; return; }
        if (!previewScrollIsCurrent()) return;
        this.pendingOutlineNavigation = null;
        PreviewPage page = this.previewPage;
        long operation = beginPreviewScroll(true);
        this.preview.evaluateJavascript("if(" + previewDomGuard(page) + "){window.mdScrollToSource(" + navigation.offset + ");}", null);
        releasePreviewScroll(page, operation, 120L);
    }

    private void wrap(String str, String str2) {
        if (!canMutate()) return;
        ensureEditor(true);
        int iMax = Math.max(MODE_EDIT, this.editor.getSelectionStart());
        int iMax2 = Math.max(iMax, this.editor.getSelectionEnd());
        Editable text = this.editor.getText();
        text.insert(iMax2, str2);
        text.insert(iMax, str);
        KineticEditText kineticEditText = this.editor;
        if (iMax == iMax2) {
            kineticEditText.setSelection(iMax + str.length());
        } else {
            kineticEditText.setSelection(iMax + str.length(), iMax2 + str.length());
        }
    }

    private void prefixLines(String str) {
        if (!canMutate()) return;
        ensureEditor(true);
        String string = this.editor.getText().toString();
        int iMax = Math.max(MODE_EDIT, this.editor.getSelectionStart());
        int iMax2 = Math.max(iMax, this.editor.getSelectionEnd());
        int iLastIndexOf = string.lastIndexOf(10, Math.max(MODE_EDIT, iMax - MODE_PREVIEW)) + MODE_PREVIEW;
        int iIndexOf = string.indexOf(10, iMax2);
        if (iIndexOf < 0) {
            iIndexOf = string.length();
        }
        String str2 = str + string.substring(iLastIndexOf, iIndexOf).replace("\n", "\n" + str);
        this.editor.getText().replace(iLastIndexOf, iIndexOf, str2);
        this.editor.setSelection(iLastIndexOf, str2.length() + iLastIndexOf);
    }

    private void insertLink() {
        if (!canMutate()) return;
        ensureEditor(true);
        int iMax = Math.max(MODE_EDIT, this.editor.getSelectionStart());
        int iMax2 = Math.max(iMax, this.editor.getSelectionEnd());
        String string = this.editor.getText().subSequence(iMax, iMax2).toString();
        if (string.isEmpty()) {
            string = "link text";
        }
        this.editor.getText().replace(iMax, iMax2, "[" + string + "](https://)");
        int length = iMax + string.length() + 3;
        this.editor.setSelection(length, length + 8);
    }

    private void insertCode() {
        int iMax = Math.max(MODE_EDIT, this.editor.getSelectionStart());
        if (this.editor.getText().subSequence(iMax, Math.max(iMax, this.editor.getSelectionEnd())).toString().contains("\n")) {
            wrap("```\n", "\n```");
        } else {
            wrap("`", "`");
        }
    }

    private void insertTable() {
        insertBlock("\n| Column | Column |\n| --- | --- |\n| Value | Value |\n");
    }

    private void insertBlock(String str) {
        if (!canMutate()) return;
        ensureEditor(true);
        int iMax = Math.max(MODE_EDIT, this.editor.getSelectionStart());
        this.editor.getText().insert(iMax, str);
        this.editor.setSelection(iMax + str.length());
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void continueMarkdownLine(Editable editable) {
        int selectionStart;
        String strReplaceFirst;
        if (this.suppressChanges || this.smartEditing || this.editorLocked || this.editor == null || (selectionStart = this.editor.getSelectionStart()) <= 0 || selectionStart > editable.length()) {
            return;
        }
        int i = selectionStart - 1;
        if (editable.charAt(i) != '\n') {
            return;
        }
        String string = editable.subSequence(editable.toString().lastIndexOf(10, Math.max(MODE_EDIT, selectionStart - 2)) + MODE_PREVIEW, i).toString();
        Matcher matcher = Pattern.compile("^(\\s*)(\\d+)\\.\\s+.*$").matcher(string);
        if (string.matches("^\\s*[-+*] \\[([ xX])\\] .+$")) {
            strReplaceFirst = string.replaceFirst("^(\\s*[-+*] \\[)[ xX](\\] ).*$", "$1 $2");
        } else if (string.matches("^\\s*[-+*] .+$")) {
            strReplaceFirst = string.replaceFirst("^(\\s*[-+*] ).*$", "$1");
        } else if (string.matches("^\\s*> .+$")) {
            strReplaceFirst = string.replaceFirst("^(\\s*> ).*$", "$1");
        } else if (matcher.matches()) {
            strReplaceFirst = matcher.group(MODE_PREVIEW) + (Integer.parseInt(matcher.group(MODE_SPLIT)) + MODE_PREVIEW) + ". ";
        } else {
            strReplaceFirst = null;
        }
        if (strReplaceFirst == null || strReplaceFirst.isEmpty()) {
            return;
        }
        this.smartEditing = true;
        editable.insert(selectionStart, strReplaceFirst);
        this.editor.setSelection(selectionStart + strReplaceFirst.length());
        this.smartEditing = false;
    }

    private void showFindReplace() {
        ensureEditor(false);
        final DocumentTab searchTab = (this.activeTab >= 0 && this.activeTab < this.tabs.size()) ? this.tabs.get(this.activeTab) : null;
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(MODE_PREVIEW);
        linearLayout.setPadding(dp(20), dp(4), dp(20), MODE_EDIT);
        final EditText editText = new EditText(this);
        editText.setHint("Find");
        editText.setSingleLine(true);
        if (searchTab != null) editText.setText(searchTab.findQuery);
        final EditText editText2 = new EditText(this);
        editText2.setHint("Replace with");
        editText2.setSingleLine(true);
        if (searchTab != null) editText2.setText(searchTab.replaceQuery);
        linearLayout.addView(editText, matchWrap());
        linearLayout.addView(editText2, matchWrap());
        LinearLayout linearLayout2 = new LinearLayout(this);
        linearLayout2.setOrientation(MODE_EDIT);
        final CheckBox checkBox = new CheckBox(this);
        checkBox.setText("Case");
        final CheckBox checkBox2 = new CheckBox(this);
        checkBox2.setText("Whole word");
        final CheckBox checkBox3 = new CheckBox(this);
        checkBox3.setText("Regex");
        if (searchTab != null) {
            checkBox.setChecked(searchTab.findCaseSensitive);
            checkBox2.setChecked(searchTab.findWholeWord);
            checkBox3.setChecked(searchTab.findRegex);
        }
        linearLayout2.addView(checkBox, wrapWrap());
        linearLayout2.addView(checkBox2, wrapWrap());
        linearLayout2.addView(checkBox3, wrapWrap());
        linearLayout.addView(linearLayout2, matchWrap());
        final TextView textView = new TextView(this);
        textView.setPadding(dp(4), dp(8), dp(4), dp(8));
        linearLayout.addView(textView, matchWrap());
        LinearLayout linearLayout3 = new LinearLayout(this);
        linearLayout3.setOrientation(MODE_EDIT);
        final Button button = new Button(this);
        button.setText("All matches");
        button.setAllCaps(false);
        final Button button2 = new Button(this);
        button2.setText(R.string.replace_all);
        button2.setAllCaps(false);
        linearLayout3.addView(button, new LinearLayout.LayoutParams(MODE_EDIT, -2, 1.0f));
        linearLayout3.addView(button2, new LinearLayout.LayoutParams(MODE_EDIT, -2, 1.0f));
        linearLayout.addView(linearLayout3, matchWrap());
        final AlertDialog alertDialogCreate = new AlertDialog.Builder(this).setTitle("Find and replace").setView(linearLayout).setPositiveButton("Find next", (DialogInterface.OnClickListener) null).setNegativeButton("Close", (DialogInterface.OnClickListener) null).setNeutralButton("Replace", (DialogInterface.OnClickListener) null).create();
        final Runnable runnable = new Runnable() { // from class: com.example.markdowndesk.MainActivity$$ExternalSyntheticLambda11
            @Override // java.lang.Runnable
            public final void run() {
                if (searchTab != null) {
                    searchTab.findQuery = editText.getText().toString();
                    searchTab.replaceQuery = editText2.getText().toString();
                    searchTab.findCaseSensitive = checkBox.isChecked();
                    searchTab.findWholeWord = checkBox2.isChecked();
                    searchTab.findRegex = checkBox3.isChecked();
                    MainActivity.this.scheduleSession();
                }
                MainActivity.this.lambda$showFindReplace$78(editText, checkBox, checkBox2, checkBox3, textView);
            }
        };
        editText.addTextChangedListener(simpleWatcher(runnable));
        editText2.addTextChangedListener(simpleWatcher(runnable));
        checkBox.setOnClickListener(new View.OnClickListener() { // from class: com.example.markdowndesk.MainActivity$$ExternalSyntheticLambda12
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                runnable.run();
            }
        });
        checkBox2.setOnClickListener(new View.OnClickListener() { // from class: com.example.markdowndesk.MainActivity$$ExternalSyntheticLambda13
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                runnable.run();
            }
        });
        checkBox3.setOnClickListener(new View.OnClickListener() { // from class: com.example.markdowndesk.MainActivity$$ExternalSyntheticLambda14
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                runnable.run();
            }
        });
        alertDialogCreate.setOnShowListener(new DialogInterface.OnShowListener() { // from class: com.example.markdowndesk.MainActivity$$ExternalSyntheticLambda15
            @Override // android.content.DialogInterface.OnShowListener
            public final void onShow(DialogInterface dialogInterface) {
                MainActivity.this.lambda$showFindReplace$86(alertDialogCreate, editText, checkBox, checkBox2, checkBox3, textView, editText2, button, button2, dialogInterface);
            }
        });
        alertDialogCreate.setOnDismissListener(new DialogInterface.OnDismissListener() { // from class: com.example.markdowndesk.MainActivity$$ExternalSyntheticLambda16
            @Override // android.content.DialogInterface.OnDismissListener
            public final void onDismiss(DialogInterface dialogInterface) {
                MainActivity.this.lambda$showFindReplace$87(dialogInterface);
            }
        });
        alertDialogCreate.show();
        editText.requestFocus();
        showKeyboard(editText);
        runnable.run();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$showFindReplace$78(EditText editText, CheckBox checkBox, CheckBox checkBox2, CheckBox checkBox3, TextView textView) {
        updateSearchCount(editText.getText().toString(), searchOptions(checkBox, checkBox2, checkBox3), textView);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$showFindReplace$82(EditText editText, CheckBox checkBox, CheckBox checkBox2, CheckBox checkBox3, TextView textView, View view) {
        findNext(editText.getText().toString(), searchOptions(checkBox, checkBox2, checkBox3), textView);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$showFindReplace$86(AlertDialog alertDialog, final EditText editText, final CheckBox checkBox, final CheckBox checkBox2, final CheckBox checkBox3, final TextView textView, final EditText editText2, Button button, Button button2, DialogInterface dialogInterface) {
        alertDialog.getButton(-1).setOnClickListener(new View.OnClickListener() { // from class: com.example.markdowndesk.MainActivity$$ExternalSyntheticLambda22
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.lambda$showFindReplace$82(editText, checkBox, checkBox2, checkBox3, textView, view);
            }
        });
        alertDialog.getButton(-3).setOnClickListener(new View.OnClickListener() { // from class: com.example.markdowndesk.MainActivity$$ExternalSyntheticLambda23
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.lambda$showFindReplace$83(editText, editText2, checkBox, checkBox2, checkBox3, textView, view);
            }
        });
        button.setOnClickListener(new View.OnClickListener() { // from class: com.example.markdowndesk.MainActivity$$ExternalSyntheticLambda24
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.lambda$showFindReplace$84(editText, checkBox, checkBox2, checkBox3, textView, view);
            }
        });
        button2.setOnClickListener(new View.OnClickListener() { // from class: com.example.markdowndesk.MainActivity$$ExternalSyntheticLambda25
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.lambda$showFindReplace$85(editText, editText2, checkBox, checkBox2, checkBox3, textView, view);
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$showFindReplace$83(EditText editText, EditText editText2, CheckBox checkBox, CheckBox checkBox2, CheckBox checkBox3, TextView textView, View view) {
        replaceOne(editText.getText().toString(), editText2.getText().toString(), searchOptions(checkBox, checkBox2, checkBox3), textView);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$showFindReplace$84(EditText editText, CheckBox checkBox, CheckBox checkBox2, CheckBox checkBox3, TextView textView, View view) {
        showSearchMatches(editText.getText().toString(), searchOptions(checkBox, checkBox2, checkBox3), textView);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$showFindReplace$85(EditText editText, EditText editText2, CheckBox checkBox, CheckBox checkBox2, CheckBox checkBox3, TextView textView, View view) {
        replaceAll(editText.getText().toString(), editText2.getText().toString(), searchOptions(checkBox, checkBox2, checkBox3), textView);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$showFindReplace$87(DialogInterface dialogInterface) {
        clearSearchHighlights();
    }

    private TextWatcher simpleWatcher(final Runnable runnable) {
        return new TextWatcher() { // from class: com.example.markdowndesk.MainActivity.3
            @Override // android.text.TextWatcher
            public void afterTextChanged(Editable editable) {
                runnable.run();
            }

            @Override // android.text.TextWatcher
            public void beforeTextChanged(CharSequence charSequence, int i, int i2, int i3) {
            }

            @Override // android.text.TextWatcher
            public void onTextChanged(CharSequence charSequence, int i, int i2, int i3) {
            }
        };
    }

    private SearchEngine.Options searchOptions(CheckBox checkBox, CheckBox checkBox2, CheckBox checkBox3) {
        return new SearchEngine.Options(checkBox.isChecked(), checkBox2.isChecked(), checkBox3.isChecked());
    }

    private void updateSearchCount(String str, SearchEngine.Options options, TextView textView) {
        clearSearchHighlights();
        try {
            List<SearchEngine.Match> listFindAll = SearchEngine.findAll(this.editor.getText().toString(), str, options);
            for (SearchEngine.Match match : listFindAll) {
                this.editor.getText().setSpan(new SearchHighlightSpan(this.palette.accentSoft), match.start, match.end, 33);
            }
            int size = listFindAll.size();
            textView.setText(str.isEmpty() ? "" : size + (size == MODE_PREVIEW ? " match" : " matches"));
        } catch (PatternSyntaxException e) {
            textView.setText("Regex error: " + e.getDescription());
        }
    }

    private void clearSearchHighlights() {
        if (this.editor == null) {
            return;
        }
        Editable text = this.editor.getText();
        int length = text.length();
        SearchHighlightSpan[] searchHighlightSpanArr = (SearchHighlightSpan[]) text.getSpans(MODE_EDIT, length, SearchHighlightSpan.class);
        int length2 = searchHighlightSpanArr.length;
        for (int i = MODE_EDIT; i < length2; i += MODE_PREVIEW) {
            text.removeSpan(searchHighlightSpanArr[i]);
        }
    }

    private boolean findNext(String str, SearchEngine.Options options, TextView textView) {
        if (str.isEmpty()) {
            return false;
        }
        try {
            List<SearchEngine.Match> listFindAll = SearchEngine.findAll(this.editor.getText().toString(), str, options);
            updateSearchCount(str, options, textView);
            if (listFindAll.isEmpty()) {
                Toast.makeText(this, "No match", MODE_EDIT).show();
                return false;
            }
            int iMax = Math.max(MODE_EDIT, this.editor.getSelectionEnd());
            SearchEngine.Match match = listFindAll.get(MODE_EDIT);
            for (SearchEngine.Match match2 : listFindAll) {
                if (match2.start >= iMax) {
                    match = match2;
                    break;
                }
            }
            this.editor.setSelection(match.start, match.end);
            scrollEditorToOffset(match.start, false);
            return true;
        } catch (PatternSyntaxException e) {
            Toast.makeText(this, "Regex error: " + e.getDescription(), MODE_PREVIEW).show();
            return false;
        }
    }

    private void showSearchMatches(String str, SearchEngine.Options options, TextView textView) {
        if (str.isEmpty()) {
            return;
        }
        try {
            String string = this.editor.getText().toString();
            final List<SearchEngine.Match> listFindAll = SearchEngine.findAll(string, str, options);
            updateSearchCount(str, options, textView);
            boolean zIsEmpty = listFindAll.isEmpty();
            if (zIsEmpty) {
                Toast.makeText(this, "No match", MODE_EDIT).show();
                return;
            }
            String[] strArr = new String[listFindAll.size()];
            for (int i = MODE_EDIT; i < listFindAll.size(); i += MODE_PREVIEW) {
                SearchEngine.Match match = listFindAll.get(i);
                strArr[i] = "L" + match.line + "  " + lineSnippet(string, match.start);
            }
            new AlertDialog.Builder(this).setTitle(listFindAll.size() + (listFindAll.size() == MODE_PREVIEW ? " match" : " matches")).setItems(strArr, new DialogInterface.OnClickListener() { // from class: com.example.markdowndesk.MainActivity$$ExternalSyntheticLambda8
                @Override // android.content.DialogInterface.OnClickListener
                public final void onClick(DialogInterface dialogInterface, int i2) {
                    MainActivity.this.lambda$showSearchMatches$88(listFindAll, dialogInterface, i2);
                }
            }).setNegativeButton("Close", (DialogInterface.OnClickListener) null).show();
        } catch (PatternSyntaxException e) {
            Toast.makeText(this, "Regex error: " + e.getDescription(), MODE_PREVIEW).show();
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$showSearchMatches$88(List list, DialogInterface dialogInterface, int i) {
        SearchEngine.Match match = (SearchEngine.Match) list.get(i);
        this.editor.setSelection(match.start, match.end);
        scrollEditorToOffset(match.start, false);
    }

    private String lineSnippet(String str, int i) {
        int iLastIndexOf = str.lastIndexOf(10, Math.max(MODE_EDIT, i - 1)) + MODE_PREVIEW;
        int iIndexOf = str.indexOf(10, i);
        if (iIndexOf < 0) {
            iIndexOf = str.length();
        }
        String strTrim = str.substring(iLastIndexOf, iIndexOf).trim();
        if (strTrim.length() <= 80) {
            return strTrim;
        }
        return strTrim.substring(MODE_EDIT, 77) + "…";
    }

    private void replaceOne(String str, String str2, SearchEngine.Options options, TextView textView) {
        if (!canMutate()) return;
        if (str.isEmpty()) {
            return;
        }
        try {
            int selectionStart = this.editor.getSelectionStart();
            int selectionEnd = this.editor.getSelectionEnd();
            String string = (selectionStart < 0 || selectionEnd < selectionStart) ? "" : this.editor.getText().subSequence(selectionStart, selectionEnd).toString();
            if (!SearchEngine.matchesSelection(string, str, options)) {
                if (!findNext(str, options, textView)) {
                    return;
                }
                selectionStart = this.editor.getSelectionStart();
                selectionEnd = this.editor.getSelectionEnd();
                string = this.editor.getText().subSequence(selectionStart, selectionEnd).toString();
            }
            SearchEngine.Replacement replacementReplaceAll = SearchEngine.replaceAll(string, str, str2, options);
            if (replacementReplaceAll.count == 0) {
                return;
            }
            this.editor.getText().replace(selectionStart, selectionEnd, replacementReplaceAll.text);
            this.editor.setSelection(selectionStart + replacementReplaceAll.text.length());
            updateSearchCount(str, options, textView);
        } catch (RuntimeException e) {
            Toast.makeText(this, "Replace error: " + message(e), MODE_PREVIEW).show();
        }
    }

    private void replaceAll(String str, String str2, SearchEngine.Options options, TextView textView) {
        if (!canMutate()) return;
        if (str.isEmpty()) {
            return;
        }
        try {
            SearchEngine.Replacement replacementReplaceAll = SearchEngine.replaceAll(this.editor.getText().toString(), str, str2, options);
            if (replacementReplaceAll.count == 0) {
                Toast.makeText(this, "No match", MODE_EDIT).show();
                return;
            }
            this.editor.getText().replace(MODE_EDIT, this.editor.length(), replacementReplaceAll.text);
            this.editor.setSelection(this.editor.length());
            updateSearchCount(str, options, textView);
            Toast.makeText(this, "Replaced " + replacementReplaceAll.count + (replacementReplaceAll.count == MODE_PREVIEW ? " match" : " matches"), MODE_EDIT).show();
        } catch (RuntimeException e) {
            Toast.makeText(this, "Replace error: " + message(e), MODE_PREVIEW).show();
        }
    }

    private void ensureEditor(boolean z) {
        if (this.mode == MODE_PREVIEW) {
            setMode(MODE_EDIT, z);
            return;
        }
        this.editor.requestFocus();
        if (z) {
            showKeyboard(this.editor);
        }
    }

    private void showDocumentInfo() {
        String text = this.editor.getText().toString();
        int words = EditorTools.wordCount(text);
        int chars = text.length();
        int lines = EditorTools.lineCount(text);
        int minutes = words == 0 ? 0 : Math.max(1, (int)Math.ceil(words / 220.0d));
        int start = Math.max(0, this.editor.getSelectionStart());
        int line = 1, column = 1;
        for (int i = 0; i < Math.min(start, text.length()); i++) {
            if (text.charAt(i) == '\n') { line++; column = 1; } else column++;
        }
        StringBuilder info = new StringBuilder();
        info.append(words).append(" words\n")
            .append(chars).append(" characters\n")
            .append(lines).append(" lines\n")
            .append(minutes).append(" min read\n")
            .append("Cursor: line ").append(line).append(", column ").append(column).append("\n")
            .append("Source text: ").append(Math.round(this.fontSp)).append(" sp\n")
            .append("Reading text: ").append(Math.round(this.previewFontSp)).append(" sp\n")
            .append("Mode: ").append(this.mode == MODE_EDIT ? "Write" : this.mode == MODE_PREVIEW ? "Read" : "Split");
        if (this.editorLocked) info.append("\nEditing: locked");
        if (this.readOnly) info.append("\nProvider: read-only source");
        if (this.currentUri != null) info.append("\n\n").append(this.currentUri.toString());
        new AlertDialog.Builder(this).setTitle(this.displayName).setMessage(info.toString()).setPositiveButton("Done", null).show();
    }

    // Main-thread keys include suppressed setText calls as well as user edits.
    private void invalidateStatistics() {
        this.statisticsRevision++;
        this.statisticsResult = null;
        this.statisticsRequestedRevision = -1;
        this.statistics.invalidate();
        if (this.statisticsRunnable != null) this.main.removeCallbacks(this.statisticsRunnable);
        this.statisticsRunnable = null;
    }

    private String statisticsDocumentId() {
        return this.activeTab >= 0 && this.activeTab < this.tabs.size()
                ? this.tabs.get(this.activeTab).id : "";
    }

    private void requestStatistics() {
        if (this.statisticsStopped) return;
        final String documentId = statisticsDocumentId();
        final long revision = this.statisticsRevision;
        final Locale locale = Locale.getDefault();
        if (revision == this.statisticsRequestedRevision
                && documentId.equals(this.statisticsRequestedDocument)
                && locale.equals(this.statisticsRequestedLocale)) return;
        this.statisticsResult = null;
        this.statistics.invalidate();
        if (this.statisticsRunnable != null) this.main.removeCallbacks(this.statisticsRunnable);
        this.statisticsRequestedRevision = revision;
        this.statisticsRequestedDocument = documentId;
        this.statisticsRequestedLocale = locale;
        this.statisticsRunnable = () -> {
            this.statisticsRunnable = null;
            if (this.statisticsStopped || this.editor == null || revision != this.statisticsRevision
                    || !documentId.equals(statisticsDocumentId()) || !locale.equals(Locale.getDefault())) return;
            // Editable stays on the UI thread; the worker receives only this immutable snapshot.
            this.statistics.request(documentId, revision, this.editor.getText().toString(), locale);
        };
        this.main.postDelayed(this.statisticsRunnable, 250L);
    }

    private void acceptStatistics(LatestStatistics.Result result) {
        if (this.statisticsStopped || this.editor == null || result.revision != this.statisticsRevision
                || !result.documentId.equals(statisticsDocumentId()) || !result.locale.equals(Locale.getDefault())) return;
        this.statisticsResult = result;
        updateStatus();
    }

    private void stopStatistics() {
        this.statisticsStopped = true;
        if (this.statisticsRunnable != null) this.main.removeCallbacks(this.statisticsRunnable);
        this.statisticsRunnable = null;
        this.statistics.close();
        this.statisticsIo.shutdownNow();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void updateStatus() {
        String str;
        String str2;
        if (this.titleView == null || this.editor == null) {
            return;
        }
        String str3 = "";
        this.titleView.setText(this.displayName + (this.dirty ? "  •" : "") + (this.readOnly ? "  ·  Read-only" : ""));
        if (this.mode == 0) {
            str = "Write";
        } else {
            str = this.mode == MODE_PREVIEW ? "Read" : "Split";
        }
        requestStatistics();
        int length = this.editor.length();
        String quantityString2 = getResources().getQuantityString(R.plurals.character_count, length, Integer.valueOf(length));
        LatestStatistics.Result counts = this.statisticsResult;
        if (counts == null) {
            this.statusView.setText("Calculating statistics · " + quantityString2 + " · " + str);
        } else {
            int iWordCount = counts.words;
            String quantityString = getResources().getQuantityString(R.plurals.word_count, iWordCount, Integer.valueOf(iWordCount));
            int iMax = iWordCount == 0 ? 0 : Math.max(1, (int) Math.ceil(((double) iWordCount) / 220.0d));
            if (this.wordGoal > 0) {
                str3 = " · " + Math.min(100, Math.round((iWordCount * 100.0f) / this.wordGoal)) + "% of " + this.wordGoal;
            }
            this.statusView.setText(quantityString + " · " + quantityString2 + " · " + counts.lines + " lines · " + iMax + " min read" + str3 + " · " + str);
        }
        Button button = this.saveButton;
        if (this.saveInProgress) {
            str2 = "•••";
        } else if (this.readOnly) {
            str2 = "COPY";
        } else {
            str2 = this.dirty ? "SAVE" : "SAVED";
        }
        button.setText(str2);
        button.setContentDescription(this.saveInProgress ? "Saving document"
                : this.readOnly ? "Save a copy" : this.dirty ? "Save document" : "Document saved");
        this.saveButton.setEnabled(!this.saveInProgress);
        if (this.palette != null) {
            if (this.dirty || this.readOnly) {
                this.saveButton.setTextColor(UiTheme.readableForeground(-1, this.palette.accent));
                this.saveButton.setBackground(UiTheme.rounded(this.palette.accent, dp(13), this.palette.accent, MODE_EDIT));
            } else {
                styleButton(this.saveButton, true, false);
            }
        }
    }

    private boolean openExternal(Uri uri) {
        if (uri == null) {
            return true;
        }
        String scheme = uri.getScheme();
        if (!"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme) && !"mailto".equalsIgnoreCase(scheme)) {
            return true;
        }
        try {
            startActivity(new Intent("android.intent.action.VIEW", uri));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "No app can open this link", MODE_EDIT).show();
        }
        return true;
    }

    private void takePermission(Uri uri, int i) {
        int i2 = i & 3;
        if (i2 == 0) {
            return;
        }
        try {
            getContentResolver().takePersistableUriPermission(uri, i2);
        } catch (SecurityException e) {
        }
    }

    private void showError(String str) {
        if (isFinishing()) {
            return;
        }
        new AlertDialog.Builder(this).setTitle("Markdown Desk").setMessage(str).setPositiveButton("OK", (DialogInterface.OnClickListener) null).show();
    }

    private String message(Throwable th) {
        return (th.getMessage() == null || th.getMessage().trim().isEmpty()) ? th.getClass().getSimpleName() : th.getMessage();
    }

    private String markdownName(String str) {
        String lowerCase = str.toLowerCase(Locale.ROOT);
        return (lowerCase.endsWith(".md") || lowerCase.endsWith(".markdown") || lowerCase.endsWith(".txt")) ? str : str + ".md";
    }

    private boolean systemDark() {
        return (getResources().getConfiguration().uiMode & 48) == 32;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$showKeyboard$89(View view) {
        InputMethodManager inputMethodManager = (InputMethodManager) getSystemService("input_method");
        if (inputMethodManager != null) {
            inputMethodManager.showSoftInput(view, MODE_PREVIEW);
        }
    }

    private void showKeyboard(final View view) {
        view.post(new Runnable() { // from class: com.example.markdowndesk.MainActivity$$ExternalSyntheticLambda17
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.this.lambda$showKeyboard$89(view);
            }
        });
    }

    private void hideKeyboard() {
        InputMethodManager inputMethodManager;
        View currentFocus = getCurrentFocus();
        if (currentFocus == null || (inputMethodManager = (InputMethodManager) getSystemService("input_method")) == null) {
            return;
        }
        inputMethodManager.hideSoftInputFromWindow(currentFocus.getWindowToken(), MODE_EDIT);
    }

    private static void detach(View view) {
        if (view == null || !(view.getParent() instanceof ViewGroup)) {
            return;
        }
        ((ViewGroup) view.getParent()).removeView(view);
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(-1, -2);
    }

    private LinearLayout.LayoutParams wrapWrap() {
        return new LinearLayout.LayoutParams(-2, -2);
    }

    private LinearLayout.LayoutParams paneParams() {
        return paneParams(1.0f);
    }

    private LinearLayout.LayoutParams paneParams(float weight) {
        return this.body.getOrientation() == LinearLayout.HORIZONTAL
                ? new LinearLayout.LayoutParams(MODE_EDIT, -1, weight)
                : new LinearLayout.LayoutParams(-1, MODE_EDIT, weight);
    }

    private int dp(int i) {
        return Math.round(i * getResources().getDisplayMetrics().density);
    }

    private static int withAlpha(int i, int i2) {
        return (i & 16777215) | (Math.max(MODE_EDIT, Math.min(255, i2)) << 24);
    }

    private static int clamp(int i, int i2, int i3) {
        return Math.max(i2, Math.min(i3, i));
    }
}
