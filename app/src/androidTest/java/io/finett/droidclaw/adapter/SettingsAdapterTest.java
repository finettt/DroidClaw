package io.finett.droidclaw.adapter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

import android.content.Context;
import android.view.View;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
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
import io.finett.droidclaw.util.TestThemeHelper;

@RunWith(AndroidJUnit4.class)
public class SettingsAdapterTest {

    private SettingsAdapter adapter;

    @Before
    public void setUp() {
        adapter = new SettingsAdapter();
    }

    // ==================== Initial State Tests ====================

    @Test
    public void initialState_hasZeroItems() {
        assertEquals(0, adapter.getItemCount());
    }

    @Test
    public void initialState_itemsList_isEmpty() {
        // Items are private, but we can verify through adapter behavior
        assertEquals(0, adapter.getItemCount());
    }

    // ==================== setItems Tests ====================

    @Test
    public void setItems_withItems_updatesCount() {
        List<SettingsAdapter.SettingsItem> items = Arrays.asList(
            new SettingsAdapter.SettingsItem("providers", R.drawable.ic_settings_provider, "Providers", "2 providers configured", true),
            new SettingsAdapter.SettingsItem("models", R.drawable.ic_settings_provider, "Models", "5 models available", true)
        );

        adapter.setItems(items);

        assertEquals(2, adapter.getItemCount());
    }

    @Test
    public void setItems_withEmptyList_clearsAdapter() {
        List<SettingsAdapter.SettingsItem> items = Arrays.asList(
            new SettingsAdapter.SettingsItem("providers", R.drawable.ic_settings_provider, "Providers", "2 providers configured", true)
        );
        adapter.setItems(items);

        adapter.setItems(new ArrayList<>());

        assertEquals(0, adapter.getItemCount());
    }

    @Test
    public void setItems_withNull_clearsAdapter() {
        List<SettingsAdapter.SettingsItem> items = Arrays.asList(
            new SettingsAdapter.SettingsItem("providers", R.drawable.ic_settings_provider, "Providers", "2 providers configured", true)
        );
        adapter.setItems(items);

        adapter.setItems(null);

        assertEquals(0, adapter.getItemCount());
    }

    @Test
    public void setItems_replacesExistingList() {
        List<SettingsAdapter.SettingsItem> items1 = Arrays.asList(
            new SettingsAdapter.SettingsItem("providers", R.drawable.ic_settings_provider, "Providers", "2 providers configured", true)
        );
        adapter.setItems(items1);

        List<SettingsAdapter.SettingsItem> items2 = Arrays.asList(
            new SettingsAdapter.SettingsItem("models", R.drawable.ic_settings_provider, "Models", "5 models available", true),
            new SettingsAdapter.SettingsItem("agent", R.drawable.ic_settings_agent, "Agent", "Default configuration", true)
        );
        adapter.setItems(items2);

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

        assertNotNull(viewHolder.itemView.findViewById(R.id.icon_image));
        assertNotNull(viewHolder.itemView.findViewById(R.id.text_title));
        assertNotNull(viewHolder.itemView.findViewById(R.id.text_subtitle));
        assertNotNull(viewHolder.itemView.findViewById(R.id.text_chevron));
    }

    // ==================== ViewHolder Binding Tests ====================

    @Test
    public void onBindViewHolder_bindsIcon() {
        SettingsAdapter.SettingsItem item = new SettingsAdapter.SettingsItem(
            "providers", R.drawable.ic_settings_provider, "Providers", "2 providers configured", true
        );
        List<SettingsAdapter.SettingsItem> items = Arrays.asList(item);
        adapter.setItems(items);

        Context context = TestThemeHelper.getThemedContext();
        RecyclerView recyclerView = new RecyclerView(context);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        RecyclerView.ViewHolder viewHolder = adapter.onCreateViewHolder(recyclerView, 0);

        adapter.onBindViewHolder((SettingsAdapter.SettingsViewHolder) viewHolder, 0);

        // Icon is now an ImageView, not TextView
        assertNotNull(viewHolder.itemView.findViewById(R.id.icon_image));
    }

