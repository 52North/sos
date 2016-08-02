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
package org.n52.sos.ds.hibernate;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.hibernate.Criteria;
import org.hibernate.HibernateException;
import org.hibernate.Session;
import org.hibernate.criterion.DetachedCriteria;
import org.hibernate.criterion.Disjunction;
import org.hibernate.criterion.Projections;
import org.hibernate.criterion.Restrictions;
import org.hibernate.criterion.Subqueries;
import org.hibernate.spatial.criterion.SpatialProjections;
import org.hibernate.transform.ResultTransformer;
import org.n52.sos.config.annotation.Configurable;
import org.n52.sos.ds.FeatureQueryHandler;
import org.n52.sos.ds.FeatureQueryHandlerQueryObject;
import org.n52.sos.ds.HibernateDatasourceConstants;
import org.n52.sos.ds.I18NDAO;
import org.n52.sos.ds.hibernate.dao.FeatureOfInterestDAO;
import org.n52.sos.ds.hibernate.dao.FeatureOfInterestTypeDAO;
import org.n52.sos.ds.hibernate.dao.HibernateSqlQueryConstants;
import org.n52.sos.ds.hibernate.entities.FeatureOfInterest;
import org.n52.sos.ds.hibernate.util.HibernateHelper;
import org.n52.sos.ds.hibernate.util.QueryHelper;
import org.n52.sos.ds.hibernate.util.SpatialRestrictions;
import org.n52.sos.exception.ows.NoApplicableCodeException;
import org.n52.sos.exception.ows.concrete.NotYetSupportedException;
import org.n52.sos.i18n.I18NDAORepository;
import org.n52.sos.i18n.LocalizedString;
import org.n52.sos.i18n.metadata.I18NFeatureMetadata;
import org.n52.sos.ogc.OGCConstants;
import org.n52.sos.ogc.filter.SpatialFilter;
import org.n52.sos.ogc.gml.AbstractFeature;
import org.n52.sos.ogc.gml.CodeWithAuthority;
import org.n52.sos.ogc.om.features.samplingFeatures.SamplingFeature;
import org.n52.sos.ogc.ows.OwsExceptionReport;
import org.n52.sos.ogc.sos.SosConstants;
import org.n52.sos.ogc.sos.SosEnvelope;
import org.n52.sos.service.ServiceConfiguration;
import org.n52.sos.util.GeometryHandler;
import org.n52.sos.util.JavaHelper;
import org.n52.sos.util.SosHelper;
import org.n52.sos.util.StringHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;
import com.google.common.collect.Sets;
import com.vividsolutions.jts.geom.Geometry;


@Configurable
public class HibernateFeatureQueryHandler implements FeatureQueryHandler, HibernateSqlQueryConstants {
    private static final Logger LOGGER = LoggerFactory.getLogger(HibernateFeatureQueryHandler.class);
    private static final String SQL_QUERY_GET_ENVELOP = "getEnvelope";
    private static final String PATCH_FEATURE_ID = "feature_id";
    private static final String PATCH_GEOM = "pa";
    private static final String PATCH_MIN_Z = "minz";
    private static final String PATCH_MAX_Z = "maxz";
    private final MinMaxZTransformer transformer = new MinMaxZTransformer();
    
    @Deprecated
    @Override
    public AbstractFeature getFeatureByID(String featureID, Object connection, String version)
            throws OwsExceptionReport {
        FeatureQueryHandlerQueryObject queryObject = new FeatureQueryHandlerQueryObject();
        queryObject.setConnection(connection);
        queryObject.addFeatureIdentifier(featureID);
        queryObject.setVersion(version);
        return getFeatureByID(queryObject);
    }

    @Override
    public AbstractFeature getFeatureByID(FeatureQueryHandlerQueryObject queryObject) throws OwsExceptionReport {
        final Session session = HibernateSessionHolder.getSession(queryObject.getConnection());
        try {
            final Criteria q =
                    session.createCriteria(FeatureOfInterest.class).add(
                            Restrictions.eq(FeatureOfInterest.IDENTIFIER, queryObject.getFeatureIdentifier()));
            return createSosAbstractFeature((FeatureOfInterest) q.uniqueResult(), queryObject);
        } catch (final HibernateException he) {
            throw new NoApplicableCodeException().causedBy(he).withMessage(
                    "An error occurred while querying feature data for a featureOfInterest identifier!");
        }

    }

