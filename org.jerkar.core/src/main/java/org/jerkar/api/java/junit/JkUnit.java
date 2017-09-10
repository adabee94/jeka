package org.jerkar.api.java.junit;

import java.io.File;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;

import org.jerkar.api.file.JkFileTree;
import org.jerkar.api.file.JkFileTreeSet;
import org.jerkar.api.java.JkClassLoader;
import org.jerkar.api.java.JkClasspath;
import org.jerkar.api.java.JkJavaProcess;
import org.jerkar.api.system.JkLog;
import org.jerkar.api.utils.JkUtilsIO;
import org.jerkar.api.utils.JkUtilsIterable;
import org.jerkar.api.utils.JkUtilsReflect;
import org.jerkar.api.utils.JkUtilsString;

/**
 * Convenient class to launch Junit tests.
 *
 * @author Jerome Angibaud
 */
public final class JkUnit {

    /**
     * Detail level for the junit report.
     */
    public enum JunitReportDetail {

        /** No report at all */
        NONE,

        /** Only detail abut the failed test and overall statistics */
        BASIC,

        /** Complete report as the ones generated by surefire.*/
        FULL;
    }

    private static final String JUNIT4_RUNNER_CLASS_NAME = "org.junit.runner.JUnitCore";

    private static final String JUNIT3_RUNNER_CLASS_NAME = "junit.textui.TestRunner";

    private static final String JUNIT4_TEST_ANNOTATION_CLASS_NAME = "org.junit.Test";

    private static final String JUNIT3_TEST_CASE_CLASS_NAME = "junit.framework.TestCase";

    private static final String JUNIT3_TEST_SUITE_CLASS_NAME = "junit.framework.TestSuite";

    private static final String JUNIT3_TEST_RESULT_CLASS_NAME = "junit.framework.TestResult";

    /**
     * A interface to implement to enhance test execution. It can be used for test coverage tool for example.
     */
    //@FunctionalInterface
    //public static interface Enhancer {

    /**
     * Returns a modified Junit launcher from the specified one.
     */
    //   JkUnit enhance(JkUnit jkUnit);

    //}

    private final JkClasspath classpath;

    private final JunitReportDetail reportDetail;

    private final File reportDir;

    private final JkJavaProcess forkedProcess;

    private final List<Runnable> postActions;

    private final JkFileTreeSet classesToTest;

    private final boolean breakOnFailure;

    private final boolean printOutputOnConsole;

    private JkUnit(JkClasspath classpath, JunitReportDetail reportDetail, File reportDir,
            JkJavaProcess fork, List<Runnable> runnables, JkFileTreeSet testClasses,
            boolean crashOnFailed, boolean printOutputOnConsole) {
        this.classpath = classpath;
        this.reportDetail = reportDetail;
        this.reportDir = reportDir;
        this.forkedProcess = fork;
        this.postActions = Collections.unmodifiableList(runnables);
        this.classesToTest = testClasses;
        this.breakOnFailure = crashOnFailed;
        this.printOutputOnConsole = printOutputOnConsole;
    }

    @SuppressWarnings("unchecked")
    private JkUnit(JkClasspath classpath, JunitReportDetail reportDetail, File reportDir,
            JkJavaProcess fork, JkFileTreeSet testClasses, boolean crashOnFailed,
            boolean printOutputOnConsole) {
        this(classpath, reportDetail, reportDir, fork, Collections.EMPTY_LIST, testClasses,
                crashOnFailed, printOutputOnConsole);
    }

    /**
     * Returns a launcher forking a process according the specified java process description.
     */
    public static JkUnit ofFork(JkJavaProcess jkJavaProcess) {
        return new JkUnit(null, JunitReportDetail.NONE, null, jkJavaProcess, JkFileTreeSet.empty(),
                true, true);
    }

    /**
     * Returns a launcher forking a process having the specified classpath.
     */
    public static JkUnit ofFork(JkClasspath classpath) {
        return ofFork(JkJavaProcess.of().withClasspath(classpath));
    }


    /**
     * Returns a launcher forking a process having the specified class directory and the specified classpath entries.
     */
    public static JkUnit ofClasspath(File binDir, Iterable<File> classpathEntries) {
        return of(JkClasspath.of(binDir).and(classpathEntries));
    }

    /**
     * Returns a launcher having the specified classpath.
     */
    public static JkUnit of(JkClasspath classpath) {
        return new JkUnit(classpath, JunitReportDetail.NONE, null, null, JkFileTreeSet.empty(),
                true, true);
    }



    /**
     * Returns an empty launcher
     */
    public static JkUnit of() {
        return new JkUnit(JkClasspath.of(), JunitReportDetail.NONE, null, null, JkFileTreeSet.empty(),
                true, true);
    }

