package com.lcg.match.utils;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 单词转换工具类
 *
 * @author lei.chuguang Email:475825657@qq.com
 * @since 2026/9/8
 */
public final class Factor {
    private Factor() {
    }

    public static List<String> transform(String url, List<String> words) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(15_000);
        connection.setReadTimeout(30_000);
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        connection.setRequestProperty("Accept", "application/json");
        try {
            JSONArray requestBody = new JSONArray();
            for (String word : words) {
                requestBody.put(word);
            }
            byte[] requestBytes = requestBody.toString().getBytes(StandardCharsets.UTF_8);
            try (OutputStream outputStream = connection.getOutputStream()) {
                outputStream.write(requestBytes);
            }

            int responseCode = connection.getResponseCode();
            InputStream responseStream = responseCode >= 200 && responseCode <= 299
                    ? connection.getInputStream()
                    : connection.getErrorStream();
            String responseText = readResponse(responseStream);
            if (responseCode < 200 || responseCode > 299) {
                throw new IOException("Factor API returned HTTP " + responseCode + ": " + responseText);
            }

            JSONArray response;
            List<String> result = new ArrayList<>();
            try {
                response = new JSONArray(responseText);
                for (int index = 0; index < response.length(); index++) {
                    result.add(response.getString(index));
                }
            } catch (JSONException exception) {
                throw new IOException("Factor API returned invalid JSON", exception);
            }
            return result;
        } finally {
            connection.disconnect();
        }
    }

    private static String readResponse(InputStream responseStream) throws IOException {
        if (responseStream == null) {
            return "";
        }
        StringBuilder responseText = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(responseStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                responseText.append(line);
            }
        }
        return responseText.toString();
    }
}
