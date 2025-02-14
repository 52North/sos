/*
 * Copyright (C) 2012-2023 52°North Spatial Information Research GmbH
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
package org.n52.sos.ds;

import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.inject.Inject;
import javax.xml.namespace.QName;

import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.hibernate.HibernateException;
import org.hibernate.Session;
import org.locationtech.jts.geom.Geometry;
import org.n52.io.request.IoParameters;
import org.n52.sensorweb.server.db.old.dao.DbQuery;
import org.n52.sensorweb.server.db.old.dao.DbQueryFactory;
import org.n52.series.db.beans.*;
import org.n52.series.db.beans.dataset.DatasetType;
import org.n52.series.db.old.HibernateSessionStore;
import org.n52.series.db.old.dao.DatasetDao;
import org.n52.series.db.old.dao.OfferingDao;
import org.n52.series.db.old.dao.PhenomenonDao;
import org.n52.shetland.ogc.gml.ReferenceType;
import org.n52.shetland.ogc.gml.time.TimePeriod;
import org.n52.shetland.ogc.ows.exception.CodedException;
import org.n52.shetland.ogc.ows.exception.NoApplicableCodeException;
import org.n52.shetland.ogc.ows.exception.OwsExceptionReport;
import org.n52.shetland.ogc.ows.extension.CapabilitiesExtension;
import org.n52.shetland.ogc.ows.service.GetCapabilitiesRequest;
import org.n52.shetland.ogc.sos.Sos1Constants;
import org.n52.shetland.ogc.sos.Sos2Constants;
import org.n52.shetland.ogc.sos.SosConstants;
import org.n52.shetland.ogc.sos.SosObservationOffering;
import org.n52.shetland.ogc.sos.SosOffering;
import org.n52.shetland.ogc.sos.extension.SosObservationOfferingExtension;
import org.n52.shetland.ogc.sos.ro.RelatedOfferingConstants;
import org.n52.shetland.ogc.sos.ro.RelatedOfferings;
import org.n52.shetland.util.CollectionHelper;
import org.n52.shetland.util.OMHelper;
import org.n52.shetland.util.ReferencedEnvelope;
import org.n52.sos.config.CapabilitiesExtensionService;
import org.n52.sos.util.I18NHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import com.google.common.collect.Sets;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Implementation of the interface AbstractGetCapabilitiesHandler
 *
 * @since 4.0.0
 */
@SuppressFBWarnings({"EI_EXPOSE_REP2"})
public class GetCapabilitiesHandler extends AbstractSosGetCapabilitiesHandler implements ApiQueryHelper, I18NHelper {

    private static final Logger LOGGER = LoggerFactory.getLogger(GetCapabilitiesHandler.class);

    private static final String ERROR_QUERYING_CAPABILITIES = "Error while querying data for GetCapabilities document!";

    @Inject
    private HibernateSessionStore sessionStore;

    @Inject
    private CapabilitiesExtensionService capabilitiesExtensionService;

    private DbQueryFactory dbQueryFactory;

    @Inject
    public void setDbQueryFactory(DbQueryFactory dbQueryFactory) {
        this.dbQueryFactory = dbQueryFactory;
    }

