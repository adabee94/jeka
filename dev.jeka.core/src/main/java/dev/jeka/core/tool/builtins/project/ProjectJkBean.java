package dev.jeka.core.tool.builtins.project;

import dev.jeka.core.api.depmanagement.JkDependencySet;
import dev.jeka.core.api.depmanagement.JkRepo;
import dev.jeka.core.api.depmanagement.JkRepoProperties;
import dev.jeka.core.api.depmanagement.JkRepoSet;
import dev.jeka.core.api.depmanagement.artifact.JkArtifactId;
import dev.jeka.core.api.depmanagement.artifact.JkStandardFileArtifactProducer;
import dev.jeka.core.api.depmanagement.resolution.JkDependencyResolver;
import dev.jeka.core.api.depmanagement.resolution.JkResolveResult;
import dev.jeka.core.api.depmanagement.resolution.JkResolvedDependencyNode;
import dev.jeka.core.api.file.JkPathFile;
import dev.jeka.core.api.function.JkConsumers;
import dev.jeka.core.api.java.JkJavaCompiler;
import dev.jeka.core.api.java.JkJavaProcess;
import dev.jeka.core.api.java.JkJavaVersion;
import dev.jeka.core.api.java.JkJdks;
import dev.jeka.core.api.project.JkCompileLayout;
import dev.jeka.core.api.project.JkIdeSupport;
import dev.jeka.core.api.project.JkProject;
import dev.jeka.core.api.system.JkLog;
import dev.jeka.core.api.system.JkProperties;
import dev.jeka.core.api.testing.JkTestProcessor;
import dev.jeka.core.api.utils.JkUtilsIO;
import dev.jeka.core.api.utils.JkUtilsPath;
import dev.jeka.core.api.utils.JkUtilsString;
import dev.jeka.core.tool.JkBean;
import dev.jeka.core.tool.JkConstants;
import dev.jeka.core.tool.JkDoc;
import dev.jeka.core.tool.JkInjectProperty;
import dev.jeka.core.tool.builtins.scaffold.JkScaffolder;
import dev.jeka.core.tool.builtins.scaffold.ScaffoldJkBean;
import org.w3c.dom.Document;

import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Plugin for building JVM language based projects. It comes with a {@link JkProject} pre-configured with {@link JkProperties}.
 * and a decoration for scaffolding.
 */
@JkDoc("Provides a configured JkProject instance for building JVM based projects.")
public class ProjectJkBean extends JkBean implements JkIdeSupport.JkSupplier {

    /**
     * Options for the packaging tasks (jar creation). These options are injectable from command line.
     */
    public final JkPackOptions pack = new JkPackOptions();

    /**
     * Options for run tasks
     */
    public final JkRunOptions run = new JkRunOptions();

    /**
     * Options for the testing tasks. These options are injectable from command line.
     */
    public final JkTestOptions test = new JkTestOptions();

    public final JkScaffoldOptions scaffold = new JkScaffoldOptions();

    public final JkLayoutOptions layout = new JkLayoutOptions();

    @JkDoc("Extra arguments to be passed to the compiler (e.g. -Xlint:unchecked).")
    public String compilerExtraArgs;

    @JkDoc("The output file for the xml dependency description.")
    public Path outputFile;

    @JkDoc("The target JVM version for compiled files.")
    @JkInjectProperty("jeka.java.version")
    public String javaVersion;

    private final ScaffoldJkBean scaffoldJkBean = getBean(ScaffoldJkBean.class).configure(this::configure);

    private JkProject project;

    private JkConsumers<JkProject, Void> projectConfigurators = JkConsumers.of();

