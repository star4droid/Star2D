/*******************************************************************************
 * Copyright 2011 See AUTHORS file.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package com.badlogic.gdx.utils;
import com.badlogic.gdx.Gdx;

/** Typed runtime exception used throughout libGDX
 * 
 * @author mzechner */
public class GdxRuntimeException extends RuntimeException {
	private static final long serialVersionUID = 6735854402467673117L;

	public GdxRuntimeException (String message) {
		super(message);
		Log(message,this);
	}

	public GdxRuntimeException (Throwable t) {
		super(t);
		Log("Error",t);
	}

	public GdxRuntimeException (String message, Throwable t) {
		super(message, t);
		Log(message,t);
	}
	
	public static void Log(String message,Throwable throwable){
		String full="";
		String space="";
		for(int x =0;x<12;x++)
			space+="_";
		for(StackTraceElement element:throwable.getStackTrace()){
			full+="class name : "+element.getClassName()+"\n file : "+element.getFileName()+
			"\n line number : "+element.getLineNumber()+"\n method : "+element.getMethodName()+"\n"+space+"\n";
		}
		Gdx.files.external("/logs/log.txt").writeString(message+" : \n"+full,false);
	}
	
}
