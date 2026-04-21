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
import io.finett.droidclaw.util.AdapterTestHelper;
import io.finett.droidclaw.util.TestThemeHelper;

@RunWith(AndroidJUnit4.class)
public class ModelsAdapterTest {

    private ModelsAdapter adapter;

    @Before
    public void setUp() {
        adapter = new ModelsAdapter();
    }

    @Test
    public void initialState_hasZeroItems() {
        assertEquals(0, adapter.getItemCount());
    }

    @Test
    public void initialState_getCurrentList_returnsEmptyList() {
        assertTrue(adapter.getCurrentList().isEmpty());
    }

    @Test
    public void submitList_withModels_updatesCount() throws InterruptedException {
        List<Model> models = Arrays.asList(
            new Model("gpt-4", "GPT-4", "openai", false, Arrays.asList("text"), 8192, 4096),
            new Model("gpt-3.5", "GPT-3.5", "openai", false, Arrays.asList("text"), 4096, 2048)
        );

        AdapterTestHelper.submitListAndWait(adapter, models);

        assertEquals(2, adapter.getItemCount());
    }

    @Test
    public void submitList_withEmptyList_clearsAdapter() throws InterruptedException {
        List<Model> models = Arrays.asList(
            new Model("gpt-4", "GPT-4", "openai", false, Arrays.asList("text"), 8192, 4096)
        );
        AdapterTestHelper.submitListAndWait(adapter, models);

        AdapterTestHelper.submitListAndWait(adapter, new ArrayList<>());

        assertEquals(0, adapter.getItemCount());
    }

    @Test
    public void submitList_withNull_clearsAdapter() throws InterruptedException {
        List<Model> models = Arrays.asList(
            new Model("gpt-4", "GPT-4", "openai", false, Arrays.asList("text"), 8192, 4096)
        );
        AdapterTestHelper.submitListAndWait(adapter, models);

        AdapterTestHelper.submitListAndWait(adapter, null);

        assertEquals(0, adapter.getItemCount());
    }

    @Test
    public void submitList_replacesExistingList() throws InterruptedException {
        List<Model> models1 = Arrays.asList(
            new Model("gpt-4", "GPT-4", "openai", false, Arrays.asList("text"), 8192, 4096)
        );
        AdapterTestHelper.submitListAndWait(adapter, models1);

        List<Model> models2 = Arrays.asList(
            new Model("claude-3", "Claude 3", "anthropic", true, Arrays.asList("text", "image"), 100000, 4096),
            new Model("claude-2", "Claude 2", "anthropic", false, Arrays.asList("text"), 100000, 4096)
        );
        AdapterTestHelper.submitListAndWait(adapter, models2);

        assertEquals(2, adapter.getItemCount());
    }

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

