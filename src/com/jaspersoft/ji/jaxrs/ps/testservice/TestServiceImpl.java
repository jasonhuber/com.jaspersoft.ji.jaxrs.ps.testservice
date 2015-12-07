package com.jaspersoft.ji.jaxrs.ps.testservice;

import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.jaspersoft.jasperserver.api.JSExceptionWrapper;
import com.jaspersoft.jasperserver.remote.exception.IllegalParameterValueException;
import com.jaspersoft.jasperserver.remote.exception.RemoteException;
import com.jaspersoft.jasperserver.remote.exception.ResourceNotFoundException;
import com.jaspersoft.jasperserver.remote.exception.xml.ErrorDescriptor;
import com.jaspersoft.ji.license.LicenseCheckStatus;
import com.jaspersoft.ji.license.LicenseException;
import com.jaspersoft.ji.license.LicenseManager;

import com.jaspersoft.ji.remote.services.DomainService;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jdom.input.JDOMParseException;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;

import com.jaspersoft.commons.semantic.ConfigurationObject;
import com.jaspersoft.commons.semantic.Item;
import com.jaspersoft.commons.semantic.Schema;
import com.jaspersoft.commons.semantic.datasource.SemanticLayerDataSource;
import com.jaspersoft.commons.semantic.datasource.SemanticLayerFactory;
import com.jaspersoft.commons.semantic.datasource.SemanticLayerQueryExecuter;
import com.jaspersoft.commons.semantic.impl.SchemaImpl;
import com.jaspersoft.commons.semantic.metaapi.MetaData;
import com.jaspersoft.commons.semantic.metaapi.MetaDataFactoryFactory;
import com.jaspersoft.commons.util.JSControlledJdbcQueryExecuterFactory;
import com.jaspersoft.jasperserver.api.engine.common.service.EngineService;
import com.jaspersoft.jasperserver.api.engine.jasperreports.util.JRDomainQueryExecuterAdapter;
import com.jaspersoft.jasperserver.api.metadata.common.domain.Query;
import com.jaspersoft.jasperserver.api.metadata.common.domain.Resource;
import com.jaspersoft.jasperserver.api.metadata.common.service.RepositoryService;
import org.springframework.stereotype.Component;

@Component("domainServicesService")
public class TestServiceImpl implements TestService {

    protected static final Log log = LogFactory.getLog(TestServiceImpl.class);

    public static final String DATASOURCE_NOT_FOUND = "DATASOURCE_NOT_FOUND";
    public static final String DATASOURCE_IS_NOT_DOMAIN = "DATASOURCE_IS_NOT_DOMAIN";
    public static final String CANNOT_EXTRACT_METADATA = "CANNOT_EXTRACT_METADATA";
    public static final String CANNOT_EXECUTE_DOMAIN_QUERY = "CANNOT_EXECUTE_DOMAIN_QUERY";
    public static final String NO_FIELDS_DOMAIN_QUERY = "NO_FIELDS_DOMAIN_QUERY";

    @javax.annotation.Resource(name = "concreteRepository")
    private RepositoryService repository;
    @javax.annotation.Resource(name = "messageSource")
    private MessageSource messages;
    @javax.annotation.Resource
    private MetaDataFactoryFactory metaDataFactoryFactory;
    @javax.annotation.Resource
    private EngineService engineService;
    @javax.annotation.Resource
    private ConfigurationObject slConfig;
    @javax.annotation.Resource
    private SemanticLayerFactory semanticLayerFactory;
    @javax.annotation.Resource(name="licenseManager")
    private LicenseManager licenseManager;

    /**
     *
     * @param domainUri
     * @param locale
     * @return MetaData object, guaranteed to be non-null (not found or not supported resource indicated by exception)
     * @throws ResourceNotFoundException if no resource found at domainUri
     * @throws RemoteException in case of unclassified error
     * @throws LicenseException
     */
    public MetaData getTestMetaData(String domainUri, Locale locale) throws RemoteException, LicenseException {
        /* Checking if license is valid and domains feature supported */
        checkLicense();

        LocaleContextHolder.setLocale(locale);

        Resource resource = repository.getResource(null, domainUri);
        if (resource == null) {
            throw new ResourceNotFoundException("Domain not found", domainUri);
        }
        if (!(resource instanceof SemanticLayerDataSource)) {
            throw new IllegalParameterValueException(new ErrorDescriptor.Builder().setErrorCode("resource.type.not.supported").setParameters(new String[] {domainUri}).getErrorDescriptor());
        }

        try {
        
            SemanticLayerDataSource dataSource = (SemanticLayerDataSource) resource;
            return metaDataFactoryFactory.getMetaData(dataSource, Collections.EMPTY_MAP);
            
        } catch (Exception ex) {
            throw new RemoteException(messages.getMessage(CANNOT_EXTRACT_METADATA, new Object[]{ domainUri }, locale), ex);
        }
    }
    
