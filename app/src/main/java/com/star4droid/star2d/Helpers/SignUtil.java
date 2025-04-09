package com.star4droid.star2d.Helpers;

import android.content.Context;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.io.InputStream;
import java.util.List;

public class SignUtil {
	
	public static void sign(Context context,String in,String out) throws Exception {
		
		InputStream mInput1 = context.getAssets().open("keys/testkey.pk8"),
		mInput2 = context.getAssets().open("keys/testkey.x509.pem");
		//com.googlecode.dex2jar.tools.ApkSign.main(new String[]{"-f","-o",out,in});
		new com.android.apksig.ApkSigner.Builder(ImmutableList.of(new com.android.apksig.ApkSigner.SignerConfig.Builder("test",readPrivateKey(mInput1),List.of(readPublicKey(mInput2))).build())).setCreatedBy("Star2D Evo").setMinSdkVersion(21).setOutputApk(new File(out)).setInputApk(new File(in)).setV1SigningEnabled(true).setV2SigningEnabled(true).build().sign();
		
	}
	private static java.security.PrivateKey readPrivateKey(java.io.InputStream input)throws java.io.IOException, java.security.GeneralSecurityException{
		try{
			byte[] bytes=readBytes(input);
			
			java.security.spec.KeySpec spec=decryptPrivateKey(bytes,"");
			if(spec==null){
				spec=new java.security.spec.PKCS8EncodedKeySpec(bytes);
			}
			try{
				return java.security.KeyFactory.getInstance("RSA").generatePrivate(spec);
			}
			catch(java.security.spec.InvalidKeySpecException ex){
				return java.security.KeyFactory.getInstance("DSA").generatePrivate(spec);
			}
		}
		finally{
			input.close();
		}
	}
	
	private static byte[] readBytes(java.io.InputStream in)throws java.io.IOException{
		byte[] buf=new byte[1024];
		java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();
		int num;
		while((num=in.read(buf,0,buf.length))!=-1)
		out.write(buf,0,num);
		return out.toByteArray();
	}
	
	private static java.security.spec.KeySpec decryptPrivateKey(byte[] encryptedPrivateKey, String keyPassword)throws java.security.GeneralSecurityException{
		javax.crypto.EncryptedPrivateKeyInfo epkInfo=null;
		try{
			epkInfo=new javax.crypto.EncryptedPrivateKeyInfo(encryptedPrivateKey);
		}
		catch(java.io.IOException ex){
			return null;
			
		}
		char[] password=keyPassword.toCharArray();
		javax.crypto.SecretKeyFactory skFactory=javax.crypto.SecretKeyFactory.getInstance(epkInfo.getAlgName());
		java.security.Key key=skFactory.generateSecret(new javax.crypto.spec.PBEKeySpec(password));
		javax.crypto.Cipher cipher=javax.crypto.Cipher.getInstance(epkInfo.getAlgName());
		cipher.init(javax.crypto.Cipher.DECRYPT_MODE, key, epkInfo.getAlgParameters());
		try{
			return epkInfo.getKeySpec(cipher);
		}
		catch(java.security.spec.InvalidKeySpecException ex){
			System.err.println("PrivateKey may be bad.");
			throw ex;
		}
	}
	
	private static java.security.cert.X509Certificate readPublicKey(java.io.InputStream input)throws java.io.IOException, java.security.GeneralSecurityException{
		try{
			java.security.cert.CertificateFactory cf=java.security.cert.CertificateFactory.getInstance("X.509");
			return (java.security.cert.X509Certificate)cf.generateCertificate(input);
		}
		finally{
			input.close();
		}
	}
}