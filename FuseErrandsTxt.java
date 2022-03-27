import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

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

		private final RootDirectory rootDirectory = new RootDirectory("");

		MemoryFSAdapter(String location, String filename, String fileContents) {
			String text = fileContents;
			byte[] bytes = {};
			try {
				bytes = text.getBytes("UTF-8");
			} catch (UnsupportedEncodingException e1) {
				// Not going to happen
			}
			ByteBuffer wrap = ByteBuffer.wrap(bytes);
			ErrandsTxtFile errandsTxtFile = new ErrandsTxtFile(filename, wrap);
			rootDirectory.files.add(errandsTxtFile);
			try {
				this.log(true).mount(location);
			} catch (FuseException e) {
				e.printStackTrace();
			}
		}

		private final class RootDirectory extends MemoryPath {
			private final List<ErrandsTxtFile> files = new ArrayList<ErrandsTxtFile>();

			private String name;

			private RootDirectory(String name) {
				this.name = name;
			}

			@Override
			protected MemoryPath find(String path) {
				String path1 = path;
				FuseErrandsTxt.MemoryFSAdapter.MemoryPath ret;
				while (path1.startsWith("/")) {
					path1 = path1.substring(1);
				}
				if (path1.equals(name) || path1.isEmpty()) {
					ret = this;
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
				synchronized (this) {
					if (!path.contains("/")) {
						for (ErrandsTxtFile file : files) {
							String name2 = file.name;
							if (name2.equals(path)) {
								return file;
							}
						}
						return null;
					}
					String nextName = path.substring(0, path.indexOf("/"));
					String rest = path.substring(path.indexOf("/"));
					for (ErrandsTxtFile p : files) {
						if (p.name.equals(nextName)) {
							return p.find(rest);
						}
					}
				}
				return null;
			}

//			@Override
			private void getattr(StatWrapper stat) {
				stat.setMode(NodeType.DIRECTORY);
			}
		}

		private final class ErrandsTxtFile extends MemoryPath {
			private String name;
			private ByteBuffer contents = ByteBuffer.allocate(0);

			private ErrandsTxtFile(String filename, RootDirectory rootDir) {
				if (filename == null) {
					System.out
							.println("FuseErrandsTxt.MemoryFSAdapter.ErrandsTxtFile.ErrandsTxtFile() develoepr error");
					System.exit(-1);
				}
				name = filename;
			}

			public ErrandsTxtFile(String name, ByteBuffer contentsBytes) {
				this.contents = contentsBytes;
				this.name = name;
			}

//			@Override
			protected void getattr(StatWrapper stat) {
				stat.setMode(NodeType.FILE).size(contents.capacity());
			}

			private int read(ByteBuffer buffer, long size, long offset) {
				int bytesToRead = (int) Math.min(contents.capacity() - offset, size);
				byte[] bytesRead = new byte[bytesToRead];
				synchronized (this) {
					contents.position((int) offset);
					contents.get(bytesRead, 0, bytesToRead);
					buffer.put(bytesRead);
					contents.position(0); // Rewind
				}
				return bytesToRead;
			}

			private synchronized void truncate(long size) {
				if (size < contents.capacity()) {
					// Need to create a new, smaller buffer
					ByteBuffer newContents = ByteBuffer.allocate((int) size);
					byte[] bytesRead = new byte[(int) size];
					contents.get(bytesRead);
					newContents.put(bytesRead);
					contents = newContents;
				}
			}

			private int write(ByteBuffer buffer, long bufSize, long writeOffset) {
				int maxWriteIndex = (int) (writeOffset + bufSize);
				byte[] bytesToWrite = new byte[(int) bufSize];
				synchronized (this) {
					if (maxWriteIndex > contents.capacity()) {
						// Need to create a new, larger buffer
						ByteBuffer newContents = ByteBuffer.allocate(maxWriteIndex);
						newContents.put(contents);
						contents = newContents;
					}
					buffer.get(bytesToWrite, 0, (int) bufSize);
					contents.position((int) writeOffset);
					contents.put(bytesToWrite);
					contents.position(0); // Rewind
				}
				return (int) bufSize;
			}
		}

		@Deprecated // don't use superclasses
		private abstract class MemoryPath {

			private String name;

			@Deprecated
			protected MemoryPath find(String path) {
				while (path.startsWith("/")) {
					path = path.substring(1);
				}
				if (path.equals(name) || path.isEmpty()) {
					return this;
				}
				return null;
			}

//			protected abstract void getattr(StatWrapper stat);
		}

		@Override
		public int access(String path, int access) {
			return 0;
		}

		@Override
		public int create(String path, ModeWrapper mode, FileInfoWrapper info) {
			if (rootDirectory.find(path) != null) {
				return -ErrorCodes.EEXIST();
			}
			MemoryPath parent = getParentPath(path);
			if (parent instanceof RootDirectory) {
				FuseErrandsTxt.MemoryFSAdapter.RootDirectory memoryDirectory = (RootDirectory) parent;
				memoryDirectory.files.add(new ErrandsTxtFile(getLastComponent(path), memoryDirectory));
				return 0;
			}
			return -ErrorCodes.ENOENT();
		}

		private MemoryPath getParentPath(String path) {
			return rootDirectory.find(path.substring(0, path.lastIndexOf("/")));
		}

		@Override
		public int getattr(String path, StatWrapper stat) {
			MemoryPath p = rootDirectory.find(path);
			if (p != null) {
				if (p instanceof ErrandsTxtFile) {
					((ErrandsTxtFile) p).getattr(stat);
				} else {
					((RootDirectory) p).getattr(stat);
				}

				return 0;
			}
			return -ErrorCodes.ENOENT();
		}

		private String getLastComponent(String path) {
			while (path.substring(path.length() - 1).equals("/")) {
				path = path.substring(0, path.length() - 1);
			}
			if (path.isEmpty()) {
				return "";
			}
			return path.substring(path.lastIndexOf("/") + 1);
		}

		@Override
		public int open(String path, FileInfoWrapper info) {
			return 0;
		}

		@Override
		public int read(String path, ByteBuffer buffer, long size, long offset, FileInfoWrapper info) {
			MemoryPath p = rootDirectory.find(path);
			if (p == null) {
				return -ErrorCodes.ENOENT();
			}
			if (!(p instanceof ErrandsTxtFile)) {
				return -ErrorCodes.EISDIR();
			}
			return ((ErrandsTxtFile) p).read(buffer, size, offset);
		}

		@Override
		public int readdir(String path, DirectoryFiller filler) {
			MemoryPath p = rootDirectory.find(path);
			if (p == null) {
				return -ErrorCodes.ENOENT();
			}
			if (!(p instanceof RootDirectory)) {
				return -ErrorCodes.ENOTDIR();
			}
			FuseErrandsTxt.MemoryFSAdapter.RootDirectory memoryDirectory = (RootDirectory) p;
			for (FuseErrandsTxt.MemoryFSAdapter.MemoryPath p1 : memoryDirectory.files) {
				filler.add(p1.name);
			}
			return 0;
		}

		@Override
		public int truncate(String path, long offset) {
			MemoryPath p = rootDirectory.find(path);
			if (p == null) {
				return -ErrorCodes.ENOENT();
			}
			if (!(p instanceof ErrandsTxtFile)) {
				return -ErrorCodes.EISDIR();
			}
			((ErrandsTxtFile) p).truncate(offset);
			return 0;
		}

		@Override
		public int unlink(String path) {
			return -ErrorCodes.ENOENT();
		}

		@Override
		public int write(String path, ByteBuffer buf, long bufSize, long writeOffset, FileInfoWrapper wrapper) {
			MemoryPath p = rootDirectory.find(path);
			if (p == null) {
				return -ErrorCodes.ENOENT();
			}
			if (!(p instanceof ErrandsTxtFile)) {
				return -ErrorCodes.EISDIR();
			}
			return ((ErrandsTxtFile) p).write(buf, bufSize, writeOffset);
		}
	}
}