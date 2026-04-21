package com.smartcampus;

import com.smartcampus.config.SmartCampusApplication;
import java.io.IOException;
import java.net.URI;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;

public class Main {
    public static final String BASE_URI = "http://0.0.0.0:8080/api/v1/";

    public static HttpServer startServer() {
        ResourceConfig config = ResourceConfig.forApplication(new SmartCampusApplication());
        return GrizzlyHttpServerFactory.createHttpServer(URI.create(BASE_URI), config);
    }

    public static void main(String[] args) throws IOException {
        HttpServer server = startServer();
        System.out.println("Smart Campus API started at " + BASE_URI);
        System.out.println("Press Enter to stop the server...");
        System.in.read();
        server.shutdownNow();
    }
}
