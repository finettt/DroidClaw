package io.finett.droidclaw.agent;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import android.os.Looper;
import com.google.gson.JsonArray;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.LooperMode;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import io.finett.droidclaw.api.LlmApiService;
import io.finett.droidclaw.api.RequestScope;
import io.finett.droidclaw.model.AgentConfig;
import io.finett.droidclaw.model.ChatMessage;
import io.finett.droidclaw.repository.MemoryRepository;
import io.finett.droidclaw.tool.ToolRegistry;
import io.finett.droidclaw.util.SettingsManager;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

/** Real HTTP regression: Stop must not cancel another request on the same client. */
@RunWith(RobolectricTestRunner.class)
@LooperMode(LooperMode.Mode.PAUSED)
public class AgentLoopSharedClientCancellationTest {
    @Test public void nonStreamingCancelLeavesScopedAndUnscopedRequestsAlive() throws Exception {
        assertSharedClientIsolation(false);
    }

    @Test public void streamingCancelLeavesScopedAndUnscopedRequestsAlive() throws Exception {
        assertSharedClientIsolation(true);
    }

    @Test public void cancelDuringSummaryLeavesUnrelatedRequestsAlive() throws Exception {
        assertSharedClientIsolation(false, true);
    }

    private void assertSharedClientIsolation(boolean streaming) throws Exception {
        assertSharedClientIsolation(streaming, false);
    }

    private void assertSharedClientIsolation(boolean streaming, boolean summarizing) throws Exception {
        CountDownLatch received = new CountDownLatch(3);
        CountDownLatch release = new CountDownLatch(1);
        MockWebServer server = new MockWebServer();
        server.setDispatcher(new Dispatcher() {
            @Override public MockResponse dispatch(RecordedRequest request) throws InterruptedException {
                received.countDown();
                if (!release.await(10, TimeUnit.SECONDS)) return new MockResponse().setResponseCode(503);
                return new MockResponse().setHeader("Content-Type", "application/json")
                        .setBody("{\"choices\":[{\"message\":{\"content\":\"survived\"}}]}");
            }
        });
        server.start();
        SettingsManager settings = mock(SettingsManager.class);
        AgentConfig config = new AgentConfig();
        config.setStreamResponses(streaming);
        when(settings.getAgentConfig()).thenReturn(config);
        when(settings.isConfigured()).thenReturn(true);
        when(settings.getApiUrl()).thenReturn(server.url("/v1/chat/completions").toString());
        when(settings.getModelName()).thenReturn("test-model");
        when(settings.getMaxAgentIterations()).thenReturn(20);
        LlmApiService api = new LlmApiService(settings);
        // Observe real Calls without replacing the service or its transport.
        Field clientField = LlmApiService.class.getDeclaredField("client");
        clientField.setAccessible(true);
        OkHttpClient client = (OkHttpClient) clientField.get(api);
        try {
            ToolRegistry tools = mock(ToolRegistry.class);
            when(tools.getToolDefinitions()).thenReturn(new JsonArray());
            AgentLoop.AgentCallback callback = mock(AgentLoop.AgentCallback.class);
            MemoryRepository memory = mock(MemoryRepository.class);
            ConversationSummarizer summarizer = summarizing ? new ConversationSummarizer(api, memory, 0) : null;
            AgentLoop loop = new AgentLoop(api, tools, settings, summarizer, null);
            List<ChatMessage> history = Collections.singletonList(new ChatMessage("hello", ChatMessage.TYPE_USER));
            loop.start(history, callback);

            CountDownLatch completed = new CountDownLatch(2);
            AtomicReference<String> failure = new AtomicReference<>();
            api.sendMessage(history, new LlmApiService.ChatCallback() {
                @Override public void onSuccess(String text) {
                    if (!"survived".equals(text)) failure.set(text);
                    completed.countDown();
                }
                @Override public void onError(String error) { failure.set(error); completed.countDown(); }
            });
            RequestScope unrelatedScope = new RequestScope();
            api.sendMessageWithTools(history, null, null, unrelatedScope, new LlmApiService.ChatCallbackWithTools() {
                @Override public void onSuccess(LlmApiService.LlmResponse response) {
                    if (!"survived".equals(response.getContent())) failure.set(response.getContent());
                    completed.countDown();
                }
                @Override public void onError(String error) { failure.set(error); completed.countDown(); }
            });
            assertTrue("all three HTTP requests must be in flight", received.await(5, TimeUnit.SECONDS));
            List<Call> inFlight = client.dispatcher().runningCalls();
            assertEquals(3, inFlight.size());

            loop.cancel();
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            assertTrue(loop.getRunScope().isCancelled());
            assertFalse(unrelatedScope.isCancelled());
            assertEquals("only the stopped run's real Call is cancelled", 1,
                    inFlight.stream().filter(Call::isCanceled).count());
            verify(callback, times(1)).onCancelled(anyList());
            release.countDown();
            awaitCallbacks(completed);
            assertNull("unrelated requests must complete normally", failure.get());
            verify(callback, never()).onError(anyString());
            verify(callback, never()).onComplete(anyString(), anyList());
            verifyNoInteractions(memory);
        } finally {
            release.countDown();
            client.dispatcher().cancelAll();
            client.dispatcher().executorService().shutdownNow();
            client.connectionPool().evictAll();
            server.shutdown();
        }
    }

    private static void awaitCallbacks(CountDownLatch done) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (done.getCount() > 0 && System.nanoTime() < deadline) {
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            done.await(10, TimeUnit.MILLISECONDS);
        }
        assertEquals("callbacks must finish within five seconds", 0, done.getCount());
    }
}
