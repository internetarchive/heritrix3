/*
 *  This file is part of the Heritrix web crawler (crawler.archive.org).
 *
 *  Licensed to the Internet Archive (IA) by one or more individual
 *  contributors.
 *
 *  The IA licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.archive.net.webdriver;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * A browser launched on another machine by an ssh client process. The ssh client forwards a port
 * on the remote machine to the recording proxy and the browser's WebDriver BiDi port to the same
 * port on this machine.
 */
public class SshBrowserProcess extends BrowserProcess {
    private static final Pattern ALLOCATED_PORT =
            Pattern.compile("Allocated port (\\d+) for remote forward to .*");
    private static final Pattern AUTHORITY =
            Pattern.compile("(?:(.+)@)?(?:\\[([^]]+)]|([^@:\\[\\]]+))(?::(\\d+))?");
    private static final String WRAPPER_SCRIPT = """
            base=${XDG_CACHE_HOME:-$HOME/.cache}/heritrix-browser
            mkdir -p "$base" && cd "$base" && mkdir "$profile" || exit 1
            printf '%s' "$user_js" > "$profile/user.js"
            if [ -z "$exe" ]; then
                exe=$(command -v firefox || command -v chromedriver) || {
                    echo 'Neither firefox nor chromedriver is on the PATH' >&2
                    rm -rf "$profile"
                    rmdir "$base" 2>/dev/null
                    exit 1
                }
            fi
            # sshd closes our stdin when the ssh client exits or is killed. Background jobs get
            # /dev/null as stdin, so keep ours as fd 3 for the watcher below and don't pass it to
            # the browser.
            exec 3<&0
            case $exe in
                *firefox*) set -- --remote-debugging-port=$port --profile "$PWD/$profile" "$@" ;;
                *) set -- --port=$port "$@" ;;
            esac
            # With setsid the browser gets its own process group, so whatever it starts, like
            # chromedriver's Chrome, can be killed along with it.
            setsid=$(command -v setsid)
            $setsid "$exe" "$@" </dev/null 3<&- &
            browser=$!
            target=$browser
            [ -n "$setsid" ] && target=-$browser
            # Once ssh is gone our stdout and stderr are broken pipes, and the shell reporting
            # that the browser was killed would get SIGPIPE before cleaning up.
            exec >/dev/null 2>&1
            # When stdin reaches EOF, ask the browser to exit and kill it if it hasn't after ten
            # seconds. If the browser exits first, the watcher is killed instead.
            { cat >/dev/null; kill -TERM $target; sleep 10; kill -KILL $target; } <&3 &
            watcher=$!
            wait $browser
            status=$?
            kill $watcher 2>/dev/null
            [ -n "$setsid" ] && kill -KILL $target
            rm -rf "$profile"
            rmdir "$base" 2>/dev/null
            exit $status
            """;
    private final int proxyPort;
    private final String profileName;

