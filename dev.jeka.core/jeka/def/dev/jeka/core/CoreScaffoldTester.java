package dev.jeka.core;

import dev.jeka.core.api.system.JkLocator;
import dev.jeka.core.api.system.JkLog;
import dev.jeka.core.api.utils.JkUtilsPath;

import java.nio.file.Path;

/**
 * End-to-end tests about scaffolding.
 * Supposed to be run in dev.jeka.core working dir.
 */
class CoreScaffoldTester extends JekaCommandLineExecutor {

    CoreScaffoldTester() {
        super("..");
    }

    void run() {
        scaffold("scaffold#run", "help", false);
        Path projectDir = scaffold("scaffold#run scaffold#wrap", "help", false);
        Path jekaDir = JkLocator.getJekaHomeDir();
        scaffold("scaffold#run scaffold#wrap scaffold#wrapDelegatePath="
                + jekaDir, "help", true);
        projectDir = scaffold("scaffold#run project#", "clean project#pack", false);
        runJeka(projectDir.toString(), "eclipse#files eclipse#all");
        runJeka(projectDir.toString(), "intellij#iml intellij#modulesXml");
    }

    private Path scaffold(String scaffoldCmdLine, String checkCommandLine, boolean checkWithWrapper) {
        Path path = JkUtilsPath.createTempDirectory("jeka-scaffold-test-");
        runJeka(path.toString(), scaffoldCmdLine);
        runJeka(checkWithWrapper, path.toString(), checkCommandLine);
        return path;
    }

    public static void main(String[] args) throws Exception {
        JkLog.setDecorator(JkLog.Style.DEBUG);
        new CoreScaffoldTester().run();
    }

}
