/**
 * Gerenciamento de POIs do Overpass, Clusters e Ícones Customizados
 */

const hospitalLayer = L.markerClusterGroup({
    clusterPane: 'support',
    maxClusterRadius: 50,
    iconCreateFunction: function (cluster) {
        return L.divIcon({
            html: `<div style="background-color: #0EA5E9; color: white; border-radius: 50%; width: 30px; height: 30px; display: flex; align-items: center; justify-content: center; font-weight: bold; font-size: 14px; box-shadow: 0 2px 5px rgba(0,0,0,0.3);">${cluster.getChildCount()}</div>`,
            className: 'custom-cluster-icon',
            iconSize: L.point(30, 30)
        });
    }
});

const policeLayer = L.markerClusterGroup({
    clusterPane: 'support',
    maxClusterRadius: 50,
    iconCreateFunction: function (cluster) {
        return L.divIcon({
            html: `<div style="background-color: #0EA5E9; color: white; border-radius: 50%; width: 30px; height: 30px; display: flex; align-items: center; justify-content: center; font-weight: bold; font-size: 14px; box-shadow: 0 2px 5px rgba(0,0,0,0.3);">${cluster.getChildCount()}</div>`,
            className: 'custom-cluster-icon',
            iconSize: L.point(30, 30)
        });
    }
});

const shelterLayer = L.markerClusterGroup({
    clusterPane: 'support',
    maxClusterRadius: 50,
    iconCreateFunction: function (cluster) {
        return L.divIcon({
            html: `<div style="background-color: #0EA5E9; color: white; border-radius: 50%; width: 30px; height: 30px; display: flex; align-items: center; justify-content: center; font-weight: bold; font-size: 14px; box-shadow: 0 2px 5px rgba(0,0,0,0.3);">${cluster.getChildCount()}</div>`,
            className: 'custom-cluster-icon',
            iconSize: L.point(30, 30)
        });
    }
});

function createSupportIcon(iconName) {
    return L.divIcon({
        className: 'custom-support-marker',
        html: `
            <div class="support-shield" style="position: relative; pointer-events: none;">
                <div style="display: flex; align-items: center; justify-content: center; padding: 0; border-radius: 50%; width: 34px; height: 34px; background-color: #0EA5E9; box-shadow: 0 4px 6px rgba(0,0,0,0.1);">
                    <i data-lucide="${iconName}" size="18" style="color: white;"></i>
                </div>
            </div>
        `,
        iconSize: [34, 34],
        iconAnchor: [17, 17]
    });
}

const icons = {
    hospital: createSupportIcon('cross'),
    police: createSupportIcon('shield'),
    shelter: createSupportIcon('home')
};

async function loadPOIs() {
    const pois = await window.safeMap.fetchOverpassPOIs();

    pois.forEach(node => {
        const latlng = [node.lat, node.lon];
        const tags = node.tags;

        let layerToAdd = null;
        let iconToUse = null;
        let typeName = "";

        if (tags.amenity === 'hospital') {
            layerToAdd = hospitalLayer;
            iconToUse = icons.hospital;
            typeName = "Hospital";
        } else if (tags.amenity === 'police') {
            layerToAdd = policeLayer;
            iconToUse = icons.police;
            typeName = "Delegacia";
        } else if (tags.social_facility || tags.amenity === 'social_facility') {
            layerToAdd = shelterLayer;
            iconToUse = icons.shelter;
            typeName = "Acolhimento Social";
        }

        if (layerToAdd) {
            const marker = L.marker(latlng, {
                icon: iconToUse,
                pane: 'support'
            });

            marker.on('click', async () => {
                const supportBlock = document.getElementById('support-details');
                const incidentBlock = document.getElementById('incident-details');
                const detailsOverlay = document.getElementById('details-overlay');
                const addressEl = document.getElementById('support-address');
                
                document.getElementById('support-name').textContent = tags.name || typeName;
                
                let fallbackAddress = [];
                if (tags['addr:street']) fallbackAddress.push(tags['addr:street']);
                if (tags['addr:housenumber']) fallbackAddress.push(tags['addr:housenumber']);
                
                addressEl.textContent = fallbackAddress.length > 0 ? fallbackAddress.join(', ') : 'Buscando endereço exato...';
                document.getElementById('support-type').textContent = `Ponto de apoio: ${typeName}.`;

                incidentBlock.style.display = 'none';
                supportBlock.style.display = 'block';
                detailsOverlay.classList.add('active');

                // Configurar o botão "Ver local na lista de apoio"
                const howToGetBtn = document.getElementById('btn-view-on-support');
                if (howToGetBtn) {
                    howToGetBtn.onclick = (e) => {
                        e.preventDefault();
                        const name = tags.name || typeName;
                        console.log("Navegando para Apoio com busca:", name);
                        window.location.href = `apoio.html?search=${encodeURIComponent(name)}`;
                    };
                }

                if (window.safeMap.getAddressFromCoords) {
                    const geoData = await window.safeMap.getAddressFromCoords(latlng[0], latlng[1]);
                    if (geoData) {
                        let finalStreet = geoData.street;
                        
                        if (!finalStreet.includes(',') && tags['addr:housenumber']) {
                            finalStreet += `, ${tags['addr:housenumber']}`;
                        }
                        
                        addressEl.textContent = finalStreet + (geoData.district ? ` - ${geoData.district}` : '');
                    } else if (fallbackAddress.length === 0) {
                        addressEl.textContent = 'Endereço não informado';
                    }
                }
            });

            layerToAdd.addLayer(marker);
        }
    });

    // Só adiciona ao mapa se o filtro estiver ativo (o padrão é ativo)
    const supportActive = document.querySelector('.category-chip[data-type="support"].active');
    if (supportActive) {
        map.addLayer(hospitalLayer);
        map.addLayer(policeLayer);
        map.addLayer(shelterLayer);
    }
    
    // Garantir que os ícones do Lucide sejam renderizados quando o cluster abre ou move
    const updateIcons = () => window.lucide && window.lucide.createIcons();
    hospitalLayer.on('animationend', updateIcons);
    policeLayer.on('animationend', updateIcons);
    shelterLayer.on('animationend', updateIcons);
    
    // Também escutar quando novas camadas entram no mapa (útil quando sai do cluster)
    map.on('layeradd', (e) => {
        if (e.layer instanceof L.Marker && e.layer.options.pane === 'support') {
            updateIcons();
        }
    });
    
    console.log(`POIs carregados: Hospitais: ${hospitalLayer.getLayers().length}, Polícia: ${policeLayer.getLayers().length}, Acolhimento: ${shelterLayer.getLayers().length}`);
    
    if (window.lucide) {
        window.lucide.createIcons();
    }
}

loadPOIs();

window.safeMap.layers = window.safeMap.layers || {};
window.safeMap.layers.support = [hospitalLayer, policeLayer, shelterLayer];
