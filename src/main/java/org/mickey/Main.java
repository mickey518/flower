package org.mickey;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.IOException;

@SpringBootApplication
public class Main {
    public static void main(String[] args) {
        SpringApplication.run(Main.class, args);

        // 自动打开网页
        String url = "http://127.0.0.1:8080/index.html";
        openUrl(url);
    }

    private static void openUrl(String url) {
        try {
            Runtime.getRuntime().exec("rundll32 url.dll,FileProtocolHandler " + url);
        } catch (IOException e) {
            System.err.println("请手动打开：" + url);
        }
    }
}