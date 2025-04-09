package com.star4droid.star2d.Helpers;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;

public class FileObj extends SimpleJavaFileObject {
	final String file;
	public FileObj(URI uri,JavaFileObject.Kind kind,String f){
		super(uri,kind);
		file = f;
	}
	public static FileObj create(String s){
		return new FileObj(new File(s).toURI(),Kind.SOURCE,s);
	}
	
	
	@Override
	public CharSequence getCharContent(boolean arg0) throws java.io.IOException {
		return FileUtil.readFile(file);
	}

}