    /**
     * Returns a copy of this launcher but with the specified report detail.
     */
    public JkUnit withReport(JunitReportDetail reportDetail) {
        return new JkUnit(this.classpath, reportDetail, reportDir, this.forkedProcess,
                classesToTest, this.breakOnFailure, this.printOutputOnConsole);
    }

    /**
     * Returns a copy of this launcher but that fail fast on the first failure.
     */
    public JkUnit withBreakOnFailure(boolean crashOnFailure) {
        return new JkUnit(this.classpath, reportDetail, reportDir, this.forkedProcess,
                classesToTest, this.breakOnFailure, this.printOutputOnConsole);
    }

    /**
     * Returns a copy of this launcher but with the specified report directory output.
     */
    public JkUnit withReportDir(File reportDir) {
        return new JkUnit(this.classpath, reportDetail, reportDir, this.forkedProcess,
                classesToTest, this.breakOnFailure, this.printOutputOnConsole);
    }

    /**
     * Returns a copy of this launcher but with the specified report directory output.
     */
    public JkUnit withClasspath(JkClasspath classpath) {
        return new JkUnit(classpath, reportDetail, reportDir, this.forkedProcess,
                classesToTest, this.breakOnFailure, this.printOutputOnConsole);
    }

    /**
     * Returns a copy of this launcher but forked.
     */
    public JkUnit forkKeepingSameClassPath(JkJavaProcess process) {
        final JkJavaProcess fork = process.withClasspath(jkClasspath());
        return new JkUnit(null, reportDetail, reportDir, fork, this.classesToTest,
                this.breakOnFailure, this.printOutputOnConsole);
    }

    /**
     * Returns a copy of this launcher but specifying an action to run at the end execution.
     */
    public JkUnit withPostAction(Runnable runnable) {
        final List<Runnable> list = new LinkedList<Runnable>(this.postActions);
        list.add(runnable);
        return new JkUnit(classpath, reportDetail, reportDir, forkedProcess, list,
                this.classesToTest, this.breakOnFailure, this.printOutputOnConsole);
    }

    /**
     * Creates a forked JkUnit from this one but using the specified process. If
     * <code>appendClasspath</code> is <code>true</code> then the classpath
     * already defined in this object is appended to the specified process
     * classpath.
     */
    public JkUnit forked(JkJavaProcess process, boolean appendClasspath) {
        final JkJavaProcess effectiveProcess = appendClasspath ? process
                .andClasspath(this.classpath) : process;
                return new JkUnit(null, reportDetail, reportDir, effectiveProcess, this.classesToTest,
                        this.breakOnFailure, this.printOutputOnConsole);
    }

    /**
     * Creates an identical JkUnit to this one but specifying the forked mode.
     * If the forked mode is <code>true<code> then the specified
     * {@link JkJavaProcess} is used to run the tests..
     */
    public JkUnit forked(boolean fork, JkJavaProcess process, boolean appendClasspath) {
        if (fork && !forked()) {
            return forked(process, appendClasspath);
        }
        if (!fork && forked()) {
            return new JkUnit(forkedProcess.classpath(), reportDetail, reportDir, null,
                    this.classesToTest, this.breakOnFailure, this.printOutputOnConsole);
        }
        return this;
    }

    /**
     * Creates an identical JkUnit to this one but specifying the forked mode.
     * If the forked mode is <code>true<code> then default {@link JkJavaProcess}
     * is used to run the tests (java process launched without any option).
     */
    public JkUnit forked(boolean fork) {
        return forked(fork, JkJavaProcess.of(), true);
    }

    /**
     * Returns an enhanced copy of this launcher but specifying location of classes to test.
     */
    public JkUnit withClassesToTest(JkFileTreeSet classesToTest) {
        return new JkUnit(this.classpath, reportDetail, reportDir, forkedProcess, classesToTest,
                this.breakOnFailure, this.printOutputOnConsole);
    }

    /**
     * Returns an enhanced copy of this launcher but specifying if the output should be displayed on console.
     */
    public JkUnit withOutputOnConsole(boolean outputOnConsole) {
        return new JkUnit(this.classpath, reportDetail, reportDir, forkedProcess, classesToTest,
                this.breakOnFailure, outputOnConsole);
    }

    /**
     * Returns an enhanced copy of this launcher but specifying location of classes to test.
     */
    public JkUnit withClassesToTest(JkFileTree classesToTest) {
        return new JkUnit(this.classpath, reportDetail, reportDir, forkedProcess,
                JkFileTreeSet.of(classesToTest), this.breakOnFailure, this.printOutputOnConsole);
    }

