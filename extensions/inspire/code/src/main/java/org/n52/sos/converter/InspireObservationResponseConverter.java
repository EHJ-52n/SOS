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
package org.n52.sos.converter;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.n52.sos.cache.ContentCache;
import org.n52.sos.convert.RequestResponseModifier;
import org.n52.sos.convert.RequestResponseModifierFacilitator;
import org.n52.sos.convert.RequestResponseModifierKeyType;
import org.n52.sos.exception.CodedException;
import org.n52.sos.exception.ows.InvalidParameterValueException;
import org.n52.sos.ogc.om.ObservationMergeIndicator;
import org.n52.sos.ogc.om.ObservationMerger;
import org.n52.sos.ogc.om.OmObservation;
import org.n52.sos.ogc.om.StreamingObservation;
import org.n52.sos.ogc.om.StreamingValue;
import org.n52.sos.ogc.ows.OwsExceptionReport;
import org.n52.sos.ogc.sos.Sos1Constants;
import org.n52.sos.ogc.sos.Sos2Constants;
import org.n52.sos.ogc.sos.SosConstants;
import org.n52.sos.request.AbstractObservationRequest;
import org.n52.sos.request.AbstractServiceRequest;
import org.n52.sos.request.GetObservationRequest;
import org.n52.sos.response.AbstractServiceResponse;
import org.n52.sos.response.GetObservationResponse;
import org.n52.sos.service.Configurator;
import org.n52.sos.util.CollectionHelper;
import org.n52.svalbard.inspire.omso.InspireOMSOConstants;
import org.n52.svalbard.inspire.omso.MultiPointObservation;
import org.n52.svalbard.inspire.omso.PointObservation;
import org.n52.svalbard.inspire.omso.PointTimeSeriesObservation;
import org.n52.svalbard.inspire.omso.ProfileObservation;
import org.n52.svalbard.inspire.omso.TrajectoryObservation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

public class InspireObservationResponseConverter implements RequestResponseModifier<AbstractServiceRequest<?>, AbstractServiceResponse> {

    private static final Logger LOGGER = LoggerFactory.getLogger(InspireObservationResponseConverter.class);

    private static final Set<RequestResponseModifierKeyType> REQUEST_RESPONSE_MODIFIER_KEY_TYPES = getKeyTypes();

    /**
     * Get the keys
     * 
     * @return Set of keys
     */
    private static Set<RequestResponseModifierKeyType> getKeyTypes() {
        Set<String> services = Sets.newHashSet(SosConstants.SOS);
        Set<String> versions = Sets.newHashSet(Sos1Constants.SERVICEVERSION, Sos2Constants.SERVICEVERSION);
        Map<GetObservationRequest, GetObservationResponse> requestResponseMap = Maps.newHashMap();

        requestResponseMap.put(new GetObservationRequest(), new GetObservationResponse());
        Set<RequestResponseModifierKeyType> keys = Sets.newHashSet();
        for (String service : services) {
            for (String version : versions) {
                for (AbstractServiceRequest<?> request : requestResponseMap.keySet()) {
                    keys.add(new RequestResponseModifierKeyType(service, version, request));
                    keys.add(new RequestResponseModifierKeyType(service, version, request,
                            requestResponseMap.get(request)));
                }
            }
        }
        return keys;
    }

    @Override
    public Set<RequestResponseModifierKeyType> getRequestResponseModifierKeyTypes() {
        return Collections.unmodifiableSet(REQUEST_RESPONSE_MODIFIER_KEY_TYPES);
    }
    
    @Override
    public AbstractServiceRequest<?> modifyRequest(AbstractServiceRequest<?> request) throws OwsExceptionReport {
        if (request instanceof GetObservationRequest) {
            GetObservationRequest req = (GetObservationRequest) request;
            if (req.isSetResponseFormat() && InspireOMSOConstants.NS_OMSO_30.equals(req.getResponseFormat())) {
                if (req.isSetResultModel()) {
                    checkRequestedResultType(req.getResultModel());
                }
            }
        }
        return request;
    }

