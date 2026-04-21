package com.smartcampus.mappers;

import com.smartcampus.exceptions.SensorUnavailableException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

@Provider
public class SensorUnavailableExceptionMapper implements ExceptionMapper<SensorUnavailableException> {

    @Context
    private UriInfo uriInfo;

    @Override
    public Response toResponse(SensorUnavailableException exception) {
        ErrorResponse error = new ErrorResponse(
            Response.Status.FORBIDDEN.getStatusCode(),
            "Forbidden",
            exception.getMessage(),
            uriInfo.getPath());

        return Response.status(Response.Status.FORBIDDEN)
            .type(MediaType.APPLICATION_JSON)
            .entity(error)
            .build();
    }
}
