package com.sadique.launcher;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.AtomicFile;
import java.io.*;
import java.math.BigInteger;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Date;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

/** Software ADB RSA identity encrypted at rest by a non-exportable Android Keystore AES key. */
final class VolumeAdbIdentity {
    private static final String ALIAS = "personal_volume_adb_identity_v1";
    final PrivateKey key;
    final Certificate certificate;
    private VolumeAdbIdentity(PrivateKey key, Certificate certificate) { this.key=key; this.certificate=certificate; }
    static synchronized VolumeAdbIdentity load(Context context) throws Exception {
        KeyStore store=KeyStore.getInstance("AndroidKeyStore"); store.load(null);
        if (!store.containsAlias(ALIAS)) {
            KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
            generator.generateKey();
        }
        SecretKey wrapping=(SecretKey)store.getKey(ALIAS,null);
        AtomicFile file=new AtomicFile(new File(context.getNoBackupFilesDir(),"volume-adb-identity.bin"));
        if (file.getBaseFile().exists()) {
            byte[] sealed=file.readFully();
            if (sealed.length<30 || sealed.length>16384 || sealed[0]!=12) throw new IOException("Invalid saved ADB identity");
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE,wrapping,new GCMParameterSpec(128,sealed,1,12));
            byte[] clear=cipher.doFinal(sealed,13,sealed.length-13);
            try (DataInputStream input=new DataInputStream(new ByteArrayInputStream(clear))) {
                byte[] key=part(input), certificate=part(input);
                return new VolumeAdbIdentity(KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(key)),
                        CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(certificate)));
            } finally { java.util.Arrays.fill(clear,(byte)0); }
        }
        KeyPairGenerator generator=KeyPairGenerator.getInstance("RSA"); generator.initialize(2048);
        java.security.KeyPair pair=generator.generateKeyPair();
        X500Name name=new X500Name("CN=PersonalLauncher local volume"); long now=System.currentTimeMillis();
        byte[] der=new X509v3CertificateBuilder(name,new BigInteger(128,new SecureRandom()),new Date(now-86400000L),
                new Date(now+315360000000L),name,SubjectPublicKeyInfo.getInstance(pair.getPublic().getEncoded()))
                .build(new JcaContentSignerBuilder("SHA256withRSA").build(pair.getPrivate())).getEncoded();
        Certificate certificate=CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(der));
        ByteArrayOutputStream clear=new ByteArrayOutputStream();
        try (DataOutputStream output=new DataOutputStream(clear)) { write(output,pair.getPrivate().getEncoded()); write(output,der); }
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE,wrapping);
        byte[] plain=clear.toByteArray(), sealed;
        try { sealed=cipher.doFinal(plain); } finally { java.util.Arrays.fill(plain,(byte)0); }
        FileOutputStream stream=null;
        try {
            stream=file.startWrite(); stream.write(cipher.getIV().length); stream.write(cipher.getIV()); stream.write(sealed);
            file.finishWrite(stream);
        } catch (IOException error) { if (stream!=null) file.failWrite(stream); throw error; }
        return new VolumeAdbIdentity(pair.getPrivate(),certificate);
    }
    private static byte[] part(DataInputStream input) throws IOException {
        int size=input.readInt(); if (size<=0 || size>8192) throw new IOException("Invalid identity part");
        byte[] data=new byte[size]; input.readFully(data); return data;
    }
    private static void write(DataOutputStream output,byte[] bytes) throws IOException { output.writeInt(bytes.length); output.write(bytes); }
}
