package com.smartcampus.resources;

import com.smartcampus.exceptions.LinkedResourceNotFoundException;
import com.smartcampus.exceptions.ResourceNotFoundException;
import com.smartcampus.model.Room;
import com.smartcampus.model.Sensor;
import com.smartcampus.store.InMemoryStore;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

@Path("/sensors")
@Produces(MediaType.APPLICATION_JSON)
public class SensorResource {
    // Supported sensor types: CO2, Temperature, Occupancy, Light Intensity


    public SensorResource() {
        // No-arg constructor required by JAX-RS
    }

    @GET
    public List<Sensor> getAllSensors(@QueryParam("type") String type) {
        List<Sensor> all = new ArrayList<>(InMemoryStore.SENSORS.values());
        if (type == null || type.trim().isEmpty()) {
            return all;
        }

        String expectedType = type.trim().toLowerCase(Locale.ROOT);
        return all.stream()
            .filter(sensor -> sensor.getType() != null)
            .filter(sensor -> sensor.getType().toLowerCase(Locale.ROOT).equals(expectedType))
            .collect(Collectors.toList());
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response createSensor(Sensor sensor, @Context UriInfo uriInfo) {
        if (sensor == null || isBlank(sensor.getId()) || isBlank(sensor.getType()) || isBlank(sensor.getRoomId())) {
            throw new BadRequestException("Sensor id, type and roomId are required.");
        }

        Room room = InMemoryStore.ROOMS.get(sensor.getRoomId());
        if (room == null) {
            throw new LinkedResourceNotFoundException("Linked room does not exist: " + sensor.getRoomId());
        }

        Sensor newSensor = new Sensor(
            sensor.getId(),
            sensor.getType(),
            normalizeStatus(sensor.getStatus()),
            sensor.getCurrentValue(),
            sensor.getRoomId());

        Sensor existing = InMemoryStore.SENSORS.putIfAbsent(newSensor.getId(), newSensor);
        if (existing != null) {
            throw new WebApplicationException(
                Response.status(Response.Status.CONFLICT)
                    .entity(Map.of("message", "A sensor with this id already exists."))
                    .type(MediaType.APPLICATION_JSON)
                    .build());
        }

        room.getSensorIds().add(newSensor.getId());

        URI location = uriInfo.getAbsolutePathBuilder().path(newSensor.getId()).build();
        return Response.created(location).entity(newSensor).build();
    }

    @GET
    @Path("/{sensorId}")
    public Sensor getSensorById(@PathParam("sensorId") String sensorId) {
        Sensor sensor = InMemoryStore.SENSORS.get(sensorId);
        if (sensor == null) {
            throw new ResourceNotFoundException("Sensor not found: " + sensorId);
        }
        return sensor;
    }

    @Path("/{sensorId}/readings")
    public SensorReadingResource sensorReadings(@PathParam("sensorId") String sensorId) {
        if (!InMemoryStore.SENSORS.containsKey(sensorId)) {
            throw new ResourceNotFoundException("Sensor not found: " + sensorId);
        }
        return new SensorReadingResource(sensorId);
    }

    private String normalizeStatus(String status) {
        if (status == null || status.trim().isEmpty()) {
            return "ACTIVE";
        }
        return status.trim().toUpperCase(Locale.ROOT);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
