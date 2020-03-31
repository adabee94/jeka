package dev.jeka.core.api.java.project;

import dev.jeka.core.api.depmanagement.JkJavaDepScopes;
import dev.jeka.core.api.java.JkJavadocMaker;
import dev.jeka.core.api.system.JkLog;

import java.nio.file.Files;
import java.nio.file.Path;

public class JkJavaProjectMakerDocumentationStep {

    private final JkJavaProjectMaker maker;

    private JkJavadocMaker<JkJavaProjectMakerDocumentationStep> javadocMaker;

    private boolean done;

    /**
     * For parent chaining
     */
    public final JkJavaProjectMaker.JkSteps _;

    private JkJavaProjectMakerDocumentationStep(JkJavaProjectMaker maker) {
        this.maker = maker;
        this._ = maker.getSteps();
    }

    static JkJavaProjectMakerDocumentationStep of(JkJavaProjectMaker maker) {
        JkJavaProjectMakerDocumentationStep result = new JkJavaProjectMakerDocumentationStep(maker);
        result.javadocMaker = JkJavadocMaker.of(result);
        return result;
    }

    public JkJavadocMaker<JkJavaProjectMakerDocumentationStep> getJavadocMaker() {
        return javadocMaker;
    }

    /**
     * Generates javadoc files (files + zip)
     */
    public void run() {
        final JkJavaProject project = maker.project;
        Iterable<Path> classpath = maker.fetchDependenciesFor(JkJavaDepScopes.SCOPES_FOR_COMPILATION);
        Path dir = maker.getOutLayout().getJavadocDir();
        javadocMaker.process(classpath, project.getSourceLayout().getSources(), dir);
    }

    public void runIfNecessary() {
        if (done && !Files.exists(maker.getOutLayout().getJavadocDir())) {
            JkLog.info("Javadoc already generated. Won't perfom again");
        } else {
            run();
            done = true;
        }
    }

    void reset() {
        done = false;
    }

}
