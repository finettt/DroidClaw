package io.finett.droidclaw.adapter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.view.View;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import io.finett.droidclaw.R;
import io.finett.droidclaw.model.Model;
import io.finett.droidclaw.model.Provider;
import io.finett.droidclaw.util.AdapterTestHelper;
import io.finett.droidclaw.util.TestThemeHelper;

@RunWith(AndroidJUnit4.class)
public class ProvidersAdapterTest {

    private ProvidersAdapter adapter;

    @Before
    public void setUp() {
        adapter = new ProvidersAdapter();
    }

    // ==================== Initial State Tests ====================

    @Test
    public void initialState_hasZeroItems() {
        assertEquals(0, adapter.getItemCount());
    }

    @Test
    public void initialState_getCurrentList_returnsEmptyList() {
        assertTrue(adapter.getCurrentList().isEmpty());
    }

    // ==================== submitList Tests ====================

    @Test
    public void submitList_withProviders_updatesCount() throws InterruptedException {
        List<Provider> providers = Arrays.asList(
            new Provider("openai", "OpenAI", "https://api.openai.com", "sk-test", "openai"),
            new Provider("anthropic", "Anthropic", "https://api.anthropic.com", "sk-ant", "anthropic")
        );

        AdapterTestHelper.submitListAndWait(adapter, providers);

        assertEquals(2, adapter.getItemCount());
    }

    @Test
    public void submitList_withEmptyList_clearsAdapter() throws InterruptedException {
        List<Provider> providers = Arrays.asList(
            new Provider("openai", "OpenAI", "https://api.openai.com", "sk-test", "openai")
        );
        AdapterTestHelper.submitListAndWait(adapter, providers);

        AdapterTestHelper.submitListAndWait(adapter, new ArrayList<>());

        assertEquals(0, adapter.getItemCount());
    }

    @Test
    public void submitList_withNull_clearsAdapter() throws InterruptedException {
        List<Provider> providers = Arrays.asList(
            new Provider("openai", "OpenAI", "https://api.openai.com", "sk-test", "openai")
        );
        AdapterTestHelper.submitListAndWait(adapter, providers);

        AdapterTestHelper.submitListAndWait(adapter, null);

        assertEquals(0, adapter.getItemCount());
    }

    @Test
    public void submitList_replacesExistingList() throws InterruptedException {
        List<Provider> providers1 = Arrays.asList(
            new Provider("openai", "OpenAI", "https://api.openai.com", "sk-test", "openai")
        );
        AdapterTestHelper.submitListAndWait(adapter, providers1);

        List<Provider> providers2 = Arrays.asList(
            new Provider("anthropic", "Anthropic", "https://api.anthropic.com", "sk-ant", "anthropic"),
            new Provider("google", "Google", "https://generativelanguage.googleapis.com", "key", "google")
        );
        AdapterTestHelper.submitListAndWait(adapter, providers2);

        assertEquals(2, adapter.getItemCount());
    }

    // ==================== ViewHolder Creation Tests ====================

