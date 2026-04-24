package io.finett.droidclaw.util;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A JUnit rule that automatically retries flaky tests.
 * Can be used with the {@link Flaky} annotation to mark tests that should be retried.
 */
public class FlakyTestRule implements TestRule {

    private static final Logger LOGGER = Logger.getLogger(FlakyTestRule.class.getName());

    private final int maxAttempts;

    /**
     * Creates a FlakyTestRule with the default maximum attempts (3).
     */
    public FlakyTestRule() {
        this(3);
    }

    /**
     * Creates a FlakyTestRule with a custom maximum attempts.
     *
     * @param maxAttempts the maximum number of times to run each flaky test
     */
    public FlakyTestRule(int maxAttempts) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be at least 1");
        }
        this.maxAttempts = maxAttempts;
    }

    @Override
    public Statement apply(Statement base, Description description) {
        Flaky flaky = description.getAnnotation(Flaky.class);

        if (flaky == null) {
            // Test is not marked as flaky, run normally
            return base;
        }

        // Test is marked as flaky, apply retry logic
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                Throwable lastException = null;
                int attempt = 0;

                while (attempt < maxAttempts) {
                    attempt++;
                    try {
                        base.evaluate();
                        // Test passed, we're done
                        if (attempt > 1) {
                            LOGGER.log(Level.WARNING,
                                    "Test {0} passed on attempt {1}/{2}",
                                    new Object[]{description.getDisplayName(), attempt, maxAttempts});
                        }
                        return;
                    } catch (Throwable t) {
                        lastException = t;
                        LOGGER.log(Level.WARNING,
                                "Test {0} failed on attempt {1}/{2}: {3}",
                                new Object[]{description.getDisplayName(), attempt, maxAttempts, t.getMessage()});
                    }
                }

                // All attempts failed
                LOGGER.warning("Test " + description.getDisplayName() +
                        " failed after " + maxAttempts + " attempts");
                throw lastException;
            }
        };
    }
}
