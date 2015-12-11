/*
 * Copyright (C) 2005 - 2014 TIBCO Software Inc. All rights reserved.
 * http://www.jaspersoft.com.
 * Licensed under commercial Jaspersoft Subscription License Agreement
 */
package com.jaspersoft.ji.jaxrs.ps.testservice;

import com.jaspersoft.commons.semantic.datasource.SemanticLayerDataSource;
import com.jaspersoft.commons.semantic.metaapi.MetaData;
import com.jaspersoft.jasperserver.api.common.domain.ExecutionContext;
import com.jaspersoft.jasperserver.api.common.domain.impl.ExecutionContextImpl;
import com.jaspersoft.jasperserver.api.metadata.common.domain.Folder;
import com.jaspersoft.jasperserver.api.metadata.common.service.RepositoryService;
import com.jaspersoft.jasperserver.dto.domain.ClientSimpleDomain;
import com.jaspersoft.jasperserver.dto.domain.DomainMetaData;
import com.jaspersoft.jasperserver.remote.common.RemoteServiceWrapper;
import com.jaspersoft.jasperserver.remote.customdatasources.TableMetadataConverter;
import com.jaspersoft.jasperserver.remote.exception.MandatoryParameterNotFoundException;
import com.jaspersoft.jasperserver.remote.exception.RemoteException;
import com.jaspersoft.jasperserver.remote.resources.converters.ToClientConversionOptions;
import com.jaspersoft.ji.adhoc.service.AdhocEngineServiceImpl;
import com.jaspersoft.ji.remote.converters.DomainMetaDataConverter;
import com.jaspersoft.ji.remote.resources.converters.SemanticLayerDataSourceResourceConverter;
import com.jaspersoft.ji.remote.services.DomainService;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

/**
 * @author Jason Huber
 * @version $Id: Test.java 	$
 */
@Component
@Path("/test")
public class Test extends RemoteServiceWrapper<TestService> {
    @Context
    private HttpServletRequest servletRequest;
    @Resource
    private AdhocEngineServiceImpl adhocEngineService;
    @Resource(name = "concreteRepository")
    private RepositoryService repositoryService;
    @Resource
    private SemanticLayerDataSourceResourceConverter semanticLayerDataSourceResourceConverter;
    @Resource
    private DomainMetaDataConverter converter;
    @Resource
    private TableMetadataConverter tableMetadataConverter;
    @Resource(name = "Test")
    public void setRemoteService(TestService remoteService) {
        this.remoteService = remoteService;
    }

    @GET
    @Path("/{domainUri: .+}/metadata/")
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public DomainMetaData getDomainMetadata(final @PathParam("domainUri") String _domainUri) throws RemoteException {
        final String domainUri = "/" + _domainUri;
        MetaData domainMetaData = remoteService.getTestMetaData(domainUri, servletRequest.getLocale());
        return converter.toClient(domainMetaData, null);
    }

    @POST
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public Response generateDomain(ClientSimpleDomain clientDomain) throws Exception {
        final String uri = clientDomain.getUri();
        if (uri == null || uri.isEmpty()) {
            throw new MandatoryParameterNotFoundException("uri");
        }
        int lastSeparator = uri.lastIndexOf(Folder.SEPARATOR);
        final String name = uri.substring(lastSeparator + Folder.SEPARATOR_LENGTH);
        String parentFolderUri = uri.substring(0, lastSeparator);
        parentFolderUri = parentFolderUri.equals("") ? Folder.SEPARATOR : parentFolderUri;
        final String dataSourceUri = clientDomain.getDataSource() != null ? clientDomain.getDataSource().getUri() : null;
        if (dataSourceUri == null || dataSourceUri.isEmpty()) {
            throw new MandatoryParameterNotFoundException("dataSource.uri");
        }
        final String label = clientDomain.getLabel() != null ? clientDomain.getLabel() : name;
        final String description = clientDomain.getDescription();
        final ExecutionContext runtimeExecutionContext = ExecutionContextImpl.getRuntimeExecutionContext();
        final SemanticLayerDataSource domain = adhocEngineService.createSemanticLayerDataSourceFromResource(runtimeExecutionContext,
                repositoryService.getResource(runtimeExecutionContext, dataSourceUri),
                tableMetadataConverter.toServer(clientDomain.getMetadata(), null),
                label, description, parentFolderUri, name);
        return Response.status(Response.Status.CREATED).entity(
                semanticLayerDataSourceResourceConverter.toClient(domain, ToClientConversionOptions.getDefault())).build();
    }
}
