/**
 * Copyright (C) 2012-2016 52°North Initiative for Geospatial Open Source
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
package org.n52.sos.gda;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.n52.sos.ogc.gml.ReferenceType;
import org.n52.sos.ogc.gml.time.TimeInstant;
import org.n52.sos.ogc.gml.time.TimePeriod;
import org.n52.sos.response.AbstractServiceResponse;
import org.n52.sos.util.CollectionHelper;
import org.n52.sos.util.StringHelper;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

/**
 * Response of a {@link GetDataAvailabilityRequest}.
 * 
 * @author Christian Autermann
 * 
 * @since 4.0.0
 */
public class GetDataAvailabilityResponse extends AbstractServiceResponse {
    private final List<DataAvailability> dataAvailabilities = new LinkedList<DataAvailability>();
    private String responseFormat;
    private String namespace = GetDataAvailabilityConstants.NS_GDA;

    /**
     * Creates a new {@code GetDataAvailabilityResponse} consisting of zero or
     * more {@code DataAvailability} objects.
     * 
     * @param dataAvailabilities
     *            the data availabilities
     */
    public GetDataAvailabilityResponse(DataAvailability... dataAvailabilities) {
        Collections.addAll(this.dataAvailabilities, dataAvailabilities);
    }

    @Override
    public String getOperationName() {
        return GetDataAvailabilityConstants.OPERATION_NAME;
    }

    /**
     * @return the {@code DataAvailabilities}.
     */
    public List<DataAvailability> getDataAvailabilities() {
        return Collections.unmodifiableList(dataAvailabilities);
    }

    /**
     * Adds a new {@code DataAvailability} to the response.
     * 
     * @param dataAvailability
     *            the {@code DataAvailability}.
     */
    public void addDataAvailability(DataAvailability dataAvailability) {
        this.dataAvailabilities.add(dataAvailability);
    }

    /**
     * Sets the {@code DataAvailabilities} of the response.
     * 
     * @param dataAvailabilities
     *            the {@code DataAvailabilities}
     */
    public void setDataAvailabilities(Collection<? extends DataAvailability> dataAvailabilities) {
        this.dataAvailabilities.clear();
        this.dataAvailabilities.addAll(dataAvailabilities);
    }
    
    /**
     * @return the responseFormat
     */
    public String getResponseFormat() {
        return responseFormat;
    }

    /**
     * @param responseFormat the responseFormat to set
     */
    public void setResponseFormat(String responseFormat) {
        this.responseFormat = responseFormat;
    }
    
    public boolean isSetResponseFormat() {
        return !Strings.isNullOrEmpty(getResponseFormat());
    }

    public void setNamespace(String namespace) {
        if (StringHelper.isNotEmpty(namespace)) {
            this.namespace = namespace;
        }
    }

    public String getNamespace() {
        return this.namespace;
    }

    /**
     * Describes the availability of observation with a specified combination of
     * {@code featureOfInterest}, {@code observedProperty} and {@code procedure}
     * .
     */
    public static class DataAvailability {

        private final ReferenceType featureOfInterest;

        private final ReferenceType observedProperty;

        private final ReferenceType procedure;

        private final TimePeriod phenomenonTime;
        
        private long count = -1;
        
        private List<TimeInstant> resultTimes = Lists.newArrayList();
        
        private ReferenceType offering;
        
        private Set<FormatDescriptor> formatDescriptors;
        
        private Object metadata;
        
        /**
         * Creates a new {@code DataAvailability}.
         * 
         * @param featureOfInterest
         *            the {@code featureOfInterest}
         * @param observedProperty
         *            the {@code observedProperty}
         * @param procedure
         *            the {@code procedure}
         * @param phenomenonTime
         *            the {@code phenomenonTime} for which data is available.
         */
        public DataAvailability(ReferenceType procedure, ReferenceType observedProperty,
                ReferenceType featureOfInterest, TimePeriod phenomenonTime) {
            this.observedProperty = observedProperty;
            this.procedure = procedure;
            this.featureOfInterest = featureOfInterest;
            this.phenomenonTime = phenomenonTime;
        }
        
