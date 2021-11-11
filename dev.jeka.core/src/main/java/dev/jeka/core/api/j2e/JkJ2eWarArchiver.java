package dev.jeka.core.api.j2e;

import dev.jeka.core.api.depmanagement.artifact.JkArtifactId;
import dev.jeka.core.api.depmanagement.artifact.JkStandardFileArtifactProducer;
import dev.jeka.core.api.file.JkPathFile;
import dev.jeka.core.api.file.JkPathTree;
import dev.jeka.core.api.project.JkProject;
import dev.jeka.core.api.system.JkLog;
import dev.jeka.core.api.utils.JkUtilsPath;
import dev.jeka.core.tool.JkDoc;
import dev.jeka.core.tool.JkDocPluginDeps;
import dev.jeka.core.tool.builtins.project.JkPluginProject;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public class JkJ2eWarArchiver {

    private Path classDir;

    private Path webappDir;

    private List<Path> libs;

    private Path extraStaticResourceDir;

    private JkJ2eWarArchiver() {
    }

    public static JkJ2eWarArchiver of() {
        return new JkJ2eWarArchiver();
    }


    public JkJ2eWarArchiver setClassDir(Path classDir) {
        this.classDir = classDir;
        return this;
    }

    public JkJ2eWarArchiver setWebappDir(Path webappDir) {
        this.webappDir = webappDir;
        return this;
    }

    public JkJ2eWarArchiver setLibs(List<Path> libs) {
        this.libs = libs;
        return this;
    }

    public JkJ2eWarArchiver setExtraStaticResourceDir(Path extraStaticResourceDir) {
        this.extraStaticResourceDir = extraStaticResourceDir;
        return this;
    }

    public void generateWarDir(Path destDir) {
        JkPathTree webappTree = webappDir != null ? JkPathTree.of(webappDir) : null;
        if (webappTree == null || !webappTree.exists() || !webappTree.containFiles()) {
            JkLog.warn(webappTree.getRoot().toString() + " is empty or does not exists.");
        } else {
            webappTree.copyTo(destDir);
        }
        if (extraStaticResourceDir != null && Files.exists(extraStaticResourceDir)) {
            JkPathTree.of(extraStaticResourceDir).copyTo(destDir);
        }
        JkPathTree.of(classDir).copyTo(destDir.resolve("WEB-INF/classes"));
        Path libDir = destDir.resolve("lib");
        JkPathTree.of(libDir).deleteContent();
        libs.forEach(path -> JkPathFile.of(path).copyToDir(libDir));
    }

    public void generateWarFile(Path destFile) {
        Path temp = JkUtilsPath.createTempDirectory("jeka-war");
        generateWarDir(temp);
        JkPathTree.of(temp).zipTo(destFile);
        JkPathTree.of(temp).deleteRoot();
    }

}
