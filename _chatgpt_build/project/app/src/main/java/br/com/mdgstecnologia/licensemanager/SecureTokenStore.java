package br.com.mdgstecnologia.licensemanager;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

final class SecureTokenStore {
    private static final String PREFS="mdgs_secure_prefs",TOKEN="github_token_encrypted",ALIAS="MDGS_LM_GITHUB_TOKEN_KEY";
    private final Context context;
    SecureTokenStore(Context context){this.context=context.getApplicationContext();}
    void save(String token)throws Exception{
        SecretKey key=getOrCreateKey(); Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE,key);
        byte[] encrypted=cipher.doFinal(token.getBytes(StandardCharsets.UTF_8));
        String packed=Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP)+":"+Base64.encodeToString(encrypted,Base64.NO_WRAP);
        prefs().edit().putString(TOKEN,packed).apply();
    }
    String load(){
        try{
            String packed=prefs().getString(TOKEN,""); if(packed==null||packed.isEmpty())return "";
            String[] parts=packed.split(":",2); if(parts.length!=2)return "";
            KeyStore ks=KeyStore.getInstance("AndroidKeyStore"); ks.load(null); SecretKey key=(SecretKey)ks.getKey(ALIAS,null); if(key==null)return "";
            byte[] iv=Base64.decode(parts[0],Base64.NO_WRAP),encrypted=Base64.decode(parts[1],Base64.NO_WRAP);
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.DECRYPT_MODE,key,new GCMParameterSpec(128,iv));
            return new String(cipher.doFinal(encrypted),StandardCharsets.UTF_8);
        }catch(Exception e){return "";}
    }
    void clear(){prefs().edit().remove(TOKEN).apply();}
    private SharedPreferences prefs(){return context.getSharedPreferences(PREFS,Context.MODE_PRIVATE);}
    private SecretKey getOrCreateKey()throws Exception{
        KeyStore ks=KeyStore.getInstance("AndroidKeyStore"); ks.load(null); if(ks.containsAlias(ALIAS))return (SecretKey)ks.getKey(ALIAS,null);
        KeyGenerator kg=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
        kg.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
        return kg.generateKey();
    }
}