/**
 * Inicialização e configuração base do Leaflet.js
 */

const INITIAL_LAT = -23.56168;
const INITIAL_LNG = -46.65598;
const INITIAL_ZOOM = 15;

const map = L.map('map-viewport', {
    zoomControl: false,
    preferCanvas: true
}).setView([INITIAL_LAT, INITIAL_LNG], INITIAL_ZOOM);

// Mapa base definido pelo backend (CARTO com chave em produção; Stadia sem chave no localhost)
const FALLBACK_BASEMAP = {
    tileUrl: 'https://tiles.stadiamaps.com/tiles/alidade_smooth/{z}/{x}/{y}{r}.png',
    attribution: '&copy; <a href="https://stadiamaps.com/">Stadia Maps</a> &copy; <a href="https://openmaptiles.org/">OpenMapTiles</a> &copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors',
    maxZoom: 20
};

fetch('/api/config/mapa')
    .then(res => res.ok ? res.json() : FALLBACK_BASEMAP)
    .catch(() => FALLBACK_BASEMAP)
    .then(cfg => {
        L.tileLayer(cfg.tileUrl, { attribution: cfg.attribution, maxZoom: cfg.maxZoom || 20 }).addTo(map);
    });

map.createPane('pois');
map.getPane('pois').style.zIndex = 400;

map.createPane('support');
map.getPane('support').style.zIndex = 500;
map.on('moveend', () => {
    if (window.lucide) {
        window.lucide.createIcons();
    }
});
map.createPane('incidents');
map.getPane('incidents').style.zIndex = 600;

map.createPane('user');
map.getPane('user').style.zIndex = 700;

window.safeMap = window.safeMap || {};
window.safeMap.map = map;

/** "Bairro — Cidade, UF" a partir do retorno de getAddressFromCoords. */
window.safeMap.formatLocality = function(geoData) {
    if (!geoData) return '';
    const cityUf = [geoData.city, geoData.state].filter(Boolean).join(', ');
    return [geoData.district, cityUf].filter(Boolean).join(' — ');
};

window.safeMap.getAddressFromCoords = async function(lat, lng) {
    try {
        const response = await fetch(`https://geocode.arcgis.com/arcgis/rest/services/World/GeocodeServer/reverseGeocode?location=${lng},${lat}&f=json`);
        const data = await response.json();
        if (data && data.address) {
            let road = data.address.Address || '';
            let houseNumber = data.address.AddNum || '';
            let suburb = data.address.Neighborhood || data.address.District || '';
            
            const cleanCep = (data.address.Postal || "").replace(/\D/g, "");
            if (cleanCep && cleanCep.length === 8) {
                try {
                    const cepResponse = await fetch(`https://viacep.com.br/ws/${cleanCep}/json/`);
                    const cepData = await cepResponse.json();
                    if (cepData && cepData.bairro) {
                        suburb = cepData.bairro;
                    }
                } catch (cepErr) {
                    console.warn('Erro ao consultar bairro do CEP no ViaCEP:', cepErr);
                }
            }
            
            if (houseNumber && road.endsWith(" " + houseNumber)) {
                road = road.substring(0, road.length - (houseNumber.length + 1));
            }
            
            let addressStr = road;
            if (road && houseNumber) addressStr += `, ${houseNumber}`;
            
            return {
                street: addressStr || (data.address.Match_addr ? data.address.Match_addr.split(',')[0] : 'Endereço não encontrado'),
                district: suburb || '',
                city: data.address.City || '',
                state: data.address.RegionAbbr || ''
            };
        }
        return null;
    } catch (e) {
        console.warn('Erro no ArcGIS reverse geocoding, tentando Nominatim fallback:', e);
        try {
            const response = await fetch(`https://nominatim.openstreetmap.org/reverse?format=json&lat=${lat}&lon=${lng}&zoom=18&addressdetails=1`, {
                headers: {
                    'Accept-Language': 'pt-BR'
                }
            });
            const data = await response.json();
            if (data && data.address) {
                const road = data.address.road || data.address.pedestrian || data.address.square || '';
                const houseNumber = data.address.house_number || '';
                let suburb = data.address.suburb || data.address.neighbourhood || data.address.city_district || '';
                
                const cleanCep = (data.address.postcode || "").replace(/\D/g, "");
                if (cleanCep && cleanCep.length === 8) {
                    try {
                        const cepResponse = await fetch(`https://viacep.com.br/ws/${cleanCep}/json/`);
                        const cepData = await cepResponse.json();
                        if (cepData && cepData.bairro) {
                            suburb = cepData.bairro;
                        }
                    } catch (cepErr) {
                        console.warn('Erro ao consultar bairro do CEP no ViaCEP:', cepErr);
                    }
                }
                
                let addressStr = road;
                if (road && houseNumber) addressStr += `, ${houseNumber}`;
                
                return {
                    street: addressStr || (data.display_name ? data.display_name.split(',')[0] : 'Endereço não encontrado'),
                    district: suburb || '',
                    city: data.address.city || data.address.town || data.address.village || data.address.municipality || '',
                    state: window.safeMap.geo ? window.safeMap.geo.stateAbbr(data.address) : (data.address.state || '')
                };
            }
            return null;
        } catch (err2) {
            console.error('Nominatim reverse geocoding fallback error:', err2);
            return null;
        }
    }
};
