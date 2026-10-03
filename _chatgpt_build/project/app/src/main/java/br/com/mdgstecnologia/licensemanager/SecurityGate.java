package br.com.mdgstecnologia.licensemanager;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

final class SecurityGate {
    private static final String SALT="MDGS-LM-ANDROID-FIRST-ACCESS-v1|";
    private static final String EXPECTED_SHA256="2affc08178f9ca0e2bf701bba42b0721f31badfad92cd4d83ee0f41285f7e0c8";
    private SecurityGate(){}
    static boolean verify(String password){
        try{
            MessageDigest md=MessageDigest.getInstance("SHA-256");
            byte[] actual=md.digest((SALT+password).getBytes(StandardCharsets.UTF_8)),expected=hexToBytes(EXPECTED_SHA256);
            return MessageDigest.isEqual(actual,expected);
        }catch(Exception e){return false;}
    }
    private static byte[] hexToBytes(String hex){
        byte[] out=new byte[hex.length()/2];
        for(int i=0;i<out.length;i++){int hi=Character.digit(hex.charAt(i*2),16),lo=Character.digit(hex.charAt(i*2+1),16);out[i]=(byte)((hi<<4)|lo);}
        return out;
    }
}