package com.oracle.banking.twofa.service;

import com.google.zxing.*;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.oracle.banking.twofa.dto.TwoFactorDtos.*;
import com.oracle.banking.twofa.entity.AuthFactor;
import com.oracle.banking.twofa.exception.*;
import com.oracle.banking.twofa.repository.AuthFactorRepository;
import io.jsonwebtoken.io.Decoders;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import javax.crypto.*;
import javax.crypto.spec.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;

@Service public class TwoFactorService {
 private final AuthFactorRepository factors; private final String issuer; private final SecretKey key; private final SecureRandom random=new SecureRandom();
 public TwoFactorService(AuthFactorRepository factors,@Value("${twofa.issuer:Oracle Internet Banking}") String issuer,@Value("${twofa.encryption-key}") String encodedKey){this.factors=factors;this.issuer=issuer;this.key=new SecretKeySpec(Decoders.BASE64.decode(encodedKey),"AES");}
 @Transactional public SetupResponse setup(String userId){String secret=createSecret();AuthFactor factor=factors.findByUserId(userId).orElseGet(()->new AuthFactor(userId,encrypt(secret)));if(factors.findByUserId(userId).isPresent()){factor.replaceSecret(encrypt(secret));factor.disable();}factors.save(factor);String uri="otpauth://totp/"+encode(issuer)+":"+encode(userId)+"?secret="+secret+"&issuer="+encode(issuer)+"&algorithm=SHA1&digits=6&period=30";return new SetupResponse(secret,uri,qr(uri),issuer,userId,false);}
 @Transactional public StatusResponse verifySetup(String userId,String code){AuthFactor f=required(userId);if(!valid(decrypt(f.encryptedSecret()),code))throw new TwoFactorException("Invalid OTP code");f.enable();return new StatusResponse(true);}
 public StatusResponse verify(String userId,String code){AuthFactor f=required(userId);if(!f.isEnabled()||!valid(decrypt(f.encryptedSecret()),code))throw new TwoFactorException("Invalid OTP code");return new StatusResponse(true);}
 @Transactional public StatusResponse disable(String userId,String code){AuthFactor f=required(userId);if(!f.isEnabled()||!valid(decrypt(f.encryptedSecret()),code))throw new TwoFactorException("Invalid OTP code");f.disable();return new StatusResponse(false);}
 public StatusResponse status(String userId){return new StatusResponse(factors.findByUserId(userId).map(AuthFactor::isEnabled).orElse(false));}
 private AuthFactor required(String id){return factors.findByUserId(id).orElseThrow(()->new ResourceNotFoundException("2FA setup not found"));}
 private String encrypt(String v){try{byte[] iv=new byte[12];random.nextBytes(iv);Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,key,new GCMParameterSpec(128,iv));byte[] e=c.doFinal(v.getBytes(StandardCharsets.UTF_8)),all=Arrays.copyOf(iv,iv.length+e.length);System.arraycopy(e,0,all,iv.length,e.length);return Base64.getEncoder().encodeToString(all);}catch(Exception e){throw new IllegalStateException("Unable to protect TOTP secret",e);}}
 private String decrypt(String v){try{byte[] all=Base64.getDecoder().decode(v),iv=Arrays.copyOfRange(all,0,12),e=Arrays.copyOfRange(all,12,all.length);Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,key,new GCMParameterSpec(128,iv));return new String(c.doFinal(e),StandardCharsets.UTF_8);}catch(Exception e){throw new IllegalStateException("Unable to read TOTP secret",e);}}
 private String createSecret(){byte[] b=new byte[20];random.nextBytes(b);return b32(b);} private boolean valid(String s,String code){if(code==null||!code.matches("\\d{6}"))return false;long n=Instant.now().getEpochSecond()/30;for(long i=-1;i<=1;i++)if(code.equals(code(s,n+i)))return true;return false;}
 private String code(String s,long n){try{byte[] cb=new byte[8];for(int i=7;i>=0;i--){cb[i]=(byte)n;n>>>=8;}Mac m=Mac.getInstance("HmacSHA1");m.init(new SecretKeySpec(unb32(s),"HmacSHA1"));byte[] h=m.doFinal(cb);int o=h[h.length-1]&15,x=((h[o]&127)<<24)|((h[o+1]&255)<<16)|((h[o+2]&255)<<8)|(h[o+3]&255);return String.format("%06d",x%1000000);}catch(Exception e){throw new IllegalStateException("Unable to calculate TOTP",e);}}
 private String qr(String uri){try{BitMatrix m=new QRCodeWriter().encode(uri,BarcodeFormat.QR_CODE,250,250);ByteArrayOutputStream o=new ByteArrayOutputStream();MatrixToImageWriter.writeToStream(m,"PNG",o);return Base64.getEncoder().encodeToString(o.toByteArray());}catch(Exception e){throw new IllegalStateException("Unable to generate QR code",e);}}
 private static String encode(String v){return java.net.URLEncoder.encode(v,StandardCharsets.UTF_8);} private static final char[] B="ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();
 private static String b32(byte[] d){StringBuilder o=new StringBuilder();int q=0,z=0;for(byte v:d){q=(q<<8)|(v&255);z+=8;while(z>=5){o.append(B[(q>>(z-5))&31]);z-=5;}}if(z>0)o.append(B[(q<<(5-z))&31]);return o.toString();}
 private static byte[] unb32(String v){int q=0,z=0;ByteArrayOutputStream o=new ByteArrayOutputStream();for(char c:v.replace("=","").toUpperCase().toCharArray()){int x=new String(B).indexOf(c);if(x<0)throw new IllegalArgumentException("Invalid Base32 secret");q=(q<<5)|x;z+=5;if(z>=8){o.write((q>>(z-8))&255);z-=8;}}return o.toByteArray();}
}
