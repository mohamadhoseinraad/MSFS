import java.io.*;
import java.util.*;

// Block to store data in the file system
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

// File class to represent a file and its block pointers
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

// Directory class to represent directories and files inside them
class Directory implements Serializable {
    private String name;
    public String path = "/";
    private Map<String, File> files = new HashMap<>();
    private Map<String, Directory> subDirectories = new HashMap<>();
    private Directory parent;

    public Directory(String name, Directory parent) {
        this.name = name;
        this.parent = parent;
        if (parent != null){
            path = parent.path + name +"/";
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

    public boolean addDirectory(Directory dir) {
        if (!subDirectories.containsKey(dir.getName())) {
            subDirectories.put(dir.getName(), dir);
            return true;
        }
        return false;
    }
}

// Main SimpleFileSystem with fixed size and directory management
class SimpleFileSystem implements Serializable {
    private static final int TOTAL_BLOCKS = 4096 * 500;  // File system size (1MB with 256-byte blocks)
    private Block[] blocks = new Block[TOTAL_BLOCKS];
    private BitSet bitMap = new BitSet(TOTAL_BLOCKS); // Bitmap to track used blocks
    private Directory rootDirectory = new Directory("/", null);  // Root directory
    Directory currentDirectory = rootDirectory;          // Start in root directory

    public SimpleFileSystem() {
        // Initialize the blocks for the file system
        for (int i = 0; i < TOTAL_BLOCKS; i++) {
            blocks[i] = new Block();
        }
    }

    // Change the current working directory
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

    // Create a new directory
    public void makeDirectory(String dirName) {
        if (!currentDirectory.getSubDirectories().containsKey(dirName)) {
            Directory newDir = new Directory(dirName, currentDirectory);
            currentDirectory.addDirectory(newDir);
        } else {
            System.out.println("Directory already exists: " + dirName);
        }
    }

    // Remove a directory
    public void removeDirectory(String dirName) {
        if (currentDirectory.getSubDirectories().containsKey(dirName)) {
            currentDirectory.getSubDirectories().remove(dirName);
            System.out.println("Directory removed: " + dirName);
        } else {
            System.out.println("Directory not found: " + dirName);
        }
    }

    // Create a new file
    public void createFile(String fileName) {
        if (!currentDirectory.getFiles().containsKey(fileName)) {
            File newFile = new File(fileName);
            currentDirectory.addFile(newFile);
            System.out.println("File created: " + fileName + "\n");
        } else {
            System.out.println("File already exists: " + fileName + "\n");
        }
    }

    // Write data to a file
    public void writeFile(String fileName, byte[] data) {
        File file = currentDirectory.getFiles().get(fileName);
        if (file == null) {
            System.out.println("File not found: " + fileName + "\n");
            return;
        }

        int remainingData = data.length;
        int dataOffset = 0;

        while (remainingData > 0) {
            int freeBlock = bitMap.nextClearBit(0);
            if (freeBlock >= TOTAL_BLOCKS) {
                System.out.println("No more free blocks available.\n");
                return;
            }

            bitMap.set(freeBlock);
            file.addBlock(freeBlock);

            int writeSize = Math.min(remainingData, Block.BLOCK_SIZE);
            blocks[freeBlock].writeData(Arrays.copyOfRange(data, dataOffset, dataOffset + writeSize), 0);

            remainingData -= writeSize;
            dataOffset += writeSize;
            file.setSize(file.getSize() + writeSize);
        }

        System.out.println("Data written to file: " + fileName + "\n");
    }

    // Read data from a file
    public void readFile(String fileName) {
        File file = currentDirectory.getFiles().get(fileName);
        if (file == null) {
            System.out.println("File not found: " + fileName + "\n");
            return;
        }

        List<Integer> blockPointers = file.getBlockPointers();
        for (int blockIndex : blockPointers) {
            byte[] data = blocks[blockIndex].readData(0, Block.BLOCK_SIZE);
            System.out.print(new String(data).trim());
        }
        System.out.println();
    }

    // Save the file system to a file
    public void saveFileSystem(String fileName) {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(fileName))) {
            oos.writeObject(this);
            System.out.println("File system saved to " + fileName +"\n");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Load the file system from a file
    public static SimpleFileSystem loadFileSystem(String fileName) {
        System.out.println("mounting disk image ....");
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(fileName))) {
            return (SimpleFileSystem) ois.readObject();
        } catch (ClassNotFoundException e) {
            System.out.println("Disk image is broken! create new one of format it\n");
            e.printStackTrace();
            return null;
        } catch (IOException e){
            System.out.println(fileName + " disk image not fount in directory!\n");
            e.printStackTrace();
            return null;
        }
    }
}

// Main application
public class FileSystemApp {
    public static void main(String[] args) {
        SimpleFileSystem fs = new SimpleFileSystem();
        //SimpleFileSystem fs = SimpleFileSystem.loadFileSystem("myFileSystem.dat");
        String curPath = fs.currentDirectory.path;
        String cmd;
        do {
            System.out.print("MSFS>>"+"myFileSystem"+curPath);
            cmd = appTerminal.scanner.nextLine();
            String[] cmdPart  = cmd.split(" ");
            switch (cmdPart[0]){
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
                case "mkdir":{
                    if (cmdPart.length == 2){
                        fs.makeDirectory(cmdPart[1]);
                    }
                    break;
                }
                case "rm":{
                    if (cmdPart.length == 2){
                        fs.removeDirectory(cmdPart[1]);
                    }
                    break;
                }
                default:{
                    System.out.println("Invalid command");
                }
            }
        }while (!cmd.equals("exit()"));


//        // Create directories and files
//        fs.makeDirectory("docs");
//        fs.changeDirectory("docs");
//        fs.createFile("test.txt");
//        fs.writeFile("test.txt", "Hello from the docs directory!".getBytes());
//        fs.listDirectory();
//        fs.readFile("test.txt");
//        fs.makeDirectory("docs2");
        fs.listDirectory();
        fs.changeDirectory("docs");
        fs.listDirectory();
        // Navigate and list directories
//        fs.changeDirectory("..");
//        fs.listDirectory();

        // Save the file system to disk
//        fs.saveFileSystem("myFileSystem.dat");

        // Load the file system from disk
//        SimpleFileSystem loadedFs = SimpleFileSystem.loadFileSystem("myFileSystem.dat");
//        if (loadedFs != null) {
//            loadedFs.listDirectory();
//        }
    }
}
class appTerminal {
    public static Scanner scanner = new Scanner(System.in);

    
}
class HandleCMD{

}