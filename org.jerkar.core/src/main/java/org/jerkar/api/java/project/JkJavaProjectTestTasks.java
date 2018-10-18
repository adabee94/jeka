package org.jerkar.api.java.project;

import org.jerkar.api.depmanagement.JkJavaDepScopes;
import org.jerkar.api.file.JkPathMatcher;
import org.jerkar.api.file.JkPathSequence;
import org.jerkar.api.file.JkPathTreeSet;
import org.jerkar.api.function.JkRunnables;
import org.jerkar.api.java.JkClasspath;
import org.jerkar.api.java.JkJavaCompileSpec;
import org.jerkar.api.java.JkJavaCompiler;
import org.jerkar.api.java.JkResourceProcessor;
import org.jerkar.api.java.junit.JkJavaTestClasses;
import org.jerkar.api.java.junit.JkUnit;

import java.nio.charset.Charset;
import java.nio.file.Path;

public class JkJavaProjectTestTasks {

    private final JkJavaProjectMaker maker;

    private final JkRunnables preTest = JkRunnables.of(() -> {});

    private final JkRunnables resourceGenerator = JkRunnables.of(() -> {});

    public final JkRunnables postTest = JkRunnables.of(() -> {});

    private final JkRunnables resourceProcessor;

    private final JkRunnables compileRunner;

    private JkUnit runner;

    public final JkRunnables testExecutor = JkRunnables.of(() -> runner.run(getTestClasses()));

    private JkJavaCompiler compiler = JkJavaCompiler.of();

    private JkPathMatcher testClassMatcher = JkPathMatcher.ofAccept("**/*Test.class");

    private JkJavaProjectTestTasks testProcessors;

    JkJavaProjectTestTasks(JkJavaProjectMaker maker, Charset charset) {
        this.maker = maker;
        resourceProcessor = JkRunnables.of(() -> JkResourceProcessor.of(maker.project.getSourceLayout().testResources())
                .and(maker.getOutLayout().generatedTestResourceDir())
                .and(maker.project.getResourceInterpolators())
                .generateTo(maker.getOutLayout().testClassDir(), charset));
        compileRunner = JkRunnables.of(() -> {
            final JkJavaCompileSpec testCompileSpec = getTestCompileSpec();
            compiler.compile(testCompileSpec);
        });
        runner = getDefaultTester();
    }

    public JkRunnables getPreTest() {
        return preTest;
    }

    public JkRunnables getResourceGenerator() {
        return resourceGenerator;
    }

    public JkRunnables getResourceProcessor() {
        return resourceProcessor;
    }

    public JkRunnables getCompileRunner() {
        return compileRunner;
    }

    public JkJavaCompiler getCompiler() {
        return compiler;
    }

    public JkJavaProjectTestTasks setCompiler(JkJavaCompiler compiler) {
        this.compiler = compiler;
        return this;
    }

    public JkJavaProjectTestTasks setFork(boolean fork, String ... params) {
        this.compiler = this.compiler.withForking(fork, params);
        return this;
    }


    public JkUnit getRunner() {
        return runner;
    }

    public void setRunner(JkUnit runner) {
        this.runner = runner;
    }

    private final JkUnit getDefaultTester() {
        final Path junitReport = maker.getOutLayout().testReportDir().resolve("junit");
        return JkUnit.of().withOutputOnConsole(false).withReport(JkUnit.JunitReportDetail.BASIC)
                .withReportDir(junitReport);
    }

    public JkJavaTestClasses getTestClasses() {
        return JkJavaTestClasses.of(getTestClasspath(),
                JkPathTreeSet.of(maker.getOutLayout().testClassDir()).andMatcher(testClassMatcher));
    }

    public JkJavaProjectTestTasks setForkRun(boolean fork) {
        this.runner = runner.withForking(fork);
        return this;
    }

    public JkJavaProjectTestTasks setForkCompile(boolean fork, String ... params) {
        compiler = compiler.withForking(fork, params);
        return this;
    }

    public JkPathMatcher getTestClassMatcher() {
        return testClassMatcher;
    }

    public JkJavaProjectTestTasks setTestClassMatcher(JkPathMatcher testClassMatcher) {
        this.testClassMatcher = testClassMatcher;
        return this;
    }

    private JkJavaCompileSpec getTestCompileSpec() {
        JkJavaCompileSpec result = maker.project.getCompileSpec().copy();
        final JkPathSequence classpath = maker.fetchDependenciesFor(JkJavaDepScopes.SCOPES_FOR_TEST).andPrepending(maker.getOutLayout().classDir());
        return result
                .setClasspath(classpath)
                .addSources(maker.project.getSourceLayout().tests())
                .setOutputDir(maker.getOutLayout().testClassDir());
    }

    public JkClasspath getTestClasspath() {
        return JkClasspath.of(maker.getOutLayout().testClassDir())
                .and(maker.getOutLayout().classDir())
                .andMany(maker.fetchDependenciesFor(JkJavaDepScopes.SCOPES_FOR_TEST));
    }


    void run() {
        preTest.run();
        compileRunner.run();
        resourceGenerator.run();
        resourceProcessor.run();
        testExecutor.run();
        postTest.run();
    }

}
