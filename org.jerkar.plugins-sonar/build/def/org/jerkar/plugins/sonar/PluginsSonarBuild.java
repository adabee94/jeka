package org.jerkar.plugins.sonar;

import static org.jerkar.api.depmanagement.JkJavaDepScopes.PROVIDED;

import org.jerkar.CoreBuild;
import org.jerkar.api.depmanagement.JkDependencySet;
import org.jerkar.api.project.java.JkJavaProject;
import org.jerkar.tool.JkImportBuild;
import org.jerkar.tool.JkInit;
import org.jerkar.tool.builtins.java.JkJavaProjectBuild;

public class PluginsSonarBuild extends JkJavaProjectBuild {

    @JkImportBuild("../org.jerkar.core")
    private CoreBuild core;


    @Override
    protected void afterOptionsInjected() {
        final JkJavaProject project = java().project();
        CoreBuild.applyCommonSettings(project, "plugins-sonar");
        project.setDependencies(JkDependencySet.of()
                .and(core.java().project(), PROVIDED));
    }

    public static void main(String[] args) throws Exception {
        JkInit.instanceOf(PluginsSonarBuild.class, args).doDefault();
    }

}
