package ru.copytermixgpt;

import android.app.PendingIntent;
import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Bundle;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.ArrayList;

public class ChatAccessibilityService extends AccessibilityService {
    // Разрешает подсветку только выбранного Accessibility-кандидата.
    public static final String ACTION_COPY_RESPONSE =
        "ru.copytermixgpt.ACTION_COPY_RESPONSE_CHAT";
    public static final String ACTION_STATUS =
        "ru.copytermixgpt.ACTION_CHAT_STATUS";

    public static final String ACTION_RUN_TERMUX =
        "ru.copytermixgpt.ACTION_RUN_TERMUX";

    public static final String ACTION_AUTO_TOGGLE =
        "ru.copytermixgpt.ACTION_AUTO_TOGGLE";

    public static final String ACTION_RESET =
        "ru.copytermixgpt.ACTION_RESET";

    public static final String ACTION_TERMUX_DONE =
        "ru.copytermixgpt.ACTION_TERMUX_DONE";

    private static final String CHATGPT_PACKAGE = "com.openai.chatgpt";

    private final Handler handler = new Handler(Looper.getMainLooper());

    // AUTO_SEARCH_BEGIN
    private boolean autoRunning = false;
    private String autoPreviousSnapshot = null;
    private String autoMatchedWord = "";
    private int autoStagnantCount = 0;
    // Invalidates delayed work and gesture callbacks from previous AUTO sessions.
    private int autoRunToken = 0;

    private final Runnable autoStepRunnable = this::autoStep;
    // AUTO_SEARCH_END

    private WindowManager windowManager;
    private HighlightView highlight;
    private WindowManager.LayoutParams highlightParams;

    private boolean receiverRegistered;
    private boolean callbackReceiverRegistered;

    private static final String TERMUX_CALLBACK_TOKEN = "7f90efcb04111c31eb9f1d8aeeac9372";

    private final BroadcastReceiver termuxCallbackReceiver =
        new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (!ACTION_TERMUX_DONE.equals(intent.getAction())) return;

                Bundle pluginResult = intent.getBundleExtra("result");
                boolean trustedPendingResult = pluginResult != null;

                String suppliedToken = intent.getStringExtra("callback_token");
                if (!trustedPendingResult &&
                    !TERMUX_CALLBACK_TOKEN.equals(suppliedToken)) {
                    log("Отклонён неподтверждённый callback Termux");
                    return;
                }
                if (!awaitingTermuxResult) {
                    log("Callback без ожидающей команды проигнорирован");
                    return;
                }

                awaitingTermuxResult = false;
                termuxProcessing = false;
                String code;
                String resultText;

                if (pluginResult != null) {
                    String stdout = pluginResult.getString("stdout", "");
                    String stderr = pluginResult.getString("stderr", "");
                    int exitCode = pluginResult.getInt("exitCode", 1);
                    int internalError = pluginResult.getInt("err", -1);
                    String internalMessage =
                        pluginResult.getString("errmsg", "");

                    code = Integer.toString(exitCode);
                    StringBuilder combined = new StringBuilder();

                    if (!stdout.isEmpty()) combined.append(stdout);

                    if (!stderr.isEmpty()) {
                        if (combined.length() > 0) combined.append("\\n");
                        combined.append("===== STDERR =====\\n");
                        combined.append(stderr);
                    }

                    int stdoutOriginal = pluginResult.getInt(
                        "stdout_original_length", stdout.length());
                    int stderrOriginal = pluginResult.getInt(
                        "stderr_original_length", stderr.length());

                    if (stdoutOriginal > stdout.length() ||
                        stderrOriginal > stderr.length()) {
                        combined.append("\\n[ВНИМАНИЕ: Termux ограничил размер callback. Полный результат сохранён в Assistant_response.txt]");
                    }

                    if (internalError != -1) {
                        code = "1";
                        combined.append("\\nОШИБКА ВНУТРЕННЕГО МЕХАНИЗМА TERMUX: ");
                        combined.append(internalMessage);
                    }

                    resultText = combined.toString();
                } else {
                    code = intent.getStringExtra("exit_code");
                    resultText = intent.getStringExtra("result_text");
                }

                if (resultText == null) {
                    log("ОШИБКА: callback Termux не содержит результата");
                    status("command_error");
                    Toast.makeText(ChatAccessibilityService.this,
                        "Termux не передал результат",
                        Toast.LENGTH_LONG).show();
                    return;
                }

                latestTermuxResult = resultText;
                latestTermuxResultTruncated = pluginResult != null &&
                    (pluginResult.getInt("stdout_original_length",
                        pluginResult.getString("stdout", "").length()) >
                        pluginResult.getString("stdout", "").length() ||
                     pluginResult.getInt("stderr_original_length",
                        pluginResult.getString("stderr", "").length()) >
                        pluginResult.getString("stderr", "").length());

                log("Получен результат Termux. Символов: " + resultText.length());
                log("Termux завершил команду. Код: " + code);

                if ("0".equals(code)) {
                    status("command_done");
                    Toast.makeText(ChatAccessibilityService.this,
                        "Команда выполнена. Передаю результат в ChatGPT...",
                        Toast.LENGTH_LONG).show();
                } else {
                    status("command_error");
                    Toast.makeText(ChatAccessibilityService.this,
                        "Команда завершилась с ошибкой. Передаю вывод в ChatGPT...",
                        Toast.LENGTH_LONG).show();
                }

