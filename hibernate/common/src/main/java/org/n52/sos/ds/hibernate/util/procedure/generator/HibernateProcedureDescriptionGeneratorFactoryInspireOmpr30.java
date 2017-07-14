/**
 * Copyright (C) 2012-2017 52°North Initiative for Geospatial Open Source
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
package org.n52.sos.ds.hibernate.util.procedure.generator;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import javax.inject.Inject;

import org.hibernate.Session;
import org.n52.faroe.SettingsService;
import org.n52.iceland.cache.ContentCacheController;
import org.n52.iceland.i18n.I18NDAORepository;
import org.n52.shetland.inspire.base2.Contact;
import org.n52.shetland.inspire.base2.RelatedParty;
import org.n52.shetland.inspire.ompr.InspireOMPRConstants;
import org.n52.shetland.iso.gmd.LocalisedCharacterString;
import org.n52.shetland.iso.gmd.PT_FreeText;
import org.n52.shetland.ogc.ows.OwsAddress;
import org.n52.shetland.ogc.ows.OwsContact;
import org.n52.shetland.ogc.ows.exception.OwsExceptionReport;
import org.n52.shetland.ogc.sos.SosProcedureDescription;
import org.n52.shetland.util.CollectionHelper;
import org.n52.sos.ds.hibernate.dao.DaoFactory;
import org.n52.sos.ds.hibernate.entities.Procedure;
import org.n52.sos.service.Configurator;

/**
 * Generator class for WaterML 2.0 procedure descriptions
 *
 * @author Carsten Hollmann <c.hollmann@52north.org>
 * @since 4.2.0
 *
 */
public class HibernateProcedureDescriptionGeneratorFactoryInspireOmpr30
implements HibernateProcedureDescriptionGeneratorFactory {

    private final DaoFactory daoFactory;
    private final I18NDAORepository i18NDAORepository;
    private final ContentCacheController cacheController;
    private final SettingsService settingsService;

    @Inject
    public HibernateProcedureDescriptionGeneratorFactoryInspireOmpr30(DaoFactory daoFactory,
                                                              I18NDAORepository i18NDAORepository,
                                                              ContentCacheController cacheController,
                                                              SettingsService settingsService) {
        this.daoFactory = daoFactory;
        this.i18NDAORepository = i18NDAORepository;
        this.cacheController = cacheController;
        this.settingsService = settingsService;
    }

    @Override
    public Set<HibernateProcedureDescriptionGeneratorKey> getKeys() {
        return Collections.unmodifiableSet(HibernateProcedureDescriptionGeneratorInspireOmpr30.GENERATOR_KEY_TYPES);
    }

    @Override
    public HibernateProcedureDescriptionGenerator create(HibernateProcedureDescriptionGeneratorKey key) {
        HibernateProcedureDescriptionGenerator generator
                = new HibernateProcedureDescriptionGeneratorInspireOmpr30(getDaoFactory(),
                                                                  getI18NDAORepository(),
                                                                  getCacheController());
        getSettingsService().configureOnce(generator);
        return generator;
    }

    public DaoFactory getDaoFactory() {
        return this.daoFactory;
    }

    public I18NDAORepository getI18NDAORepository() {
        return this.i18NDAORepository;
    }

    public ContentCacheController getCacheController() {
        return this.cacheController;
    }

    public SettingsService getSettingsService() {
        return this.settingsService;
    }
}
