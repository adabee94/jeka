package dev.jeka.core;

import dev.jeka.core.api.depmanagement.artifact.JkArtifactId;
import dev.jeka.core.api.depmanagement.artifact.JkArtifactProducer;
import dev.jeka.core.api.file.JkPathFile;
import dev.jeka.core.api.file.JkPathTree;
import dev.jeka.core.api.file.JkPathTreeSet;
import dev.jeka.core.api.file.JkZipTree;
import dev.jeka.core.api.java.JkJavaVersion;
import dev.jeka.core.api.project.JkProject;
import dev.jeka.core.api.system.JkLog;
import dev.jeka.core.api.testing.JkTestProcessor;
import dev.jeka.core.api.testing.JkTestSelection;
import dev.jeka.core.api.tooling.JkGitProcess;
import dev.jeka.core.api.utils.JkUtilsPath;
import dev.jeka.core.tool.JkBean;
import dev.jeka.core.tool.JkConstants;
import dev.jeka.core.tool.JkInit;
import dev.jeka.core.tool.builtins.project.ProjectJkBean;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static dev.jeka.core.api.project.JkProject.JAVADOC_ARTIFACT_ID;
import static dev.jeka.core.api.project.JkProject.SOURCES_ARTIFACT_ID;

/**
 * Run main method to create full distrib.
 * For publishing in OSSRH the following options must be set : -ossrhPwd=Xxxxxx -pgp#secretKeyPassword=Xxxxxxx
 */
public class CoreBuild extends JkBean {

    private static final JkArtifactId DISTRIB_FILE_ID = JkArtifactId.of("distrib", "zip");

    private static final JkArtifactId WRAPPER_ARTIFACT_ID = JkArtifactId.of("wrapper", "jar");

    final ProjectJkBean projectBean = getBean(ProjectJkBean.class).configure(this::configure);

    public boolean runIT;

    private void configure(JkProject project)  {
        project
            .setJvmTargetVersion(JkJavaVersion.V8)
            .artifactProducer
                .putMainArtifact(this::doPackWithEmbeddedJar)
                .putArtifact(DISTRIB_FILE_ID, this::doDistrib)
                .putArtifact(WRAPPER_ARTIFACT_ID, this::doWrapper)
            .__
            .compiler
                .setForkedWithDefaultProcess()
            .__
            .prodCompilation
                .layout
                    .mixResourcesAndSources()
                .__
                .addJavaCompilerOptions("-Xlint:none","-g")
            .__
            .testing
                .testCompilation
                    .layout
                        .mixResourcesAndSources()
                    .__
                .__
                .testSelection
                    .addIncludePatterns(JkTestSelection.STANDARD_INCLUDE_PATTERN)
                    .addIncludePatternsIf(runIT, JkTestSelection.IT_INCLUDE_PATTERN)
                .__
                .testProcessor
                    .engineBehavior
                        .setProgressDisplayer(JkTestProcessor.JkProgressOutputStyle.ONE_LINE)
                    .__
                .__
            .__
            .packaging
                .manifest
                    .addMainClass("dev.jeka.core.tool.Main").__
                .javadocProcessor
                    .setDisplayOutput(false)
                    .addOptions("-notimestamp")
                .__
            .__
            .publication
                .setModuleId("dev.jeka:jeka-core")
                .setVersion(JkGitProcess.of()::getVersionFromTag)
                .maven
                    .pomMetadata
                        .setProjectName("jeka")
                        .addApache2License()
                        .setProjectDescription("Automate with plain Java code and nothing else.")
                        .addGithubDeveloper("djeang", "djeangdev@yahoo.fr");
    }

    private Path distribFolder() {
        return projectBean.getProject().getOutputDir().resolve("distrib");
    }

    private void doDistrib(Path distribFile) {
        final JkArtifactProducer artifactProducer = projectBean.getProject().artifactProducer;
        if (artifactProducer.getArtifactIds().contains(SOURCES_ARTIFACT_ID)) {
            artifactProducer.makeMissingArtifacts(artifactProducer.getMainArtifactId(),
                    SOURCES_ARTIFACT_ID, WRAPPER_ARTIFACT_ID);
        } else {
            artifactProducer.makeMissingArtifacts(artifactProducer.getMainArtifactId(), WRAPPER_ARTIFACT_ID);
        }
        final JkPathTree distrib = JkPathTree.of(distribFolder());
        distrib.deleteContent();
        JkLog.startTask("Create distrib");
        final List<Path> ivySourceLibs = JkPathTree.of(getBaseDir()).goTo("jeka/sources")
                .andMatching(true, "ivy-*.jar").getFiles();
        distrib
            .importFiles(getBaseDir().toAbsolutePath().normalize().getParent().resolve("LICENSE"))
            .importDir(getBaseDir().resolve("src/main/shell"))
            .importFiles(artifactProducer.getArtifactPath(artifactProducer.getMainArtifactId()))
            .importFiles(artifactProducer.getArtifactPath(WRAPPER_ARTIFACT_ID));

        if (artifactProducer.getArtifactIds().contains(SOURCES_ARTIFACT_ID)) {
            distrib.importFiles(artifactProducer.getArtifactPath(SOURCES_ARTIFACT_ID));
        }

        if (artifactProducer.getArtifactIds().contains(JAVADOC_ARTIFACT_ID)) {
            artifactProducer.makeMissingArtifacts(artifactProducer.getMainArtifactId(), JAVADOC_ARTIFACT_ID);
            // distrib.importFiles(artifactProducer.getArtifactPath(JAVADOC_ARTIFACT_ID));
        }
        JkPathFile.of(distrib.get("jeka")).setPosixExecPermissions();
        JkPathFile.of(distrib.get("wrapper/jekaw")).setPosixExecPermissions();
        if (!projectBean.getProject().testing.isSkipped() && runIT) {
            testScaffolding();
        }
        JkLog.info("Distribution created in " + distrib.getRoot());
        zipDistrib(distrib.getRoot(), distribFile);
        JkLog.info("Distribution zipped in " + distribFile);
        JkLog.endTask();
    }