    @Override
    public AbstractServiceResponse modifyResponse(AbstractServiceRequest<?> request, AbstractServiceResponse  response)
            throws OwsExceptionReport {
        // TODO How to identify INSPIRE-Obs and ObsType
        if (response instanceof GetObservationResponse) {
            GetObservationResponse resp = (GetObservationResponse) response;
            if (InspireOMSOConstants.NS_OMSO_30.equals(resp.getResponseFormat()) && CollectionHelper.isNotEmpty(resp.getObservationCollection())) {
                if (resp.hasStreamingData()) {
                    checkForStreamingData(request, resp);
                } else {
                    checkForNonStreamingData(request, resp);
    //                for (OmObservation obs : resp.getObservationCollection()) {
    //                    if (obs.isSetHeightDepthParameter()) {
    //                        convertedObservations.addAll(convertToProfileObservations(obs));
    //                    } else if (obs.isSetSpatialFilteringProfileParameter()) {
    //                        convertedObservations.addAll(convertToTrajectoryObservations(obs));
    //                    } else {
    //                        convertedObservations.addAll(convertToPointObservations(obs));
    //                    }
    //                }
    //                if (InspireOMSOConstants.OBS_TYPE_POINT_OBSERVATION.equals(observationType)) {
    //                    return convertToPointObservations(resp.getObservationCollection());
    //                } else if (InspireOMSOConstants.OBS_TYPE_POINT_TIME_SERIES_OBSERVATION.equals(observationType)) {
    //                    return convertToPointTimeSeriesObservations(resp);
    //                } else if (InspireOMSOConstants.OBS_TYPE_MULTI_POINT_OBSERVATION.equals(observationType)) {
    //                    return convertToMultipointObservations(resp);
    //                } else if (InspireOMSOConstants.OBS_TYPE_PROFILE_OBSERVATION.equals(observationType)) {
    //                    return convertToProfileObservations(resp);
    //                } else if (InspireOMSOConstants.OBS_TYPE_TRAJECTORY_OBSERVATION.equals(observationType)) {
    //                    return convertToTrajectoryObservations(resp);
    //                } 
                }
            }
        }
        return response;
    }

    private void checkForStreamingData(AbstractServiceRequest<?> request, GetObservationResponse response) throws OwsExceptionReport {
        Map<String, List<OmObservation>> map = Maps.newHashMap();
        for (OmObservation omObservation : response.getObservationCollection()) {
            if (omObservation.getValue() instanceof StreamingValue<?>) {
                if (checkRequestedObservationTypeForOffering(omObservation, request)) {
                    List<OmObservation> observations = ((StreamingValue<?>)omObservation.getValue()).getObservation();
                    for (OmObservation observation : observations) {
                        if (CollectionHelper.isNotEmpty(observations)) {
                            String observationType = checkForObservationTypeForStreaming(observation, request);
                            if (InspireOMSOConstants.OBS_TYPE_PROFILE_OBSERVATION.equals(observationType)) {
                                putOrAdd(map, InspireOMSOConstants.OBS_TYPE_PROFILE_OBSERVATION, convertToProfileObservations(observation));
                            } else if (InspireOMSOConstants.OBS_TYPE_TRAJECTORY_OBSERVATION.equals(observationType)) {
                                putOrAdd(map, InspireOMSOConstants.OBS_TYPE_TRAJECTORY_OBSERVATION, convertToTrajectoryObservations(observation));
                            } else if (InspireOMSOConstants.OBS_TYPE_MULTI_POINT_OBSERVATION.equals(observationType)) {
                                putOrAdd(map, InspireOMSOConstants.OBS_TYPE_MULTI_POINT_OBSERVATION, convertToMultiPointObservations(observation));
                            } else if (InspireOMSOConstants.OBS_TYPE_POINT_TIME_SERIES_OBSERVATION.equals(observationType)) {
                                putOrAdd(map, InspireOMSOConstants.OBS_TYPE_POINT_TIME_SERIES_OBSERVATION, convertToPointTimeSeriesObservations(observation));
                            } else if (InspireOMSOConstants.OBS_TYPE_POINT_OBSERVATION.equals(observationType)) {
                                putOrAdd(map, InspireOMSOConstants.OBS_TYPE_POINT_OBSERVATION, convertToPointObservations(observation));
                            }
                        }
                    }
                }
            } else if (omObservation.getValue() instanceof StreamingObservation) {
                // TODO
            }
        }
        response.setObservationCollection(mergeObservations(map));
    }
    
    private void checkForNonStreamingData(AbstractServiceRequest<?> request, GetObservationResponse response) {
        Map<String, List<OmObservation>> map = Maps.newHashMap(); 
        String requestedObservationType = ((AbstractObservationRequest) request).getResultModel();
        for (OmObservation obs : response.getObservationCollection()) {
            // TODO check for requested type
            if (obs.isSetHeightDepthParameter()) {
                putOrAdd(map, InspireOMSOConstants.OBS_TYPE_PROFILE_OBSERVATION, convertToProfileObservations(obs));
            } else if (obs.isSetSpatialFilteringProfileParameter()) {
                putOrAdd(map, InspireOMSOConstants.OBS_TYPE_TRAJECTORY_OBSERVATION, convertToTrajectoryObservations(obs));
            } else {
                putOrAdd(map, InspireOMSOConstants.OBS_TYPE_POINT_OBSERVATION, convertToPointObservations(obs));
            }
        }
        response.setObservationCollection(mergeObservations(map));
    }

