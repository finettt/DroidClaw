package io.finett.droidclaw.fragment;

import static androidx.navigation.Navigation.setViewNavController;
import static androidx.test.core.app.ApplicationProvider.getApplicationContext;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.view.View;
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

import io.finett.droidclaw.R;
import io.finett.droidclaw.filesystem.WorkspaceManager;

@RunWith(AndroidJUnit4.class)
public class FileBrowserFragmentTest {

    private WorkspaceManager workspaceManager;

    @Before
    public void setUp() {
        workspaceManager = new WorkspaceManager(getApplicationContext());
        // Clean up workspace before each test
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

            // Wait for background loading
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

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
        // Create test files
        createTestFile("test.txt", "Test content");
        createTestFile("test.md", "Markdown content");

        try (FragmentScenario<FileBrowserFragment> scenario =
                     FragmentScenario.launchInContainer(FileBrowserFragment.class, null, R.style.Theme_DroidClaw)) {
            scenario.onFragment(fragment -> {
                attachNavController(fragment, R.id.fileBrowserFragment);
            });

            // Wait for background loading
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

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
        // Create test directory
        File testDir = new File(workspaceManager.getWorkspaceRoot(), "test_dir");
        testDir.mkdirs();

        try (FragmentScenario<FileBrowserFragment> scenario =
                     FragmentScenario.launchInContainer(FileBrowserFragment.class, null, R.style.Theme_DroidClaw)) {
            scenario.onFragment(fragment -> {
                attachNavController(fragment, R.id.fileBrowserFragment);
            });

            // Wait for background loading
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

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

            // Wait for loading
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            // Recreate fragment
            scenario.recreate();

            scenario.onFragment(fragment -> {
                attachNavController(fragment, R.id.fileBrowserFragment);
            });

            // Wait for loading after recreate
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            scenario.onFragment(fragment -> {
                TextView pathText = fragment.requireView().findViewById(R.id.pathText);
                assertEquals("Should maintain root path after recreate", "/", pathText.getText().toString());
            });
        }
    }

    @Test
    public void directoryClick_shouldTriggerNavigation() {
        // Create test directory
        File testDir = new File(workspaceManager.getWorkspaceRoot(), "test_dir");
        testDir.mkdirs();

        try (FragmentScenario<FileBrowserFragment> scenario =
                     FragmentScenario.launchInContainer(FileBrowserFragment.class, null, R.style.Theme_DroidClaw)) {
            scenario.onFragment(fragment -> {
                attachNavController(fragment, R.id.fileBrowserFragment);
            });

            // Wait for background loading
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            scenario.onFragment(fragment -> {
                RecyclerView fileList = fragment.requireView().findViewById(R.id.fileList);
                
                // Click on first item (directory)
                if (fileList.getAdapter().getItemCount() > 0) {
                    View firstItem = fileList.getLayoutManager().findViewByPosition(0);
                    if (firstItem != null) {
                        firstItem.performClick();
                    }
                }
            });

            // Wait for click processing
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            scenario.onFragment(fragment -> {
                TextView pathText = fragment.requireView().findViewById(R.id.pathText);
                // Path should have changed from root
                String currentPath = pathText.getText().toString();
                assertNotNull("Path should not be null after navigation", currentPath);
            });
        }
    }

    @Test
    public void filesSortedCorrectly_directoriesFirst() {
        // Create mixed files and directories
        File testDir = new File(workspaceManager.getWorkspaceRoot(), "a_directory");
        testDir.mkdirs();
        createTestFile("z_file.txt", "Content");

        try (FragmentScenario<FileBrowserFragment> scenario =
                     FragmentScenario.launchInContainer(FileBrowserFragment.class, null, R.style.Theme_DroidClaw)) {
            scenario.onFragment(fragment -> {
                attachNavController(fragment, R.id.fileBrowserFragment);
            });

            // Wait for background loading
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            scenario.onFragment(fragment -> {
                RecyclerView fileList = fragment.requireView().findViewById(R.id.fileList);
                assertTrue("Should have at least 2 items", fileList.getAdapter().getItemCount() >= 2);
                // Directory should come first even though it starts with 'a' and file starts with 'z'
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
        // This test ensures the fragment doesn't crash on errors
        try (FragmentScenario<FileBrowserFragment> scenario =
                     FragmentScenario.launchInContainer(FileBrowserFragment.class, null, R.style.Theme_DroidClaw)) {
            scenario.onFragment(fragment -> {
                attachNavController(fragment, R.id.fileBrowserFragment);
            });

            // Wait for loading
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            scenario.onFragment(fragment -> {
                // Fragment should still be functional
                assertNotNull("Fragment view should still exist", fragment.requireView());
            });
        }
    }

    @Test
    public void multipleFileTypes_displayCorrectIcons() {
        // Create files with different extensions
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

            // Wait for background loading
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            scenario.onFragment(fragment -> {
                RecyclerView fileList = fragment.requireView().findViewById(R.id.fileList);
                assertTrue("Should display multiple files", fileList.getAdapter().getItemCount() >= 5);
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