    /**
     * Get the contents for SOS 1.0.0 capabilities
     *
     * @return sectionSpecificContentObject metadata holder to get contents
     * @throws OwsExceptionReport If an error occurs
     */
    @Override
    @Transactional()
    protected List<SosObservationOffering> getContentsForSosV1(
            SectionSpecificContentObject sectionSpecificContentObject) throws OwsExceptionReport {
        Session session = null;
        try {
            session = sessionStore.getSession();
            Collection<OfferingEntity> offerings = getOfferings(session);
            List<SosObservationOffering> sosOfferings = new ArrayList<>(offerings.size());
            for (OfferingEntity offering : offerings) {
                Collection<ProcedureEntity> procedures =
                        GetCapabilitiesHandler.this.getProceduresForOfferingEntity(offering, session);
                ReferencedEnvelope envelopeForOffering = getCache().getEnvelopeForOffering(offering.getIdentifier());
                Set<String> featuresForoffering = getFOI4offering(offering.getIdentifier());
                Collection<String> responseFormats = getResponseFormatRepository()
                        .getSupportedResponseFormats(SosConstants.SOS, Sos1Constants.SERVICEVERSION);
                if (checkOfferingValues(envelopeForOffering, featuresForoffering, responseFormats)) {
                    SosObservationOffering sosObservationOffering = new SosObservationOffering();

                    // insert observationTypes
                    sosObservationOffering.setObservationTypes(getObservationTypes(offering.getIdentifier()));

                    // only if fois are contained for the offering set the
                    // values of
                    // the envelope
                    if (offering.isSetGeometry()) {
                        sosObservationOffering.setObservedArea(processObservedArea(offering.getGeometry()));
                    } else if (getCache().hasEnvelopeForOffering(offering.getIdentifier())) {
                        sosObservationOffering
                                .setObservedArea(getCache().getEnvelopeForOffering(offering.getIdentifier()));
                    }

                    // TODO: add intended application
                    // xb_oo.addIntendedApplication("");
                    // add offering name
                    addSosOfferingToObservationOffering(offering, sosObservationOffering,
                            sectionSpecificContentObject.getGetCapabilitiesRequest());

                    // set up phenomena
                    sosObservationOffering.setObservableProperties(
                            getCache().getObservablePropertiesForOffering(offering.getIdentifier()));
                    Set<String> compositePhenomenonsForOffering =
                            getCache().getCompositePhenomenonsForOffering(offering.getIdentifier());
                    sosObservationOffering.setCompositePhenomena(compositePhenomenonsForOffering);

                    Map<String, Collection<String>> phens4CompPhens =
                            Optional.ofNullable(compositePhenomenonsForOffering).map(Set::stream)
                                    .orElseGet(Stream::empty).collect(toMap(Function.identity(),
                                            getCache()::getObservablePropertiesForCompositePhenomenon));

                    sosObservationOffering.setPhens4CompPhens(phens4CompPhens);

                    // set up time
                    setUpTimeForOffering(offering, sosObservationOffering);

                    // add feature of interests
                    if (getProfileHandler().getActiveProfile().isListFeatureOfInterestsInOfferings()) {
                        sosObservationOffering.setFeatureOfInterest(getFOI4offering(offering.getIdentifier()));
                    }

                    // set procedures
                    sosObservationOffering.setProcedures(
                            procedures.stream().map(p -> p.getIdentifier()).collect(Collectors.toSet()));

                    // insert result models
                    Collection<QName> resultModels = OMHelper.getQNamesForResultModel(
                            getCache().getObservationTypesForOffering(offering.getIdentifier()));
                    sosObservationOffering.setResultModels(resultModels);

                    // set response format
                    sosObservationOffering.setResponseFormats(responseFormats);

                    // set response Mode
                    sosObservationOffering.setResponseModes(SosConstants.RESPONSE_MODES);

                    sosOfferings.add(sosObservationOffering);
                }
            }

            return sosOfferings;
        } catch (HibernateException e) {
            throw new NoApplicableCodeException().causedBy(e)
                    .withMessage(ERROR_QUERYING_CAPABILITIES);
        } finally {
            sessionStore.returnSession(session);
        }
    }

    private ReferencedEnvelope processObservedArea(Geometry geometry) throws OwsExceptionReport {
        geometry.setSRID(
                geometry.getSRID() <= 0
                        ? Double.isNaN(geometry.getCoordinate().getZ()) ? getGeometryHandler().getStorageEPSG()
                        : getGeometryHandler().getStorage3DEPSG()
                        : geometry.getSRID());
        return new ReferencedEnvelope(
                getGeometryHandler().switchCoordinateAxisFromToDatasourceIfNeeded(geometry).getEnvelopeInternal(),
                geometry.getSRID());
    }

