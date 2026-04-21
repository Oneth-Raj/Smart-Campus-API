package com.smartcampus.mappers;

import java.util.logging.Level;
import java.util.logging.Logger;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

@Provider
public class GlobalExceptionMapper implements ExceptionMapper<Throwable> {
    private static final Logger LOGGER = Logger.getLogger(GlobalExceptionMapper.class.getName());

    @Context
    private UriInfo uriInfo;

    @Override
    public Response toResponse(Throwable exception) {
        if (exception instanceof WebApplicationException) {
            WebApplicationException webEx = (WebApplicationException) exception;
            Response existing = webEx.getResponse();
            if (existing != null && existing.hasEntity()) {
                return existing;
            }
            int status = existing != null ? existing.getStatus() : Response.Status.INTERNAL_SERVER_ERROR.getStatusCode();
            ErrorResponse error = new ErrorResponse(
                status,
                Response.Status.fromStatusCode(status) != null
                    ? Response.Status.fromStatusCode(status).getReasonPhrase()
                    : "Error",
                exception.getMessage() == null ? "Request could not be processed." : exception.getMessage(),
                uriInfo.getPath());
            return Response.status(status)
                .type(MediaType.APPLICATION_JSON)
                .entity(error)
                .build();
        }

        LOGGER.log(Level.SEVERE, "Unhandled exception", exception);
        ErrorResponse error = new ErrorResponse(
            Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(),
            "Internal Server Error",
            "An unexpected error occurred. Please contact support.",
            uriInfo == null ? "" : uriInfo.getPath());

        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
            .type(MediaType.APPLICATION_JSON)
            .entity(error)
            .build();
    }
}
