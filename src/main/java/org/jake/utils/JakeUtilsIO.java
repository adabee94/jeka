package org.jake.utils;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.jake.file.utils.JakeUtilsFile;

public final class JakeUtilsIO {

	private JakeUtilsIO() {
	}

	/**
	 * Creates a no-op print stream.
	 */
	public static PrintStream nopPrintStream() {
		return new PrintStream(nopOuputStream());
	}

	/**
	 * Creates a no-op outputStream.
	 */
	public static OutputStream nopOuputStream() {
		return new OutputStream() {

			@Override
			public void write(int paramInt) throws IOException {
				// Do nothing
			}
		};
	}

	/**
	 * Close the specified input stream, ignoring any exceptions.
	 */
	public static void closeQuietly(InputStream inputStream) {
		try {
			inputStream.close();
		} catch (final Exception e) {
			// Ignored
		}
	}

	/**
	 * Close the specified output stream, ignoring any exceptions.
	 */
	public static void closeQuietly(OutputStream outputStream) {
		try {
			outputStream.close();
		} catch (final Exception e) {
			// Ignored
		}
	}

	/**
	 * Equivalent to {@link InputStream#read()} but without checked exceptions.
	 */
	public static int read(InputStream inputStream) {
		try {
			return inputStream.read();
		} catch (final Exception e) {
			throw new RuntimeException(e);
		}
	}

	// TODO encoding ????
	public static List<String> readLines(InputStream in) {
		final List<String> result = new LinkedList<String>();
		final BufferedReader reader = new BufferedReader(new InputStreamReader(
				in));
		String line;
		try {
			while ((line = reader.readLine()) != null) {
				result.add(line);
			}
		} catch (final IOException e) {
			throw new RuntimeException(e);
		}
		return result;
	}

	public static String readResource(String resourcePath) {
		final InputStream is = JakeUtilsFile.class.getClassLoader()
				.getResourceAsStream(resourcePath);
		return readLine(is);
	}

	public static String readLine(InputStream in) {
		final BufferedReader reader = new BufferedReader(new InputStreamReader(
				in));
		final StringBuilder out = new StringBuilder();
		final String newLine = System.getProperty("line.separator");
		String line;
		try {
			while ((line = reader.readLine()) != null) {
				out.append(line);
				out.append(newLine);
			}
		} catch (final IOException e) {
			throw new RuntimeException(e);
		}
		return out.toString();
	}

	public static String readResourceIfExist(String resourcePath) {
		final InputStream is = JakeUtilsFile.class.getClassLoader()
				.getResourceAsStream(resourcePath);
		if (is == null) {
			return null;
		}
		return readLine(is);
	}

	/**
	 * Equivalent to {@link ZipOutputStream#closeEntry()} but without checked exception.
	 */
	public static void closeEntry(ZipOutputStream outputStream) {
		try {
			outputStream.closeEntry();
		} catch (final IOException e) {
			throw new RuntimeException(e);
		}
	}

	public static ZipOutputStream createZipOutputStream(File file,
			int compressLevel) {
		try {
			if (!file.exists()) {
				file.createNewFile();
			}
			final FileOutputStream fos = new FileOutputStream(file);
			final ZipOutputStream zos = new ZipOutputStream(fos);
			zos.setLevel(compressLevel);
			return zos;
		} catch (final IOException e) {
			throw new RuntimeException(e);
		}

	}

	public static Set<String> mergeZip(ZipOutputStream zos, ZipFile zipFile) {
		final Set<String> duplicateEntries = new HashSet<String>();
		final Enumeration<? extends ZipEntry> entries = zipFile.entries();
		while (entries.hasMoreElements()) {
			final ZipEntry e = entries.nextElement();
			try {
				if (!e.isDirectory()) {
					final boolean success = addEntryInputStream(zos,
							e.getName(), zipFile.getInputStream(e));
					;
					if (!success) {
						duplicateEntries.add(e.getName());
					}
				}
			} catch (final IOException e1) {
				throw new RuntimeException("Error while merging entry "
						+ e.getName() + " from zip file " + zipFile.getName(),
						e1);
			}
		}
		return duplicateEntries;
	}

