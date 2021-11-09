import dev.jeka.core.api.depmanagement.JkFileSystemDependency;
import dev.jeka.core.api.java.JkJavaVersion;
import dev.jeka.core.api.system.JkLocator;
import dev.jeka.core.tool.JkClass;
import dev.jeka.core.tool.JkInit;
import dev.jeka.core.tool.builtins.project.JkPluginProject;

class SpringbootPluginBuild extends JkClass {

    final JkPluginProject javaPlugin = getPlugin(JkPluginProject.class);

    @Override
    protected void setup() {
        javaPlugin.getProject().simpleFacade()
                .setJvmTargetVersion(JkJavaVersion.V8)
                .setCompileDependencies(deps -> deps
                        .andFiles(JkLocator.getJekaJarPath())
                )
                .setRuntimeDependencies(deps -> deps
                        .minus(JkFileSystemDependency.of(JkLocator.getJekaJarPath()))
                );
        javaPlugin.getProject().getPublication()
            .getMaven()
                .setModuleId("dev.jeka:springboot-plugin")
                .getPomMetadata()
                    .setProjectName("Jeka plugin for Spring Boot")
                    .setProjectDescription("A Jeka plugin for Spring boot application")
                    .addGithubDeveloper("djeang", "djeangdev@yahoo.fr");
    }

    public void cleanPack() {
        clean(); javaPlugin.pack();
    }

    public static void main(String[] args) {
        JkInit.instanceOf(SpringbootPluginBuild.class, args).cleanPack();
    }

}