    private List<OmObservation> mergeObservations(Map<String, List<OmObservation>> map) {
        List<OmObservation> mergedObservations = Lists.newArrayList();
        for (String key : map.keySet()) {
            switch (key) {
            case InspireOMSOConstants.OBS_TYPE_PROFILE_OBSERVATION:
                mergedObservations.addAll(mergeProfileObservation(map.get(key)));
                break;
            case InspireOMSOConstants.OBS_TYPE_TRAJECTORY_OBSERVATION:
                mergedObservations.addAll(mergeTrajectoryObservation(map.get(key)));           
                break;
            case InspireOMSOConstants.OBS_TYPE_MULTI_POINT_OBSERVATION:
                mergedObservations.addAll(mergeMultiPointObservation(map.get(key)));
                break;
            case InspireOMSOConstants.OBS_TYPE_POINT_TIME_SERIES_OBSERVATION:
                mergedObservations.addAll(mergePointTimeSeriesObservation(map.get(key)));
                break;
            default:
                mergedObservations.addAll(map.get(key));
                break;
            }
        }
        return mergedObservations;
    }

    private Collection<? extends OmObservation> mergePointTimeSeriesObservation(List<OmObservation> observations) {
        return new ObservationMerger().mergeObservations(observations, ObservationMergeIndicator.defaultObservationMergerIndicator());
    }

    private Collection<? extends OmObservation> mergeMultiPointObservation(List<OmObservation> observations) {
        ObservationMergeIndicator observationMergeIndicator = new ObservationMergeIndicator();
        observationMergeIndicator.setObservableProperty(true).setPhenomenonTime(true);
        //.setOfferings(true); // TODO check!!!
        return new ObservationMerger().mergeObservations(observations, observationMergeIndicator);
    }

    private Collection<? extends OmObservation> mergeProfileObservation(List<OmObservation> observations) {
        ObservationMergeIndicator observationMergeIndicator = new ObservationMergeIndicator();
        observationMergeIndicator.setObservableProperty(true).setProcedure(true).setFeatureOfInterest(true).setPhenomenonTime(true).setOfferings(true);
        return new ObservationMerger().mergeObservations(observations, observationMergeIndicator);
    }

    private Collection<? extends OmObservation> mergeTrajectoryObservation(List<OmObservation> observations) {
        ObservationMergeIndicator observationMergeIndicator = new ObservationMergeIndicator();
        observationMergeIndicator.setObservableProperty(true).setProcedure(true).setFeatureOfInterest(true).setOfferings(true);
        return new ObservationMerger().mergeObservations(observations, observationMergeIndicator);
    }

    private List<OmObservation> convertToPointObservations(OmObservation observation) {
           return Lists.<OmObservation>newArrayList(new PointObservation(observation));
//            if (response.hasStreamingData()) {
//                if (pointObservation.getValue() instanceof StreamingValue<?>) {
//                    StreamingValue<?> sv = (StreamingValue<?>)pointObservation.getValue();
//                    sv.setObservationTemplate(new PointObservation(sv.getObservationTemplate()));
//                }
//            }
    }

    private List<OmObservation> convertToPointTimeSeriesObservations(OmObservation observation) {
        return Lists.<OmObservation>newArrayList(new PointTimeSeriesObservation(observation));
//            if (response.hasStreamingData()) {
//                if (pointTimeSeriesObservation.getValue() instanceof StreamingValue<?>) {
//                    StreamingValue<?> sv = (StreamingValue<?>)pointTimeSeriesObservation.getValue();
//                    sv.setObservationTemplate(new PointTimeSeriesObservation(sv.getObservationTemplate()));
//                }
//            }
    }

