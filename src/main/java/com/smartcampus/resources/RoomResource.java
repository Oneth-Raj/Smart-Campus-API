package com.smartcampus.resources;

import com.smartcampus.exceptions.ResourceNotFoundException;
import com.smartcampus.exceptions.RoomNotEmptyException;
import com.smartcampus.model.Room;
import com.smartcampus.store.InMemoryStore;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

@Path("/rooms")
@Produces(MediaType.APPLICATION_JSON)
public class RoomResource {

    public RoomResource() {
        // No-arg constructor required by JAX-RS
    }

    @GET
    public List<Room> getAllRooms() {
        return new ArrayList<>(InMemoryStore.ROOMS.values());
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response createRoom(Room room, @Context UriInfo uriInfo) {
        if (room == null || isBlank(room.getId()) || isBlank(room.getName())) {
            throw new BadRequestException("Room id and name are required.");
        }
        
        if (room.getCapacity() <= 0) {
            throw new BadRequestException("Room capacity must be greater than 0.");
        }

        Room newRoom = new Room(room.getId(), room.getName(), room.getCapacity(), room.getSensorIds());
        // Using ConcurrentHashMap.putIfAbsent() for atomic check-and-insert
        // This prevents race conditions when two threads create the same ID
        Room existing = InMemoryStore.ROOMS.putIfAbsent(newRoom.getId(), newRoom);
        if (existing != null) {
            throw new WebApplicationException(
                Response.status(Response.Status.CONFLICT)
                    .entity(Map.of("message", "A room with this id already exists."))
                    .type(MediaType.APPLICATION_JSON)
                    .build());
        }

        URI location = uriInfo.getAbsolutePathBuilder().path(newRoom.getId()).build();
        return Response.created(location).entity(newRoom).build();
    }

    @GET
    @Path("/{roomId}")
    public Room getRoomById(@PathParam("roomId") String roomId) {
        Room room = InMemoryStore.ROOMS.get(roomId);
        if (room == null) {
            throw new ResourceNotFoundException("Room not found: " + roomId);
        }
        return room;
    }

    @DELETE
    @Path("/{roomId}")
    public Response deleteRoom(@PathParam("roomId") String roomId) {
        Room room = InMemoryStore.ROOMS.get(roomId);
        if (room == null) {
            throw new ResourceNotFoundException("Room not found: " + roomId);
        }

        if (room.getSensorIds() != null && !room.getSensorIds().isEmpty()) {
            throw new RoomNotEmptyException("Room cannot be deleted while sensors are assigned.");
        }

        InMemoryStore.ROOMS.remove(roomId);
        return Response.noContent().build();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