    @Test
    public void onBindViewHolder_bindsTitle() {
        SettingsAdapter.SettingsItem item = new SettingsAdapter.SettingsItem(
            "models", R.drawable.ic_settings_provider, "Models Configuration", "Manage your AI models", true
        );
        List<SettingsAdapter.SettingsItem> items = Arrays.asList(item);
        adapter.setItems(items);

        Context context = TestThemeHelper.getThemedContext();
        RecyclerView recyclerView = new RecyclerView(context);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        RecyclerView.ViewHolder viewHolder = adapter.onCreateViewHolder(recyclerView, 0);

        adapter.onBindViewHolder((SettingsAdapter.SettingsViewHolder) viewHolder, 0);

        TextView titleText = viewHolder.itemView.findViewById(R.id.text_title);
        assertEquals("Models Configuration", titleText.getText().toString());
    }

    @Test
    public void onBindViewHolder_bindsSubtitle() {
        SettingsAdapter.SettingsItem item = new SettingsAdapter.SettingsItem(
            "agent", R.drawable.ic_settings_agent, "Agent Settings", "Default configuration with 20 max iterations", true
        );
        List<SettingsAdapter.SettingsItem> items = Arrays.asList(item);
        adapter.setItems(items);

        Context context = TestThemeHelper.getThemedContext();
        RecyclerView recyclerView = new RecyclerView(context);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        RecyclerView.ViewHolder viewHolder = adapter.onCreateViewHolder(recyclerView, 0);

        adapter.onBindViewHolder((SettingsAdapter.SettingsViewHolder) viewHolder, 0);

        TextView subtitleText = viewHolder.itemView.findViewById(R.id.text_subtitle);
        assertEquals("Default configuration with 20 max iterations", subtitleText.getText().toString());
    }

    @Test
    public void onBindViewHolder_bindsChevronVisibility_whenShowChevronIsTrue() {
        SettingsAdapter.SettingsItem item = new SettingsAdapter.SettingsItem(
            "providers", R.drawable.ic_settings_provider, "Providers", "2 providers configured", true
        );
        List<SettingsAdapter.SettingsItem> items = Arrays.asList(item);
        adapter.setItems(items);

        Context context = TestThemeHelper.getThemedContext();
        RecyclerView recyclerView = new RecyclerView(context);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        RecyclerView.ViewHolder viewHolder = adapter.onCreateViewHolder(recyclerView, 0);

        adapter.onBindViewHolder((SettingsAdapter.SettingsViewHolder) viewHolder, 0);

        TextView chevronText = viewHolder.itemView.findViewById(R.id.text_chevron);
        assertEquals(View.VISIBLE, chevronText.getVisibility());
    }

    @Test
    public void onBindViewHolder_bindsChevronVisibility_whenShowChevronIsFalse() {
        SettingsAdapter.SettingsItem item = new SettingsAdapter.SettingsItem(
            "done", R.drawable.ic_settings_provider, "Done", "All settings saved", false
        );
        List<SettingsAdapter.SettingsItem> items = Arrays.asList(item);
        adapter.setItems(items);

        Context context = TestThemeHelper.getThemedContext();
        RecyclerView recyclerView = new RecyclerView(context);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        RecyclerView.ViewHolder viewHolder = adapter.onCreateViewHolder(recyclerView, 0);

        adapter.onBindViewHolder((SettingsAdapter.SettingsViewHolder) viewHolder, 0);

        TextView chevronText = viewHolder.itemView.findViewById(R.id.text_chevron);
        assertEquals(View.GONE, chevronText.getVisibility());
    }

