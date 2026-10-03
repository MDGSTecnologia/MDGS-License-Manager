package br.com.mdgstecnologia.licensemanager;

import android.util.Base64;
import java.security.SecureRandom;
import java.util.Locale;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

final class PasswordUtil {
    private PasswordUtil() {}
    static String newSalt() throws Exception {
        byte[] salt=new byte[16]; new SecureRandom().nextBytes(salt); return Base64.encodeToString(salt,Base64.NO_WRAP);
    }
    static String hash(String password,String saltBase64) throws Exception {
        byte[] salt=Base64.decode(saltBase64,Base64.NO_WRAP);
        PBEKeySpec spec=new PBEKeySpec(password.toUpperCase(Locale.ROOT).toCharArray(),salt,100000,256);
        try { return Base64.encodeToString(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).getEncoded(),Base64.NO_WRAP); }
        finally { spec.clearPassword(); }
    }
}