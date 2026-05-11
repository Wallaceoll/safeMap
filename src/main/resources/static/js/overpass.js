/**
 * Integração com a Overpass API para buscar POIs reais (Hospitais, Delegacias, Acolhimento)
 */

const SP_BBOX = "-23.6300,-46.7300,-23.5000,-46.5500";

const OVERPASS_URL = "https://overpass-api.de/api/interpreter";

const QUERY = `
    [out:json][timeout:25];
    (
      nwr["amenity"="hospital"](${SP_BBOX});
      nwr["amenity"="police"](${SP_BBOX});
      nwr["social_facility"](${SP_BBOX});
      nwr["amenity"="social_facility"](${SP_BBOX});
    );
    out center;
`;

async function fetchOverpassPOIs() {
    const cacheKey = 'safeMap_overpass_pois_cache_v3';
    const cached = sessionStorage.getItem(cacheKey);

    if (cached) {
        console.log("Usando POIs do cache local.");
        return JSON.parse(cached);
    }

    try {
        console.log("Buscando POIs reais no OpenStreetMap via Overpass...");
        const response = await fetch(OVERPASS_URL, {
            method: 'POST',
            body: 'data=' + encodeURIComponent(QUERY),
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded',
                'Accept': 'application/json',
                'User-Agent': 'SafeMap/1.0 (prototype)'
            }
        });

        if (!response.ok) {
            throw new Error(`Overpass API error: ${response.status}`);
        }

        const data = await response.json();

        const pois = data.elements.filter(el => el.tags).map(el => {
            if (el.type === 'way' || el.type === 'relation') {
                return { ...el, lat: el.center.lat, lon: el.center.lon };
            }
            return el;
        });
        
        sessionStorage.setItem(cacheKey, JSON.stringify(pois));
        return pois;
    } catch (err) {
        console.error("Falha ao buscar dados do Overpass:", err);
        return [];
    }
}

window.safeMap = window.safeMap || {};
window.safeMap.fetchOverpassPOIs = fetchOverpassPOIs;
