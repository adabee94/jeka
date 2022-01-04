package dev.jeka.plugins.springboot;

import dev.jeka.core.api.depmanagement.*;
import dev.jeka.core.api.depmanagement.artifact.JkArtifactId;
import dev.jeka.core.api.depmanagement.artifact.JkArtifactProducer;
import dev.jeka.core.api.depmanagement.artifact.JkStandardFileArtifactProducer;
import dev.jeka.core.api.depmanagement.resolution.JkDependencyResolver;
import dev.jeka.core.api.file.JkPathFile;
import dev.jeka.core.api.file.JkPathSequence;
import dev.jeka.core.api.java.JkClassLoader;
import dev.jeka.core.api.java.JkJavaProcess;
import dev.jeka.core.api.java.JkManifest;
import dev.jeka.core.api.java.JkUrlClassLoader;
import dev.jeka.core.api.project.JkProject;
import dev.jeka.core.api.project.JkProjectConstruction;
import dev.jeka.core.api.system.JkLog;
import dev.jeka.core.api.tooling.JkPom;
import dev.jeka.core.api.utils.JkUtilsAssert;
import dev.jeka.core.api.utils.JkUtilsIO;
import dev.jeka.core.api.utils.JkUtilsString;
import dev.jeka.core.tool.JkBean;
import dev.jeka.core.tool.JkDoc;
import dev.jeka.core.tool.builtins.project.ProjectJkBean;
import dev.jeka.core.tool.builtins.scaffold.ScaffoldJkBean;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

@JkDoc("Provides enhancement to Project plugin in order to produce a startable Springboot jar for your application.\n" +
        "The main produced artifact is the springboot one (embedding all dependencies) while the artifact classified as 'original' stands for the vanilla jar.\n" +
        "Dependency versions are resolved against BOM provided by Spring Boot team according Spring Boot version you use.")
public final class SpringbootJkBean extends JkBean {

    private static String DEFAULT_SPRINGBOOT_VERSION = "2.5.6";

    public static final JkArtifactId ORIGINAL_ARTIFACT = JkArtifactId.of("original", "jar");

    private static final String SPRINGBOOT_APPLICATION_ANNOTATION_NAME =
            "org.springframework.boot.autoconfigure.SpringBootApplication";

    private static final String BOM_COORDINATE = "org.springframework.boot:spring-boot-dependencies::pom:";

    public static final String SPRING_BOOT_VERSION_MANIFEST_ENTRY = "Spring-Boot-Version";

    @JkDoc("Version of Spring Boot version used to resolve dependency versions.")
    private String springbootVersion = DEFAULT_SPRINGBOOT_VERSION;

    @JkDoc("Class name holding main method to start Spring Boot. If null, Jeka will try to guess it at build time.")
    public String mainClassName;

    @JkDoc("If true, Spring Milestone or Snapshot Repository will be used to fetch non release version of spring modules")
    public boolean autoSpringRepo = true;

    @JkDoc("Command arg line to pass to springboot for #run method (e.g. '--server.port=8083 -Dspring.profiles.active=prod'")
    public String runArgs;

    @JkDoc("If true, the build create also the original jar file (without embedded dependencies")
    public boolean createOriginalJar;

    @JkDoc("For internal test purpose. If not null, scaffolded build class will reference this classpath for springboot plugin dependency.")
    public String scaffoldDefClasspath;

    private final ProjectJkBean projectBean;

    /**
     * Right after to be instantiated, plugin instances are likely to configured by the owning build.
     * Therefore, every plugin members that are likely to be configured by the owning build must be
     * initialized in the constructor.
     */
    protected SpringbootJkBean() {
        projectBean = getRuntime().getBean(ProjectJkBean.class);
    }

    public void setSpringbootVersion(String springbootVersion) {
        this.springbootVersion = springbootVersion;
    }

    @Override
    @JkDoc("Modifies the Java project from Project plugin in such this project produces a SpringBoot jar as the main artifact.")
    protected void postInit() {
        configure(projectBean.getProject());
    }

    @JkDoc("Run Springboot application from the generated jar")
    public void run() {
        JkArtifactProducer artifactProducer = projectBean.getProject().getArtifactProducer();
        JkArtifactId mainArtifactId = artifactProducer.getMainArtifactId();
        artifactProducer.makeMissingArtifacts(mainArtifactId);
        Path mainArtifactFile = artifactProducer.getMainArtifactPath();
        String[] args = new String[0];
        if (!JkUtilsString.isBlank(this.runArgs)) {
            args = JkUtilsString.translateCommandline(this.runArgs);
        }
        JkJavaProcess.ofJavaJar(mainArtifactFile, null).exec(args);
    }

