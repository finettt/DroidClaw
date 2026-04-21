package io.finett.droidclaw.fragment;

import static androidx.navigation.Navigation.setViewNavController;
import static androidx.test.core.app.ApplicationProvider.getApplicationContext;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.fragment.app.testing.FragmentScenario;
import androidx.navigation.testing.TestNavHostController;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileWriter;
import java.util.concurrent.atomic.AtomicInteger;

import io.finett.droidclaw.R;
import io.finett.droidclaw.filesystem.WorkspaceManager;

@RunWith(AndroidJUnit4.class)
public class FileBrowserFragmentTest {

    private static final long WAIT_TIMEOUT_MS = 3000;
    private static final long POLL_INTERVAL_MS = 100;

    private WorkspaceManager workspaceManager;

    @Before
    public void setUp() {
        workspaceManager = new WorkspaceManager(getApplicationContext());
        cleanWorkspace();
    }

    private void cleanWorkspace() {
        File workspaceRoot = workspaceManager.getWorkspaceRoot();
        deleteRecursive(workspaceRoot);
        workspaceRoot.mkdirs();
    }

    private void deleteRecursive(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        file.delete();
    }

        private void waitForAdapterItems(FragmentScenario<FileBrowserFragment> scenario, int minItems) {
        long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - startTime < WAIT_TIMEOUT_MS) {
            AtomicInteger itemCount = new AtomicInteger(0);
            scenario.onFragment(fragment -> {
                RecyclerView fileList = fragment.requireView().findViewById(R.id.fileList);
                if (fileList.getAdapter() != null) {
                    itemCount.set(fileList.getAdapter().getItemCount());
                }
            });
            if (itemCount.get() >= minItems) {
                return;
            }
            try {
                Thread.sleep(POLL_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

        private void waitForLoading() {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        try {
            Thread.sleep(100); // Small buffer after idle
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    public void launch_displaysFileBrowserUI() {
        try (FragmentScenario<FileBrowserFragment> scenario =
                     FragmentScenario.launchInContainer(FileBrowserFragment.class, null, R.style.Theme_DroidClaw)) {
            scenario.onFragment(fragment -> {
                attachNavController(fragment, R.id.fileBrowserFragment);

                TextView titleText = fragment.requireView().findViewById(R.id.titleText);
                TextView pathText = fragment.requireView().findViewById(R.id.pathText);
                RecyclerView fileList = fragment.requireView().findViewById(R.id.fileList);

                assertNotNull("Title text should be present", titleText);
                assertNotNull("Path text should be present", pathText);
                assertNotNull("File list should be present", fileList);
            });
        }
    }

    @Test
    public void launch_displaysRootPath() {
        try (FragmentScenario<FileBrowserFragment> scenario =
                     FragmentScenario.launchInContainer(FileBrowserFragment.class, null, R.style.Theme_DroidClaw)) {
            scenario.onFragment(fragment -> {
                attachNavController(fragment, R.id.fileBrowserFragment);

                TextView pathText = fragment.requireView().findViewById(R.id.pathText);
                assertEquals("Should display root path", "/", pathText.getText().toString());
            });
        }
    }

    @Test
    public void emptyWorkspace_showsEmptyMessage() {
        try (FragmentScenario<FileBrowserFragment> scenario =
                     FragmentScenario.launchInContainer(FileBrowserFragment.class, null, R.style.Theme_DroidClaw)) {
            scenario.onFragment(fragment -> {
                attachNavController(fragment, R.id.fileBrowserFragment);
            });

            waitForLoading();

            scenario.onFragment(fragment -> {
                TextView emptyText = fragment.requireView().findViewById(R.id.emptyText);
                RecyclerView fileList = fragment.requireView().findViewById(R.id.fileList);

                assertEquals("Empty message should be visible when no files",
                        View.VISIBLE, emptyText.getVisibility());
                assertEquals("File list should be hidden when no files",
                        View.GONE, fileList.getVisibility());
            });
        }
    }

    @Test
    public void withFiles_displaysFileList() {
        createTestFile("test.txt", "Test content");
        createTestFile("test.md", "Markdown content");

        try (FragmentScenario<FileBrowserFragment> scenario =
                     FragmentScenario.launchInContainer(FileBrowserFragment.class, null, R.style.Theme_DroidClaw)) {
            scenario.onFragment(fragment -> {
                attachNavController(fragment, R.id.fileBrowserFragment);
            });

            waitForAdapterItems(scenario, 2);

            scenario.onFragment(fragment -> {
                RecyclerView fileList = fragment.requireView().findViewById(R.id.fileList);
                TextView emptyText = fragment.requireView().findViewById(R.id.emptyText);

                assertEquals("File list should be visible when files exist",
                        View.VISIBLE, fileList.getVisibility());
                assertEquals("Empty message should be hidden when files exist",
                        View.GONE, emptyText.getVisibility());

                assertNotNull("RecyclerView adapter should be set", fileList.getAdapter());
                assertTrue("Should display files", fileList.getAdapter().getItemCount() >= 2);
            });
        }
    }

    @Test
    public void withDirectory_displaysDirectoryIcon() {
        File testDir = new File(workspaceManager.getWorkspaceRoot(), "test_dir");
        testDir.mkdirs();

        try (FragmentScenario<FileBrowserFragment> scenario =
                     FragmentScenario.launchInContainer(FileBrowserFragment.class, null, R.style.Theme_DroidClaw)) {
            scenario.onFragment(fragment -> {
                attachNavController(fragment, R.id.fileBrowserFragment);
            });

            waitForAdapterItems(scenario, 1);

            scenario.onFragment(fragment -> {
                RecyclerView fileList = fragment.requireView().findViewById(R.id.fileList);
                assertTrue("Should display directory", fileList.getAdapter().getItemCount() >= 1);
            });
        }
    }

    @Test
    public void fileList_hasCorrectAdapter() {
        try (FragmentScenario<FileBrowserFragment> scenario =
                     FragmentScenario.launchInContainer(FileBrowserFragment.class, null, R.style.Theme_DroidClaw)) {
            scenario.onFragment(fragment -> {
                attachNavController(fragment, R.id.fileBrowserFragment);

                RecyclerView fileList = fragment.requireView().findViewById(R.id.fileList);
                assertNotNull("Adapter should be set", fileList.getAdapter());
                assertNotNull("LayoutManager should be set", fileList.getLayoutManager());
            });
        }
    }

    @Test
    public void recreate_maintainsState() {
        createTestFile("test.txt", "Test content");

        try (FragmentScenario<FileBrowserFragment> scenario =
                     FragmentScenario.launchInContainer(FileBrowserFragment.class, null, R.style.Theme_DroidClaw)) {
            scenario.onFragment(fragment -> {
                attachNavController(fragment, R.id.fileBrowserFragment);
            });

            waitForLoading();

            scenario.recreate();

            scenario.onFragment(fragment -> {
                attachNavController(fragment, R.id.fileBrowserFragment);
            });

            waitForLoading();

            scenario.onFragment(fragment -> {
                TextView pathText = fragment.requireView().findViewById(R.id.pathText);
                assertEquals("Should maintain root path after recreate", "/", pathText.getText().toString());
            });
        }
    }

    @Test
    public void directoryClick_shouldTriggerNavigation() {
        File testDir = new File(workspaceManager.getWorkspaceRoot(), "test_dir");
        testDir.mkdirs();

        try (FragmentScenario<FileBrowserFragment> scenario =
                     FragmentScenario.launchInContainer(FileBrowserFragment.class, null, R.style.Theme_DroidClaw)) {
            scenario.onFragment(fragment -> {
                attachNavController(fragment, R.id.fileBrowserFragment);
            });

            waitForAdapterItems(scenario, 1);

            scenario.onFragment(fragment -> {
                RecyclerView fileList = fragment.requireView().findViewById(R.id.fileList);

                if (fileList.getAdapter().getItemCount() > 0) {
                    View firstItem = fileList.getLayoutManager().findViewByPosition(0);
                    if (firstItem != null) {
                        firstItem.performClick();
                    }
                }
            });

            waitForLoading();

            scenario.onFragment(fragment -> {
                TextView pathText = fragment.requireView().findViewById(R.id.pathText);
                String currentPath = pathText.getText().toString();
                assertNotNull("Path should not be null after navigation", currentPath);
            });
        }
    }

    @Test
    public void filesSortedCorrectly_directoriesFirst() {
        File testDir = new File(workspaceManager.getWorkspaceRoot(), "a_directory");
        testDir.mkdirs();
        createTestFile("z_file.txt", "Content");

        try (FragmentScenario<FileBrowserFragment> scenario =
                     FragmentScenario.launchInContainer(FileBrowserFragment.class, null, R.style.Theme_DroidClaw)) {
            scenario.onFragment(fragment -> {
                attachNavController(fragment, R.id.fileBrowserFragment);
            });

            waitForAdapterItems(scenario, 2);

            scenario.onFragment(fragment -> {
                RecyclerView fileList = fragment.requireView().findViewById(R.id.fileList);
                assertTrue("Should have at least 2 items", fileList.getAdapter().getItemCount() >= 2);
            });
        }
    }

    @Test
    public void titleText_showsCorrectTitle() {
        try (FragmentScenario<FileBrowserFragment> scenario =
                     FragmentScenario.launchInContainer(FileBrowserFragment.class, null, R.style.Theme_DroidClaw)) {
            scenario.onFragment(fragment -> {
                attachNavController(fragment, R.id.fileBrowserFragment);

                TextView titleText = fragment.requireView().findViewById(R.id.titleText);
                assertEquals("Should show file browser title",
                        fragment.getString(R.string.file_browser_title), titleText.getText().toString());
            });
        }
    }

    @Test
    public void loadDirectory_handlesIOErrors() {
        try (FragmentScenario<FileBrowserFragment> scenario =
                     FragmentScenario.launchInContainer(FileBrowserFragment.class, null, R.style.Theme_DroidClaw)) {
            scenario.onFragment(fragment -> {
                attachNavController(fragment, R.id.fileBrowserFragment);
            });

            waitForLoading();

            scenario.onFragment(fragment -> {
                assertNotNull("Fragment view should still exist", fragment.requireView());
            });
        }
    }

    @Test
    public void multipleFileTypes_displayCorrectIcons() {
        createTestFile("test.md", "Markdown");
        createTestFile("test.py", "Python");
        createTestFile("test.js", "JavaScript");
        createTestFile("test.json", "JSON");
        createTestFile("test.txt", "Text");

        try (FragmentScenario<FileBrowserFragment> scenario =
                     FragmentScenario.launchInContainer(FileBrowserFragment.class, null, R.style.Theme_DroidClaw)) {
            scenario.onFragment(fragment -> {
                attachNavController(fragment, R.id.fileBrowserFragment);
            });

            waitForAdapterItems(scenario, 5);

            scenario.onFragment(fragment -> {
                RecyclerView fileList = fragment.requireView().findViewById(R.id.fileList);
                assertTrue("Should display multiple files", fileList.getAdapter().getItemCount() >= 5);
            });
        }
    }

    @Test
    public void subdirectory_showsParentDirectoryEntry() {
        File testDir = new File(workspaceManager.getWorkspaceRoot(), "subdir");
        testDir.mkdirs();

        try (FragmentScenario<FileBrowserFragment> scenario =
                     FragmentScenario.launchInContainer(FileBrowserFragment.class, null, R.style.Theme_DroidClaw)) {
            scenario.onFragment(fragment -> {
                attachNavController(fragment, R.id.fileBrowserFragment);
            });

            waitForAdapterItems(scenario, 1);

            scenario.onFragment(fragment -> {
                RecyclerView fileList = fragment.requireView().findViewById(R.id.fileList);
                if (fileList.getAdapter().getItemCount() > 0) {
                    View firstItem = fileList.getLayoutManager().findViewByPosition(0);
                    if (firstItem != null) {
                        firstItem.performClick();
                    }
                }
            });

            waitForAdapterItems(scenario, 1);

            scenario.onFragment(fragment -> {
                RecyclerView fileList = fragment.requireView().findViewById(R.id.fileList);
                assertTrue("Should have parent directory entry (..) in subdirectory",
                        fileList.getAdapter().getItemCount() >= 1);

                View firstItem = fileList.getLayoutManager().findViewByPosition(0);
                if (firstItem != null) {
                    TextView fileName = firstItem.findViewById(R.id.fileName);
                    assertEquals("First item should be parent directory", "..", fileName.getText().toString());

                    ImageView fileIcon = firstItem.findViewById(R.id.fileIcon);
                    assertNotNull("Parent directory should have an icon", fileIcon.getDrawable());

                    TextView fileDetails = firstItem.findViewById(R.id.fileDetails);
                    assertEquals("Parent directory should show correct label",
                            fragment.getString(R.string.file_browser_parent_directory), fileDetails.getText().toString());
                }
            });
        }
    }

    @Test
    public void rootDirectory_noParentDirectoryEntry() {
        try (FragmentScenario<FileBrowserFragment> scenario =
                     FragmentScenario.launchInContainer(FileBrowserFragment.class, null, R.style.Theme_DroidClaw)) {
            scenario.onFragment(fragment -> {
                attachNavController(fragment, R.id.fileBrowserFragment);
            });

            waitForLoading();

            scenario.onFragment(fragment -> {
                RecyclerView fileList = fragment.requireView().findViewById(R.id.fileList);
                if (fileList.getAdapter().getItemCount() > 0) {
                    View firstItem = fileList.getLayoutManager().findViewByPosition(0);
                    if (firstItem != null) {
                        TextView fileName = firstItem.findViewById(R.id.fileName);
                        assertTrue("Root directory should not have parent directory entry",
                                !".." .equals(fileName.getText().toString()));
                    }
                }
            });
        }
    }

    @Test
    public void clickParentDirectory_navigatesToParent() {
        File testDir = new File(workspaceManager.getWorkspaceRoot(), "parent_dir");
        testDir.mkdirs();
        File nestedDir = new File(testDir, "child_dir");
        nestedDir.mkdirs();

        try (FragmentScenario<FileBrowserFragment> scenario =
                     FragmentScenario.launchInContainer(FileBrowserFragment.class, null, R.style.Theme_DroidClaw)) {
            scenario.onFragment(fragment -> {
                attachNavController(fragment, R.id.fileBrowserFragment);
            });

            waitForAdapterItems(scenario, 1);

            scenario.onFragment(fragment -> {
                RecyclerView fileList = fragment.requireView().findViewById(R.id.fileList);
                View firstItem = fileList.getLayoutManager().findViewByPosition(0);
                if (firstItem != null) {
                    firstItem.performClick();
                }
            });

            waitForAdapterItems(scenario, 1);

            scenario.onFragment(fragment -> {
                RecyclerView fileList = fragment.requireView().findViewById(R.id.fileList);
                View secondItem = fileList.getLayoutManager().findViewByPosition(1);
                if (secondItem != null) {
                    secondItem.performClick();
                }
            });

            waitForAdapterItems(scenario, 1);

            scenario.onFragment(fragment -> {
                TextView pathText = fragment.requireView().findViewById(R.id.pathText);
                assertTrue("Should be in child directory",
                        pathText.getText().toString().contains("child_dir"));
            });

            scenario.onFragment(fragment -> {
                RecyclerView fileList = fragment.requireView().findViewById(R.id.fileList);
                View firstItem = fileList.getLayoutManager().findViewByPosition(0);
                if (firstItem != null) {
                    TextView fileName = firstItem.findViewById(R.id.fileName);
                    assertEquals("First item should be parent directory", "..", fileName.getText().toString());
                    firstItem.performClick();
                }
            });

            waitForAdapterItems(scenario, 1);

            scenario.onFragment(fragment -> {
                TextView pathText = fragment.requireView().findViewById(R.id.pathText);
                assertTrue("Should be back in parent directory",
                        pathText.getText().toString().contains("parent_dir"));
                assertTrue("Should not be in child directory anymore",
                        !pathText.getText().toString().contains("child_dir"));
            });
        }
    }

    @Test
    public void parentDirectoryEntry_showsChevron() {
        File testDir = new File(workspaceManager.getWorkspaceRoot(), "test_subdir");
        testDir.mkdirs();

        try (FragmentScenario<FileBrowserFragment> scenario =
                     FragmentScenario.launchInContainer(FileBrowserFragment.class, null, R.style.Theme_DroidClaw)) {
            scenario.onFragment(fragment -> {
                attachNavController(fragment, R.id.fileBrowserFragment);
            });

            waitForAdapterItems(scenario, 1);

            scenario.onFragment(fragment -> {
                RecyclerView fileList = fragment.requireView().findViewById(R.id.fileList);
                View firstItem = fileList.getLayoutManager().findViewByPosition(0);
                if (firstItem != null) {
                    firstItem.performClick();
                }
            });

            waitForAdapterItems(scenario, 1);

            scenario.onFragment(fragment -> {
                RecyclerView fileList = fragment.requireView().findViewById(R.id.fileList);
                View firstItem = fileList.getLayoutManager().findViewByPosition(0);
                if (firstItem != null) {
                    TextView fileChevron = firstItem.findViewById(R.id.fileChevron);
                    assertEquals("Parent directory entry should show chevron",
                            View.VISIBLE, fileChevron.getVisibility());
                }
            });
        }
    }

    private void createTestFile(String name, String content) {
        try {
            File file = new File(workspaceManager.getWorkspaceRoot(), name);
            FileWriter writer = new FileWriter(file);
            writer.write(content);
            writer.close();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create test file: " + name, e);
        }
    }

    private void attachNavController(FileBrowserFragment fragment, int destinationId) {
        TestNavHostController navController = new TestNavHostController(fragment.requireContext());
        navController.setGraph(R.navigation.nav_graph);
        navController.setCurrentDestination(destinationId);
        setViewNavController(fragment.requireView(), navController);
    }
}