                handler.postDelayed(() -> {
                    if (!awaitingTermuxResult && !termuxProcessing &&
                        !processing && latestTermuxResult != null) {
                        log("Callback получен. Запускаю основную цепочку: результат Termux → ChatGPT");
                        startCycle();
                    }
                }, 700);
            }
        };
    // COPY_BLOCK_AUTO_COMPLETION_V1
    private boolean copyBlockGenerationArmed = false;

    private boolean processing;
    private boolean termuxProcessing;
    private boolean awaitingTermuxResult;
    private String insertedText = "";

    private String fullText = "";

    private String latestTermuxResult = null;
    private boolean latestTermuxResultTruncated = false;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (ACTION_COPY_RESPONSE.equals(action)) {
                copyResponseToClipboard();
            } else if (ACTION_RUN_TERMUX.equals(action)) {
                runTermuxCommand(intent.getStringExtra("command"));
            } else if (ACTION_RESET.equals(action)) {
                resetCurrentOperation();
            } else if (ACTION_AUTO_TOGGLE.equals(action)) {
                log("AUTO: команда переключения получена");
                toggleAutoSearch();
            }
        }
    };

    @Override
    protected void onServiceConnected() {
 passportScanEnsureButton(); // PASSPORT_SCAN_INIT

        super.onServiceConnected();

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        IntentFilter filter = new IntentFilter(ACTION_COPY_RESPONSE);
        filter.addAction(ACTION_RUN_TERMUX);
        filter.addAction(ACTION_RESET);
        filter.addAction(ACTION_AUTO_TOGGLE);

        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(receiver, filter);
        }
        receiverRegistered = true;

        IntentFilter callbackFilter = new IntentFilter(ACTION_TERMUX_DONE);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(
                termuxCallbackReceiver,
                callbackFilter,
                Context.RECEIVER_EXPORTED
            );
        } else {
            registerReceiver(termuxCallbackReceiver, callbackFilter);
        }
        callbackReceiverRegistered = true;

        DiagnosticLogger.log("AccessibilityService подключён");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;

        String packageName = String.valueOf(event.getPackageName());
        if (!CHATGPT_PACKAGE.equals(packageName)) return;

        int eventType = event.getEventType();

        boolean relevantEvent =
                eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
                eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED ||
                eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED ||
                eventType == AccessibilityEvent.TYPE_VIEW_CLICKED;

        if (!relevantEvent) return;

        // Существующая диагностика нажатий сохраняется.
        if (eventType != AccessibilityEvent.TYPE_VIEW_CLICKED) return;

        log("===== USER CLICK BEGIN =====");
        log("CLICK PACKAGE=" + packageName);
        log("CLICK CLASS=" + event.getClassName());
        log("CLICK TEXT=" + event.getText());
        log("CLICK DESCRIPTION=" + event.getContentDescription());
        log("CLICK SOURCE WINDOW=" + event.getWindowId());

        AccessibilityNodeInfo source = event.getSource();

        if (source != null) {
            dumpClickedNode(source);
            source.recycle();
        } else {
            log("CLICK SOURCE: недоступен");
        }

        log("===== USER CLICK END =====");
    }

    private void dumpClickedNode(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo current =
            AccessibilityNodeInfo.obtain(node);

        for (int level = 0; current != null && level <= 5; level++) {
            Rect bounds = new Rect();
            current.getBoundsInScreen(bounds);

            String label = copyNodeLabel(current);
            CharSequence cls = current.getClassName();
            String viewId = "";

            try {
                viewId = current.getViewIdResourceName();
            } catch (Exception ignored) {}

            log("CLICK NODE LEVEL=" + level
                + " CLASS=" + cls
                + " LABEL='" + label + "'"
                + " VIEW_ID='" + viewId + "'"
                + " BOUNDS=" + bounds.toShortString()
                + " CLICKABLE=" + current.isClickable()
                + " ENABLED=" + current.isEnabled()
                + " VISIBLE=" + current.isVisibleToUser()
                + " ACTIONS=" + current.getActionList());

            AccessibilityNodeInfo parent = null;

            try {
                parent = current.getParent();
            } catch (Exception e) {
                log("CLICK PARENT ERROR=" + e);
            }

            current.recycle();
            current = parent;
        }
    }

    @Override
    public void onInterrupt() {
        DiagnosticLogger.log("AccessibilityService прерван");

        // Прерывание сервиса завершает AUTO и делает его callbacks устаревшими.
        autoRunning = false;
        autoRunToken++;
        handler.removeCallbacks(autoStepRunnable);

        removeHighlight();
        clearAutoButtonHighlights();
        processing = false;
    }



    // AUTO_SEARCH_BEGIN
    private java.util.List<String> loadAutoWords() {
        String raw = getSharedPreferences(
            "auto_search", MODE_PRIVATE
        ).getString("words", "");

        java.util.List<String> words = new java.util.ArrayList<>();

        for (String line : raw.split("\\r?\\n")) {
            String word = line.trim().toLowerCase(Locale.ROOT);
            if (!word.isEmpty() && !words.contains(word)) {
                words.add(word);
            }
        }
        return words;
    }

    private void toggleAutoSearch() {
        if (autoRunning) {
            stopAutoSearch("Поиск остановлен пользователем.", "auto_stopped");
            return;
        }

        java.util.List<String> words = loadAutoWords();

        if (words.isEmpty()) {
            Toast.makeText(this,
                "Сначала задайте и сохраните поисковые слова",
                Toast.LENGTH_LONG).show();
            return;
        }

        autoRunToken++;
        autoRunning = true;
        autoPreviousSnapshot = null;
        autoMatchedWord = "";
        autoStagnantCount = 0;

        status("auto_running");

        Toast.makeText(this,
            "AUTO: поиск начат. Слов: " + words.size(),
            Toast.LENGTH_SHORT).show();

        handler.postDelayed(autoStepRunnable, 300);
    }


    private void stopAutoSearch(String message, String statusValue) {
        autoRunning = false;
        autoRunToken++;
        handler.removeCallbacks(autoStepRunnable);
        clearAutoButtonHighlights();
        status(statusValue);

        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        log("AUTO: " + message);
    }


    private void autoStep() {
        if (!autoRunning) return;

        AccessibilityNodeInfo root = getRootInActiveWindow();

        if (root == null) {
            stopAutoSearch(
                "AUTO: окно ChatGPT недоступно.",
                "auto_error"
            );
            return;
        }

        if (!CHATGPT_PACKAGE.equals(
            String.valueOf(root.getPackageName()))) {
            root.recycle();
            stopAutoSearch(
                "AUTO остановлен: откройте чат ChatGPT.",
                "auto_error"
            );
            return;
        }

        java.util.List<String> words = loadAutoWords();

        if (words.isEmpty()) {
            root.recycle();
            stopAutoSearch(
                "Список поисковых слов пуст.",
                "auto_error"
            );
            return;
        }

        int[] autoStats = new int[]{0, 0, 0, 0};

        AccessibilityNodeInfo match =
            findAutoMatch(root, words, 0, autoStats);

        StringBuilder allText = new StringBuilder();
        collectAutoDiagnosticText(root, 0, new int[]{0}, allText);

        log("AUTO DIAG: words=" + words.size()
            + " nodes=" + autoStats[0]
            + " textNodes=" + autoStats[1]
            + " chars=" + autoStats[2]
            + " aggregateChars=" + allText.length()
            + " eligible=" + autoStats[3]
            + " result=" + (match != null ? "MATCH" : "MISS"));

        if (match == null) {
            String aggregate = allText.toString().toLowerCase(Locale.ROOT);
            for (String word : words) {
                log("AUTO WORD CHECK: word='" + word
                    + "' aggregateContains=" + aggregate.contains(word));
            }
        }

        if (match != null) {
            String found = autoMatchedWord;

            match.recycle();
            root.recycle();

            log("AUTO POSITION: найдено '" + found
                + "'. Целевая высота: 2/3 экрана.");

            final int runToken = autoRunToken;
            handler.postDelayed(() -> {
                if (autoRunning && autoRunToken == runToken) {
                    positionAutoMatch(found, runToken);
                }
            }, 350);
            return;
        }

        String snapshot =
            collectAutoSnapshot(root, 0, new int[]{0});

        if (snapshot.equals(autoPreviousSnapshot)) {
            autoStagnantCount++;
        } else {
            autoStagnantCount = 0;
        }

        autoPreviousSnapshot = snapshot;

        if (autoStagnantCount >= 3) {
            root.recycle();
            stopAutoSearch(
                "Прокрутка завершена: новых сообщений не обнаружено.",
                "auto_stopped"
            );
            return;
        }

        // Не доверяем ACTION_SCROLL_FORWARD:
        // он может вернуть true без фактического перемещения чата.
        root.recycle();

        log("AUTO: выполняется принудительный жест прокрутки");
        performAutoSwipe();
    }


    private void positionAutoMatch(String word, int runToken) {
        if (!autoRunning || autoRunToken != runToken) return;

        AccessibilityNodeInfo root = getRootInActiveWindow();

        if (root == null) {
            stopAutoSearch(
                "Слово найдено, но не удалось получить координаты.",
                "auto_found"
            );
            return;
        }

        if (!CHATGPT_PACKAGE.equals(String.valueOf(root.getPackageName()))) {
            root.recycle();
            stopAutoSearch("Слово найдено.", "auto_found");
            return;
        }

        java.util.List<String> target =
            java.util.Collections.singletonList(word);

        AccessibilityNodeInfo node =
            findAutoMatch(root, target, 0, new int[]{0, 0, 0, 0});

        if (node == null) {
            root.recycle();
            log("AUTO POSITION: элемент не найден повторно.");
            stopAutoSearch("Найдено слово: " + word, "auto_found");
            return;
        }

        android.graphics.Rect bounds = new android.graphics.Rect();
        node.getBoundsInScreen(bounds);

        android.graphics.Rect windowBounds =
            new android.graphics.Rect();
        root.getBoundsInScreen(windowBounds);

        node.recycle();
        root.recycle();

        int targetY = windowBounds.top
            + (int) (windowBounds.height() * 2.0f / 3.0f);
        int currentY = bounds.centerY();

        log("AUTO POSITION: window="
            + windowBounds.toShortString()
            + " element=" + bounds.toShortString());
        int delta = targetY - currentY;

        log("AUTO POSITION: bounds=" + bounds.toShortString()
            + " currentY=" + currentY
            + " targetY=" + targetY
            + " delta=" + delta);

        if (bounds.isEmpty() || Math.abs(delta) < 20) {
            diagnoseCopyButton(word);
            stopAutoSearch(
                "Слово найдено: " + word + ". Проверка кнопки копирования записана в журнал.",
                "auto_found"
            );
            return;
        }

        int x = Math.max(windowBounds.left + 1,
            Math.min(windowBounds.right - 2, bounds.centerX()));
        int startY = Math.max(windowBounds.top + 1,
            Math.min(windowBounds.bottom - 2, currentY));

        int endY = Math.max(
            1,
            Math.min(windowBounds.bottom - 2, startY + delta)
        );

        if (Math.abs(endY - startY) < 10) {
            log("AUTO POSITION: корректирующий жест невозможен.");
            stopAutoSearch("Найдено слово: " + word, "auto_found");
            return;
        }

        android.graphics.Path path = new android.graphics.Path();
        path.moveTo(x, startY);
        path.lineTo(x, endY);

        GestureDescription gesture =
            new GestureDescription.Builder()
                .addStroke(
                    new GestureDescription.StrokeDescription(
                        path, 0, 350
                    )
                )
                .build();

        boolean accepted = dispatchGesture(
            gesture,
            new GestureResultCallback() {
                @Override
                public void onCompleted(GestureDescription description) {
                    if (!autoRunning || autoRunToken != runToken) {
                        log("AUTO POSITION: устаревший callback проигнорирован.");
                        return;
                    }
                    log("AUTO POSITION: корректирующий жест выполнен.");
                    diagnoseCopyButton(word);
                    stopAutoSearch(
                        "Слово найдено: " + word + ". Проверка кнопки копирования записана в журнал.",
                        "auto_found"
                    );
                }

                @Override
                public void onCancelled(GestureDescription description) {
                    if (!autoRunning || autoRunToken != runToken) {
                        log("AUTO POSITION: устаревший callback отмены проигнорирован.");
                        return;
                    }
                    log("AUTO POSITION: жест отменён.");
                    stopAutoSearch(
                        "Найдено слово: " + word
                            + ". Точное позиционирование не подтверждено.",
                        "auto_found"
                    );
                }
            },
            handler
        );

        log("AUTO POSITION: dispatchGesture accepted=" + accepted);

        if (!accepted && autoRunning && autoRunToken == runToken) {
            stopAutoSearch(
                "Найдено слово: " + word
                    + ". Корректирующий жест не принят.",
                "auto_found"
            );
        }
    }



    private String copyNodeLabel(AccessibilityNodeInfo node) {
        if (node == null) return "";
        CharSequence text = node.getText();
        CharSequence desc = node.getContentDescription();
        String value = (text == null ? "" : text.toString())
            + " / "
            + (desc == null ? "" : desc.toString());
        value = value.replace('\n', ' ').replace('\r', ' ');
        return value.length() > 110
            ? value.substring(0, 110) + "..."
            : value;
    }

    private void logCopyNode(String tag, AccessibilityNodeInfo node) {
        if (node == null) return;

        Rect b = new Rect();
        node.getBoundsInScreen(b);

        log("COPY TREE " + tag
            + " class=" + node.getClassName()
            + " label='" + copyNodeLabel(node) + "'"
            + " bounds=" + b.toShortString()
            + " clickable=" + node.isClickable()
            + " enabled=" + node.isEnabled()
            + " visible=" + node.isVisibleToUser()
            + " actions=" + node.getActionList()
            + " children=" + node.getChildCount());
    }


    private void dumpCopySubtree(
        String path,
        AccessibilityNodeInfo node,
        int depth,
        int[] count
    ) {
        if (node == null || depth > 14 || count[0] >= 400) return;

        count[0]++;

        Rect bounds = new Rect();
        node.getBoundsInScreen(bounds);

        String actions = "";
        java.util.List<AccessibilityNodeInfo.AccessibilityAction> list =
            node.getActionList();

        for (AccessibilityNodeInfo.AccessibilityAction action : list) {
            if (actions.length() > 180) break;
            if (actions.length() > 0) actions += ",";
            actions += action.getId() + ":" + action.getLabel();
        }

        log("COPY DEEP " + path
            + " depth=" + depth
            + " class=" + node.getClassName()
            + " label='" + copyNodeLabel(node) + "'"
            + " bounds=" + bounds.toShortString()
            + " clickable=" + node.isClickable()
            + " enabled=" + node.isEnabled()
            + " visible=" + node.isVisibleToUser()
            + " actions=[" + actions + "]"
            + " children=" + node.getChildCount());

        for (int i = 0; i < node.getChildCount(); i++) {
            if (count[0] >= 400) break;

            AccessibilityNodeInfo child = node.getChild(i);
            if (child == null) continue;

            dumpCopySubtree(
                path + "." + i,
                child,
                depth + 1,
                count
            );

            child.recycle();
        }
    }


    // AUTO_MATCH_CONTEXT_COPY_V1
    private AccessibilityNodeInfo findAutoCopyTargetInMatchContext(
            AccessibilityNodeInfo match) {

        if (match == null) return null;

        AccessibilityNodeInfo scope = null;

        try {
            scope = match.getParent();

            for (int level = 1; level <= 7 && scope != null; level++) {
                int[] visited = new int[]{0};

                AccessibilityNodeInfo target =
                        findAutoCopyTargetInSubtree(scope, 0, visited);

                if (target != null) {
                    log("AUTO MATCH COPY: target найден, contextLevel="
                            + level + " visited=" + visited[0]);

                    scope.recycle();
                    scope = null;
                    return target;
                }

                AccessibilityNodeInfo next = scope.getParent();
                scope.recycle();
                scope = next;
            }

        } catch (Exception e) {
            log("AUTO MATCH COPY SEARCH ERROR: " + e);
        } finally {
            if (scope != null) {
                try { scope.recycle(); } catch (Exception ignored) {}
            }
        }

        return null;
    }

    private AccessibilityNodeInfo findAutoCopyTargetInSubtree(
            AccessibilityNodeInfo node,
            int depth,
            int[] visited) {

        if (node == null || depth > 55 || visited[0] >= 1800) {
            return null;
        }

        visited[0]++;

        try {
            if (isSecondPassportCopyCodeButton(node)) {
                AccessibilityNodeInfo parent = node.getParent();

                if (parent != null) {
                    boolean valid =
                            parent.isVisibleToUser()
                            && parent.isEnabled()
                            && parent.isClickable()
                            && secondPassportHasClickAction(parent)
                            && secondPassportHasCopyDescription(parent, 0, 2);

                    if (valid) return parent;
                    parent.recycle();
                }
            }

            int count = Math.min(node.getChildCount(), 80);

            for (int i = 0; i < count; i++) {
                AccessibilityNodeInfo child = node.getChild(i);
                if (child == null) continue;

                AccessibilityNodeInfo found = null;

                try {
                    found = findAutoCopyTargetInSubtree(
                            child, depth + 1, visited);
                } finally {
                    child.recycle();
                }

                if (found != null) return found;
                if (visited[0] >= 1800) break;
            }

        } catch (Exception e) {
            log("AUTO MATCH COPY TREE ERROR: " + e);
        }

        return null;
    }


    private void clickAutoCopyForMatchedWord(
            AccessibilityNodeInfo match,
            String word) {

        AccessibilityNodeInfo target =
                findAutoCopyTargetInMatchContext(match);

        if (target == null) {
            log("AUTO MATCH COPY: кнопка не найдена в контексте слова '"
                    + word + "'");
            clearAutoButtonHighlights();
            stopAutoSearch("Кнопка копирования не найдена", "Ошибка");
            return;
        }

        final android.graphics.Rect tapBounds =
                new android.graphics.Rect();
        final android.content.ClipboardManager clipboard =
                (android.content.ClipboardManager)
                        getSystemService(CLIPBOARD_SERVICE);
        final boolean[] copied = new boolean[]{false};

        // Holder объявлен ДО listener: Java не обращается
        // к локальной переменной до её объявления.
        final android.content.ClipboardManager.OnPrimaryClipChangedListener[]
                listenerRef = new android.content.ClipboardManager
                        .OnPrimaryClipChangedListener[1];

        final android.content.ClipboardManager.OnPrimaryClipChangedListener
                listener = () -> {
            handler.post(() -> {
                if (copied[0]) return;
                copied[0] = true;

                try {
                    if (clipboard != null && listenerRef[0] != null) {
                        clipboard.removePrimaryClipChangedListener(
                                listenerRef[0]);
                    }
                } catch (Exception ignored) {}

                clearAutoButtonHighlights();
                stopAutoSearch("Код скопирован", "Готово");
                log("AUTO MATCH COPY: изменение буфера обмена обнаружено");
            });
        };
        listenerRef[0] = listener;

        try {
            target.getBoundsInScreen(tapBounds);

            boolean valid =
                    target.isVisibleToUser()
                    && target.isEnabled()
                    && target.isClickable()
                    && secondPassportHasClickAction(target)
                    && secondPassportHasCopyDescription(target, 0, 2)
                    && !tapBounds.isEmpty();

            log("AUTO MATCH COPY TARGET"
                    + " class=" + target.getClassName()
                    + " bounds=" + tapBounds.toShortString()
                    + " visible=" + target.isVisibleToUser()
                    + " enabled=" + target.isEnabled()
                    + " clickable=" + target.isClickable()
                    + " actionClick=" + secondPassportHasClickAction(target)
                    + " passport=" + valid);

            if (!valid) {
                clearAutoButtonHighlights();
                stopAutoSearch("Проверка кнопки копирования не пройдена",
                        "Ошибка");
                return;
            }

            target.performAction(
                    AccessibilityNodeInfo.AccessibilityAction
                            .ACTION_SHOW_ON_SCREEN.getId()
            );
            target.getBoundsInScreen(tapBounds);

            if (clipboard != null) {
                clipboard.addPrimaryClipChangedListener(listener);
            }

            boolean clicked = target.performAction(
                    AccessibilityNodeInfo.ACTION_CLICK
            );

            log("AUTO MATCH COPY: ACTION_CLICK=" + clicked
                    + " word='" + word + "'");

            handler.postDelayed(() -> {
                if (copied[0]) return;

                log("AUTO MATCH COPY: ACTION_CLICK не подтвердил копирование; "
                        + "резервный тап");

                // Убираем overlay, чтобы он не перекрывал кнопку.
                clearAutoButtonHighlights();

                if (tapBounds.isEmpty()) {
                    finishAutoCopyFailure(clipboard, listenerRef,
                            "Координаты кнопки недоступны");
                    return;
                }

                android.graphics.Path path = new android.graphics.Path();
                path.moveTo(tapBounds.centerX(), tapBounds.centerY());

                android.accessibilityservice.GestureDescription gesture =
                        new android.accessibilityservice.GestureDescription
                                .Builder()
                                .addStroke(
                                        new android.accessibilityservice
                                                .GestureDescription
                                                .StrokeDescription(
                                                path, 0, 100
                                        )
                                )
                                .build();

                boolean dispatched = dispatchGesture(
                        gesture,
                        new android.accessibilityservice
                                .AccessibilityService.GestureResultCallback() {
                            @Override
                            public void onCompleted(
                                    android.accessibilityservice
                                            .GestureDescription description) {
                                log("AUTO MATCH COPY: резервный тап выполнен");

                                handler.postDelayed(() -> {
                                    if (!copied[0]) {
                                        finishAutoCopyFailure(
                                                clipboard,
                                                listenerRef,
                                                "Копирование не подтверждено"
                                        );
                                    }
                                }, 1100);
                            }

                            @Override
                            public void onCancelled(
                                    android.accessibilityservice
                                            .GestureDescription description) {
                                finishAutoCopyFailure(
                                        clipboard,
                                        listenerRef,
                                        "Android отменил резервный тап"
                                );
                            }
                        },
                        null
                );

                log("AUTO MATCH COPY: dispatchGesture=" + dispatched);

                if (!dispatched) {
                    finishAutoCopyFailure(
                            clipboard,
                            listenerRef,
                            "Android не принял резервный тап"
                    );
                }
            }, 650);

        } catch (Exception e) {
            try {
                if (clipboard != null) {
                    clipboard.removePrimaryClipChangedListener(listener);
                }
            } catch (Exception ignored) {}

            clearAutoButtonHighlights();
            log("AUTO MATCH COPY CLICK ERROR: " + e);
            stopAutoSearch("Ошибка нажатия кнопки копирования", "Ошибка");
        } finally {
            target.recycle();
        }
    }

    private void finishAutoCopyFailure(
            android.content.ClipboardManager clipboard,
            android.content.ClipboardManager.OnPrimaryClipChangedListener[] listenerRef,
            String reason) {

        try {
            if (clipboard != null && listenerRef[0] != null) {
                clipboard.removePrimaryClipChangedListener(listenerRef[0]);
            }
        } catch (Exception ignored) {}

        clearAutoButtonHighlights();
        log("AUTO MATCH COPY FAILED: " + reason);
        stopAutoSearch(reason, "Проверьте результат");
    }


    private void diagnoseCopyButton(String word) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) {
            log("COPY DIAG: root недоступен");
            return;
        }

        if (!CHATGPT_PACKAGE.equals(String.valueOf(root.getPackageName()))) {
            root.recycle();
            log("COPY DIAG: открыто не окно ChatGPT");
            return;
        }

        AccessibilityNodeInfo match = findAutoMatch(
            root,
            java.util.Collections.singletonList(word),
            0,
            new int[]{0, 0, 0, 0}
        );

        if (match == null) {
            root.recycle();
            log("COPY DIAG: найденное слово повторно не обнаружено");
            return;
        }

        log("COPY DIAG BEGIN word='" + word + "'");
        logCopyNode("MATCH", match);

        AccessibilityNodeInfo current = AccessibilityNodeInfo.obtain(match);

        for (int level = 1; level <= 7 && current != null; level++) {
            AccessibilityNodeInfo parent = current.getParent();
            current.recycle();
            current = parent;

            if (current == null) break;

            logCopyNode("ANCESTOR-" + level, current);

            if (level == 1) {
                log("COPY DEEP BEGIN: родитель найденного слова");
                dumpCopySubtree(
                    "MATCH_PARENT",
                    current,
                    0,
                    new int[]{0}
                );
                log("COPY DEEP END");
            }

            int childCount = Math.min(current.getChildCount(), 40);

            for (int i = 0; i < childCount; i++) {
                AccessibilityNodeInfo child = current.getChild(i);
                if (child == null) continue;

                logCopyNode("A" + level + "-CHILD-" + i, child);

                int grandCount = Math.min(child.getChildCount(), 20);
                for (int j = 0; j < grandCount; j++) {
                    AccessibilityNodeInfo grand = child.getChild(j);
                    if (grand == null) continue;

                    logCopyNode(
                        "A" + level + "-CHILD-" + i + "-SUB-" + j,
                        grand
                    );
                    grand.recycle();
                }

                child.recycle();
            }
        }

        if (current != null) current.recycle();

        // Сначала обычные кнопки, затем точная геометрическая
        // привязка Copy к верхней правой части контейнера команды.
        highlightAutoButtons(root);
        highlightCopyButtonInMatchContainer(match);

        // Слово найдено, прокрутка остановлена.
        clickAutoCopyForMatchedWord(match, word);

        match.recycle();
        root.recycle();

        log("COPY DIAG END: обработка контекста завершена");
    }

    private void collectAutoDiagnosticText(
        AccessibilityNodeInfo node,
        int depth,
        int[] count,
        StringBuilder output
    ) {
        if (node == null || depth > 45 || count[0]++ >= 1200) return;

        if (node.isVisibleToUser() && !node.isEditable()) {
            CharSequence text = node.getText();
            CharSequence desc = node.getContentDescription();

            if (text != null) output.append(text).append(' ');
            if (desc != null) output.append(desc).append(' ');
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child == null) continue;

            collectAutoDiagnosticText(child, depth + 1, count, output);
            child.recycle();

            if (output.length() > 50000) return;
        }
    }

    private AccessibilityNodeInfo findAutoMatch(
        AccessibilityNodeInfo node,
        java.util.List<String> words,
        int depth,
        int[] stats
    ) {
        if (node == null || depth > 45 || stats[0] >= 1200) {
            return null;
        }

        stats[0]++;

        CharSequence text = node.getText();
        CharSequence desc = node.getContentDescription();

        int textLength = text == null ? 0 : text.length();
        int descLength = desc == null ? 0 : desc.length();

        if (textLength > 0 || descLength > 0) {
            stats[1]++;
            stats[2] += textLength + descLength;
        }

        boolean eligible =
            node.isVisibleToUser() && !node.isEditable();

        if (eligible) stats[3]++;

        // Сначала проверяем дочерние элементы.
        // Так выбирается конкретный текст, а не весь контейнер сообщения.
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child == null) continue;

            AccessibilityNodeInfo found =
                findAutoMatch(child, words, depth + 1, stats);

            child.recycle();

            if (found != null) return found;
        }

        if (eligible && (textLength > 0 || descLength > 0)) {
            String value = "";

            if (text != null) value += text.toString();
            if (desc != null) value += " " + desc.toString();

            value = value.toLowerCase(Locale.ROOT);

            for (String word : words) {
                if (value.contains(word)) {
                    autoMatchedWord = word;
                    return AccessibilityNodeInfo.obtain(node);
                }
            }
        }

        return null;
    }
    private String collectAutoSnapshot(
        AccessibilityNodeInfo node,
        int depth,
        int[] count
    ) {
        if (node == null || depth > 45 || count[0]++ >= 1200) {
            return "";
        }

        StringBuilder result = new StringBuilder();

        if (node.isVisibleToUser() && !node.isEditable()) {
            CharSequence text = node.getText();
            CharSequence desc = node.getContentDescription();

            if ((text != null && text.length() > 0)
                    || (desc != null && desc.length() > 0)) {
                Rect bounds = new Rect();
                node.getBoundsInScreen(bounds);

                result.append(bounds.toShortString()).append(':');
                if (text != null) result.append(text);
                result.append('|');
                if (desc != null) result.append(desc);
                result.append(';');
            }
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child == null) continue;

            try {
                result.append(
                    collectAutoSnapshot(child, depth + 1, count)
                );
            } finally {
                child.recycle();
            }

            if (result.length() > 30000) break;
        }

        return result.toString();
    }

    private AccessibilityNodeInfo findAutoScrollable(
        AccessibilityNodeInfo node,
        int depth,
        int[] count
    ) {
        if (node == null || depth > 45 || count[0]++ > 1200) {
            return null;
        }

        if (node.isVisibleToUser() && node.isScrollable()) {
            return AccessibilityNodeInfo.obtain(node);
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child == null) continue;

            AccessibilityNodeInfo found =
                findAutoScrollable(child, depth + 1, count);

            child.recycle();

            if (found != null) return found;
        }

        return null;
    }

    
    
    private android.graphics.Rect findAutoScrollableBounds(
            AccessibilityNodeInfo node,
            int depth,
            int[] visited,
            int screenWidth,
            int screenHeight,
            android.graphics.Rect rootBounds) {

        if (node == null || depth > 22 || visited[0] >= 350
                || !node.isVisibleToUser()) {
            return null;
        }

        visited[0]++;
        android.graphics.Rect best = null;

        try {
            if (node.isScrollable()) {
                android.graphics.Rect bounds = new android.graphics.Rect();
                node.getBoundsInScreen(bounds);

                boolean insideWindow = !bounds.isEmpty()
                        && bounds.intersect(rootBounds)
                        && bounds.intersect(0, 0, screenWidth, screenHeight);

                if (insideWindow
                        && bounds.width() >= screenWidth * 0.55f
                        && bounds.height() >= screenHeight * 0.28f) {
                    best = new android.graphics.Rect(bounds);
                }
            }

            int childCount = node.getChildCount();

            for (int i = 0; i < childCount && visited[0] < 350; i++) {
                AccessibilityNodeInfo child = null;

                try {
                    child = node.getChild(i);

                    if (child != null) {
                        android.graphics.Rect candidate =
                                findAutoScrollableBounds(
                                        child,
                                        depth + 1,
                                        visited,
                                        screenWidth,
                                        screenHeight,
                                        rootBounds
                                );

                        if (candidate != null) {
                            long candidateArea =
                                    (long) candidate.width() * candidate.height();

                            long bestArea = best == null ? Long.MAX_VALUE :
                                    (long) best.width() * best.height();

                            // Вложенная область прокрутки предпочтительнее
                            // общего scrollable-контейнера окна.
                            if (candidateArea < bestArea) {
                                best = candidate;
                            }
                        }
                    }
                } catch (Exception e) {
                    log("AUTO SCROLL BOUNDS: ошибка чтения узла: " + e);
                } finally {
                    if (child != null) {
                        try { child.recycle(); } catch (Exception ignored) {}
                    }
                }
            }
        } catch (Exception e) {
            log("AUTO SCROLL BOUNDS: ошибка обхода: " + e);
        }

        return best;
    }

    private void performAutoSwipe() {
        if (!autoRunning) return;
        final int runToken = autoRunToken;

        AccessibilityNodeInfo root = getRootInActiveWindow();

        if (root == null) {
            stopAutoSearch(
                    "Не удалось получить окно ChatGPT для прокрутки.",
                    "auto_error"
            );
            return;
        }

        android.graphics.Rect rootBounds = new android.graphics.Rect();
        root.getBoundsInScreen(rootBounds);

        String packageName = String.valueOf(root.getPackageName());

        if (!CHATGPT_PACKAGE.equals(packageName)) {
            root.recycle();
            stopAutoSearch(
                    "Активное окно больше не принадлежит ChatGPT.",
                    "auto_error"
            );
            return;
        }

        android.util.DisplayMetrics realMetrics =
                new android.util.DisplayMetrics();

        android.view.WindowManager wm =
                (android.view.WindowManager) getSystemService(WINDOW_SERVICE);

        if (wm == null) {
            root.recycle();
            stopAutoSearch(
                    "Не удалось определить размеры экрана.",
                    "auto_error"
            );
            return;
        }

        wm.getDefaultDisplay().getRealMetrics(realMetrics);

        int screenWidth = realMetrics.widthPixels;
        int screenHeight = realMetrics.heightPixels;

        int[] visited = new int[]{0};

        android.graphics.Rect target =
                findAutoScrollableBounds(
                        root,
                        0,
                        visited,
                        screenWidth,
                        screenHeight,
                        rootBounds
                );

        boolean scrollableFound = target != null;

        if (target == null) {
            // ChatGPT may expose no scrollable Accessibility node.
            // Use only the central conversation viewport: exclude the
            // top toolbar and, critically, the bottom composer/input area.
            target = new android.graphics.Rect(rootBounds);

            int fallbackTop = (int) (target.height() * 0.18f);
            int fallbackBottom = (int) (target.height() * 0.24f);

            target.top += fallbackTop;
            target.bottom -= fallbackBottom;
        } else {
            // Even a scrollable ancestor can include the composer.
            // Keep the gesture away from the lower controls.
            int insetTop = (int) (target.height() * 0.10f);
            int insetBottom = (int) (target.height() * 0.18f);

            target.top += insetTop;
            target.bottom -= insetBottom;
        }

        root.recycle();

        target.left = Math.max(0, target.left);
        target.top = Math.max(0, target.top);
        target.right = Math.min(screenWidth, target.right);
        target.bottom = Math.min(screenHeight, target.bottom);

        int width = target.width();
        int height = target.height();

        if (width < screenWidth * 0.45f || height < 220) {
            log("AUTO SWIPE: недопустимая область прокрутки " + target);
            stopAutoSearch(
                    "Не удалось определить безопасную область текста ChatGPT.",
                    "auto_error"
            );
            return;
        }

        int x = target.left + width / 2;

        // Swipe inside the conversation body, not over the composer.
        // Finger moves upward: the chat content advances toward later text.
        int startY = target.top + (int) (height * 0.72f);
        int endY = target.top + (int) (height * 0.28f);

        if (startY <= endY) {
            stopAutoSearch(
                    "Ошибка расчёта направления свайпа.",
                    "auto_error"
            );
            return;
        }

        android.graphics.Path path = new android.graphics.Path();
        path.moveTo(x, startY);
        path.lineTo(x, endY);

        log("AUTO SWIPE: package=" + packageName
                + " screen=" + screenWidth + "x" + screenHeight
                + " root=" + rootBounds
                + " scrollableFound=" + scrollableFound
                + " visitedNodes=" + visited[0]
                + " target=" + target
                + " fingerStart=(" + x + "," + startY + ")"
                + " fingerEnd=(" + x + "," + endY + ")"
                + " direction=BOTTOM_TO_TOP"
                + " viewport=COMPOSER_EXCLUDED"
                + " swipeRatio=72_TO_28");

        GestureDescription gesture =
                new GestureDescription.Builder()
                        .addStroke(
                                new GestureDescription.StrokeDescription(
                                        path, 0, 450
                                )
                        )
                        .build();

        boolean accepted = dispatchGesture(
                gesture,
                new GestureResultCallback() {
                    @Override
                    public void onCompleted(
                            GestureDescription description) {
                        if (!autoRunning || autoRunToken != runToken) {
                            log("AUTO SWIPE: устаревший callback завершения проигнорирован.");
                            return;
                        }

                        log("AUTO SWIPE: жест завершён");
                        handler.postDelayed(() -> {
                            if (autoRunning && autoRunToken == runToken) {
                                autoStep();
                            }
                        }, 1000);
                    }

                    @Override
                    public void onCancelled(
                            GestureDescription description) {
                        if (!autoRunning || autoRunToken != runToken) {
                            log("AUTO SWIPE: устаревший callback отмены проигнорирован.");
                            return;
                        }

                        log("AUTO SWIPE: жест отменён");
                        stopAutoSearch(
                                "Системный жест прокрутки отменён.",
                                "auto_error"
                        );
                    }
                },
                handler
        );

        log("AUTO SWIPE: dispatchGesture accepted=" + accepted);

        if (!accepted && autoRunning && autoRunToken == runToken) {
            stopAutoSearch(
                    "Android не принял жест прокрутки.",
                    "auto_error"
            );
        }
    }

    private boolean performAutoScrollForwardNode(
        AccessibilityNodeInfo node,
        int screenWidth,
        int screenHeight
    ) {
        if (node == null) return false;

        // Сначала проверяем вложенные области прокрутки.
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);

            if (child == null) continue;

            boolean done = performAutoScrollForwardNode(
                child,
                screenWidth,
                screenHeight
            );

            child.recycle();

            if (done) return true;
        }

        if (!node.isScrollable()) return false;

        android.graphics.Rect bounds = new android.graphics.Rect();
        node.getBoundsInScreen(bounds);

        int minWidth = (int) (screenWidth * 0.55f);
        int minHeight = (int) (screenHeight * 0.35f);

        // Не прокручиваем маленькие элементы и верхнюю панель.
        if (bounds.width() < minWidth
            || bounds.height() < minHeight
            || bounds.top < (int) (screenHeight * 0.05f)
            || bounds.bottom < (int) (screenHeight * 0.40f)) {
            return false;
        }

        boolean result = node.performAction(
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        );

        log("AUTO SCROLL NODE: class="
            + node.getClassName()
            + " bounds=" + bounds
            + " result=" + result);

        return result;
    }

    // AUTO_SEARCH_END

    private void runTermuxCommand(String command) {
        if (processing || openingChatGPT) {
            Toast.makeText(this,
                "Сначала дождись завершения отправки предыдущего результата",
                Toast.LENGTH_LONG).show();
            return;
        }

        if (termuxProcessing) {
            Toast.makeText(this, "Предыдущая команда ещё выполняется",
                Toast.LENGTH_SHORT).show();
            return;
        }

        command = command == null ? "" : command.trim();

        if (command.isEmpty()) {
            Toast.makeText(this, "Команда не получена",
                Toast.LENGTH_SHORT).show();
            return;
        }

        if (command.length() > 100000) {
            Toast.makeText(this, "Команда слишком длинная",
                Toast.LENGTH_LONG).show();
            return;
        }

        try {
            Intent termux = new Intent();
            termux.setClassName(
                "com.termux",
                "com.termux.app.RunCommandService"
            );
            termux.setAction("com.termux.RUN_COMMAND");
            termux.putExtra(
                "com.termux.RUN_COMMAND_PATH",
                "/data/data/com.termux/files/usr/bin/bash"
            );
            termux.putExtra(
                "com.termux.RUN_COMMAND_ARGUMENTS",
                new String[] {
                    "-c",
                    "exec \"$HOME/.termux/copy-termix-runner.sh\" \"$1\"",
                    "copy-termix-gpt",
                    command
                }
            );
            termux.putExtra(
                "com.termux.RUN_COMMAND_WORKDIR",
                "/data/data/com.termux/files/home"
            );
            termux.putExtra(
                "com.termux.RUN_COMMAND_BACKGROUND",
                true
            );

            latestTermuxResult = null;

            Intent callbackIntent = new Intent(ACTION_TERMUX_DONE);
            callbackIntent.setPackage(getPackageName());

            int requestCode = (int)
                (System.currentTimeMillis() & 0x7fffffff);

            int pendingFlags = PendingIntent.FLAG_ONE_SHOT;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                pendingFlags |= PendingIntent.FLAG_MUTABLE;
            }

            PendingIntent resultPendingIntent =
                PendingIntent.getBroadcast(
                    this,
                    requestCode,
                    callbackIntent,
                    pendingFlags
                );

            termux.putExtra(
                "com.termux.RUN_COMMAND_PENDING_INTENT",
                resultPendingIntent
            );

            awaitingTermuxResult = true;
            termuxProcessing = true;
            status("busy");

            startService(termux);

            log("Команда передана в Termux. Длина: " + command.length());

            Toast.makeText(this, "Команда передана в Termux",
                Toast.LENGTH_SHORT).show();

        } catch (Exception e) {
            termuxProcessing = false;
            awaitingTermuxResult = false;
            status("command_error");
            log("Ошибка запуска Termux: " + e);
            Toast.makeText(this, "Не удалось запустить Termux: " +
                e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }


    // COPY_BLOCK_GENERATION_GATE_V1

    // Ищет точную кнопку копирования и возвращает её кликабельного родителя.
    // Возвращённый AccessibilityNodeInfo принадлежит вызывающему методу.
    private AccessibilityNodeInfo findCopyCodeClickTarget(
            AccessibilityNodeInfo node,
            int depth,
            int[] visited) {

        if (node == null || depth > 55 || visited[0] >= 3000) {
            return null;
        }

        visited[0]++;

        if (isSecondPassportCopyCodeButton(node)) {
            AccessibilityNodeInfo parent = null;

            try {
                parent = node.getParent();

                if (parent != null &&
                        parent.isVisibleToUser() &&
                        parent.isEnabled() &&
                        parent.isClickable() &&
                        secondPassportHasClickAction(parent)) {

                    return AccessibilityNodeInfo.obtain(parent);
                }
            } catch (Exception e) {
                log("COPY TARGET ERROR: " + e);
            } finally {
                if (parent != null) parent.recycle();
            }
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            if (visited[0] >= 3000) break;

            AccessibilityNodeInfo child = null;

            try {
                child = node.getChild(i);

                if (child != null) {
                    AccessibilityNodeInfo found =
                            findCopyCodeClickTarget(
                                    child, depth + 1, visited);

                    if (found != null) return found;
                }
            } finally {
                if (child != null) child.recycle();
            }
        }

        return null;
    }

    private boolean copyGenerationStopLabel(
            AccessibilityNodeInfo node) {

        if (node == null || !node.isVisibleToUser()) return false;

        CharSequence description = node.getContentDescription();
        CharSequence text = node.getText();

        if (description != null &&
                description.toString().contains("Остановить")) {
            return true;
        }

        return text != null &&
                text.toString().contains("Остановить");
    }

    // Отдельная проверка состояния генерации после поиска Copy.
    private boolean containsStopGenerationButton(
            AccessibilityNodeInfo node,
            int depth,
            int[] visited) {

        if (node == null || depth > 55 || visited[0] >= 3000) {
            return false;
        }

        visited[0]++;

        if (copyGenerationStopLabel(node)) return true;

        for (int i = 0; i < node.getChildCount(); i++) {
            if (visited[0] >= 3000) break;

            AccessibilityNodeInfo child = null;

            try {
                child = node.getChild(i);

                if (child != null &&
                        containsStopGenerationButton(
                                child, depth + 1, visited)) {
                    return true;
                }
            } finally {
                if (child != null) child.recycle();
            }
        }

        return false;
    }


    private void copyResponseToClipboard() {
        if (processing || termuxProcessing || awaitingTermuxResult) {
            Toast.makeText(this,
                "Другая операция ещё выполняется",
                Toast.LENGTH_LONG).show();
            return;
        }

        log("===== COPY BLOCK BEGIN =====");

        AccessibilityNodeInfo root = getRootInActiveWindow();

        if (root == null) {
            log("COPY BLOCK: активное окно недоступно");
            Toast.makeText(this,
                "Окно ChatGPT недоступно",
                Toast.LENGTH_LONG).show();
            return;
        }

        AccessibilityNodeInfo target = null;

        try {
            CharSequence pkg = root.getPackageName();

            if (pkg == null ||
                    !"com.openai.chatgpt".contentEquals(pkg)) {
                log("COPY BLOCK: активное окно не ChatGPT");

                Toast.makeText(this,
                    "Сначала открой ChatGPT",
                    Toast.LENGTH_LONG).show();
                return;
            }

            // ЭТАП 1: сначала ищем кнопку «Скопировать код».
            int[] copyVisited = new int[]{0};

            target = findCopyCodeClickTarget(
                    root, 0, copyVisited);

            log("COPY BUTTON SEARCH NODES=" + copyVisited[0]);

            if (target == null) {
                log("COPY BLOCK: кнопка «Скопировать код» не найдена");

                Toast.makeText(this,
                    "Кнопка «Скопировать код» не найдена",
                    Toast.LENGTH_LONG).show();
                return;
            }

            log("COPY BUTTON FOUND"
                + " CLASS=" + target.getClassName()
                + " CLICKABLE=" + target.isClickable()
                + " ENABLED=" + target.isEnabled());

            // ЭТАП 2: после обнаружения Copy проверяем генерацию.
            int[] generationVisited = new int[]{0};

            boolean generationActive =
                    containsStopGenerationButton(
                            root, 0, generationVisited);

            log("GENERATION CHECK NODES=" + generationVisited[0]);
            log("GENERATION ACTIVE=" + generationActive);

            if (generationActive) {
                log("COPY BLOCK REJECTED: найден индикатор «Остановить»");

                Toast.makeText(this,
                    "ChatGPT ещё формирует ответ. Копирование запрещено.",
                    Toast.LENGTH_LONG).show();
                return;
            }

            // ЭТАП 3: одна попытка нажатия.
            if (!target.isVisibleToUser() ||
                    !target.isEnabled() ||
                    !target.isClickable() ||
                    !secondPassportHasClickAction(target)) {

                log("COPY BLOCK REJECTED: кнопка больше недоступна");

                Toast.makeText(this,
                    "Кнопка копирования недоступна",
                    Toast.LENGTH_LONG).show();
                return;
            }

            log("COPY BLOCK: выполняется одно ACTION_CLICK");

            boolean clicked = target.performAction(
                    AccessibilityNodeInfo.ACTION_CLICK);

            log("COPY BLOCK ACTION_CLICK=" + clicked);

            if (clicked) {
                status("copied");

                Toast.makeText(this,
                    "Команда копирования блока выполнена",
                    Toast.LENGTH_LONG).show();
            } else {
                status("command_error");

                Toast.makeText(this,
                    "ChatGPT не принял команду копирования",
                    Toast.LENGTH_LONG).show();
            }

        } catch (Exception e) {
            log("COPY BLOCK ERROR: " + e);

            Toast.makeText(this,
                "Ошибка копирования: " + e.getMessage(),
                Toast.LENGTH_LONG).show();

        } finally {
            if (target != null) target.recycle();
            root.recycle();

            log("===== COPY BLOCK END =====");
        }
    }


    
    // Этап ожидания готовности ChatGPT.
    private boolean openingChatGPT = false;

    private void startCycle() {
        if (processing || openingChatGPT) {
            log("Запуск пропущен: цикл уже выполняется.");
            return;
        }

        if (isChatGPTComposerReady()) {
            log("ChatGPT уже готов. Начинаю обработку.");
            startCycleWithChatReady();
            return;
        }

        openingChatGPT = true;
        status("busy");

        log("ChatGPT не готов. Запускаю или восстанавливаю приложение.");

        openChatGPTAndWait(0);
    }

    private boolean isChatGPTComposerReady() {
        AccessibilityNodeInfo root = getRootInActiveWindow();

        if (root == null) {
            return false;
        }

        boolean ready = false;

        try {
            CharSequence packageName = root.getPackageName();

            if (packageName != null &&
                    CHATGPT_PACKAGE.contentEquals(packageName)) {

                AccessibilityNodeInfo input = findInput(root);

                if (input != null) {
                    ready = input.isEnabled() &&
                            input.isVisibleToUser();

                    input.recycle();
                }
            }
        } catch (Exception e) {
            log("Ошибка проверки готовности ChatGPT: " + e);
        } finally {
            root.recycle();
        }

        return ready;
    }

    private void openChatGPTAndWait(int attempt) {
        if (!openingChatGPT) {
            return;
        }

        if (isChatGPTComposerReady()) {
            openingChatGPT = false;

            log("Поле ввода ChatGPT готово. Запускаю основной цикл.");

            startCycleWithChatReady();
            return;
        }

        if (attempt == 0) {
            try {
                Intent launchIntent =
                    getPackageManager().getLaunchIntentForPackage(
                        CHATGPT_PACKAGE
                    );

                if (launchIntent == null) {
                    openingChatGPT = false;
                    fail("Не удалось найти приложение ChatGPT.");
                    return;
                }

                launchIntent.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK |
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
                );

                startActivity(launchIntent);

                log("Отправлена команда открытия ChatGPT.");

            } catch (Exception e) {
                openingChatGPT = false;

                log("Ошибка запуска ChatGPT: " + e);

                fail("Не удалось открыть ChatGPT: " + e.getMessage());
                return;
            }
        }

        if (attempt >= 40) {
            openingChatGPT = false;

            log("Тайм-аут ожидания поля ввода ChatGPT.");

            fail(
                "ChatGPT не предоставил поле ввода за 20 секунд. " +
                "Отправка отменена."
            );

            return;
        }

        handler.postDelayed(
            () -> openChatGPTAndWait(attempt + 1),
            500
        );
    }


    private void startCycleWithChatReady() {
        if (processing) {
            log("Цикл уже выполняется. Повторный запуск пропущен.");
            return;
        }

        processing = true;
        DiagnosticLogger.startCycle();
        status("busy");

        log("Шаг 1: запуск обработки");

        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) {
            fail("Не удалось получить активное окно");
            return;
        }

        CharSequence packageName = root.getPackageName();
        String currentPackage = packageName == null ? "" : packageName.toString();

        log("Активный пакет: " + currentPackage);

        if (!CHATGPT_PACKAGE.equals(currentPackage)) {
            fail("Открой приложение ChatGPT. Сейчас активно другое окно.");
            return;
        }

        String text = readResponseFile();
        if (text == null || text.trim().isEmpty()) {
            fail("Файл Download/Assistant_response.txt отсутствует или пуст");
            return;
        }

        
        fullText = text;
        insertedText = fullText;


        android.content.ClipboardManager clipboard =
            (android.content.ClipboardManager)
                getSystemService(CLIPBOARD_SERVICE);

        if (clipboard != null) {
            clipboard.setPrimaryClip(
                android.content.ClipData.newPlainText(
                    "Assistant_response", fullText
                )
            );
            log("Ответ Termux скопирован в буфер обмена.");
        } else {
            log("ВНИМАНИЕ: буфер обмена недоступен.");
        }
        log("Текст загружен одним сообщением. Символов: " + fullText.length());

        log("Шаг 2: диагностика дерева элементов ChatGPT");
        dumpAccessibilityTree(root, 0, new int[]{0});

        log("Шаг 3: поиск поля ввода");
        AccessibilityNodeInfo input = findInput(root);

        if (input == null) {
            fail("Поле ввода ChatGPT не найдено");
            return;
        }

        Rect inputBounds = new Rect();
        input.getBoundsInScreen(inputBounds);

        log("Поле ввода найдено: " + inputBounds.toShortString());
        showHighlight(inputBounds, "ПОЛЕ ВВОДА", Color.rgb(30, 150, 255));

        log("Шаг 3: установка текста в поле");

        boolean focused = input.performAction(
            AccessibilityNodeInfo.ACTION_FOCUS
        );
        log("Фокус поля: " + focused);

        Bundle args = new Bundle();
        args.putCharSequence(
            AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
            insertedText
        );

        boolean set = input.performAction(
            AccessibilityNodeInfo.ACTION_SET_TEXT,
            args
        );

        log("ACTION_SET_TEXT: " + set);

        if (!set) {
            fail("Android не разрешил вставить текст в поле");
            return;
        }

        handler.postDelayed(() -> verifyInsertion(), 700);
    }

    private void verifyInsertion() {
        if (!processing) {
            log("Операция отменена: RESET или остановка цикла.");
            return;
        }

        AccessibilityNodeInfo root = getRootInActiveWindow();

        if (root == null) {
            fail("Окно исчезло после вставки");
            return;
        }

        AccessibilityNodeInfo input = findInput(root);
        if (input == null) {
            fail("Поле ввода исчезло после вставки");
            return;
        }

        CharSequence actual = input.getText();
        String actualText = actual == null ? "" : actual.toString();

        boolean verified = actualText.trim().equals(insertedText.trim());

        log("Проверка вставки: " + (verified ? "УСПЕШНО" : "НЕ СОВПАЛО"));
        log("Ожидалось символов: " + insertedText.length());
        log("Фактически символов: " + actualText.length());

        if (!verified) {
            fail("Текст не прошёл проверку. Отправка отменена.");
            return;
        }

        log("Шаг 4: вставка подтверждена.");
        log("Ожидание 1000 мс: интерфейс ChatGPT может переместить кнопку.");

        handler.postDelayed(() -> findAndClickSendButton(), 1000);
    }

    private void findAndClickSendButton() {
        if (!processing) {
            log("Операция отменена: RESET или остановка цикла.");
            return;
        }

        log("Поиск кнопки после задержки 1000 мс.");

        // Получаем актуальное дерево, а не используем старые координаты.
        AccessibilityNodeInfo root = getRootInActiveWindow();

        if (root == null) {
            fail("Не удалось получить новое дерево ChatGPT.");
            return;
        }

        AccessibilityNodeInfo input = findInput(root);

        if (input == null) {
            root.recycle();
            fail("Не найдено поле ввода для определения положения кнопки.");
            return;
        }

        Rect inputBounds = new Rect();
        input.getBoundsInScreen(inputBounds);
        input.recycle();

        log("Поле ввода для поиска кнопки: " +
            inputBounds.toShortString());

        AccessibilityNodeInfo send =
            findSendButton(root, inputBounds);

        root.recycle();

        if (send == null) {
            fail("Настоящая кнопка отправки не найдена. Текст оставлен в поле.");
            return;
        }

        Rect bounds = new Rect();
        send.getBoundsInScreen(bounds);

        if (bounds.isEmpty() || !send.isVisibleToUser()) {
            send.recycle();
            fail("Кнопка отправки невидима или имеет некорректные координаты.");
            return;
        }

        log("Актуальная кнопка отправки: " + bounds.toShortString());

        showHighlight(
            bounds,
            "ОТПРАВИТЬ",
            Color.rgb(30, 210, 80)
        );

        int centerX = bounds.centerX();
        int centerY = bounds.centerY();

        log("Координаты получены после задержки: X=" +
            centerX + " Y=" + centerY);
        log("Постоянная поправка координат не применяется.");
        log("Шаг 5: пробуем ACTION_CLICK");

        boolean clicked = send.performAction(
            AccessibilityNodeInfo.ACTION_CLICK
        );

        log("ACTION_CLICK вернул: " + clicked);
        send.recycle();

        if (clicked) {
            handler.postDelayed(() -> verifySending(), 1400);
        } else {
            log("ACTION_CLICK не сработал. Пробуем dispatchGesture.");
            sendGesture(centerX, centerY);
        }
    }

    private void verifySending() {
        if (!processing) {
            log("Операция отменена: RESET или остановка цикла.");
            return;
        }

        AccessibilityNodeInfo root = getRootInActiveWindow();

        if (root == null) {
            finishWithResult("Окно ChatGPT исчезло. Отправка не подтверждена.");
            return;
        }

        AccessibilityNodeInfo input = findInput(root);

        if (input == null) {
            root.recycle();
            finishWithResult("Поле ввода не найдено. Отправка не подтверждена.");
            return;
        }

        CharSequence value = input.getText();
        String remaining = value == null ? "" : value.toString().trim();

        input.recycle();
        root.recycle();

        if (!remaining.isEmpty()) {
            finishWithResult(
                "Текст остался в поле. Отправка полного сообщения не подтверждена."
            );
            return;
        }

        log("Отправка единого сообщения подтверждена.");

        Toast.makeText(
            this,
            "Сообщение отправлено",
            Toast.LENGTH_LONG
        ).show();

        removeHighlight();
        processing = false;
        status("sent");
    }


    private void sendGesture(int x, int y) {
        Path path = new Path();
        path.moveTo(x, y);

        GestureDescription.StrokeDescription stroke =
            new GestureDescription.StrokeDescription(path, 0, 100);

        GestureDescription gesture =
            new GestureDescription.Builder()
                .addStroke(stroke)
                .build();

        boolean dispatched = dispatchGesture(
            gesture,
            new GestureResultCallback() {
                @Override
                public void onCompleted(GestureDescription description) {
                    log("dispatchGesture: жест выполнен");
                    handler.postDelayed(() -> verifySending(), 1400);
                }

                @Override
                public void onCancelled(GestureDescription description) {
                    finishWithResult("Системный жест отменён.");
                }
            },
            handler
        );

        log("dispatchGesture принят системой: " + dispatched);

        if (!dispatched) {
            fail("Система не приняла жест нажатия");
        }
    }



    // Расширенная диагностика Compose без записи текста переписки.
    private void dumpAccessibilityTree(
        AccessibilityNodeInfo node, int depth, int[] counter
    ) {
        if (node == null || depth > 40 || counter[0] >= 500) return;

        counter[0]++;

        String cls = String.valueOf(node.getClassName());
        String id = String.valueOf(node.getViewIdResourceName());

        CharSequence text = node.getText();
        CharSequence hint = node.getHintText();
        CharSequence desc = node.getContentDescription();

        Rect bounds = new Rect();
        node.getBoundsInScreen(bounds);

        StringBuilder actions = new StringBuilder();
        for (AccessibilityNodeInfo.AccessibilityAction action
                : node.getActionList()) {
            if (actions.length() > 0) actions.append(",");
            actions.append(action.getId());
        }

        log("TREE depth=" + depth
            + " class=" + cls
            + " id=" + id
            + " children=" + node.getChildCount()
            + " textLen=" + (text == null ? 0 : text.length())
            + " hintLen=" + (hint == null ? 0 : hint.length())
            + " descLen=" + (desc == null ? 0 : desc.length()) + " descValue=[" + String.valueOf(desc) + "]"
            + " editable=" + node.isEditable()
            + " focusable=" + node.isFocusable()
            + " focused=" + node.isFocused()
            + " visible=" + node.isVisibleToUser()
            + " important=" + node.isImportantForAccessibility()
            + " enabled=" + node.isEnabled()
            + " clickable=" + node.isClickable()
            + " actions=[" + actions + "]"
            + " bounds=" + bounds.toShortString());

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            dumpAccessibilityTree(child, depth + 1, counter);
            if (child != null) child.recycle();
        }
    }

    private AccessibilityNodeInfo findInput(AccessibilityNodeInfo root) {
        return findInputRecursive(root, 0);
    }

    private AccessibilityNodeInfo findInputRecursive(
        AccessibilityNodeInfo node, int depth
    ) {
        if (node == null || depth > 45) return null;

        CharSequence className = node.getClassName();
        String cls = className == null ? "" : className.toString();

        if (cls.contains("EditText") && node.isEnabled()) {
            return AccessibilityNodeInfo.obtain(node);
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            AccessibilityNodeInfo found =
                findInputRecursive(child, depth + 1);

            if (found != null) return found;
        }

        return null;
    }

    private AccessibilityNodeInfo findSendButton(
        AccessibilityNodeInfo root, Rect inputBounds
    ) {
        log("Поиск подписи отправки справа от поля ввода.");
        return findSendRecursive(root, inputBounds, 0);
    }

    private AccessibilityNodeInfo findSendRecursive(
        AccessibilityNodeInfo node, Rect inputBounds, int depth
    ) {
        if (node == null || depth > 45) return null;

        if (node.isVisibleToUser() && isSendLabelNode(node)) {
            Rect labelBounds = new Rect();
            node.getBoundsInScreen(labelBounds);

            // Подпись должна находиться в районе правого края поля ввода.
            boolean nearComposer =
                !labelBounds.isEmpty()
                && labelBounds.centerX() >= inputBounds.right - 240
                && labelBounds.centerY() >= inputBounds.top - 120
                && labelBounds.centerY() <= inputBounds.bottom + 180;

            if (nearComposer) {
                AccessibilityNodeInfo current =
                    AccessibilityNodeInfo.obtain(node);

                // Ищем ближайшего подходящего кликабельного родителя.
                for (int level = 0; level <= 2 && current != null; level++) {
                    Rect b = new Rect();
                    current.getBoundsInScreen(b);

                    int width = b.width();
                    int height = b.height();

                    boolean suitable =
                        current.isVisibleToUser()
                        && current.isEnabled()
                        && current.isClickable()
                        && !b.isEmpty()
                        && width <= 220
                        && height <= 220
                        && b.centerX() >= inputBounds.right - 240
                        && b.centerY() >= inputBounds.top - 120
                        && b.centerY() <= inputBounds.bottom + 180;

                    if (suitable) {
                        log("Кандидат синей стрелки найден.");
                        log("Глубина подписи: " + depth);
                        log("Уровень кликабельного родителя: " + level);
                        log("Границы кандидата: " + b.toShortString());
                        return current;
                    }

                    AccessibilityNodeInfo parent = current.getParent();
                    current.recycle();
                    current = parent;
                }

                if (current != null) current.recycle();
            } else {
                log("Совпадение подписи исключено: вне области поля ввода. " +
                    labelBounds.toShortString());
            }
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child == null) continue;

            AccessibilityNodeInfo found =
                findSendRecursive(child, inputBounds, depth + 1);

            child.recycle();

            if (found != null) return found;
        }

        return null;
    }

    private boolean isSendLabelNode(AccessibilityNodeInfo node) {
        CharSequence text = node.getText();
        CharSequence desc = node.getContentDescription();
        String id = node.getViewIdResourceName();

        String value = (
            (text == null ? "" : text.toString()) + " " +
            (desc == null ? "" : desc.toString()) + " " +
            (id == null ? "" : id)
        ).toLowerCase(Locale.ROOT);

        return value.contains("send") || value.contains("отправ");
    }


    private String readResponseFile() {
        String uriText = getSharedPreferences(
            "response_file", MODE_PRIVATE
        ).getString("uri", null);

        if (uriText != null && !uriText.isEmpty()) {
            log("Чтение полного ответа из выбранного файла.");

            try {
                Uri uri = Uri.parse(uriText);

                try (java.io.InputStream in =
                         getContentResolver().openInputStream(uri)) {

                    if (in == null) {
                        log("Не удалось открыть выбранный файл.");
                    } else {
                        java.io.ByteArrayOutputStream out =
                            new java.io.ByteArrayOutputStream();

                        byte[] buffer = new byte[16384];
                        int count;
                        long total = 0;

                        while ((count = in.read(buffer)) != -1) {
                            out.write(buffer, 0, count);
                            total += count;
                        }

                        String result = out.toString("UTF-8");
                        log("Полный файл прочитан. Байтов: " + total +
                            ", символов: " + result.length());
                        return result;
                    }
                }
            } catch (Exception e) {
                log("Ошибка чтения файла: " + e);
            }
        }

        if (latestTermuxResult != null) {
            if (latestTermuxResultTruncated) {
                log("Callback обрезан. Частичный результат отправлять запрещено.");
                return null;
            }

            log("Файл недоступен. Используется полный callback Termux.");
            return latestTermuxResult;
        }

        log("Нет доступного файла ответа или полного callback.");
        return null;
    }


    // AUTO_BUTTON_HIGHLIGHTS_BEGIN
    private final java.util.List<View> autoButtonHighlightViews =
        new java.util.ArrayList<>();

    private final java.util.List<WindowManager.LayoutParams>
        autoButtonHighlightParams = new java.util.ArrayList<>();

    private void clearAutoButtonHighlights() {
        if (windowManager != null) {
            for (View view : autoButtonHighlightViews) {
                try {
                    windowManager.removeView(view);
                } catch (Exception ignored) {}
            }
        }
        autoButtonHighlightViews.clear();
        autoButtonHighlightParams.clear();
    }

    private void addAutoButtonHighlight(
        Rect bounds, String label, int color, String passport
    ) {
        
if (windowManager == null || bounds == null || bounds.isEmpty()) {
            return;
        }

        try {
            HighlightView view = new HighlightView(
                this, label, color
            );

            view.setClickable(true);
            view.setOnClickListener(v -> {
                removeAllButtonHighlights();

                try {
                    android.content.ClipboardManager clipboard =
                            (android.content.ClipboardManager)
                                    getSystemService(CLIPBOARD_SERVICE);

                    if (clipboard != null) {
                        clipboard.setPrimaryClip(
                                android.content.ClipData.newPlainText(
                                        "Accessibility button passport",
                                        passport
                                )
                        );

                        android.widget.Toast.makeText(
                                this,
                                "Паспорт скопирован",
                                android.widget.Toast.LENGTH_SHORT
                        ).show();

                        log("BUTTON PASSPORT COPIED: " + label
                                + " chars=" + passport.length());
                    }
                } catch (Exception e) {
                    log("BUTTON PASSPORT COPY ERROR: " + e);
                }
            });

            WindowManager.LayoutParams params =
                new WindowManager.LayoutParams(
                    bounds.width(),
                    bounds.height(),
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT
                );

            params.gravity = Gravity.TOP | Gravity.LEFT;
            params.x = bounds.left;
            params.y = bounds.top;

            windowManager.addView(view, params);
            autoButtonHighlightViews.add(view);
            autoButtonHighlightParams.add(params);

            log("AUTO BUTTON HIGHLIGHT: " + label
                + " color=" + (color == Color.RED ? "RED" : "GREEN")
                + " bounds=" + bounds.toShortString());

        } catch (Exception e) {
            log("AUTO BUTTON HIGHLIGHT ERROR: " + e);
        }
    }


    private void collectAndHighlightButtons(
        AccessibilityNodeInfo node,
        int depth,
        int[] count,
        int[] buttonIndex
    ) {
        if (node == null || depth > 40 || count[0] >= 1800) return;
        count[0]++;

        if (node.isVisibleToUser() && node.isEnabled()) {
            Rect bounds = new Rect();
            node.getBoundsInScreen(bounds);

            CharSequence className = node.getClassName();
            String cls = className == null ? "" : className.toString();
            String label = copyNodeLabel(node);

            boolean buttonClass =
                cls.contains("Button") || cls.contains("ImageButton");
            boolean clickable = node.isClickable();
            boolean hasClickAction = false;

            try {
                for (AccessibilityNodeInfo.AccessibilityAction action
                        : node.getActionList()) {
                    if (action.getId() == AccessibilityNodeInfo.ACTION_CLICK) {
                        hasClickAction = true;
                        break;
                    }
                }
            } catch (Exception ignored) {}

            boolean leaf = node.getChildCount() == 0;
            boolean buttonLike =
                buttonClass || ((clickable || hasClickAction) && leaf);

            if (buttonLike && !bounds.isEmpty()) {
                int number = ++buttonIndex[0];

                CharSequence value;
                String description = "";
                String viewId = "";
                String text = "";

                try {
                    value = node.getContentDescription();
                    description = value == null ? "" : value.toString();
                } catch (Exception ignored) {}

                try {
                    viewId = node.getViewIdResourceName();
                    if (viewId == null) viewId = "";
                } catch (Exception ignored) {}

                try {
                    value = node.getText();
                    text = value == null ? "" : value.toString();
                } catch (Exception ignored) {}

                String actions = "";
                try {
                    for (AccessibilityNodeInfo.AccessibilityAction action
                            : node.getActionList()) {
                        if (actions.length() > 250) break;
                        if (actions.length() > 0) actions += ",";
                        actions += action.getId() + ":" + action.getLabel();
                    }
                } catch (Exception ignored) {}

                log("BUTTON CATALOG #" + number
                    + " CLASS='" + cls + "'"
                    + " TEXT='" + text + "'"
                    + " DESCRIPTION='" + description + "'"
                    + " LABEL='" + label + "'"
                    + " VIEW_ID='" + viewId + "'"
                    + " BOUNDS=" + bounds.toShortString()
                    + " SIZE=" + bounds.width() + "x" + bounds.height()
                    + " CENTER=(" + bounds.centerX() + "," + bounds.centerY() + ")"
                    + " CLICKABLE=" + clickable
                    + " CLICK_ACTION=" + hasClickAction
                    + " CHILDREN=" + node.getChildCount()
                    + " ACTIONS=[" + actions + "]");

                // Каталог собирает паспорта, но подсвечивает только точную кнопку.
                if (isObservedChatCopyStructure(node)) {
                    addAutoButtonHighlight(
                        secondPassportHighlightBounds(node, bounds),
                        String.valueOf(number),
                        Color.GREEN,
                        buildAccessibilityPassport(node)
                    );
                }
            }
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            if (count[0] >= 1800) break;
            AccessibilityNodeInfo child = node.getChild(i);
            if (child == null) continue;

            collectAndHighlightButtons(
                child, depth + 1, count, buttonIndex
            );
            child.recycle();
        }
    }


    // Поиск визуального элемента Copy относительно контейнера команды.
    private void findTopRightVisualNode(
        AccessibilityNodeInfo node,
        Rect container,
        int depth,
        int[] count,
        Rect[] bestBounds,
        double[] bestScore
    ) {
        if (node == null || depth > 18 || count[0] >= 500) return;
        count[0]++;

        if (node.isVisibleToUser() && node.isEnabled()) {
            Rect b = new Rect();
            node.getBoundsInScreen(b);

            int cw = container.width();
            int ch = container.height();

            if (!b.isEmpty() && cw > 0 && ch > 0
                    && container.contains(b)) {
                double aspect = (double) b.width() / b.height();
                int rightGap = container.right - b.right;
                int topGap = b.top - container.top;

                boolean squareLike =
                    aspect >= 0.65 && aspect <= 1.45;

                boolean nearRight =
                    rightGap >= -2 && rightGap <= Math.max(24, cw * 0.15);

                boolean nearTop =
                    topGap >= -2 && topGap <= Math.max(24, ch * 0.25);

                boolean suitableSize =
                    b.width() >= 32 && b.height() >= 32
                    && b.height() <= ch * 1.5
                    && b.width() <= cw * 0.6;

                if (squareLike && nearRight && nearTop && suitableSize) {
                    double score =
                        (double) Math.max(0, rightGap) / cw
                        + (double) Math.max(0, topGap) / ch
                        + Math.abs(1.0 - aspect) * 0.2;

                    if (score < bestScore[0]) {
                        bestScore[0] = score;
                        bestBounds[0] = new Rect(b);
                    }
                }
            }
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            if (count[0] >= 500) break;
            AccessibilityNodeInfo child = node.getChild(i);
            if (child == null) continue;

            findTopRightVisualNode(
                child, container, depth + 1,
                count, bestBounds, bestScore
            );
            child.recycle();
        }
    }



    private void highlightCopyButtonInMatchContainer(
            AccessibilityNodeInfo match) {

        if (match == null) {
            log("CHAT BUTTON SCAN: MATCH IS NULL");
            return;
        }

        android.graphics.Rect matchBounds =
                new android.graphics.Rect();
        match.getBoundsInScreen(matchBounds);

        log("===== CHAT BUTTON SCAN BEGIN =====");
        log("MATCH BOUNDS=" + matchBounds.flattenToString());

        AccessibilityNodeInfo scope = match.getParent();

        if (scope == null) {
            log("CHAT BUTTON SCAN: PARENT NOT FOUND");
            return;
        }

        java.util.HashSet<String> seen =
                new java.util.HashSet<>();

        int[] number = new int[]{0};
        int[] candidateCount = new int[]{0};

        scanObservedChatButtons(
                scope,
                matchBounds,
                seen,
                number,
                candidateCount,
                0
        );

        log("CHAT BUTTON TOTAL=" + number[0]);
        log("CHAT BUTTON STRUCTURE CANDIDATES=" +
                candidateCount[0]);
        log("===== CHAT BUTTON SCAN END =====");
    }

    
    private void removeAllButtonHighlights() {
        try {
            if (windowManager != null) {
                for (int i = autoButtonHighlightViews.size() - 1; i >= 0; i--) {
                    android.view.View item = autoButtonHighlightViews.get(i);
                    try {
                        if (item != null && item.getParent() != null) {
                            windowManager.removeView(item);
                        }
                    } catch (Exception e) {
                        log("HIGHLIGHT REMOVE ERROR: " + e);
                    }
                }
            }
        } finally {
            autoButtonHighlightViews.clear();
            autoButtonHighlightParams.clear();
        }
        log("ALL BUTTON HIGHLIGHTS REMOVED AFTER SELECTION");
    }


    private String buildAccessibilityPassport(
            AccessibilityNodeInfo target) {

        StringBuilder out = new StringBuilder(16000);
        out.append("===== ACCESSIBILITY BUTTON PASSPORT =====\n");
        out.append("CAPTURE_TIME=")
                .append(new java.text.SimpleDateFormat(
                        "yyyy-MM-dd HH:mm:ss",
                        java.util.Locale.US
                ).format(new java.util.Date()))
                .append('\n');

        out.append("\n===== TARGET AND CHILDREN =====\n");
        appendPassportNode(out, target, "TARGET", 0, 3);

        AccessibilityNodeInfo parent = null;
        try {
            parent = target == null ? null : target.getParent();
        } catch (Exception ignored) {}

        int level = 1;
        while (parent != null && level <= 6) {
            out.append("\n===== ANCESTOR LEVEL ")
                    .append(level).append(" =====\n");
            appendPassportNode(out, parent, "ANCESTOR", 0, 2);

            AccessibilityNodeInfo next = null;
            try {
                next = parent.getParent();
            } catch (Exception ignored) {}
            parent = next;
            level++;
        }

        out.append("\n===== END PASSPORT =====\n");
        return out.toString();
    }

    private void appendPassportNode(
            StringBuilder out,
            AccessibilityNodeInfo node,
            String relation,
            int depth,
            int maxDepth) {

        if (node == null || depth > maxDepth) return;

        android.graphics.Rect bounds = new android.graphics.Rect();
        try { node.getBoundsInScreen(bounds); } catch (Exception ignored) {}

        CharSequence text = null;
        CharSequence desc = null;
        String viewId = "";

        try { text = node.getText(); } catch (Exception ignored) {}
        try { desc = node.getContentDescription(); } catch (Exception ignored) {}
        try { viewId = String.valueOf(node.getViewIdResourceName()); }
        catch (Exception ignored) {}

        String pad = new String(new char[depth * 2]).replace('\0', ' ');

        out.append(pad).append("--- NODE ---\n");
        out.append(pad).append("RELATION=").append(relation).append('\n');
        out.append(pad).append("DEPTH=").append(depth).append('\n');
        out.append(pad).append("CLASS=").append(node.getClassName()).append('\n');
        out.append(pad).append("PACKAGE=").append(node.getPackageName()).append('\n');
        out.append(pad).append("TEXT=").append(text).append('\n');
        out.append(pad).append("CONTENT_DESCRIPTION=").append(desc).append('\n');
        out.append(pad).append("VIEW_ID=").append(viewId).append('\n');
        out.append(pad).append("BOUNDS=").append(bounds.flattenToString()).append('\n');
        out.append(pad).append("CLICKABLE=").append(node.isClickable()).append('\n');
        out.append(pad).append("LONG_CLICKABLE=").append(node.isLongClickable()).append('\n');
        out.append(pad).append("FOCUSABLE=").append(node.isFocusable()).append('\n');
        out.append(pad).append("FOCUSED=").append(node.isFocused()).append('\n');
        out.append(pad).append("ACCESSIBILITY_FOCUSED=").append(node.isAccessibilityFocused()).append('\n');
        out.append(pad).append("ENABLED=").append(node.isEnabled()).append('\n');
        out.append(pad).append("SELECTED=").append(node.isSelected()).append('\n');
        out.append(pad).append("CHECKABLE=").append(node.isCheckable()).append('\n');
        out.append(pad).append("CHECKED=").append(node.isChecked()).append('\n');
        out.append(pad).append("SCROLLABLE=").append(node.isScrollable()).append('\n');
        out.append(pad).append("VISIBLE_TO_USER=").append(node.isVisibleToUser()).append('\n');
        out.append(pad).append("CHILD_COUNT=").append(node.getChildCount()).append('\n');

        try {
            out.append(pad).append("ACTIONS=");
            for (Object action : node.getActionList()) {
                out.append('[').append(action).append(']');
            }
            out.append('\n');
        } catch (Exception e) {
            out.append(pad).append("ACTIONS_ERROR=").append(e).append('\n');
        }

        if (depth >= maxDepth) return;

        int count = Math.min(node.getChildCount(), 24);
        for (int i = 0; i < count; i++) {
            AccessibilityNodeInfo child = null;
            try { child = node.getChild(i); } catch (Exception ignored) {}
            if (child != null) {
                appendPassportNode(
                        out, child, "CHILD_" + i, depth + 1, maxDepth
                );
            }
        }
    }