    @Deprecated
    @Override
    @SuppressWarnings("unchecked")
    public Collection<String> getFeatureIDs(final SpatialFilter filter, final Object connection)
            throws OwsExceptionReport {
        final Session session = HibernateSessionHolder.getSession(connection);
        try {
            if (getGeometryHandler().isSpatialDatasource()) {
                final Criteria c =
                        session.createCriteria(FeatureOfInterest.class).setProjection(
                                Projections.distinct(Projections.property(FeatureOfInterest.IDENTIFIER)));
                if (filter != null) {
                    c.add(SpatialRestrictions.filter(FeatureOfInterest.GEOMETRY, filter.getOperator(),
                            getGeometryHandler().switchCoordinateAxisFromToDatasourceIfNeeded(filter.getGeometry())));
                }
                return c.list();
            } else {

                final List<String> identifiers = new LinkedList<String>();
                final List<FeatureOfInterest> features = session.createCriteria(FeatureOfInterest.class).list();
                if (filter != null) {
                    final Geometry envelope = getGeometryHandler().getFilterForNonSpatialDatasource(filter);
                    for (final FeatureOfInterest feature : features) {
                        final Geometry geom = getGeomtery(feature, session);
                        if (geom != null && envelope.contains(geom)) {
                            identifiers.add(feature.getIdentifier());
                        }
                    }
                }
                return identifiers;
            }
        } catch (final HibernateException he) {
            throw new NoApplicableCodeException().causedBy(he).withMessage(
                    "An error occurred while querying feature identifiers for spatial filter!");
        }
    }

    @Override
    public Collection<String> getFeatureIDs(FeatureQueryHandlerQueryObject queryObject) throws OwsExceptionReport {
        return getFeatureIDs(queryObject.getSpatialFitler(), queryObject.getConnection());
    }

    @Deprecated
    @Override
    public Map<String, AbstractFeature> getFeatures(Collection<String> featureIDs, List<SpatialFilter> spatialFilters,
            Object connection, String version) throws OwsExceptionReport {
        FeatureQueryHandlerQueryObject queryObject = new FeatureQueryHandlerQueryObject();
        queryObject.setFeatureIdentifiers(featureIDs);
        queryObject.setSpatialFilters(spatialFilters);
        queryObject.setConnection(connection);
        queryObject.setVersion(version);
        return getFeatures(queryObject);
    }

    @Override
    public Map<String, AbstractFeature> getFeatures(FeatureQueryHandlerQueryObject queryObject)
            throws OwsExceptionReport {
        try {
            if (getGeometryHandler().isSpatialDatasource()) {
                return getFeaturesForSpatialDatasource(queryObject);
            } else {
                return getFeaturesForNonSpatialDatasource(queryObject);
            }
        } catch (final HibernateException he) {
            throw new NoApplicableCodeException().causedBy(he).withMessage(
                    "Error while querying features from data source!");
        }
    }

    @Deprecated
    @Override
    public SosEnvelope getEnvelopeForFeatureIDs(Collection<String> featureIDs, Object connection)
            throws OwsExceptionReport {
        return getEnvelopeForFeatureIDs(new FeatureQueryHandlerQueryObject().setFeatureIdentifiers(featureIDs)
                .setConnection(connection));
    }

    @Override
    public SosEnvelope getEnvelopeForFeatureIDs(FeatureQueryHandlerQueryObject queryObject) throws OwsExceptionReport {
        final Session session = HibernateSessionHolder.getSession(queryObject.getConnection());
        if (queryObject.isSetFeatureIdentifiers()) {
            try {
                Geometry geom =
                        (Geometry) session
                                .createCriteria(PatchEntity.class)
                                .add(QueryHelper.getCriterionForIdentifiers(PATCH_FEATURE_ID,
                                        queryObject.getFeatureIdentifiers()))
                                .setProjection(SpatialProjections.extent(PATCH_GEOM))
                                .uniqueResult();
                
                if (geom != null) {
                    int srid = geom.getSRID() > 0 ? geom.getSRID() : getStorageEPSG();
                    geom.setSRID(srid);
                    geom = getGeometryHandler().switchCoordinateAxisFromToDatasourceIfNeeded(geom);
                    SosEnvelope sosEnvelope = new SosEnvelope(geom.getEnvelopeInternal(), srid);
                    MinMaxZ queryMinMaxZ = queryMinMaxZ(queryObject);
                    sosEnvelope.setMinZ(queryMinMaxZ.getMinZ()).setMaxZ(queryMinMaxZ.getMaxZ());
                    return sosEnvelope;
                }
            } catch (final HibernateException he) {
                throw new NoApplicableCodeException().causedBy(he).withMessage(
                        "Exception thrown while requesting global feature envelope");
            }
        }
        return null;
    }
    