    /**
     * Get the contents for SOS 2.0 capabilities
     *
     * @return sectionSpecificContentObject metadata holder to get contents
     * @throws OwsExceptionReport * If an error occurs
     */
    @Override
    @Transactional()
    protected List<SosObservationOffering> getContentsForSosV2(
            SectionSpecificContentObject sectionSpecificContentObject) throws OwsExceptionReport {
        Session session = null;
        try {
            session = sessionStore.getSession();

            Map<Long, Pair<OfferingEntity, List<DatasetEntity>>> offerings = getOfferingsWithAssoc(session);
            List<SosObservationOffering> sosOfferings = new ArrayList<>(offerings.size());
            Map<String, List<SosObservationOfferingExtension>> extensions =
                    this.capabilitiesExtensionService.getActiveOfferingExtensions();

            if (CollectionHelper.isEmpty(offerings)) {
                // Set empty offering to add empty Contents section to
                // Capabilities
                sosOfferings.add(new SosObservationOffering());
            } else {
                if (checkListOnlyParentOfferings()) {
                    sosOfferings.addAll(
                            createAndGetParentOfferings(
                                    offerings.values().stream().map(Pair::getKey).collect(Collectors.toList()),
                                    sectionSpecificContentObject,
                                    extensions,
                                    session));
                } else {
                    for (Map.Entry<Long, Pair<OfferingEntity, List<DatasetEntity>>> entry : offerings.entrySet()) {
                        Long offeringId = entry.getKey();
                        OfferingEntity offering = entry.getValue().getKey();
                        List<DatasetEntity> ds = entry.getValue().getValue();

                        if (!ds.isEmpty()) {
                            Collection<String> observationTypes = getObservationTypes(offering);
                            if (observationTypes != null && !observationTypes.isEmpty()) {
                                // FIXME why a loop? We are in SOS 2.0 context
                                // -> offering 1 <-> 1 procedure!
                                for (DatasetEntity dataset : ds) {

                                    ProcedureEntity procedure = dataset.getProcedure();

                                    SosObservationOffering sosObservationOffering = new SosObservationOffering();
                                    // insert observationTypes
                                    sosObservationOffering.setObservationTypes(observationTypes);

                                    if (offering.isSetGeometry()) {
                                        sosObservationOffering
                                                .setObservedArea(processObservedArea(offering.getGeometry()));
                                    } else if (getCache().hasEnvelopeForOffering(offering.getIdentifier())) {
                                        sosObservationOffering.setObservedArea(
                                                getCache().getEnvelopeForOffering(offering.getIdentifier()));
                                    }

                                    sosObservationOffering
                                            .setProcedures(Collections.singletonList(procedure.getIdentifier()));
                                    GetCapabilitiesRequest request =
                                            sectionSpecificContentObject.getGetCapabilitiesRequest();

                                    // TODO: add intended application
                                    // add offering to observation offering
                                    addSosOfferingToObservationOffering(offering, sosObservationOffering, request);
                                    // add offering extension
                                    if (getOfferingExtensionRepository().hasOfferingExtensionProviderFor(request)) {
                                        getOfferingExtensionRepository().getOfferingExtensionProvider(request).stream()
                                                .filter(Objects::nonNull)
                                                .filter(provider -> provider
                                                        .hasExtendedOfferingFor(offering.getIdentifier()))
                                                .map(provider -> provider
                                                        .getOfferingExtensions(offering.getIdentifier()))
                                                .forEach(sosObservationOffering::addExtensions);
                                    }

                                    if (extensions.containsKey(sosObservationOffering.getOffering().getIdentifier())) {
                                        extensions.get(sosObservationOffering.getIdentifier()).stream()
                                                .map(CapabilitiesExtension::new)
                                                .forEach(sosObservationOffering::addExtension);
                                    }

                                    setUpPhenomenaForOffering(offering, sosObservationOffering, Collections.singleton(dataset.getPhenomenon()));

                                    setUpTimeForOffering(offering, sosObservationOffering);
                                    setUpRelatedFeaturesForOffering(offering, sosObservationOffering);
                                    setUpFeatureOfInterestTypesForOffering(offering, sosObservationOffering);
                                    setUpProcedureDescriptionFormatForOffering(sosObservationOffering,
                                            Sos2Constants.SERVICEVERSION);
                                    setUpResponseFormatForOffering(sosObservationOffering,
                                            Sos2Constants.SERVICEVERSION);

                                    sosOfferings.add(sosObservationOffering);
                                }
                            } else {
                                LOGGER.debug("no observationTypes for offering {}", offeringId);
                            }
                        } else {
                            LOGGER.error(
                                    "No procedures are contained in the database for the offering {}! "
                                            + "Please contact the admin of this SOS.",
                                    offering.getIdentifier());
                        }
                    }
                }
            }

            return sosOfferings;
        } catch (HibernateException e) {
            throw new NoApplicableCodeException().causedBy(e)
                    .withMessage(ERROR_QUERYING_CAPABILITIES);
        } finally {
            sessionStore.returnSession(session);
        }
    }