        assertNotNull(viewHolder.itemView.findViewById(R.id.text_model_name));
        assertNotNull(viewHolder.itemView.findViewById(R.id.text_model_context));
    }

    @Test
    public void onBindViewHolder_bindsModelName() {
        List<Model> models = Arrays.asList(
            new Model("gpt-4", "GPT-4 Turbo", "openai", false, Arrays.asList("text"), 128000, 4096)
        );
        adapter.submitList(models);

        Context context = TestThemeHelper.getThemedContext();
        RecyclerView recyclerView = new RecyclerView(context);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        RecyclerView.ViewHolder viewHolder = adapter.onCreateViewHolder(recyclerView, 0);

        adapter.onBindViewHolder((ModelsAdapter.ModelViewHolder) viewHolder, 0);

        TextView nameText = viewHolder.itemView.findViewById(R.id.text_model_name);
        assertEquals("GPT-4 Turbo", nameText.getText().toString());
    }

    @Test
    public void onBindViewHolder_bindsContextWindow() {
        List<Model> models = Arrays.asList(
            new Model("claude-3", "Claude 3", "anthropic", true, Arrays.asList("text"), 200000, 4096)
        );
        adapter.submitList(models);

        Context context = TestThemeHelper.getThemedContext();
        RecyclerView recyclerView = new RecyclerView(context);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        RecyclerView.ViewHolder viewHolder = adapter.onCreateViewHolder(recyclerView, 0);

        adapter.onBindViewHolder((ModelsAdapter.ModelViewHolder) viewHolder, 0);

        TextView contextText = viewHolder.itemView.findViewById(R.id.text_model_context);
        String expectedText = ApplicationProvider.getApplicationContext()
                .getString(R.string.model_context_window_tokens, 200000);
        assertEquals(expectedText, contextText.getText().toString());
    }

    @Test
    public void onBindViewHolder_multipleModels_bindsCorrectly() {
        List<Model> models = Arrays.asList(
            new Model("gpt-4", "GPT-4", "openai", false, Arrays.asList("text"), 8192, 4096),
            new Model("claude-3", "Claude 3", "anthropic", true, Arrays.asList("text"), 100000, 4096)
        );
        adapter.submitList(models);

        Context context = TestThemeHelper.getThemedContext();
        RecyclerView recyclerView = new RecyclerView(context);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));

        RecyclerView.ViewHolder viewHolder1 = adapter.onCreateViewHolder(recyclerView, 0);
        adapter.onBindViewHolder((ModelsAdapter.ModelViewHolder) viewHolder1, 0);
        TextView nameText1 = viewHolder1.itemView.findViewById(R.id.text_model_name);
        assertEquals("GPT-4", nameText1.getText().toString());

        RecyclerView.ViewHolder viewHolder2 = adapter.onCreateViewHolder(recyclerView, 0);
        adapter.onBindViewHolder((ModelsAdapter.ModelViewHolder) viewHolder2, 1);
        TextView nameText2 = viewHolder2.itemView.findViewById(R.id.text_model_name);
        assertEquals("Claude 3", nameText2.getText().toString());
    }

    @Test
    public void clickListener_whenSet_receivesClickEvents() throws InterruptedException {
        AtomicBoolean clicked = new AtomicBoolean(false);
        AtomicReference<Model> clickedModel = new AtomicReference<>();

        adapter.setOnModelClickListener(model -> {
            clicked.set(true);
            clickedModel.set(model);
        });

        Model testModel = new Model("gpt-4", "GPT-4", "openai", false, Arrays.asList("text"), 8192, 4096);
        List<Model> models = Arrays.asList(testModel);
        AdapterTestHelper.submitListAndWait(adapter, models);

        Context context = TestThemeHelper.getThemedContext();
        RecyclerView recyclerView = new RecyclerView(context);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        recyclerView.setAdapter(adapter);
        
        recyclerView.measure(
            View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY)
        );
        recyclerView.layout(0, 0, 1000, 1000);
        
        RecyclerView.ViewHolder viewHolder = recyclerView.findViewHolderForAdapterPosition(0);
        assertNotNull("ViewHolder should be attached", viewHolder);

        viewHolder.itemView.performClick();

        assertTrue(clicked.get());
        assertEquals(testModel.getId(), clickedModel.get().getId());
    }

    @Test
    public void clickListener_whenNotSet_doesNotCrash() {
        Model testModel = new Model("gpt-4", "GPT-4", "openai", false, Arrays.asList("text"), 8192, 4096);
        List<Model> models = Arrays.asList(testModel);
        adapter.submitList(models);

        Context context = TestThemeHelper.getThemedContext();
        RecyclerView recyclerView = new RecyclerView(context);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        RecyclerView.ViewHolder viewHolder = adapter.onCreateViewHolder(recyclerView, 0);
        adapter.onBindViewHolder((ModelsAdapter.ModelViewHolder) viewHolder, 0);

        viewHolder.itemView.performClick();
    }

    @Test
    public void diffUtil_identifiesSameItems() throws InterruptedException {
        Model model1 = new Model("gpt-4", "GPT-4", "openai", false, Arrays.asList("text"), 8192, 4096);
        Model model2 = new Model("gpt-4", "GPT-4 Updated", "openai", false, Arrays.asList("text"), 16384, 8192);

        AdapterTestHelper.submitListAndWait(adapter, Arrays.asList(model1));
        assertEquals(1, adapter.getItemCount());

        AdapterTestHelper.submitListAndWait(adapter, Arrays.asList(model2));
        assertEquals(1, adapter.getItemCount());
    }

    @Test
    public void diffUtil_identifiesDifferentItems() throws InterruptedException {
        Model model1 = new Model("gpt-4", "GPT-4", "openai", false, Arrays.asList("text"), 8192, 4096);
        Model model2 = new Model("claude-3", "Claude 3", "anthropic", true, Arrays.asList("text"), 100000, 4096);

        AdapterTestHelper.submitListAndWait(adapter, Arrays.asList(model1));
        assertEquals(1, adapter.getItemCount());

        AdapterTestHelper.submitListAndWait(adapter, Arrays.asList(model1, model2));
        assertEquals(2, adapter.getItemCount());
    }

    @Test
    public void submitList_withVeryLongModelName_handlesCorrectly() {
        String longName = "This is a very long model name that should test whether " +
                "the adapter can handle long text content properly without any issues";
        Model model = new Model("long-name-model", longName, "openai", false, Arrays.asList("text"), 8192, 4096);
        adapter.submitList(Arrays.asList(model));

        Context context = TestThemeHelper.getThemedContext();
        RecyclerView recyclerView = new RecyclerView(context);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        RecyclerView.ViewHolder viewHolder = adapter.onCreateViewHolder(recyclerView, 0);
        adapter.onBindViewHolder((ModelsAdapter.ModelViewHolder) viewHolder, 0);

        TextView nameText = viewHolder.itemView.findViewById(R.id.text_model_name);
        assertEquals(longName, nameText.getText().toString());
    }

    @Test
    public void submitList_withZeroContextWindow_displaysCorrectly() {
        Model model = new Model("test-model", "Test Model", "test", false, Arrays.asList("text"), 0, 0);
        adapter.submitList(Arrays.asList(model));

        Context context = TestThemeHelper.getThemedContext();
        RecyclerView recyclerView = new RecyclerView(context);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        RecyclerView.ViewHolder viewHolder = adapter.onCreateViewHolder(recyclerView, 0);
        adapter.onBindViewHolder((ModelsAdapter.ModelViewHolder) viewHolder, 0);

        TextView contextText = viewHolder.itemView.findViewById(R.id.text_model_context);
        String expectedText = ApplicationProvider.getApplicationContext()
                .getString(R.string.model_context_window_tokens, 0);
        assertEquals(expectedText, contextText.getText().toString());
    }

    @Test
    public void submitList_withLargeContextWindow_displaysCorrectly() {
        Model model = new Model("large-ctx", "Large Context", "test", false,
                Arrays.asList("text"), Integer.MAX_VALUE, 4096);
        adapter.submitList(Arrays.asList(model));

        Context context = TestThemeHelper.getThemedContext();
        RecyclerView recyclerView = new RecyclerView(context);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        RecyclerView.ViewHolder viewHolder = adapter.onCreateViewHolder(recyclerView, 0);
        adapter.onBindViewHolder((ModelsAdapter.ModelViewHolder) viewHolder, 0);

        TextView contextText = viewHolder.itemView.findViewById(R.id.text_model_context);
        assertNotNull(contextText.getText());
    }

    @Test
    public void submitList_withSpecialCharactersInName_handlesCorrectly() {
        String specialName = "Model™ with Special©Characters® & <html>";
        Model model = new Model("special", specialName, "test", false, Arrays.asList("text"), 8192, 4096);
        adapter.submitList(Arrays.asList(model));

        Context context = TestThemeHelper.getThemedContext();
        RecyclerView recyclerView = new RecyclerView(context);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        RecyclerView.ViewHolder viewHolder = adapter.onCreateViewHolder(recyclerView, 0);
        adapter.onBindViewHolder((ModelsAdapter.ModelViewHolder) viewHolder, 0);

        TextView nameText = viewHolder.itemView.findViewById(R.id.text_model_name);
        assertEquals(specialName, nameText.getText().toString());
    }

    @Test
    public void submitList_multipleUpdates_maintainsCorrectState() throws InterruptedException {
        List<Model> models1 = Arrays.asList(
            new Model("gpt-4", "GPT-4", "openai", false, Arrays.asList("text"), 8192, 4096)
        );
        AdapterTestHelper.submitListAndWait(adapter, models1);
        assertEquals(1, adapter.getItemCount());

        List<Model> models2 = Arrays.asList(
            new Model("gpt-4", "GPT-4", "openai", false, Arrays.asList("text"), 8192, 4096),
            new Model("gpt-3.5", "GPT-3.5", "openai", false, Arrays.asList("text"), 4096, 2048)
        );
        AdapterTestHelper.submitListAndWait(adapter, models2);
        assertEquals(2, adapter.getItemCount());

        AdapterTestHelper.submitListAndWait(adapter, new ArrayList<>());
        assertEquals(0, adapter.getItemCount());

        AdapterTestHelper.submitListAndWait(adapter, models1);
        assertEquals(1, adapter.getItemCount());
    }
}