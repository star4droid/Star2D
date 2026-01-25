package com.star4droid.star2d.Helpers;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;

public class CoreFileUtil {
    
    public static String readFile(String path) {
		if(!isExistFile(path)) return "";
		createNewFile(path);
		
		StringBuilder sb = new StringBuilder();
		FileReader fr = null;
		try {
			fr = new FileReader(new File(path));
			
			char[] buff = new char[1024];
			int length = 0;
			
			while ((length = fr.read(buff)) > 0) {
				sb.append(new String(buff, 0, length));
			}
			} catch (IOException e) {
			e.printStackTrace();
			} finally {
			if (fr != null) {
				try {
					fr.close();
					} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
		
		return sb.toString();
	}
	
	public static void writeFile(String path, String str) {
		createNewFile(path);
		FileWriter fileWriter = null;
		
		try {
			fileWriter = new FileWriter(new File(path), false);
			fileWriter.write(str);
			fileWriter.flush();
			} catch (IOException e) {
			e.printStackTrace();
			} finally {
			try {
				if (fileWriter != null)
				fileWriter.close();
				} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
	
	public static void copyFile(String sourcePath, String destPath) {
		if (!isExistFile(sourcePath)) return;
		createNewFile(destPath);
		
		FileInputStream fis = null;
		FileOutputStream fos = null;
		
		try {
			fis = new FileInputStream(sourcePath);
			fos = new FileOutputStream(destPath, false);
			
			byte[] buff = new byte[1024];
			int length = 0;
			
			while ((length = fis.read(buff)) > 0) {
				fos.write(buff, 0, length);
			}
			} catch (IOException e) {
			e.printStackTrace();
			} finally {
			if (fis != null) {
				try {
					fis.close();
					} catch (IOException e) {
					e.printStackTrace();
				}
			}
			if (fos != null) {
				try {
					fos.close();
					} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}
	
	public static void copyDir(String oldPath, String newPath) {
		File oldFile = new File(oldPath);
		File[] files = oldFile.listFiles();
		File newFile = new File(newPath);
		if (!newFile.exists()) {
			newFile.mkdirs();
		}
		if(files==null) return;
		for (File file : files) {
			if (file.isFile()) {
				copyFile(file.getPath(), newPath + "/" + file.getName());
				} else if (file.isDirectory()) {
				copyDir(file.getPath(), newPath + "/" + file.getName());
			}
		}
	}
	
	public static void moveFile(String sourcePath, String destPath) {
		copyFile(sourcePath, destPath);
		deleteFile(sourcePath);
	}
	
	public static void deleteFile(String path) {
		File file = new File(path);
		
		if (!file.exists()) return;
		
		if (file.isFile()) {
			file.delete();
			return;
		}
		
		File[] fileArr = file.listFiles();
		
		if (fileArr != null) {
			for (File subFile : fileArr) {
				if (subFile.isDirectory()) {
					deleteFile(subFile.getAbsolutePath());
				}
				
				if (subFile.isFile()) {
					subFile.delete();
				}
			}
		}
		
		file.delete();
	}
	
	public static boolean isExistFile(String path) {
		File file = new File(path);
		return file.exists();
	}
	
	public static void makeDir(String path) {
		if (!isExistFile(path)) {
			File file = new File(path);
			file.mkdirs();
		}
	}
	
	public static void listDir(String path, ArrayList<String> list) {
		File dir = new File(path);
		if (!dir.exists() || dir.isFile()) return;
		
		File[] listFiles = dir.listFiles();
		if (listFiles == null || listFiles.length <= 0) return;
		
		if (list == null) return;
		list.clear();
		for (File file : listFiles) {
			list.add(file.getAbsolutePath());
		}
	}
	
	public static boolean isDirectory(String path) {
		if (!isExistFile(path)) return false;
		return new File(path).isDirectory();
	}
	
	public static boolean isFile(String path) {
		if (!isExistFile(path)) return false;
		return new File(path).isFile();
	}
	
	private static void createNewFile(String path) {
		int lastSep = path.lastIndexOf(File.separator);
		if (lastSep > 0) {
			String dirPath = path.substring(0, lastSep);
			makeDir(dirPath);
		}
		
		File file = new File(path);
		
		try {
			if (!file.exists()) file.createNewFile();
			} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
