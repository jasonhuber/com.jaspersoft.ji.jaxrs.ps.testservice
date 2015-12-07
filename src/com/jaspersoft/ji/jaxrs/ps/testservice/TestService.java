package com.jaspersoft.ji.jaxrs.ps.testservice;


import com.jaspersoft.commons.semantic.metaapi.MetaData;
import com.jaspersoft.jasperserver.remote.exception.RemoteException;
import com.jaspersoft.ji.license.LicenseException;

import java.util.Locale;
import java.util.Map;


public interface TestService {

    public MetaData getTestMetaData(String domainUri, Locale locale) throws RemoteException, LicenseException;

    public Map executeTestQuery(String domainUri, String queryStr, Locale locale) throws RemoteException, LicenseException;

}

