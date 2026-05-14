/**
 * Gerenciamento de filtros e camadas do mapa
 */

document.querySelectorAll('.category-chip').forEach(chip => {
    chip.addEventListener('click', () => {
        chip.classList.toggle('active');

        const anyIncidentActive = document.querySelector('.category-chip[data-type="incidents"].active');
        const supportActive = document.querySelector('.category-chip[data-type="support"].active');

        const incidentLayer = window.safeMap.layers.incidents;
        const supportLayers = window.safeMap.layers.support || [];

        if (anyIncidentActive) {
            if (!map.hasLayer(incidentLayer)) {
                map.addLayer(incidentLayer);
            }
            if (window.safeMap.renderIncidents) window.safeMap.renderIncidents();
        } else {
            if (map.hasLayer(incidentLayer)) {
                map.removeLayer(incidentLayer);
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

        // Garantir ícones atualizados após mudanças de camada
        if (window.lucide) {
            setTimeout(() => window.lucide.createIcons(), 50);
        }
    });
});