    // see example here https://www.tabnine.com/code/java/methods/org.apache.commons.compress.archivers.zip.ZipArchiveEntry/setUnixMode
    private static void zipDistrib(Path distribDir, Path zipFile)  {
        try {
            ZipArchiveOutputStream out = new ZipArchiveOutputStream(zipFile);
            appendRecursively(distribDir, "", out);
            out.close();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void appendRecursively(final Path file, String relativeFile, final ZipArchiveOutputStream out) throws IOException {
        boolean isDirectory = Files.isDirectory(file);
        final ZipArchiveEntry entry = new ZipArchiveEntry(file, relativeFile);
        if (!isDirectory && Files.isExecutable(file)) {
            entry.setUnixMode(0777);  // necessary to mark it as executable inside the archive
        }
        boolean isRoot = isDirectory && relativeFile.isEmpty();
        if (!isRoot) {
            out.putArchiveEntry(entry);
            if (!isDirectory) {
                Files.copy(file, out);
            }
            out.closeArchiveEntry();
        }
        if (entry.isDirectory()) {
            String prefix = relativeFile.isEmpty() ? "" : relativeFile + "/";
            for (final String filename : file.toFile().list()) {
                appendRecursively(file.resolve(filename), prefix + filename, out);
            }
        }
    }

    void testScaffolding()  {
        JkLog.startTask("Run scaffold tests");
        new CoreScaffoldTester().run();
        JkLog.endTask();
    }

    private void doPackWithEmbeddedJar(Path targetJar) {

        // Main jar
        JkProject project = this.projectBean.getProject();
        project.packaging.createBinJar(targetJar);
        JkZipTree jarTree = JkZipTree.of(targetJar);

        // Create an embedded jar containing all 3rd party libs + embedded part code in jeka project
        Path embeddedJar = project.getOutputDir().resolve("embedded.jar");
        JkPathTree classTree = JkPathTree.of(project.prodCompilation.layout.resolveClassDir());
        Path providedLibs = getBaseDir().resolve(JkConstants.JEKA_DIR).resolve("libs/compile");
        JkPathTreeSet.of(classTree.andMatching("**/embedded/**/*")).zipTo(embeddedJar);
        JkZipTree.of(embeddedJar).andMatching( "META-INF/*.SF", "META-INF/*.RSA").deleteContent().close();

        // Name uniquely this embedded jar according its content
        String checksum = JkPathFile.of(embeddedJar).getChecksum("MD5");
        String embeddedFinalName = "jeka-embedded-" + checksum + ".jar";

        // Copy embedded jar into temp folder and remove embedded part code from jeka classes
        jarTree.goTo("META-INF").importFile(embeddedJar, embeddedFinalName);
        Path embeddedNaneFile = jarTree.get("META-INF/jeka-embedded-name");
        JkUtilsPath.deleteIfExists(embeddedNaneFile);
        JkPathFile.of(embeddedNaneFile).write(embeddedFinalName.getBytes(Charset.forName("utf-8")));
        jarTree.andMatching( "**/embedded/**").deleteContent();
        jarTree.close();

        // Cleanup
        JkUtilsPath.deleteIfExists(embeddedJar);
    }

    private void doWrapper(Path wrapperJar) {
        projectBean.getProject().prodCompilation.runIfNeeded();
        JkPathTree.of(projectBean.getProject().prodCompilation.layout
                .resolveClassDir()).andMatching("dev/jeka/core/wrapper/**").zipTo(wrapperJar);
    }

    public void cleanPack() {
        cleanOutput(); projectBean.pack();
    }

    // This method has to be run in dev.jeka.core (this module root) working directory
    public static void main(String[] args) {
        JkInit.instanceOf(CoreBuild.class, args).cleanPack();
    }

    public static class RunBuildAndIT {
        public static void main(String[] args) {
            CoreBuild coreBuild = JkInit.instanceOf(CoreBuild.class, args, "-runIT");
            coreBuild.projectBean.pack();
        }
    }

}