    private JkProject createProject() {
        Path baseDir = getBaseDir();
        JkProject project = JkProject.of().setBaseDir(baseDir);
        if (!JkLog.isAcceptAnimation()) {
            project.testing.testProcessor.engineBehavior.setProgressDisplayer(
                    JkTestProcessor.JkProgressOutputStyle.SILENT);
        }
        if (!JkUtilsString.isBlank(this.javaVersion)) {
            JkJavaVersion version = JkJavaVersion.of(this.javaVersion);
            project.setJvmTargetVersion(version);
        }
        applyRepo(project);
        if (layout.style == JkLayoutOptions.Style.SIMPLE) {
            project.flatFacade().useSimpleLayout();
        }
        if (layout.mixSourcesAndResources) {
            project.flatFacade().mixResourcesAndSources();
        }
        projectConfigurators.accept(project);
        JkJavaCompiler compiler = project.compiler;
        if (!compiler.isToolOrProcessSpecified()) {
            compiler.setGuessHints(jdks(), project.getJvmTargetVersion(), true);
        }
        this.applyPostSetupOptions(project);
        return project;
    }

    private JkJdks jdks() {
        return JkJdks.ofJdkHomeProps(getRuntime().getProperties().getAllStartingWith("jeka.jdk.", false));
    }

    private void applyRepo(JkProject project) {
        JkRepoProperties repoProperties = JkRepoProperties.of(this.getRuntime().getProperties());
        JkRepoSet mavenPublishRepos = repoProperties.getPublishRepository();
        if (mavenPublishRepos.getRepos().isEmpty()) {
            mavenPublishRepos = mavenPublishRepos.and(JkRepo.ofLocal());
        }
        project.publication.maven.setPublishRepos(mavenPublishRepos);
        JkRepoSet ivyPulishRepos = repoProperties.getPublishRepository();
        if (ivyPulishRepos.getRepos().isEmpty()) {
            ivyPulishRepos = ivyPulishRepos.and(JkRepo.ofLocal());
        }
        project.publication.ivy.setRepos(ivyPulishRepos);
        final JkRepoSet downloadRepos = repoProperties.getDownloadRepos();
        JkDependencyResolver resolver = project.dependencyResolver;
        resolver.setRepos(resolver.getRepos().and(downloadRepos));
    }

    private void applyPostSetupOptions(JkProject aProject) {
        final JkStandardFileArtifactProducer artifactProducer = aProject.artifactProducer;
        JkArtifactId sources = JkProject.SOURCES_ARTIFACT_ID;
        if (pack.sources != null && !pack.sources) {
            artifactProducer.removeArtifact(sources);
        } else if (pack.sources != null && pack.sources && !artifactProducer.getArtifactIds().contains(sources)) {
            Consumer<Path> sourceJar = aProject.packaging::createSourceJar;
            artifactProducer.putArtifact(sources, sourceJar);
        }
        JkArtifactId javadoc = JkProject.JAVADOC_ARTIFACT_ID;
        if (pack.javadoc != null && !pack.javadoc) {
            artifactProducer.removeArtifact(javadoc);
        } else if (pack.javadoc != null && pack.javadoc && !artifactProducer.getArtifactIds().contains(javadoc)) {
            Consumer<Path> javadocJar = aProject.packaging::createJavadocJar;
            artifactProducer.putArtifact(javadoc, javadocJar);
        }
        JkTestProcessor testProcessor = aProject.testing.testProcessor;
        testProcessor.setJvmHints(jdks(), aProject.getJvmTargetVersion());
        if (test.fork != null && test.fork && testProcessor.getForkingProcess() == null) {
            final JkJavaProcess javaProcess = JkJavaProcess.ofJava(JkTestProcessor.class.getName())
                    .addJavaOptions(this.test.jvmOptions);
            if (project.getJvmTargetVersion() != null &&
                    !JkJavaVersion.ofCurrent().equals(project.getJvmTargetVersion())) {
                Path javaHome = jdks().getHome(project.getJvmTargetVersion());
                if (javaHome != null) {
                    JkLog.trace("Tests are configured to run using JDK %s", javaHome);
                    javaProcess.setCommand(javaHome.resolve("bin/java").toString());
                }
            }
            testProcessor.setForkingProcess(javaProcess);
        } else if (test.fork != null && !test.fork && testProcessor.getForkingProcess() != null) {
            testProcessor.setForkingProcess(false);
        }
        if (test.fork == null)
        if (test.skip != null) {
            aProject.testing.setSkipped(test.skip);
        }
        if (this.compilerExtraArgs != null) {
            aProject.prodCompilation.addJavaCompilerOptions(JkUtilsString.translateCommandline(this.compilerExtraArgs));
        }
    }