        /**
         * Creates a new {@code DataAvailability}.
         * 
         * @param featureOfInterest
         *            the {@code featureOfInterest}
         * @param observedProperty
         *            the {@code observedProperty}
         * @param procedure
         *            the {@code procedure}
         * @param phenomenonTime
         *            the {@code phenomenonTime} for which data is available.
         * @param valueCount
         *            the {@code valueCount} for this combination.
         */
        public DataAvailability(ReferenceType procedure, ReferenceType observedProperty,
                ReferenceType featureOfInterest, TimePeriod phenomenonTime, long valueCount) {
            this.observedProperty = observedProperty;
            this.procedure = procedure;
            this.featureOfInterest = featureOfInterest;
            this.phenomenonTime = phenomenonTime;
            this.count = valueCount;
        }

        /**
         * @return the {@code featureOfInterest}
         */
        public ReferenceType getFeatureOfInterest() {
            return featureOfInterest;
        }

        /**
         * @return the {@code observedProperty}
         */
        public ReferenceType getObservedProperty() {
            return observedProperty;
        }

        /**
         * @return the {@code procedure}
         */
        public ReferenceType getProcedure() {
            return procedure;
        }

        /**
         * @return the {@code phenomenonTime} for which data is available.
         */
        public TimePeriod getPhenomenonTime() {
            return phenomenonTime;
        }
        
        /**
         * @return the {@code count} for this combination.
         */
        public long getCount() {
            return count;
        }
        
        /**
         * Set the {@code count} for this combination
         * @return this.
         */
        public DataAvailability setCount(long count) {
            this.count = count;
            return this;
        }

        public boolean isSetCount() {
            return count >= 0 ;
        }
        
        public DataAvailability setResultTimes(List<TimeInstant> resultTimes) {
            if (resultTimes != null) {
                this.resultTimes = resultTimes;
            }
            return this;
        }
        
        public DataAvailability addResultTime(TimeInstant resultTime) {
            getResultTimes().add(resultTime);
            return this;
        }
        
        public List<TimeInstant> getResultTimes() {
            return this.resultTimes;
        }
        
        public boolean isSetResultTime() {
            return CollectionHelper.isNotEmpty(getResultTimes());
        }

        /**
         * @return the offering
         */
        public ReferenceType getOffering() {
            return offering;
        }

        /**
         * @param offering the offering to set
         */
        public void setOffering(ReferenceType offering) {
            this.offering = offering;
        }
        
        public boolean isSetOffering() {
            return getOffering() != null && getOffering().isSetHref();
        }

        /**
         * @return the observationTypes
         */
        public Set<FormatDescriptor> getFormatDescriptors() {
            return formatDescriptors;
        }

        /**
         * @param observationTypes the observationTypes to set
         */
        public void setFormatDescriptors(Set<FormatDescriptor> formatDescriptors) {
            this.formatDescriptors = formatDescriptors;
        }
        
        public boolean isSetFormatDescriptors() {
            return CollectionHelper.isNotEmpty(getFormatDescriptors());
        }

        /**
         * @return the metadata
         */
        public Object getMetadata() {
            return metadata;
        }

        /**
         * @param metadata the metadata to set
         */
        public void setMetadata(Object metadata) {
            this.metadata = metadata;
        }
        
        public boolean isSetMetadata() {
            return getMetadata() != null;
        }
    }
    
    /**
     * @author <a href="mailto:c.hollmann@52north.org">Carsten Hollmann</a>
     * @since 4.4.0
     *
     */
    public static class FormatDescriptor { 
        private String responseFormat;
        private Set<String> observationTypes;
        
        /**
         * @param responseFormat
         * @param observationTypes
         */
        public FormatDescriptor(String responseFormat, Set<String> observationTypes) {
            super();
            this.responseFormat = responseFormat;
            this.observationTypes = observationTypes;
        }
        
        /**
         * @return the responseFormat
         */
        public String getResponseFormat() {
            return responseFormat;
        }
        
        /**
         * @param responseFormat the responseFormat to set
         */
        public void setResponseFormat(String responseFormat) {
            this.responseFormat = responseFormat;
        }
        
        /**
         * @return the observationTypes
         */
        public Set<String> getObservationTypes() {
            return observationTypes;
        }
        
        /**
         * @param observationTypes the observationTypes to set
         */
        public void setObservationTypes(Set<String> observationTypes) {
            this.observationTypes.clear();
            this.observationTypes.addAll(observationTypes);
        }
        
    }
}
