package com.safemap.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.safemap.model.Ocorrencia;
import com.safemap.model.TipoOcorrencia;
import com.safemap.repository.OcorrenciaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RoutingService {

    private final OcorrenciaRepository ocorrenciaRepository;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @SuppressWarnings("unchecked")
    public Map<String, Object> calculateRoutes(double originLat, double originLng, double destLat, double destLng) {
        try {
            String directCoords = String.format(java.util.Locale.US, "%f,%f;%f,%f", originLng, originLat, destLng, destLat);
            Map<String, Object> responseMap = fetchOsrmRoute(directCoords);
            if (responseMap == null) {
                throw new RuntimeException("Falha ao consultar serviço de rotas externo (OSRM).");
            }

            List<Map<String, Object>> routes = (List<Map<String, Object>>) responseMap.get("routes");
            if (routes == null || routes.isEmpty()) {
                return responseMap;
            }

            routes = new ArrayList<>(routes);

            List<Ocorrencia> activeIncidents = ocorrenciaRepository.findAllForHeatmap();

            Map<String, Object> primaryRoute = routes.get(0);
            Map<String, Object> primaryGeometry = (Map<String, Object>) primaryRoute.get("geometry");
            List<List<Double>> primaryCoords = primaryGeometry != null ? (List<List<Double>>) primaryGeometry.get("coordinates") : null;
            double directRisk = calculateRouteRisk(primaryCoords, activeIncidents);
            primaryRoute.put("riskScore", directRisk);

            if (directRisk > 0.0 && primaryCoords != null) {
                Ocorrencia nearestDanger = findFirstIntersectingIncident(primaryCoords, activeIncidents);
                if (nearestDanger != null) {
                    double dangerLat = nearestDanger.getLatitude();
                    double dangerLng = nearestDanger.getLongitude();

                    double dLat = destLat - originLat;
                    double dLng = destLng - originLng;
                    double len = Math.sqrt(dLat * dLat + dLng * dLng);

                    if (len > 0.0) {
                        double pLat = -dLng / len;
                        double pLng = dLat / len;

                        double offsetLat = pLat * 0.005;
                        double offsetLng = (pLng * 0.005) / Math.cos(Math.toRadians(dangerLat));

                        double w1Lat = dangerLat + offsetLat;
                        double w1Lng = dangerLng + offsetLng;
                        double w2Lat = dangerLat - offsetLat;
                        double w2Lng = dangerLng - offsetLng;

                        Map<String, Object> r1Response = fetchOsrmRoute(
                                String.format(java.util.Locale.US, "%f,%f;%f,%f;%f,%f", originLng, originLat, w1Lng, w1Lat, destLng, destLat)
                        );
                        Map<String, Object> r2Response = fetchOsrmRoute(
                                String.format(java.util.Locale.US, "%f,%f;%f,%f;%f,%f", originLng, originLat, w2Lng, w2Lat, destLng, destLat)
                        );

                        evaluateAndAddBypassRoute(routes, r1Response, activeIncidents, primaryRoute);
                        evaluateAndAddBypassRoute(routes, r2Response, activeIncidents, primaryRoute);
                    }
                }
            }

            for (int i = 1; i < routes.size(); i++) {
                Map<String, Object> route = routes.get(i);
                if (route.get("riskScore") == null) {
                    Map<String, Object> geometry = (Map<String, Object>) route.get("geometry");
                    if (geometry != null) {
                        List<List<Double>> coords = (List<List<Double>>) geometry.get("coordinates");
                        route.put("riskScore", calculateRouteRisk(coords, activeIncidents));
                    } else {
                        route.put("riskScore", 0.0);
                    }
                }
            }

            responseMap.put("routes", routes);
            return responseMap;

        } catch (Exception e) {
            throw new RuntimeException("Erro ao processar roteamento seguro: " + e.getMessage(), e);
        }
    }

    private Map<String, Object> fetchOsrmRoute(String coordsQuery) {
        String url = "https://router.project-osrm.org/route/v1/foot/" + coordsQuery + "?overview=full&geometries=geojson&alternatives=true";
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Accept", "application/json")
                    .header("User-Agent", "SafeMap/1.0")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return objectMapper.readValue(response.body(), new TypeReference<Map<String, Object>>() {});
            }
        } catch (Exception e) {
            System.err.println("Erro ao chamar OSRM para query " + coordsQuery + ": " + e.getMessage());
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private void evaluateAndAddBypassRoute(List<Map<String, Object>> routes, Map<String, Object> osrmResponse, List<Ocorrencia> incidents, Map<String, Object> primaryRoute) {
        if (osrmResponse == null) return;
        List<Map<String, Object>> bypassRoutes = (List<Map<String, Object>>) osrmResponse.get("routes");
        if (bypassRoutes == null || bypassRoutes.isEmpty()) return;

        Map<String, Object> candidate = bypassRoutes.get(0);
        Map<String, Object> geometry = (Map<String, Object>) candidate.get("geometry");
        if (geometry == null) return;

        List<List<Double>> coords = (List<List<Double>>) geometry.get("coordinates");
        double riskScore = calculateRouteRisk(coords, incidents);

        double directDistance = ((Number) primaryRoute.get("distance")).doubleValue();
        double bypassDistance = ((Number) candidate.get("distance")).doubleValue();

        if (bypassDistance < directDistance * 2.5) {
            candidate.put("riskScore", riskScore);
            routes.add(candidate);
        }
    }

    private Ocorrencia findFirstIntersectingIncident(List<List<Double>> coordinates, List<Ocorrencia> incidents) {
        for (List<Double> coord : coordinates) {
            if (coord.size() < 2) continue;
            double lon = coord.get(0);
            double lat = coord.get(1);

            for (Ocorrencia incident : incidents) {
                if (incident.getLatitude() == null || incident.getLongitude() == null) continue;
                if (!isHighRisk(incident.getTipoOcorrencia())) continue;
                double distance = calculateDistance(lat, lon, incident.getLatitude(), incident.getLongitude());
                if (distance <= 0.25) {
                    return incident;
                }
            }
        }

        for (List<Double> coord : coordinates) {
            if (coord.size() < 2) continue;
            double lon = coord.get(0);
            double lat = coord.get(1);

            for (Ocorrencia incident : incidents) {
                if (incident.getLatitude() == null || incident.getLongitude() == null) continue;
                double distance = calculateDistance(lat, lon, incident.getLatitude(), incident.getLongitude());
                if (distance <= 0.25) {
                    return incident;
                }
            }
        }
        return null;
    }

    private double calculateRouteRisk(List<List<Double>> coordinates, List<Ocorrencia> incidents) {
        if (coordinates == null || coordinates.isEmpty() || incidents == null || incidents.isEmpty()) {
            return 0.0;
        }

        int uniqueNearIncidentsCount = 0;

        for (Ocorrencia incident : incidents) {
            if (incident.getLatitude() == null || incident.getLongitude() == null) continue;
            double incidentLat = incident.getLatitude();
            double incidentLng = incident.getLongitude();

            boolean isNear = false;
            for (List<Double> coord : coordinates) {
                if (coord.size() < 2) continue;
                double lon = coord.get(0);
                double lat = coord.get(1);

                double distance = calculateDistance(lat, lon, incidentLat, incidentLng);
                if (distance <= 0.25) {
                    isNear = true;
                    break;
                }
            }

            if (isNear) {
                uniqueNearIncidentsCount++;
            }
        }

        return (double) uniqueNearIncidentsCount;
    }

    private boolean isHighRisk(TipoOcorrencia tipo) {
        return tipo == TipoOcorrencia.ASSEDIO ||
               tipo == TipoOcorrencia.IMPORTUNACAO_SEXUAL ||
               tipo == TipoOcorrencia.AGRESSAO_FISICA ||
               tipo == TipoOcorrencia.AGRESSAO_VERBAL;
    }

    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        double earthRadius = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                   Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                   Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return earthRadius * c;
    }
}