private void scanObservedChatButtons(
            AccessibilityNodeInfo node,
            android.graphics.Rect matchBounds,
            java.util.HashSet<String> seen,
            int[] number,
            int[] candidateCount,
            int depth) {

        if (node == null || depth > 55) return;

        android.graphics.Rect bounds =
                new android.graphics.Rect();
        node.getBoundsInScreen(bounds);

        int cx = bounds.centerX();
        int cy = bounds.centerY();

        boolean inside =
                cx >= matchBounds.left - 5 &&
                cx <= matchBounds.right + 5 &&
                cy >= matchBounds.top - 5 &&
                cy <= matchBounds.bottom + 5;

        String cls = String.valueOf(node.getClassName());
        boolean isButtonClass =
                cls.endsWith(".Button") ||
                cls.endsWith("Button");

        int w = bounds.width();
        int h = bounds.height();

        boolean square =
                w >= 48 && h >= 48 &&
                w <= 220 && h <= 220 &&
                h > 0 &&
                w * 100 / h >= 75 &&
                w * 100 / h <= 135;

        boolean buttonLike =
                isButtonClass ||
                (node.isClickable() && square);

        if (inside && buttonLike && !bounds.isEmpty()) {

            String key =
                    cls + "|" + bounds.flattenToString();

            if (seen.add(key)) {

                number[0]++;

                CharSequence text = node.getText();
                CharSequence desc = node.getContentDescription();

                String viewId = "";
                try {
                    viewId = String.valueOf(
                            node.getViewIdResourceName());
                } catch (Exception ignored) {}

                String textValue =
                        text == null ? "" : text.toString();

                String descValue =
                        desc == null ? "" : desc.toString();

                boolean candidate =
                        isObservedChatCopyStructure(node);

                String label = String.valueOf(number[0]);
                int color = 0xFF00C853;

                if (candidate) {
                    candidateCount[0]++;
                    // Только нумерация на экране.
                    color = 0xFF00C853;
                }

                log("CHAT BUTTON #" + number[0]);
                log("  CLASS=" + cls);
                log("  TEXT=" + textValue);
                log("  DESCRIPTION=" + descValue);
                log("  DESCRIPTION_LENGTH=" + descValue.length());
                log("  VIEW_ID=" + viewId);
                log("  CLICKABLE=" + node.isClickable());
                log("  ENABLED=" + node.isEnabled());
                log("  BOUNDS=" + bounds.flattenToString());

                if (candidate) {
                    log("COPY STRUCTURE CANDIDATE #" +
                            number[0]);
                }


                // Никаких рамок для узлов, не прошедших паспорт.
                if (candidate) {
                    addAutoButtonHighlight(
                            secondPassportHighlightBounds(node, bounds),
                            label,
                            color,
                            buildAccessibilityPassport(node)
                    );
                }
            }
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                scanObservedChatButtons(
                        child,
                        matchBounds,
                        seen,
                        number,
                        candidateCount,
                        depth + 1
                );
            }
        }
    }



    // Критерий подтверждён двумя паспортами кнопки «Скопировать код».
    private boolean isSecondPassportCopyCodeButton(
            android.view.accessibility.AccessibilityNodeInfo node) {
        if (node == null || !node.isVisibleToUser() || !node.isEnabled()) {
            return false;
        }

        CharSequence cls = node.getClassName();
        if (cls == null ||
                !"android.widget.Button".contentEquals(cls)) {
            return false;
        }

        CharSequence pkg = node.getPackageName();
        if (pkg == null ||
                !"com.openai.chatgpt".contentEquals(pkg)) {
            return false;
        }

        android.view.accessibility.AccessibilityNodeInfo parent = null;
        try {
            parent = node.getParent();
            if (parent == null ||
                    !parent.isVisibleToUser() ||
                    !parent.isEnabled() ||
                    !parent.isClickable()) {
                return false;
            }

            CharSequence parentClass = parent.getClassName();
            if (parentClass == null ||
                    !"android.view.View".contentEquals(parentClass)) {
                return false;
            }

            return secondPassportHasClickAction(parent)
                    && secondPassportHasCopyDescription(parent, 0, 2);
        } finally {
            if (parent != null) parent.recycle();
        }
    }

    private boolean secondPassportHasClickAction(
            android.view.accessibility.AccessibilityNodeInfo node) {
        if (node == null) return false;
        try {
            for (android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction action
                    : node.getActionList()) {
                if (action != null &&
                        action.getId() ==
                        android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK) {
                    return true;
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private boolean secondPassportHasCopyDescription(
            android.view.accessibility.AccessibilityNodeInfo node,
            int depth,
            int maxDepth) {
        if (node == null || depth > maxDepth) return false;

        CharSequence desc = node.getContentDescription();
        if (desc != null &&
                "Скопировать код".contentEquals(desc.toString().trim())) {
            return true;
        }

        if (depth == maxDepth) return false;

        for (int i = 0; i < node.getChildCount(); i++) {
            android.view.accessibility.AccessibilityNodeInfo child = null;
            try {
                child = node.getChild(i);
                if (child != null &&
                        secondPassportHasCopyDescription(
                                child, depth + 1, maxDepth)) {
                    return true;
                }
            } finally {
                if (child != null) child.recycle();
            }
        }
        return false;
    }

    // Геометрия применяется только для рамки, не для идентификации.
    private android.graphics.Rect secondPassportHighlightBounds(
            android.view.accessibility.AccessibilityNodeInfo node,
            android.graphics.Rect fallback) {
        android.graphics.Rect result = fallback == null
                ? new android.graphics.Rect()
                : new android.graphics.Rect(fallback);

        if (node == null) return result;

        android.view.accessibility.AccessibilityNodeInfo parent = null;
        try {
            parent = node.getParent();
            if (parent == null ||
                    !parent.isClickable() ||
                    !secondPassportHasClickAction(parent) ||
                    !secondPassportHasCopyDescription(parent, 0, 2)) {
                return result;
            }

            android.graphics.Rect parentBounds = new android.graphics.Rect();
            parent.getBoundsInScreen(parentBounds);
            if (!parentBounds.isEmpty()) return parentBounds;
        } catch (Exception ignored) {
        } finally {
            if (parent != null) parent.recycle();
        }

        return result;
    }

    private boolean isObservedChatCopyStructure(
            AccessibilityNodeInfo button) {
        return isSecondPassportCopyCodeButton(button);
    }

    private boolean hasDescriptionLength15(
            AccessibilityNodeInfo node) {

        if (node == null) return false;

        CharSequence desc = node.getContentDescription();

        if (desc != null && desc.length() == 15) {
            log("COPY CANDIDATE DESCRIPTION=" + desc);
            return true;
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);

            if (child != null &&
                    hasDescriptionLength15(child)) {
                return true;
            }
        }

        return false;
    }


    private void findCopyByObservedStructure(
        AccessibilityNodeInfo node,
        Rect container,
        int depth,
        int[] count,
        Rect[] bestBounds,
        double[] bestScore
    ) {
        if (node == null || depth > 20 || count[0] >= 700) return;
        count[0]++;

        if (node.isVisibleToUser() && node.isEnabled()) {
            Rect button = new Rect();
            node.getBoundsInScreen(button);

            int bw = button.width();
            int bh = button.height();

            boolean square =
                !button.isEmpty()
                && bw >= 48 && bh >= 48
                && bw <= 220 && bh <= 220
                && (double) bw / bh >= 0.80
                && (double) bw / bh <= 1.25;

            if (square && container.contains(button)) {
                AccessibilityNodeInfo row = null;

                try {
                    row = node.getParent();

                    if (row != null && row.isVisibleToUser()) {
                        Rect rowBounds = new Rect();
                        row.getBoundsInScreen(rowBounds);

                        int rw = rowBounds.width();
                        int rh = rowBounds.height();

                        double rowAspect =
                            rh > 0 ? (double) rw / rh : 0;

                        int rightGap = rowBounds.right - button.right;
                        int topDiff = Math.abs(rowBounds.top - button.top);
                        int bottomDiff =
                            Math.abs(rowBounds.bottom - button.bottom);

                        int containerRightGap =
                            container.right - rowBounds.right;
                        int containerBottomGap =
                            container.bottom - rowBounds.bottom;

                        boolean validRow =
                            container.contains(rowBounds)
                            && rh >= bh * 0.80
                            && rh <= bh * 1.25
                            && rowAspect >= 2.0
                            && rowAspect <= 4.5
                            && rw >= bw * 2
                            && rightGap >= 0
                            && rightGap <= 12
                            && topDiff <= 12
                            && bottomDiff <= 12;

                        boolean attachedToBottomRight =
                            containerRightGap >= -2
                            && containerRightGap
                                <= Math.max(48, container.width() * 0.10)
                            && containerBottomGap >= -2
                            && containerBottomGap
                                <= Math.max(48, container.height() * 0.10);

                        if (validRow && attachedToBottomRight) {
                            double candidateScore =
                                (double) containerRightGap
                                    / container.width()
                                + (double) containerBottomGap
                                    / container.height()
                                + (double) topDiff / bh
                                + (double) bottomDiff / bh;

                            log("COPY STRUCTURE CANDIDATE"
                                + " button=" + button.toShortString()
                                + " row=" + rowBounds.toShortString()
                                + " container=" + container.toShortString()
                                + " score=" + candidateScore);

                            if (candidateScore < bestScore[0]) {
                                bestScore[0] = candidateScore;
                                bestBounds[0] = new Rect(button);
                            }
                        }
                    }
                } catch (Exception e) {
                    log("COPY STRUCTURE NODE ERROR: " + e);
                } finally {
                    if (row != null) row.recycle();
                }
            }
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            if (count[0] >= 700) break;

            AccessibilityNodeInfo child = node.getChild(i);
            if (child == null) continue;

            findCopyByObservedStructure(
                child, container, depth + 1,
                count, bestBounds, bestScore
            );
            child.recycle();
        }
    }

    private void highlightAutoButtons(AccessibilityNodeInfo root) {
        clearAutoButtonHighlights();

        if (root == null) return;

        log("===== BUTTON CATALOG BEGIN =====");
        log("AUTO BUTTON HIGHLIGHT: начало обхода кнопок");
        int[] nodeCount = new int[]{0};
        int[] buttonIndex = new int[]{0};
        collectAndHighlightButtons(root, 0, nodeCount, buttonIndex);
        log("BUTTON CATALOG TOTAL=" + buttonIndex[0]);
        log("BUTTON CATALOG NODES=" + nodeCount[0]);
        log("===== BUTTON CATALOG END =====");
        log("AUTO BUTTON HIGHLIGHT: всего рамок="
            + autoButtonHighlightViews.size());
    }
    // AUTO_BUTTON_HIGHLIGHTS_END

    private void showHighlight(Rect rect, String label, int color) {
        removeHighlight();

        if (windowManager == null || rect == null || rect.isEmpty()) return;

        highlight = new HighlightView(this, label, color);

        int type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;

        highlightParams = new WindowManager.LayoutParams(
            rect.width(),
            rect.height(),
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE |
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        );

        highlightParams.gravity = Gravity.TOP | Gravity.LEFT;
        highlightParams.x = rect.left;
        highlightParams.y = rect.top;

        try {
            windowManager.addView(highlight, highlightParams);

            final int targetLeft = rect.left;
            final int targetTop = rect.top;

            highlight.post(() -> {
                if (highlight == null || highlightParams == null) return;

                int[] actual = new int[2];
                highlight.getLocationOnScreen(actual);

                int dx = targetLeft - actual[0];
                int dy = targetTop - actual[1];

                log("Рамка: цель=(" + targetLeft + "," + targetTop +
                    "), фактически=(" + actual[0] + "," + actual[1] + ")");

                if (dx != 0 || dy != 0) {
                    log("Измеренное смещение рамки: dx=" + dx + " dy=" + dy);
                    highlightParams.x += dx;
                    highlightParams.y += dy;

                    try {
                        windowManager.updateViewLayout(
                            highlight,
                            highlightParams
                        );
                    } catch (Exception e) {
                        log("Не удалось скорректировать рамку: " + e);
                    }
                } else {
                    log("Смещение рамки не обнаружено.");
                }
            });

        } catch (Exception e) {
            log("Ошибка отображения рамки: " + e);
        }
    }

    private void removeHighlight() {
        if (highlight != null && windowManager != null) {
            try {
                windowManager.removeView(highlight);
            } catch (Exception ignored) {
            }
        }

        highlight = null;
        highlightParams = null;
    }

    private void finishWithResult(String message) {
        log("РЕЗУЛЬТАТ: " + message);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        removeHighlight();
        processing = false;
        status("ready");
    }

    private void fail(String message) {
        log("ОШИБКА: " + message);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        removeHighlight();
        processing = false;
        status("error");
    }

    private void status(String value) {
        Intent intent = new Intent(ACTION_STATUS);
        intent.setPackage(getPackageName());
        intent.putExtra("status", value);
        sendBroadcast(intent);
    }

    private void log(String message) {
        DiagnosticLogger.log(message);
        android.util.Log.d("CopyTermixGPT", message);
    }

    private static class HighlightView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final String label;
        private final int color;

        HighlightView(Context context, String label, int color) {
            super(context);
            this.label = label;
            this.color = color;
            setBackground(new ColorDrawable(Color.TRANSPARENT));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(5);
            paint.setColor(color);
            canvas.drawRect(
                3, 3, getWidth() - 3, getHeight() - 3, paint
            );

            paint.setStyle(Paint.Style.FILL);
            paint.setTypeface(Typeface.DEFAULT_BOLD);

            String number = label.replaceAll("[^0-9]", "");
            if (number.isEmpty()) number = label;

            float size = Math.min(getWidth(), getHeight()) * 0.48f;
            size = Math.max(20f, Math.min(48f, size));
            paint.setTextSize(size);

            float textWidth = paint.measureText(number);
            android.graphics.Rect textBounds =
                    new android.graphics.Rect();
            paint.getTextBounds(number, 0, number.length(), textBounds);

            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            float pad = Math.max(7f, size * 0.22f);

            paint.setColor(0xDD202020);
            canvas.drawRoundRect(
                    cx - textWidth / 2f - pad,
                    cy - textBounds.height() / 2f - pad,
                    cx + textWidth / 2f + pad,
                    cy + textBounds.height() / 2f + pad,
                    9f, 9f, paint
            );

            paint.setColor(Color.WHITE);
            canvas.drawText(
                    number,
                    cx - textWidth / 2f,
                    cy - textBounds.exactCenterY(),
                    paint
            );
        }
    }

    private void resetCurrentOperation() {
        log("RESET: отмена текущей операции.");

        // Сначала инвалидируем AUTO, включая уже выполняющиеся жесты.
        autoRunning = false;
        autoRunToken++;
        autoPreviousSnapshot = null;
        autoMatchedWord = "";
        autoStagnantCount = 0;

        // Отменяем все ожидающие callbacks этого Handler.
        handler.removeCallbacksAndMessages(null);

        clearAutoButtonHighlights();
        processing = false;
        termuxProcessing = false;
        awaitingTermuxResult = false;
        insertedText = "";

        fullText = "";
        latestTermuxResult = null;
        latestTermuxResultTruncated = false;

        removeHighlight();
        status("reset");

        Toast.makeText(
            this,
            "RESET: текущая операция сброшена",
            Toast.LENGTH_SHORT
        ).show();

        log("RESET завершён. Файлы и история ChatGPT не затронуты. " +
            "Уже запущенный процесс Termux может продолжать работу; его callback будет проигнорирован.");
    }

    @Override
    public void onDestroy() {
 passportScanRemoveWindows(); // PASSPORT_SCAN_CLEANUP

        autoRunning = false;
        autoRunToken++;
        handler.removeCallbacks(autoStepRunnable);
        removeHighlight();

        if (receiverRegistered) {
            try {
                unregisterReceiver(receiver);
            } catch (Exception ignored) {
            }
            receiverRegistered = false;
        }

        if (callbackReceiverRegistered) {
            try {
                unregisterReceiver(termuxCallbackReceiver);
            } catch (Exception ignored) {
            }
            callbackReceiverRegistered = false;
        }

        super.onDestroy();
    }


    // PASSPORT_SCAN_FLOATING_BUTTON_V1
    private android.view.WindowManager passportScanWindowManager;
    private android.view.View passportScanButton;
    private android.view.View passportScanSelectionLayer;
    private boolean passportScanSelecting = false;

    private android.view.WindowManager.LayoutParams passportScanButtonParams;
    private float passportScanDownRawX;
    private float passportScanDownRawY;
    private int passportScanStartX;
    private int passportScanStartY;
    private boolean passportScanMoved = false;

    private final java.util.ArrayList<android.view.View>
        passportScanHighlightViews = new java.util.ArrayList<>();

    private static final int PASSPORT_SCAN_MAX_BUTTONS = 120;


    private int passportScanDp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }


    private void passportScanClearHighlights() {
        try {
            if (passportScanWindowManager != null) {
                for (int i = passportScanHighlightViews.size() - 1;
                     i >= 0; i--) {
                    android.view.View v = passportScanHighlightViews.get(i);
                    try {
                        if (v != null && v.getParent() != null) {
                            passportScanWindowManager.removeView(v);
                        }
                    } catch (Exception ignored) {}
                }
            }
        } finally {
            passportScanHighlightViews.clear();
        }
    }

    private void passportScanStopScanning() {
        passportScanSelecting = false;
        passportScanClearHighlights();
    }

    private void passportScanCopyPassport(String label, String passport) {
        try {
            android.content.ClipboardManager clipboard =
                (android.content.ClipboardManager)
                    getSystemService(CLIPBOARD_SERVICE);

            if (clipboard == null) {
                passportScanToast("Буфер обмена недоступен");
                passportScanStopScanning();
                return;
            }

            clipboard.setPrimaryClip(
                android.content.ClipData.newPlainText(
                    "Accessibility button passport", passport
                )
            );

            passportScanStopScanning();

            android.util.Log.i(
                "PassportScan",
                "PASSPORT_COPIED label=" + label
                    + " length=" + passport.length()
            );

            passportScanToast("Паспорт скопирован: " + label);

        } catch (Exception e) {
            passportScanStopScanning();
            android.util.Log.e(
                "PassportScan", "COPY_ERROR: " + e
            );
            passportScanToast("Ошибка копирования паспорта");
        }
    }

    private void passportScanAddHighlight(
        android.graphics.Rect bounds,
        String label,
        String passport
    ) {
        if (bounds == null || bounds.isEmpty()) return;
        if (passportScanWindowManager == null) return;

        try {
            HighlightView frame = new HighlightView(
                this,
                label,
                android.graphics.Color.rgb(0, 200, 83)
            );

            frame.setClickable(true);
            frame.setFocusable(false);
            frame.setOnClickListener(v ->
                passportScanCopyPassport(label, passport)
            );

            WindowManager.LayoutParams lp =
                new WindowManager.LayoutParams(
                    bounds.width(),
                    bounds.height(),
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT
                );

            lp.gravity = Gravity.TOP | Gravity.LEFT;
            lp.x = bounds.left;
            lp.y = bounds.top;

            passportScanWindowManager.addView(frame, lp);
            passportScanHighlightViews.add(frame);

        } catch (Exception e) {
            android.util.Log.e(
                "PassportScan", "HIGHLIGHT_ERROR: " + e
            );
        }
    }

    private void passportScanCollectButtons(
        android.view.accessibility.AccessibilityNodeInfo node,
        java.util.HashSet<String> seen,
        int[] count,
        int[] visited,
        int depth,
        int screenWidth,
        int screenHeight
    ) {
        if (node == null || depth > 45) return;
        if (visited[0]++ >= 3000) return;
        if (count[0] >= PASSPORT_SCAN_MAX_BUTTONS) return;

        try {
            android.graphics.Rect bounds = new android.graphics.Rect();
            node.getBoundsInScreen(bounds);

            String cls = String.valueOf(node.getClassName());

            boolean buttonClass =
                cls.endsWith(".Button") ||
                cls.endsWith("Button");

            boolean actionable =
                node.isClickable() ||
                passportScanHasClickAction(node);

            long area =
                (long) bounds.width() * (long) bounds.height();

            long screenArea =
                (long) screenWidth * (long) screenHeight;

            boolean reasonableSize =
                bounds.width() >= passportScanDp(18) &&
                bounds.height() >= passportScanDp(18) &&
                area > 0 &&
                area <= screenArea * 45L / 100L;

            boolean onScreen =
                bounds.right > 0 &&
                bounds.bottom > 0 &&
                bounds.left < screenWidth &&
                bounds.top < screenHeight;

            boolean candidate =
                node.isVisibleToUser() &&
                node.isEnabled() &&
                (actionable || buttonClass) &&
                reasonableSize &&
                onScreen;

            if (candidate) {
                String key = bounds.flattenToString();

                if (seen.add(key)) {
                    count[0]++;

                    String label = String.valueOf(count[0]);

                    CharSequence text = node.getText();
                    CharSequence desc = node.getContentDescription();

                    if (text != null && text.length() > 0) {
                        label += ": " + text;
                    } else if (desc != null && desc.length() > 0) {
                        label += ": " + desc;
                    } else {
                        label += ": " + cls;
                    }

                    String passport = buildAccessibilityPassport(node);

                    passportScanAddHighlight(
                        bounds,
                        label,
                        passport
                    );

                    android.util.Log.i(
                        "PassportScan",
                        "BUTTON #" + count[0]
                            + " class=" + cls
                            + " bounds=" + bounds.flattenToString()
                    );
                }
            }

            int children = Math.min(node.getChildCount(), 100);

            for (int i = 0; i < children; i++) {
                if (count[0] >= PASSPORT_SCAN_MAX_BUTTONS) break;
                if (visited[0] >= 3000) break;

                android.view.accessibility.AccessibilityNodeInfo child = null;

                try {
                    child = node.getChild(i);
                    if (child != null) {
                        passportScanCollectButtons(
                            child,
                            seen,
                            count,
                            visited,
                            depth + 1,
                            screenWidth,
                            screenHeight
                        );
                    }
                } catch (Exception ignored) {
                } finally {
                    if (child != null) {
                        try { child.recycle(); } catch (Exception ignored) {}
                    }
                }
            }

        } catch (Exception e) {
            android.util.Log.w(
                "PassportScan", "NODE_SCAN_ERROR: " + e
            );
        }
    }

    private void passportScanStartSelection() {
        if (passportScanSelecting) {
            passportScanStopScanning();
            passportScanToast("Сканирование остановлено");
            return;
        }

        try {
            passportScanWindowManager =
                (WindowManager) getSystemService(WINDOW_SERVICE);

            if (passportScanWindowManager == null) {
                passportScanToast("WindowManager недоступен");
                return;
            }

            passportScanClearHighlights();

            android.view.accessibility.AccessibilityNodeInfo root =
                getRootInActiveWindow();

            if (root == null) {
                passportScanToast("Не удалось получить экран приложения");
                return;
            }

            passportScanSelecting = true;

            int width = getResources().getDisplayMetrics().widthPixels;
            int height = getResources().getDisplayMetrics().heightPixels;

            int[] count = new int[]{0};
            int[] visited = new int[]{0};

            java.util.HashSet<String> seen =
                new java.util.HashSet<>();

            passportScanCollectButtons(
                root,
                seen,
                count,
                visited,
                0,
                width,
                height
            );

            android.util.Log.i(
                "PassportScan",
                "SCAN_FINISHED buttons=" + count[0]
                    + " nodes=" + visited[0]
            );

            if (count[0] == 0) {
                passportScanStopScanning();
                passportScanToast("Кнопки на текущем экране не найдены");
            } else {
                passportScanToast(
                    "Найдено кнопок: " + count[0]
                        + ". Нажми на нужную рамку"
                );
            }

        } catch (Exception e) {
            passportScanStopScanning();

            android.util.Log.e(
                "PassportScan", "SCAN_START_ERROR: " + e
            );

            passportScanToast("Ошибка сканирования");
        }
    }


    private void passportScanEnsureButton() {
        try {
            if (passportScanButton != null) return;

            passportScanWindowManager =
                (WindowManager) getSystemService(WINDOW_SERVICE);

            if (passportScanWindowManager == null) return;

            android.widget.TextView button =
                new android.widget.TextView(this);

            button.setText("СКАН");
            button.setTextColor(Color.WHITE);
            button.setTextSize(11);
            button.setGravity(Gravity.CENTER);
            button.setPadding(
                passportScanDp(5), passportScanDp(3),
                passportScanDp(5), passportScanDp(3)
            );

            android.graphics.drawable.GradientDrawable bg =
                new android.graphics.drawable.GradientDrawable();

            bg.setColor(Color.rgb(35, 95, 155));
            bg.setCornerRadius(passportScanDp(10));
            bg.setStroke(
                passportScanDp(1),
                Color.WHITE
            );

            button.setBackground(bg);
            button.setElevation(passportScanDp(6));
            button.setClickable(true);
            button.setFocusable(false);
            button.setImportantForAccessibility(
                View.IMPORTANT_FOR_ACCESSIBILITY_NO
            );

            WindowManager.LayoutParams lp =
                new WindowManager.LayoutParams(
                    passportScanDp(60),
                    passportScanDp(38),
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT
                );

            lp.gravity = Gravity.TOP | Gravity.RIGHT;
            lp.x = passportScanDp(12);
            lp.y = passportScanDp(180);

            button.setOnTouchListener((v, event) -> {
                switch (event.getAction()) {
                    case android.view.MotionEvent.ACTION_DOWN:
                        passportScanDownRawX = event.getRawX();
                        passportScanDownRawY = event.getRawY();
                        passportScanStartX = lp.x;
                        passportScanStartY = lp.y;
                        passportScanMoved = false;
                        return true;

                    case android.view.MotionEvent.ACTION_MOVE:
                        float dx = event.getRawX() - passportScanDownRawX;
                        float dy = event.getRawY() - passportScanDownRawY;

                        if (Math.abs(dx) > passportScanDp(5)
                            || Math.abs(dy) > passportScanDp(5)) {
                            passportScanMoved = true;
                        }

                        if (passportScanMoved) {
                            lp.x = Math.max(
                                0,
                                passportScanStartX - (int) dx
                            );
                            lp.y = Math.max(
                                0,
                                passportScanStartY + (int) dy
                            );

                            try {
                                passportScanWindowManager.updateViewLayout(
                                    button, lp
                                );
                            } catch (Exception ignored) {}
                        }
                        return true;

                    case android.view.MotionEvent.ACTION_UP:
                        float moveX =
                            Math.abs(event.getRawX() - passportScanDownRawX);
                        float moveY =
                            Math.abs(event.getRawY() - passportScanDownRawY);

                        if (!passportScanMoved
                            && moveX < passportScanDp(8)
                            && moveY < passportScanDp(8)) {
                            passportScanStartSelection();
                        }
                        return true;

                    case android.view.MotionEvent.ACTION_CANCEL:
                        return true;
                }
                return true;
            });

            passportScanWindowManager.addView(button, lp);

            passportScanButton = button;
            passportScanButtonParams = lp;

            android.util.Log.i(
                "PassportScan",
                "SCAN_BUTTON_CREATED size=60x38 movable=true"
            );

        } catch (Exception e) {
            android.util.Log.e(
                "PassportScan",
                "BUTTON_CREATE_ERROR: " + e
            );
        }
    }


    

    private void passportScanFinishSelection(float x, float y) {
        passportScanRemoveSelectionLayer();

        try {
            android.view.accessibility.AccessibilityNodeInfo root =
                getRootInActiveWindow();

            if (root == null) {
                passportScanToast("Не удалось получить дерево Accessibility");
                return;
            }

            java.util.ArrayList<android.view.accessibility.AccessibilityNodeInfo> path =
                new java.util.ArrayList<>();

            passportScanFindPath(root, (int) x, (int) y, path, 0);

            if (path.isEmpty()) {
                passportScanToast("В выбранной области элемент не найден");
                return;
            }

            android.view.accessibility.AccessibilityNodeInfo target =
                passportScanChooseTarget(path);

            String passport = passportScanBuildPassport(target, x, y, path);

            android.content.ClipboardManager clipboard =
                (android.content.ClipboardManager)
                    getSystemService(CLIPBOARD_SERVICE);

            if (clipboard == null) {
                passportScanToast("Буфер обмена недоступен");
                return;
            }

            clipboard.setPrimaryClip(
                android.content.ClipData.newPlainText(
                    "Accessibility passport", passport
                )
            );

            android.util.Log.i("PassportScan",
                "PASSPORT_COPIED length=" + passport.length());

            passportScanToast("Паспорт скопирован в буфер обмена");

        } catch (Exception e) {
            android.util.Log.e("PassportScan",
                "SCAN_ERROR: " + e.getMessage());
            passportScanToast("Ошибка сканирования: " + e.getMessage());
        }
    }

    private boolean passportScanFindPath(
        android.view.accessibility.AccessibilityNodeInfo node,
        int x, int y,
        java.util.ArrayList<android.view.accessibility.AccessibilityNodeInfo> path,
        int depth
    ) {
        if (node == null || depth > 40) return false;

        android.graphics.Rect bounds = new android.graphics.Rect();
        node.getBoundsInScreen(bounds);

        if (!bounds.contains(x, y)) return false;

        path.add(node);

        int count = Math.min(node.getChildCount(), 200);
        for (int i = 0; i < count; i++) {
            android.view.accessibility.AccessibilityNodeInfo child =
                node.getChild(i);

            if (child != null &&
                passportScanFindPath(child, x, y, path, depth + 1)) {
                return true;
            }
        }

        return true;
    }

    private android.view.accessibility.AccessibilityNodeInfo
    passportScanChooseTarget(
        java.util.ArrayList<android.view.accessibility.AccessibilityNodeInfo> path
    ) {
        // Ищем ближайший к нажатой области кликабельный узел.
        for (int i = path.size() - 1; i >= 0; i--) {
            android.view.accessibility.AccessibilityNodeInfo n = path.get(i);
            if (n.isClickable() || passportScanHasClickAction(n)) {
                return n;
            }
        }

        return path.get(path.size() - 1);
    }

    private boolean passportScanHasClickAction(
        android.view.accessibility.AccessibilityNodeInfo node
    ) {
        try {
            for (android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction a
                    : node.getActionList()) {
                if (a.getId() ==
                    android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK) {
                    return true;
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    private String passportScanBuildPassport(
        android.view.accessibility.AccessibilityNodeInfo node,
        float tapX, float tapY,
        java.util.ArrayList<android.view.accessibility.AccessibilityNodeInfo> path
    ) {
        StringBuilder out = new StringBuilder(4096);

        out.append("===== ACCESSIBILITY ELEMENT PASSPORT =====\n");
        out.append("SOURCE=CopyTermixGPT floating scanner\n");
        out.append("TAP_X=").append(tapX).append('\n');
        out.append("TAP_Y=").append(tapY).append('\n');
        out.append("PATH_DEPTH=").append(path.size()).append('\n');

        out.append("\n[SELECTED ELEMENT]\n");
        passportScanAppendNode(out, node);

        out.append("\n[ANCESTOR CHAIN]\n");
        android.view.accessibility.AccessibilityNodeInfo parent =
            node.getParent();

        int level = 1;
        while (parent != null && level <= 10) {
            out.append("\nPARENT_LEVEL=").append(level++).append('\n');
            passportScanAppendNode(out, parent);
            parent = parent.getParent();
        }

        out.append("\n[TOUCHED NODE PATH]\n");
        for (int i = 0; i < path.size(); i++) {
            out.append("\nPATH_LEVEL=").append(i).append('\n');
            passportScanAppendNode(out, path.get(i));
        }

        out.append("\n===== END PASSPORT =====\n");
        out.append("CHATGPT_AUTO_CLICK=NOT_PERFORMED\n");

        return out.toString();
    }

    private void passportScanAppendNode(
        StringBuilder out,
        android.view.accessibility.AccessibilityNodeInfo n
    ) {
        if (n == null) {
            out.append("NODE=null\n");
            return;
        }

        android.graphics.Rect r = new android.graphics.Rect();
        n.getBoundsInScreen(r);

        out.append("CLASS=").append(n.getClassName()).append('\n');
        out.append("PACKAGE=").append(n.getPackageName()).append('\n');
        out.append("TEXT=").append(n.getText()).append('\n');
        out.append("CONTENT_DESCRIPTION=")
            .append(n.getContentDescription()).append('\n');

        try {
            out.append("VIEW_ID=").append(n.getViewIdResourceName()).append('\n');
        } catch (Exception e) {
            out.append("VIEW_ID=UNAVAILABLE\n");
        }

        out.append("CLICKABLE=").append(n.isClickable()).append('\n');
        out.append("ENABLED=").append(n.isEnabled()).append('\n');
        out.append("VISIBLE_TO_USER=").append(n.isVisibleToUser()).append('\n');
        out.append("FOCUSABLE=").append(n.isFocusable()).append('\n');
        out.append("FOCUSED=").append(n.isFocused()).append('\n');
        out.append("SELECTED=").append(n.isSelected()).append('\n');
        out.append("CHECKABLE=").append(n.isCheckable()).append('\n');
        out.append("CHECKED=").append(n.isChecked()).append('\n');
        out.append("LONG_CLICKABLE=").append(n.isLongClickable()).append('\n');
        out.append("SCROLLABLE=").append(n.isScrollable()).append('\n');
        out.append("EDITABLE=").append(n.isEditable()).append('\n');
        out.append("CHILD_COUNT=").append(n.getChildCount()).append('\n');

        out.append("BOUNDS_SCREEN=")
            .append(r.left).append(' ')
            .append(r.top).append(' ')
            .append(r.right).append(' ')
            .append(r.bottom).append('\n');

        out.append("ACTIONS=");
        try {
            for (android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction a
                    : n.getActionList()) {
                out.append("{id=").append(a.getId())
                    .append(",label=").append(a.getLabel()).append("} ");
            }
        } catch (Exception e) {
            out.append("UNAVAILABLE");
        }
        out.append('\n');
    }

    private void passportScanRemoveSelectionLayer() {
        try {
            if (passportScanSelectionLayer != null &&
                passportScanWindowManager != null) {
                passportScanWindowManager.removeView(
                    passportScanSelectionLayer
                );
            }
        } catch (Exception ignored) {}

        passportScanSelectionLayer = null;
        passportScanSelecting = false;
    }

    private void passportScanRemoveWindows() {
        passportScanStopScanning();

        passportScanRemoveSelectionLayer();

        try {
            if (passportScanButton != null &&
                passportScanWindowManager != null) {
                passportScanWindowManager.removeView(passportScanButton);
            }
        } catch (Exception ignored) {}

        passportScanButton = null;
        passportScanButtonParams = null;
    }

    private void passportScanToast(String message) {
        try {
            android.widget.Toast.makeText(
                this, message, android.widget.Toast.LENGTH_LONG
            ).show();
        } catch (Exception ignored) {}
    }
    // END PASSPORT_SCAN_FLOATING_BUTTON_V1

}
