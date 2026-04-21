package com.smartcampus.resources;

import com.smartcampus.exceptions.ResourceNotFoundException;
import com.smartcampus.exceptions.SensorUnavailableException;
import com.smartcampus.model.Sensor;
import com.smartcampus.model.SensorReading;
import com.smartcampus.store.InMemoryStore;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

@Produces(MediaType.APPLICATION_JSON)
public class SensorReadingResource {
    private final String sensorId;

    public SensorReadingResource(String sensorId) {
        this.sensorId = sensorId;
    }

    @GET
    @Path("/")
    public List<SensorReading> getReadings() {
        ensureSensorExists();
        return InMemoryStore.getOrCreateReadings(sensorId);
    }

    @POST
    @Path("/")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response addReading(SensorReading payload, @Context UriInfo uriInfo) {
        Sensor sensor = ensureSensorExists();
        if ("MAINTENANCE".equals(sensor.getStatus().toUpperCase(Locale.ROOT))) {
            throw new SensorUnavailableException("Sensor is in MAINTENANCE and cannot accept new readings.");
        }

        if (payload == null) {
            throw new BadRequestException("Reading payload is required.");
        }

        String readingId = (payload.getId() == null || payload.getId().trim().isEmpty())
            ? UUID.randomUUID().toString()
            : payload.getId();
        long timestamp = payload.getTimestamp() <= 0 ? System.currentTimeMillis() : payload.getTimestamp();

        SensorReading newReading = new SensorReading(readingId, timestamp, payload.getValue());
        InMemoryStore.getOrCreateReadings(sensorId).add(newReading);
        sensor.setCurrentValue(newReading.getValue());

        URI location = uriInfo.getAbsolutePathBuilder().path(newReading.getId()).build();
        return Response.created(location).entity(newReading).build();
    }

    private Sensor ensureSensorExists() {
        Sensor sensor = InMemoryStore.SENSORS.get(sensorId);
        if (sensor == null) {
            throw new ResourceNotFoundException("Sensor not found: " + sensorId);
        }
        return sensor;
    }
}
