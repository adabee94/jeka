package dev.jeka.core.api.depmanagement;


import org.junit.Assert;
import org.junit.Test;

import static dev.jeka.core.api.depmanagement.JkPopularLibs.GUAVA;
import static dev.jeka.core.api.depmanagement.JkPopularLibs.JAVAX_SERVLET_API;

public class JkQualifiedDependencySetTest {

    @Test
    public void computeIdeDependencies_1compileAnd1provided_ok() {
        JkDependencySet compile = JkDependencySet.of()
                .and(GUAVA.version("19.0"))
                .and (JAVAX_SERVLET_API.version("4.0.1"));
        JkDependencySet runtime = compile.minus(JAVAX_SERVLET_API);
        JkProjectDependencies projectDependencies = JkProjectDependencies.of(compile, runtime, JkDependencySet.of());
        JkQualifiedDependencySet qdeps = JkQualifiedDependencySet.computeIdeDependencies(projectDependencies);
        JkQualifiedDependency guava = qdeps.findByModule(GUAVA.toString()).get(0);
        JkQualifiedDependency servlet = qdeps.findByModule(JAVAX_SERVLET_API.toString()).get(0);
        Assert.assertEquals("compile", guava.getQualifier() );
        Assert.assertEquals("provided", servlet.getQualifier() );
    }


}
