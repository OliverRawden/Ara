package tech.rawden.ara.tool;

import java.util.concurrent.TimeUnit;

public class TerminalExecutor {

    private static final long DEFAULT_TIMEOUT_MS = 30_000;

    public record CommandResult(int exitCode, String stdout, String stderr, long elapsedMs, boolean timedOut) {}

    public static CommandResult execute(String command) {
        return execute(command, DEFAULT_TIMEOUT_MS);
    }

    public static CommandResult execute(String command, long timeoutMs) {
        try {
            var builder = new ProcessBuilder("/bin/bash", "-c", command).redirectErrorStream(false);
            var start = System.nanoTime();
            var process = builder.start();

            boolean finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
            var elapsedMs = (System.nanoTime() - start) / 1_000_000;

            if (!finished) {
                process.destroyForcibly();
                var partialStdout = new String(process.getInputStream().readAllBytes());
                var partialStderr = new String(process.getErrorStream().readAllBytes());
                return new CommandResult(-1, partialStdout, partialStderr, elapsedMs, true);
            }

            var stdout = new String(process.getInputStream().readAllBytes());
            var stderr = new String(process.getErrorStream().readAllBytes());
            return new CommandResult(process.exitValue(), stdout, stderr, elapsedMs, false);
        } catch (Exception e) {
            return new CommandResult(-1, "", e.getMessage() + "\n", 0, false);
        }
    }
}