    private void configure(JkScaffolder scaffolder) {
        JkProject configuredProject = getProject();
        scaffolder.setJekaClassCodeProvider( () -> {
            final String snippet;
            if (scaffold.template == JkScaffoldOptions.Template.CODE_LESS) {
                return null;
            }
            if (scaffold.template == JkScaffoldOptions.Template.NORMAL) {
                snippet = "buildclass.snippet";
            } else if (scaffold.template == JkScaffoldOptions.Template.PLUGIN) {
                snippet = "buildclassplugin.snippet";
            } else {
                snippet = "buildclassfacade.snippet";
            }
            String template = JkUtilsIO.read(ProjectJkBean.class.getResource(snippet));
            String baseDirName = getBaseDir().getFileName().toString();
            return template.replace("${group}", baseDirName).replace("${name}", baseDirName);
        });
        scaffolder.setClassFilename("Build.java");
        scaffolder.extraActions.append( () -> this.scaffold.scaffoldProjectStructure(configuredProject));
    }



    // ------------------------------ Accessors -----------------------------------------

    public JkProject getProject() {
        return Optional.ofNullable(project).orElseGet(() -> {
            project = createProject();
            return project;
        });
    }

    public ProjectJkBean configure(Consumer<JkProject> projectConfigurator) {
        this.projectConfigurators.append(projectConfigurator);
        return this;
    }

    // ------------------------------- command line methods -----------------------------

    @JkDoc("Delete the content of jeka/output directory and might execute extra clean actions")
    public void clean() {
        super.cleanOutput();
        getProject().executeCleanExtraActions();
    }

    @JkDoc("Generate sources")
    public void generateSources() {
        getProject().prodCompilation.generateSources();
    }

    @JkDoc("Performs compilation and resource processing.")
    public void compile() {
        getProject().prodCompilation.run();
    }

    @JkDoc("Compiles and run tests defined within the project (typically Junit tests).")
    public void test() {    //NOSONAR
        getProject().testing.run();
    }

    @JkDoc("Generates from scratch artifacts defined through 'pack' options (Perform compilation and testing if needed).  " +
            "\nDoes not re-generate artifacts already generated : " +
            "execute 'clean java#pack' to re-generate artifacts.")
    public void pack() {   //NOSONAR
        getProject().artifactProducer.makeAllMissingArtifacts();
    }

    /**
     * Displays the resolved dependency tree on the console.
     */
    @JkDoc("Displays resolved dependency tree on console.")
    public final void showDependencies() {
        showDependencies("compile", getProject().prodCompilation.getDependencies());
        showDependencies("runtime", getProject().packaging.getRuntimeDependencies());
        showDependencies("test", getProject().testing.testCompilation.getDependencies());
    }

    private void showDependencies(String purpose, JkDependencySet deps) {
        JkLog.info("\nDependencies for " + purpose + " : ");
        final JkResolveResult resolveResult = this.getProject().dependencyResolver.resolve(deps);
        final JkResolvedDependencyNode tree = resolveResult.getDependencyTree();
        JkLog.info("------------------------------");
        JkLog.info(String.join("\n", tree.toStrings()));
        JkLog.info("");
    }

