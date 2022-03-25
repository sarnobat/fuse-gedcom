import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Scanner;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;

import net.fusejna.DirectoryFiller;
import net.fusejna.ErrorCodes;
import net.fusejna.FuseException;
import net.fusejna.StructFuseFileInfo.FileInfoWrapper;
import net.fusejna.StructStat.StatWrapper;
import net.fusejna.types.TypeMode.NodeType;
import net.fusejna.util.FuseFilesystemAdapterFull;

// 2022-03: going forward, I'm using Java 11. No neeed for groovy anymore
// https://github.com/EtiennePerot/fuse-jna/blob/master/src/main/java/net/fusejna/util/FuseFilesystemAdapterFull.java
public class FuseErrandsTxt {

	private static Map<String, String> displayNameOfChildToParent = new HashMap<>();

	public static void main(String[] args) throws FuseException, IOException {
		System.out.println("App.main() 1");
		System.err.println("Usage: HelloFS <mountpoint>");
		String out = System.getProperty("out", "/tmp/errands/");
//			new ProcessBuilder().command("diskutil", "unmount", string2).inheritIO().start();
		try {
			Path p = Paths.get(out);
			System.err.println(p);
			Files.createDirectory(p);
		} catch (FileAlreadyExistsException e) {
//				System.out.println("App.main() 2");
//				System.exit(-1);
		}
		System.out.println("App.main() 3");

//		new Thread() {
//
//			@SuppressWarnings("resource")
//			@Override
//			public void run() {
//				System.out.println("App.main.run() 1");
//				String dirPath = System.getProperty("in", System.getProperty("user.home") + "/sarnobat.git/errands/");
//
//				String ret = dir2Txt(dirPath, "");
//			}
//
//			@Deprecated
//			private String dir2Txt(String property, String indentation) {
//				String contents = "";
//				File dir = new File(property);
//				List<File> files = Arrays.stream(Objects.requireNonNull(dir.listFiles())).collect(Collectors.toList());
//				for (File f : files) {
//					System.out.println("FuseErrandsTxt.main() " + f.getAbsolutePath());
//					if (f.isDirectory()) {
//						contents += dir2Txt(f.getAbsolutePath(), indentation + "\t");
//					} else if (f.isFile()) {
//						contents += f.getName() + "\n";
//					}
//				}
//				return contents;
//			}
//
//		}.run();
		String in = System.getProperty("in", System.getProperty("user.home") + "/sarnobat.git/errands/");
		System.out.println("App.main() 5");
		new HelloFS1(args[0]);
//		new HelloFS1(in, out);
	}

	static class HelloFS1 extends FuseFilesystemAdapterFull {
		final String filename = "/hello.txt";
		final String contents = "Hello World!\n";
		@Deprecated
		private static final String CONTENTS = "Hello World\n";
		private  String in;
		private String out;

		public HelloFS1(String string) {
			try {
				this.log(true).mount(string);
			} catch (FuseException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				System.exit(-1);
			}
		}

		public HelloFS1(String in, String out) {
			this.in = in;
			this.out = out;
			try {
				this.log(true).mount(in);
			} catch (FuseException e) {
				e.printStackTrace();
				System.exit(-1);
			}
		}

		@Override
		public int getattr(final String path, final StatWrapper stat) {
			if (path.equals(File.separator)) { // Root directory
				stat.setMode(NodeType.DIRECTORY);
				return 0;
			}
			if (path.equals(filename)) { // hello.txt
				stat.setMode(NodeType.FILE).size(contents.length());
				return 0;
			}
			return -ErrorCodes.ENOENT();
		}

//		@Override
		public int getattr1(String path, StatWrapper stat) {
			try {
				stat.setAllTimesMillis(System.currentTimeMillis());
				// System.out.println("SRIDHAR App.getattr() " + path);
				if (path.equals(File.separator)) { // Root directory
					stat.setMode(NodeType.DIRECTORY);
					return 0;
				}
				if (path.contains(".txt")) { // hello.txt
					stat.setMode(NodeType.FILE).size(CONTENTS.length());
					return 0;
				} else {
					stat.setMode(NodeType.DIRECTORY);
					return 0;
				}
			} catch (Exception e) {
				e.printStackTrace();
				return ErrorCodes.ENOENT();
			}
		}

		@Override
		public int read(final String path, final ByteBuffer buffer, final long size, final long offset,
				final FileInfoWrapper info) {
			// Compute substring that we are being asked to read
			final String s = contents.substring((int) offset,
					(int) Math.max(offset, Math.min(contents.length() - offset, offset + size)));
			buffer.put(s.getBytes());
			return s.getBytes().length;
		}

		private String getLastPartOf(String path) {
			Path path2 = Paths.get(path);
			// String string = path2.getName(path2.getNameCount()).toString();
			String string = path2.getFileName().toString();
			// System.out.println("SRIDHAR App.getLastPartOf() " + string);
			return string;
		}

		@Override
		public int readdir(final String path, final DirectoryFiller filler) {
			filler.add(filename);
			return 0;
		}

//		@Override
		public int read1(String path, ByteBuffer buffer, long size, long offset, final FileInfoWrapper info) {
			System.err.println("FuseErrandsTxt.read() path = " + path);
			// Compute substring that we are being asked to read
			final String fileContents = dir2Txt(in, out);
			buffer.put(fileContents.getBytes());
			// System.out.println("SRIDHAR App.read() " + fileContents);
			return fileContents.getBytes().length;
		}

		private String dir2Txt(String property, String indentation) {
			String contents = "";
			File dir = new File(property);
			List<File> files = Arrays.stream(Objects.requireNonNull(dir.listFiles())).collect(Collectors.toList());
			for (File f : files) {
				System.out.println("FuseErrandsTxt.dir2Txt() " + f.getAbsolutePath());
				if (f.isDirectory()) {
					contents += dir2Txt(f.getAbsolutePath(), indentation + "\t");
				} else if (f.isFile()) {
					contents += f.getName() + "\n";
				}
			}
			return contents;
		}

//		@Override
		public int readdir1(String path, DirectoryFiller filler) {
			System.out.println("SRIDHAR App.readdir() " + path);
			try {
				if (path.equals("/")) {
					System.out.println("FuseErrandsTxt.My.readdir() 1");
					filler.add("errands.txt");
				} else {
					System.out.println("FuseErrandsTxt.My.readdir() 2");
					String s = Paths.get(path).getFileName().toString();
				}
			} catch (Exception e) {
				e.printStackTrace();
				System.exit(-1);
			}
			return 0;
		}

		@Override
		public int rename(String oldName, String newName) {
			System.out.println("SRIDHAR App.rename() mv " + oldName + " " + newName);
			return 0;

		}
	}

}
