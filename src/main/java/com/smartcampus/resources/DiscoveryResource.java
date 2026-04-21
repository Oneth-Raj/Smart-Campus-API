package com.smartcampus.resources;

import java.util.LinkedHashMap;
import java.util.Map;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Path("/")
@Produces(MediaType.APPLICATION_JSON)
public class DiscoveryResource {

    @GET
    public Map<String, Object> discover() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("name", "University Smart Building Control System");
        response.put("version", "v1");

        Map<String, String> contact = new LinkedHashMap<>();
        contact.put("owner", "Oneth Rajakaruna");
        contact.put("email", "20240870@university.ac.uk");
        response.put("contact", contact);

        Map<String, String> resources = new LinkedHashMap<>();
        resources.put("rooms", "/api/v1/rooms");
        resources.put("sensors", "/api/v1/sensors");
        response.put("resources", resources);

        return response;
    }
}
