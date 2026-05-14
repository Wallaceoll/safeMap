/**
 * Integração com a Overpass API para buscar POIs reais (Hospitais, Delegacias, Acolhimento)
 */

const OVERPASS_URL = "https://overpass-api.de/api/interpreter";

// BBOX padrão de São Paulo se não for informado
const DEFAULT_BBOX = "-23.6300,-46.7300,-23.5000,-46.5500";

async function fetchOverpassPOIs(bbox = DEFAULT_BBOX) {
    const cacheKey = `safeMap_overpass_pois_${bbox.replace(/,/g, '_')}`;
    const cached = sessionStorage.getItem(cacheKey);

    if (cached) {
        console.log("Usando POIs do cache local.");
        return JSON.parse(cached);
    }

    const query = `[out:json][timeout:25];(nwr["amenity"="hospital"](${bbox});nwr["amenity"="police"](${bbox});nwr["social_facility"](${bbox});nwr["amenity"="social_facility"](${bbox}););out center;`;

    // Tentamos o servidor principal e um mirror se falhar
    const endpoints = [
        "https://overpass-api.de/api/interpreter",
        "https://overpass.kumi.systems/api/interpreter",
        "https://overpass.openstreetmap.ru/api/interpreter"
    ];

    for (const url of endpoints) {
        try {
            console.log(`Buscando POIs via ${url}...`);
            // Usar GET com o parâmetro 'data' é mais compatível com CORS em alguns ambientes
            const response = await fetch(`${url}?data=${encodeURIComponent(query)}`);

            if (!response.ok) continue;

            const data = await response.json();
            if (!data || !data.elements) continue;

            const pois = data.elements.filter(el => el.tags).map(el => {
                if (el.type === 'way' || el.type === 'relation') {
                    return { ...el, lat: el.center.lat, lon: el.center.lon };
                }
                return el;
            });

            sessionStorage.setItem(cacheKey, JSON.stringify(pois));
            return pois;
        } catch (err) {
            console.warn(`Falha no endpoint ${url}:`, err);
        }
    }

    console.error("Todos os endpoints do Overpass falharam (provavelmente CORS ou rede).");
    return [];
}

window.safeMap = window.safeMap || {};
window.safeMap.fetchOverpassPOIs = fetchOverpassPOIs;
