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

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.SynchronousQueue;

import io.finett.droidclaw.MainActivity;
import io.finett.droidclaw.R;
import io.finett.droidclaw.agent.AgentLoop;
import io.finett.droidclaw.agent.ConversationSummarizer;
import io.finett.droidclaw.agent.IdentityManager;
import io.finett.droidclaw.agent.MemoryContextBuilder;
import io.finett.droidclaw.api.LlmApiService;
import io.finett.droidclaw.filesystem.WorkspaceManager;
import io.finett.droidclaw.model.ChatMessage;
import io.finett.droidclaw.repository.ChatRepository;
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

    // ==================== Session state ====================

    /** Per-session state kept for the lifetime of the loop. */
    public static class AgentSession {
        public enum State { RUNNING, PAUSED_APPROVAL, COMPLETED, ERROR }

        public final String sessionId;

        volatile State state = State.RUNNING;

        /** Queued approval request waiting for the user to return. */
        volatile PendingApproval pendingApproval;

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

    // ==================== Worker thread ====================

    private void runAgentLoop(AgentSession session, List<ChatMessage> conversationHistory,
                               int modelContextWindow) {
        try {
            SettingsManager settingsManager = new SettingsManager(getApplicationContext());
            LlmApiService apiService = new LlmApiService(settingsManager);
            ToolRegistry toolRegistry = new ToolRegistry(getApplicationContext(), settingsManager);

            WorkspaceManager workspaceManager = new WorkspaceManager(getApplicationContext());
            MemoryRepository memoryRepository = new MemoryRepository(workspaceManager);

            ConversationSummarizer summarizer = new ConversationSummarizer(
                    apiService, memoryRepository, modelContextWindow);
            MemoryContextBuilder memoryContext = new MemoryContextBuilder(memoryRepository);

            IdentityManager identityManager = new IdentityManager(
                    getApplicationContext(), workspaceManager);

            AgentLoop agentLoop = new AgentLoop(
                    apiService, toolRegistry, settingsManager, summarizer, memoryContext);

            try {
                List<ChatMessage> identityMessages = identityManager.getIdentityMessages();
                agentLoop.setIdentityContext(identityMessages);
            } catch (Exception e) {
                Log.w(TAG, "Could not load identity context", e);
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

                    mainHandler.post(() -> {
                        UICallback cb = uiCallbacks.remove(session.sessionId);
                        if (cb != null) {
                            cb.onComplete(finalResponse, history);
                        }
                        // Session delivered — remove it.
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