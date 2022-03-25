import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
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
		String out = System.getProperty("out", "/tmp/1/");
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

		String in = System.getProperty("in", System.getProperty("user.home") + "/sarnobat.git/errands/");
		System.out.println("App.main() 5");
		if (!args[0].equals(out)) {
			System.out.println("FuseErrandsTxt.main() out = " + out);
			System.out.println("FuseErrandsTxt.main() args[0] = " + args[0]);
			System.out.println("FuseErrandsTxt.main() error");
			System.exit(-1);
		}
		new HelloFS1(in, out);
	}

	static class HelloFS1 extends FuseFilesystemAdapterFull {
		private final String filename2 = "/errands.txt";
		private String contents2;
		private ByteBuffer contents = ByteBuffer.allocate(0);

		private String in;
		private String out;

		public HelloFS1(String in, String out) {
			this.in = in;
			this.out = out;
			this.contents2 = dir2Txt(in, "");
			try {
				this.log(true).mount(out);
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
			if (path.equals(filename2)) { // hello.txt
				stat.setMode(NodeType.FILE).size(contents2.length());
				return 0;
			}
			return -ErrorCodes.ENOENT();
		}

		@Override
		public int read(String path, ByteBuffer buffer, long size, long offset, final FileInfoWrapper info) {
			System.err.println("FuseErrandsTxt.read2() A path = " + path);
			// Compute substring that we are being asked to read
			final String fileContents = contents2;
			System.out.println("FuseErrandsTxt.HelloFS1.read2() B fileContents = " + fileContents);
			try {
			buffer.put(fileContents.getBytes());
			} catch (Exception e) {
				e.printStackTrace();
//				System.exit(-1);
			}
			System.out.println("FuseErrandsTxt.HelloFS1.read2() C fileContents = " + fileContents);
			return fileContents.getBytes().length;
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
			filler.add(filename2);
			return 0;
		}

		private String dir2Txt(String property, String indentation) {
			String contents = "";
			File dir = new File(property);
			List<File> files = Arrays.stream(Objects.requireNonNull(dir.listFiles())).collect(Collectors.toList());
			for (File f : files) {
				System.out.println("FuseErrandsTxt.dir2Txt() " + f.getAbsolutePath());
				if (f.isDirectory()) {
					contents += indentation + f.getName() + "\n";
					contents += dir2Txt(f.getAbsolutePath(), indentation + "\t");
				} else if (f.isFile()) {
					System.err.println("FuseErrandsTxt.HelloFS1.dir2Txt() - Unimplemented");
				}
			}
			return contents;
		}

		@Override
		public int rename(String oldName, String newName) {
			System.out.println("SRIDHAR App.rename() mv " + oldName + " " + newName);
			return 0;

		}

		@Override
		public int write(final String path, final ByteBuffer buf, final long bufSize, final long writeOffset,
				final FileInfoWrapper wrapper) {
//			String toBeWritten = new String(buf.array(), StandardCharsets.UTF_8);
//			contents2 = toBeWritten;
//			System.out.println("FuseErrandsTxt.HelloFS1.write() " + toBeWritten);
			return doWrite(buf, bufSize, writeOffset);
		}
		
		private int doWrite(final ByteBuffer buffer, final long bufSize, final long writeOffset)
		{
			final int maxWriteIndex = (int) (writeOffset + bufSize);
			final byte[] bytesToWrite = new byte[(int) bufSize];
			synchronized (this) {
				if (maxWriteIndex > contents.capacity()) {
					// Need to create a new, larger buffer
					final ByteBuffer newContents = ByteBuffer.allocate(maxWriteIndex);
					newContents.put(contents);
					contents = newContents;
				}
				buffer.get(bytesToWrite, 0, (int) bufSize);
				contents.position((int) writeOffset);
				contents.put(bytesToWrite);
				contents2 = new String(bytesToWrite, StandardCharsets.UTF_8);
				contents.position(0); // Rewind
			}
			return (int) bufSize;
		}
	}

}
