package dev.jeka.plugins.nodejs;

import dev.jeka.core.api.system.JkProcess;
import dev.jeka.core.api.utils.JkUtilsString;
import dev.jeka.core.api.utils.JkUtilsSystem;

import java.nio.file.Files;
import java.nio.file.Path;

public class JkNodeJs {

    private final Path installDir;

    private JkNodeJs(Path installDir) {
        this.installDir = installDir;
    }

    public static JkNodeJs of(Path installDir) {
        return new JkNodeJs(installDir);
    }

    public void npm(Path workingDir, String commandLine) {
        String cmd = JkUtilsSystem.IS_WINDOWS ? "npm.cmd" : "bin/npm";
        createProcess(workingDir, cmd).exec(JkUtilsString.translateCommandline(commandLine));
    }

    public void npx(Path workingDir, String commandLine) {
        String cmd = JkUtilsSystem.IS_WINDOWS ? "npx.cmd" : "bin/npx";
        createProcess(workingDir, cmd).exec(JkUtilsString.translateCommandline(commandLine));
    }

    private JkProcess createProcess(Path workingDir, String cmdName) {
        //String path = System.getenv("PATH");
        //String pathVar = this.installDir.toString() + File.pathSeparator + path;
        return JkProcess.of(installDir.resolve(cmdName).toString())
                .setWorkingDir(workingDir)
                .setFailOnError(true)
                //.setEnv("PATH", pathVar)
                .setLogCommand(true)
                .setLogOutput(true);
    }

    public boolean isBinaryPresent() {
        if (JkUtilsSystem.IS_WINDOWS) {
            return Files.exists(installDir.resolve("npm.cmd"));
        }
        return Files.exists(installDir.resolve("bin/npm"));
    }
}