    private Collection<OfferingEntity> getOfferings(Session session) {
        OfferingDao offeringDao = new OfferingDao(session);
        Collection<OfferingEntity> offerings = offeringDao.getAllInstances(createDbQuery(IoParameters.createDefaults()));

        Collection<OfferingEntity> allOfferings = offeringDao.get(createDbQuery(IoParameters.createDefaults()));
        Collection<DatasetEntity> datasets = new DatasetDao(session).get(createDbQuery(IoParameters.createDefaults()));
        Set<OfferingEntity> notVisibleOfferings = datasets.stream().filter(
                        d -> d.isDeleted() || !d.isPublished() && !d.getDatasetType().equals(DatasetType.not_initialized))
                .map(d -> d.getOffering()).collect(Collectors.toSet());
        offerings.addAll(
                allOfferings.stream().filter(o -> !notVisibleOfferings.contains(o)).collect(Collectors.toSet()));
        return offerings;
    }

    private Map<Long, Pair<OfferingEntity, List<DatasetEntity>>> getOfferingsWithAssoc(Session session) {
        OfferingDao offeringDao = new OfferingDao(session);

        // These return different offerings for some reason.
        Collection<OfferingEntity> offerings = offeringDao.getAllInstances(createDbQuery(IoParameters.createDefaults()));
        Collection<OfferingEntity> allOfferings = offeringDao.get(createDbQuery(IoParameters.createDefaults()));

        Collection<DatasetEntity> datasets = new DatasetDao(session).get(createDbQuery(IoParameters.createDefaults()));
        Set<OfferingEntity> notVisibleOfferings = datasets.stream().filter(
                        d -> d.isDeleted() || !d.isPublished() && !d.getDatasetType().equals(DatasetType.not_initialized))
                .map(d -> d.getOffering()).collect(Collectors.toSet());
        offerings.addAll(allOfferings.stream().filter(o -> !notVisibleOfferings.contains(o)).collect(Collectors.toSet()));

        HashMap<Long, Pair<OfferingEntity, List<DatasetEntity>>> mapped = new HashMap<>();
        offerings.forEach(o -> {
            mapped.put(o.getId(), new MutablePair<>(o, new ArrayList<>(1)));
        });
        for (DatasetEntity ds : datasets) {
            mapped.computeIfPresent(ds.getOffering().getId(), (k, v) -> {
                v.getValue().add(ds);
                return v;
            });
        }

        // Offen: Wieviele Offerings sind in jedem Datastream?
        // Wieviele Procedures sind in jedem Offering?
        return mapped;
    }


