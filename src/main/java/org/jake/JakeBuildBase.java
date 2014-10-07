package org.jake;

import java.io.File;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import org.jake.utils.JakeUtilsFile;
import org.jake.utils.JakeUtilsReflect;
import org.jake.utils.JakeUtilsString;
import org.jake.utils.JakeUtilsTime;

/**
 * Base project builder defining some commons tasks and utilities
 * necessary for any kind of project, regardless involved technologies.
 * 
 * Classes inheriting from this one must provide a zero-argument constructor (can be private).
 * 
 * @author Djeang
 */
public class JakeBuildBase {

	private static final int JUMP = 2;

	private File baseDirFile = JakeUtilsFile.workingDir();

	private final String version = JakeUtilsTime.timestampSec();

	protected JakeBuildBase() {
	}

	void setBaseDir(File baseDir) {
		this.baseDirFile = baseDir;
	}

	/**
	 * The current version for this project. Might look like "0.6.3".
	 */
	public String version() {
		return version;
	}

	/**
	 * The string used to suffix produced artefacts name to indicate version.
	 * Might look like "-0.6.3".
	 */
	public String versionSuffix() {
		if (version() == null || version().isEmpty()) {
			return "";
		}
		return "-" + version();
	}

	/**
	 * The project name. This is likely to used in produced artifacts.
	 */
	public String projectName() {
		final String projectDirName = baseDir().root().getName();
		return projectDirName.contains(".") ? JakeUtilsString.substringAfterLast(projectDirName, ".") : projectDirName;
	}

	/**
	 * The project group name. This is likely to used in produced artifacts.
	 */
	public String groupName() {
		final String projectDirName = baseDir().root().getName();
		return projectDirName.contains(".") ? JakeUtilsString.substringBeforeLast(projectDirName, ".") : projectDirName;
	}

	/**
	 * By default, this method returns the concatenation of the project group and project name. It is likely to
	 * be used as produced artifacts file names.
	 */
	public String projectFullName() {
		if (groupName() == null || groupName().equals(projectName())) {
			return projectName();
		}
		return groupName()+ "." + projectName();
	}


	/**
	 * Returns the base directory for this project. All file/directory path are
	 * resolved from this directory.
	 */
	public JakeDir baseDir() {
		return JakeDir.of(baseDirFile);
	}

	/**
	 * Return a file located at the specified path relative to the base directory.
	 */
	public File baseDir(String relativePath) {
		if (relativePath.isEmpty()) {
			return baseDirFile;
		}
		return baseDir().file(relativePath);
	}

	/**
	 * The output directory where all the final and intermediate
	 * artifacts are generated.
	 */
	public JakeDir ouputDir() {
		return baseDir().sub("build/output").createIfNotExist();
	}

	/**
	 * Returns the file located at the specified path relative to the output directory.
	 */
	public File ouputDir(String relativePath) {
		return ouputDir().file(relativePath);
	}

	// ------------ Operations ------------

	@JakeDoc("Clean the output directory.")
	public void clean() {
		JakeLog.start("Cleaning output directory " + ouputDir().root().getPath() );
		JakeUtilsFile.deleteDirContent(ouputDir().root());
		JakeLog.done();
	}

	@JakeDoc("Conventional method standing for the default operations to perform.")
	public void base() {
		clean();
	}

	@JakeDoc("Display all available methods defined in this build.")
	public void help() {
		JakeLog.info("Usage: jake [methodA...] [-optionName=value...] [-DsystemPropName=value...]");
		JakeLog.info("When no method specified, then 'default' action is processed.");
		JakeLog.info("Ex: jake javadoc compile -verbose=true -other=xxx -DmyProp=Xxxx");
		JakeLog.nextLine();
		JakeLog.info("Available action(s) for build '" + this.getClass().getName() + "' : " );
		JakeLog.shift(JUMP);
		final List<CommandDescription> list = new LinkedList<JakeBuildBase.CommandDescription>();
		for (final Method method : this.getClass().getMethods()) {
			final int modifier = method.getModifiers();
			if (!method.getReturnType().equals(void.class)
					|| method.getParameterTypes().length != 0
					|| JakeUtilsReflect.isMethodPublicIn(Object.class, method.getName())
					|| Modifier.isAbstract(modifier) || Modifier.isStatic(modifier)) {
				continue;
			}
			final JakeDoc jakeDoc = JakeUtilsReflect.getInheritedAnnotation(method, JakeDoc.class);
			final CommandDescription actionDescription;
			if (jakeDoc != null) {
				actionDescription = new CommandDescription(method, jakeDoc.value());
			} else {
				actionDescription = new CommandDescription(method);
			}
			list.add(actionDescription);
		}
		CommandDescription.log(list);
		JakeLog.shift(-JUMP);
		JakeLog.nextLine();
		JakeLog.info("Standard options for this build class : ");
		JakeLog.nextLine();
		JakeLog.shift(JUMP);
		JakeLog.info(JakeOptions.help(this.getClass()));
		JakeLog.shift(-JUMP);
	}

	private static class CommandDescription implements Comparable<CommandDescription> {

		private final String name;
		private final String[] docs;
		private final Class<?> declaringClass;

		public CommandDescription(Method method, String[] docs) {
			super();
			this.name = method.getName();
			this.docs = Arrays.copyOf(docs, docs.length);
			this.declaringClass = method.getDeclaringClass();
		}

		public CommandDescription(Method method) {
			this(method, new String[0]);
		}

		@Override
		public int compareTo(CommandDescription other) {
			if (this.declaringClass.equals(other.declaringClass)) {
				return this.name.compareTo(other.name);
			}
			if (this.declaringClass.isAssignableFrom(other.declaringClass)) {
				return -1;
			}
			return 1;
		}

		public void log() {
			if (docs == null || docs.length == 0) {
				JakeLog.info(name + " : Not documented.");
			} else if (docs.length == 1) {
				JakeLog.info(name + " : " + docs[0]);
			} else {
				final String intro = name + " : ";
				JakeLog.info(intro + docs[0]);
				final String margin = JakeUtilsString.repeat(" ", intro.length());
				for (int i = 1; i < docs.length; i++) {
					JakeLog.info(margin + docs[i]);
				}
			}
		}

		public static void log(List<CommandDescription> actions) {
			Class<?> currentDecClass = null;
			Collections.sort(actions);
			for(final CommandDescription actionDescription : actions) {
				if (actionDescription.declaringClass != currentDecClass) {
					JakeLog.nextLine();
					JakeLog.info("From " + actionDescription.declaringClass.getName());
					currentDecClass = actionDescription.declaringClass;
				}
				JakeLog.shift(1);
				actionDescription.log();
				JakeLog.shift(-1);
			}
			JakeLog.nextLine();
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((name == null) ? 0 : name.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) {
				return true;
			}
			if (obj == null) {
				return false;
			}
			if (getClass() != obj.getClass()) {
				return false;
			}
			final CommandDescription other = (CommandDescription) obj;
			if (name == null) {
				if (other.name != null) {
					return false;
				}
			} else if (!name.equals(other.name)) {
				return false;
			}
			return true;
		}
	}
}
