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

public class App extends FuseFilesystemAdapterFull {

	private static Map<String, Individual> childToMother = new HashMap<>();
	private static Map<String, Individual> childToFather = new HashMap<>();
	private static Map<Individual, String> individualToChildFamilyId = new HashMap<>();
	private static Map<String, String> displayNameOfChildToParent = new HashMap<>();
	private static Map<String, Individual> idToIndividual = new HashMap<>();
	@Deprecated
	private static Map<String, Individual> displayNameToIndividual = new HashMap<>();
	private static Map<String, Individual> displayNameToIndividualWithSpouse = new HashMap<>();
	private static Map<String, Family> idToFamily = new HashMap<>();
	private static Set<Individual> individualsWithNoParent = new HashSet<>();
	@Deprecated
	// we are mixing different marriages
	private static Multimap<String, Individual> displayNameToChildren = HashMultimap.create();
	private static Multimap<String, Individual> displayNameToChildrenWithSpouse = HashMultimap.create();

	private static final String ROOT_ID = "I29";

	public static void main(String[] args) throws FuseException, IOException {
		boolean showSpouses = Boolean.parseBoolean(System.getProperty("spouses", "true"));
		// Strange - groovy ignores arg1's hardcoding. Maybe it's not an
		// acceptable
		// array initialization in groovy?
		// final String[] args1 = args;// {
		// "/sarnobat.garagebandbroken/trash/fuse-jna/mnt" };
		System.out.println("App.main() 1");
		if (args.length == 1) {
			new App().log(true).mount(args[0]);
		} else {
			System.err.println("Usage: HelloFS <mountpoint>");
			String string = "family_tree";
			//String string2 = System.getProperty("user.home") + "/github/fuse-java/proj/" + string;
			String string2 = System.getProperty("dir");
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
					//File myObj = new File(System.getProperty("user.home") + "/sarnobat.git/gedcom/rohidekar.ged");
					File myObj = new File(System.getProperty("gedcom"));
					Scanner myReader;

					try {
						System.out.println("App.main.run() 2");
						myReader = new Scanner(myObj);

						System.out.println("App.main.run() 3");
					} catch (FileNotFoundException e) {
						throw new RuntimeException(e);
					}
					Individual individual = null;
					Family family = null;
					System.out.println("App.main.run() 4");
					while (myReader.hasNextLine()) {
//						System.out.println("App.main.run() 5");
						String data = myReader.nextLine();
						if (data.startsWith("0") && data.endsWith("INDI")) {

							if (individual != null) {
								// System.out.println(individual.toString());
							}
							String regex = "0..(.*)..INDI";
							Pattern p = Pattern.compile(regex);
							Matcher matcher = p.matcher(data);
							if (matcher.find()) {
								String s = matcher.group(1);
								individual = new Individual(s);
								idToIndividual.put(s, individual);
							} else {
								throw new RuntimeException("Developer error");
							}
							continue;
						}
						if (individual == null) {
							continue;
						}
						if (data.startsWith("2 GIVN")) {
							String replaceAll = data.replaceAll(".*GIVN ", "");
							individual.setFirstName(replaceAll);
						} else if (data.startsWith("2 SURN")) {
							String replaceAll = data.replaceAll(".*SURN ", "");
							individual.setLastName(replaceAll);
							displayNameToIndividual.put(individual.toString(), individual);
						} else if (data.startsWith("0") && data.endsWith("FAM")) {
							String regex = "0..(.*)..FAM";
							Pattern p = Pattern.compile(regex);
							Matcher matcher = p.matcher(data);
							if (matcher.find()) {
								String s = matcher.group(1);
								family = new Family(s);
								idToFamily.put(s, family);
							} else {
								throw new RuntimeException("Developer error");
							}
						} else if (data.startsWith("1 FAMS")) {
							String replaceAll = data.replaceAll("1 FAMS .", "").replaceAll(".\044", "");
							individualToChildFamilyId.put(individual, replaceAll);
						} else if (data.startsWith("1 HUSB")) {
							String replaceAll = data.replaceAll(".*HUSB .", "").replaceAll(".\044", "");
							Individual husband = idToIndividual.get(replaceAll);
							family.setHusband(husband);
						} else if (data.startsWith("1 WIFE")) {
							String replaceAll = data.replaceAll(".*WIFE .", "").replaceAll(".\044", "");
							family.setWife(idToIndividual.get(replaceAll));
						} else if (data.startsWith("1 CHIL")) {
							String replaceAll = data.replaceAll(".*CHIL .", "").replaceAll(".\044", "");
							Individual i = idToIndividual.get(replaceAll);
							family.addChild(i);
							i.setParentFamily(family);
						}
					}
					myReader.close();
					if (idToFamily.size() < 88) {
						throw new RuntimeException("missing families");
					}
					if (idToIndividual.size() < 256) {
						throw new RuntimeException("missing individual");
					}
					// if (!idToIndividual.keySet().contains("F10")) {
					// throw new RuntimeException();
					// }
					// attach each individual to its family
					for (Individual i : individualToChildFamilyId.keySet()) {
						Family f = idToFamily.get(individualToChildFamilyId.get(i));
						i.setChildFamily(f);
						i.addChildFamily(f);
						// System.out.println("Has parent: " + i.toString());
					}
					for (Family f : idToFamily.values()) {
// 						System.out .println("SRIDHAR App.main.run() family father = " + f.getHusband().toString() + "\thas " + f.getChildren().size() + " children: " + f.getChildren().toString());
						f.getHusband().setSpouse(f.getWife());
						f.getWife().setSpouse(f.getHusband());
						for (Individual child : f.getChildren()) {

							if ("I119".equals(child.getId())) {

							}
							childToFather.put(child.getId(), f.getHusband());
							childToMother.put(child.getId(), f.getWife());

							displayNameToChildren.put(f.getHusband().toString(), child);
							displayNameToChildren.put(f.getWife().toString(), child);
							displayNameToChildrenWithSpouse.put(f.getHusband().toString(), child);
							displayNameToChildrenWithSpouse.put(f.getWife().toString(), child);

						}
						if (!f.getHusband().toString().contains("--")) {
							System.err.println("SRIDHAR App.run() missing " + f.getHusband().toString());
							System.exit(-1);
						}
					}
					for (Family f : idToFamily.values()) {
						displayNameToIndividualWithSpouse.put(f.getHusband().toString(), f.getHusband());
						displayNameToIndividualWithSpouse.put(f.getWife().toString(), f.getWife());
					}
					for (String id : idToIndividual.keySet()) {
						if (!childToFather.containsKey(id) && !childToMother.containsKey(id)) {
							System.out.println(id + " has no parents :" + idToIndividual.get(id));
						}
					}

					if (displayNameToChildrenWithSpouse.size() < 20) {
						throw new RuntimeException();
					}
					if (!idToIndividual.keySet().contains(ROOT_ID)) {
						throw new RuntimeException("Missing root ID " + ROOT_ID);
					}

					String o = "Venkat Rao Rohidekar I29 -- Tarabai  I30";
					if (!displayNameToIndividualWithSpouse.keySet().contains(o)) {
						throw new RuntimeException("developer error");
					}

					Individual child = displayNameToIndividualWithSpouse.get(o);
					if (!displayNameToIndividualWithSpouse.containsKey(child.toString())) {
						for (String s : displayNameToIndividualWithSpouse.keySet()) {
							System.out.println("// SRIDHAR App.main.run() " + s);
						}
						throw new RuntimeException("");
					}
					// I24 - root
					System.out.println(printFamily(idToIndividual.get(ROOT_ID).getChildFamily(), ""));
				}

			}.run();
			System.out.println("App.main() 5");
			new App().log(false).mount(string);
		}
	}

	private static String printFamily(Family f, String string) {
		if (f == null) {
			return "";
		}
		String s = "";// " " + f.getWife().toString();
		for (Individual c : f.getChildren()) {
			if (c == f.getHusband()) {
				throw new RuntimeException("infinite loop");
			}
			s += "\n" + string + c.toString();// + (c.getSpouse() == null ? "" : " -- " + c.getSpouse().toString());
//			Family childFamily1 = c.getChildFamily();
//			s += printFamily(childFamily1, string + "  ");
			for (Family childFamily : c.getChildFamilies()) {
				s += printFamily(childFamily, string + "  ");
			}
		}
		return s;
	}

	// TODO: rename this to Marriage
	private static class Family {
		private final String id;
		private Individual husband;
		private Individual wife;
		private final Set<Individual> children = new HashSet<>();

		Individual getHusband() {
			return husband;
		}

		public Collection<Individual> getChildren() {
			return children;
		}

		public String getId() {
			return id;
		}

		public void addChild(Individual i) {
			children.add(i);
		}

		void setHusband(Individual husband) {
			this.husband = husband;
		}

		Individual getWife() {
			return wife;
		}

		void setWife(Individual wife) {
			this.wife = wife;
		}

		Family(String id) {
			this.id = id;
		}

		@Override
		public String toString() {
			String string = id + "  " + husband.toString() + " -- " + wife.toString();
			if (children.size() > 0) {
				string += " (";
				for (Individual i : children) {
					string += "," + i.toString();
				}
			}
			string += ")";
			return string;
		}
	}

	private static class Individual {
		private final String id;

		String firstName;
		// TODO: this should be a collection of child families
		@Deprecated
		Family childFamily;
		Map<String, Family> childFamilies = new HashMap<>();
		Family parentFamily;
		Individual spouse;

		@Deprecated
		Family getChildFamily() {
			return childFamily;
		}

		public Iterable<Family> getChildFamilies() {
			return childFamilies.values();
		}

		public String getId() {
			return id;
		}

		public void setSpouse(Individual husband) {
			this.spouse = husband;
		}

		public Individual getSpouse() {
			return this.spouse;
		}

		@Deprecated
		void setChildFamily(Family childFamily) {
			this.childFamily = childFamily;
		}

		void addChildFamily(Family childFamily) {
			this.childFamilies.put(childFamily.getId(), childFamily);
		}

		Family getParentFamily() {
			return parentFamily;
		}

		void setParentFamily(Family parentFamily) {
			this.parentFamily = parentFamily;
		}

		Individual(String id) {
			this.id = id;
		}

		String getFirstName() {
			return firstName == null ? "" : firstName;
		}

		void setFirstName(String firstName) {
			this.firstName = firstName;
		}

		String getLastName() {
			return lastName == null ? "" : lastName;
		}

		void setLastName(String lastName) {
			this.lastName = lastName;
		}

		String lastName;

		@Override
		public String toString() {
			String string = spouse == null ? ""
					: " -- " + spouse.getFirstName() + " " + spouse.getLastName() + " " + spouse.id;
			String string2 = getFirstName() + " " + getLastName() + " ";
			Boolean showId = Boolean.valueOf(System.getProperty("showid", "true"));
			if (showId) {
				string2 += id + string;
			} else {
				if (getFirstName().isBlank() && getLastName().isBlank()) {
					string2 += id + string;
				}
			}
			return string2;
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
					// System.out.println("SRIDHAR App.getattr() DIRECTORY: " +
					// path);
					stat.setMode(NodeType.DIRECTORY);
					return 0;
				} else {
					// System.out.println("SRIDHAR App.getattr() lastPartOf = "
					// + lastPartOf);
					// System.out.println("SRIDHAR App.getattr() FILE: " +
					// path);
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
				Individual individual = idToIndividual.get(ROOT_ID);
				String string = individual.toString();
				filler.add(string);
				for (Family childFamily : individual.getChildFamilies()) {
					for (Individual c : childFamily.getChildren()) {
						displayNameOfChildToParent.put(c.toString(), string);
					}
				}
			} else {
				String s = Paths.get(path).getFileName().toString();
				System.out.println("SRIDHAR App.readdir() " + s);
				Individual child = displayNameToIndividualWithSpouse.get(s);
				for (String key : displayNameToIndividualWithSpouse.keySet()) {
					System.out.println("SRIDHAR App.readdir() " + key);
				}
				if (!displayNameToIndividualWithSpouse.containsKey(child.toString())) {
					// throw new RuntimeException("");
					System.out.println("error: " + child.toString() + " not found");
					System.exit(-1);

				}
				Collection<Individual> collection = displayNameToChildrenWithSpouse.get(child.toString());
				for (Individual i : collection) {
					filler.add(i.toString());
				}
				// for (Entry<String, String> childToParent :
				// displayNameOfChildToParent.entrySet()) {
				// String parent = childToParent.getValue();
				// if (parent.equals(path)) {
				// filler.add(childToParent.getKey());
				// }
				// }
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
