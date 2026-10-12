/**
 * Gerenciamento de POIs do Overpass (pontos de apoio) e Ícones Customizados
 *
 * Funciona em qualquer região do Brasil: os pontos são carregados por tiles
 * conforme o usuário navega, a partir de um zoom mínimo.
 */

const hospitalLayer = L.layerGroup();
const policeLayer = L.layerGroup();
const shelterLayer = L.layerGroup();

/** Abaixo deste zoom a área visível é grande demais para consultar a Overpass. */
const POI_MIN_ZOOM = 14;
/** Acima deste total, marcadores de tiles fora da tela são descartados. */
const POI_MAX_MARKERS = 600;

function createSupportIcon(iconName) {
    return L.divIcon({
        className: 'custom-support-marker',
        html: `
            <div class="support-shield" style="position: absolute; top: 50%; left: 50%; pointer-events: none;">
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

const loadedTiles = new Set();
const markersByTile = new Map();
const markerIds = new Set();
let markerCount = 0;
let isFetchingPOIs = false;
let pendingPOIReload = false;
let poiRetryTimer = null;
/** Espera antes de tentar de novo os tiles que falharam (a Overpass pública oscila bastante). */
const POI_RETRY_DELAY_MS = 15000;

function isSupportFilterActive() {
    return !!document.querySelector('.category-chip[data-type="support"].active');
}

// --------------------------------------------------------
//  Aviso discreto sobre o estado dos pontos de apoio
// --------------------------------------------------------

let poiNotice = null;

function showPOINotice(text) {
    if (!isSupportFilterActive()) {
        hidePOINotice();
        return;
    }
    if (!poiNotice) {
        poiNotice = document.createElement('div');
        poiNotice.setAttribute('role', 'status');
        poiNotice.style.cssText = "position: fixed; bottom: 150px; left: 50%; transform: translateX(-50%); background: rgba(17,24,39,0.85); color: white; padding: 8px 16px; border-radius: 16px; font-size: 12px; line-height: 1.4; z-index: 900; pointer-events: none; width: max-content; max-width: min(280px, calc(100% - 32px)); text-align: center;";
        document.body.appendChild(poiNotice);
    }
    poiNotice.textContent = text;
    poiNotice.style.display = 'block';
}

function hidePOINotice() {
    if (poiNotice) poiNotice.style.display = 'none';
}

// --------------------------------------------------------
//  Marcadores
// --------------------------------------------------------

function classifyPOI(tags) {
    if (tags.amenity === 'hospital') return { layer: hospitalLayer, icon: icons.hospital, typeName: "Hospital" };
    if (tags.amenity === 'police') return { layer: policeLayer, icon: icons.police, typeName: "Delegacia" };
    if (tags.social_facility || tags.amenity === 'social_facility') {
        return { layer: shelterLayer, icon: icons.shelter, typeName: "Acolhimento Social" };
    }
    return null;
}

function createPOIMarker(node) {
    const tags = node.tags;
    const kind = classifyPOI(tags);
    if (!kind) return null;

    const latlng = [node.lat, node.lon];
    const typeName = kind.typeName;
    const marker = L.marker(latlng, { icon: kind.icon, pane: 'support' });

    marker.on('click', async () => {
        const supportBlock = document.getElementById('support-details');
        const incidentBlock = document.getElementById('incident-details');
        const detailsOverlay = document.getElementById('details-overlay');
        const addressEl = document.getElementById('support-address');

        document.getElementById('support-name').textContent = tags.name || typeName;

        const storedStreet = [tags['addr:street'], tags['addr:housenumber']].filter(Boolean).join(', ');
        const storedLocality = window.safeMap.formatLocality({
            district: tags['addr:suburb'], city: tags['addr:city'], state: tags['addr:state']
        });

        addressEl.textContent = storedStreet
            ? [storedStreet, storedLocality].filter(Boolean).join(' - ')
            : 'Buscando endereço exato...';
        document.getElementById('support-type').textContent = `Ponto de apoio: ${typeName}.`;

        incidentBlock.style.display = 'none';
        supportBlock.style.display = 'block';
        detailsOverlay.classList.add('active');

        const howToGetBtn = document.getElementById('btn-view-on-support');
        if (howToGetBtn) {
            howToGetBtn.onclick = (e) => {
                e.preventDefault();
                const name = tags.name || typeName;
                window.location.href = `apoio.html?search=${encodeURIComponent(name)}&lat=${node.lat}&lng=${node.lon}`;
            };
        }

        // Sem endereço cadastrado: estima pela coordenada
        if (!storedStreet && window.safeMap.getAddressFromCoords) {
            const geoData = await window.safeMap.getAddressFromCoords(latlng[0], latlng[1]);
            if (geoData) {
                const locality = window.safeMap.formatLocality(geoData) || storedLocality;
                addressEl.textContent = locality ? `${geoData.street} - ${locality}` : geoData.street;
            } else {
                addressEl.textContent = storedLocality || 'Endereço não disponível';
            }
        }
    });

    marker._poiLayer = kind.layer;
    return marker;
}

function addTileMarkers(tile, pois) {
    loadedTiles.add(tile.key);
    const markers = [];

    pois.forEach(node => {
        const id = node.id || `${node.lat}-${node.lon}`;
        if (markerIds.has(id)) return;

        const marker = createPOIMarker(node);
        if (!marker) return;

        marker._osmId = id;
        markerIds.add(id);
        marker._poiLayer.addLayer(marker);
        markers.push(marker);
    });

    markersByTile.set(tile.key, markers);
    markerCount += markers.length;
}

/** Remove marcadores de tiles fora da tela quando há marcadores demais. */
function pruneDistantTiles(visibleKeys) {
    if (markerCount <= POI_MAX_MARKERS) return;

    for (const [key, markers] of markersByTile) {
        if (visibleKeys.has(key)) continue;
        markers.forEach(m => {
            m._poiLayer.removeLayer(m);
            markerIds.delete(m._osmId);
        });
        markerCount -= markers.length;
        markersByTile.delete(key);
        loadedTiles.delete(key); // continua no cache; recarrega rápido se o usuário voltar
    }
}

// --------------------------------------------------------
//  Carregamento
// --------------------------------------------------------

async function loadPOIs() {
    if (isFetchingPOIs) {
        pendingPOIReload = true;
        return;
    }

    if (map.getZoom() < POI_MIN_ZOOM) {
        showPOINotice('Aproxime o mapa para ver os pontos de apoio.');
        return;
    }

    const bounds = map.getBounds();
    const south = bounds.getSouth(), west = bounds.getWest();
    const north = bounds.getNorth(), east = bounds.getEast();

    const visibleKeys = new Set(window.safeMap.poiTiles.tilesForBounds(south, west, north, east).map(t => t.key));
    pruneDistantTiles(visibleKeys);

    if ([...visibleKeys].every(k => loadedTiles.has(k))) {
        hidePOINotice();
        return;
    }

    isFetchingPOIs = true;
    try {
        const { failed } = await window.safeMap.fetchPOIsInBounds(south, west, north, east, addTileMarkers, loadedTiles);
        if (failed) {
            console.warn("[pois] Alguns pontos de apoio não puderam ser carregados.");
            showPOINotice('Não foi possível carregar todos os pontos de apoio. Tentaremos novamente.');
            clearTimeout(poiRetryTimer);
            poiRetryTimer = setTimeout(loadPOIs, POI_RETRY_DELAY_MS);
        } else {
            hidePOINotice();
        }
    } finally {
        isFetchingPOIs = false;
    }

    if (isSupportFilterActive()) {
        [hospitalLayer, policeLayer, shelterLayer].forEach(l => {
            if (!map.hasLayer(l)) map.addLayer(l);
        });
    }
    if (window.lucide) window.lucide.createIcons();

    if (pendingPOIReload) {
        pendingPOIReload = false;
        loadPOIs();
    }
}

let iconRefreshScheduled = false;
map.on('layeradd', (e) => {
    if (e.layer instanceof L.Marker && e.layer.options.pane === 'support' && window.lucide && !iconRefreshScheduled) {
        iconRefreshScheduled = true;
        requestAnimationFrame(() => {
            iconRefreshScheduled = false;
            window.lucide.createIcons();
        });
    }
});

let moveTimeout;
map.on('moveend', () => {
    clearTimeout(moveTimeout);
    moveTimeout = setTimeout(loadPOIs, 600);
});

document.querySelectorAll('.category-chip[data-type="support"]').forEach(chip => {
    chip.addEventListener('click', () => setTimeout(() => {
        if (isSupportFilterActive()) loadPOIs();
        else hidePOINotice();
    }, 0));
});

loadPOIs();

window.safeMap.layers = window.safeMap.layers || {};
window.safeMap.layers.support = [hospitalLayer, policeLayer, shelterLayer];