    @JkDoc("Run Springboot application from the generated jar")
    public void runAsync() {
        JkArtifactProducer artifactProducer = projectBean.getProject().getArtifactProducer();
        JkArtifactId mainArtifactId = artifactProducer.getMainArtifactId();
        artifactProducer.makeMissingArtifacts(mainArtifactId);
        Path mainArtifactFile = artifactProducer.getMainArtifactPath();
        String[] args = new String[0];
        if (!JkUtilsString.isBlank(this.runArgs)) {
            args = JkUtilsString.translateCommandline(this.runArgs);
        }
        JkJavaProcess.ofJavaJar(mainArtifactFile, null).exec(args);
    }


    private void configure(JkProject project) {

        // Add spring snapshot or milestone repos if necessary
        JkDependencyResolver dependencyResolver = project.getConstruction().getDependencyResolver();
        JkVersion version = JkVersion.of(springbootVersion);
        if (autoSpringRepo && version.hasBlockAt(3)) {
            JkRepoSet repos = JkSpringRepos.getRepoForVersion(version.getBlock(3));
            dependencyResolver.addRepos(repos);
        }

        // run tests in forked mode
        project.getConstruction().getTesting().getTestProcessor().setForkingProcess(true);

        // Do not publish javadoc and sources
        project.includeJavadocAndSources(false, false);

        // Add springboot version to Manifest
        project.getConstruction().getManifest().addMainAttribute(SPRING_BOOT_VERSION_MANIFEST_ENTRY,
                this.springbootVersion);

        // resolve dependency versions upon springboot provided ones
        project.getConstruction().getCompilation().configureDependencies(deps -> deps
            .andBom(BOM_COORDINATE + springbootVersion));

        // define bootable jar as main artifact
        JkStandardFileArtifactProducer artifactProducer = project.getArtifactProducer();
        Consumer<Path> bootJar = this::createBootJar;
        artifactProducer.putMainArtifact(bootJar);

        // add original jar artifact
        if (createOriginalJar) {
            Consumer<Path> makeBinJar = project.getConstruction()::createBinJar;
            artifactProducer.putArtifact(ORIGINAL_ARTIFACT, makeBinJar);
        }

        // Add template build class to scaffold
        if (getRuntime().getBeanOptional(ScaffoldJkBean.class).isPresent()) {
            ScaffoldJkBean scaffold = getRuntime().getBean(ScaffoldJkBean.class);
            String code = JkUtilsIO.read(SpringbootJkBean.class.getClassLoader().getResource("snippet/Build.java"));
            String defClasspath = scaffoldDefClasspath != null ? scaffoldDefClasspath.replace("\\", "/") : "dev.jeka:springboot-plugin";
            code = code.replace("${dependencyDescription}", defClasspath);
            code = code.replace("${springbootVersion}", latestSpringbootVersion());
            final String jkClassCode = code;
            scaffold.getScaffolder().setJekaClassCodeProvider(() -> jkClassCode);
            scaffold.getScaffolder().getExtraActions()
                .append(this::scaffoldSample);
        }
    }

    /**
     * Creates the bootable jar at the standard location.
     */
    public void createBootJar() {
        JkStandardFileArtifactProducer artifactProducer = projectBean.getProject().getArtifactProducer();
        createBootJar(artifactProducer.getMainArtifactPath());
    }

    /**
     * Creates the bootable jar at the specified location.
     */
    public void createBootJar(Path target) {
        JkProjectConstruction construction = projectBean.getProject().getConstruction();
        JkStandardFileArtifactProducer artifactProducer = projectBean.getProject().getArtifactProducer();
        JkDependencyResolver dependencyResolver = construction.getDependencyResolver();
        JkVersionProvider versionProvider = projectBean.getProject().getConstruction().getDependencyResolver()
                .resolveBom(JkModuleDependency.of(BOM_COORDINATE + springbootVersion));
        JkVersion loaderVersion = versionProvider.getVersionOf(JkSpringModules.Boot.LOADER);
        JkDependencySet bootloaderDependency = JkDependencySet.of(JkModuleDependency.of(JkSpringModules.Boot.LOADER))
                .andBom(BOM_COORDINATE + springbootVersion);
        Path bootloader = dependencyResolver.resolve(bootloaderDependency).getFiles().getEntry(0);
        final JkPathSequence embeddedJars = construction.getDependencyResolver().resolve(
                construction.getRuntimeDependencies().normalised(projectBean.getProject().getDuplicateConflictStrategy()))
                .getFiles();
        Path originalJarPath = projectBean.getProject().getArtifactProducer().getArtifactPath(ORIGINAL_ARTIFACT);
        if (!Files.exists(originalJarPath)) {
            construction.createBinJar(originalJarPath);
        }
        createBootJar(originalJarPath, embeddedJars, bootloader,
                artifactProducer.getMainArtifactPath(), springbootVersion);
    }