    private String getFeatureList(Set<String> featureIdentifiers) {
        StringBuilder builder = new StringBuilder();
        for (String string : featureIdentifiers) {
            builder.append("'").append(string).append("'");
            builder.append(",");
        }
        builder.substring(0, builder.lastIndexOf(",")-1);
        return builder.toString();
    }

    /*
     * (non-Javadoc)
     *
     * @see
     * org.n52.sos.ds.FeatureQueryHandler#insertFeature(org.n52.sos.ogc.om.features
     * .samplingFeatures.SamplingFeature, java.lang.Object)
     *
     * FIXME check semantics of this method in respect to its name and the
     * documentation in the super class
     */
    @Override
    public String insertFeature(final SamplingFeature samplingFeature, final Object connection)
            throws OwsExceptionReport {
        if (StringHelper.isNotEmpty(samplingFeature.getUrl())) {
            if (samplingFeature.isSetIdentifier()) {
                return samplingFeature.getIdentifierCodeWithAuthority().getValue();
            } else {
                return samplingFeature.getUrl();
            }
        } else {
            final Session session = HibernateSessionHolder.getSession(connection);
            String featureIdentifier;
            if (!samplingFeature.isSetIdentifier()) {
                featureIdentifier =
                        SosConstants.GENERATED_IDENTIFIER_PREFIX
                                + JavaHelper.generateID(samplingFeature.getXmlDescription());
                samplingFeature.setIdentifier(new CodeWithAuthority(featureIdentifier));
            }
            return insertFeatureOfInterest(samplingFeature, session).getIdentifier();
        }
    }

    @Deprecated
    @Override
    public int getDefaultEPSG() {
        return getStorageEPSG();
    }

    @Deprecated
    @Override
    public int getDefault3DEPSG() {
        return getStorage3DEPSG();
    }

    @Override
    public int getStorageEPSG() {
        return getGeometryHandler().getStorageEPSG();
    }

    @Override
    public int getStorage3DEPSG() {
        return getGeometryHandler().getStorage3DEPSG();
    }

    @Override
    public int getDefaultResponseEPSG() {
        return getGeometryHandler().getDefaultResponseEPSG();
    }

    @Override
    public int getDefaultResponse3DEPSG() {
        return getGeometryHandler().getDefaultResponse3DEPSG();
    }

    protected GeometryHandler getGeometryHandler() {
        return GeometryHandler.getInstance();
    }

    private boolean isFeatureReferenced(final SamplingFeature samplingFeature) {
        return StringHelper.isNotEmpty(samplingFeature.getUrl());
    }

    /**
     * Creates a map with FOI identifier and SOS feature
     * <p/>
     *
     * @param features
     *            FeatureOfInterest objects
     * @param queryObject
     *            SOS version
     *            <p/>
     * @return Map with FOI identifier and SOS feature
     *         <p/>
     * @throws OwsExceptionReport
     *             * If feature type is not supported
     */
    protected Map<String, AbstractFeature> createSosFeatures(final List<FeatureOfInterest> features,
            final FeatureQueryHandlerQueryObject queryObject, Session session) throws OwsExceptionReport {
        final Map<String, AbstractFeature> sosAbstractFois = new HashMap<String, AbstractFeature>();
        for (final FeatureOfInterest feature : features) {
            final AbstractFeature sosFeature = createSosAbstractFeature(feature, queryObject, session);
            sosAbstractFois.put(feature.getIdentifier(), sosFeature);
        }
        // TODO if sampledFeatures are also in sosAbstractFois, reference them.
        return sosAbstractFois;
    }

    protected FeatureOfInterest getFeatureOfInterest(final String identifier, final Geometry geometry,
            final Session session) throws OwsExceptionReport {
        if (!identifier.startsWith(SosConstants.GENERATED_IDENTIFIER_PREFIX)) {
            return (FeatureOfInterest) session.createCriteria(FeatureOfInterest.class)
                    .add(Restrictions.eq(FeatureOfInterest.IDENTIFIER, identifier)).uniqueResult();
        } else {
            return (FeatureOfInterest) session.createCriteria(FeatureOfInterest.class)
                    .add(SpatialRestrictions.eq(FeatureOfInterest.GEOMETRY,
                            getGeometryHandler().switchCoordinateAxisFromToDatasourceIfNeeded(geometry)))
                    .uniqueResult();
        }
    }