	private static boolean addEntryInputStream(ZipOutputStream zos,
			String entryName, InputStream inputStream) {
		final ZipEntry zipEntry = new ZipEntry(entryName);
		try {
			zos.putNextEntry(zipEntry);
		} catch (final ZipException e) {

			// Ignore duplicate entry - no overwriting
			return false;
		} catch (final IOException e) {
			throw new RuntimeException("Error while adding zip entry "
					+ zipEntry, e);
		}
		final int buffer = 2048;
		final BufferedInputStream bufferedInputStream = new BufferedInputStream(
				inputStream, buffer);
		int count;
		try {
			final byte data[] = new byte[buffer];
			while ((count = bufferedInputStream.read(data, 0, buffer)) != -1) {
				zos.write(data, 0, count);
			}
			bufferedInputStream.close();
			inputStream.close();
			zos.closeEntry();
			return true;
		} catch (final IOException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Add a zip entry into the provided <code>ZipOutputStream</code>. The zip
	 * entry is the part of <code>filePathToZip</code> truncated with the
	 * <code>baseFolderPath</code>.
	 * <p>
	 * So a file or folder <code>c:\my\base\folder\my\file\to\zip.txt</code>
	 * will be added in archive using <code>my/file/to/zip.txt</code> entry.
	 */
	public static void addZipEntry(ZipOutputStream zos, File fileToZip,
			File baseFolder) {
		if (!baseFolder.isDirectory()) {
			throw new IllegalArgumentException(baseFolder.getPath() + " is not a directory." );
		}

		if (fileToZip.isDirectory()) {
			final File[] files = fileToZip.listFiles();
			for (final File file : files) {
				addZipEntry(zos, file, baseFolder);
			}
		} else {
			final String filePathToZip;
			final int start;
			try {
				filePathToZip = fileToZip.getCanonicalPath();
				start = baseFolder.getCanonicalPath().length() + 1;
			} catch (final IOException e1) {
				throw new IllegalStateException(e1);
			}

			final int end = filePathToZip.length();
			String entryName = filePathToZip.substring(start, end);
			entryName = entryName.replace(File.separatorChar, '/');
			final FileInputStream inputStream;
			try {
				inputStream = new FileInputStream(filePathToZip);
			} catch (final FileNotFoundException e) {
				throw new IllegalStateException(e);
			}
			addEntryInputStream(zos, entryName, inputStream);
		}
	}

	public static ZipFile newZipFile(File file) {
		try {
			return new ZipFile(file);
		} catch (final Exception e) {
			throw new RuntimeException(e);
		}
	}

	public static void closeQietly(ZipFile zipFile) {
		try {
			zipFile.close();
		} catch (final IOException e) {
			throw new RuntimeException(e);
		}
	}

	public static void copy(InputStream in, OutputStream out) {
		final byte[] buf = new byte[1024];
		int len;
		try {
			while ((len = in.read(buf)) > 0) {
				out.write(buf, 0, len);
			}
		} catch (final IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		closeQuietly(in);
		closeQuietly(out);
	}

	/**
	 * Returns a thread that write each data read from the specified input stream to the specified output stream.
	 */
	public static StreamGobbler newStreamGobbler(InputStream is, PrintWriter os) {
		return new StreamGobbler(is, os);
	}

	/**
	 * Runs a thread copying all data from a stream to a specified writer.
	 * The thread is started when the instance is created. You have to call {@link #stop()}
	 * to stop the thread.
	 */
	public static class StreamGobbler {

		private final InnerRunnable innerRunnable;

		private StreamGobbler(InputStream is, PrintWriter os) {
			this.innerRunnable = new InnerRunnable(is, os);
			new Thread(innerRunnable).start();
		}

		/**
		 * Stop the gobbling, meaning stop the thread.
		 */
		public StreamGobbler stop() {
			this.innerRunnable.stop.set(true);
			return this;
		}

		public static class InnerRunnable implements Runnable {

			private final InputStream in;

			private final PrintWriter out;

			private final AtomicBoolean stop = new AtomicBoolean(false);

			private InnerRunnable(InputStream is, PrintWriter os) {
				this.in = is;
				this.out = os;
			}

			@Override
			public void run() {
				try {
					final InputStreamReader isr = new InputStreamReader(in);
					final BufferedReader br = new BufferedReader(isr);
					String line=null;
					while ( !stop.get() && (line = br.readLine()) != null) {
						out.println(line);
					}
				} catch (final IOException e) {
					throw new RuntimeException(e);
				}
			}

		}


	}


}
