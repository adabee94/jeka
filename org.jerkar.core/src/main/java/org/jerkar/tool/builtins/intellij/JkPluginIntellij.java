package org.jerkar.tool.builtins.intellij;

import org.jerkar.api.depmanagement.JkDependencySet;
import org.jerkar.api.ide.intellij.JkImlGenerator;
import org.jerkar.api.project.java.JkJavaProject;
import org.jerkar.api.system.JkLog;
import org.jerkar.api.utils.JkUtilsPath;
import org.jerkar.tool.*;
import org.jerkar.tool.builtins.java.JkJavaProjectBuild;
import org.jerkar.tool.builtins.scaffold.JkPluginScaffold;

import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

@JkDoc("Generation of Idea Intellij metadata files (*.iml and modules.xml).")
@JkDocPluginDeps(JkPluginScaffold.class)
public final class JkPluginIntellij extends JkPlugin {

    @JkDoc("If true, dependency paths will be expressed relatively to $JERKAR_REPO$ and $JERKAR_HOME$ path variable instead of absolute paths.")
    public boolean useVarPath = false;

    @JkDoc("If true, the project dependencies are not taken in account to generate iml, only build class dependencies are.")
    public boolean onlyBuildDependencies = false;

    private final JkPluginScaffold scaffold;

    protected JkPluginIntellij(JkBuild build) {
        super(build);
        scaffold = build.plugins().get(JkPluginScaffold.class);
    }

    /** Generates Idea [my-module].iml file */
    @JkDoc("Generates Idea [my-module].iml file.")
    public void generateIml() {

        final JkImlGenerator generator;
        if (build instanceof JkJavaProjectBuild) {
            final JkJavaProjectBuild projectBuild = (JkJavaProjectBuild) build;
            generator = new JkImlGenerator(projectBuild.java().project());
        } else {
            generator = new JkImlGenerator(build.baseDir());
        }
        final List<Path> depProjects = new LinkedList<>();
        for (final JkBuild depBuild : build.importedBuilds().directs()) {
            depProjects.add(depBuild.baseTree().root());
        }
        generator.setUseVarPath(useVarPath);
        generator.setBuildDependencies(build.buildDependencyResolver(), build.buildDependencies());

        generator.setImportedBuildProjects(depProjects);
        if (build instanceof JkJavaProjectBuild) {
            final JkJavaProjectBuild projectBuild = (JkJavaProjectBuild) build;
            JkJavaProject project = projectBuild.java().project();
            if (!onlyBuildDependencies) {
                generator.setDependencies(project.maker().getDependencyResolver(), project.getDependencies());
            } else {
                generator.setDependencies(project.maker().getDependencyResolver(), JkDependencySet.of());
            }
            generator.setSourceJavaVersion(project.getSourceVersion());
            generator.setForceJdkVersion(true);
        }
        final String xml = generator.generate();
        final Path imlFile = build.baseDir().resolve(".idea").resolve(
                build.baseDir().getFileName().toString() + ".iml");
        JkUtilsPath.deleteIfExists(imlFile);
        JkUtilsPath.write(imlFile, xml.getBytes(Charset.forName("UTF-8")));
        JkLog.info("Iml file generated at " + imlFile);
    }

    /** Generate modules.xml files */
    @JkDoc("Generates ./idea/modules.xml file.")
    public void generateModulesXml() {
        final Path current = build.baseTree().root();
        final Iterable<Path> imls = build.baseTree().accept("**.iml").files();
        final ModulesXmlGenerator modulesXmlGenerator = new ModulesXmlGenerator(current, imls);
        modulesXmlGenerator.generate();
    }

    @JkDoc("Generates iml files on this folder and its descendant recursively.")
    public void generateAllIml() {
        final Iterable<Path> folders = build.baseTree()
                .accept("**/" + JkConstants.BUILD_DEF_DIR, JkConstants.BUILD_DEF_DIR)
                .refuse("**/build/output/**")
                .stream().collect(Collectors.toList());
        for (final Path folder : folders) {
            final Path projectFolder = folder.getParent().getParent();
            JkLog.execute("Generating iml file on " + projectFolder, () ->
                Main.exec(projectFolder, "idea#generateIml"));
        }
    }

    @JkDoc("Shorthand for idea#generateAllIml + idea#generateModulesXml.")
    public void generateAll() {
        generateAllIml();
        generateModulesXml();
    }

    @JkDoc("Adds *.iml generation to scaffolding.")
    @Override
    protected void activate() {
        scaffold.addExtraAction(this::generateIml);
    }
}