    protected AbstractFeature createSosAbstractFeature(final FeatureOfInterest feature,
            final FeatureQueryHandlerQueryObject queryObject) throws OwsExceptionReport {
        final Session session = HibernateSessionHolder.getSession(queryObject.getConnection());
        return createSosAbstractFeature(feature, queryObject, session);
    }

    /**
     * Creates a SOS feature from the FeatureOfInterest object
     *
     * @param feature
     *            FeatureOfInterest object
     * @param version
     *            SOS version
     * @return SOS feature
     * @throws OwsExceptionReport
     */
    protected AbstractFeature createSosAbstractFeature(final FeatureOfInterest feature,
            final FeatureQueryHandlerQueryObject queryObject, Session session) throws OwsExceptionReport {
        if (feature == null) {
            return null;
        }
        FeatureOfInterestDAO featureOfInterestDAO = new FeatureOfInterestDAO();
        final CodeWithAuthority identifier = featureOfInterestDAO.getIdentifier(feature);
        if (!SosHelper.checkFeatureOfInterestIdentifierForSosV2(feature.getIdentifier(), queryObject.getVersion())) {
            identifier.setValue(null);
        }
        final SamplingFeature sampFeat = new SamplingFeature(identifier);
        addNameAndDescription(queryObject, feature, sampFeat, featureOfInterestDAO);
        sampFeat.setGeometry(getGeomtery(feature, session));
        sampFeat.setFeatureType(feature.getFeatureOfInterestType().getFeatureOfInterestType());
        sampFeat.setUrl(feature.getUrl());
        if (feature.isSetDescriptionXml()) {
            sampFeat.setXmlDescription(feature.getDescriptionXml());
        }
        final Set<FeatureOfInterest> parentFeatures = feature.getParents();
        if (parentFeatures != null && !parentFeatures.isEmpty()) {
            final List<AbstractFeature> sampledFeatures = new ArrayList<AbstractFeature>(parentFeatures.size());
            for (final FeatureOfInterest parentFeature : parentFeatures) {
                sampledFeatures.add(createSosAbstractFeature(parentFeature, queryObject, session));
            }
            sampFeat.setSampledFeatures(sampledFeatures);
        }
        return sampFeat;
    }

    private void addNameAndDescription(FeatureQueryHandlerQueryObject query,
                                       FeatureOfInterest feature,
                                       SamplingFeature samplingFeature,
                                       FeatureOfInterestDAO featureDAO)
            throws OwsExceptionReport {
        I18NDAO<I18NFeatureMetadata> i18nDAO = I18NDAORepository.getInstance().getDAO(I18NFeatureMetadata.class);
        Locale requestedLocale = query.getI18N();
        // set name as human readable identifier if set
        if (feature.isSetName()) {
        	samplingFeature.setHumanReadableIdentifier(feature.getName());
        }
        if (i18nDAO == null) {
            // no i18n support
            samplingFeature.addName(featureDAO.getName(feature));
            samplingFeature.setDescription(featureDAO.getDescription(feature));
        } else {
            I18NFeatureMetadata i18n = i18nDAO.getMetadata(feature.getIdentifier());
            if (requestedLocale != null) {
                // specific locale was requested
                Optional<LocalizedString> name = i18n.getName().getLocalizationOrDefault(requestedLocale);
                if (name.isPresent()) {
                    samplingFeature.addName(name.get().asCodeType());
                }
                Optional<LocalizedString> description = i18n.getDescription().getLocalizationOrDefault(requestedLocale);
                if (description.isPresent()) {
                    samplingFeature.setDescription(description.get().getText());
                }
            } else {
                if (ServiceConfiguration.getInstance().isShowAllLanguageValues()) {
                    for (LocalizedString name : i18n.getName()) {
                        samplingFeature.addName(name.asCodeType());
                    }
                } else {
                    Optional<LocalizedString> name = i18n.getName().getDefaultLocalization();
                    if (name.isPresent()) {
                        samplingFeature.addName(name.get().asCodeType());
                    }
                }
                // choose always the description in the default locale
                Optional<LocalizedString> description = i18n.getDescription().getDefaultLocalization();
                if (description.isPresent()) {
                    samplingFeature.setDescription(description.get().getText());
                }
            }
        }
    }

