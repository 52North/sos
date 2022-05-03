/*
 * Copyright (C) 2012-2022 52°North Spatial Information Research GmbH
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
package org.n52.sos.ds.hibernate.admin;

import java.util.Map;

import javax.inject.Inject;

import org.hibernate.Session;
import org.n52.iceland.ds.ConnectionProvider;
import org.n52.shetland.ogc.ows.exception.OwsExceptionReport;
import org.n52.sos.ds.ProcedureFormatDAO;
import org.n52.sos.ds.hibernate.HibernateSessionHolder;
import org.n52.sos.ds.hibernate.dao.DaoFactory;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * @author <a href="mailto:shane@axiomalaska.com">Shane StClair</a>
 */
@SuppressFBWarnings({"EI_EXPOSE_REP2"})
public class HibernateProcedureFormatDAO implements ProcedureFormatDAO {

    private HibernateSessionHolder sessionHolder;
    private DaoFactory daoFactory;

    @Inject
    public void setConnectionProvider(ConnectionProvider connectionProvider) {
        this.sessionHolder = new HibernateSessionHolder(connectionProvider);
    }

    @Inject
    public void setDaoFactory(DaoFactory daoFactory) {
        this.daoFactory = daoFactory;
    }

    @Override
    public Map<String, String> getProcedureFormatMap() throws OwsExceptionReport {
        Session session = null;
        Map<String, String> procedureFormatMap = null;
        try {
            session = this.sessionHolder.getSession();
            procedureFormatMap = daoFactory.getProcedureDAO().getProcedureFormatMap(session);
        } finally {
            this.sessionHolder.returnSession(session);
        }
        return procedureFormatMap;
    }
}
