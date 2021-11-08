package dev.jeka.core.api.java.project;

import dev.jeka.core.api.depmanagement.resolution.JkDependencyResolver;
import dev.jeka.core.api.file.JkPathSequence;
import dev.jeka.core.api.java.JkJavaCompileSpec;
import dev.jeka.core.api.java.JkJavaCompiler;
import dev.jeka.core.api.java.testing.JkTestProcessor;
import dev.jeka.core.api.java.testing.JkTestResult;
import dev.jeka.core.api.java.testing.JkTestSelection;
import dev.jeka.core.api.system.JkLog;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/**
 * Handles project testing step. This involve both test compilation and run.
 * Users can configure inner phases by chaining runnables.
 * They also can modify {@link JkJavaCompiler}, {@link JkJavaCompileSpec} for test compilation and
 * {@link JkTestProcessor}, {@link JkTestSelection} for test run.
 */
public class JkJavaProjectTesting {

    private final JkJavaProjectConstruction construction;

    private final JkJavaProjectCompilation<JkJavaProjectTesting> compilation;

    private JkTestProcessor testProcessor;

    private JkTestSelection testSelection;

    // relative path from output dir
    private String reportDir = "test-report";

    private boolean done;

    private boolean skipped;

    private boolean breakOnFailures = true;

    /**
     * For parent chaining
     */
    public final JkJavaProjectConstruction __;

    JkJavaProjectTesting(JkJavaProjectConstruction construction) {
        this.construction = construction;
        this.__ = construction;
        compilation = JkJavaProjectCompilation.ofTest(construction, this);
        testProcessor = defaultTestProcessor();
        testSelection = defaultTestSelection();
    }

    public JkJavaProjectTesting apply(Consumer<JkJavaProjectTesting> consumer) {
        consumer.accept(this);
        return this;
    }

    /**
     * Returns tests to be run. The returned instance is mutable so users can modify it
     * from this method return.
     */
    public JkTestSelection<JkJavaProjectTesting> getTestSelection() {
        return testSelection;
    }

    /**
     * Returns processor running the tests. The returned instance is mutable so users can modify it
     * from this method return.
     */
    public JkTestProcessor<JkJavaProjectTesting> getTestProcessor() {
        return testProcessor;
    }

    /**
     * Returns the compilation step for the test part.
     */
    public JkJavaProjectCompilation<JkJavaProjectTesting> getCompilation() {
        return compilation;
    }

    /**
     * Returns the classpath to run the test. It consists in test classes + prod classes +
     * dependencies defined in testing/compile.
     */
    public JkPathSequence getTestClasspath() {
        JkDependencyResolver resolver = construction.getDependencyResolver();
        JkJavaProjectCompilation prodCompilation = construction.getCompilation();
        return JkPathSequence.of()
                .and(compilation.getLayout().resolveClassDir())
                .and(compilation.resolveDependencies().getFiles())
                .and(prodCompilation.getLayout().resolveClassDir())
                .and(construction.resolveRuntimeDependencies().getFiles())
                .withoutDuplicates();
    }

    /**
     * Returns if the tests should be skipped.
     */
    public boolean isSkipped() {
        return skipped;
    }

    /**
     * Specifies if the tests should be skipped.
     */
    public JkJavaProjectTesting setSkipped(boolean skipped) {
        this.skipped = skipped;
        return this;
    }

    /**
     * Returns if #run should fail (throwing a {@link IllegalArgumentException}) if test result has failures.
     */
    public boolean isBreakOnFailures() {
        return breakOnFailures;
    }

    public JkJavaProjectTesting setBreakOnFailures(boolean breakOnFailures) {
        this.breakOnFailures = breakOnFailures;
        return this;
    }

    public Path getReportDir() {
        return construction.getProject().getOutputDir().resolve(reportDir);
    }

    public JkJavaProjectTesting setReportDir(String reportDir) {
        this.reportDir = reportDir;
        return this;
    }

    /**
     * Performs entire test phase, including : <ul>
     *     <li>compile regular code if needed</li>
     *     <li>perform pre test tasks if present</li>
     *     <li>compile test code and process test resources</li>
     *     <li>execute compiled tests</li>
     *     <li>execute post tesks if present</li>
     * </ul>
     */
    public void run() {
        JkLog.startTask("Process tests");
        this.construction.getCompilation().runIfNecessary();
        this.compilation.run();
        executeWithTestProcessor();
        JkLog.endTask();
    }

    /**
     * As #run but perfom only if not already done.
     */
    public void runIfNecessary() {
        if (done) {
            JkLog.trace("Tests has already been performed. Won't do it again.");
        } else if (skipped) {
            JkLog.info("Tests are skipped. Won't perform.");
        } else {
            run();
            done = true;
        }
    }

    void reset() {
        done = false;
    }

    private void executeWithTestProcessor() {
        UnaryOperator<JkPathSequence> op = paths -> paths.resolvedTo(construction.getProject().getOutputDir());
        testSelection.setTestClassRoots(op);
        JkTestResult result = testProcessor.launch(getTestClasspath(), testSelection);
        if (breakOnFailures) {
            result.assertNoFailure();
        }
    }

    private JkTestProcessor<JkJavaProjectTesting> defaultTestProcessor() {
        JkTestProcessor result = JkTestProcessor.ofParent(this);
        final Path reportDir = compilation.getLayout().getOutputDir().resolve(this.reportDir);
        result.getEngineBehavior()
                .setLegacyReportDir(reportDir)
                .setProgressDisplayer(JkTestProcessor.JkProgressOutputStyle.ONE_LINE);
        return result;
    }

    private JkTestSelection<JkJavaProjectTesting> defaultTestSelection() {
        return JkTestSelection.ofParent(this).addTestClassRoots(
                Paths.get(compilation.getLayout().getClassDir()));
    }

}
