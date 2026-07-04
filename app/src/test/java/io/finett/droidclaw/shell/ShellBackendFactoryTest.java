package io.finett.droidclaw.shell;

import org.junit.Test;

import io.finett.droidclaw.model.AgentConfig;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link ShellBackendFactory} — backend selection based on AgentConfig.
 */
public class ShellBackendFactoryTest {

    @Test
    public void create_localBackend_returnsLocalShellBackend() {
        AgentConfig config = new AgentConfig();
        config.setShellBackend("local");
        ShellConfig shellConfig = ShellConfig.createFull();

        ShellBackend backend = ShellBackendFactory.create(config, shellConfig);

        assertNotNull(backend);
        assertTrue(backend instanceof LocalShellBackend);
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

        ShellConfig shellConfig = ShellConfig.createAllowlistDefault();

        ShellBackend backend = ShellBackendFactory.create(config, shellConfig);

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

        ShellBackend backend = ShellBackendFactory.create(config, shellConfig);

        assertNotNull(backend);
        assertTrue(backend instanceof SshShellBackend);
    }

    @Test
    public void create_defaultBackend_isLocal() {
        AgentConfig config = new AgentConfig();
        // Default backend is "local"
        ShellConfig shellConfig = ShellConfig.createFull();

        ShellBackend backend = ShellBackendFactory.create(config, shellConfig);

        assertNotNull(backend);
        assertTrue(backend instanceof LocalShellBackend);
    }

    @Test
    public void create_nullBackend_isLocal() {
        AgentConfig config = new AgentConfig();
        config.setShellBackend(null);
        ShellConfig shellConfig = ShellConfig.createFull();

        ShellBackend backend = ShellBackendFactory.create(config, shellConfig);

        assertNotNull(backend);
        assertTrue(backend instanceof LocalShellBackend);
    }

    @Test
    public void create_emptyBackend_isLocal() {
        AgentConfig config = new AgentConfig();
        config.setShellBackend("");
        ShellConfig shellConfig = ShellConfig.createFull();

        ShellBackend backend = ShellBackendFactory.create(config, shellConfig);

        assertNotNull(backend);
        assertTrue(backend instanceof LocalShellBackend);
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

        ShellBackend backend = ShellBackendFactory.create(config, shellConfig);

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
    public void isSshBackend_falseWhenLocalConfigured() {
        AgentConfig config = new AgentConfig();
        config.setShellBackend("local");

        assertFalse(ShellBackendFactory.isSshBackend(config));
    }

    @Test
    public void isSshBackend_falseWhenDefault() {
        AgentConfig config = new AgentConfig();
        // Default is "local"

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
        ShellBackendFactory.create(config, shellConfig);
    }

    @Test
    public void create_sshBackend_withAllConfig() {
        AgentConfig config = new AgentConfig();
        config.setShellBackend("ssh");
        config.setSshHost("myserver.com");
        config.setSshPort(2222);
        config.setSshUser("deploy");
        config.setSshAuthType("key");
        config.setSshPrivateKeyPath("/home/deploy/.ssh/deploy_key");
        config.setSshVerifyHostKey(true);

        ShellConfig shellConfig = ShellConfig.createFull();

        ShellBackend backend = ShellBackendFactory.create(config, shellConfig);

        assertNotNull(backend);
        assertTrue(backend instanceof SshShellBackend);

        // Verify backend is properly configured
        SshShellBackend sshBackend = (SshShellBackend) backend;
        // We can't directly access private fields, but we can test behavior
        assertFalse(sshBackend.isConnected());
    }
}