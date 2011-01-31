package org.taverna.server.master.rest;

import static javax.ws.rs.core.Response.Status.NOT_FOUND;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

import org.taverna.server.master.exceptions.NoDirectoryEntryException;

@Provider
public class NoDirectoryEntryHandler extends HandlerCore implements
		ExceptionMapper<NoDirectoryEntryException> {
	@Override
	public Response toResponse(NoDirectoryEntryException exn) {
		return respond(NOT_FOUND, exn);
	}
}