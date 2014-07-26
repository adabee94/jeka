package org.jake;

import java.io.InputStream;
import java.util.LinkedList;
import java.util.List;

import org.jake.file.utils.JakeUtilsFile;
import org.jake.utils.JakeUtilsString;

public class JakeLauncher {

	public static void main(String[] args) {
		final int lenght = printAsciiArt1();
		JakeLog.info(JakeUtilsString.repeat(" ", lenght) + "The 100% Java build system.");
		final String version = JakeUtilsFile.readResourceIfExist("org/jake/version.txt");
		if (version != null) {
			JakeLog.info(JakeUtilsString.repeat(" ", 70) + "Version : " + version);
		}
		JakeLog.nextLine();
		defineSystemProps(args);
		final List<String> actions = extractAcions(args);
		final ProjectBuilder projectBuilder = new ProjectBuilder(JakeUtilsFile.workingDir());
		final boolean result = projectBuilder.build(actions);
		if (!result) {
			System.exit(1);
		}
	}

	private static List<String> extractAcions(String[] args) {
		final List<String> result = new LinkedList<String>();
		for (final String arg : args) {
			if (!arg.startsWith("-")) {
				result.add(arg);
			}
		}
		return result;
	}

	private static void defineSystemProps(String[] args) {
		for (final String arg : args) {
			if (arg.startsWith("-D")) {
				final int equalIndex = arg.indexOf("=");
				if (equalIndex <= -1) {
					System.setProperty(arg.substring(2), "");
				} else {
					final String name = arg.substring(2, equalIndex);
					final String value = arg.substring(equalIndex+1);
					System.setProperty(name, value);
				}
			}
		}
	}


	public static int printAsciiArt1() {
		final InputStream inputStream = JakeLauncher.class.getResourceAsStream("ascii1.txt");
		final List<String> lines = JakeUtilsFile.toLines(inputStream);
		int i = 0;
		for (final String line: lines) {
			if (i < line.length()) {
				i = line.length();
			}
			JakeLog.info(line);
		}
		return i;
	}

}
