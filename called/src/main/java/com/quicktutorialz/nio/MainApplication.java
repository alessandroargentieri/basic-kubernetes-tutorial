package com.quicktutorialz.nio;

import com.quicktutorialz.nio.handlers.PersonHandler;
import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.util.Headers;

public class MainApplication {

    public static void main(String[] args) {

        int port = 8081;
        try {
            port = Integer.parseInt(System.getenv("CALLEDPORT"));
        }catch(NumberFormatException e){
            //e.printStackTrace();
        }

        String host = "0.0.0.0";

        Undertow server = Undertow.builder()
                .addHttpListener(port, host)
                .setHandler(Handlers.path()
                        .addPrefixPath("/called", new PersonHandler())
                        .addPrefixPath("/", exchange -> {
                            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/plain");
                            exchange.getResponseSender().send("400 - Invalid request");
                        })
                )
                .build();
        server.start();
    }

}
