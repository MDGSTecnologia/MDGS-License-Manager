package br.com.mdgstecnologia.licensemanager;

import android.util.Base64;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

final class GitHubClient {
    private static final String OWNER="MDGSTecnologia", REPO="MDGS-License-Manager", BRANCH="main", PATH="licenses.signed.json";
    private static final String API="https://api.github.com/repos/"+OWNER+"/"+REPO+"/contents/"+PATH;
    static final class GitHubException extends Exception { final int statusCode; GitHubException(int c,String m){super(m);statusCode=c;} }
    String getRemoteText(String token) throws Exception {
        Response r=request("GET",API+"?ref="+BRANCH+"&nocache="+UUID.randomUUID(),token,null);
        if(r.code!=200) throw friendly(r.code,"Não foi possível acessar o banco de dados remoto.");
        JSONObject current=new JSONObject(r.body);
        if(!"file".equalsIgnoreCase(current.optString("type"))) throw new Exception("O caminho remoto não retornou um arquivo.");
        String content=current.optString("content","").replaceAll("\\s","");
        if(content.isEmpty()) throw new Exception("Conteúdo remoto ausente.");
        String text=new String(Base64.decode(content,Base64.DEFAULT),StandardCharsets.UTF_8);
        if(!text.isEmpty()&&text.charAt(0)=='\uFEFF') text=text.substring(1);
        return text;
    }
    void validateToken(String token) throws Exception {
        if(token==null||token.trim().isEmpty()) throw new Exception("Informe o token.");
        Response r=request("GET",API+"?ref="+BRANCH,token.trim(),null);
        if(r.code!=200) throw friendly(r.code,"Não foi possível validar o token.");
    }
    String saveRemoteText(String token,String signedText) throws Exception {
        if(token==null||token.trim().isEmpty()) throw new Exception("Token não configurado. Valide o token primeiro.");
        CryptoManager.decryptDatabase(signedText);
        String getUri=API+"?ref="+BRANCH;
        Response current=request("GET",getUri+"&nocache="+UUID.randomUUID(),token,null);
        String sha=null;
        if(current.code==200) sha=new JSONObject(current.body).optString("sha",null);
        else if(current.code!=404) throw friendly(current.code,"Não foi possível consultar o cadastro remoto.");
        JSONObject body=new JSONObject();
        body.put("message","Atualiza licenças MDGS "+LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        body.put("content",Base64.encodeToString(signedText.getBytes(StandardCharsets.UTF_8),Base64.NO_WRAP));
        body.put("branch",BRANCH);
        if(sha!=null&&!sha.isEmpty()) body.put("sha",sha);
        Response put=request("PUT",API,token,body.toString().getBytes(StandardCharsets.UTF_8));
        if(put.code<200||put.code>=300) throw friendly(put.code,"Não foi possível salvar o cadastro remoto.");
        Thread.sleep(300L);
        Response verify=request("GET",getUri+"&nocache="+UUID.randomUUID(),token,null);
        if(verify.code!=200) throw friendly(verify.code,"A atualização foi enviada, mas a confirmação falhou.");
        JSONObject check=new JSONObject(verify.body);
        String content=check.optString("content","").replaceAll("\\s","");
        byte[] remote=Base64.decode(content,Base64.DEFAULT), local=signedText.getBytes(StandardCharsets.UTF_8);
        if(!java.security.MessageDigest.isEqual(remote,local)) throw new Exception("A atualização foi enviada, mas o conteúdo remoto ficou diferente.");
        String remoteText=new String(remote,StandardCharsets.UTF_8);
        CryptoManager.decryptDatabase(remoteText);
        return remoteText;
    }
    private static GitHubException friendly(int code,String prefix){
        if(code==401)return new GitHubException(code,"Token inválido ou expirado.");
        if(code==403)return new GitHubException(code,"O GitHub recusou a operação. Verifique as permissões do token.");
        if(code==404)return new GitHubException(code,"Arquivo remoto não encontrado.");
        return new GitHubException(code,prefix+" HTTP "+code+".");
    }
    private Response request(String method,String url,String token,byte[] body) throws Exception {
        HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();
        c.setRequestMethod(method); c.setConnectTimeout(30000); c.setReadTimeout("PUT".equals(method)?45000:30000); c.setUseCaches(false);
        c.setRequestProperty("User-Agent","MDGS-License-Manager-Android/1.1");
        c.setRequestProperty("Accept","application/vnd.github+json");
        c.setRequestProperty("X-GitHub-Api-Version","2022-11-28");
        c.setRequestProperty("Cache-Control","no-cache");
        if(token!=null&&!token.trim().isEmpty()) c.setRequestProperty("Authorization","Bearer "+token.trim());
        if(body!=null){ c.setDoOutput(true); c.setRequestProperty("Content-Type","application/json; charset=utf-8"); c.setFixedLengthStreamingMode(body.length); try(OutputStream os=c.getOutputStream()){os.write(body);} }
        int code=c.getResponseCode();
        InputStream is=code>=200&&code<400?c.getInputStream():c.getErrorStream();
        String text=is==null?"":new String(readAll(is),StandardCharsets.UTF_8); c.disconnect(); return new Response(code,text);
    }
    private static byte[] readAll(InputStream in) throws Exception { try(InputStream input=in; ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buf=new byte[8192];int n;while((n=input.read(buf))>=0)out.write(buf,0,n);return out.toByteArray();} }
    private static final class Response { final int code; final String body; Response(int c,String b){code=c;body=b;} }
}
