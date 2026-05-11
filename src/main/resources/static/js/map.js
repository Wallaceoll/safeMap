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

L.tileLayer('https://{s}.basemaps.cartocdn.com/light_all/{z}/{x}/{y}{r}.png', {
    attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors &copy; <a href="https://carto.com/attributions">CARTO</a>',
    subdomains: 'abcd',
    maxZoom: 20
}).addTo(map);

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

window.safeMap.getAddressFromCoords = async function(lat, lng) {
    try {
        const response = await fetch(`https://nominatim.openstreetmap.org/reverse?format=json&lat=${lat}&lon=${lng}&zoom=18&addressdetails=1`, {
            headers: {
                'Accept-Language': 'pt-BR',
                'User-Agent': 'SafeMap/1.0'
            }
        });
        const data = await response.json();
        if (data && data.address) {
            const road = data.address.road || data.address.pedestrian || data.address.square || '';
            const houseNumber = data.address.house_number || '';
            const suburb = data.address.suburb || data.address.neighbourhood || data.address.city_district || '';
            
            let addressStr = road;
            if (road && houseNumber) addressStr += `, ${houseNumber}`;
            
            return {
                street: addressStr || (data.display_name ? data.display_name.split(',')[0] : 'Endereço não encontrado'),
                district: suburb || 'São Paulo'
            };
        }
        return null;
    } catch (e) {
        console.error('Reverse geocoding error:', e);
        return null;
    }
};
