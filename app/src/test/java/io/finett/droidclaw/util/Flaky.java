package io.finett.droidclaw.util;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to mark a test method as flaky.
 * When used with {@link FlakyTestRule}, the annotated test will be automatically
 * retried up to a specified number of times before being marked as failed.
 *
 * <p>Usage:
 * <pre>{@code
 * @Rule
 * public FlakyTestRule flakyTestRule = new FlakyTestRule();
 *
 * @Flaky
 * @Test
 * public void myFlakyTest() {
 *     // Test code that may fail intermittently
 * }
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Flaky {

    /**
     * The maximum number of attempts to run the test.
     * Default is 3.
     *
     * @return the maximum number of attempts
     */
    int maxAttempts() default 3;
}