    protected FeatureOfInterest insertFeatureOfInterest(final SamplingFeature samplingFeature, final Session session)
            throws OwsExceptionReport {
        if (!getGeometryHandler().isSpatialDatasource()) {
            throw new NotYetSupportedException("Insertion of full encoded features for non spatial datasources");
        }
        FeatureOfInterestDAO featureOfInterestDAO = new FeatureOfInterestDAO();
        final String newId = samplingFeature.getIdentifierCodeWithAuthority().getValue();
        FeatureOfInterest feature = getFeatureOfInterest(newId, samplingFeature.getGeometry(), session);
        if (feature == null) {
            feature = new FeatureOfInterest();
            featureOfInterestDAO.addIdentifierNameDescription(samplingFeature, feature, session);
            processGeometryPreSave(samplingFeature, feature, session);
            if (samplingFeature.isSetXmlDescription()) {
                feature.setDescriptionXml(samplingFeature.getXmlDescription());
            }
            if (samplingFeature.isSetFeatureType()) {
                feature.setFeatureOfInterestType(new FeatureOfInterestTypeDAO().getOrInsertFeatureOfInterestType(
                        samplingFeature.getFeatureType(), session));
            }
            if (samplingFeature.isSetSampledFeatures()) {
                Set<FeatureOfInterest> parents =
                        Sets.newHashSetWithExpectedSize(samplingFeature.getSampledFeatures().size());
                for (AbstractFeature sampledFeature : samplingFeature.getSampledFeatures()) {
                    if (!OGCConstants.UNKNOWN.equals(sampledFeature.getIdentifierCodeWithAuthority().getValue())) {
                        if (sampledFeature instanceof SamplingFeature) {
                            parents.add(insertFeatureOfInterest((SamplingFeature) sampledFeature, session));
                        } else {
                            parents.add(insertFeatureOfInterest(new SamplingFeature(sampledFeature.getIdentifierCodeWithAuthority()), session));
                        }
                    }
                }
                feature.setParents(parents);
            }
            session.save(feature);
            session.flush();
            session.refresh(feature);
            featureOfInterestDAO.insertNameAndDescription(feature, samplingFeature, session);
//            return newId;
//        } else {
//            return feature.getIdentifier();
        }
        return feature;
    }

    protected void processGeometryPreSave(final SamplingFeature ssf, final FeatureOfInterest f, Session session)
            throws OwsExceptionReport {
        f.setGeom(getGeometryHandler().switchCoordinateAxisFromToDatasourceIfNeeded(ssf.getGeometry()));
    }

    /**
     * Get the geometry from featureOfInterest object.
     *
     * @param feature
     * @return geometry
     * @throws OwsExceptionReport
     */
    protected Geometry getGeomtery(final FeatureOfInterest feature, Session session) throws OwsExceptionReport {
        if (feature.isSetIdentifier()) {
            Criteria c = session.createCriteria(PatchEntity.class);
            c.add(Restrictions.eq(PATCH_FEATURE_ID, feature.getIdentifier()));
            PatchEntity patchEntity = (PatchEntity)c.uniqueResult();
            return getGeometryHandler().switchCoordinateAxisFromToDatasourceIfNeeded(patchEntity.getPa());
        }
        return null;
    }

    protected Map<String, AbstractFeature> getFeaturesForNonSpatialDatasource(
            FeatureQueryHandlerQueryObject queryObject) throws OwsExceptionReport {
        final Session session = HibernateSessionHolder.getSession(queryObject.getConnection());
        final Map<String, AbstractFeature> featureMap = new HashMap<>(0);
        List<Geometry> envelopes = null;
        boolean hasSpatialFilter = false;
        if (queryObject.isSetSpatialFilters()) {
            hasSpatialFilter = true;
            envelopes = new ArrayList<Geometry>(queryObject.getSpatialFilters().size());
            for (final SpatialFilter filter : queryObject.getSpatialFilters()) {
                envelopes.add(getGeometryHandler().getFilterForNonSpatialDatasource(filter));
            }
        }
        final List<FeatureOfInterest> featuresOfInterest =
                new FeatureOfInterestDAO().getFeatureOfInterestObject(queryObject.getFeatureIdentifiers(), session);
        for (final FeatureOfInterest feature : featuresOfInterest) {
            final SamplingFeature sosAbstractFeature =
                    (SamplingFeature) createSosAbstractFeature(feature, queryObject);
            if (!hasSpatialFilter) {
                featureMap.put(sosAbstractFeature.getIdentifierCodeWithAuthority().getValue(), sosAbstractFeature);
            } else {
                if (getGeometryHandler().featureIsInFilter(sosAbstractFeature.getGeometry(), envelopes)) {
                    featureMap.put(sosAbstractFeature.getIdentifierCodeWithAuthority().getValue(), sosAbstractFeature);
                }
            }
        }
        return featureMap;
    }

