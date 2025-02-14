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

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import javax.inject.Inject;

import org.hibernate.Session;
import org.joda.time.Period;
import org.joda.time.format.PeriodFormat;
import org.n52.faroe.ConfigurationError;
import org.n52.faroe.Validation;
import org.n52.faroe.annotation.Configurable;
import org.n52.faroe.annotation.Setting;
import org.n52.iceland.cache.WritableContentCache;
import org.n52.iceland.cache.ctrl.StaticCapabilitiesProvider;
import org.n52.iceland.i18n.I18NDAORepository;
import org.n52.iceland.i18n.I18NSettings;
import org.n52.iceland.ogc.ows.OwsServiceMetadataRepository;
import org.n52.janmayen.i18n.LocaleHelper;
import org.n52.sensorweb.server.db.old.dao.DbQueryFactory;
import org.n52.series.db.old.HibernateSessionStore;
import org.n52.shetland.ogc.ows.exception.CompositeOwsException;
import org.n52.shetland.ogc.ows.exception.NoApplicableCodeException;
import org.n52.shetland.ogc.ows.exception.OwsExceptionReport;
import org.n52.shetland.util.CollectionHelper;
import org.n52.sos.cache.SosWritableContentCache;
import org.n52.sos.ds.cache.CacheFeederSettingDefinitionProvider;
import org.n52.sos.ds.cache.InitialCacheUpdate;
import org.n52.sos.ds.cache.base.OfferingCacheUpdate;
import org.n52.sos.util.GeometryHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Implementation of the interface CacheFeederDAO
 *
 * @since 4.0.0
 */
@Configurable
@SuppressFBWarnings({"EI_EXPOSE_REP2"})
public class SosCacheFeederHandler implements CacheFeederHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(SosCacheFeederHandler.class);

    private static final String ERROR_UPDATE_CACHE = "Error while updating ContentCache!";

    private static final String ERROR_RETURNING_CONNECTION = "Error while returning connection after cache update!";


    /**
     * Defines the number of threads available in the thread pool of the cache
     * update executor service.
     */
    private int cacheThreadCount = 5;
    private Locale defaultLocale;
    private I18NDAORepository i18NDAORepository;
    private OwsServiceMetadataRepository serviceMetadataRepository;
    private HibernateSessionStore sessionStore;
    private GeometryHandler geometryHandler;
    private DbQueryFactory dbQueryFactory;
    private Optional<StaticCapabilitiesProvider> staticCapabilitiesProvider = Optional.empty();

    @Inject
    public void setConnectionProvider(HibernateSessionStore sessionStore) {
        this.sessionStore = sessionStore;
    }

    @Setting(I18NSettings.I18N_DEFAULT_LANGUAGE)
    public void setDefaultLocale(String defaultLocale) {
        this.defaultLocale = LocaleHelper.decode(defaultLocale);
    }

    @Inject
    public void setServiceMetadataRepository(OwsServiceMetadataRepository repo) {
        this.serviceMetadataRepository = repo;
    }

    @Inject
    public void setI18NDAORepository(I18NDAORepository i18NDAORepository) {
        this.i18NDAORepository = i18NDAORepository;
    }

    @Inject
    public void setGeometryHandler(GeometryHandler geometryHandler) {
        this.geometryHandler = geometryHandler;
    }

    @Inject
    public void setDbQueryFactory(DbQueryFactory dbQueryFactory) {
        this.dbQueryFactory = dbQueryFactory;
    }

    @Inject
    public void setStaticCapabilitiesProvider(Optional<StaticCapabilitiesProvider> staticCapabilitiesProvider) {
        this.staticCapabilitiesProvider = staticCapabilitiesProvider;
    }

    @Setting(CacheFeederSettingDefinitionProvider.CACHE_THREAD_COUNT)
    public void setCacheThreadCount(int threads) throws ConfigurationError {
        Validation.greaterZero("Cache Thread Count", threads);
        this.cacheThreadCount = threads;
    }

    @Override
    public void updateCache(SosWritableContentCache cache) throws OwsExceptionReport {
        checkCacheNotNull(cache);
        List<OwsExceptionReport> errors = CollectionHelper.synchronizedList();
        Session session = null;
        try {
            InitialCacheUpdate update = new InitialCacheUpdate(
                    this.cacheThreadCount,
                    this.defaultLocale,
                    this.i18NDAORepository,
                    this.sessionStore,
                    this.serviceMetadataRepository,
                    geometryHandler,
                    dbQueryFactory);
            session = this.sessionStore.getSession();
            update.setCache(cache);
            update.setErrors(errors);
            update.setSession(session);

            LOGGER.debug("Starting cache update");
            long cacheUpdateStartTime = System.currentTimeMillis();

            update.execute();
            if (staticCapabilitiesProvider.isPresent()) {
                staticCapabilitiesProvider.get().create();
            }
            logCacheLoadTime(cacheUpdateStartTime);
        } catch (Exception e) {
            LOGGER.error(ERROR_UPDATE_CACHE, e);
            errors.add(new NoApplicableCodeException().causedBy(e).withMessage(ERROR_UPDATE_CACHE));
        } finally {
            try {
                this.sessionStore.returnSession(session);
            } catch (Exception e2) {
                // TODO check why this is necessary
                LOGGER.error(ERROR_RETURNING_CONNECTION, e2);
            }
        }
        if (!errors.isEmpty()) {
            throw new CompositeOwsException(errors);
        }

    }

    @Override
    public void updateCacheOfferings(SosWritableContentCache cache, Collection<String> offeringsNeedingUpdate)
            throws OwsExceptionReport {
        checkCacheNotNull(cache);
        if (CollectionHelper.isEmpty(offeringsNeedingUpdate)) {
            return;
        }
        List<OwsExceptionReport> errors = CollectionHelper.synchronizedList();
        Session session = this.sessionStore.getSession();
        OfferingCacheUpdate update = new OfferingCacheUpdate(
                this.cacheThreadCount,
                this.defaultLocale,
                this.geometryHandler,
                this.sessionStore,
                dbQueryFactory);
        update.setCache(cache);
        update.setErrors(errors);
        update.setSession(session);

        LOGGER.info("Starting offering cache update for {} offering(s)", offeringsNeedingUpdate.size());
        long cacheUpdateStartTime = System.currentTimeMillis();

        try {
            update.execute();
        } catch (Exception e) {
            LOGGER.error(ERROR_UPDATE_CACHE, e);
            errors.add(new NoApplicableCodeException().causedBy(e).withMessage(ERROR_UPDATE_CACHE));
        } finally {
            try {
                this.sessionStore.returnSession(session);
            } catch (Exception e2) {
                // TODO check why this is necessary
                LOGGER.error(ERROR_RETURNING_CONNECTION, e2);
            }
        }

        logCacheLoadTime(cacheUpdateStartTime);

        if (!errors.isEmpty()) {
            throw new CompositeOwsException(errors);
        }
    }

    private void checkCacheNotNull(WritableContentCache cache) {
        if (cache == null) {
            throw new NullPointerException("cache is null");
        }
    }

    private void logCacheLoadTime(long startTime) {
        Period cacheLoadPeriod = new Period(startTime, System.currentTimeMillis());
        LOGGER.info("Cache load finished in {} ({} seconds)",
                PeriodFormat.getDefault().print(cacheLoadPeriod.normalizedStandard()),
                cacheLoadPeriod.toStandardSeconds());
    }

}