    private List<OmObservation> convertToMultiPointObservations(OmObservation observation) throws CodedException {
        return Lists.<OmObservation>newArrayList(new MultiPointObservation(observation));
//        List<OmObservation> observations = Lists.newArrayList();
//        Map<String, MultiPointObservation> mergedObservations = Maps.newHashMap();
//        for (OmObservation omObservation : response.getObservationCollection()) {
//            String observedProperty = omObservation.getObservationConstellation().getObservableProperty().getIdentifier();
//            // TODO reset featureOfInterest
//            if (mergedObservations.containsKey(observedProperty)) {
//                MultiPointObservation multiPointObservation = mergedObservations.get(observedProperty);
//                if (response.hasStreamingData()) {
//                    // TODO Merge StreamingValue to current
//                } else {
//                    
//                }
//                
//            } else {
//                MultiPointObservation multiPointObservation = new MultiPointObservation(omObservation);
//                if (response.hasStreamingData()) {
//                    if (multiPointObservation.getValue() instanceof StreamingValue<?>) {
//                        StreamingValue<?> sv = (StreamingValue<?>)multiPointObservation.getValue();
//                        sv.setObservationTemplate(new MultiPointObservation(sv.getObservationTemplate()));
//                        // TODO Merge StreamingValue for same observedProperty(/Procedure)
//                    }
//                } else {
//                 // TODO Merge same observedProperty(/Procedure) at same time, FOI = Surface, convert value to MultiPointCoverage
//                }
//                mergedObservations.put(observedProperty, multiPointObservation);
//                observations.add(multiPointObservation);
//            }
//        }
////      ObservationMerger observationMerger = new ObservationMerger();
////      List<OmObservation> mergeObservations = observationMerger.mergeObservations(response.getObservationCollection(), ObservationMergeIndicator.defaultObservationMergerIndicator());
//        response.setObservationCollection(observations);
//        return response;
    }

    private List<OmObservation> convertToProfileObservations(OmObservation observation) {
         return Lists.<OmObservation>newArrayList(new ProfileObservation(observation));
//            if (observations.hasStreamingData()) {
//                if (profileObservation.getValue() instanceof StreamingValue<?>) {
//                    StreamingValue<?> sv = (StreamingValue<?>)profileObservation.getValue();
//                    sv.setObservationTemplate(new ProfileObservation(sv.getObservationTemplate()));
//                }
//            } else {
//             // TODO Merge same constellation different depth/height (param) same time, FOI = Curve, convert value to Rectified-/ReferencableGridCoverage
//            }
    }

    private List<OmObservation> convertToTrajectoryObservations(OmObservation observation) {
            return Lists.<OmObservation>newArrayList(new TrajectoryObservation(observation));
//            if (response.hasStreamingData()) {
//                if (trajectoryObservation.getValue() instanceof StreamingValue<?>) {
//                    StreamingValue<?> sv = (StreamingValue<?>)trajectoryObservation.getValue();
//                    sv.setObservationTemplate(new TrajectoryObservation(sv.getObservationTemplate()));
//                }
//            } else {
//             // TODO Merge same constellation different samplingGeometry, FOI = Curve, convert value to ???
//            }
    }

    private String checkForObservationTypeForStreaming(OmObservation observation, AbstractServiceRequest<?> request) {
        if (request instanceof AbstractObservationRequest && ((AbstractObservationRequest) request).isSetResultModel()) {
            String requestedObservationType = ((AbstractObservationRequest) request).getResultModel();
            if (checkForObservationType(observation, requestedObservationType)) {
                if (InspireOMSOConstants.OBS_TYPE_POINT_TIME_SERIES_OBSERVATION.equals(requestedObservationType)) {
//                    if (!observation.isSetHeightDepthParameter() && observation.isSetSpatialFilteringProfileParameter() && checkForTrajectory(observation)) {
                        return InspireOMSOConstants.OBS_TYPE_POINT_TIME_SERIES_OBSERVATION;
//                    }
                } else if (InspireOMSOConstants.OBS_TYPE_MULTI_POINT_OBSERVATION.equals(requestedObservationType)) {
//                    if (!observation.isSetHeightDepthParameter()) {
                        return InspireOMSOConstants.OBS_TYPE_MULTI_POINT_OBSERVATION;
//                    }
                } else if (InspireOMSOConstants.OBS_TYPE_PROFILE_OBSERVATION.equals(requestedObservationType) && observation.isSetHeightDepthParameter()) {
                    return InspireOMSOConstants.OBS_TYPE_PROFILE_OBSERVATION;
                } else if (InspireOMSOConstants.OBS_TYPE_TRAJECTORY_OBSERVATION.equals(requestedObservationType) && observation.isSetSpatialFilteringProfileParameter() && checkForTrajectory(observation)) {
                    return InspireOMSOConstants.OBS_TYPE_TRAJECTORY_OBSERVATION;
                } 
            }
        } else {
            if (checkForObservationType(observation, InspireOMSOConstants.OBS_TYPE_TRAJECTORY_OBSERVATION)) {
                return InspireOMSOConstants.OBS_TYPE_TRAJECTORY_OBSERVATION;
            } else if (checkForObservationType(observation, InspireOMSOConstants.OBS_TYPE_PROFILE_OBSERVATION)) {
                return InspireOMSOConstants.OBS_TYPE_PROFILE_OBSERVATION;
            }  else if (checkForObservationType(observation, InspireOMSOConstants.OBS_TYPE_POINT_TIME_SERIES_OBSERVATION)) {
                return InspireOMSOConstants.OBS_TYPE_POINT_TIME_SERIES_OBSERVATION;
            } else if (checkForObservationType(observation, InspireOMSOConstants.OBS_TYPE_MULTI_POINT_OBSERVATION)) {
                return InspireOMSOConstants.OBS_TYPE_MULTI_POINT_OBSERVATION;
            }
//            if (observation.isSetHeightDepthParameter()) {
//                return InspireOMSOConstants.OBS_TYPE_PROFILE_OBSERVATION;
//            } else if (observation.isSetSpatialFilteringProfileParameter() && checkForTrajectory(observation)) {
//                return InspireOMSOConstants.OBS_TYPE_TRAJECTORY_OBSERVATION;
//            } 
        }
        // TODO default setting
        return InspireOMSOConstants.OBS_TYPE_POINT_OBSERVATION;
    }

