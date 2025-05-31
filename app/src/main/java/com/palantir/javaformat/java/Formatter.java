package com.palantir.javaformat.java;

public class Formatter extends FormatterImp {
	
	public Formatter(JavaFormatterOptions options,boolean b){
		super(options,b);
	}
	
	public static Formatter createFormatter(JavaFormatterOptions options){
		return new Formatter(options,false);
	}
	
	public static int getRuntimeVersion(){
		try {
			return FormatterImp.getRuntimeVersion();
		} catch(NoSuchMethodError error){
			return 1;
		}
	}
}