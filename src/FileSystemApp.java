import java.io.*;
import java.util.*;

class Block implements Serializable {
    public static final int BLOCK_SIZE = 256;  // Block size in bytes
    private byte[] data = new byte[BLOCK_SIZE]; // Actual block data

    public void writeData(byte[] newData, int offset) {
        System.arraycopy(newData, 0, data, offset, Math.min(newData.length, BLOCK_SIZE - offset));
    }

    public byte[] readData(int offset, int length) {
        return Arrays.copyOfRange(data, offset, Math.min(offset + length, BLOCK_SIZE));
    }
}

class File implements Serializable {
    private String fileName;
    private List<Integer> blockPointers = new ArrayList<>();
    private int size;

    public File(String fileName) {
        this.fileName = fileName;
        this.size = 0;
    }

    public void addBlock(int blockIndex) {
        blockPointers.add(blockIndex);
    }

    public List<Integer> getBlockPointers() {
        return blockPointers;
    }

    public String getFileName() {
        return fileName;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }
}

class Directory implements Serializable {
    private String name;
    public String path = "/";
    private Map<String, File> files = new HashMap<>();
    private Map<String, Directory> subDirectories = new HashMap<>();
    private Directory parent;

    public Directory(String name, Directory parent) {
        this.name = name;
        this.parent = parent;
        if (parent != null) {
            path = parent.path + name + "/";
        }
    }

    public String getName() {
        return name;
    }

    public Directory getParent() {
        return parent;
    }

    public Map<String, File> getFiles() {
        return files;
    }

    public Map<String, Directory> getSubDirectories() {
        return subDirectories;
    }

    public boolean addFile(File file) {
        if (!files.containsKey(file.getFileName())) {
            files.put(file.getFileName(), file);
            return true;
        }
        return false;
    }

    public File removeFile(String fileName) {
        if (files.containsKey(fileName)) {
            File tmp = files.remove(fileName);
            return tmp;
        }
        return null;
    }

    public boolean addDirectory(Directory dir) {
        if (!subDirectories.containsKey(dir.getName())) {
            subDirectories.put(dir.getName(), dir);
            return true;
        }
        return false;
    }
}

class SimpleFileSystem implements Serializable {
    //private static final int TOTAL_BLOCKS = 4096 * 5;  // File system size (1MB with 256-byte blocks)
    final int TOTAL_BLOCKS;
    Block[] blocks;
    BitSet bitMap;
    Directory rootDirectory = new Directory("/", null);
    Directory currentDirectory = rootDirectory;

    public SimpleFileSystem(int size) {
        TOTAL_BLOCKS = size;
        blocks = new Block[TOTAL_BLOCKS];
        bitMap = new BitSet(TOTAL_BLOCKS);
        for (int i = 0; i < TOTAL_BLOCKS; i++) {
            blocks[i] = new Block();
        }
    }

    public void changeDirectory(String dirName) {
        if (dirName.equals("..")) {
            if (currentDirectory.getParent() != null) {
                currentDirectory = currentDirectory.getParent();
            }
        } else if (currentDirectory.getSubDirectories().containsKey(dirName)) {
            currentDirectory = currentDirectory.getSubDirectories().get(dirName);
        } else {
            System.out.println("Directory not found: " + dirName);
        }
    }

    // List files and directories
    public void listDirectory() {
        for (String dirName : currentDirectory.getSubDirectories().keySet()) {
            System.out.println("[DIR] " + dirName);
        }
        for (String fileName : currentDirectory.getFiles().keySet()) {
            System.out.println("[FILE] " + fileName);
        }
    }

    public void listDirectorySize() {
        for (String dirName : currentDirectory.getSubDirectories().keySet()) {
            System.out.println("[DIR] " + dirName);
        }
        for (File file : currentDirectory.getFiles().values()) {
            System.out.println("[FILE] " + file.getFileName() + " ----- " + file.getSize());
        }
    }

    public void makeDirectory(String dirName) {
        if (!currentDirectory.getSubDirectories().containsKey(dirName)) {
            Directory newDir = new Directory(dirName, currentDirectory);
            currentDirectory.addDirectory(newDir);
        } else {
            System.out.println("Directory already exists: " + dirName);
        }
    }

    public void removeDirectory(String dirName) {
        if (currentDirectory.getSubDirectories().containsKey(dirName)) {
            currentDirectory.getSubDirectories().remove(dirName);
        } else {
            System.out.println("Directory not found: " + dirName);
        }
    }

}

// Main application
public class FileSystemApp {

