import dev.jeka.core.JekaCommandLineExecutor;
import dev.jeka.core.api.system.JkLog;
import dev.jeka.core.api.utils.JkUtilsPath;

import java.awt.*;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;

/**
 * This class has to be run using dev.jeka.master as working dir.
 * It assumes that dev.jeka.core module has already been built.
 */
class SamplesTester extends JekaCommandLineExecutor {

    SamplesTester() {
        super("..");
    }

    void run() {
        runJekaw("dev.jeka.samples.basic", "-kb=simpleProject cleanPackPublish checkedValue=A checkValueIsA");
        runJeka("dev.jeka.samples.springboot", "@../../plugins/dev.jeka.plugins.springboot project#clean project#pack project#publishLocal");
        runJekaw("dev.jeka.samples.basic", "-kb=signedArtifacts cleanPackPublish");
        runJekaw("dev.jeka.samples.basic", "-kb=thirdPartyDependencies cleanPack");
        runJekaw("dev.jeka.samples.basic", "-kb=antStyle cleanPackPublish");
        runJekaw("dev.jeka.samples.dependers", "-kb=fatJar project#clean project#pack");
        runJekaw("dev.jeka.samples.dependers", "-kb=normalJar project#clean project#pack");
        runJekaw("dev.jeka.samples.junit5", "project#clean project#pack");
        runJekaw("dev.jeka.samples.junit5", "project#clean project#pack checkReportGenerated -project#tests.fork");
        runJeka("dev.jeka.samples.jacoco", "@../../plugins/dev.jeka.plugins.jacoco project#clean project#pack checkReportGenerated");
        runJeka("dev.jeka.samples.sonarqube", "@../../plugins/dev.jeka.plugins.sonarqube project#clean project#pack");
    }

    public void launchManually(String cmdLine) {
        Path dir = JkUtilsPath.createTempDirectory("jeka-sample-generated");
        runJeka(dir.toString(), cmdLine);
        try {
            Desktop.getDesktop().open(dir.toFile());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static void main(String[] args) throws IOException {
        JkLog.setDecorator(JkLog.Style.BRACE);
        new SamplesTester().run();
    }

    public static class ExtraLauncher {
        public static void main(String[] args) {
            new SamplesTester().launchManually("scaffold#run project#");
        }
    }


}
