import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;

import net.fusejna.DirectoryFiller;
import net.fusejna.ErrorCodes;
import net.fusejna.FuseException;
import net.fusejna.StructFuseFileInfo.FileInfoWrapper;
import net.fusejna.StructStat.StatWrapper;
import net.fusejna.types.TypeMode.ModeWrapper;
import net.fusejna.types.TypeMode.NodeType;
import net.fusejna.util.FuseFilesystemAdapterAssumeImplemented;

public class FuseErrandsTxt {

	public static void main(String... args) throws FuseException {
		if (args.length != 1) {
			System.err.println("Usage: MemoryFS <mountpoint>");
			System.exit(1);
		}
		try {
			// Add the inode number
			// | xargs --delimiter '\n' --max-args=1 ls -id
			Process process = new ProcessBuilder()
					.command("bash", "-c", "find /Users/sarnobat/sarnobat.git/errands/ -type d "
							+ "| python3 /Users/sarnobat/src.git/python/yamlfs/yamlfs_stdin.py")
					.start();
			BufferedInputStream bis = new BufferedInputStream(process.getInputStream());
			Reader reader = new InputStreamReader(bis);
			BufferedReader br = new BufferedReader(reader);

			String line;
			String all = "";
			while ((line = br.readLine()) != null) {
				System.out.println("MemoryFS.main() " + line);
				all += line + "\n";
			}
			new MemoryFSAdapter(args[0], "errands.txt", all);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private static class MemoryFSAdapter extends FuseFilesystemAdapterAssumeImplemented {

		private static final String rootDirName = "";
		private final RootDirectory rootDirectory = new RootDirectory(rootDirName);
		private final ErrandsTxtFile errandsTxtFile;
//		private final String filename;
//		private final String fileContents;
		ByteBuffer contentsBytes;

		MemoryFSAdapter(String location, String filename, String fileContents) {
			byte[] bytes = {};
			try {
				bytes = fileContents.getBytes("UTF-8");
			} catch (UnsupportedEncodingException e1) {
				// Not going to happen
			}
			contentsBytes = ByteBuffer.wrap(bytes);
//			this.filename = filename;
			this.errandsTxtFile = new ErrandsTxtFile(filename, contentsBytes);
			try {
				this.log(true).mount(location);
			} catch (FuseException e) {
				e.printStackTrace();
			}
		}

		private final class RootDirectory extends MemoryPath {

//			@Deprecated // use a global field, not object member
//			private final String rootDirName;
			private RootDirectory(String name) {
//				this.rootDirName = name;
			}
		}

		private final class ErrandsTxtFile extends MemoryPath {
			@Deprecated
			private final String txtFilename;
			private ByteBuffer contentsBytes = ByteBuffer.allocate(0);

			public ErrandsTxtFile(String name, ByteBuffer contentsBytes) {
				this.contentsBytes = contentsBytes;
				this.txtFilename = name;
			}

			private int read(ByteBuffer buffer, long size, long offset, ByteBuffer contentsBytes,
					ErrandsTxtFile errandsTxtFile) {
				int bytesToRead = (int) Math.min(contentsBytes.capacity() - offset, size);
				byte[] bytesRead = new byte[bytesToRead];
				synchronized (errandsTxtFile) {
					contentsBytes.position((int) offset);
					contentsBytes.get(bytesRead, 0, bytesToRead);
					buffer.put(bytesRead);
					contentsBytes.position(0); // Rewind
				}
				return bytesToRead;
			}

			private synchronized void truncate(long size, ByteBuffer contentsBytes, ErrandsTxtFile errandsTxtFile) {
				synchronized (errandsTxtFile) {
					if (size < contentsBytes.capacity()) {
						// Need to create a new, smaller buffer
						ByteBuffer newContents = ByteBuffer.allocate((int) size);
						byte[] bytesRead = new byte[(int) size];
						contentsBytes.get(bytesRead);
						newContents.put(bytesRead);
						contentsBytes = newContents;
					}
				}
			}

			private int write(ByteBuffer buffer, long bufSize, long writeOffset, ByteBuffer contentsBytes) {
				int maxWriteIndex = (int) (writeOffset + bufSize);
				byte[] bytesToWrite = new byte[(int) bufSize];
				synchronized (this) {
					if (maxWriteIndex > contentsBytes.capacity()) {
						// Need to create a new, larger buffer
						ByteBuffer newContents = ByteBuffer.allocate(maxWriteIndex);
						newContents.put(contentsBytes);
						contentsBytes = newContents;
					}
					buffer.get(bytesToWrite, 0, (int) bufSize);
					contentsBytes.position((int) writeOffset);
					contentsBytes.put(bytesToWrite);
					contentsBytes.position(0); // Rewind
				}
				return (int) bufSize;
			}

			MemoryPath find(String path) {
				while (path.startsWith("/")) {
					path = path.substring(1);
				}
				if (path.equals(txtFilename) || path.isEmpty()) {
					return this;
				}
				return null;
			}
		}

		@Deprecated // don't use superclasses
		public static abstract class MemoryPath {
		}

		@Override
		public int access(String path, int access) {
			return 0;
		}

		@Override
		public int create(String path, ModeWrapper mode, FileInfoWrapper info) {
			if (FuseErrandsTxt.MemoryFSAdapter.find(path, errandsTxtFile, rootDirectory) != null) {
				return -ErrorCodes.EEXIST();
			}
			MemoryPath parent = FuseErrandsTxt.MemoryFSAdapter.find(path.substring(0, path.lastIndexOf("/")),
					errandsTxtFile, rootDirectory);
			if (parent instanceof RootDirectory) {
				return 0;
			}
			return -ErrorCodes.ENOENT();
		}

		@Override
		public int getattr(String path, StatWrapper stat) {
			MemoryPath p = FuseErrandsTxt.MemoryFSAdapter.find(path, errandsTxtFile, rootDirectory);
			if (p != null) {
				if (p instanceof ErrandsTxtFile) {
					stat.setMode(NodeType.FILE).size(((ErrandsTxtFile) p).contentsBytes.capacity());
				} else {
					stat.setMode(NodeType.DIRECTORY);
				}
				return 0;
			}
			return -ErrorCodes.ENOENT();
		}

		@Override
		public int open(String path, FileInfoWrapper info) {
			return 0;
		}

		@Override
		public int read(String path, ByteBuffer buffer, long size, long offset, FileInfoWrapper info) {
			MemoryPath p = FuseErrandsTxt.MemoryFSAdapter.find(path, errandsTxtFile, rootDirectory);
			if (p == null) {
				return -ErrorCodes.ENOENT();
			}
			if (!(p instanceof ErrandsTxtFile)) {
				return -ErrorCodes.EISDIR();
			}
			return ((ErrandsTxtFile) p).read(buffer, size, offset, contentsBytes, errandsTxtFile);
		}

		@Override
		public int readdir(String path, DirectoryFiller filler) {
			MemoryPath p = FuseErrandsTxt.MemoryFSAdapter.find(path, errandsTxtFile, rootDirectory);
			if (p == null) {
				return -ErrorCodes.ENOENT();
			}
			if (!(p instanceof RootDirectory)) {
				return -ErrorCodes.ENOTDIR();
			}
			filler.add(errandsTxtFile.txtFilename);
			return 0;
		}

		@Override
		public int truncate(String path, long offset) {
			MemoryPath p = MemoryFSAdapter.find(path, errandsTxtFile, rootDirectory);
			if (p == null) {
				return -ErrorCodes.ENOENT();
			}
			if (!(p instanceof ErrandsTxtFile)) {
				return -ErrorCodes.EISDIR();
			}
			((ErrandsTxtFile) p).truncate(offset, contentsBytes, errandsTxtFile);
			return 0;
		}

		@Override
		public int unlink(String path) {
			return -ErrorCodes.ENOENT();
		}

		@Override
		public int write(String path, ByteBuffer buf, long bufSize, long writeOffset, FileInfoWrapper wrapper) {
			MemoryPath p = FuseErrandsTxt.MemoryFSAdapter.find(path, errandsTxtFile, rootDirectory);
			if (p == null) {
				return -ErrorCodes.ENOENT();
			}
			if (!(p instanceof ErrandsTxtFile)) {
				return -ErrorCodes.EISDIR();
			}
			return ((ErrandsTxtFile) p).write(buf, bufSize, writeOffset, contentsBytes);
		}

		static MemoryPath find(String path, ErrandsTxtFile errandsTxtFile2, RootDirectory rootDir) {
			String path1 = path;
			MemoryPath ret;
			while (path1.startsWith("/")) {
				path1 = path1.substring(1);
			}
			if (path1.equals(rootDirName) || path1.isEmpty()) {
				ret = rootDir;
			} else {
				ret = null;
			}
			MemoryPath find2 = ret;
			if (find2 != null) {
				return find2;
			}
			while (path.startsWith("/")) {
				path = path.substring(1);
			}
			synchronized (rootDir) {
				if (!path.contains("/")) {
					String name2 = errandsTxtFile2.txtFilename;
					if (name2.equals(path)) {
						return errandsTxtFile2;
					}
					return null;
				}
				String nextName = path.substring(0, path.indexOf("/"));
				String rest = path.substring(path.indexOf("/"));
				if (errandsTxtFile2.txtFilename.equals(nextName)) {
					return errandsTxtFile2.find(rest);
				}
			}
			return null;
		}
	}
}