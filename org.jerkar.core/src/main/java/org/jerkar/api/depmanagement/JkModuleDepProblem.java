package org.jerkar.api.depmanagement;

import java.io.Serializable;

/**
 * Information about problem when resolving dependencies
 */
public class JkModuleDepProblem implements Serializable {

    private static final long serialVersionUID = 1L;

    static JkModuleDepProblem of(JkModuleId moduleId, String version, String text) {
        return new JkModuleDepProblem(moduleId, JkVersion.of(version), text);
    }

    private final JkModuleId moduleId;

    private final JkVersion version;

    private final String problemText;

    private JkModuleDepProblem(JkModuleId moduleId, JkVersion version, String problemText) {
        this.moduleId = moduleId;
        this.version= version;
        this.problemText = problemText;
    }

    /**
     * Returns the getModuleId related to this problem.
     */
    public JkModuleId moduleId() {
        return moduleId;
    }

    /**
     * Returns the projectVersion range for which the problematic module dependency has been declared.
     */
    public JkVersion version() {
        return version;
    }

    /**
     * Returns the text explaining this problem.
     */
    public String problemText() {
        return problemText;
    }

    @Override
    public String toString() {
        return moduleId + ":" + version + " -> " + problemText;
    }
}
