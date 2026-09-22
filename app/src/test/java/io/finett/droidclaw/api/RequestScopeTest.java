package io.finett.droidclaw.api;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import org.junit.Test;

import okhttp3.Call;

/** Unit tests for {@link RequestScope} (issue #144). */
public class RequestScopeTest {

    @Test
    public void cancelCancelsAllRegisteredCalls() {
        RequestScope scope = new RequestScope();
        Call a = mock(Call.class);
        Call b = mock(Call.class);
        scope.register(a);
        scope.register(b);

        scope.cancel();

        verify(a).cancel();
        verify(b).cancel();
        assertTrue(scope.isCancelled());
    }

    @Test
    public void registerAfterCancelCancelsImmediately() {
        RequestScope scope = new RequestScope();
        scope.cancel();

        Call late = mock(Call.class);
        scope.register(late);

        verify(late).cancel();
    }

    @Test
    public void unregisteredCallIsNotCancelled() {
        RequestScope scope = new RequestScope();
        Call done = mock(Call.class);
        scope.register(done);
        scope.unregister(done);

        scope.cancel();

        verify(done, never()).cancel();
    }

    @Test
    public void cancelIsIdempotent() {
        RequestScope scope = new RequestScope();
        Call a = mock(Call.class);
        scope.register(a);

        scope.cancel();
        scope.cancel();

        verify(a, times(1)).cancel();
        assertTrue(scope.isCancelled());
    }

    @Test
    public void freshScopeIsNotCancelled() {
        RequestScope scope = new RequestScope();
        assertFalse(scope.isCancelled());
        // register/unregister of null must be safe no-ops
        scope.register(null);
        scope.unregister(null);
    }
}
