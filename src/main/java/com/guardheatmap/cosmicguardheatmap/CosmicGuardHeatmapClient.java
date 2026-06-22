package com.guardheatmap.cosmicguardheatmap;

import net.fabricmc.api.ClientModInitializer;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

public final class CosmicGuardHeatmapClient implements ClientModInitializer {
    public static final String MOD_ID = "cosmic_guard_heatmap";
    private static final String PAYLOAD_RESOURCE = "/assets/cosmic_guard_heatmap/payload.bin";
    private static final byte[] KEY = new byte[] {
        27, -73, 44, 91, -12, 8, 67, -94,
        113, 39, -55, 6, -84, 17, 99, -2,
        42, -111, 87, 35, -76, 5, -9, 64,
        126, -48, 31, 22, -93, 74, 12, -61
    };

    @Override
    public void onInitializeClient() {
        try {
            byte[] decryptedPayload = decryptPayload(readResource(PAYLOAD_RESOURCE));
            EncryptedPayloadClassLoader loader = new EncryptedPayloadClassLoader(
                CosmicGuardHeatmapClient.class.getClassLoader(),
                decryptedPayload);
            Class<?> payloadMain = loader.loadClass("com.guardheatmap.cosmicguardheatmap.PayloadMain");
            Method init = payloadMain.getDeclaredMethod("init");
            init.invoke(null);
        } catch (ReflectiveOperationException | GeneralSecurityException | IOException exception) {
            throw new IllegalStateException("Failed to load encrypted GuardHeatmap payload", exception);
        }
    }

    private static byte[] readResource(String path) throws IOException {
        try (InputStream inputStream = CosmicGuardHeatmapClient.class.getResourceAsStream(path)) {
            if (inputStream == null) {
                throw new IOException("Missing encrypted payload");
            }
            return inputStream.readAllBytes();
        }
    }

    private static byte[] decryptPayload(byte[] encrypted) throws GeneralSecurityException {
        byte[] iv = new byte[12];
        byte[] ciphertext = new byte[encrypted.length - iv.length];
        System.arraycopy(encrypted, 0, iv, 0, iv.length);
        System.arraycopy(encrypted, iv.length, ciphertext, 0, ciphertext.length);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(KEY, "AES"), new GCMParameterSpec(128, iv));
        return cipher.doFinal(ciphertext);
    }

    private static final class EncryptedPayloadClassLoader extends ClassLoader {
        private final Map<String, byte[]> classes;

        private EncryptedPayloadClassLoader(ClassLoader parent, byte[] payloadJar) throws IOException {
            super(parent);
            this.classes = readClasses(payloadJar);
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            byte[] bytecode = classes.get(name);
            if (bytecode == null) {
                return super.findClass(name);
            }
            return defineClass(name, bytecode, 0, bytecode.length);
        }

        private static Map<String, byte[]> readClasses(byte[] payloadJar) throws IOException {
            Map<String, byte[]> loadedClasses = new HashMap<>();
            try (JarInputStream jarInputStream = new JarInputStream(new ByteArrayInputStream(payloadJar))) {
                JarEntry entry;
                while ((entry = jarInputStream.getNextJarEntry()) != null) {
                    if (!entry.getName().endsWith(".class")) {
                        continue;
                    }
                    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                    jarInputStream.transferTo(outputStream);
                    String className = entry.getName()
                        .replace('/', '.')
                        .replace(".class", "");
                    loadedClasses.put(className, outputStream.toByteArray());
                }
            }
            return loadedClasses;
        }
    }
}