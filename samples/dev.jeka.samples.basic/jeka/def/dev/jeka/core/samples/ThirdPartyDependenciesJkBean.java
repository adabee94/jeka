package dev.jeka.core.samples;

import com.google.common.base.MoreObjects;
import dev.jeka.core.api.project.JkProject;
import dev.jeka.core.tool.JkBean;
import dev.jeka.core.tool.JkDoc;
import dev.jeka.core.tool.JkInit;
import dev.jeka.core.tool.JkInjectClasspath;
import dev.jeka.core.tool.builtins.project.ProjectJkBean;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;

import java.io.IOException;

import static dev.jeka.core.api.depmanagement.JkPopularLibs.*;

/**
 * This build demonstrates how to use 3rd party dependencies into your build class.
 * 
 * @author Jerome Angibaud
 * @formatter:off
 */
@JkInjectClasspath("commons-httpclient:commons-httpclient:3.1")
@JkInjectClasspath("com.google.guava:guava:21.0")
public class ThirdPartyDependenciesJkBean extends JkBean {

    ProjectJkBean projectPlugin = getRuntime().getBean(ProjectJkBean.class).configure(this::configure);

    private void configure(JkProject project) {
        project.flatFacade()
            .configureCompileDependencies(deps -> deps
                .and(JAVAX_SERVLET_API.toCoordinate("3.1.0"))
                .and(GUAVA.toCoordinate("30.0-jre")))
            .configureRuntimeDependencies(compileDeps -> compileDeps
                .minus(JAVAX_SERVLET_API))
            .configureTestDependencies(deps -> deps
                .and(SimpleProjectJkBean.JUNIT5)
                .and(MOCKITO_ALL.toCoordinate("1.10.19")));
    }

    @JkDoc("Performs some load test using http client")
    public void seleniumLoadTest() throws IOException {
        HttpClient client = new HttpClient();
        GetMethod getMethod = new GetMethod("http://my.url");
        client.executeMethod(getMethod);
        client = MoreObjects.firstNonNull(client, client); // senseless but just to illustrate we can use Guava
        // ....
    }

    public void cleanPack() {
        cleanOutput();
        projectPlugin.pack();
    }

    public static void main(String[] args) {
        JkInit.instanceOf(ThirdPartyDependenciesJkBean.class, args).cleanPack();
    }

}