    public ProjectJkBean projectBean() {
        return projectBean;
    }

    private static JkPom getSpringbootBom(JkDependencyResolver dependencyResolver, String springbootVersion) {
        JkModuleDependency moduleDependency = JkModuleDependency.of(
                "org.springframework.boot:spring-boot-dependencies::pom:" + springbootVersion);
        JkLog.info("Fetch Springboot dependency versions from " + moduleDependency);
        Path pomFile = dependencyResolver.resolve(moduleDependency).getFiles().getEntries().get(0);
        if (pomFile == null || !Files.exists(pomFile)) {
            throw new IllegalStateException(moduleDependency + " not found");
        }
        JkLog.info("Springboot dependency versions will be resolved from " + pomFile);
        return JkPom.of(pomFile);
    }


    public static void createBootJar(Path original, JkPathSequence libsToInclude, Path bootLoaderJar, Path targetJar,
                                     String springbootVersion) {
        JkUtilsAssert.argument(Files.exists(original), "Original jar not found at " + original);
        JkClassLoader classLoader = JkUrlClassLoader.of(original, ClassLoader.getSystemClassLoader().getParent())
                .toJkClassLoader();
        List<String> mainClasses = classLoader.findClassesHavingMainMethod();
        List<String> classWithSpringbootAppAnnotation = classLoader.findClassesMatchingAnnotations(
                annotationNames -> annotationNames.contains(SPRINGBOOT_APPLICATION_ANNOTATION_NAME));
        for (String name : mainClasses) {
            if (classWithSpringbootAppAnnotation.contains(name)) {
                SpringbootPacker.of(libsToInclude, bootLoaderJar, name,
                        springbootVersion).makeExecJar(original, targetJar);
                return;
            }
        }
        throw new IllegalStateException("No @SpringBootApplication class with main method found.");
    }

    @JkDoc("Scaffold a basic example application in package org.example")
    public void scaffoldSample() {
        String basePackage = "your/basepackage";
        Path sourceDir = projectBean.getProject().getConstruction().getCompilation().getLayout()
                .getSources().getRootDirsOrZipFiles().get(0);
        Path pack = sourceDir.resolve(basePackage);
        URL url = SpringbootJkBean.class.getClassLoader().getResource("snippet/Application.java");
        JkPathFile.of(pack.resolve("Application.java")).createIfNotExist().fetchContentFrom(url);
        url = SpringbootJkBean.class.getClassLoader().getResource("snippet/Controller.java");
        JkPathFile.of(pack.resolve("Controller.java")).createIfNotExist().fetchContentFrom(url);
        Path testSourceDir = projectBean.getProject().getConstruction().getTesting().getCompilation().getLayout()
                .getSources().getRootDirsOrZipFiles().get(0);
        pack = testSourceDir.resolve(basePackage);
        url = SpringbootJkBean.class.getClassLoader().getResource("snippet/ControllerIT.java");
        JkPathFile.of(pack.resolve("ControllerIT.java")).createIfNotExist().fetchContentFrom(url);
    }

    private String pluginVersion() {
        return JkManifest.of().loadFromClass(SpringbootJkBean.class)
                .getMainAttribute(JkManifest.IMPLEMENTATION_VERSION);
    }

    private String latestSpringbootVersion() {
        try {
            List<String> springbootVersions = projectBean.getProject().getConstruction().getDependencyResolver()
                    .searchVersions(JkSpringModules.Boot.STARTER_PARENT);
            return springbootVersions.stream()
                    .sorted(JkVersion.VERSION_COMPARATOR.reversed())
                    .findFirst().get();
        } catch (Exception e) {
            JkLog.warn(e.getMessage());
            JkLog.warn("Cannot find latest springboot version, choose default : " + DEFAULT_SPRINGBOOT_VERSION);
            return DEFAULT_SPRINGBOOT_VERSION;
        }
    }
}
