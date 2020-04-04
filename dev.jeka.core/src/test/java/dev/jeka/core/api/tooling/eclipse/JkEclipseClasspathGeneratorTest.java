package dev.jeka.core.api.tooling.eclipse;

import dev.jeka.core.api.depmanagement.JkComputedDependency;
import dev.jeka.core.api.depmanagement.JkDependencySet;
import dev.jeka.core.api.depmanagement.JkPopularModules;
import dev.jeka.core.api.depmanagement.JkScopedDependency;
import dev.jeka.core.api.file.JkPathTree;
import dev.jeka.core.api.java.project.JkCompileLayout;
import dev.jeka.core.api.java.project.JkJavaProject;
import org.junit.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.Assert.assertEquals;

// Test on a project structure as Base -> Core -> Desktop
public class JkEclipseClasspathGeneratorTest {

    static final String ZIP_NAME = "sample-multi-scriptless.zip";

    @Test
    public void generate() throws Exception {
        final Path top = unzipToDir(ZIP_NAME);
        // JkLog.silent(true);

        final JkJavaProject baseProject = JkJavaProject.of()
            .setBaseDir(top.resolve("base"))
            .getSteps()
                .getCompilation()
                    .getLayout()
                        .emptySources().addSource("src")
                        .emptyResources().addResource("res").__.__
                .getTesting()
                    .getTestCompilation()
                        .getLayout()
                            .emptySources().addSource("test")
                            .emptyResources().addSource("res-test").__.__.__.__
                .getDependencyManagement()
                .addDependencies(JkDependencySet.of()
                    .and(JkPopularModules.APACHE_HTTP_CLIENT, "4.5.6")).__;
        final JkEclipseClasspathGenerator baseGenerator = JkEclipseClasspathGenerator.of(baseProject.getJavaIdeSupport())
            .setUsePathVariables(true)
            .setRunDependencies(baseProject.getDependencyManagement().getResolver(),
                    JkDependencySet.of().and(JkPopularModules.GUAVA, "21.0"));
        final String baseClasspath = baseGenerator.generate();
        System.out.println("\nbase .classpath");
        System.out.println(baseClasspath);

        final JkJavaProject coreProject = JkJavaProject.of()
            .setBaseDir(top.resolve("core"))
            .getDependencyManagement()
                .addDependencies(JkDependencySet.of().and(baseProject)).__
            .getSteps()
                .getTesting()
                    .getTestProcessor()
                        .setForkingProcess(true).__.__.__;
        final JkEclipseClasspathGenerator coreGenerator =
                JkEclipseClasspathGenerator.of(coreProject.getJavaIdeSupport());
        final String coreClasspath = coreGenerator.generate();
        System.out.println("\ncore .classpath");
        System.out.println(coreClasspath);

        final JkJavaProject desktopProject = JkJavaProject.of()
            .setBaseDir(top.resolve("desktop"))
            .getDependencyManagement()
                .addDependencies(JkDependencySet.of().and(coreProject)).__;
        desktopProject.getArtifactProducer().makeAllArtifacts();
        final JkEclipseClasspathGenerator desktopGenerator =
                JkEclipseClasspathGenerator.of(desktopProject.getJavaIdeSupport());
        final String result2 = desktopGenerator.generate();
        System.out.println("\ndesktop .classpath");
        System.out.println(result2);


        // ----------------- Now, try to apply generated .classpath to projects and compare if it matches

        final JkEclipseClasspathApplier classpathApplier = new JkEclipseClasspathApplier(false);

        Path base = baseProject.getBaseDir();
        final JkJavaProject baseProject2 = JkJavaProject.of().setBaseDir(base);

        Files.write(base.resolve(".classpath"), baseClasspath.getBytes(Charset.forName("UTF-8")));

        JkEclipseProjectGenerator.ofJavaNature("base").writeTo(base.resolve(".project"));
        classpathApplier.apply(baseProject2);
        System.out.println(baseProject2.getDependencyManagement().getDependencies().toList());
        final JkCompileLayout base2Layout = baseProject2.getSteps().getCompilation().getLayout();
        final JkCompileLayout baseLayout = baseProject.getSteps().getCompilation().getLayout();
        assertEquals(baseLayout.getBaseDir(), base2Layout.getBaseDir());
        final List<Path> srcFiles = base2Layout.resolveSources().getFiles();
        assertEquals(2, srcFiles.size());
        assertEquals("Base.java", srcFiles.get(0).getFileName().toString());
        final List<Path> resFiles = base2Layout.resolveResources().getFiles();
        assertEquals(1, resFiles.size());
        assertEquals("base.txt", resFiles.get(0).getFileName().toString());
        assertEquals(4, baseProject2.getDependencyManagement().getDependencies().toList().size());

        Path core = baseProject.getBaseDir();
        final JkJavaProject coreProject2 = JkJavaProject.of().setBaseDir(core);
        Files.write(core.resolve(".classpath"), coreClasspath.getBytes(Charset.forName("utf-8")));
        //JkUtilsFile.writeString(new File(core, ".classpath"), coreClasspath, false);
        JkEclipseProjectGenerator.ofJavaNature("core").writeTo(core.resolve(".project"));
        classpathApplier.apply(coreProject2);
        final List<JkScopedDependency> coreDeps2 = coreProject2.getDependencyManagement().getDependencies().toList();
        assertEquals(1, coreDeps2.size());
        final JkComputedDependency baseProjectDep = (JkComputedDependency) coreDeps2.get(0).getDependency();
        assertEquals(base, baseProjectDep.getIdeProjectBaseDir());

        //JkPathTree.of(top).deleteContent();
    }

    private static Path unzipToDir(String zipName) throws IOException, URISyntaxException {
        final Path dest = Files.createTempDirectory(JkEclipseClasspathGeneratorTest.class.getName());
        final Path zip = Paths.get(JkEclipseClasspathGeneratorTest.class.getResource(zipName).toURI());
        JkPathTree.ofZip(zip).copyTo(dest);
        System.out.println("unzipped in " + dest);
        return dest;
    }

}