    public Map executeTestQuery(String domainUri, String queryStr, Locale locale) throws RemoteException, LicenseException {
        /* Checking if license is valid and domains feature supported */
        checkLicense();

        LocaleContextHolder.setLocale(locale);

        Resource resource = repository.getResource(null, domainUri);
        if (resource == null) {
            throw new RemoteException(messages.getMessage(DATASOURCE_NOT_FOUND, new Object[]{ domainUri }, locale));
        }
        if (!(resource instanceof SemanticLayerDataSource)) {
            throw new RemoteException(messages.getMessage(DATASOURCE_NOT_FOUND, new Object[]{ domainUri }, locale));
        }

        try {
            
            //SemanticLayerDataSource dataSource = (SemanticLayerDataSource) resource;
            //ReportDataSourceService dataSourceService = engineService.createDataSourceService(dataSource);
            Map reportParameters = new LinkedHashMap();
            // set "hard" limit to queries based on config
            reportParameters.put(
            		JSControlledJdbcQueryExecuterFactory.MAX_RESULT_SET_ROWS,
            		slConfig.getMaxResultSetRows());
            // add query timeout limit
            reportParameters.put(
                    JSControlledJdbcQueryExecuterFactory.MAX_QUERY_EXECUTION_TIME_SEC, 
                    new Integer(slConfig.getMaxExecutionTimeSec()));
            // add Domain specific objects
            reportParameters.put(SemanticLayerQueryExecuter.SEMANTIC_LAYER_FACTORY, semanticLayerFactory);
            Schema schema = semanticLayerFactory.getSchemaByURI(domainUri);
            reportParameters.put(SemanticLayerQueryExecuter.SEMANTIC_LAYER_SCHEMA, schema);

            com.jaspersoft.commons.semantic.Query domainQuery;
            try {
                 domainQuery = semanticLayerFactory.getQueryFromXML(queryStr, (SchemaImpl) schema);
            } catch (JDOMParseException ex) {
                throw new RemoteException("Can't parse the query: "+ex.getMessage());
            }

            List poList = domainQuery.getPresentationObjects();
            if (poList == null || poList.isEmpty()) {
                throw new RemoteException(messages.getMessage(NO_FIELDS_DOMAIN_QUERY, null, locale));
            }
            String[] columns = new String[poList.size()];
            for (int i = 0; i < poList.size(); i++) {
                Item po = (Item) poList.get(i);
                columns[i] = po.getFullId();
            }
            
            Query jrQuery = createQuery(queryStr, domainUri);
            return JRDomainQueryExecuterAdapter.executeQuery(jrQuery, columns, reportParameters);
        } catch (RemoteException ex) {
            throw ex;
        } catch (JSExceptionWrapper ex) {
            throw new RemoteException(ex.getMessage(), ex);
        } catch (Exception ex) {
            throw new RemoteException(messages.getMessage(CANNOT_EXECUTE_DOMAIN_QUERY, new Object[]{ domainUri }, locale), ex);
        }
    }
    
    private Query createQuery(String queryStr, String dataSourceUri) throws Exception {
        Query q = (Query) getRepository().newResource(null, Query.class);

        q.setCreationDate(new Date());
        //q.setName(name);
        //q.setLabel(label);

        q.setLanguage("domain");
        q.setDataSourceReference(dataSourceUri);

        q.setSql(queryStr);

        return q;
    }

    private void checkLicense() throws LicenseException {
        LicenseCheckStatus licenseCheckStatus = licenseManager.checkLicense();
        if (licenseCheckStatus.isLicenseAccepted()) {
            if (!licenseManager.isFeatureSupported("AHD")) {
                String message = getMessages().getMessage("LIC_014_feature.not.licensed.domains", null, LocaleContextHolder.getLocale());
                throw new LicenseException(message);
            }
        } else {
            throw new LicenseException("License fail." + licenseCheckStatus.getMessage());
        }
    }

    public RepositoryService getRepository() {
        return repository;
    }

    public void setRepository(RepositoryService repositoryService) {
        this.repository = repositoryService;
    }

    public MessageSource getMessages() {
        return messages;
    }

    public void setMessages(MessageSource messages) {
        this.messages = messages;
    }

    public MetaDataFactoryFactory getMetaDataFactoryFactory() {
        return metaDataFactoryFactory;
    }

    public void setMetaDataFactoryFactory(
            MetaDataFactoryFactory metaDataFactoryFactory) {
        this.metaDataFactoryFactory = metaDataFactoryFactory;
    }

    public EngineService getEngineService() {
        return engineService;
    }

    public void setEngineService(EngineService engineService) {
        this.engineService = engineService;
    }

    public ConfigurationObject getSlConfig() {
        return slConfig;
    }

    public void setSlConfig(ConfigurationObject slConfig) {
        this.slConfig = slConfig;
    }

    public SemanticLayerFactory getSemanticLayerFactory() {
        return semanticLayerFactory;
    }

    public void setSemanticLayerFactory(SemanticLayerFactory semanticLayerFactory) {
        this.semanticLayerFactory = semanticLayerFactory;
    }

}
