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
     * @throws IOException 下载失败、存储目录不可用或解压失败时抛出
     */
    public static String load(String rootPath, String url) throws IOException {
        String path = rootPath + "model";
        File file = new File(rootPath + File.separator + "model.txt");
        if (file.exists()) return path;
        //
        URL url1 = new URL(url);
        HttpURLConnection connection = (HttpURLConnection) url1.openConnection();
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
        InputStream inputStream = connection.getInputStream();
        // 创建 ZipInputStream
        ZipInputStream zipInputStream = new ZipInputStream(inputStream);
        // 解压 ZIP 文件
        extract(zipInputStream, path);
        zipInputStream.close(); // Close the zip input stream
        inputStream.close(); // Close the input stream
        // 创建标记文件
        // 标记文件用于判断 ZIP 文件是否已下载
        // 创建标记文件
        FileOutputStream stream = new FileOutputStream(file);
        stream.write("ok".getBytes());
        stream.close();
        // 连接断开
        connection.disconnect();
        return path;
    }

    /**
     * 逐个解压 ZIP 条目，并校验目标路径，防止条目写出目标目录。
     */
    private static void extract(ZipInputStream zipInputStream, String unzipFilePath)
            throws IOException {
        byte[] buffer = new byte[8 * 1024];
        // 遍历 ZIP 条目
        ZipEntry entry;
        while ((entry = zipInputStream.getNextEntry()) != null) {
            //构建压缩包中一个文件解压后保存的文件全路径
            String entryFilePath = unzipFilePath + File.separator + entry.getName();
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
    }
}