    private SshBrowserProcess(Process process, CompletableFuture<Integer> allocatedPort,
                              String profileName) throws IOException, InterruptedException {
        super(process, line -> {
            Matcher matcher = ALLOCATED_PORT.matcher(line);
            if (matcher.matches()) allocatedPort.complete(Integer.parseInt(matcher.group(1)));
        });
        process.onExit().thenRun(() -> allocatedPort.completeExceptionally(
                new IOException("ssh exited with status " + process.exitValue())));
        this.profileName = profileName;
        try {
            this.proxyPort = allocatedPort.get(LAUNCH_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (ExecutionException | TimeoutException e) {
            destroyForcibly();
            throw new IOException("ssh did not report the port forwarded to the proxy", e);
        } catch (InterruptedException e) {
            destroyForcibly();
            throw e;
        }
    }

    /**
     * Launches a browser over ssh.
     *
     * @param sshCommand the ssh client to run
     * @param uri        ssh://[user@]host[:port]
     * @param executable the browser to run on the remote machine, or null for firefox or
     *                   chromedriver from the PATH
     * @param proxyPort  the port of the recording proxy on this machine
     * @param userJs     contents of the user.js file to write into the Firefox profile
     */
    public static SshBrowserProcess launch(String sshCommand, URI uri, String executable,
                                           List<String> options, int proxyPort, String userJs)
            throws IOException, InterruptedException {
        // Firefox rejects a WebSocket handshake whose Host header has a different port than it
        // listens on, so the BiDi port is forwarded to the same port number. If that's taken on
        // the remote machine the launch fails and is retried.
        int bidiPort;
        try (var socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            bidiPort = socket.getLocalPort();
        }
        String profileName = "profile-" + UUID.randomUUID();
        String script = wrapperScript(executable, options, bidiPort, profileName, userJs);
        // stdin is left as a pipe from this JVM so that it reaches EOF if the JVM dies
        var process = new ProcessBuilder(command(sshCommand, uri, proxyPort, bidiPort, script))
                .redirectErrorStream(true)
                .start();
        return new SshBrowserProcess(process, new CompletableFuture<>(), profileName);
    }

    /**
     * Checks that uri is ssh://[user@]host[:port]. The host may be an IPv6 literal in brackets
     * or an alias from the ssh config.
     *
     * @throws IllegalArgumentException if it isn't
     */
    public static void checkUri(URI uri) {
        parseAuthority(uri);
    }

    private static Matcher parseAuthority(URI uri) {
        Matcher matcher;
        if (!"ssh".equals(uri.getScheme()) || uri.getAuthority() == null
            || !uri.getRawPath().isEmpty() || uri.getRawQuery() != null
            || !(matcher = AUTHORITY.matcher(uri.getAuthority())).matches()) {
            throw new IllegalArgumentException("Expected ssh://[user@]host[:port] but got " + uri);
        }
        return matcher;
    }

    static List<String> command(String sshCommand, URI uri, int proxyPort, int bidiPort,
                                String script) {
        Matcher authority = parseAuthority(uri);
        var command = new ArrayList<>(List.of(sshCommand,
                "-o", "BatchMode=yes",
                // the Allocated port message is logged at INFO
                "-o", "LogLevel=INFO",
                // a shared connection wouldn't report the port and would keep the forwards open
                "-o", "ControlMaster=no",
                "-o", "ControlPath=none",
                "-o", "ExitOnForwardFailure=yes",
                "-o", "ServerAliveInterval=15",
                "-o", "ServerAliveCountMax=3",
                "-o", "ConnectTimeout=10",
                "-R", "0:127.0.0.1:" + proxyPort,
                "-L", "127.0.0.1:" + bidiPort + ":127.0.0.1:" + bidiPort));
        if (authority.group(1) != null) {
            command.add("-l");
            command.add(authority.group(1));
        }
        if (authority.group(4) != null) {
            command.add("-p");
            command.add(authority.group(4));
        }
        command.add("--");
        command.add(authority.group(2) != null ? authority.group(2) : authority.group(3));
        command.add("sh -c " + shellQuote(script));
        return command;
    }

    static String wrapperScript(String executable, List<String> options, int port,
                                String profileName, String userJs) {
        String args = options.stream().map(option -> " " + shellQuote(option))
                .collect(Collectors.joining());
        return "profile=" + shellQuote(profileName) + "\n" +
               "exe=" + shellQuote(executable == null ? "" : executable) + "\n" +
               "port=" + port + "\n" +
               "user_js=" + shellQuote(userJs) + "\n" +
               "set --" + args + "\n" +
               WRAPPER_SCRIPT;
    }

    static String shellQuote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }

    /**
     * The port on the remote machine that is forwarded to the recording proxy.
     */
    public int proxyPort() {
        return proxyPort;
    }

    /**
     * The name of the browser's profile directory, relative to the browser's working directory.
     */
    public String profileName() {
        return profileName;
    }

    /**
     * Closes ssh's stdin so the remote side stops the browser and removes its profile, then waits
     * for ssh to exit.
     */
    @Override
    public void close() {
        try {
            process.getOutputStream().close();
            process.waitFor(15, TimeUnit.SECONDS);
        } catch (IOException e) {
            // fall back to killing ssh
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        super.close();
    }
}
