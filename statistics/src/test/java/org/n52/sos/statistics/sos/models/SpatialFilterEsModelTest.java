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
package org.n52.sos.statistics.sos.models;

import java.util.Map;

import org.junit.Assert;
import org.junit.Test;
import org.n52.iceland.exception.ows.OwsExceptionReport;
import org.n52.iceland.ogc.filter.FilterConstants.SpatialOperator;
import org.n52.sos.ogc.filter.SpatialFilter;
import org.n52.sos.statistics.sos.SosDataMapping;
import org.n52.sos.util.JTSHelper;

import com.vividsolutions.jts.geom.Geometry;

public class SpatialFilterEsModelTest {

    @Test
    public void createBBOXGeometryAndConvert() throws OwsExceptionReport {
        Geometry geom = JTSHelper.createGeometryFromWKT("POLYGON ((30 10, 40 40, 20 40, 10 20, 30 10))", 4326);
        SpatialFilter filter = new SpatialFilter(SpatialOperator.BBOX, geom, "value-ref");

        Map<String, Object> map = SpatialFilterEsModel.convert(filter).getAsMap();

        Assert.assertEquals("value-ref", map.get(SosDataMapping.SPATIAL_FILTER_VALUE_REF));
        Assert.assertEquals(SpatialOperator.BBOX.toString(), map.get(SosDataMapping.SPATIAL_FILTER_OPERATOR));
        Assert.assertNotNull(map.get(SosDataMapping.SPATIAL_FILTER_SHAPE));
    }

    @Test
    public void createInvalidOperatorTypeGeometry() throws OwsExceptionReport {
        Geometry geom = JTSHelper.createGeometryFromWKT("POLYGON ((30 10, 40 40, 20 40, 10 20, 30 10))", 4326);
        SpatialFilter filter = new SpatialFilter(SpatialOperator.Crosses, geom, "value-ref");

        Map<String, Object> map = SpatialFilterEsModel.convert(filter).getAsMap();

        Assert.assertNull(map);
    }

    @Test(
            expected = IllegalArgumentException.class)
    public void createInvalidGeometry() throws OwsExceptionReport {
        Geometry geom = JTSHelper.createGeometryFromWKT("POLYGON ((40 40, 20 40, 10 20, 30 10))", 4326);
        SpatialFilter filter = new SpatialFilter(SpatialOperator.Crosses, geom, "value-ref");

        Map<String, Object> map = SpatialFilterEsModel.convert(filter).getAsMap();

        Assert.assertNull(map);
    }

    @Test
    public void returnNullMapIfNull() {
        Assert.assertNull(SpatialFilterEsModel.convert(null).getAsMap());
    }

    @Test
    public void createInvalidSridGeometry() throws OwsExceptionReport {
        Geometry geom = JTSHelper.createGeometryFromWKT("POLYGON ((30 10, 40 40, 20 40, 10 20, 30 10))", 9999);
        SpatialFilter filter = new SpatialFilter(SpatialOperator.BBOX, geom, "value-ref");

        Map<String, Object> map = SpatialFilterEsModel.convert(filter).getAsMap();

        Assert.assertNull(map);
    }
}
