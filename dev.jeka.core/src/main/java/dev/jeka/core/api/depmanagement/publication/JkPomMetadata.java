package dev.jeka.core.api.depmanagement.publication;

import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * These are extra information required to publish on Maven central.
 *
 * @author Jerome Angibaud
 */
public final class JkPomMetadata<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * For parent chaining
     */
    public final T __;

    private String projectName;

    private String projectDescription;

    private String projectUrl;  // Landing page url

    private String scmConnection;  // Read only scm uri

    private String scmDeveloperConnection;  // Read-Write scm uri

    private String scmUrl;

    private final List<JkDeveloperInfo> developers;

    /** License list for the project, generaly there is a single license */
    private final List<JkLicenseInfo> licenses;

    public String getProjectName() {
        return projectName;
    }

    public String getProjectDescription() {
        return projectDescription;
    }

    public String getProjectUrl() {
        return projectUrl;
    }

    public JkPomMetadata<T> setProjectName(String name) {
        this.projectName = name;
        return this;
    }

    public JkPomMetadata<T> setProjectDescription(String description) {
        this.projectDescription = description;
        return this;
    }

    public JkPomMetadata<T> setProjectUrl(String projectUrl) {
        this.projectUrl = projectUrl;
        return this;
    }

    public JkPomMetadata<T> setScmConnection(String scmConnection) {
        this.scmConnection = scmConnection;
        return this;
    }

    public JkPomMetadata<T> setScmDeveloperConnection(String scmDeveloperConnection) {
        this.scmDeveloperConnection = scmDeveloperConnection;
        return this;
    }

    public JkPomMetadata<T> setScmUrl(String scmUrl) {
        this.scmUrl = scmUrl;
        return this;
    }

    public String getScmConnection() {
        return scmConnection;
    }

    public String getScmDeveloperConnection() {
        return scmDeveloperConnection;
    }

    public String getScmUrl() {
        return scmUrl;
    }

    private JkPomMetadata(T parent) {
        this.__ = parent;
        this.developers = new LinkedList<>();
        this.licenses = new LinkedList<>();
    }

    /**
     * Creates an empty publication info.
     */
    public static JkPomMetadata<Void> of() {
        return new JkPomMetadata(null);
    }

    /**
     * Creates an empty publication info.
     */
    public static <T> JkPomMetadata<T> ofParent(T parent) {
        return new JkPomMetadata(parent);
    }

    /**
     * Adds a developer pthis this publication.
     */
    public JkPomMetadata<T> addDeveloper(JkDeveloperInfo developerInfo) {
        this.developers.add(developerInfo);
        return this;
    }

    /**
     * @see #addDeveloper(JkDeveloperInfo)
     */
    public JkPomMetadata<T> addDeveloper(String name, String email, String organisation,
                                         String organisationUrl) {
        return addDeveloper(new JkDeveloperInfo(name, email, organisation, organisationUrl));
    }

    /**
     * @see #addDeveloper(JkDeveloperInfo)
     */
    public JkPomMetadata<T> addGithubDeveloper(String name, String email) {
        return addDeveloper(new JkDeveloperInfo(name, email, "Github", "https://github.com"));
    }

    /**
     * Adds license information about this publication
     */
    public JkPomMetadata<T> addLicense(JkLicenseInfo licenseInfo) {
        this.licenses.add(licenseInfo);
        return this;
    }

    /**
     * @see #addLicense(JkLicenseInfo)
     */
    public JkPomMetadata<T> addLicense(String name, String url) {
        this.licenses.add(JkLicenseInfo.of(name, url));
        return this;
    }

    /**
     * R@see #addLicense(JkLicenseInfo)
     */
    public JkPomMetadata<T> addApache2License() {
        return addLicense("Apache License V2.0", "http://www.apache.org/licenses/LICENSE-2.0.html");
    }

    /**
     * @see #addLicense(JkLicenseInfo)
     */
    public JkPomMetadata<T> addGpl3License() {
        return addLicense("GNU General public license V3", "https://www.gnu.org/copyleft/gpl.html");
    }

    /**
     *  @see #addLicense(JkLicenseInfo)
     */
    public JkPomMetadata<T> addMitLicense() {
        return addLicense("MIT License", "http://opensource.org/licenses/MIT");
    }

    public List<JkDeveloperInfo> getDevelopers() {
        return Collections.unmodifiableList(developers);
    }

    public List<JkLicenseInfo> getLicenses() {
        return Collections.unmodifiableList(licenses);
    }


    /**
     * Information about a developer.
     */
    public static final class JkDeveloperInfo implements Serializable {

        private static final long serialVersionUID = 1L;

        /** Name of developer */
        private final String name;

        /** Email we can contact the developer */
        private final String email;

        /** Organization name the developer belong to */
        private final String organisation;

        /** Organization url the developer belong to */
        private final String organisationUrl;

        /**
         * Constructs a {@link JkDeveloperInfo} specifying its name, email, organization value
         * and organization url.
         */
         JkDeveloperInfo(String name, String email, String organisation,
                String organisationUrl) {
            super();
            this.name = name;
            this.email = email;
            this.organisation = organisation;
            this.organisationUrl = organisationUrl;
        }

        public String getName() {
            return name;
        }

        public String getEmail() {
            return email;
        }

        public String getOrganisation() {
            return organisation;
        }

        public String getOrganisationUrl() {
            return organisationUrl;
        }
    }

    /**
     * Information about a license.
     */
    public static final class JkLicenseInfo implements Serializable {

        private static final long serialVersionUID = 1L;

        /** Name of the license (as Apache 2) */
        private final String name;

        /** An url pointing on the license terms */
        private final String url;

        /**
         * Constructs a {@link JkLicenseInfo} specifying its name and URL.
         */
        private JkLicenseInfo(String name, String url) {
            super();
            this.name = name;
            this.url = url;
        }

        public static JkLicenseInfo of(String name, String url) {
            return new JkLicenseInfo(name, url);
        }

        public String getName() {
            return name;
        }

        public String getUrl() {
            return url;
        }
    }

}
