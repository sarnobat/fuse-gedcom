import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.google.common.base.Charsets;

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
//				System.out.println("MemoryFS.main() " + line);
				all += line + "\n";
			}
			new MemoryFSAdapter(args[0], "errands.txt", all);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private static class MemoryFSAdapter extends FuseFilesystemAdapterAssumeImplemented {

		private static final Path pathRoot = Paths.get("/").normalize();
		private final Path pathErrandsTxt;
		private ByteBuffer contentsBytes;

		MemoryFSAdapter(String location, String filename, String fileContents) {
			// Strange, it won't work unless I put slash
			pathErrandsTxt = Paths.get("/errands.txt").normalize();
			byte[] bytes = {};
			try {
				bytes = fileContents.getBytes("UTF-8");
			} catch (UnsupportedEncodingException e1) {
				// Not going to happen
			}
			contentsBytes = ByteBuffer.wrap(bytes);
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
			System.out.println("FuseErrandsTxt.MemoryFSAdapter.create() SRIDHAR " + path);
			return ErrorCodes.ENOENT();
		}

		@Override
		public int getattr(String path, StatWrapper stat) {
			System.out.println("SRIDHAR FuseErrandsTxt.MemoryFSAdapter.getattr() path           = " + path);
			System.out.println("SRIDHAR FuseErrandsTxt.MemoryFSAdapter.getattr() pathErrandsTxt = " + pathErrandsTxt);
			if (Paths.get(path).equals(pathErrandsTxt)) {
				System.out.println("FuseErrandsTxt.MemoryFSAdapter.getattr() - file: " + path);
				stat.setMode(NodeType.FILE).size(contentsBytes.capacity());
			} else {
				System.out.println("FuseErrandsTxt.MemoryFSAdapter.getattr() - directory: " + path);
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
			if (Paths.get(path).normalize().equals(pathRoot)) {
				return ErrorCodes.EISDIR();
			} else if (Paths.get(path).normalize().equals(pathErrandsTxt)) {
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
			System.out.println("SRIDHAR FuseErrandsTxt.MemoryFSAdapter.read() path     = " + path);
			System.out.println("SRIDHAR FuseErrandsTxt.MemoryFSAdapter.read() pathRoot = " + pathRoot);
			if (pathRoot.equals(Paths.get(path).normalize())) {
				filler.add("errands.txt");
			} else if (Paths.get(path).normalize().equals(pathErrandsTxt)) {
				return ErrorCodes.ENOTDIR();
			}
			return 0;
		}

		@Override
		public int truncate(String path, long offset) {
			if (Paths.get(path).normalize().equals(pathRoot)) {
				return ErrorCodes.EISDIR();
			} else if (Paths.get(path).normalize().equals(pathErrandsTxt)) {

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

			if (Paths.get(path).normalize().equals(pathRoot)) {
				return ErrorCodes.EISDIR();
			} else if (Paths.get(path).normalize().equals(pathErrandsTxt)) {
				System.out.println(
						"SRIDHAR FuseErrandsTxt.MemoryFSAdapter.write() writeOffset writeOffset = " + writeOffset);
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
				{
					String[] s = new String(contentsBytes.array(), Charsets.UTF_8).split("\\n");
					for (String line : s) {
						if (Paths.get(line).toFile().exists()) {
							System.out.println("SRIDHAR FuseErrandsTxt.MemoryFSAdapter.write() already exists: " + line);
						}else {
							// it got changed
							System.out.println("!!!!!!!!!SRIDHAR FuseErrandsTxt.MemoryFSAdapter.write() edited: " + line);
						}
					}
				}
				return (int) bufSize;

			} else {
				return 0;
			}
		}
	}
}