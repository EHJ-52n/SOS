/*
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

import java.util.Locale;
import java.util.Set;

import org.hibernate.Session;

import org.n52.shetland.ogc.ows.exception.OwsExceptionReport;
import org.n52.shetland.ogc.sensorML.ProcessMethod;
import org.n52.shetland.ogc.sensorML.ProcessModel;
import org.n52.shetland.ogc.sensorML.RulesDefinition;
import org.n52.shetland.ogc.sensorML.SensorMLConstants;
import org.n52.shetland.ogc.sensorML.System;
import org.n52.shetland.ogc.sos.SosProcedureDescription;
import org.n52.shetland.ogc.swe.SweAbstractDataComponent;
import org.n52.shetland.ogc.swe.simpleType.SweObservableProperty;
import org.n52.shetland.util.CollectionHelper;
import org.n52.sos.ds.hibernate.dao.DaoFactory;
import org.n52.sos.ds.hibernate.entities.Procedure;

/**
 * Generator class for SensorML 1.0.1 procedure descriptions
 * @author <a href="mailto:c.hollmann@52north.org">Carsten Hollmann</a>
 * @since 4.2.0
 *
 */
public class HibernateProcedureDescriptionGeneratorFactorySml101 implements HibernateProcedureDescriptionGeneratorFactory {

    private static final Set<HibernateProcedureDescriptionGeneratorFactoryKey> GENERATOR_KEY_TYPES = CollectionHelper.set(
            new HibernateProcedureDescriptionGeneratorFactoryKey(SensorMLConstants.SENSORML_OUTPUT_FORMAT_MIME_TYPE),
            new HibernateProcedureDescriptionGeneratorFactoryKey(SensorMLConstants.SENSORML_OUTPUT_FORMAT_URL));

    private final DaoFactory daoFactory;

    public HibernateProcedureDescriptionGeneratorFactorySml101(DaoFactory daoFactory) {
        this.daoFactory = daoFactory;
    }

    @Override
    public Set<HibernateProcedureDescriptionGeneratorFactoryKey> getKeys() {
        return GENERATOR_KEY_TYPES;
    }

    @Override
    public SosProcedureDescription<?> create(Procedure procedure, Locale i18n, Session session) throws OwsExceptionReport {
        return new HibernateProcedureDescriptionGeneratorSml101(daoFactory).generateProcedureDescription(procedure, i18n, session);
    }


    private class HibernateProcedureDescriptionGeneratorSml101 extends AbstractHibernateProcedureDescriptionGeneratorSml {
        public HibernateProcedureDescriptionGeneratorSml101(DaoFactory daoFactory) {
            super(daoFactory);
        }

        /**
         * Generate procedure description from Hibernate procedure entity if no
         * description (file, XML text) is available
         *
         * @param procedure
         *            Hibernate procedure entity
         * @param session
         *            the session
         *
         * @return Generated procedure description
         *
         * @throws OwsExceptionReport
         *             If an error occurs
         */
        @Override
        public SosProcedureDescription<?> generateProcedureDescription(Procedure procedure, Locale i18n, Session session) throws OwsExceptionReport {
            setLocale(i18n);
            // 2 try to get position from entity
            if (procedure.isSpatial()) {
                // 2.1 if position is available -> system -> own class <- should
                // be compliant with SWE lightweight profile
                return new SosProcedureDescription<>(createSmlSystem(procedure, session));
            } else {
                // 2.2 if no position is available -> processModel -> own class
                return new SosProcedureDescription<>(createSmlProcessModel(procedure, session));
            }
        }

        /**
         * Create a SensorML ProcessModel from Hibernate procedure entity
         *
         * @param procedure
         *            Hibernate procedure entity
         *
         * @return SensorML ProcessModel
         *
         * @throws OwsExceptionReport
         *             If an error occurs
         */
        private ProcessModel createSmlProcessModel(Procedure procedure, Session session) throws OwsExceptionReport {
            final ProcessModel processModel = new ProcessModel();
            setCommonValues(procedure, processModel, session);
            processModel.setMethod(createMethod(procedure, getObservablePropertiesForProcedure(procedure.getIdentifier())));
    //        processModel.setNames(createNames(procedure));
            return processModel;
        }

        /**
         * Create a SensorML System from Hibernate procedure entity
         *
         * @param procedure
         *            Hibernate procedure entity
         *
         * @return SensorML System
         *
         * @throws OwsExceptionReport
         *             If an error occurs
         */
        private System createSmlSystem(Procedure procedure, Session session) throws OwsExceptionReport {
            System smlSystem = new System();
            setCommonValues(procedure, smlSystem, session);
            smlSystem.setPosition(createPosition(procedure));
            return smlSystem;
        }

        /**
         * Create a SensorML ProcessMethod for ProcessModel
         *
         * @param procedure
         *            Hibernate procedure entity
         * @param observableProperties
         *            Properties observed by the procedure
         *
         * @return SenbsorML ProcessModel
         */
        private ProcessMethod createMethod(Procedure procedure, String[] observableProperties) {
            return new ProcessMethod(createRulesDefinition(procedure, observableProperties));
        }

        /**
         * Create the rules definition for ProcessMethod
         *
         * @param procedure
         *            Hibernate procedure entity
         * @param observableProperties
         *            Properties observed by the procedure
         *
         * @return SensorML RulesDefinition
         */
        private RulesDefinition createRulesDefinition(Procedure procedure, String[] observableProperties) {
            RulesDefinition rD = new RulesDefinition();
            String template = procedureSettings().getProcessMethodRulesDefinitionDescriptionTemplate();
            String description =
                    String.format(template, procedure.getIdentifier(), COMMA_JOINER.join(observableProperties));
            rD.setDescription(description);
            return rD;
        }

        @Override
        protected SweAbstractDataComponent getInputComponent(String observableProperty) {
            return  new SweObservableProperty().setDefinition(observableProperty);
        }
    }
}