    @JkDoc("Displays resolved dependency tree in xml")
    public void showDependenciesXml() {
        Transformer transformer = null;
        try {
            transformer = TransformerFactory.newInstance().newTransformer();  //NOSONAR
        } catch (TransformerConfigurationException e) {
            throw new RuntimeException(e);
        }
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        Writer out;
        if (outputFile == null) {
            out = new PrintWriter(JkLog.getOutPrintStream());
        } else {
            try {
                JkPathFile.of(outputFile).createIfNotExist();
                out = new FileWriter(outputFile.toFile());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        Document document = getProject().getDependenciesAsXml();
        try {
            transformer.transform(new DOMSource(document), new StreamResult(out));
        } catch (TransformerException e) {
            throw new RuntimeException(e);
        }
    }

    @JkDoc("Displays information about the Java project to build.")
    public void info() {
        JkLog.info(this.getProject().getInfo());
        JkLog.info("\nExecute 'java#showDependencies' to display details on dependencies.");
    }

    @JkDoc("Run the generated jar.")
    public void runJar() {
        this.run.runJar();
    }

    @JkDoc("Publishes produced artifacts to configured repository.")
    public void publish() {
        JkLog.info("Publish " + getProject() + " ...");
        getProject().publication.publish();
    }

    @JkDoc("Publishes produced artifacts to local repository.")
    public void publishLocal() {
        getProject().publication.publishLocal();
    }


    @Override
    public JkIdeSupport getJavaIdeSupport() {
        return getProject().getJavaIdeSupport();
    }


    /**
     * Standard options for packaging java projects.
     */
    public static class JkPackOptions {

        /** When true, javadoc is created and packed in a jar file.*/
        @JkDoc("If true, javadoc jar is added in the list of artifact to produce/publish.")
        public Boolean javadoc;

        /** When true, sources are packed in a jar file.*/
        @JkDoc("If true, sources jar is added in the list of artifact to produce/publish.")
        public Boolean sources;

    }

    /**
     * Options about tests
     */
    public static final class JkTestOptions {

        /** Turn it on to skip tests. */
        @JkDoc("If true, tests are not run.")
        public Boolean skip;

        /** Turn it on to run tests in a withForking process. */
        @JkDoc("If true, tests will be executed in a forked process.")
        public Boolean fork;

        /** Argument passed to the JVM if tests are withForking. Example : -Xms2G -Xmx2G */
        @JkDoc("Argument passed to the JVM if tests are executed in a forked process. E.g. -Xms2G -Xmx2G.")
        public String jvmOptions;

    }

    public static class JkLayoutOptions {

        enum Style {SIMPLE, MAVEN}

        @JkDoc("Style of directory source structure (src/main/java or just src)")
        public Style style = Style.MAVEN;

        @JkDoc("If true, Resource files are located in same folder than Java code.")
        public boolean mixSourcesAndResources = false;

    }

    public class JkRunOptions {

        @JkDoc("JVM options to use when running generated jar")
        public String jvmOptions;

        @JkDoc("Program arguments to use when running generated jar")
        public String programArgs;

        @JkDoc("If true, the resolved runtime classpath will be used when running the generated jar.\n" +
                "If the generated jar is a Uber jar or contains all the needed dependencies, leave it to 'false'")
        public boolean useRuntimeDepsForClasspath;

        void runJar() {
            Path jarPath = ProjectJkBean.this.getProject().artifactProducer.getMainArtifactPath();
            if (!Files.exists(jarPath)) {
                ProjectJkBean.this.getProject().artifactProducer.makeMainArtifact();
            }
            JkJavaProcess javaProcess  = JkJavaProcess.ofJavaJar(jarPath, null).setLogCommand(JkLog.isVerbose())
                    .addJavaOptions(JkUtilsString.translateCommandline(jvmOptions))
                    .addParams(JkUtilsString.translateCommandline(programArgs));
            if (useRuntimeDepsForClasspath) {
                javaProcess
                        .setClasspath(ProjectJkBean.this.getProject().packaging.resolveRuntimeDependencies().getFiles());
            }
            Consumer<Process> processConsumer = process ->
                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    JkLog.info("Destroy process " + process);
                    process.destroyForcibly();
                }));
            javaProcess.exec(processConsumer);
        }
    }

    public static class JkScaffoldOptions {

        public enum Template {

            NORMAL, SIMPLE_FACADE, PLUGIN, CODE_LESS

        }

        @JkDoc("The template used for scaffolding the build class")
        public Template template = Template.SIMPLE_FACADE;

        @JkDoc("Generate jeka/project-libs sub-folders for hosting local libraries")
        public boolean generateLocalLibsFolders = true;

        public final DependenciesTxt dependenciesTxt = new DependenciesTxt();

        private void scaffoldProjectStructure(JkProject configuredProject) {
            JkLog.info("Create source directories.");
            JkCompileLayout prodLayout = configuredProject.prodCompilation.layout;
            prodLayout.resolveSources().toList().stream().forEach(tree -> tree.createIfNotExist());
            prodLayout.resolveResources().toList().stream().forEach(tree -> tree.createIfNotExist());
            JkCompileLayout testLayout = configuredProject.testing.testCompilation.layout;
            testLayout.resolveSources().toList().stream().forEach(tree -> tree.createIfNotExist());
            testLayout.resolveResources().toList().stream().forEach(tree -> tree.createIfNotExist());

            // Create specific files and folders
            String dependenciesTxtContent = dependenciesTxtContent();
            JkPathFile.of(configuredProject.getBaseDir().resolve(JkConstants.JEKA_DIR)
                    .resolve(JkConstants.PROJECT_DEPENDENCIES_TXT_FILE))
                    .createIfNotExist()
                    .write(dependenciesTxtContent.getBytes(StandardCharsets.UTF_8));
            Path libs = configuredProject.getBaseDir().resolve(JkConstants.JEKA_DIR)
                    .resolve(JkConstants.PROJECT_LIBS_DIR);
            if (generateLocalLibsFolders) {
                JkPathFile.of(libs.resolve("readme.txt"))
                        .fetchContentFrom(ProjectJkBean.class.getResource("libs-readme.txt"));
                JkUtilsPath.createDirectories(libs.resolve("regular"));
                JkUtilsPath.createDirectories(libs.resolve("compile-only"));
                JkUtilsPath.createDirectories(libs.resolve("runtime-only"));
                JkUtilsPath.createDirectories(libs.resolve("test"));
                JkUtilsPath.createDirectories(libs.resolve("sources"));
            }

            // This is special scaffolding for project pretending to be plugins for Jeka
            if (this.template == Template.PLUGIN) {
                Path breakinkChangeFile = configuredProject.getBaseDir().resolve("breaking_versions.txt");
                String text = "## Next line means plugin 2.4.0.RC11 is not compatible with Jeka 0.9.0.RELEASE and above\n" +
                        "## 2.4.0.RC11 : 0.9.0.RELEASE   (remove this comment and leading '##' to be effective)";
                JkPathFile.of(breakinkChangeFile).write(text);
                Path sourceDir =
                        configuredProject.prodCompilation.layout.getSources().toList().get(0).getRoot();
                String pluginCode = JkUtilsIO.read(ProjectJkBean.class.getResource("pluginclass.snippet"));
                JkPathFile.of(sourceDir.resolve("your/basepackage/XxxxxJkBean.java"))
                        .createIfNotExist()
                        .write(pluginCode.getBytes(StandardCharsets.UTF_8));
            }
        }

        private String dependenciesTxtContent() {
            List<String> lines = JkUtilsIO.readAsLines(ProjectJkBean.class.getResourceAsStream("dependencies.txt"));
            StringBuilder sb = new StringBuilder();
            for (String line : lines) {
                sb.append(line).append("\n");
                if (line.startsWith("==== COMPILE") && !JkUtilsString.isBlank(this.dependenciesTxt.compile)) {
                    Arrays.stream(this.dependenciesTxt.compile.split(",")).forEach(extraDep ->
                            sb.append(extraDep.trim()).append("\n")
                    );
                }
                if (line.startsWith("==== RUNTIME") && !JkUtilsString.isBlank(this.dependenciesTxt.runtime)) {
                    Arrays.stream(this.dependenciesTxt.runtime.split(",")).forEach(extraDep ->
                            sb.append(extraDep.trim()).append("\n")
                    );
                }
                if (line.startsWith("==== TEST") && !JkUtilsString.isBlank(this.dependenciesTxt.test)) {
                    Arrays.stream(this.dependenciesTxt.test.split(",")).forEach(extraDep ->
                            sb.append(extraDep.trim()).append("\n")
                    );
                }
            }
            return sb.toString();
        }

        public static class DependenciesTxt {

            @JkDoc("Comma separated dependencies to include in project-dependencies.txt COMPILE section")
            public String compile;

            @JkDoc("Comma separated dependencies to include in project-dependencies.txt RUNTIME section")
            public String runtime;

            @JkDoc("Comma separated dependencies to include in project-dependencies.txt TEST section")
            public String test;

        }


    }

}