    private boolean checkForTrajectory(OmObservation observation) {
        ContentCache cache = Configurator.getInstance().getCache();
        for (String offering : observation.getObservationConstellation().getOfferings()) {
            if (cache.getAllObservationTypesForOffering(offering).contains(InspireOMSOConstants.OBS_TYPE_TRAJECTORY_OBSERVATION)) {
                return true;
            }
        }
        return checkForObservationType(observation, InspireOMSOConstants.OBS_TYPE_TRAJECTORY_OBSERVATION);
//        Configurator.getInstance().getCache().getAllObservationTypesForOffering(observation.g)
//        NamedValue<Geometry> first = observations.get(0).getSpatialFilteringProfileParameter();
//        NamedValue<Geometry> second = observations.get(observations.size()/2).getSpatialFilteringProfileParameter();
//        return first.getValue().getValue().distance(second.getValue().getValue()) > 0.0001;
    }
    
    private boolean checkForObservationType(OmObservation observation, String observationType) {
        ContentCache cache = Configurator.getInstance().getCache();
        for (String offering : observation.getObservationConstellation().getOfferings()) {
            if (cache.getAllObservationTypesForOffering(offering).contains(observationType)) {
                return true;
            }
        }
        return false;
//        Configurator.getInstance().getCache().getAllObservationTypesForOffering(observation.g)
//        NamedValue<Geometry> first = observations.get(0).getSpatialFilteringProfileParameter();
//        NamedValue<Geometry> second = observations.get(observations.size()/2).getSpatialFilteringProfileParameter();
//        return first.getValue().getValue().distance(second.getValue().getValue()) > 0.0001;
    }

    private boolean checkRequestedObservationTypeForOffering(OmObservation observation, AbstractServiceRequest<?> request) {
        if (request instanceof AbstractObservationRequest && ((AbstractObservationRequest) request).isSetResultModel()) {
            String observationType = ((AbstractObservationRequest) request).getResultModel();
            return checkForObservationType(observation, observationType);
        }
        return true;
    }

    private void checkRequestedResultType(String resultType) throws CodedException {
        if (!getValidResultTypes().contains(resultType)) {
            throw new InvalidParameterValueException().at("resultType").withMessage(
                    "The requested resultType '%s' is not valid for the responseFormat '%s'", resultType,
                    InspireOMSOConstants.NS_OMSO_30);
        }
    }

    private Set<String> getValidResultTypes() {
        return Sets.newHashSet(InspireOMSOConstants.OBS_TYPE_POINT_OBSERVATION,
                InspireOMSOConstants.OBS_TYPE_POINT_TIME_SERIES_OBSERVATION,
                InspireOMSOConstants.OBS_TYPE_MULTI_POINT_OBSERVATION,
                InspireOMSOConstants.OBS_TYPE_PROFILE_OBSERVATION,
                InspireOMSOConstants.OBS_TYPE_TRAJECTORY_OBSERVATION);
    }

    private void putOrAdd(Map<String, List<OmObservation>> map, String type,
            List<OmObservation> observations) {
        if (CollectionHelper.isNotEmpty(observations)) {
            if (map.containsKey(type)) {
                map.get(type).addAll(observations);
            } else {
                map.put(type, observations);
            }
        }
    }

    @Override
    public RequestResponseModifierFacilitator getFacilitator() {
        return new RequestResponseModifierFacilitator().setMerger(true);
    }

}
