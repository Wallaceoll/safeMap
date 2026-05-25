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

let currentBBox = null;
let isFetchingPOIs = false;

async function loadPOIs(forcedBBox = null) {
    if (isFetchingPOIs) return;

    let bbox = forcedBBox;
    if (!bbox && window.safeMap.map) {
        const bounds = window.safeMap.map.getBounds();
        const sw = bounds.getSouthWest();
        const ne = bounds.getNorthEast();
        bbox = `${sw.lat.toFixed(4)},${sw.lng.toFixed(4)},${ne.lat.toFixed(4)},${ne.lng.toFixed(4)}`;
    }

    if (!forcedBBox && currentBBox === bbox) return;
    currentBBox = bbox;

    isFetchingPOIs = true;
    const pois = await window.safeMap.fetchOverpassPOIs(bbox);
    isFetchingPOIs = false;

    if (pois.length === 0 && hospitalLayer.getLayers().length === 0) {
        console.warn("Usando pontos de apoio de fallback devido a falha na API.");
        pois.push(
            { lat: -23.5670, lon: -46.6480, tags: { name: "Hospital Santa Catarina", amenity: "hospital" } },
            { lat: -23.5630, lon: -46.6540, tags: { name: "78º DP - Jardins", amenity: "police" } },
            { lat: -23.5580, lon: -46.6610, tags: { name: "Centro de Apoio", social_facility: "shelter" } }
        );
    }

    const existingIds = new Set();
    [hospitalLayer, policeLayer, shelterLayer].forEach(l => {
        l.getLayers().forEach(m => { if (m._osmId) existingIds.add(m._osmId) });
    });

    pois.forEach(node => {
        const id = node.id || `${node.lat}-${node.lon}`;
        if (existingIds.has(id)) return;

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
            marker._osmId = id;

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

                const howToGetBtn = document.getElementById('btn-view-on-support');
                if (howToGetBtn) {
                    howToGetBtn.onclick = (e) => {
                        e.preventDefault();
                        const name = tags.name || typeName;
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
                    }
                }
            });

            layerToAdd.addLayer(marker);
        }
    });

    const supportActive = document.querySelector('.category-chip[data-type="support"].active');
    if (supportActive) {
        if (!map.hasLayer(hospitalLayer)) map.addLayer(hospitalLayer);
        if (!map.hasLayer(policeLayer)) map.addLayer(policeLayer);
        if (!map.hasLayer(shelterLayer)) map.addLayer(shelterLayer);
    }

    const updateIcons = () => window.lucide && window.lucide.createIcons();
    hospitalLayer.on('animationend', updateIcons);
    policeLayer.on('animationend', updateIcons);
    shelterLayer.on('animationend', updateIcons);

    map.on('layeradd', (e) => {
        if (e.layer instanceof L.Marker && e.layer.options.pane === 'support') {
            updateIcons();
        }
    });

    if (window.lucide) window.lucide.createIcons();
}

let moveTimeout;
map.on('moveend', () => {
    clearTimeout(moveTimeout);
    moveTimeout = setTimeout(() => loadPOIs(), 1000);
});

loadPOIs();

window.safeMap.layers = window.safeMap.layers || {};
window.safeMap.layers.support = [hospitalLayer, policeLayer, shelterLayer];
