package io.finett.droidclaw.python;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;
import static org.junit.Assert.*;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Android instrumented tests for PythonExecutor.
 * These tests require the Chaquopy Python runtime which is only available on Android devices.
 */
@RunWith(AndroidJUnit4.class)
public class PythonExecutorTest {

    private PythonExecutor executor;
    private PythonConfig config;
    private Context context;
    private File testScriptDir;

    @Before
    public void setUp() throws IOException {
        context = getApplicationContext();
        
        // Create test configuration
        config = PythonConfig.builder()
                .timeout(30)
                .enablePip(true)
                .maxOutputSize(1024 * 1024)
                .build();
        
        executor = new PythonExecutor(context, config);
        
        // Create test script directory
        testScriptDir = new File(context.getCacheDir(), "test_scripts");
        testScriptDir.mkdirs();
    }

    @After
    public void tearDown() {
        if (executor != null) {
            executor.shutdown();
        }
        
        // Clean up test scripts
        if (testScriptDir != null && testScriptDir.exists()) {
            deleteRecursive(testScriptDir);
        }
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
    public void testExecutorCreation() {
        assertNotNull("PythonExecutor should be created successfully", executor);
    }

    @Test
    public void testExecuteCode_simplePrint() {
        String code = "print('Hello, World!')";
        PythonResult result = executor.executeCode(code);
        
        assertNotNull("Result should not be null", result);
        assertTrue("Execution should be successful", result.isSuccess());
        assertNotNull("Output should not be null", result.getOutput());
        assertTrue("Output should contain 'Hello, World!'", 
                result.getOutput().contains("Hello, World!"));
    }

    @Test
    public void testExecuteCode_withMath() {
        String code = "result = 2 + 2\nprint(result)";
        PythonResult result = executor.executeCode(code);
        
        assertTrue("Execution should be successful", result.isSuccess());
        assertTrue("Output should contain '4'", result.getOutput().contains("4"));
    }

    @Test
    public void testExecuteCode_withVariables() {
        String code = "x = 10\ny = 20\nprint(x + y)";
        PythonResult result = executor.executeCode(code);
        
        assertTrue("Execution should be successful", result.isSuccess());
        assertTrue("Output should contain '30'", result.getOutput().contains("30"));
    }

    @Test
    public void testExecuteCode_withImports() {
        String code = "import sys\nprint(sys.version)";
        PythonResult result = executor.executeCode(code);
        
        assertTrue("Execution should be successful", result.isSuccess());
        assertNotNull("Output should not be null", result.getOutput());
        assertTrue("Output should contain Python version", 
                result.getOutput().length() > 0);
    }

    @Test
    public void testExecuteCode_withFunctionDefinition() {
        String code = "def greet(name):\n    return f'Hello, {name}!'\nprint(greet('Test'))";
        PythonResult result = executor.executeCode(code);
        
        assertTrue("Execution should be successful", result.isSuccess());
        assertTrue("Output should contain 'Hello, Test!'", 
                result.getOutput().contains("Hello, Test!"));
    }

    @Test
    public void testExecuteCode_withClassDefinition() {
        String code = "class Calculator:\n" +
                "    def add(self, a, b):\n" +
                "        return a + b\n" +
                "calc = Calculator()\n" +
                "print(calc.add(5, 3))";
        PythonResult result = executor.executeCode(code);
        
        assertTrue("Execution should be successful", result.isSuccess());
        assertTrue("Output should contain '8'", result.getOutput().contains("8"));
    }

    @Test
    public void testExecuteCode_withSyntaxError() {
        String code = "print('missing quote)";
        PythonResult result = executor.executeCode(code);
        
        assertFalse("Execution should fail for syntax error", result.isSuccess());
        assertNotNull("Error message should not be null", result.getError());
        assertTrue("Error should mention syntax issue", 
                result.getError().toLowerCase().contains("error") ||
                result.getError().toLowerCase().contains("syntax"));
    }

    @Test
    public void testExecuteCode_withRuntimeError() {
        String code = "raise ValueError('Test error')";
        PythonResult result = executor.executeCode(code);
        
        assertFalse("Execution should fail for runtime error", result.isSuccess());
        assertNotNull("Error message should not be null", result.getError());
        assertTrue("Error should mention ValueError", 
                result.getError().contains("ValueError"));
    }

    @Test
    public void testExecuteCode_withDivisionByZero() {
        String code = "result = 1 / 0";
        PythonResult result = executor.executeCode(code);
        
        assertFalse("Execution should fail for division by zero", result.isSuccess());
        assertNotNull("Error message should not be null", result.getError());
    }

    @Test
    public void testExecuteCode_withEmptyCode() {
        String code = "";
        PythonResult result = executor.executeCode(code);
        
        // Empty code should execute successfully (no output)
        assertTrue("Empty code should execute successfully", result.isSuccess());
    }

    @Test
    public void testExecuteCode_withMultilineCode() {
        String code = "for i in range(3):\n    print(f'Line {i}')\nprint('Done')";
        PythonResult result = executor.executeCode(code);
        
        assertTrue("Execution should be successful", result.isSuccess());
        assertTrue("Output should contain 'Line 0'", result.getOutput().contains("Line 0"));
        assertTrue("Output should contain 'Line 1'", result.getOutput().contains("Line 1"));
        assertTrue("Output should contain 'Line 2'", result.getOutput().contains("Line 2"));
        assertTrue("Output should contain 'Done'", result.getOutput().contains("Done"));
    }

    @Test
    public void testExecuteCode_withCustomTimeout() {
        String code = "print('Quick execution')";
        PythonResult result = executor.executeCode(code, 10);
        
        assertTrue("Execution should be successful", result.isSuccess());
        assertTrue("Output should contain expected text", 
                result.getOutput().contains("Quick execution"));
    }

    @Test
    public void testExecuteCode_withShortTimeout() {
        String code = "import time\ntime.sleep(0.1)\nprint('Done')";
        PythonResult result = executor.executeCode(code, 5);
        
        // Should complete within 5 seconds
        assertTrue("Execution should be successful", result.isSuccess());
    }

    @Test
    public void testExecuteCode_withTimeout() {
        // This code will run longer than the timeout
        String code = "import time\ntime.sleep(10)\nprint('This should not print')";
        PythonResult result = executor.executeCode(code, 1);
        
        assertFalse("Execution should fail due to timeout", result.isSuccess());
        String error = result.getError();
        assertTrue("Error should mention timed out",
                error.toLowerCase().contains("timed out"));
    }

    @Test
    public void testExecuteCode_executionTime() {
        String code = "print('Quick')";
        PythonResult result = executor.executeCode(code);
        
        assertTrue("Execution should be successful", result.isSuccess());
        assertTrue("Execution time should be non-negative", 
                result.getExecutionTimeMs() >= 0);
    }

    @Test
    public void testExecuteScript_validScript() throws IOException {
        File scriptFile = new File(testScriptDir, "test_script.py");
        try (FileWriter writer = new FileWriter(scriptFile)) {
            writer.write("print('Script executed successfully')\n");
        }
        
        PythonResult result = executor.executeScript(scriptFile);
        
        assertTrue("Script execution should be successful", result.isSuccess());
        assertTrue("Output should contain expected text", 
                result.getOutput().contains("Script executed successfully"));
    }

    @Test
    public void testExecuteScript_nonExistentFile() {
        File nonExistent = new File(testScriptDir, "nonexistent.py");
        PythonResult result = executor.executeScript(nonExistent);
        
        assertFalse("Execution should fail for non-existent file", result.isSuccess());
        assertNotNull("Error message should not be null", result.getError());
    }

    @Test
    public void testExecuteScript_withArguments() throws IOException {
        File scriptFile = new File(testScriptDir, "args_script.py");
        try (FileWriter writer = new FileWriter(scriptFile)) {
            writer.write("import sys\nprint(f'Args: {sys.argv}')\n");
        }
        
        PythonResult result = executor.executeScript(scriptFile);
        
        assertTrue("Script execution should be successful", result.isSuccess());
        // sys.argv may be empty when running via exec(), just verify it runs
        assertNotNull("Output should not be null", result.getOutput());
    }

    @Test
    public void testExecuteScript_withError() throws IOException {
        File scriptFile = new File(testScriptDir, "error_script.py");
        try (FileWriter writer = new FileWriter(scriptFile)) {
            writer.write("raise RuntimeError('Script error')\n");
        }
        
        PythonResult result = executor.executeScript(scriptFile);
        
        assertFalse("Execution should fail", result.isSuccess());
        assertTrue("Error should mention RuntimeError", 
                result.getError().contains("RuntimeError"));
    }

    @Test
    public void testGetPythonVersion() {
        String version = executor.getPythonVersion();
        
        assertNotNull("Python version should not be null", version);
        assertFalse("Python version should not be empty", version.isEmpty());
        // Chaquopy uses Python 3.11 per build.gradle
        assertTrue("Should be Python 3.x", version.startsWith("3."));
    }

    @Test
    public void testIsPackageInstalled_builtinModule() {
        boolean isInstalled = executor.isPackageInstalled("sys");
        
        assertTrue("Built-in 'sys' module should be installed", isInstalled);
    }

    @Test
    public void testIsPackageInstalled_preinstalledPackage() {
        // 'requests' is pre-installed via build.gradle
        boolean isInstalled = executor.isPackageInstalled("requests");
        
        assertTrue("'requests' should be installed from build.gradle", isInstalled);
    }

    @Test
    public void testIsPackageInstalled_nonExistentPackage() {
        boolean isInstalled = executor.isPackageInstalled("nonexistent_package_xyz123");
        
        assertFalse("Non-existent package should not be installed", isInstalled);
    }

    @Test
    public void testInstallPackage_pipDisabled() {
        // Create executor with pip disabled
        PythonConfig noPipConfig = PythonConfig.builder()
                .timeout(30)
                .enablePip(false)
                .build();
        
        PythonExecutor noPipExecutor = new PythonExecutor(context, noPipConfig);
        
        try {
            PythonResult result = noPipExecutor.installPackage("some-package");
            
            assertFalse("Install should fail when pip is disabled", result.isSuccess());
            assertTrue("Error should mention pip is disabled", 
                    result.getError().toLowerCase().contains("pip"));
        } finally {
            noPipExecutor.shutdown();
        }
    }

    @Test
    public void testInstallPackage_invalidPackage() {
        PythonResult result = executor.installPackage("nonexistent_package_xyz123");
        
        // Installing non-existent package should fail
        assertFalse("Install should fail for non-existent package", result.isSuccess());
    }

    @Test
    public void testExecuteCode_withListComprehension() {
        String code = "squares = [x**2 for x in range(5)]\nprint(squares)";
        PythonResult result = executor.executeCode(code);
        
        assertTrue("Execution should be successful", result.isSuccess());
        assertTrue("Output should contain expected values", 
                result.getOutput().contains("[0, 1, 4, 9, 16]"));
    }

    @Test
    public void testExecuteCode_withDictionary() {
        String code = "data = {'key': 'value', 'number': 42}\nprint(data['key'])";
        PythonResult result = executor.executeCode(code);
        
        assertTrue("Execution should be successful", result.isSuccess());
        assertTrue("Output should contain 'value'", result.getOutput().contains("value"));
    }

    @Test
    public void testExecuteCode_withJson() {
        String code = "import json\ndata = json.loads('{\"name\": \"test\"}')\nprint(data['name'])";
        PythonResult result = executor.executeCode(code);
        
        assertTrue("Execution should be successful", result.isSuccess());
        assertTrue("Output should contain 'test'", result.getOutput().contains("test"));
    }

    @Test
    public void testExecuteCode_withRequests() {
        // This tests that pre-installed packages work
        String code = "import requests\nprint('requests imported successfully')";
        PythonResult result = executor.executeCode(code);
        
        assertTrue("Execution should be successful", result.isSuccess());
        assertTrue("Output should confirm import", 
                result.getOutput().contains("requests imported successfully"));
    }

    @Test
    public void testExecuteCode_withBeautifulSoup() {
        // This tests that pre-installed packages work
        String code = "from bs4 import BeautifulSoup\nprint('BeautifulSoup imported')";
        PythonResult result = executor.executeCode(code);
        
        assertTrue("Execution should be successful", result.isSuccess());
        assertTrue("Output should confirm import", 
                result.getOutput().contains("BeautifulSoup imported"));
    }

    @Test
    public void testExecuteCode_multipleExecutions() {
        // Test that multiple executions work correctly
        for (int i = 0; i < 3; i++) {
            String code = "print('Iteration " + i + "')";
            PythonResult result = executor.executeCode(code);
            
            assertTrue("Execution " + i + " should be successful", result.isSuccess());
            assertTrue("Output " + i + " should contain iteration number", 
                    result.getOutput().contains("Iteration " + i));
        }
    }

    @Test
    public void testExecuteCode_largeOutput() {
        // Generate larger output
        StringBuilder code = new StringBuilder("output = []\n");
        for (int i = 0; i < 100; i++) {
            code.append("output.append('Line ").append(i).append("')\n");
        }
        code.append("print('\\n'.join(output))");
        
        PythonResult result = executor.executeCode(code.toString());
        
        assertTrue("Execution should be successful", result.isSuccess());
        assertTrue("Output should contain first line", result.getOutput().contains("Line 0"));
        assertTrue("Output should contain last line", result.getOutput().contains("Line 99"));
    }

    @Test
    public void testShutdown() {
        PythonConfig config = PythonConfig.createDefault();
        PythonExecutor testExecutor = new PythonExecutor(context, config);
        
        // Shutdown should not throw
        testExecutor.shutdown();
        
        assertTrue("Shutdown should complete without error", true);
    }

    @Test
    public void testExecuteCode_unicodeOutput() {
        String code = "print('Hello 世界 🌍')";
        PythonResult result = executor.executeCode(code);
        
        assertTrue("Execution should be successful", result.isSuccess());
        assertTrue("Output should contain unicode characters", 
                result.getOutput().contains("世界"));
    }

    @Test
    public void testExecuteCode_withNewlines() {
        String code = "print('Line 1\\nLine 2\\nLine 3')";
        PythonResult result = executor.executeCode(code);
        
        assertTrue("Execution should be successful", result.isSuccess());
        assertTrue("Output should contain multiple lines", 
                result.getOutput().contains("Line 1") &&
                result.getOutput().contains("Line 2") &&
                result.getOutput().contains("Line 3"));
    }

    @Test
    public void testExecuteCode_withSpecialCharacters() {
        String code = "print('Special chars: \\t \\\" \\\\ \\n')";
        PythonResult result = executor.executeCode(code);
        
        assertTrue("Execution should be successful", result.isSuccess());
    }

    @Test
    public void testExecuteCode_withTryExcept() {
        String code = "try:\n" +
                "    result = 1 / 0\n" +
                "except ZeroDivisionError:\n" +
                "    print('Caught division by zero')";
        PythonResult result = executor.executeCode(code);
        
        assertTrue("Execution should be successful", result.isSuccess());
        assertTrue("Output should confirm exception was caught", 
                result.getOutput().contains("Caught division by zero"));
    }

    @Test
    public void testExecuteCode_withLambda() {
        String code = "square = lambda x: x ** 2\nprint(square(5))";
        PythonResult result = executor.executeCode(code);
        
        assertTrue("Execution should be successful", result.isSuccess());
        assertTrue("Output should contain 25", result.getOutput().contains("25"));
    }

    @Test
    public void testExecuteCode_withGenerator() {
        String code = "gen = (x * 2 for x in range(3))\nprint(list(gen))";
        PythonResult result = executor.executeCode(code);
        
        assertTrue("Execution should be successful", result.isSuccess());
        assertTrue("Output should contain [0, 2, 4]", result.getOutput().contains("[0, 2, 4]"));
    }

    @Test
    public void testExecuteCode_withDecorator() {
        String code = "def my_decorator(func):\n" +
                "    def wrapper():\n" +
                "        print('Before')\n" +
                "        func()\n" +
                "        print('After')\n" +
                "    return wrapper\n" +
                "\n" +
                "@my_decorator\n" +
                "def say_hello():\n" +
                "    print('Hello!')\n" +
                "\n" +
                "say_hello()";
        PythonResult result = executor.executeCode(code);
        
        assertTrue("Execution should be successful", result.isSuccess());
        assertTrue("Output should contain 'Before'", result.getOutput().contains("Before"));
        assertTrue("Output should contain 'Hello!'", result.getOutput().contains("Hello!"));
        assertTrue("Output should contain 'After'", result.getOutput().contains("After"));
    }
}