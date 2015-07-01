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
package org.n52.sos.ds.hibernate;

import java.util.List;

import javax.inject.Inject;

import org.hibernate.Criteria;
import org.hibernate.HibernateException;
import org.hibernate.Session;
import org.hibernate.criterion.Restrictions;

import org.n52.iceland.convert.ConverterException;
import org.n52.iceland.exception.CodedException;
import org.n52.iceland.exception.ows.NoApplicableCodeException;
import org.n52.iceland.exception.ows.OwsExceptionReport;
import org.n52.iceland.i18n.LocaleHelper;
import org.n52.iceland.ogc.sos.SosConstants;
import org.n52.sos.ds.AbstractGetObservationByIdHandler;
import org.n52.sos.ds.HibernateDatasourceConstants;
import org.n52.sos.ds.hibernate.dao.DaoFactory;
import org.n52.sos.ds.hibernate.entities.AbstractObservation;
import org.n52.sos.ds.hibernate.util.HibernateHelper;
import org.n52.sos.ds.hibernate.util.observation.HibernateObservationUtilities;
import org.n52.sos.request.GetObservationByIdRequest;
import org.n52.sos.response.GetObservationByIdResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.n52.iceland.ds.ConnectionProvider;
import org.n52.iceland.ogc.ows.ServiceMetadataRepository;


/**
 * Implementation of the abstract class AbstractGetObservationByIdHandler
 *
 * @since 4.0.0
 *
 */
public class GetObservationByIdDAO extends AbstractGetObservationByIdHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GetObservationByIdDAO.class);

    private HibernateSessionHolder sessionHolder;
    private ServiceMetadataRepository serviceMetadataRepository;

    public GetObservationByIdDAO() {
        super(SosConstants.SOS);
    }

    @Inject
    public void setServiceMetadataRepository(ServiceMetadataRepository repo) {
        this.serviceMetadataRepository = repo;
    }

    @Inject
    public void setConnectionProvider(ConnectionProvider connectionProvider) {
        this.sessionHolder = new HibernateSessionHolder(connectionProvider);
    }

    @Override
    public GetObservationByIdResponse getObservationById(GetObservationByIdRequest request) throws OwsExceptionReport {
        Session session = null;
        try {
            session = sessionHolder.getSession();
            List<AbstractObservation> observations = queryObservation(request, session);
            GetObservationByIdResponse response = new GetObservationByIdResponse();
            response.setService(request.getService());
            response.setVersion(request.getVersion());
            response.setResponseFormat(request.getResponseFormat());
            response.setObservationCollection(HibernateObservationUtilities.createSosObservationsFromObservations(
                    observations, request, this.serviceMetadataRepository.getServiceProviderFactory(request.getService()), request.getRequestedLocale(), session));
            return response;

        } catch (HibernateException he) {
            throw new NoApplicableCodeException().causedBy(he).withMessage("Error while querying observation data!");
        } catch (ConverterException ce) {
            throw new NoApplicableCodeException().causedBy(ce).withMessage("Error while processing observation data!");
        } finally {
            sessionHolder.returnSession(session);
        }
    }

    /**
     * Query observations for observation identifiers
     *
     * @param request
     *            GetObservationById request
     * @param session
     *            Hibernate session
     * @return Resulting observations
     * @throws CodedException
     *             If an error occurs during querying the database
     */
    @SuppressWarnings("unchecked")
    private List<AbstractObservation> queryObservation(GetObservationByIdRequest request, Session session)
            throws OwsExceptionReport {
        Criteria c =
                DaoFactory.getInstance().getObservationDAO()
                        .getObservationClassCriteriaForResultModel(request.getResultModel(), session);
        c.add(Restrictions.in(AbstractObservation.IDENTIFIER, request.getObservationIdentifier()));
        LOGGER.debug("QUERY queryObservation(request): {}", HibernateHelper.getSqlString(c));
        return c.list();
    }
}
