package io.finett.droidclaw.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.google.gson.JsonObject;

import java.io.File;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.SynchronousQueue;

import io.finett.droidclaw.MainActivity;
import io.finett.droidclaw.R;
import io.finett.droidclaw.agent.AgentLoop;
import io.finett.droidclaw.agent.ConversationSummarizer;
import io.finett.droidclaw.agent.GuidelinesManager;
import io.finett.droidclaw.agent.GuidelinesReflector;
import io.finett.droidclaw.agent.IdentityManager;
import io.finett.droidclaw.agent.LessonExtractor;
import io.finett.droidclaw.agent.MemoryContextBuilder;
import io.finett.droidclaw.api.LlmApiService;
import io.finett.droidclaw.filesystem.WorkspaceManager;
import io.finett.droidclaw.model.ChatMessage;
import io.finett.droidclaw.repository.ChatRepository;
import io.finett.droidclaw.repository.LessonRepository;
import io.finett.droidclaw.repository.MemoryRepository;
import io.finett.droidclaw.tool.ToolRegistry;
import io.finett.droidclaw.util.SettingsManager;

/**
 * Foreground service that hosts the AgentLoop so API requests survive app minimization.
 *
 * <p>UI clients (ChatFragment) bind to this service, register a UICallback, and delegate
 * all agent interactions through it. When the UI unbinds, the loop keeps running and posts
 * a foreground notification. If a tool requires approval while the UI is detached, the
 * worker thread parks on a SynchronousQueue until the user returns and approves/denies.</p>
 */
public class AgentExecutionService extends Service {

    private static final String TAG = "AgentExecutionService";

    static final String CHANNEL_ID = "droidclaw_agent_exec";
    private static final int NOTIFICATION_ID = 8802;

    /** Channel and ID for agent-completion notifications (non-foreground). */
    private static final String CHANNEL_ID_DONE = "droidclaw_agent_done";
    private static final int NOTIFICATION_ID_DONE = 8803;

    // ==================== Session state ====================

    /** Per-session state kept for the lifetime of the loop. */
    public static class AgentSession {
        public enum State { RUNNING, PAUSED_APPROVAL, COMPLETED, ERROR }

        public final String sessionId;

        volatile State state = State.RUNNING;

        /** Queued approval request waiting for the user to return. */
        volatile PendingApproval pendingApproval;

        /** The running loop; set just before {@link AgentLoop#start} so it can be cancelled. */
        volatile AgentLoop agentLoop;

        /** Set when cancellation is requested before the loop has started. */
        volatile boolean cancelRequested;

        /** Worker thread parks here when approval is needed and UI is detached. */
        final SynchronousQueue<Boolean> approvalQueue = new SynchronousQueue<>();

        /** Snapshot of conversation history after the loop completes. */
        volatile List<ChatMessage> finalHistory;

        /** Final response text. */
        volatile String finalResponse;

        /** Error text if state == ERROR. */
        volatile String errorMessage;

        AgentSession(String sessionId) {
            this.sessionId = sessionId;
        }
    }

    /** Approval request paused until UI reconnects. */
    public static class PendingApproval {
        public final String toolName;
        public final String description;
        public final JsonObject arguments;

        PendingApproval(String toolName, String description, JsonObject arguments) {
            this.toolName = toolName;
            this.description = description;
            this.arguments = arguments;
        }
    }

    // ==================== UI callback ====================

    public interface UICallback {
        void onProgress(String status);
        void onToolCall(String toolName, String arguments);
        void onToolResult(String toolName, String result);
        void onComplete(String response, List<ChatMessage> history);
        void onError(String error);
        void onApprovalRequired(String toolName, String description, JsonObject arguments,
                                AgentLoop.ApprovalCallback approvalCallback);

        /**
         * Incremental text delta while a streaming (SSE) response is arriving.
         * Optional — no-op by default for UIs that only render the final result.
         */
        default void onStreamDelta(String delta) {}

        /**
         * The run was cancelled by the user. {@code history} is the final
         * conversation including any partially streamed response text.
         * Optional — no-op by default.
         */
        default void onCancelled(List<ChatMessage> history) {}
    }

    // ==================== Binder ====================

    public class LocalBinder extends Binder {
        public AgentExecutionService getService() {
            return AgentExecutionService.this;
        }
    }

