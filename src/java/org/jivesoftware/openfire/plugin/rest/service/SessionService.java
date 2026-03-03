package org.jivesoftware.openfire.plugin.rest.service;

import javax.annotation.PostConstruct;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.jivesoftware.openfire.plugin.rest.controller.SessionController;
import org.jivesoftware.openfire.plugin.rest.entity.SessionEntities;
import org.jivesoftware.openfire.plugin.rest.exceptions.ServiceException;

@Path("restapi/v1/sessions")
public class SessionService {

    private SessionController sessionController;

    @PostConstruct
    public void init() {
        sessionController = SessionController.getInstance();
    }

    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public SessionEntities getAllSessions(
        @QueryParam("skipNameResolve") @DefaultValue("false") boolean skipNameResolve
    ) throws ServiceException {
        return sessionController.getAllSessions(skipNameResolve);
    }
    
    @GET
    @Path("/{username}")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public SessionEntities getUserSessions(
        @PathParam("username") String username,
        @QueryParam("skipNameResolve") @DefaultValue("false") boolean skipNameResolve
    ) throws ServiceException {
        return sessionController.getUserSessions(username, skipNameResolve);
    }
    
    @DELETE
    @Path("/{username}")
    public Response kickSession(@PathParam("username") String username) throws ServiceException {
        sessionController.removeUserSessions(username);
        return Response.status(Response.Status.OK).build();
    }
    
}
