package io.finett.droidclaw.agent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.gson.JsonObject;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.List;

import io.finett.droidclaw.api.LlmApiService;
import io.finett.droidclaw.model.ChatMessage;

/**
 * Unit tests for {@link GuidelinesReflector}: trigger conditions, structured
 * response handling (update / no-update / refusal / malformed) and the
 * injection-hygiene of the analyzer request.
 */
@RunWith(RobolectricTestRunner.class)
public class GuidelinesReflectorTest {

    private LlmApiService apiService;
    private GuidelinesManager guidelinesManager;
    private GuidelinesReflector reflector;

    @Before
    public void setUp() {
        apiService = mock(LlmApiService.class);
        guidelinesManager = mock(GuidelinesManager.class);
        when(guidelinesManager.loadGuidelines()).thenReturn("# GUIDELINES.md\n\n- old entry\n");
        reflector = new GuidelinesReflector(apiService, guidelinesManager);
    }

    private List<ChatMessage> sampleHistory() {
        List<ChatMessage> history = new ArrayList<>();
        history.add(new ChatMessage("Schedule a meeting for tomorrow", ChatMessage.TYPE_USER));
        history.add(new ChatMessage("Done — created the calendar event.", ChatMessage.TYPE_ASSISTANT));
        return history;
    }

    private LlmApiService.StructuredResponseCallback captureCallback() {
        ArgumentCaptor<LlmApiService.StructuredResponseCallback> captor =
                ArgumentCaptor.forClass(LlmApiService.StructuredResponseCallback.class);
        verify(apiService).sendMessageStructured(any(), isNull(), isNull(),
                any(JsonObject.class), captor.capture());
        return captor.getValue();
    }

    @Test
    public void analyze_nullOrEmptyHistory_noApiCall() {
        reflector.analyze(null);
        reflector.analyze(new ArrayList<>());
        verify(apiService, never()).sendMessageStructured(
                any(), any(), any(), any(), any());
    }

    @Test
    public void analyze_sendsTranscriptAndCurrentGuidelines() {
        reflector.analyze(sampleHistory());

        ArgumentCaptor<List<ChatMessage>> messagesCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(apiService).sendMessageStructured(messagesCaptor.capture(), isNull(),
                isNull(), any(JsonObject.class),
                any(LlmApiService.StructuredResponseCallback.class));

        List<ChatMessage> sent = messagesCaptor.getValue();
        assertEquals(2, sent.size());
        assertEquals(ChatMessage.TYPE_SYSTEM, sent.get(0).getType());
        assertTrue(sent.get(0).getContent().contains("untrusted data"));
        assertEquals(ChatMessage.TYPE_USER, sent.get(1).getType());
        assertTrue(sent.get(1).getContent().contains("- old entry"));
        assertTrue(sent.get(1).getContent().contains("[USER] Schedule a meeting"));
        assertTrue(sent.get(1).getContent().contains("[ASSISTANT] Done"));
    }

    @Test
    public void analyze_updateNeeded_savesNewGuidelines() {
        reflector.analyze(sampleHistory());
        LlmApiService.StructuredResponseCallback callback = captureCallback();

        String updated = "# GUIDELINES.md\n\n- new workflow entry\n";
        JsonObject json = new JsonObject();
        json.addProperty("update_needed", true);
        json.addProperty("reason", "user corrected the workflow");
        json.addProperty("updated_guidelines", updated);

        callback.onSuccess(new LlmApiService.StructuredResponse(
                json.toString(), null, null, null));

        verify(guidelinesManager).saveGuidelines(updated);
    }

    @Test
    public void analyze_updateNotNeeded_noSave() {
        reflector.analyze(sampleHistory());
        LlmApiService.StructuredResponseCallback callback = captureCallback();

        JsonObject json = new JsonObject();
        json.addProperty("update_needed", false);
        json.addProperty("reason", "nothing durable");
        json.addProperty("updated_guidelines", "");

        callback.onSuccess(new LlmApiService.StructuredResponse(
                json.toString(), null, null, null));

        verify(guidelinesManager, never()).saveGuidelines(any());
    }

    @Test
    public void analyze_refusal_noSave() {
        reflector.analyze(sampleHistory());
        LlmApiService.StructuredResponseCallback callback = captureCallback();

        callback.onSuccess(new LlmApiService.StructuredResponse(
                null, "I cannot analyze this conversation", null, null));

        verify(guidelinesManager, never()).saveGuidelines(any());
    }

    @Test
    public void analyze_malformedJson_noSave() {
        reflector.analyze(sampleHistory());
        LlmApiService.StructuredResponseCallback callback = captureCallback();

        callback.onSuccess(new LlmApiService.StructuredResponse(
                "this is not json at all", null, null, null));

        verify(guidelinesManager, never()).saveGuidelines(any());
    }

    @Test
    public void analyze_jsonWrappedInProse_extractedAndSaved() {
        reflector.analyze(sampleHistory());
        LlmApiService.StructuredResponseCallback callback = captureCallback();

        String updated = "# GUIDELINES.md\n\n- wrapped entry\n";
        String wrapped = "Here is my analysis:\n"
                + "{\"update_needed\": true, \"reason\": \"ok\", "
                + "\"updated_guidelines\": \"" + updated.replace("\n", "\\n") + "\"}\n"
                + "That is all.";

        callback.onSuccess(new LlmApiService.StructuredResponse(
                wrapped, null, null, null));

        verify(guidelinesManager).saveGuidelines(updated);
    }

    @Test
    public void analyze_identicalGuidelines_noRedundantWrite() {
        reflector.analyze(sampleHistory());
        LlmApiService.StructuredResponseCallback callback = captureCallback();

        JsonObject json = new JsonObject();
        json.addProperty("update_needed", true);
        json.addProperty("reason", "noop");
        json.addProperty("updated_guidelines", "# GUIDELINES.md\n\n- old entry");

        callback.onSuccess(new LlmApiService.StructuredResponse(
                json.toString(), null, null, null));

        verify(guidelinesManager, never()).saveGuidelines(any());
    }

    @Test
    public void analyze_apiError_swallowed() {
        reflector.analyze(sampleHistory());
        LlmApiService.StructuredResponseCallback callback = captureCallback();

        // Must not throw.
        callback.onError("API key not configured");

        verify(guidelinesManager, never()).saveGuidelines(any());
    }

    @Test
    public void getResponseSchema_hasRequiredFields() {
        JsonObject schema = GuidelinesReflector.getResponseSchema();
        assertEquals("object", schema.get("type").getAsString());

        JsonObject properties = schema.getAsJsonObject("properties");
        assertNotNull(properties.get("update_needed"));
        assertNotNull(properties.get("reason"));
        assertNotNull(properties.get("updated_guidelines"));
        assertEquals("boolean",
                properties.getAsJsonObject("update_needed").get("type").getAsString());
    }
}
