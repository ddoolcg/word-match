package com.lcg.match.utils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * ZIP 文件下载与解压工具。
 *
 * <p>该工具执行同步网络和文件 I/O，调用方应在后台线程中使用。</p>
 *
 * @author lei.chuguang Email:475825657@qq.com
 * @version 2.0
 * @since 2017/5/4 21:04
 */
public final class ModelDownload {
    private ModelDownload() {
    }

    /**
     * 从默认地址下载 ZIP 文件，并解压至应用专属外部存储目录。
     *
     * @param rootPath 存储目录的根路径
     * @param url      ZIP 文件的网络地址
     * @return ZIP 文件的解压路径，下载失败、存储目录不可用或解压失败时返回 null
     * @throws Exception 下载或解压过程中发生异常时抛出
     */
    public static String load(String rootPath, String url) throws Exception {
        String path = rootPath + "model";
        File file = new File(path + File.separator + "model.txt");
        if (file.exists()) return path;
        //
        HttpURLConnection connection = null;
        InputStream inputStream = null;
        ZipInputStream zipInputStream = null;
        try {
            URL url1 = new URL(url);
            connection = (HttpURLConnection) url1.openConnection();
            connection.setConnectTimeout(15_000);
            connection.setReadTimeout(30_000);
            connection.setInstanceFollowRedirects(true);
            // 获取响应码
            int responseCode = connection.getResponseCode();
            if (responseCode < HttpURLConnection.HTTP_OK
                    || responseCode >= HttpURLConnection.HTTP_MULT_CHOICE) {
                throw new IOException("Unexpected HTTP response: " + responseCode);
            }
            // 获取输入流
            inputStream = connection.getInputStream();
            // 创建 ZipInputStream
            zipInputStream = new ZipInputStream(inputStream);
            // 解压 ZIP 文件
            extract(zipInputStream, path);
            return path;
        } finally {
            // Disconnect the connection
            if (connection != null) connection.disconnect();
            // Close the input stream
            try {
                if (inputStream != null) inputStream.close();
            } catch (IOException ignored) {
            }
            // Close the zip input stream
            try {
                if (zipInputStream != null) zipInputStream.close();
            } catch (IOException ignored) {
            }
        }
    }

    /**
     * 逐个解压 ZIP 条目，并校验目标路径，防止条目写出目标目录。
     */
    private static void extract(ZipInputStream zipInputStream, String unzipFilePath)
            throws Exception {
        byte[] buffer = new byte[8 * 1024];
        int ok = 0;
        // 遍历 ZIP 条目
        ZipEntry entry;
        while ((entry = zipInputStream.getNextEntry()) != null) {
            String entryName = entry.getName();
            if (entryName.startsWith("am/")) {
                ok |= 0b0001;
            } else if (entryName.startsWith("conf/")) {
                ok |= 0b0010;
            } else if (entryName.startsWith("graph/")) {
                ok |= 0b0100;
            } else if (entryName.startsWith("ivector/")) {
                ok |= 0b1000;
            }
            //构建压缩包中一个文件解压后保存的文件全路径
            String entryFilePath = unzipFilePath + File.separator + entryName;
            File entryFile = new File(entryFilePath);
            if (entry.isDirectory()) {
                entryFile.mkdirs();
            } else {
                FileOutputStream out = new FileOutputStream(entryFile);
                // 读取条目数据并写入文件
                int count;
                while ((count = zipInputStream.read(buffer)) != -1) {
                    out.write(buffer, 0, count);
                }
                out.flush();
                out.close(); // Close the output stream
            }
            // 关闭当前条目
            zipInputStream.closeEntry();
        }
        if ((ok & 0b1111) != 0b1111) return;
        // 创建标记文件
        FileOutputStream stream = new FileOutputStream(unzipFilePath + File.separator + "model.txt");
        stream.write("ok".getBytes());
        stream.close();
    }
}
