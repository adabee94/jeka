import dev.jeka.core.api.project.JkProject;
import dev.jeka.core.api.testing.JkTestProcessor;
import dev.jeka.core.api.utils.JkUtilsAssert;
import dev.jeka.core.tool.JkBean;
import dev.jeka.core.tool.JkInjectClasspath;
import dev.jeka.core.tool.JkInit;
import dev.jeka.core.tool.builtins.project.ProjectJkBean;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestPlan;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * This build class illustrates how to use directly JUnit5 Platform API.
 *
 * To use JUnit5 Platform API in the build class, you need to declare *plateform-launcher* library on
 * the def classpath as done below.
 *
 * Normally, for most of cases, you won't need it has the Jeka API are yet powerful enough to customize
 * most of the testing scenario.
 *
 * There is 2 points where you can add *junit5 native* instructions from a {@link JkTestProcessor} :
 * <ul>
 *     <li>getEngineBehavior().setLauncherConfigurer()</li>
 *     <li>getTestSelection().setDiscoveryConfigurer()</li>
 * </ul>
 * From this two points you can customize the builder to be used for creating the actual
 * {@link org.junit.platform.launcher.core.LauncherConfig} and
 * {@link org.junit.platform.launcher.LauncherDiscoveryRequest}.
 */
@JkInjectClasspath("org.junit.platform:junit-platform-launcher:1.8.2")
class Junit5Build extends JkBean {

    final ProjectJkBean projectBean = getBean(ProjectJkBean.class).configure(this::configure);

    /*
     * Configures plugins to be bound to this command class. When this method is called, option
     * fields have already been injected from command line.
     */
    private void configure(JkProject project) {
        project
                .testing
                    .testCompilation
                        .configureDependencies(deps -> deps
                            .and("org.jdom:jdom2:2.0.6")
                        )
                        .configureDependencies(deps -> deps
                            .and("org.junit.jupiter:junit-jupiter:5.8.2")
                        ).__
                    .testProcessor
                        .engineBehavior
                            .setLauncherConfigurer(builder -> builder  // Junit5-platform API. see nit.org/junit5/docs/5.3.0/api/org/junit/platform/launcher/core/LauncherConfig.html
                                .addTestExecutionListeners(new MyJunit5PlatformListener())).__.__
                    .testSelection
                        .setDiscoveryConfigurer(builder -> builder  // see https://junit.org/junit5/docs/5.0.0/api/org/junit/platform/launcher/core/LauncherDiscoveryRequestBuilder.html
                            .configurationParameter("key1", "value1")
                            .selectors(
                                DiscoverySelectors.selectMethod("dev.jeka.core.samples.FooTest#testDisplay")));
    }

    static class MyJunit5PlatformListener implements TestExecutionListener {

        @Override
        public void testPlanExecutionStarted(TestPlan testPlan) {
            System.out.println("Test plan " + testPlan + " is being executed ...");
        }

        @Override
        public void testPlanExecutionFinished(TestPlan testPlan) {
            System.out.println("Test plan " + testPlan + " has been executed ...");
        }
    }

    public void cleanPack() {
        cleanOutput(); projectBean.pack();
    }

    public void checkReportGenerated() {
        Path report = getOutputDir().resolve("test-report");
        JkUtilsAssert.state(Files.exists(report), "Report file " + report + " not found.");
    }

    public static void main(String[] args) {
        JkInit.instanceOf(Junit5Build.class, args).cleanPack();
    }

}