    public static void main(String[] args) {
        SimpleFileSystem fs = new SimpleFileSystem(5);
        //SimpleFileSystem fs = Helper.loadFileSystem("myFileSystem.dat");
        String curPath = fs.currentDirectory.path;
        String cmd;
        String[] cmdPart;
        do {
            System.out.print("MSFS>>" + "myFileSystem" + curPath);
            cmd = appTerminal.scanner.nextLine();
            cmdPart = cmd.split(" ");
            curPath = handleCommand(fs, curPath, cmd, cmdPart);
            Helper.saveFileSystem("myFileSystem.dat", fs);
        } while (!cmd.equals("exit()"));
    }

    private static String handleCommand(SimpleFileSystem fs, String curPath, String cmd, String[] cmdPart) {
        switch (cmdPart[0]) {
            case "cd": {
                if (cmdPart.length == 2) {
                    fs.changeDirectory(cmdPart[1]);
                    curPath = fs.currentDirectory.path;
                }
                break;
            }
            case "ls": {
                if (cmdPart.length == 1) {
                    fs.listDirectory();
                }
                break;
            }
            case "mkdir": {
                if (cmdPart.length == 2) {
                    fs.makeDirectory(cmdPart[1]);
                }
                break;
            }
            case "rm": {
                if (cmdPart.length == 2) {
                    fs.removeDirectory(cmdPart[1]);
                }
                break;
            }
            case "rmf": {
                if (cmdPart.length == 2) {
                    FileHandleHelper.removeFile(fs, cmdPart[1]);
                }
                break;
            }
            case "touch": {
                if (cmdPart.length == 2) {
                    FileHandleHelper.createFile(fs, cmdPart[1]);
                } else if (cmdPart.length == 3) {
                    FileHandleHelper.writeFile(fs, cmdPart[1], cmdPart[2].getBytes());
                }
                break;
            }
            case "cat": {
                if (cmdPart.length == 2) {
                    FileHandleHelper.readFile(fs, cmdPart[1]);
                }
                break;
            }
            default: {
                if (!cmd.equals("exit()")) {
                    System.out.println("Invalid command");
                }
            }
        }
        return curPath;
    }
}

class appTerminal {
    public static Scanner scanner = new Scanner(System.in);
}

class Helper {
    public static void saveFileSystem(String fileName, SimpleFileSystem fs) {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(fileName))) {
            oos.writeObject(fs);
            //System.out.println("File system saved to " + fileName +"\n");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static SimpleFileSystem loadFileSystem(String fileName) {
        System.out.println("mounting disk image ....");
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(fileName))) {
            return (SimpleFileSystem) ois.readObject();
        } catch (ClassNotFoundException e) {
            System.out.println("Disk image is broken! create new one of format it");
            e.printStackTrace();
            return null;
        } catch (IOException e) {
            System.out.println(fileName + " disk image not fount in directory!");
            e.printStackTrace();
            return null;
        }
    }
}

class FileHandleHelper{

    public static void createFile(SimpleFileSystem fs, String fileName) {
        if (!fs.currentDirectory.getFiles().containsKey(fileName)) {
            File newFile = new File(fileName);
            fs.currentDirectory.addFile(newFile);
        } else {
            System.out.println("File already exists: " + fileName);
        }
    }

    // Write data to a file
    public static void writeFile(SimpleFileSystem fs, String fileName, byte[] data) {
        File file = fs.currentDirectory.getFiles().get(fileName);
        if (file == null) {
            System.out.println("File not found: " + fileName + "\n");
            return;
        }

        int remainingData = data.length;
        int dataOffset = 0;

        while (remainingData > 0) {
            int freeBlock = fs.bitMap.nextClearBit(0);
            if (freeBlock >= fs.TOTAL_BLOCKS) {
                System.out.println("No more free blocks available.\n");
                return;
            }

            fs.bitMap.set(freeBlock);
            file.addBlock(freeBlock);

            int writeSize = Math.min(remainingData, Block.BLOCK_SIZE);
            fs.blocks[freeBlock].writeData(Arrays.copyOfRange(data, dataOffset, dataOffset + writeSize), 0);

            remainingData -= writeSize;
            dataOffset += writeSize;
            file.setSize(file.getSize() + writeSize);
        }
    }

    public static void readFile(SimpleFileSystem fs, String fileName) {
        File file = fs.currentDirectory.getFiles().get(fileName);
        if (file == null) {
            System.out.println("File not found: " + fileName + "\n");
            return;
        }

        List<Integer> blockPointers = file.getBlockPointers();
        for (int blockIndex : blockPointers) {
            byte[] data = fs.blocks[blockIndex].readData(0, Block.BLOCK_SIZE);
            System.out.print(new String(data).trim());
        }
        System.out.println();
    }

    public static void removeFile(SimpleFileSystem fs, String fileName) {
        File file = fs.currentDirectory.removeFile(fileName);
        if (file == null) {
            System.out.println("File not found: " + fileName + "\n");
            return;
        }

        List<Integer> blockPointers = file.getBlockPointers();
        for (int blockIndex : blockPointers) {
            fs.bitMap.clear(blockIndex);
        }

    }
}