    private Collection<? extends SosObservationOffering> createAndGetParentOfferings(
            Collection<OfferingEntity> offerings, SectionSpecificContentObject sectionSpecificContentObject,
            Map<String, List<SosObservationOfferingExtension>> extensions, Session session)
            throws OwsExceptionReport {
        Map<OfferingEntity, Set<OfferingEntity>> parentChilds = getParentOfferings(offerings);

        List<SosObservationOffering> sosOfferings = new ArrayList<>(parentChilds.size());
        for (Entry<OfferingEntity, Set<OfferingEntity>> entry : parentChilds.entrySet()) {
            Collection<String> observationTypes = getObservationTypes(entry);
            if (CollectionHelper.isNotEmpty(observationTypes)) {
                Collection<ProcedureEntity> procedures = getProceduresForOfferingEntity(entry, session);
                if (CollectionHelper.isNotEmpty(procedures)) {
                    Set<OfferingEntity> allOfferings = new HashSet<>();
                    allOfferings.addAll(entry.getValue());
                    allOfferings.add(entry.getKey());
                    SosObservationOffering sosObservationOffering = new SosObservationOffering();
                    sosObservationOffering.setObservationTypes(observationTypes);
                    sosObservationOffering.setObservedArea(getObservedArea(entry));

                    sosObservationOffering.setProcedures(getParentProcedures(procedures).keySet().stream()
                            .map(p -> p.getIdentifier()).collect(Collectors.toSet()));
                    //
                    // // TODO: add intended application
                    //
                    // // add offering to observation offering
                    addSosOfferingToObservationOffering(entry.getKey(), sosObservationOffering,
                            sectionSpecificContentObject.getGetCapabilitiesRequest());
                    // add offering extension
                    if (getOfferingExtensionRepository().hasOfferingExtensionProviderFor(
                            sectionSpecificContentObject.getGetCapabilitiesRequest())) {
                        getOfferingExtensionRepository()
                                .getOfferingExtensionProvider(sectionSpecificContentObject.getGetCapabilitiesRequest())
                                .stream().filter(Objects::nonNull)
                                .filter(provider -> provider.hasExtendedOfferingFor(entry.getKey().getIdentifier()))
                                .map(provider -> provider.getOfferingExtensions(entry.getKey().getIdentifier()))
                                .forEach(sosObservationOffering::addExtensions);
                    }
                    if (extensions.containsKey(sosObservationOffering.getOffering().getIdentifier())) {
                        extensions.get(sosObservationOffering.getOffering().getIdentifier())
                                .forEach(sosObservationOffering::addExtension);
                    }
                    // add sub-level offerings
                    if (!entry.getValue().isEmpty()) {
                        RelatedOfferings relatedOfferings = new RelatedOfferings();
                        String gdaURL = getGetDataAvailabilityUrl();
                        gdaURL = addParameter(gdaURL, "responseFormat", "http://www.opengis.net/sosgda/2.0");
                        for (OfferingEntity offering : entry.getValue()) {
                            relatedOfferings.addValue(new ReferenceType(RelatedOfferingConstants.ROLE),
                                    new ReferenceType(addParameter(gdaURL, "offering", offering.getIdentifier()),
                                            offering.getIdentifier()));
                        }
                        sosObservationOffering.addExtension(relatedOfferings);
                    }

                    setUpPhenomenaForOffering(allOfferings, procedures, sosObservationOffering,
                            session);
                    setUpTimeForOffering(allOfferings.stream().map(OfferingEntity::getIdentifier),
                            sosObservationOffering);
                    setUpRelatedFeaturesForOffering(allOfferings, sosObservationOffering);
                    setUpFeatureOfInterestTypesForOffering(allOfferings, sosObservationOffering);
                    setUpProcedureDescriptionFormatForOffering(sosObservationOffering, Sos2Constants.SERVICEVERSION);
                    setUpResponseFormatForOffering(sosObservationOffering, Sos2Constants.SERVICEVERSION);

                    sosOfferings.add(sosObservationOffering);
                }
            }
        }
        return sosOfferings;
    }

    private Map<OfferingEntity, Set<OfferingEntity>> getParentOfferings(Collection<OfferingEntity> entities) {
        return entities.stream().distinct().filter(o -> !o.hasParents())
                .collect(toMap(Function.identity(), this::getAllChildrenExclude));
    }

    private Map<ProcedureEntity, Set<ProcedureEntity>> getParentProcedures(Collection<ProcedureEntity> entities) {
        return entities.stream().distinct().filter(o -> !o.hasParents())
                .collect(toMap(Function.identity(), this::getAllChildrenExclude));
    }

    private Set<OfferingEntity> getAllChildrenExclude(OfferingEntity entity) {
        return entity.getChildren().stream().map(this::getAllChildren).flatMap(Set::stream).collect(toSet());
    }

    private Set<ProcedureEntity> getAllChildrenExclude(ProcedureEntity entity) {
        return entity.getChildren().stream().map(this::getAllChildren).flatMap(Set::stream).collect(toSet());
    }