    private final IBinder binder = new LocalBinder();

    // ==================== Fields ====================

    private final ConcurrentHashMap<String, AgentSession> sessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, UICallback> uiCallbacks = new ConcurrentHashMap<>();

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private NotificationManager notificationManager;
    private PowerManager.WakeLock wakeLock;

    // ==================== Lifecycle ====================

    @Override
    public void onCreate() {
        super.onCreate();
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        createNotificationChannel();
        createDoneNotificationChannel();
        Log.d(TAG, "AgentExecutionService created");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Must call startForeground() immediately to satisfy Android's
        // ForegroundServiceDidNotStartInTimeException requirement (5-second window).
        // The notification is updated later when the agent loop actually starts.
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, buildNotification(false));
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        releaseWakeLock();
        // Unblock any parked approval threads so they can terminate cleanly.
        for (AgentSession session : sessions.values()) {
            if (session.state == AgentSession.State.PAUSED_APPROVAL) {
                try {
                    session.approvalQueue.offer(false);
                } catch (Exception ignored) {
                }
            }
        }
        Log.d(TAG, "AgentExecutionService destroyed");
    }

    // ==================== Public API ====================

    /**
     * Start the AgentLoop for the given session on a background thread.
     *
     * <p>If the session is already active this call is a no-op; the caller should
     * register a UICallback first and then check {@link #getSession} to see if the
     * session is in PAUSED_APPROVAL state and resume it.</p>
     *
     * @param sessionId          chat session identifier
     * @param conversationHistory snapshot of messages to pass to the loop
     * @param modelContextWindow  context window tokens for the selected model
     */
    public void startAgentLoop(String sessionId, List<ChatMessage> conversationHistory,
                               int modelContextWindow) {
        if (sessions.containsKey(sessionId)) {
            Log.d(TAG, "Session already active: " + sessionId);
            return;
        }

        AgentSession session = new AgentSession(sessionId);
        sessions.put(sessionId, session);

        acquireWakeLock();
        ensureForeground();

        Thread worker = new Thread(() -> runAgentLoop(session, conversationHistory, modelContextWindow),
                "agent-loop-" + sessionId);
        worker.setDaemon(true);
        worker.start();

        Log.d(TAG, "Started agent loop for session: " + sessionId);
    }

    /**
     * Register a UI callback for the given session. Must be called on the main thread.
     *
     * <p>If the session already completed or errored, the callback will be invoked
     * immediately with the cached result.</p>
     */
    public void registerUICallback(String sessionId, UICallback callback) {
        uiCallbacks.put(sessionId, callback);

        AgentSession session = sessions.get(sessionId);
        if (session == null) return;

        // Deliver buffered result if loop already finished while UI was detached.
        if (session.state == AgentSession.State.COMPLETED && session.finalHistory != null) {
            callback.onComplete(session.finalResponse, session.finalHistory);
            sessions.remove(sessionId);
        } else if (session.state == AgentSession.State.ERROR) {
            callback.onError(session.errorMessage != null ? session.errorMessage : "Unknown error");
            sessions.remove(sessionId);
        } else if (session.state == AgentSession.State.PAUSED_APPROVAL
                && session.pendingApproval != null) {
            // Resume approval dialog now that the UI is back.
            PendingApproval pa = session.pendingApproval;
            callback.onApprovalRequired(pa.toolName, pa.description, pa.arguments,
                    new AgentLoop.ApprovalCallback() {
                        @Override
                        public void onApproved() {
                            session.state = AgentSession.State.RUNNING;
                            session.pendingApproval = null;
                            session.approvalQueue.offer(true);
                            updateNotification();
                        }

                        @Override
                        public void onDenied() {
                            session.state = AgentSession.State.RUNNING;
                            session.pendingApproval = null;
                            session.approvalQueue.offer(false);
                            updateNotification();
                        }
                    });
        }
    }

    /** Unregister the UI callback for a session. The loop keeps running. */
    public void unregisterUICallback(String sessionId) {
        uiCallbacks.remove(sessionId);
        updateNotification();
    }

    /** Return the current session state, or null if no session exists. */
    public AgentSession getSession(String sessionId) {
        return sessions.get(sessionId);
    }

    /** Return true if there is an active (non-terminal) session for this id. */
    public boolean isSessionActive(String sessionId) {
        AgentSession session = sessions.get(sessionId);
        return session != null
                && session.state != AgentSession.State.COMPLETED
                && session.state != AgentSession.State.ERROR;
    }

    /**
     * Cancel the agent loop for the given session. In-flight HTTP requests are
     * aborted, any partially streamed response text is persisted, and the UI
     * callback (if attached) receives {@link UICallback#onCancelled(List)}.
     */
    public void cancelAgentLoop(String sessionId) {
        AgentSession session = sessions.get(sessionId);
        if (session == null) {
            Log.d(TAG, "cancelAgentLoop: no active session for " + sessionId);
            return;
        }
        Log.i(TAG, "Cancelling agent loop for session: " + sessionId);
        session.cancelRequested = true;

        // Wake a thread parked waiting for approval so the loop can observe
        // the cancellation instead of blocking on the dialog outcome.
        if (session.state == AgentSession.State.PAUSED_APPROVAL) {
            session.pendingApproval = null;
            session.approvalQueue.offer(false);
        }

        AgentLoop loop = session.agentLoop;
        if (loop != null) {
            loop.cancel();
        }
    }

    /**
     * Persist the (possibly partial) conversation and notify the UI that the run
     * was cancelled. Idempotent: ignored if the session already reached a
     * terminal state.
     */
    private void deliverCancelled(AgentSession session, List<ChatMessage> history) {
        if (session.state == AgentSession.State.COMPLETED
                || session.state == AgentSession.State.ERROR) {
            return;
        }
        session.state = AgentSession.State.COMPLETED;
        session.finalHistory = history;

        // Persist messages regardless of UI state.
        ChatRepository chatRepository = new ChatRepository(getApplicationContext());
        chatRepository.saveMessages(session.sessionId, history);

        mainHandler.post(() -> {
            UICallback cb = uiCallbacks.remove(session.sessionId);
            if (cb != null) {
                cb.onCancelled(history);
            }
            sessions.remove(session.sessionId);
            checkIdleAndStop();
        });
    }

    /**
     * Self-improvement hook: after the base LLM response completes, run a
     * fire-and-forget structured analysis of the conversation and update
     * {@code .agent/GUIDELINES.md} when a durable workflow improvement was
     * found. Skipped when guidelines learning is disabled or the conversation
     * is too short to carry useful signal. Never affects the user-visible flow.
     */
    private void maybeAnalyzeGuidelines(List<ChatMessage> history, GuidelinesManager guidelinesManager) {
        try {
            SettingsManager settingsManager = new SettingsManager(getApplicationContext());
            io.finett.droidclaw.model.AgentConfig config = settingsManager.getAgentConfig();
            if (config == null || !config.isGuidelinesLearningEnabled()) {
                Log.d(TAG, "Guidelines learning disabled, skipping analysis");
                return;
            }
            if (history == null || history.size() < 2) {
                Log.d(TAG, "Conversation too short for guidelines analysis");
                return;
            }

            LlmApiService analysisApi = new LlmApiService(settingsManager);
            GuidelinesReflector reflector = new GuidelinesReflector(analysisApi, guidelinesManager);
            reflector.analyze(history);
        } catch (Exception e) {
            Log.w(TAG, "Guidelines analysis skipped", e);
        }
    }

    /**
     * Self-improvement hook (layer ①): after the run completes, extract
     * durable lessons from the transcript and append them to the lesson
     * store. Skipped when lesson extraction is disabled or the conversation
     * is too short. Never affects the user-visible flow.
     */
    private void maybeExtractLessons(String sessionId, List<ChatMessage> history,
                                     WorkspaceManager workspaceManager) {
        try {
            SettingsManager settingsManager = new SettingsManager(getApplicationContext());
            io.finett.droidclaw.model.AgentConfig config = settingsManager.getAgentConfig();
            if (config == null || !config.isLessonExtractionEnabled()) {
                Log.d(TAG, "Lesson extraction disabled, skipping");
                return;
            }

            LessonRepository lessonRepository = new LessonRepository(
                    new File(workspaceManager.getMemoryDirectory(), "lessons"));
            LlmApiService extractionApi = new LlmApiService(settingsManager);
            LessonExtractor extractor = new LessonExtractor(extractionApi, lessonRepository);
            extractor.extract(sessionId, history);
        } catch (Exception e) {
            Log.w(TAG, "Lesson extraction skipped", e);
        }
    }

    // ==================== Worker thread ====================

    private void runAgentLoop(AgentSession session, List<ChatMessage> conversationHistory,
                               int modelContextWindow) {
        try {
            SettingsManager settingsManager = new SettingsManager(getApplicationContext());
            LlmApiService apiService = new LlmApiService(settingsManager);
            ToolRegistry toolRegistry = new ToolRegistry(getApplicationContext(), settingsManager);

            WorkspaceManager workspaceManager = new WorkspaceManager(getApplicationContext());
            MemoryRepository memoryRepository = new MemoryRepository(workspaceManager);
            GuidelinesManager guidelinesManager = new GuidelinesManager(workspaceManager);

            ConversationSummarizer summarizer = new ConversationSummarizer(
                    apiService, memoryRepository, modelContextWindow);
            LessonRepository lessonRepository = new LessonRepository(
                    new File(workspaceManager.getMemoryDirectory(), "lessons"));
            MemoryContextBuilder memoryContext = new MemoryContextBuilder(
                    memoryRepository, lessonRepository,
                    settingsManager.getLessonConsolidationLastRunMillis());

            IdentityManager identityManager = new IdentityManager(
                    getApplicationContext(), workspaceManager);

            AgentLoop agentLoop = new AgentLoop(
                    apiService, toolRegistry, settingsManager, summarizer, memoryContext);
            session.agentLoop = agentLoop;

            // Cancellation was requested before the loop even started (user tapped
            // stop within milliseconds of sending). Deliver it without running.
            if (session.cancelRequested) {
                Log.d(TAG, "Session cancelled before loop start: " + session.sessionId);
                deliverCancelled(session, conversationHistory);
                try {
                    toolRegistry.shutdown();
                } catch (Exception ignored) {
                }
                return;
            }

            try {
                List<ChatMessage> identityMessages = identityManager.getIdentityMessages();
                agentLoop.setIdentityContext(identityMessages);
            } catch (Exception e) {
                Log.w(TAG, "Could not load identity context", e);
            }

            try {
                String guidelines = guidelinesManager.loadGuidelines();
                if (!guidelines.trim().isEmpty()) {
                    agentLoop.setGuidelinesContext(guidelines);
                }
            } catch (Exception e) {
                Log.w(TAG, "Could not load guidelines context", e);
            }

            agentLoop.start(conversationHistory, new AgentLoop.AgentCallback() {
                @Override
                public void onProgress(String status) {
                    mainHandler.post(() -> {
                        UICallback cb = uiCallbacks.get(session.sessionId);
                        if (cb != null) cb.onProgress(status);
                    });
                }

                @Override
                public void onStreamDelta(String delta) {
                    mainHandler.post(() -> {
                        UICallback cb = uiCallbacks.get(session.sessionId);
                        if (cb != null) cb.onStreamDelta(delta);
                    });
                }

                @Override
                public void onToolCall(String toolName, String arguments) {
                    mainHandler.post(() -> {
                        UICallback cb = uiCallbacks.get(session.sessionId);
                        if (cb != null) cb.onToolCall(toolName, arguments);
                    });
                }

                @Override
                public void onToolResult(String toolName, String result) {
                    mainHandler.post(() -> {
                        UICallback cb = uiCallbacks.get(session.sessionId);
                        if (cb != null) cb.onToolResult(toolName, result);
                    });
                }

                @Override
                public void onComplete(String finalResponse, List<ChatMessage> history) {
                    session.state = AgentSession.State.COMPLETED;
                    session.finalResponse = finalResponse;
                    session.finalHistory = history;

                    // Persist messages regardless of UI state.
                    ChatRepository chatRepository = new ChatRepository(getApplicationContext());
                    chatRepository.saveMessages(session.sessionId, history);

                    // Self-improvement: analyze the finished conversation and update
                    // GUIDELINES.md when a durable workflow improvement was found.
                    maybeAnalyzeGuidelines(history, guidelinesManager);

                    // Self-improvement layer ①: extract durable lessons into the
                    // JSONL lesson store for injection into future conversations.
                    maybeExtractLessons(session.sessionId, history, workspaceManager);

                    mainHandler.post(() -> {
                        UICallback cb = uiCallbacks.remove(session.sessionId);
                        if (cb != null) {
                            cb.onComplete(finalResponse, history);
                        } else {
                            // App is not in the foreground — post a completion notification.
                            showCompletionNotification(session.sessionId, finalResponse);
                        }
                        sessions.remove(session.sessionId);
                        checkIdleAndStop();
                    });
                }

                @Override
                public void onError(String error) {
                    session.state = AgentSession.State.ERROR;
                    session.errorMessage = error;

                    mainHandler.post(() -> {
                        UICallback cb = uiCallbacks.remove(session.sessionId);
                        if (cb != null) {
                            cb.onError(error);
                        } else {
                            // App is not in the foreground — post an error notification.
                            showErrorNotification(session.sessionId, error);
                        }
                        sessions.remove(session.sessionId);
                        checkIdleAndStop();
                    });
                }

                @Override
                public void onApprovalRequired(String toolName, String description,
                                               JsonObject arguments,
                                               AgentLoop.ApprovalCallback approvalCallback) {
                    UICallback cb = uiCallbacks.get(session.sessionId);
                    if (cb != null) {
                        // UI is attached — delegate directly.
                        mainHandler.post(() ->
                                cb.onApprovalRequired(toolName, description, arguments,
                                        approvalCallback));
                    } else {
                        // UI is detached — park the worker thread and show notification.
                        session.state = AgentSession.State.PAUSED_APPROVAL;
                        session.pendingApproval = new PendingApproval(
                                toolName, description, arguments);
                        mainHandler.post(AgentExecutionService.this::updateNotification);

                        Log.d(TAG, "Approval required while UI detached — parking thread for: "
                                + toolName);

                        // Block until the user returns and approves/denies.
                        Boolean approved;
                        try {
                            approved = session.approvalQueue.take();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            approved = false;
                        }

                        if (Boolean.TRUE.equals(approved)) {
                            approvalCallback.onApproved();
                        } else {
                            approvalCallback.onDenied();
                        }
                    }
                }

                @Override
                public void onCancelled(List<ChatMessage> history) {
                    deliverCancelled(session, history);
                }
            });

            try {
                toolRegistry.shutdown();
            } catch (Exception ignored) {
            }

        } catch (Exception e) {
            Log.e(TAG, "Unexpected error in agent loop worker", e);
            session.state = AgentSession.State.ERROR;
            session.errorMessage = "Internal error: " + e.getMessage();

            mainHandler.post(() -> {
                UICallback cb = uiCallbacks.remove(session.sessionId);
                if (cb != null) cb.onError(session.errorMessage);
                sessions.remove(session.sessionId);
                checkIdleAndStop();
            });
        }
    }

    // ==================== Foreground / notification ====================

    private void ensureForeground() {
        startForeground(NOTIFICATION_ID, buildNotification(false));
        acquireWakeLock();
    }

    private void checkIdleAndStop() {
        if (sessions.isEmpty()) {
            stopForeground(true);
            releaseWakeLock();
            stopSelf();
            Log.d(TAG, "All sessions complete — service stopped");
        } else {
            updateNotification();
        }
    }

    private void updateNotification() {
        boolean hasPausedApproval = false;
        for (AgentSession s : sessions.values()) {
            if (s.state == AgentSession.State.PAUSED_APPROVAL) {
                hasPausedApproval = true;
                break;
            }
        }
        notificationManager.notify(NOTIFICATION_ID, buildNotification(hasPausedApproval));
    }

    private Notification buildNotification(boolean waitingForApproval) {
        Intent openIntent = new Intent(this, MainActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int piFlags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                ? PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
                : PendingIntent.FLAG_UPDATE_CURRENT;
        PendingIntent openPending = PendingIntent.getActivity(this, 0, openIntent, piFlags);

        String title = waitingForApproval
                ? "DroidClaw — approval required"
                : "DroidClaw — agent is running";
        String text = waitingForApproval
                ? "Tap to approve or deny the pending action"
                : "Processing your request in the background…";

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_popup_sync)
                .setContentTitle(title)
                .setContentText(text)
                .setPriority(waitingForApproval
                        ? NotificationCompat.PRIORITY_HIGH
                        : NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .setAutoCancel(false)
                .setContentIntent(openPending)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "DroidClaw Agent",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Shows when the agent is processing a request in the background");
            channel.setShowBadge(false);
            notificationManager.createNotificationChannel(channel);
        }
    }

    /**
     * Channel for agent-done notifications. Uses DEFAULT importance so the
     * user actually hears/sees when the agent finishes while the app is away.
     */
    private void createDoneNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID_DONE,
                    "DroidClaw Agent Status",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            channel.setDescription("Posted when the agent completes a task while the app is closed");
            channel.setShowBadge(true);
            notificationManager.createNotificationChannel(channel);
        }
    }

    // ==================== Completion / error notifications ====================

    /**
     * Show a non-foreground notification when the agent completes and the UI is detached.
     */
    private void showCompletionNotification(String sessionId, String responseText) {
        // Replace the old foreground notification and then show the done notification.
        stopForeground(false);
        notificationManager.notify(NOTIFICATION_ID_DONE,
                buildCompletionNotification(sessionId, responseText));
    }

    /**
     * Show a non-foreground notification when the agent errors and the UI is detached.
     */
    private void showErrorNotification(String sessionId, String error) {
        stopForeground(false);
        notificationManager.notify(NOTIFICATION_ID_DONE,
                buildErrorNotification(sessionId, error));
    }

    /**
     * Build a notification that the agent has completed its work.
     * The response preview is truncated to ~200 chars for the notification.
     */
    private Notification buildCompletionNotification(String sessionId, String responseText) {
        Intent openIntent = new Intent(this, MainActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        openIntent.putExtra("session_id", sessionId);
        int piFlags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                ? PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
                : PendingIntent.FLAG_UPDATE_CURRENT;
        PendingIntent openPending = PendingIntent.getActivity(this, 1, openIntent, piFlags);

        // Strip markdown-ish formatting for notification readability.
        String preview = stripMarkdown(responseText);
        if (preview.length() > 200) {
            preview = preview.substring(0, 200) + "…";
        }

        return new NotificationCompat.Builder(this, CHANNEL_ID_DONE)
                .setSmallIcon(android.R.drawable.ic_popup_sync)
                .setContentTitle("DroidClaw — agent completed")
                .setContentText(preview)
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText(preview))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setOngoing(false)
                .setContentIntent(openPending)
                .setShowWhen(true)
                .build();
    }

    /**
     * Build a notification that the agent encountered an error.
     */
    private Notification buildErrorNotification(String sessionId, String error) {
        Intent openIntent = new Intent(this, MainActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        openIntent.putExtra("session_id", sessionId);
        int piFlags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                ? PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
                : PendingIntent.FLAG_UPDATE_CURRENT;
        PendingIntent openPending = PendingIntent.getActivity(this, 2, openIntent, piFlags);

        return new NotificationCompat.Builder(this, CHANNEL_ID_DONE)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("DroidClaw — agent error")
                .setContentText(error)
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText(error))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setOngoing(false)
                .setContentIntent(openPending)
                .setShowWhen(true)
                .build();
    }

    /**
     * Remove common markdown markers so the notification text is readable.
     * Package-private for unit testing.
     */
    static String stripMarkdown(String text) {
        if (text == null) return "";
        return text
                .replaceAll("(?m)^#{1,6}\\s+", "")     // headings
                .replaceAll("\\*\\*(.+?)\\*\\*", "$1") // bold
                .replaceAll("\\*(.+?)\\*", "$1")        // italic
                .replaceAll("`{1,3}[^`]*`{1,3}", "")    // inline code / code blocks
                .replaceAll("\\[([^]]+)]\\([^)]+\\)", "$1") // links
                .replaceAll("(?m)^[-*+]\\s+", "")       // list markers
                .replaceAll("(?m)^>\\s+", "")            // blockquotes
                .replaceAll("\\n{3,}", "\n\n")           // collapse excessive newlines
                .trim();
    }

    // ==================== WakeLock ====================

    private void acquireWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) return;
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm != null) {
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "DroidClaw:AgentExec");
            wakeLock.acquire();
        }
    }

    private void releaseWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
            wakeLock = null;
        }
    }
}