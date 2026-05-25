/**
 * SafeMap — Cliente Overpass API
 * Chama o proxy serverless /api/overpass para evitar CORS.
 * Mantém cache via sessionStorage e compatibilidade com bbox dinâmico.
 */

const DEFAULT_BBOX = "-23.6300,-46.7300,-23.5000,-46.5500";

function getOverpassEndpoint(query) {
    const isLocalhost = window.location.hostname === "localhost" || window.location.hostname === "127.0.0.1";

    if (isLocalhost) {
        return {
            url: `https://overpass-api.de/api/interpreter?data=${encodeURIComponent(query)}`,
            mode: "direct"
        };
    }
    return {
        url: `/api/overpass`,
        mode: "proxy"
    };
}

async function fetchOverpassPOIs(bbox = DEFAULT_BBOX) {
    const cacheKey = `safeMap_overpass_v2_${bbox.replace(/,/g, '_')}`;
    const cached = sessionStorage.getItem(cacheKey);

    if (cached) {
        console.log("[overpass] Cache hit para bbox:", bbox);
        return JSON.parse(cached);
    }

    const query = `[out:json][timeout:10];(node["amenity"="hospital"](${bbox});way["amenity"="hospital"](${bbox});node["amenity"="police"](${bbox});way["amenity"="police"](${bbox});node["social_facility"](${bbox});way["social_facility"](${bbox});node["amenity"="social_facility"](${bbox});way["amenity"="social_facility"](${bbox}););out center;`;

    let pois = [];
    let success = false;

    // Mirrors públicos do Overpass API para consulta direta do navegador (CORS-enabled)
    const mirrors = [
        `https://overpass-api.de/api/interpreter?data=${encodeURIComponent(query)}`,
        `https://overpass.kumi.systems/api/interpreter?data=${encodeURIComponent(query)}`,
        `https://overpass.openstreetmap.ru/api/interpreter?data=${encodeURIComponent(query)}`
    ];

    for (const url of mirrors) {
        try {
            const domain = new URL(url).hostname;
            console.log(`[overpass] Tentando buscar diretamente da API pública: ${domain}`);
            const response = await fetch(url);
            if (response.ok) {
                const data = await response.json();
                pois = (data.elements || []).filter(el => el.tags).map(el => {
                    if ((el.type === 'way' || el.type === 'relation') && el.center) {
                        return { ...el, lat: el.center.lat, lon: el.center.lon };
                    }
                    return el;
                });
                console.log(`[overpass] Sucesso via ${domain}: ${pois.length} POIs`);
                success = true;
                break; // Sai do loop se a requisição for bem-sucedida
            } else {
                console.warn(`[overpass] Mirror ${domain} retornou status ${response.status}`);
            }
        } catch (err) {
            console.warn(`[overpass] Erro ao consultar mirror:`, err.message);
        }
    }

    if (success && pois.length > 0) {
        sessionStorage.setItem(cacheKey, JSON.stringify(pois));
    }

    return pois;
}

window.safeMap = window.safeMap || {};
window.safeMap.fetchOverpassPOIs = fetchOverpassPOIs;
