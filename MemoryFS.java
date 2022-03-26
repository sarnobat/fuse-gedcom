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

public class MemoryFS {

	public static void main(final String... args) throws FuseException {
		if (args.length != 1) {
			System.err.println("Usage: MemoryFS <mountpoint>");
			System.exit(1);
		}
		new MemoryFSAdapter(args[0], "errands.txt", "Hello there, feel free to look around.\n");
	}

	private static class MemoryFSAdapter extends FuseFilesystemAdapterAssumeImplemented {

		private final MemoryDirectory rootDirectory = new MemoryDirectory("");

		MemoryFSAdapter(String location, String filename, String fileContents) {
			byte[] contents = getContents(fileContents);
			ByteBuffer wrap = ByteBuffer.wrap(contents);
			ErrandsTxtFile errandsTxtFile = new ErrandsTxtFile(filename, fileContents, contents, wrap);
			rootDirectory.contents.add((MemoryFS.MemoryFSAdapter.MemoryPath) errandsTxtFile);
			try {
				this.log(true).mount(location);
			} catch (FuseException e) {
				e.printStackTrace();
			}
		}

		private byte[] getContents(final String text) {
			byte[] bytes = {};
			try {
				bytes = text.getBytes("UTF-8");
			} catch (final UnsupportedEncodingException e) {
				// Not going to happen
			}
			return bytes;
		}

		private final class MemoryDirectory extends MemoryPath {
			private final List<MemoryPath> contents = new ArrayList<MemoryPath>();

			private MemoryDirectory(final String name) {
				super(name);
			}

			@Override
			protected MemoryPath find(String path) {
				if (super.find(path) != null) {
					return super.find(path);
				}
				while (path.startsWith("/")) {
					path = path.substring(1);
				}
				synchronized (this) {
					if (!path.contains("/")) {
						for (final MemoryPath p : contents) {
							if (p.name.equals(path)) {
								return p;
							}
						}
						return null;
					}
					final String nextName = path.substring(0, path.indexOf("/"));
					final String rest = path.substring(path.indexOf("/"));
					for (final MemoryPath p : contents) {
						if (p.name.equals(nextName)) {
							return p.find(rest);
						}
					}
				}
				return null;
			}

			@Override
			protected void getattr(final StatWrapper stat) {
				stat.setMode(NodeType.DIRECTORY);
			}
		}

		private final class ErrandsTxtFile extends MemoryPath {
			private ByteBuffer contents = ByteBuffer.allocate(0);

			private ErrandsTxtFile(final String name, final MemoryDirectory parent) {
				super(name, parent);
			}

			public ErrandsTxtFile(final String name, final String fileContents, byte[] contents2, ByteBuffer bb) {
				super(name);
				contents = bb;
			}

			@Override
			protected void getattr(final StatWrapper stat) {
				stat.setMode(NodeType.FILE).size(contents.capacity());
			}

			private int read(final ByteBuffer buffer, final long size, final long offset) {
				final int bytesToRead = (int) Math.min(contents.capacity() - offset, size);
				final byte[] bytesRead = new byte[bytesToRead];
				synchronized (this) {
					contents.position((int) offset);
					contents.get(bytesRead, 0, bytesToRead);
					buffer.put(bytesRead);
					contents.position(0); // Rewind
				}
				return bytesToRead;
			}

			private synchronized void truncate(final long size) {
				if (size < contents.capacity()) {
					// Need to create a new, smaller buffer
					final ByteBuffer newContents = ByteBuffer.allocate((int) size);
					final byte[] bytesRead = new byte[(int) size];
					contents.get(bytesRead);
					newContents.put(bytesRead);
					contents = newContents;
				}
			}

			private int write(final ByteBuffer buffer, final long bufSize, final long writeOffset) {
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
					contents.position(0); // Rewind
				}
				return (int) bufSize;
			}
		}

		private abstract class MemoryPath {
			private String name;

			private MemoryPath(final String name) {
				this(name, null);
			}

			private MemoryPath(final String name, final MemoryDirectory parent) {
				this.name = name;
			}

			protected MemoryPath find(String path) {
				while (path.startsWith("/")) {
					path = path.substring(1);
				}
				if (path.equals(name) || path.isEmpty()) {
					return this;
				}
				return null;
			}

			protected abstract void getattr(StatWrapper stat);
		}

		@Override
		public int access(final String path, final int access) {
			return 0;
		}

		@Override
		public int create(final String path, final ModeWrapper mode, final FileInfoWrapper info) {
			if (getPath(path) != null) {
				return -ErrorCodes.EEXIST();
			}
			MemoryPath parent = getParentPath(path);
			if (parent instanceof MemoryDirectory) {
				MemoryFS.MemoryFSAdapter.MemoryDirectory memoryDirectory = (MemoryDirectory) parent;
				memoryDirectory.contents.add(new ErrandsTxtFile(getLastComponent(path), memoryDirectory));
				return 0;
			}
			return -ErrorCodes.ENOENT();
		}

		private MemoryPath getParentPath(final String path) {
			return rootDirectory.find(path.substring(0, path.lastIndexOf("/")));
		}

		@Override
		public int getattr(final String path, final StatWrapper stat) {
			final MemoryPath p = getPath(path);
			if (p != null) {
				p.getattr(stat);
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

		private MemoryPath getPath(final String path) {
			return rootDirectory.find(path);
		}

		@Override
		public int open(final String path, final FileInfoWrapper info) {
			return 0;
		}

		@Override
		public int read(final String path, final ByteBuffer buffer, final long size, final long offset,
				final FileInfoWrapper info) {
			final MemoryPath p = getPath(path);
			if (p == null) {
				return -ErrorCodes.ENOENT();
			}
			if (!(p instanceof ErrandsTxtFile)) {
				return -ErrorCodes.EISDIR();
			}
			return ((ErrandsTxtFile) p).read(buffer, size, offset);
		}

		@Override
		public int readdir(final String path, final DirectoryFiller filler) {
			final MemoryPath p = getPath(path);
			if (p == null) {
				return -ErrorCodes.ENOENT();
			}
			if (!(p instanceof MemoryDirectory)) {
				return -ErrorCodes.ENOTDIR();
			}
			MemoryFS.MemoryFSAdapter.MemoryDirectory memoryDirectory = (MemoryDirectory) p;
			for (final MemoryFS.MemoryFSAdapter.MemoryPath p1 : memoryDirectory.contents) {
				filler.add(p1.name);
			}
			return 0;
		}

		@Override
		public int truncate(final String path, final long offset) {
			final MemoryPath p = getPath(path);
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
		public int unlink(final String path) {
			return -ErrorCodes.ENOENT();
		}

		@Override
		public int write(final String path, final ByteBuffer buf, final long bufSize, final long writeOffset,
				final FileInfoWrapper wrapper) {
			final MemoryPath p = getPath(path);
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