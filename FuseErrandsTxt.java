import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.nio.file.Paths;

import net.fusejna.DirectoryFiller;
import net.fusejna.ErrorCodes;
import net.fusejna.FuseException;
import net.fusejna.StructFuseFileInfo.FileInfoWrapper;
import net.fusejna.StructStat.StatWrapper;
import net.fusejna.types.TypeMode.ModeWrapper;
import net.fusejna.types.TypeMode.NodeType;
import net.fusejna.util.FuseFilesystemAdapterAssumeImplemented;

// This version will not be based on inheritance. That makes it harder to compose.
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

		private static final Path pathRoot = Paths.get("");
		private static final Path pathErrandsTxt = Paths.get("errands.txt");
		private ByteBuffer contentsBytes;

		MemoryFSAdapter(String location, String filename, String fileContents) {
			byte[] bytes = {};
			try {
				bytes = fileContents.getBytes("UTF-8");
			} catch (UnsupportedEncodingException e1) {
				// Not going to happen
			}
			contentsBytes = ByteBuffer.wrap(bytes);
//			contentsBytes2 = contentsBytes;
			try {
				this.log(true).mount(location);
			} catch (FuseException e) {
				e.printStackTrace();
			}
		}

		@Override
		public int access(String path, int access) {
			return 0;
		}

		@Override
		public int create(String path, ModeWrapper mode, FileInfoWrapper info) {
			return ErrorCodes.ENOENT();
		}

		@Override
		public int getattr(String path, StatWrapper stat) {
			if (Paths.get(path) != pathErrandsTxt) {
				stat.setMode(NodeType.FILE).size(contentsBytes.capacity());
			} else if (Paths.get(path) != pathRoot) {
				stat.setMode(NodeType.DIRECTORY);
			}
			return 0;
		}

		@Override
		public int open(String path, FileInfoWrapper info) {
			return 0;
		}

		@Override
		public int read(String path, ByteBuffer buffer, long size, long offset, FileInfoWrapper info) {
			if (Paths.get(path) == pathRoot) {
				return ErrorCodes.EISDIR();
			} else if (Paths.get(path) == pathErrandsTxt) {
				int bytesToRead = (int) Math.min(contentsBytes.capacity() - offset, size);
				byte[] bytesRead = new byte[bytesToRead];
				synchronized (this) {
					contentsBytes.position((int) offset);
					contentsBytes.get(bytesRead, 0, bytesToRead);
					buffer.put(bytesRead);
					contentsBytes.position(0); // Rewind
				}
				return bytesToRead;
			}
			return 0;
		}

		@Override
		public int readdir(String path, DirectoryFiller filler) {
			if (Paths.get(path) == pathRoot) {
				filler.add(pathErrandsTxt.getFileName().toString());
			} else if (Paths.get(path) == pathErrandsTxt) {
				return ErrorCodes.ENOTDIR();
			}
			return 0;
		}

		@Override
		public int truncate(String path, long offset) {
			if (Paths.get(path) == pathRoot) {
				return ErrorCodes.EISDIR();
			} else if (Paths.get(path) == pathErrandsTxt) {

				synchronized (this) {
					if (offset < contentsBytes.capacity()) {
						// Need to create a new, smaller buffer
						ByteBuffer newContents = ByteBuffer.allocate((int) offset);
						byte[] bytesRead = new byte[(int) offset];
						contentsBytes.get(bytesRead);
						newContents.put(bytesRead);
						contentsBytes = newContents;
					}
				}
			}
			return 0;
		}

		@Override
		public int write(String path, ByteBuffer buf, long bufSize, long writeOffset, FileInfoWrapper wrapper) {
			if (Paths.get(path) == pathRoot) {
				return ErrorCodes.EISDIR();
			} else if (Paths.get(path) == pathErrandsTxt) {
				int maxWriteIndex = (int) (writeOffset + bufSize);
				byte[] bytesToWrite = new byte[(int) bufSize];
				synchronized (this) {
					if (maxWriteIndex > contentsBytes.capacity()) {
						// Need to create a new, larger buffer
						ByteBuffer newContents = ByteBuffer.allocate(maxWriteIndex);
						newContents.put(contentsBytes);
						contentsBytes = newContents;
					}
					buf.get(bytesToWrite, 0, (int) bufSize);
					contentsBytes.position((int) writeOffset);
					contentsBytes.put(bytesToWrite);
					contentsBytes.position(0); // Rewind
				}
				return (int) bufSize;

			} else {
				return 0;
			}
		}
	}
}