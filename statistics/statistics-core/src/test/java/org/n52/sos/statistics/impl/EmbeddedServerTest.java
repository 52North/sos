/**
 * Copyright (C) 2012-2015 52°North Initiative for Geospatial Open Source
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
package org.n52.sos.statistics.impl;

import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;

import org.elasticsearch.action.index.IndexResponse;
import org.junit.Assert;
import org.junit.Test;
import org.n52.iceland.statistics.api.ElasticsearchSettings;
import org.n52.iceland.statistics.api.ElasticsearchSettingsKeys;
import org.n52.iceland.statistics.impl.ElasticsearchAdminHandler;
import org.n52.iceland.statistics.impl.ElasticsearchDataHandler;

import basetest.SpringBaseTest;

public class EmbeddedServerTest extends SpringBaseTest {
    @Inject
    private ElasticsearchAdminHandler adminHandler;

    @Inject
    private ElasticsearchDataHandler dataHandler;

    @Inject
    private ElasticsearchSettings settings;

    @Test
    public void connectEmbeddedMode() throws Exception {

        settings.setNodeConnectionMode(ElasticsearchSettingsKeys.CONNECTION_MODE_EMBEDDED_SERVER);
        adminHandler.init();

        Map<String, Object> data = new HashMap<>();
        data.put("test", "test-string");
        IndexResponse idx = dataHandler.persist(data);

        Thread.sleep(2000);

        String ret = dataHandler.getClient().prepareGet(idx.getIndex(), idx.getType(), idx.getId()).get().getSourceAsString();
        Assert.assertNotNull(ret);

        adminHandler.destroy();

        // FileUtils.deleteDirectory(new File("./WEB-INF"));
    }
}