    @Test
    public void onBindViewHolder_multipleItems_bindsCorrectly() {
        SettingsAdapter.SettingsItem item1 = new SettingsAdapter.SettingsItem(
            "providers", R.drawable.ic_settings_provider, "Providers", "2 providers configured", true
        );
        SettingsAdapter.SettingsItem item2 = new SettingsAdapter.SettingsItem(
            "models", R.drawable.ic_settings_provider, "Models", "5 models available", true
        );

        List<SettingsAdapter.SettingsItem> items = Arrays.asList(item1, item2);
        adapter.setItems(items);

        Context context = TestThemeHelper.getThemedContext();
        RecyclerView recyclerView = new RecyclerView(context);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));

        // Test first item
        RecyclerView.ViewHolder viewHolder1 = adapter.onCreateViewHolder(recyclerView, 0);
        adapter.onBindViewHolder((SettingsAdapter.SettingsViewHolder) viewHolder1, 0);
        TextView titleText1 = viewHolder1.itemView.findViewById(R.id.text_title);
        assertEquals("Providers", titleText1.getText().toString());

        // Test second item
        RecyclerView.ViewHolder viewHolder2 = adapter.onCreateViewHolder(recyclerView, 0);
        adapter.onBindViewHolder((SettingsAdapter.SettingsViewHolder) viewHolder2, 1);
        TextView titleText2 = viewHolder2.itemView.findViewById(R.id.text_title);
        assertEquals("Models", titleText2.getText().toString());
    }

    // ==================== Click Listener Tests ====================

    @Test
    public void clickListener_whenSet_receivesClickEvents() {
        AtomicBoolean clicked = new AtomicBoolean(false);
        AtomicReference<SettingsAdapter.SettingsItem> clickedItem = new AtomicReference<>();

        adapter.setOnSettingsItemClickListener(item -> {
            clicked.set(true);
            clickedItem.set(item);
        });

        SettingsAdapter.SettingsItem testItem = new SettingsAdapter.SettingsItem(
            "providers", R.drawable.ic_settings_provider, "Providers", "2 providers configured", true
        );
        List<SettingsAdapter.SettingsItem> items = Arrays.asList(testItem);
        adapter.setItems(items);

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
        assertEquals(testItem.getId(), clickedItem.get().getId());
    }

    @Test
    public void clickListener_whenNotSet_doesNotCrash() {
        SettingsAdapter.SettingsItem testItem = new SettingsAdapter.SettingsItem(
            "providers", R.drawable.ic_settings_provider, "Providers", "2 providers configured", true
        );
        List<SettingsAdapter.SettingsItem> items = Arrays.asList(testItem);
        adapter.setItems(items);

        Context context = TestThemeHelper.getThemedContext();
        RecyclerView recyclerView = new RecyclerView(context);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        RecyclerView.ViewHolder viewHolder = adapter.onCreateViewHolder(recyclerView, 0);
        adapter.onBindViewHolder((SettingsAdapter.SettingsViewHolder) viewHolder, 0);

        // Should not crash
        viewHolder.itemView.performClick();
    }

    @Test
    public void clickListener_withNullListener_doesNotCrash() {
        SettingsAdapter.SettingsItem testItem = new SettingsAdapter.SettingsItem(
            "providers", R.drawable.ic_settings_provider, "Providers", "2 providers configured", true
        );
        List<SettingsAdapter.SettingsItem> items = Arrays.asList(testItem);
        adapter.setItems(items);

        // Explicitly set null listener
        adapter.setOnSettingsItemClickListener(null);

        Context context = TestThemeHelper.getThemedContext();
        RecyclerView recyclerView = new RecyclerView(context);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        RecyclerView.ViewHolder viewHolder = adapter.onCreateViewHolder(recyclerView, 0);
        adapter.onBindViewHolder((SettingsAdapter.SettingsViewHolder) viewHolder, 0);

        // Should not crash
        viewHolder.itemView.performClick();
    }

    // ==================== Edge Case Tests ====================

    @Test
    public void setItems_withVeryLongTitle_handlesCorrectly() {
        String longTitle = "This is a very long settings title that should test whether " +
                "the adapter can handle long text content properly without any issues";
        SettingsAdapter.SettingsItem item = new SettingsAdapter.SettingsItem(
            "long", R.drawable.ic_settings_provider, longTitle, "Subtitle", true
        );
        adapter.setItems(Arrays.asList(item));

        Context context = TestThemeHelper.getThemedContext();
        RecyclerView recyclerView = new RecyclerView(context);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        RecyclerView.ViewHolder viewHolder = adapter.onCreateViewHolder(recyclerView, 0);
        adapter.onBindViewHolder((SettingsAdapter.SettingsViewHolder) viewHolder, 0);

        TextView titleText = viewHolder.itemView.findViewById(R.id.text_title);
        assertEquals(longTitle, titleText.getText().toString());
    }

    @Test
    public void setItems_withVeryLongSubtitle_handlesCorrectly() {
        String longSubtitle = "This is a very long settings subtitle that should test whether " +
                "the adapter can handle long text content properly without any issues";
        SettingsAdapter.SettingsItem item = new SettingsAdapter.SettingsItem(
            "long", R.drawable.ic_settings_provider, "Title", longSubtitle, true
        );
        adapter.setItems(Arrays.asList(item));

        Context context = TestThemeHelper.getThemedContext();
        RecyclerView recyclerView = new RecyclerView(context);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        RecyclerView.ViewHolder viewHolder = adapter.onCreateViewHolder(recyclerView, 0);
        adapter.onBindViewHolder((SettingsAdapter.SettingsViewHolder) viewHolder, 0);

        TextView subtitleText = viewHolder.itemView.findViewById(R.id.text_subtitle);
        assertEquals(longSubtitle, subtitleText.getText().toString());
    }

    @Test
    public void setItems_withSpecialCharactersInTitle_handlesCorrectly() {
        String specialTitle = "Settings™ with Special©Characters® & <html>";
        SettingsAdapter.SettingsItem item = new SettingsAdapter.SettingsItem(
            "special", R.drawable.ic_settings_provider, specialTitle, "Subtitle", true
        );
        adapter.setItems(Arrays.asList(item));

        Context context = TestThemeHelper.getThemedContext();
        RecyclerView recyclerView = new RecyclerView(context);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        RecyclerView.ViewHolder viewHolder = adapter.onCreateViewHolder(recyclerView, 0);
        adapter.onBindViewHolder((SettingsAdapter.SettingsViewHolder) viewHolder, 0);

        TextView titleText = viewHolder.itemView.findViewById(R.id.text_title);
        assertEquals(specialTitle, titleText.getText().toString());
    }

    @Test
    public void setItems_withSpecialCharactersInSubtitle_handlesCorrectly() {
        String specialSubtitle = "Subtitle™ with Special©Characters® & <html>";
        SettingsAdapter.SettingsItem item = new SettingsAdapter.SettingsItem(
            "special", R.drawable.ic_settings_provider, "Title", specialSubtitle, true
        );
        adapter.setItems(Arrays.asList(item));

        Context context = TestThemeHelper.getThemedContext();
        RecyclerView recyclerView = new RecyclerView(context);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        RecyclerView.ViewHolder viewHolder = adapter.onCreateViewHolder(recyclerView, 0);
        adapter.onBindViewHolder((SettingsAdapter.SettingsViewHolder) viewHolder, 0);

        TextView subtitleText = viewHolder.itemView.findViewById(R.id.text_subtitle);
        assertEquals(specialSubtitle, subtitleText.getText().toString());
    }

    @Test
    public void setItems_withEmptyTitle_handlesCorrectly() {
        SettingsAdapter.SettingsItem item = new SettingsAdapter.SettingsItem(
            "empty", R.drawable.ic_settings_provider, "", "Subtitle", true
        );
        adapter.setItems(Arrays.asList(item));

        Context context = TestThemeHelper.getThemedContext();
        RecyclerView recyclerView = new RecyclerView(context);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        RecyclerView.ViewHolder viewHolder = adapter.onCreateViewHolder(recyclerView, 0);
        adapter.onBindViewHolder((SettingsAdapter.SettingsViewHolder) viewHolder, 0);

        TextView titleText = viewHolder.itemView.findViewById(R.id.text_title);
        assertEquals("", titleText.getText().toString());
    }

    @Test
    public void setItems_withEmptySubtitle_handlesCorrectly() {
        SettingsAdapter.SettingsItem item = new SettingsAdapter.SettingsItem(
            "empty", R.drawable.ic_settings_provider, "Title", "", true
        );
        adapter.setItems(Arrays.asList(item));

        Context context = TestThemeHelper.getThemedContext();
        RecyclerView recyclerView = new RecyclerView(context);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        RecyclerView.ViewHolder viewHolder = adapter.onCreateViewHolder(recyclerView, 0);
        adapter.onBindViewHolder((SettingsAdapter.SettingsViewHolder) viewHolder, 0);

        TextView subtitleText = viewHolder.itemView.findViewById(R.id.text_subtitle);
        assertEquals("", subtitleText.getText().toString());
    }

    @Test
    public void setItems_withManyItems_handlesCorrectly() {
        List<SettingsAdapter.SettingsItem> items = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            items.add(new SettingsAdapter.SettingsItem(
                "item-" + i,
                R.drawable.ic_settings_provider,
                "Item " + i,
                "Subtitle for item " + i,
                true
            ));
        }
        adapter.setItems(items);

        assertEquals(50, adapter.getItemCount());
    }

    @Test
    public void setItems_multipleUpdates_maintainsCorrectState() {
        // First update
        List<SettingsAdapter.SettingsItem> items1 = Arrays.asList(
            new SettingsAdapter.SettingsItem("providers", R.drawable.ic_settings_provider, "Providers", "2 providers", true)
        );
        adapter.setItems(items1);
        assertEquals(1, adapter.getItemCount());

        // Second update
        List<SettingsAdapter.SettingsItem> items2 = Arrays.asList(
            new SettingsAdapter.SettingsItem("providers", R.drawable.ic_settings_provider, "Providers", "2 providers", true),
            new SettingsAdapter.SettingsItem("models", R.drawable.ic_settings_provider, "Models", "5 models", true)
        );
        adapter.setItems(items2);
        assertEquals(2, adapter.getItemCount());

        // Third update - clear
        adapter.setItems(new ArrayList<>());
        assertEquals(0, adapter.getItemCount());

        // Fourth update - repopulate
        adapter.setItems(items1);
        assertEquals(1, adapter.getItemCount());
    }

    @Test
    public void setItems_withDifferentChevronStates_handlesCorrectly() {
        SettingsAdapter.SettingsItem item1 = new SettingsAdapter.SettingsItem(
            "expandable", R.drawable.ic_settings_provider, "Expandable", "Has chevron", true
        );
        SettingsAdapter.SettingsItem item2 = new SettingsAdapter.SettingsItem(
            "non-expandable", R.drawable.ic_settings_provider, "Non-Expandable", "No chevron", false
        );

        List<SettingsAdapter.SettingsItem> items = Arrays.asList(item1, item2);
        adapter.setItems(items);

        Context context = TestThemeHelper.getThemedContext();
        RecyclerView recyclerView = new RecyclerView(context);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));

        // Test first item (with chevron)
        RecyclerView.ViewHolder viewHolder1 = adapter.onCreateViewHolder(recyclerView, 0);
        adapter.onBindViewHolder((SettingsAdapter.SettingsViewHolder) viewHolder1, 0);
        TextView chevronText1 = viewHolder1.itemView.findViewById(R.id.text_chevron);
        assertEquals(View.VISIBLE, chevronText1.getVisibility());

        // Test second item (without chevron)
        RecyclerView.ViewHolder viewHolder2 = adapter.onCreateViewHolder(recyclerView, 0);
        adapter.onBindViewHolder((SettingsAdapter.SettingsViewHolder) viewHolder2, 1);
        TextView chevronText2 = viewHolder2.itemView.findViewById(R.id.text_chevron);
        assertEquals(View.GONE, chevronText2.getVisibility());
    }
}
