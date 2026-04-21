package com.smartcampus.mappers;

import com.smartcampus.exceptions.LinkedResourceNotFoundException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

@Provider
public class LinkedResourceNotFoundExceptionMapper implements ExceptionMapper<LinkedResourceNotFoundException> {

    @Context
    private UriInfo uriInfo;

    @Override
    public Response toResponse(LinkedResourceNotFoundException exception) {
        ErrorResponse error = new ErrorResponse(
            422,
            "Unprocessable Entity",
            exception.getMessage(),
            uriInfo.getPath());

        return Response.status(422)
            .type(MediaType.APPLICATION_JSON)
            .entity(error)
            .build();
    }
}