    @Test
    public void onCreateViewHolder_createsValidViewHolder() {
        Context context = TestThemeHelper.getThemedContext();
        RecyclerView recyclerView = new RecyclerView(context);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));

        RecyclerView.ViewHolder viewHolder = adapter.onCreateViewHolder(recyclerView, 0);

        assertNotNull(viewHolder);
        assertNotNull(viewHolder.itemView);
    }

    @Test
    public void onCreateViewHolder_viewHolderHasCorrectViews() {
        Context context = TestThemeHelper.getThemedContext();
        RecyclerView recyclerView = new RecyclerView(context);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));

        RecyclerView.ViewHolder viewHolder = adapter.onCreateViewHolder(recyclerView, 0);

        assertNotNull(viewHolder.itemView.findViewById(R.id.text_provider_name));
        assertNotNull(viewHolder.itemView.findViewById(R.id.text_provider_url));
        assertNotNull(viewHolder.itemView.findViewById(R.id.text_model_count));
    }

    // ==================== ViewHolder Binding Tests ====================

    @Test
    public void onBindViewHolder_bindsProviderName() {
        Provider provider = new Provider("openai", "OpenAI Platform", "https://api.openai.com", "sk-test", "openai");
        List<Provider> providers = Arrays.asList(provider);
        adapter.submitList(providers);

        Context context = TestThemeHelper.getThemedContext();
        RecyclerView recyclerView = new RecyclerView(context);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        RecyclerView.ViewHolder viewHolder = adapter.onCreateViewHolder(recyclerView, 0);

        adapter.onBindViewHolder((ProvidersAdapter.ProviderViewHolder) viewHolder, 0);

        TextView nameText = viewHolder.itemView.findViewById(R.id.text_provider_name);
        assertEquals("OpenAI Platform", nameText.getText().toString());
    }

    @Test
    public void onBindViewHolder_bindsBaseUrl() {
        Provider provider = new Provider("anthropic", "Anthropic", "https://api.anthropic.com/v1", "sk-ant", "anthropic");
        List<Provider> providers = Arrays.asList(provider);
        adapter.submitList(providers);

        Context context = TestThemeHelper.getThemedContext();
        RecyclerView recyclerView = new RecyclerView(context);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        RecyclerView.ViewHolder viewHolder = adapter.onCreateViewHolder(recyclerView, 0);

        adapter.onBindViewHolder((ProvidersAdapter.ProviderViewHolder) viewHolder, 0);

        TextView urlText = viewHolder.itemView.findViewById(R.id.text_provider_url);
        assertEquals("https://api.anthropic.com/v1", urlText.getText().toString());
    }

    @Test
    public void onBindViewHolder_bindsModelCount() {
        Provider provider = new Provider("openai", "OpenAI", "https://api.openai.com", "sk-test", "openai");
        provider.addModel(new Model("gpt-4", "GPT-4", "openai", false, Arrays.asList("text"), 8192, 4096));
        provider.addModel(new Model("gpt-3.5", "GPT-3.5", "openai", false, Arrays.asList("text"), 4096, 2048));
        
        List<Provider> providers = Arrays.asList(provider);
        adapter.submitList(providers);

        Context context = TestThemeHelper.getThemedContext();
        RecyclerView recyclerView = new RecyclerView(context);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        RecyclerView.ViewHolder viewHolder = adapter.onCreateViewHolder(recyclerView, 0);

        adapter.onBindViewHolder((ProvidersAdapter.ProviderViewHolder) viewHolder, 0);

        TextView modelCountText = viewHolder.itemView.findViewById(R.id.text_model_count);
        String expectedText = ApplicationProvider.getApplicationContext()
                .getString(R.string.provider_models_count, 2);
        assertEquals(expectedText, modelCountText.getText().toString());
    }

    @Test
    public void onBindViewHolder_withZeroModels_displaysCorrectly() {
        Provider provider = new Provider("openai", "OpenAI", "https://api.openai.com", "sk-test", "openai");
        List<Provider> providers = Arrays.asList(provider);
        adapter.submitList(providers);

        Context context = TestThemeHelper.getThemedContext();
        RecyclerView recyclerView = new RecyclerView(context);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        RecyclerView.ViewHolder viewHolder = adapter.onCreateViewHolder(recyclerView, 0);

        adapter.onBindViewHolder((ProvidersAdapter.ProviderViewHolder) viewHolder, 0);

        TextView modelCountText = viewHolder.itemView.findViewById(R.id.text_model_count);
        String expectedText = ApplicationProvider.getApplicationContext()
                .getString(R.string.provider_models_count, 0);
        assertEquals(expectedText, modelCountText.getText().toString());
    }

    @Test
    public void onBindViewHolder_multipleProviders_bindsCorrectly() {
        Provider provider1 = new Provider("openai", "OpenAI", "https://api.openai.com", "sk-test", "openai");
        provider1.addModel(new Model("gpt-4", "GPT-4", "openai", false, Arrays.asList("text"), 8192, 4096));
        
        Provider provider2 = new Provider("anthropic", "Anthropic", "https://api.anthropic.com", "sk-ant", "anthropic");
        provider2.addModel(new Model("claude-3", "Claude 3", "anthropic", true, Arrays.asList("text"), 100000, 4096));
        provider2.addModel(new Model("claude-2", "Claude 2", "anthropic", false, Arrays.asList("text"), 100000, 4096));

        List<Provider> providers = Arrays.asList(provider1, provider2);
        adapter.submitList(providers);

        Context context = TestThemeHelper.getThemedContext();
        RecyclerView recyclerView = new RecyclerView(context);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));

        // Test first provider
        RecyclerView.ViewHolder viewHolder1 = adapter.onCreateViewHolder(recyclerView, 0);
        adapter.onBindViewHolder((ProvidersAdapter.ProviderViewHolder) viewHolder1, 0);
        TextView nameText1 = viewHolder1.itemView.findViewById(R.id.text_provider_name);
        assertEquals("OpenAI", nameText1.getText().toString());

        // Test second provider
        RecyclerView.ViewHolder viewHolder2 = adapter.onCreateViewHolder(recyclerView, 0);
        adapter.onBindViewHolder((ProvidersAdapter.ProviderViewHolder) viewHolder2, 1);
        TextView nameText2 = viewHolder2.itemView.findViewById(R.id.text_provider_name);
        assertEquals("Anthropic", nameText2.getText().toString());
    }

    // ==================== Click Listener Tests ====================

    @Test
    public void clickListener_whenSet_receivesClickEvents() throws InterruptedException {
        AtomicBoolean clicked = new AtomicBoolean(false);
        AtomicReference<Provider> clickedProvider = new AtomicReference<>();

        adapter.setOnProviderClickListener(provider -> {
            clicked.set(true);
            clickedProvider.set(provider);
        });

        Provider testProvider = new Provider("openai", "OpenAI", "https://api.openai.com", "sk-test", "openai");
        List<Provider> providers = Arrays.asList(testProvider);
        AdapterTestHelper.submitListAndWait(adapter, providers);

        Context context = TestThemeHelper.getThemedContext();
        RecyclerView recyclerView = new RecyclerView(context);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        recyclerView.setAdapter(adapter);
        
        // Force layout to attach view holders properly
        recyclerView.measure(
            View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY)
        );
        recyclerView.layout(0, 0, 1000, 1000);
        
        // Get the view holder that is now properly attached
        RecyclerView.ViewHolder viewHolder = recyclerView.findViewHolderForAdapterPosition(0);
        assertNotNull("ViewHolder should be attached", viewHolder);

        viewHolder.itemView.performClick();

        assertTrue(clicked.get());
        assertEquals(testProvider.getId(), clickedProvider.get().getId());
    }

    @Test
    public void clickListener_whenNotSet_doesNotCrash() {
        Provider testProvider = new Provider("openai", "OpenAI", "https://api.openai.com", "sk-test", "openai");
        List<Provider> providers = Arrays.asList(testProvider);
        adapter.submitList(providers);

        Context context = TestThemeHelper.getThemedContext();
        RecyclerView recyclerView = new RecyclerView(context);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        RecyclerView.ViewHolder viewHolder = adapter.onCreateViewHolder(recyclerView, 0);
        adapter.onBindViewHolder((ProvidersAdapter.ProviderViewHolder) viewHolder, 0);

        // Should not crash
        viewHolder.itemView.performClick();
    }

    // ==================== DiffUtil Tests ====================

    @Test
    public void diffUtil_identifiesSameItems() throws InterruptedException {
        Provider provider1 = new Provider("openai", "OpenAI", "https://api.openai.com", "sk-test", "openai");
        Provider provider2 = new Provider("openai", "OpenAI Updated", "https://api.openai.com/v2", "sk-new", "openai");

        AdapterTestHelper.submitListAndWait(adapter, Arrays.asList(provider1));
        assertEquals(1, adapter.getItemCount());

        AdapterTestHelper.submitListAndWait(adapter, Arrays.asList(provider2));
        assertEquals(1, adapter.getItemCount());
    }

    @Test
    public void diffUtil_identifiesDifferentItems() throws InterruptedException {
        Provider provider1 = new Provider("openai", "OpenAI", "https://api.openai.com", "sk-test", "openai");
        Provider provider2 = new Provider("anthropic", "Anthropic", "https://api.anthropic.com", "sk-ant", "anthropic");

        AdapterTestHelper.submitListAndWait(adapter, Arrays.asList(provider1));
        assertEquals(1, adapter.getItemCount());

        AdapterTestHelper.submitListAndWait(adapter, Arrays.asList(provider1, provider2));
        assertEquals(2, adapter.getItemCount());
    }

    @Test
    public void diffUtil_detectsModelCountChange() throws InterruptedException {
        Provider provider1 = new Provider("openai", "OpenAI", "https://api.openai.com", "sk-test", "openai");
        Provider provider2 = new Provider("openai", "OpenAI", "https://api.openai.com", "sk-test", "openai");
        provider2.addModel(new Model("gpt-4", "GPT-4", "openai", false, Arrays.asList("text"), 8192, 4096));

        AdapterTestHelper.submitListAndWait(adapter, Arrays.asList(provider1));
        
        // When model count changes, content should be different
        AdapterTestHelper.submitListAndWait(adapter, Arrays.asList(provider2));
        assertEquals(1, adapter.getItemCount());
    }

    // ==================== Edge Case Tests ====================

    @Test
    public void submitList_withVeryLongProviderName_handlesCorrectly() {
        String longName = "This is a very long provider name that should test whether " +
                "the adapter can handle long text content properly without any issues";
        Provider provider = new Provider("long-name", longName, "https://api.example.com", "key", "api");
        adapter.submitList(Arrays.asList(provider));

        Context context = TestThemeHelper.getThemedContext();
        RecyclerView recyclerView = new RecyclerView(context);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        RecyclerView.ViewHolder viewHolder = adapter.onCreateViewHolder(recyclerView, 0);
        adapter.onBindViewHolder((ProvidersAdapter.ProviderViewHolder) viewHolder, 0);

        TextView nameText = viewHolder.itemView.findViewById(R.id.text_provider_name);
        assertEquals(longName, nameText.getText().toString());
    }

    @Test
    public void submitList_withVeryLongUrl_handlesCorrectly() {
        String longUrl = "https://very-long-subdomain-name.example.com/api/v1/endpoint/with/many/path/segments";
        Provider provider = new Provider("test", "Test Provider", longUrl, "key", "api");
        adapter.submitList(Arrays.asList(provider));

        Context context = TestThemeHelper.getThemedContext();
        RecyclerView recyclerView = new RecyclerView(context);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        RecyclerView.ViewHolder viewHolder = adapter.onCreateViewHolder(recyclerView, 0);
        adapter.onBindViewHolder((ProvidersAdapter.ProviderViewHolder) viewHolder, 0);

        TextView urlText = viewHolder.itemView.findViewById(R.id.text_provider_url);
        assertEquals(longUrl, urlText.getText().toString());
    }

    @Test
    public void submitList_withSpecialCharacters_handlesCorrectly() {
        String specialName = "Provider™ with Special©Characters® & <html>";
        String specialUrl = "https://api.example.com?key=value&other=123";
        Provider provider = new Provider("special", specialName, specialUrl, "key", "api");
        adapter.submitList(Arrays.asList(provider));

        Context context = TestThemeHelper.getThemedContext();
        RecyclerView recyclerView = new RecyclerView(context);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        RecyclerView.ViewHolder viewHolder = adapter.onCreateViewHolder(recyclerView, 0);
        adapter.onBindViewHolder((ProvidersAdapter.ProviderViewHolder) viewHolder, 0);

        TextView nameText = viewHolder.itemView.findViewById(R.id.text_provider_name);
        assertEquals(specialName, nameText.getText().toString());
        
        TextView urlText = viewHolder.itemView.findViewById(R.id.text_provider_url);
        assertEquals(specialUrl, urlText.getText().toString());
    }

    @Test
    public void submitList_withManyModels_displaysCorrectCount() {
        Provider provider = new Provider("test", "Test Provider", "https://api.test.com", "key", "api");
        
        // Add 100 models
        for (int i = 0; i < 100; i++) {
            provider.addModel(new Model("model-" + i, "Model " + i, "api", false,
                    Arrays.asList("text"), 8192, 4096));
        }
        
        adapter.submitList(Arrays.asList(provider));

        Context context = TestThemeHelper.getThemedContext();
        RecyclerView recyclerView = new RecyclerView(context);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        RecyclerView.ViewHolder viewHolder = adapter.onCreateViewHolder(recyclerView, 0);
        adapter.onBindViewHolder((ProvidersAdapter.ProviderViewHolder) viewHolder, 0);

        TextView modelCountText = viewHolder.itemView.findViewById(R.id.text_model_count);
        String expectedText = ApplicationProvider.getApplicationContext()
                .getString(R.string.provider_models_count, 100);
        assertEquals(expectedText, modelCountText.getText().toString());
    }

    @Test
    public void submitList_multipleUpdates_maintainsCorrectState() throws InterruptedException {
        // First submission
        List<Provider> providers1 = Arrays.asList(
            new Provider("openai", "OpenAI", "https://api.openai.com", "sk-test", "openai")
        );
        AdapterTestHelper.submitListAndWait(adapter, providers1);
        assertEquals(1, adapter.getItemCount());

        // Second submission
        List<Provider> providers2 = Arrays.asList(
            new Provider("openai", "OpenAI", "https://api.openai.com", "sk-test", "openai"),
            new Provider("anthropic", "Anthropic", "https://api.anthropic.com", "sk-ant", "anthropic")
        );
        AdapterTestHelper.submitListAndWait(adapter, providers2);
        assertEquals(2, adapter.getItemCount());

        // Third submission - clear
        AdapterTestHelper.submitListAndWait(adapter, new ArrayList<>());
        assertEquals(0, adapter.getItemCount());

        // Fourth submission - repopulate
        AdapterTestHelper.submitListAndWait(adapter, providers1);
        assertEquals(1, adapter.getItemCount());
    }
}