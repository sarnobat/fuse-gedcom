import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Scanner;

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

	@SuppressWarnings("unused")
	public static void main(String... args) throws FuseException, IOException {
		if (args.length != 1) {
			System.err.println("Usage: MemoryFS <mountpoint>");
			System.exit(1);
		}
		String rootDirPath = args[0];
		Files.createDirectories(Paths.get(rootDirPath));

		try {
			// Add the inode number
			// | xargs --delimiter '\n' --max-args=1 ls -id
			String all = "";
			_getStdin: {
				BufferedReader br = new BufferedReader(
						new InputStreamReader(new BufferedInputStream(new ProcessBuilder()
								.command("bash", "-c",
										"find /Users/sarnobat/sarnobat.git/errands/ -type d "
												+ "| python3 /Users/sarnobat/src.git/python/yamlfs/yamlfs_stdin.py")
								.start().getInputStream())));

				String line;
				while ((line = br.readLine()) != null) {
//				System.out.println("MemoryFS.main() " + line);
					all += line + "\n";
				}
			}
			new MemoryFSAdapter(rootDirPath, "errands.txt", all);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private static class MemoryFSAdapter extends FuseFilesystemAdapterAssumeImplemented {

		private static final Path pathRoot = Paths.get("/").normalize();
		private final Path pathErrandsTxt;
		private ByteBuffer mutableErrandsTxtContent;

		MemoryFSAdapter(String location, String filename, String stdinContents) {
			// Strange, it won't work unless I put slash
			pathErrandsTxt = Paths.get("/errands.txt").normalize();
			byte[] stdinContentBytes = {};
			try {
				stdinContentBytes = stdinContents.getBytes("UTF-8");
			} catch (UnsupportedEncodingException e1) {
				// Not going to happen
			}
			mutableErrandsTxtContent = ByteBuffer.wrap(stdinContentBytes);
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
				stat.setMode(NodeType.FILE).size(mutableErrandsTxtContent.capacity());
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
				int bytesToRead = (int) Math.min(mutableErrandsTxtContent.capacity() - offset, size);
				byte[] bytesRead = new byte[bytesToRead];
				synchronized (this) {
					mutableErrandsTxtContent.position((int) offset);
					mutableErrandsTxtContent.get(bytesRead, 0, bytesToRead);
					buffer.put(bytesRead);
					mutableErrandsTxtContent.position(0); // Rewind
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
					if (offset < mutableErrandsTxtContent.capacity()) {
						// Need to create a new, smaller buffer
						ByteBuffer newContents = ByteBuffer.allocate((int) offset);
						byte[] bytesRead = new byte[(int) offset];
						mutableErrandsTxtContent.get(bytesRead);
						newContents.put(bytesRead);
						mutableErrandsTxtContent = newContents;
					}
				}
			}
			return 0;
		}

		@SuppressWarnings("unused")
		@Override
		public int write(String path, ByteBuffer buf, long bufSize, long writeOffset, FileInfoWrapper wrapper) {

			if (Paths.get(path).normalize().equals(pathRoot)) {
				return ErrorCodes.EISDIR();
			} else if (Paths.get(path).normalize().equals(pathErrandsTxt)) {
				System.out.println(
						"SRIDHAR FuseErrandsTxt.MemoryFSAdapter.write() writeOffset writeOffset = " + writeOffset);
				int maxWriteIndex = (int) (writeOffset + bufSize);
				byte[] bytesToWrite = new byte[(int) bufSize];
				_passthrough: {
					synchronized (this) {
						if (maxWriteIndex > mutableErrandsTxtContent.capacity()) {
							// Need to create a new, larger buffer
							ByteBuffer newContents = ByteBuffer.allocate(maxWriteIndex);
							newContents.put(mutableErrandsTxtContent);
							mutableErrandsTxtContent = newContents;
						}
						buf.get(bytesToWrite, 0, (int) bufSize);
						mutableErrandsTxtContent.position((int) writeOffset);
						mutableErrandsTxtContent.put(bytesToWrite);
						mutableErrandsTxtContent.position(0); // Rewind
					}
				}
				_realFS: {
					System.out.println("FuseErrandsTxt.MemoryFSAdapter.write() TODO: implement indented2path.py");
//					System.exit(-1);
					String[] txtNewContent = new String(mutableErrandsTxtContent.array(), Charsets.UTF_8).split("\\n");
					System.out.println("FuseErrandsTxt.MemoryFSAdapter.write() chopped");

					{
						try {
							Process downstream = new ProcessBuilder()
									.command("bash", "-c", "cat - | perl -pe 's{^}{SRIDHAR saving: }'").start();
							BufferedWriter writer = new BufferedWriter(
									new OutputStreamWriter(downstream.getOutputStream()));
							System.out.println("FuseErrandsTxt.MemoryFSAdapter.write() launched");
							Thread t = new Thread(() -> {
								for (String line : txtNewContent) {
									try {
//										System.out.println("FuseErrandsTxt.MemoryFSAdapter.write() flushing: " + line);
										writer.write(line);
										writer.newLine();
										writer.flush();
//										System.out.println("FuseErrandsTxt.MemoryFSAdapter.write() flushing done: ");
									} catch (IOException e) {
										e.printStackTrace();
									}
								}
								try {
									writer.close();
								} catch (IOException e) {
									// TODO Auto-generated catch block
									e.printStackTrace();
								}

							});
							t.start();
							BufferedReader br = new BufferedReader(
									new InputStreamReader(new BufferedInputStream(downstream.getInputStream())));
							String line;
							System.out.println("FuseErrandsTxt.MemoryFSAdapter.write() reading from process output");
							while ((line = br.readLine()) != null) {
								System.out.println("FuseErrandsTxt.MemoryFSAdapter.write() - downstream 4: " + line);
							}
							br.close();
						} catch (IOException e1) {
							e1.printStackTrace();
							// TODO: not sure what is the best thing to do at this point. Exit?
						}
					}
//					for (String txtLine : txtNewContent) {
//						// TODO: move this logic to GENERIC shell script
//						if (Paths.get(txtLine).toFile().exists()) {
//							System.err.println(
//									"SRIDHAR FuseErrandsTxt.MemoryFSAdapter.write() already exists, do nothing: "
//											+ txtLine);
//						} else {
//							// it got changed
//							System.out.println(
//									"!!!!!!!!!SRIDHAR FuseErrandsTxt.MemoryFSAdapter.write() edited: " + txtLine);
//						}
//					}
				}
				return (int) bufSize;

			} else {
				return 0;
			}
		}
	}
}