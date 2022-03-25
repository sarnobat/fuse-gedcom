import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
public class FuseErrandsTxt extends FuseFilesystemAdapterFull {

	private static Map<String, String> displayNameOfChildToParent = new HashMap<>();


	public static void main(String[] args) throws FuseException, IOException {
		System.out.println("App.main() 1");
		if (args.length == 1) {
			new FuseErrandsTxt().log(true).mount(args[0]);
		} else {
			System.err.println("Usage: HelloFS <mountpoint>");
			String string2 = System.getProperty("dir");
			String string = string2;// "family_tree";
//			new ProcessBuilder().command("diskutil", "unmount", string2).inheritIO().start();
			try {
				Path p = Paths.get(string2);
				System.err.println(p);
				Files.createDirectory(p);
			} catch (FileAlreadyExistsException e) {
//				System.out.println("App.main() 2");
//				System.exit(-1);
			}
			System.out.println("App.main() 3");

			new Thread() {

				@SuppressWarnings("resource")
				@Override
				public void run() {
					System.out.println("App.main.run() 1");
					File myObj = new File(System.getProperty("gedcom"));
					Scanner myReader;

					try {
						System.out.println("App.main.run() 2");
						myReader = new Scanner(myObj);

						System.out.println("App.main.run() 3");
					} catch (FileNotFoundException e) {
						throw new RuntimeException(e);
					}
					System.out.println("App.main.run() 4");
					while (myReader.hasNextLine()) {
					}
					myReader.close();
					// I24 - root
				}

			}.run();
			System.out.println("App.main() 5");
			new FuseErrandsTxt().log(false).mount(string);
		}
	}


	private static final String FILENAME = "/hello1.txt";
	private static final String CONTENTS = "Hello World\n";

	@Override
	public int getattr(String path, StatWrapper stat) {
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
				String lastPartOf = getLastPartOf(path);
				if (displayNameToIndividualWithSpouse.keySet().contains(lastPartOf)) {
					stat.setMode(NodeType.DIRECTORY);
					return 0;
				} else {
					stat.setMode(NodeType.FILE).size(CONTENTS.length());
					return 0;
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
			return ErrorCodes.ENOENT();
		}
	}

	private String getLastPartOf(String path) {
		Path path2 = Paths.get(path);
		// String string = path2.getName(path2.getNameCount()).toString();
		String string = path2.getFileName().toString();
		// System.out.println("SRIDHAR App.getLastPartOf() " + string);
		return string;
	}

//	private static boolean isDirectory(String path) {
//		if (displayNameOfChildToParent.values().contains(path.replace("/", ""))) {
//			return true;
//		}
//		return false;
//	}

	@Override
	public int read(String path, ByteBuffer buffer, long size, long offset, final FileInfoWrapper info) {
		// Compute substring that we are being asked to read
		final String fileContents = CONTENTS.substring((int) offset,
				(int) Math.max(offset, Math.min(CONTENTS.length() - offset, offset + size)));
		buffer.put(fileContents.getBytes());
		// System.out.println("SRIDHAR App.read() " + fileContents);
		return fileContents.getBytes().length;
	}

	@Override
	public int readdir(String path, DirectoryFiller filler) {

		// filler.add(FILENAME);
		// filler.add("sridhar.txt");
		try {
			// System.out.println("SRIDHAR App.readdir() " + path);
			if (path.equals("/")) {
				// String key = "I31";
				String string = individual.toString();
				filler.add(string);
			} else {
				String s = Paths.get(path).getFileName().toString();
				System.out.println("SRIDHAR App.readdir() " + s);
			}
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(-1);
			// return -1;
		}
		return 0;
	}

	@Override
	public int rename(String oldName, String newName) {
		System.out.println("SRIDHAR App.rename() mv " + oldName + " " + newName);
		return 0;

	}
}
