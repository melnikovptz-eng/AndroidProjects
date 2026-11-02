package ru.voiceactivation.next.asr;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;

import androidx.documentfile.provider.DocumentFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/** Copies a user-selected, unpacked Vosk model folder into private app storage. */
public final class VoskModelImporter {
    private VoskModelImporter() { }

    public static File importTree(Context context, Uri treeUri, File destination) throws IOException {
        DocumentFile root = DocumentFile.fromTreeUri(context, treeUri);
        if (root == null || !root.isDirectory()) throw new IOException("Не удалось открыть папку модели");
        deleteRecursively(destination);
        if (!destination.mkdirs() && !destination.isDirectory()) {
            throw new IOException("Не удалось создать каталог модели приложения");
        }
        copyDirectory(context.getContentResolver(), root, destination);
        requireDirectory(destination, "am");
        requireDirectory(destination, "conf");
        requireDirectory(destination, "graph");
        return destination;
    }

    private static void copyDirectory(ContentResolver resolver, DocumentFile source, File target)
            throws IOException {
        DocumentFile[] children = source.listFiles();
        for (DocumentFile child : children) {
            String name = child.getName();
            if (name == null || name.isEmpty() || ".".equals(name) || "..".equals(name)
                    || name.contains("/") || name.contains("\\")) {
                continue;
            }
            File output = new File(target, name);
            if (child.isDirectory()) {
                if (!output.mkdirs() && !output.isDirectory()) {
                    throw new IOException("Не удалось создать " + name);
                }
                copyDirectory(resolver, child, output);
            } else if (child.isFile()) {
                try (InputStream in = resolver.openInputStream(child.getUri());
                     OutputStream out = new FileOutputStream(output)) {
                    if (in == null) throw new IOException("Не удалось прочитать " + name);
                    byte[] buffer = new byte[64 * 1024];
                    int count;
                    while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
                }
            }
        }
    }

    private static void requireDirectory(File root, String name) throws IOException {
        if (!new File(root, name).isDirectory()) {
            throw new IOException("Это не папка модели Vosk: отсутствует " + name);
        }
    }

    private static void deleteRecursively(File file) throws IOException {
        if (!file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) deleteRecursively(child);
            }
        }
        if (!file.delete()) throw new IOException("Не удалось удалить " + file.getName());
    }
}