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
package org.n52.sos.ds.cache.base;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

import org.hibernate.HibernateException;
import org.n52.io.request.IoParameters;
import org.n52.sensorweb.server.db.old.dao.DbQuery;
import org.n52.series.db.beans.DatasetEntity;
import org.n52.series.db.beans.FeatureEntity;
import org.n52.series.db.beans.dataset.DatasetType;
import org.n52.series.db.old.dao.DatasetDao;
import org.n52.series.db.old.dao.FeatureDao;
import org.n52.shetland.ogc.ows.exception.NoApplicableCodeException;
import org.n52.sos.ds.cache.AbstractThreadableDatasourceCacheUpdate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Sets;

/**
 *
 * @author <a href="mailto:c.autermann@52north.org">Christian Autermann</a>
 *
 * @since 4.0.0
 */
public class FeatureOfInterestCacheUpdate extends AbstractThreadableDatasourceCacheUpdate {
    private static final Logger LOGGER = LoggerFactory.getLogger(FeatureOfInterestCacheUpdate.class);

    @Override
    public void execute() {
        LOGGER.debug("Executing FeatureOfInterestCacheUpdate");
        startStopwatch();
        try {
            Collection<FeatureEntity> features =
                    new FeatureDao(getSession()).get(createDbQuery(IoParameters.createDefaults()));
            for (FeatureEntity featureEntity : features) {
                String identifier = featureEntity.getIdentifier();
                getCache().addFeatureOfInterest(identifier);
                Collection<DatasetEntity> datasets =
                        new DatasetDao<>(getSession()).get(createDatasetDbQuery(featureEntity));
                if (datasets != null && !datasets.isEmpty()) {
                    if (datasets.stream().anyMatch(
                        d -> d.isPublished() || d.getDatasetType().equals(DatasetType.not_initialized))) {
                        getCache().addPublishedFeatureOfInterest(identifier);
                    }
                    getCache().setProceduresForFeatureOfInterest(identifier, getProcedures(datasets));
                } else {
                    getCache().addPublishedFeatureOfInterest(identifier);
                }
                if (featureEntity.isSetName()) {
                    getCache().addFeatureOfInterestIdentifierHumanReadableName(identifier, featureEntity.getName());
                }
                if (featureEntity.hasParents()) {
                    getCache().addParentFeatures(identifier, getParents(featureEntity));
                }
            }
        } catch (HibernateException he) {
            getErrors().add(new NoApplicableCodeException().causedBy(he)
                    .withMessage("Error while updating featureOfInterest cache!"));
        }
        LOGGER.debug("Finished executing FeatureOfInterestCacheUpdate ({})", getStopwatchResult());
    }

    private Collection<String> getProcedures(Collection<DatasetEntity> datasets) {
        return datasets.stream().filter(d -> d.getProcedure() != null).map(d -> d.getProcedure().getIdentifier())
                .collect(Collectors.toSet());
    }

    private Collection<String> getParents(FeatureEntity featureEntity) {
        Set<String> parentFeatures = Sets.newTreeSet();
        if (featureEntity.hasParents()) {
            for (FeatureEntity parentEntity : featureEntity.getParents()) {
                parentFeatures.add(parentEntity.getIdentifier());
                parentFeatures.addAll(getParents(parentEntity));
            }
        }
        return parentFeatures;
    }

    /**
     * Get identifiers from featureOfInterest entities
     *
     * @param features
     *            FeatureOfInterest entities
     * @return Identifiers from featureOfInterest entities
     */
    protected Set<String> getFeatureIdentifiers(Collection<FeatureEntity> features) {
        return features.stream().map(FeatureEntity::getIdentifier).collect(Collectors.toSet());
    }

    private DbQuery createDatasetDbQuery(FeatureEntity feature) {
        IoParameters parameters = IoParameters.createDefaults()
                .extendWith(IoParameters.FEATURES, Long.toString(feature.getId()))
                .extendWith(IoParameters.SELECT, "id");
        return createDbQuery(parameters);
    }

}
