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
package org.n52.sos.ogc.gwml;

import org.n52.sos.w3c.SchemaLocation;

public interface GWMLConstants {

    String NS_GWML_21  = "http://www.opengis.net/gwml/2.1";
    
    String NS_GWML_21_PREFIX = "gwml2";
    
    String SCHEMA_LOCATION_URL_GWML_21 = "http://ngwd-bdnes.cits.nrcan.gc.ca/service/gwml/schemas/2.1/gwml2.xsd";

    SchemaLocation GWML_21_SCHEMA_LOCATION = new SchemaLocation(NS_GWML_21, SCHEMA_LOCATION_URL_GWML_21);
}