    /**
     * Returns an enhanced copy of this launcher but specifying location of classes to test.
     */
    public JkUnit withClassesToTest(File... classDirs) {
        return new JkUnit(this.classpath, reportDetail, reportDir, forkedProcess,
                JkFileTreeSet.of(classDirs), this.breakOnFailure, this.printOutputOnConsole);
    }

    /**
     * Returns <code>true</code> if this launcher is forked.
     */
    public boolean forked() {
        return this.forkedProcess != null;
    }

    /**
     * Returns the classpath for this launcher.
     */
    public JkClasspath classpath() {
        return classpath;
    }

    /**
     * Returns the report detail level for this launcher.
     */
    public JunitReportDetail reportDetail() {
        return reportDetail;
    }

    /**
     * Returns the output report dir.
     */
    public File reportDir() {
        return reportDir;
    }

    /**
     * Returns the process description if this launcher is forked.
     */
    public JkJavaProcess processFork() {
        return forkedProcess;
    }

    /**
     * Runs the test suite and return the result.
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public JkTestSuiteResult run() {
        final Collection<Class> classes = getClassesToTest();
        final String name = getSuiteName(classes);

        if (!classes.iterator().hasNext()) {
            JkLog.warn("No test class found.");
            return JkTestSuiteResult.empty((Properties) System.getProperties().clone(), name, 0);
        }
        final long start = System.nanoTime();
        final JkClassLoader classLoader = JkClassLoader.of(classes.iterator().next());


        final JkTestSuiteResult result;

        if (classLoader.isDefined(JUNIT4_RUNNER_CLASS_NAME)) {
            if (this.forkedProcess != null) {
                JkLog.startln("Run JUnit tests in forked mode");
                result = JUnit4TestLauncher.launchInFork(forkedProcess, printOutputOnConsole,
                        reportDetail, classes, reportDir);
            } else {
                JkLog.startln("Run JUnit tests");
                result = JUnit4TestLauncher.launchInClassLoader(classes, printOutputOnConsole,
                        reportDetail, reportDir);
            }
        } else if (classLoader.isDefined(JUNIT3_RUNNER_CLASS_NAME)) {
            JkLog.startln("Run JUnit tests");
            final Object suite = createJunit3TestSuite(classLoader, classes);
            final Class testResultClass = classLoader.load(JUNIT3_TEST_RESULT_CLASS_NAME);
            final Object testResult = JkUtilsReflect.newInstance(testResultClass);
            final Method runMethod = JkUtilsReflect.getMethod(suite.getClass(), "run",
                    testResultClass);
            final Properties properties = (Properties) System.getProperties().clone();
            JkUtilsReflect.invoke(suite, runMethod, testResult);
            final long end = System.nanoTime();
            final long duration = (end - start) / 1000000;
            result = fromJunit3Result(properties, name, testResult, duration);
        } else {
            JkUtilsIO.closeQuietly(classLoader.classloader());
            throw new IllegalStateException("No Junit found on test classpath.");

        }

        if (result.failureCount() > 0) {
            if (breakOnFailure) {
                JkLog.error(result.toStrings(JkLog.verbose()));
                JkUtilsIO.closeQuietly(classLoader.classloader());
                throw new IllegalStateException("Test failed : " + result.toString());
            } else {
                JkLog.warn(result.toStrings(JkLog.verbose()));
            }
        } else {
            JkLog.info(result.toStrings(JkLog.verbose()));
        }
        if (!JkLog.verbose() && result.failureCount() > 0) {
            JkLog.info("Launch Jerkar in verbose mode to display failure stack traces in console.");
        }
        if (reportDetail.equals(JunitReportDetail.BASIC)) {
            TestReportBuilder.of(result).writeToFileSystem(reportDir);
        }
        for (final Runnable runnable : this.postActions) {
            runnable.run(); // NOSONAR
        }
        JkLog.done("Tests run");
        JkUtilsIO.closeQuietly(classLoader.classloader());
        return result;
    }

    private JkClasspath jkClasspath() {
        if (classpath != null) {
            return classpath;
        }
        return forkedProcess.classpath();
    }

    @SuppressWarnings("rawtypes")
    private Collection<Class> getClassesToTest() {
        final JkClasspath classpath = this.jkClasspath().andHead(this.classesToTest.roots());
        final JkClassLoader classLoader = JkClassLoader.system().parent().child(classpath)
                .loadAllServices();
        final Collection<Class> result = getJunitTestClassesInClassLoader(classLoader, this.classesToTest);
        if (result.isEmpty()) {

            JkUtilsIO.closeOrFail(classLoader.classloader());
        }
        return result;
    }

    @SuppressWarnings("rawtypes")
    private static Collection<Class> getJunitTestClassesInClassLoader(JkClassLoader classloader,
            JkFileTreeSet jkFileTreeSet) {
        final Iterable<Class<?>> classes = classloader.loadClassesIn(jkFileTreeSet);
        final List<Class> testClasses = new LinkedList<Class>();
        if (classloader.isDefined(JUNIT4_RUNNER_CLASS_NAME)) {
            final Class<Annotation> testAnnotation = classloader
                    .load(JUNIT4_TEST_ANNOTATION_CLASS_NAME);
            final Class<?> testCaseClass = classloader.load(JUNIT3_TEST_CASE_CLASS_NAME);
            for (final Class clazz : classes) {
                if (isJunit3Test(clazz, testCaseClass) || isJunit4Test(clazz, testAnnotation)) {
                    testClasses.add(clazz);
                }
            }
        } else if (classloader.isDefined(JUNIT3_RUNNER_CLASS_NAME)) {
            final Class<?> testCaseClass = classloader.load(JUNIT3_TEST_CASE_CLASS_NAME);
            for (final Class clazz : classes) {
                if (isJunit3Test(clazz, testCaseClass)) {
                    testClasses.add(clazz);
                }
            }
        }
        return testClasses;
    }

    private static boolean isJunit3Test(Class<?> candidtateClazz, Class<?> testCaseClass) {
        if (Modifier.isAbstract(candidtateClazz.getModifiers())) {
            return false;
        }
        return testCaseClass.isAssignableFrom(candidtateClazz);
    }

    private static boolean isJunit4Test(Class<?> candidateClass, Class<Annotation> testAnnotation) {
        if (Modifier.isAbstract(candidateClass.getModifiers())) {
            return false;
        }
        return hasConcreteTestMethods(candidateClass, testAnnotation);
    }

    private static boolean hasConcreteTestMethods(Class<?> candidateClass,
            Class<Annotation> testAnnotation) {
        for (final Method method : candidateClass.getMethods()) {
            final int modifiers = method.getModifiers();
            if (!Modifier.isAbstract(modifiers) && Modifier.isPublic(modifiers)
                    && method.getAnnotation(testAnnotation) != null) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("rawtypes")
    private static Object createJunit3TestSuite(JkClassLoader classLoader,
            Iterable<Class> testClasses) {
        final Class<?>[] classArray = JkUtilsIterable.arrayOf(testClasses, Class.class);
        final Class<?> testSuiteClass = classLoader.load(JUNIT3_TEST_SUITE_CLASS_NAME);
        try {
            final Constructor constructor = testSuiteClass.getConstructor(classArray.getClass());
            return constructor.newInstance((Object) classArray);
        } catch (final Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static JkTestSuiteResult fromJunit3Result(Properties properties, String suiteName,
            Object result, long durationInMillis) {
        final Integer runCount = JkUtilsReflect.invoke(result, "runCount");
        final Integer ignoreCount = 0;
        final Enumeration<Object> junitFailures = JkUtilsReflect.invoke(result, "failures");
        final Enumeration<Object> junitErrors = JkUtilsReflect.invoke(result, "errors");
        final List<JkTestSuiteResult.TestCaseFailure> failures = new ArrayList<JkTestSuiteResult.TestCaseFailure>();
        while (junitFailures.hasMoreElements()) {
            final Object junitFailure = junitFailures.nextElement();
            failures.add(JkTestSuiteResult.fromJunit3Failure(junitFailure));
        }
        while (junitErrors.hasMoreElements()) {
            final Object junitError = junitErrors.nextElement();
            failures.add(JkTestSuiteResult.fromJunit3Failure(junitError));
        }
        return new JkTestSuiteResult(properties, suiteName, runCount, ignoreCount, failures,
                durationInMillis);

    }

    @SuppressWarnings("rawtypes")
    private static String getSuiteName(Iterable<Class> classes) {
        final Iterator<Class> it = classes.iterator();
        if (!it.hasNext()) {
            return "";
        }
        final Class<?> firstClass = it.next();
        if (!it.hasNext()) {
            return firstClass.getName();
        }
        String[] result = firstClass.getPackage().getName().split("\\.");
        while (it.hasNext()) {
            final String[] packageName = it.next().getPackage().getName().split("\\.");
            final int min = Math.min(result.length, packageName.length);
            for (int i = 0; i < min; i++) {
                if (!result[i].equals(packageName[i])) {
                    if (i == 0) {
                        return "ALL";
                    }
                    result = Arrays.copyOf(result, i);
                    break;
                }
            }
        }
        return JkUtilsString.join(Arrays.asList(result), ".");
    }

}
