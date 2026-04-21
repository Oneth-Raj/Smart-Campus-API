package com.smartcampus.mappers;

import com.smartcampus.exceptions.RoomNotEmptyException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

@Provider
public class RoomNotEmptyExceptionMapper implements ExceptionMapper<RoomNotEmptyException> {

    @Context
    private UriInfo uriInfo;

    @Override
    public Response toResponse(RoomNotEmptyException exception) {
        ErrorResponse error = new ErrorResponse(
            Response.Status.CONFLICT.getStatusCode(),
            "Conflict",
            exception.getMessage(),
            uriInfo.getPath());

        return Response.status(Response.Status.CONFLICT)
            .type(MediaType.APPLICATION_JSON)
            .entity(error)
            .build();
    }
}