    private Set<OfferingEntity> getAllChildren(OfferingEntity entity) {
        return Stream.concat(Stream.of(entity),
                entity.getChildren().stream().map(this::getAllChildren).flatMap(Set::stream)).collect(toSet());
    }

    private Set<ProcedureEntity> getAllChildren(ProcedureEntity entity) {
        return Stream.concat(Stream.of(entity),
                entity.getChildren().stream().map(this::getAllChildren).flatMap(Set::stream)).collect(toSet());
    }

    private void addSosOfferingToObservationOffering(OfferingEntity offering,
                                                     SosObservationOffering sosObservationOffering, GetCapabilitiesRequest request) throws CodedException {
        SosOffering sosOffering = offering.isSetName() ? new SosOffering(offering.getIdentifier(), offering.getName())
                : new SosOffering(offering.getIdentifier(), false);
        sosObservationOffering.setOffering(sosOffering);
        // add offering name
        addOfferingNames(getCache(), sosOffering, getRequestedLocale(request), Locale.ROOT, isShowAllLanguages());
        // add offering description
        addOfferingDescription(sosOffering, getRequestedLocale(request), Locale.ROOT, getCache());
    }

    private Collection<String> getObservationTypes(OfferingEntity offering) {
        if (offering.hasObservationTypes()) {
            return toStringSet(offering.getObservationTypes());
        } else {
            return getCache().getAllObservationTypesForOffering(offering.getIdentifier());
        }
    }

    private Collection<String> getObservationTypes(Entry<OfferingEntity, Set<OfferingEntity>> entry) {
        Set<String> observationTypes = new HashSet<>();
        entry.getValue().stream().map(v -> getObservationTypes(v)).forEach(observationTypes::addAll);
        observationTypes.addAll(getObservationTypes(entry.getKey().getIdentifier()));
        return observationTypes;
    }


    protected void setUpPhenomenaForOffering(Collection<OfferingEntity> allOfferings,
                                             Collection<ProcedureEntity> procedures, SosObservationOffering sosObservationOffering, Session session) {
        for (ProcedureEntity procedure : procedures) {
            setUpPhenomenaForOffering(allOfferings, procedure, sosObservationOffering, session);
        }
    }

    protected void setUpPhenomenaForOffering(OfferingEntity offering, Collection<ProcedureEntity> procedures,
                                             SosObservationOffering sosObservationOffering, Session session) {
        setUpPhenomenaForOffering(Sets.newHashSet(offering), procedures, sosObservationOffering, session);
    }

    protected void setUpPhenomenaForOffering(Collection<OfferingEntity> allOfferings, ProcedureEntity procedure,
                                             SosObservationOffering sosObservationOffering, Session session) {
        for (OfferingEntity offering : allOfferings) {
            setUpPhenomenaForOffering(offering, procedure, sosObservationOffering, session);
        }
    }

    protected void setUpPhenomenaForOffering(OfferingEntity offering, ProcedureEntity procedure,
                                             SosObservationOffering sosOffering, Session session) {
        Map<String, String> map = new HashMap<>();
        map.put(IoParameters.OFFERINGS, Long.toString(offering.getId()));
        map.put(IoParameters.PROCEDURES, Long.toString(procedure.getId()));

        Collection<PhenomenonEntity> observableProperties =
                new PhenomenonDao(session).get(createDbQuery(IoParameters.createFromSingleValueMap(map)));
        setUpPhenomenaForOffering(offering, sosOffering, observableProperties);

    }

    protected void setUpPhenomenaForOffering(OfferingEntity offering,
                                             SosObservationOffering sosOffering,
                                             Collection<PhenomenonEntity> observableProperties) {
        Set<String> validObsProps = getCache().getObservablePropertiesForOffering(offering.getIdentifier());

        Collection<String> phenomenons = new LinkedList<>();
        Map<String, Collection<String>> phens4CompPhens = new HashMap<>(observableProperties.size());
        observableProperties.forEach(observableProperty -> {
            if (validObsProps.contains(observableProperty.getIdentifier())) {
                if (!observableProperty.hasChildren() && !observableProperty.hasParents()) {
                    phenomenons.add(observableProperty.getIdentifier());
                } else if (observableProperty.hasChildren() && !observableProperty.hasParents()) {
                    phens4CompPhens.put(observableProperty.getIdentifier(), observableProperty.getChildren().stream()
                            .map(PhenomenonEntity::getIdentifier).collect(toCollection(TreeSet::new)));
                }
            }
        });
        sosOffering.addObservatbleProperties(phenomenons);
        sosOffering.setPhens4CompPhens(phens4CompPhens);
    }

