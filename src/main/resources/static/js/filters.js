/**
 * Gerenciamento de filtros e camadas do mapa
 */

document.querySelectorAll('.category-chip').forEach(chip => {
    chip.addEventListener('click', () => {
        chip.classList.toggle('active');

        const anyIncidentActive = document.querySelector('.category-chip[data-type="incidents"].active');
        const supportActive = document.querySelector('.category-chip[data-type="support"].active');

        const incidentLayer = window.safeMap.layers.incidents;
        const riskZoneLayer = window.safeMap.layers.riskZones;
        const supportLayers = window.safeMap.layers.support || [];

        if (anyIncidentActive) {
            if (incidentLayer && !map.hasLayer(incidentLayer)) {
                map.addLayer(incidentLayer);
            }
            if (riskZoneLayer && !map.hasLayer(riskZoneLayer)) {
                map.addLayer(riskZoneLayer);
            }
            if (window.safeMap.renderIncidents) window.safeMap.renderIncidents();
        } else {
            if (incidentLayer && map.hasLayer(incidentLayer)) {
                map.removeLayer(incidentLayer);
            }
            if (riskZoneLayer && map.hasLayer(riskZoneLayer)) {
                map.removeLayer(riskZoneLayer);
            }
        }

        supportLayers.forEach(layer => {
            if (supportActive) {
                if (!map.hasLayer(layer)) {
                    map.addLayer(layer);
                }
            } else {
                if (map.hasLayer(layer)) {
                    map.removeLayer(layer);
                }
            }
        });

        if (window.lucide) {
            setTimeout(() => window.lucide.createIcons(), 50);
        }
    });
});