    @SuppressWarnings("unchecked")
    protected Map<String, AbstractFeature> getFeaturesForSpatialDatasource(FeatureQueryHandlerQueryObject queryObject)
            throws OwsExceptionReport {
        final Session session = HibernateSessionHolder.getSession(queryObject.getConnection());
        DetachedCriteria geomFilter = null;
        final Criteria c =
                session.createCriteria(FeatureOfInterest.class).setResultTransformer(Criteria.DISTINCT_ROOT_ENTITY);
        boolean filtered = false;
        if (queryObject.isSetFeatureIdentifiers()) {
            c.add(QueryHelper.getCriterionForIdentifiers(FeatureOfInterest.IDENTIFIER, queryObject.getFeatureIdentifiers()));
            filtered = true;
        }
        if (queryObject.isSetSpatialFilters()) {
            geomFilter = DetachedCriteria.forClass(PatchEntity.class);
            final Disjunction disjunction = Restrictions.disjunction();
            for (final SpatialFilter filter : queryObject.getSpatialFilters()) {
                disjunction.add(SpatialRestrictions.filter(PATCH_GEOM, filter.getOperator(),
                        getGeometryHandler().switchCoordinateAxisFromToDatasourceIfNeeded(filter.getGeometry())));
            }
            geomFilter.add(disjunction);
            geomFilter.setProjection(Projections.property(PATCH_FEATURE_ID));
            filtered = true;
        }
        if (geomFilter != null) {
            c.add(Subqueries.propertyIn(FeatureOfInterest.IDENTIFIER, geomFilter));
        }
        if (filtered) {
            LOGGER.debug(
                    "QUERY getFeaturesForSpatialDatasource(): {}", HibernateHelper.getSqlString(c));
            return createSosFeatures(c.list(), queryObject, session);
        } else {
            return Collections.emptyMap();
        }
    }

    private MinMaxZ queryMinMaxZ(FeatureQueryHandlerQueryObject queryObject) throws OwsExceptionReport {
        final Session session = HibernateSessionHolder.getSession(queryObject.getConnection());
        Criteria c = session
        .createCriteria(PatchEntity.class);
        c.add(QueryHelper.getCriterionForIdentifiers(PATCH_FEATURE_ID,
                queryObject.getFeatureIdentifiers()))
        .setProjection(Projections.projectionList()
                .add(Projections.min(PATCH_MIN_Z))
                .add(Projections.max(PATCH_MAX_Z)));
        LOGGER.debug("QUERY queryMinMaxZ(): {}", HibernateHelper.getSqlString(c));
        c.setResultTransformer(transformer);
        return (MinMaxZ)c.uniqueResult();
    }

    @Override
    public String getDatasourceDaoIdentifier() {
        return HibernateDatasourceConstants.ORM_DATASOURCE_DAO_IDENTIFIER;
    }
    
    public class MinMaxZ {

        private Double minZ;
        private Double maxZ;
       /**
         * @return the minZ
         */
        public Double getMinZ() {
            return minZ;
        }
        /**
         * @param minZ the minZ to set
         */
        public void setMinZ(Double minZ) {
            this.minZ = minZ;
        }
        /**
         * @return the maxZ
         */
        public Double getMaxZ() {
            return maxZ;
        }
        /**
         * @param maxZ the maxZ to set
         */
        public void setMaxZ(Double maxZ) {
            this.maxZ = maxZ;
        }
    }
    
    private class MinMaxZTransformer implements ResultTransformer {
        private static final long serialVersionUID = -373512929481519459L;

        @Override
        public MinMaxZ transformTuple(Object[] tuple, String[] aliases) {
            MinMaxZ minMaxZ = new MinMaxZ();
            if (tuple != null) {
                minMaxZ.setMinZ(Double.parseDouble(tuple[0].toString()));
                minMaxZ.setMaxZ(Double.parseDouble(tuple[1].toString()));
                
            }
            return minMaxZ;
        }

        @Override
        @SuppressWarnings({ "rawtypes" })
        public List transformList(List collection) {
            return collection;
        }
    }
}