    protected void setUpTimeForOffering(OfferingEntity offering, SosObservationOffering sosOffering) {
        sosOffering.setPhenomenonTime(new TimePeriod(offering.getSamplingTimeStart(), offering.getSamplingTimeEnd()));
        sosOffering.setResultTime(new TimePeriod(offering.getResultTimeStart(), offering.getResultTimeEnd()));
    }

    protected void setUpFeatureOfInterestTypesForOffering(Collection<OfferingEntity> offerings,
                                                          SosObservationOffering sosOffering) {
        sosOffering.setFeatureOfInterestTypes(offerings.stream().map(o -> getFeatureOfInterstTypesForOffering(o))
                .flatMap(Set::stream).collect(toSet()));
    }

    protected void setUpFeatureOfInterestTypesForOffering(OfferingEntity offering,
                                                          SosObservationOffering sosOffering) {
        sosOffering.setFeatureOfInterestTypes(getFeatureOfInterstTypesForOffering(offering));
    }

    private Set<String> getFeatureOfInterstTypesForOffering(OfferingEntity offering) {
        if (offering.hasFeatureTypes()) {
            return toStringSet(offering.getFeatureTypes());
        } else {
            return getCache().getAllowedFeatureOfInterestTypesForOffering(offering.getIdentifier());
        }
    }

    private void setUpRelatedFeaturesForOffering(OfferingEntity offering,
                                                 SosObservationOffering sosObservationOffering) throws OwsExceptionReport {
        setUpRelatedFeaturesForOffering(Collections.singleton(offering), sosObservationOffering);
    }

    private void setUpRelatedFeaturesForOffering(Collection<OfferingEntity> offerings,
                                                 SosObservationOffering sosObservationOffering) throws OwsExceptionReport {
        setUpRelatedFeaturesForOffering(offerings.stream().map(OfferingEntity::getIdentifier), sosObservationOffering);
    }

    private ReferencedEnvelope getObservedArea(Entry<OfferingEntity, Set<OfferingEntity>> entry)
            throws OwsExceptionReport {
        ReferencedEnvelope envelope = new ReferencedEnvelope();
        if (!entry.getValue().isEmpty()) {
            for (OfferingEntity offering : entry.getValue()) {
                envelope.expandToInclude(getObservedArea(offering.getIdentifier()));
            }
        } else {
            envelope.expandToInclude(getObservedArea(entry.getKey().getIdentifier()));
        }
        return envelope;
    }

    private Collection<ProcedureEntity> getProceduresForOfferingEntity(
            Entry<OfferingEntity, Set<OfferingEntity>> entry, Session session)
            throws OwsExceptionReport {
        Collection<ProcedureEntity> procedures = new HashSet<>();
        for (OfferingEntity offering : entry.getValue()) {
            procedures.addAll(getProceduresForOfferingEntity(offering, session));
        }
        procedures.addAll(getProceduresForOfferingEntity(entry.getKey(), session));
        return procedures;
    }

    private Collection<ProcedureEntity> getProceduresForOfferingEntity(OfferingEntity offering, Session session)
            throws OwsExceptionReport {
        Map<String, String> map = new HashMap<>(1);
        map.put(IoParameters.OFFERINGS, Long.toString(offering.getId()));
        map.put(IoParameters.SELECT, "id");
        DbQuery dbQuery = createDbQuery(IoParameters.createFromSingleValueMap(map));
        return new DatasetDao<>(session).get(dbQuery)
                .stream()
                .map(DatasetEntity::getProcedure)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    @Override
    public DbQuery createDbQuery(IoParameters parameters) {
        return dbQueryFactory.createFrom(parameters);
    }
}
