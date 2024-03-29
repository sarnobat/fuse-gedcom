import java.io.File;
import java.nio.ByteBuffer;

import net.fusejna.DirectoryFiller;
import net.fusejna.ErrorCodes;
import net.fusejna.FuseException;
import net.fusejna.StructFuseFileInfo.FileInfoWrapper;
import net.fusejna.StructStat.StatWrapper;
import net.fusejna.types.TypeMode.NodeType;
import net.fusejna.util.FuseFilesystemAdapterFull;

public class HelloFS {
	public static void main(final String... args) throws FuseException {
		if (args.length != 1) {
			System.err.println("Usage: HelloFS <mountpoint>");
			System.exit(1);
		}
		new HelloFS1(args[0]);
	}

	static class HelloFS1 extends FuseFilesystemAdapterFull {
		final String filename = "/hello.txt";
		final String contents = "Hello World!\n";

		public HelloFS1(String string) {
			try {
				this.log(true).mount(string);
			} catch (FuseException e) {
				// TODO Auto-generated catch block
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

		@Override
		public int read(final String path, final ByteBuffer buffer, final long size, final long offset,
				final FileInfoWrapper info) {
			// Compute substring that we are being asked to read
			final String s = contents.substring((int) offset,
					(int) Math.max(offset, Math.min(contents.length() - offset, offset + size)));
			buffer.put(s.getBytes());
			return s.getBytes().length;
		}

		@Override
		public int readdir(final String path, final DirectoryFiller filler) {
			filler.add(filename);
			return 0;
		}
	}
}