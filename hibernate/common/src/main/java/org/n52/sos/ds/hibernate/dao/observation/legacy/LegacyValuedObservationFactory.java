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
package org.n52.sos.ds.hibernate.dao.observation.legacy;

import org.n52.sos.ds.hibernate.dao.observation.ValuedObservationFactory;
import org.n52.sos.ds.hibernate.dao.observation.series.SeriesValuedObervationFactory;
import org.n52.sos.ds.hibernate.entities.observation.ValuedObservation;
import org.n52.sos.ds.hibernate.entities.observation.legacy.AbstractValuedLegacyObservation;
import org.n52.sos.ds.hibernate.entities.observation.legacy.valued.BlobValuedLegacyObservation;
import org.n52.sos.ds.hibernate.entities.observation.legacy.valued.BooleanValuedLegacyObservation;
import org.n52.sos.ds.hibernate.entities.observation.legacy.valued.CategoryValuedLegacyObservation;
import org.n52.sos.ds.hibernate.entities.observation.legacy.valued.ComplexValuedLegacyObservation;
import org.n52.sos.ds.hibernate.entities.observation.legacy.valued.CountValuedLegacyObservation;
import org.n52.sos.ds.hibernate.entities.observation.legacy.valued.GeometryValuedLegacyObservation;
import org.n52.sos.ds.hibernate.entities.observation.legacy.valued.NumericValuedLegacyObservation;
import org.n52.sos.ds.hibernate.entities.observation.legacy.valued.SweDataArrayValuedLegacyObservation;
import org.n52.sos.ds.hibernate.entities.observation.legacy.valued.TextValuedLegacyObservation;
import org.n52.sos.ds.hibernate.entities.observation.valued.BlobValuedObservation;
import org.n52.sos.ds.hibernate.entities.observation.valued.BooleanValuedObservation;
import org.n52.sos.ds.hibernate.entities.observation.valued.CategoryValuedObservation;
import org.n52.sos.ds.hibernate.entities.observation.valued.ComplexValuedObservation;
import org.n52.sos.ds.hibernate.entities.observation.valued.CountValuedObservation;
import org.n52.sos.ds.hibernate.entities.observation.valued.GeometryValuedObservation;
import org.n52.sos.ds.hibernate.entities.observation.valued.NumericValuedObservation;
import org.n52.sos.ds.hibernate.entities.observation.valued.ProfileValuedObservation;
import org.n52.sos.ds.hibernate.entities.observation.valued.ReferenceValuedObservation;
import org.n52.sos.ds.hibernate.entities.observation.valued.SweDataArrayValuedObservation;
import org.n52.sos.ds.hibernate.entities.observation.valued.TextValuedObservation;

public class LegacyValuedObservationFactory
        extends
        ValuedObservationFactory {

    @Override
    @SuppressWarnings("rawtypes")
    public Class<? extends ValuedObservation> valuedObservationClass() {
        return AbstractValuedLegacyObservation.class;
    }

    @Override
    public Class<? extends BlobValuedObservation> blobClass() {
        return BlobValuedLegacyObservation.class;
    }

    @Override
    public Class<? extends BooleanValuedObservation> truthClass() {
        return BooleanValuedLegacyObservation.class;
    }

    @Override
    public Class<? extends CategoryValuedObservation> categoryClass() {
        return CategoryValuedLegacyObservation.class;
    }

    @Override
    public Class<? extends CountValuedObservation> countClass() {
        return CountValuedLegacyObservation.class;
    }

    @Override
    public Class<? extends GeometryValuedObservation> geometryClass() {
        return GeometryValuedLegacyObservation.class;
    }

    @Override
    public Class<? extends NumericValuedObservation> numericClass() {
        return NumericValuedLegacyObservation.class;
    }

    @Override
    public Class<? extends SweDataArrayValuedObservation> sweDataArrayClass() {
        return SweDataArrayValuedLegacyObservation.class;
    }

    @Override
    public Class<? extends TextValuedObservation> textClass() {
        return TextValuedLegacyObservation.class;
    }

    @Override
    public Class<? extends ComplexValuedObservation> complexClass() {
        return ComplexValuedLegacyObservation.class;
    }

    @Override
    public Class<? extends ProfileValuedObservation> profileClass() {
        return null;
    }

    @Override
    public Class<? extends ReferenceValuedObservation> referenceClass() {
        return null;
    }
    
    public static LegacyValuedObservationFactory getInstance() {
        return Holder.INSTANCE;
    }

    private static class Holder {
        private static final LegacyValuedObservationFactory INSTANCE
                = new LegacyValuedObservationFactory();

        private Holder() {
        }
    }

}
