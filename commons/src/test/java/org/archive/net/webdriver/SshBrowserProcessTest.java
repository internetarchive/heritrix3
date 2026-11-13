package org.archive.net.webdriver;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.archive.net.webdriver.SshBrowserProcess.shellQuote;
import static org.junit.jupiter.api.Assertions.*;

class SshBrowserProcessTest {
    private static final List<String> AWKWARD_VALUES = List.of("", "plain", "with space", "it's",
            "\"double\"", "$HOME `id` $(id) \\ * ;", "'", "line\nbreak");

    @Test
    void shellQuoteSurvivesShell() throws Exception {
        for (String value : AWKWARD_VALUES) {
            assertEquals(value, runShell("printf %s " + shellQuote(value)));
        }
    }

    @Test
    void shellQuoteSurvivesNesting() throws Exception {
        // ssh joins the remote command's words with spaces and gives them to the remote user's
        // shell, which runs the wrapper with sh -c.
        for (String value : AWKWARD_VALUES) {
            assertEquals(value, runShell("sh -c " + shellQuote("printf %s " + shellQuote(value))));
        }
    }

    @Test
    void command() {
        var command = SshBrowserProcess.command("ssh", URI.create("ssh://alice@example.org:2222"),
                8443, 40000, "script");
        assertEquals(List.of("ssh",
                "-o", "BatchMode=yes",
                "-o", "LogLevel=INFO",
                "-o", "ControlMaster=no",
                "-o", "ControlPath=none",
                "-o", "ExitOnForwardFailure=yes",
                "-o", "ServerAliveInterval=15",
                "-o", "ServerAliveCountMax=3",
                "-o", "ConnectTimeout=10",
                "-R", "0:127.0.0.1:8443",
                "-L", "127.0.0.1:40000:127.0.0.1:40000",
                "-l", "alice",
                "-p", "2222",
                "--",
                "example.org",
                "sh -c 'script'"), command);
    }

    @Test
    void commandWithoutUserOrPort() {
        var command = SshBrowserProcess.command("ssh", URI.create("ssh://example.org"), 8443, 40000,
                "script");
        assertEquals(List.of("--", "example.org", "sh -c 'script'"),
                command.subList(command.size() - 3, command.size()));
        assertFalse(command.contains("-p"));
        assertFalse(command.contains("-l"));
    }

    @Test
    void commandHosts() {
        assertEquals(List.of("-l", "-oProxyCommand=x", "--", "::1"),
                destination("ssh://-oProxyCommand=x@[::1]"));
        assertEquals(List.of("-l", "bob", "-p", "22", "--", "my_box"),
                destination("ssh://bob@my_box:22"));
    }

    private static List<String> destination(String uri) {
        var command = SshBrowserProcess.command("ssh", URI.create(uri), 8443, 40000, "script");
        return command.subList(command.indexOf("-L") + 2, command.size() - 1);
    }

    @Test
    void commandRejectsOtherUris() {
        for (String uri : List.of("http://example.org", "ssh:example.org", "ssh://example.org/path",
                "ssh://example.org?query", "ssh://example.org:port")) {
            assertThrows(IllegalArgumentException.class, () -> SshBrowserProcess.command("ssh",
                    URI.create(uri), 8443, 40000, "script"), uri);
        }
    }

    @Test
    void wrapperScriptSetsVariables() throws Exception {
        String script = SshBrowserProcess.wrapperScript("/opt/my browser/firefox",
                List.of("--headless", "it's"), 50000, "profile-1", "user_pref('a', \"b\");\n");
        String output = runShell(script.substring(0, script.indexOf("base=")) +
                "printf '%s|' \"$profile\" \"$exe\" \"$port\" \"$user_js\" \"$@\"");
        assertEquals("profile-1|/opt/my browser/firefox|50000|user_pref('a', \"b\");\n" +
                "|--headless|it's|", output);
    }

    @Test
    void interruptedLaunchKillsSsh(@TempDir Path tempDir) throws Exception {
        Path fakeSsh = tempDir.resolve("fake-ssh");
        Files.writeString(fakeSsh, "#!/bin/sh\nexec sleep 60\n");
        Files.setPosixFilePermissions(fakeSsh, PosixFilePermissions.fromString("rwx------"));
        var before = ProcessHandle.current().children().toList();
        var launching = Thread.currentThread();
        new Thread(() -> {
            while (ProcessHandle.current().children().count() == before.size()) Thread.onSpinWait();
            launching.interrupt();
        }).start();
        assertThrows(InterruptedException.class, () -> SshBrowserProcess.launch(fakeSsh.toString(),
                URI.create("ssh://example.org"), null, List.of(), 8443, ""));
        var ssh = ProcessHandle.current().children().filter(c -> !before.contains(c)).toList();
        for (var child : ssh) child.onExit().get(10, TimeUnit.SECONDS);
    }

    @Test
    @EnabledIfSystemProperty(named = "runBrowserTests", matches = "true")
    void launch(@TempDir Path tempDir) throws Exception {
        Path cache = tempDir.resolve("cache");
        Path fakeSsh = tempDir.resolve("fake-ssh");
        // Stands in for ssh by running the remote command on this machine. Neither forward exists:
        // it reports the proxy's own port as allocated, and the browser listens on the BiDi port
        // directly.
        Files.writeString(fakeSsh, """
                #!/bin/sh
                while [ "$1" != -- ]; do
                    if [ "$1" = -R ]; then proxy_port=${2##*:}; fi
                    shift
                done
                shift 2
                echo "Allocated port $proxy_port for remote forward to 127.0.0.1:$proxy_port" >&2
                export XDG_CACHE_HOME=%s
                exec sh -c "$*"
                """.formatted(shellQuote(cache.toString())));
        Files.setPosixFilePermissions(fakeSsh, PosixFilePermissions.fromString("rwx------"));

        try (var proxy = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            var process = SshBrowserProcess.launch(fakeSsh.toString(),
                    URI.create("ssh://example.invalid"), null, List.of("--headless"),
                    proxy.getLocalPort(), "// it's user.js\n");
            assertEquals(proxy.getLocalPort(), process.proxyPort());
            var driver = new LocalWebDriverBiDi(process,
                    new Session.CapabilitiesRequest(Map.of(), List.of(Map.of())));
            Path profile = cache.resolve("heritrix-browser").resolve(process.profileName());
            assertEquals("// it's user.js\n", Files.readString(profile.resolve("user.js"), UTF_8));
            assertNotNull(driver.browsingContext().getTreeAsync(0).get(30, TimeUnit.SECONDS));
            driver.close();
            assertFalse(process.isAlive());
            assertFalse(Files.exists(profile.getParent()),
                    "profile and heritrix-browser directories should have been removed");
        }
    }

    private static String runShell(String script) throws IOException, InterruptedException {
        var process = new ProcessBuilder("sh", "-c", script).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), UTF_8);
        assertEquals(0, process.waitFor(), output);
        return output;
    }
}
