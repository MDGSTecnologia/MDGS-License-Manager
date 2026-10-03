package br.com.mdgstecnologia.licensemanager;

import android.util.Base64;
import org.json.JSONObject;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.RSAPublicKeySpec;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

final class CryptoManager {
    private static final String MASTER_KEY_B64="PpSGUrLjEhOpfilKGNrR2AoEwWkQBqw+TjLXXIP7ADUuUbjosMoHkQWRP5LJcuaE/i/2N+Ktic0ePO9GMTEEEQ==";
    private static final String LEGACY_RSA_MODULUS_B64="7rK7tbkuyqnsihwApkMg9kzqyhXy7O6p/H+1fOApleUpM/yjK5N6Q3nw/DhMeuzG4S3lQJLBTazNTEdA/GCz/vs9QKSypSgkvrUVd5+9yJGxRVIdV0IZzc8+pPSdZsP7zLrgha0zyAJMRi24SIfqrf2vklcrA8FgSVNwP5I9sqZ46aWrtWMiYhGVtWSbZMohgb7yx9kvnmTIbjIRjsp/rD2SpAfsY5eeXsHTWM/VThnqL3wO5jbaBVUxp7TJWekNflSR9V7hVq8Ym7qIoMOc08pqxyhX+MOLfjQ9aGaEznMUNnlOTv69orHe+NEW6xw1gCweoL1w1+2Kfme0aQOTTw==";
    private static final String LEGACY_RSA_EXPONENT_B64="AQAB";
    private CryptoManager(){}

    static LicenseDb decryptDatabase(String text)throws Exception{
        if(text==null||text.trim().isEmpty()) throw new Exception("O arquivo remoto de licenças está vazio.");
        String clean=text.trim(); if(!clean.isEmpty()&&clean.charAt(0)=='\uFEFF')clean=clean.substring(1);
        JSONObject env=new JSONObject(clean); String alg=env.optString("Algorithm","").trim().toUpperCase();
        if("AES-256-CBC-HMAC-SHA256".equals(alg)){
            if(env.optInt("Version",0)!=2)throw new Exception("Versão criptografada inválida.");
            byte[] raw=Base64.decode(MASTER_KEY_B64,Base64.NO_WRAP); if(raw.length<64)throw new Exception("Chave criptográfica inválida.");
            byte[] aesKey=Arrays.copyOfRange(raw,0,32),macKey=Arrays.copyOfRange(raw,32,64);
            byte[] iv=Base64.decode(env.getString("IV"),Base64.NO_WRAP),cipherText=Base64.decode(env.getString("Payload"),Base64.NO_WRAP),tag=Base64.decode(env.getString("Hmac"),Base64.NO_WRAP);
            Mac mac=Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(macKey,"HmacSHA256")); mac.update(iv); byte[] calc=mac.doFinal(cipherText);
            if(!MessageDigest.isEqual(tag,calc))throw new Exception("Integridade do banco inválida.");
            Cipher cipher=Cipher.getInstance("AES/CBC/PKCS5Padding"); cipher.init(Cipher.DECRYPT_MODE,new SecretKeySpec(aesKey,"AES"),new IvParameterSpec(iv));
            return LicenseDb.fromJson(new JSONObject(new String(cipher.doFinal(cipherText),StandardCharsets.UTF_8)));
        }
        if("RSA-SHA256".equals(alg)){
            if(env.optInt("Version",0)!=1)throw new Exception("Versão legada inválida.");
            byte[] payload=Base64.decode(env.getString("Payload"),Base64.NO_WRAP),signature=Base64.decode(env.getString("Signature"),Base64.NO_WRAP);
            Signature verifier=Signature.getInstance("SHA256withRSA"); verifier.initVerify(legacyPublicKey()); verifier.update(payload);
            if(!verifier.verify(signature))throw new Exception("Assinatura legada inválida.");
            return LicenseDb.fromJson(new JSONObject(new String(payload,StandardCharsets.UTF_8)));
        }
        throw new Exception("Algoritmo não reconhecido: "+alg);
    }

    static String encryptDatabase(LicenseDb db)throws Exception{
        if(db==null)throw new Exception("Cadastro ausente.");
        byte[] plain=db.toJsonForEncryption().toString().getBytes(StandardCharsets.UTF_8),raw=Base64.decode(MASTER_KEY_B64,Base64.NO_WRAP);
        byte[] aesKey=Arrays.copyOfRange(raw,0,32),macKey=Arrays.copyOfRange(raw,32,64),iv=new byte[16]; new SecureRandom().nextBytes(iv);
        Cipher cipher=Cipher.getInstance("AES/CBC/PKCS5Padding"); cipher.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(aesKey,"AES"),new IvParameterSpec(iv)); byte[] encrypted=cipher.doFinal(plain);
        Mac mac=Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(macKey,"HmacSHA256")); mac.update(iv); byte[] tag=mac.doFinal(encrypted);
        JSONObject env=new JSONObject(); env.put("Version",2); env.put("Algorithm","AES-256-CBC-HMAC-SHA256"); env.put("IV",Base64.encodeToString(iv,Base64.NO_WRAP)); env.put("Payload",Base64.encodeToString(encrypted,Base64.NO_WRAP)); env.put("Hmac",Base64.encodeToString(tag,Base64.NO_WRAP)); return env.toString();
    }

    private static PublicKey legacyPublicKey()throws Exception{
        BigInteger modulus=new BigInteger(1,Base64.decode(LEGACY_RSA_MODULUS_B64,Base64.NO_WRAP)), exponent=new BigInteger(1,Base64.decode(LEGACY_RSA_EXPONENT_B64,Base64.NO_WRAP));
        return KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(modulus,exponent));
    }
}
