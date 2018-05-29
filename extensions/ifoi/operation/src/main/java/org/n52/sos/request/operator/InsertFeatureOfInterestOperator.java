/**
 * Copyright (C) 2012-2018 52°North Initiative for Geospatial Open Source
 * Software GmbH
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 as published
 * by the Free Software Foundation.
 *
 * If the program is linked with libraries which are licensed under one of
 * the following licenses, the combination of the program with the linked
 * library is not considered a "derivative work" of the program:
 *
 *     - Apache License, version 2.0
 *     - Apache Software License, version 1.0
 *     - GNU Lesser General Public License, version 3
 *     - Mozilla Public License, versions 1.0, 1.1 and 2.0
 *     - Common Development and Distribution License (CDDL), version 1.0
 *
 * Therefore the distribution of the program linked with libraries licensed
 * under the aforementioned licenses, is permitted by the copyright holders
 * if the distribution is compliant with both the GNU General Public
 * License version 2 and the aforementioned licenses.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 */
package org.n52.sos.request.operator;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.n52.shetland.ogc.sos.ifoi.InsertFeatureOfInterestConstants;
import org.n52.sos.ds.AbstractInsertFeatureOfInterestHandler;
import org.n52.sos.event.SosEventBus;
import org.n52.sos.event.events.FeatureInsertion;
import org.n52.sos.exception.ows.InvalidParameterValueException;
import org.n52.sos.exception.ows.MissingParameterValueException;
import org.n52.sos.ogc.gml.AbstractFeature;
import org.n52.sos.ogc.ows.CompositeOwsException;
import org.n52.sos.ogc.ows.OwsExceptionReport;
import org.n52.sos.ogc.sos.Sos2Constants;
import org.n52.sos.ogc.sos.SosConstants;
import org.n52.sos.request.InsertFeatureOfInterestRequest;
import org.n52.sos.response.InsertFeatureOfInterestResponse;
import org.n52.sos.util.JavaHelper;
import org.n52.sos.wsdl.WSDLOperation;

/**
 * {@code IRequestOperator} to handle {@link InsertFeatureOfInterestRequest}s.
 *
 * @author Christian Autermann
 *
 * @since 4.0.0
 */
public class InsertFeatureOfInterestOperator
        extends 
        AbstractTransactionalRequestOperator<AbstractInsertFeatureOfInterestHandler, InsertFeatureOfInterestRequest, InsertFeatureOfInterestResponse>
        implements WSDLAwareRequestOperator {

    private static final Set<String> CONFORMANCE_CLASSES = Collections
            .singleton(InsertFeatureOfInterestConstants.CONFORMANCE_CLASS);

    /**
     * Constructs a new {@code InsertFeatureOfInterestOperator}.
     */
    public InsertFeatureOfInterestOperator() {
        super(SosConstants.SOS, Sos2Constants.SERVICEVERSION, InsertFeatureOfInterestConstants.OPERATION_NAME,
                InsertFeatureOfInterestRequest.class);
    }

    @Override
    public Set<String> getConformanceClasses() {
        return Collections.unmodifiableSet(CONFORMANCE_CLASSES);
    }

    @Override
    public InsertFeatureOfInterestResponse receive(InsertFeatureOfInterestRequest request) throws OwsExceptionReport {
        InsertFeatureOfInterestResponse response = getDao().insertFeatureOfInterest(request);
        SosEventBus.fire(new FeatureInsertion(request, response));
        return response;
    }

    @Override
    protected void checkParameters(InsertFeatureOfInterestRequest request) throws OwsExceptionReport {
        CompositeOwsException exceptions = new CompositeOwsException();

        try {
            checkServiceParameter(request.getService());
        } catch (OwsExceptionReport owse) {
            exceptions.add(owse);
        }
        try {
            checkSingleVersionParameter(request);
        } catch (OwsExceptionReport owse) {
            exceptions.add(owse);
        }

        try {
            if (!request.hasFeatureMembers()) {
                throw new MissingParameterValueException("featureMember");
            }
            checkFeatureMembers(request.getFeatureMembers());
        } catch (OwsExceptionReport owse) {
            exceptions.add(owse);
        }
        exceptions.throwIfNotEmpty();
    }

    private void checkFeatureMembers(List<AbstractFeature> featureMembers) throws OwsExceptionReport {
        for (AbstractFeature abstractFeature : featureMembers) {
            if (!abstractFeature.isSetIdentifier()) {
                abstractFeature.setIdentifier(JavaHelper.generateID(abstractFeature.toString()));
            }
            if (getCache().hasFeatureOfInterest(abstractFeature.getIdentifier())) {
                throw new InvalidParameterValueException()
                    .at("featureMember.identifier")
                    .withMessage(
                            "The featureOfInterest with identifier '%s' still exists!",
                            abstractFeature.getIdentifier());
            }
        }
    }

    @Override
    public WSDLOperation getSosOperationDefinition() {
        return null;
    }

    @Override
    public Map<String, String> getAdditionalSchemaImports() {
        return Collections.emptyMap();
    }

    @Override
    public Map<String, String> getAdditionalPrefixes() {
        return Collections.emptyMap();
    }
}
