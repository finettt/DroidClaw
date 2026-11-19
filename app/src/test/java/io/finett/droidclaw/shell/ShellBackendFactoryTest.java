package io.finett.droidclaw.shell;

import android.content.Context;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import io.finett.droidclaw.model.AgentConfig;

import java.io.File;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link ShellBackendFactory} — backend selection based on AgentConfig.
 * The distro branch needs a Context, so the runner is Robolectric.
 */
@RunWith(RobolectricTestRunner.class)
public class ShellBackendFactoryTest {

    private Context context;
    private File distroRoot;
    private File workspaceRoot;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        distroRoot = new File(context.getFilesDir(), "distro/ubuntu");
        workspaceRoot = new File(context.getFilesDir(), "workspace");
    }


    @Test
    public void create_sshBackend_returnsSshShellBackend() {
        AgentConfig config = new AgentConfig();
        config.setShellBackend("ssh");
        config.setSshHost("192.168.1.100");
        config.setSshPort(2222);
        config.setSshUser("testuser");
        config.setSshAuthType("password");
        config.setSshPassword("testpass");
        config.setSshVerifyHostKey(false);

        ShellConfig shellConfig = ShellConfig.createFull();

        ShellBackend backend = ShellBackendFactory.create(config, shellConfig, context, distroRoot, workspaceRoot);

        assertNotNull(backend);
        assertTrue(backend instanceof SshShellBackend);
    }

    @Test
    public void create_sshBackendWithKeyAuth() {
        AgentConfig config = new AgentConfig();
        config.setShellBackend("ssh");
        config.setSshHost("server.example.com");
        config.setSshPort(22);
        config.setSshUser("admin");
        config.setSshAuthType("key");
        config.setSshPrivateKeyPath("/home/user/.ssh/id_rsa");
        config.setSshVerifyHostKey(true);

        ShellConfig shellConfig = ShellConfig.createFull();

        ShellBackend backend = ShellBackendFactory.create(config, shellConfig, context, distroRoot, workspaceRoot);

        assertNotNull(backend);
        assertTrue(backend instanceof SshShellBackend);
    }

    @Test
    public void create_defaultBackend_isDistro() {
        AgentConfig config = new AgentConfig(); // null backend -> distro default
        ShellConfig shellConfig = ShellConfig.createFull();

        ShellBackend backend = ShellBackendFactory.create(config, shellConfig, context, distroRoot, workspaceRoot);

        assertNotNull(backend);
        assertTrue(backend instanceof DistroShellBackend);
    }

    @Test
    public void create_nullBackend_isDistro() {
        AgentConfig config = new AgentConfig();
        config.setShellBackend(null);
        ShellConfig shellConfig = ShellConfig.createFull();

        ShellBackend backend = ShellBackendFactory.create(config, shellConfig, context, distroRoot, workspaceRoot);

        assertNotNull(backend);
        assertTrue(backend instanceof DistroShellBackend);
    }

    @Test
    public void create_emptyBackend_isDistro() {
        AgentConfig config = new AgentConfig();
        config.setShellBackend("");
        ShellConfig shellConfig = ShellConfig.createFull();

        ShellBackend backend = ShellBackendFactory.create(config, shellConfig, context, distroRoot, workspaceRoot);

        assertNotNull(backend);
        assertTrue(backend instanceof DistroShellBackend);
    }

    @Test
    public void create_sshBackend_caseInsensitive() {
        AgentConfig config = new AgentConfig();
        config.setShellBackend("SSH"); // uppercase
        config.setSshHost("localhost");
        config.setSshPort(22);
        config.setSshUser("user");
        config.setSshAuthType("password");
        config.setSshPassword("pass");

        ShellConfig shellConfig = ShellConfig.createFull();

        ShellBackend backend = ShellBackendFactory.create(config, shellConfig, context, distroRoot, workspaceRoot);

        assertNotNull(backend);
        assertTrue(backend instanceof SshShellBackend);
    }

    @Test
    public void isSshBackend_trueWhenSshConfigured() {
        AgentConfig config = new AgentConfig();
        config.setShellBackend("ssh");
        config.setSshHost("localhost");
        config.setSshUser("user");
        config.setSshAuthType("password");
        config.setSshPassword("pass");

        assertTrue(ShellBackendFactory.isSshBackend(config));
    }

    @Test
    public void isSshBackend_falseWhenDefault() {
        AgentConfig config = new AgentConfig();
        assertFalse(ShellBackendFactory.isSshBackend(config));
    }

    @Test(expected = IllegalArgumentException.class)
    public void create_sshBackend_withoutHost_throws() {
        AgentConfig config = new AgentConfig();
        config.setShellBackend("ssh");
        config.setSshUser("user");
        config.setSshAuthType("password");
        config.setSshPassword("pass");
        // sshHost is null/empty by default

        ShellConfig shellConfig = ShellConfig.createFull();

        // Should throw because SshConfig.Builder validates host
        ShellBackendFactory.create(config, shellConfig, context, distroRoot, workspaceRoot);
    }

    @Test
    public void create_distroBackend_honoursSeccompKnob() {
        AgentConfig config = new AgentConfig();
        config.setDistroNoSeccomp(false);
        ShellConfig shellConfig = ShellConfig.createFull();

        DistroShellBackend backend =
                (DistroShellBackend) ShellBackendFactory.create(config, shellConfig, context, distroRoot, workspaceRoot);

        assertNotNull(backend);
        // No exception means the backend was built with the knob value; behaviour is covered
        // by DistroShellBackendTest command-building tests.
    }
}
