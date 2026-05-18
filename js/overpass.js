/**
 * SafeMap — Cliente Overpass API
 * Chama o proxy serverless /api/overpass para evitar CORS.
 * Mantém cache via sessionStorage e compatibilidade com bbox dinâmico.
 */

const DEFAULT_BBOX = "-23.6300,-46.7300,-23.5000,-46.5500";

/**
 * Detecta se estamos em produção (Vercel) ou dev local.
 * Em produção usa o proxy /api/overpass.
 * Em localhost usa a API direta (sem CORS issues).
 */
function getOverpassEndpoint(query) {
    const isLocalhost = window.location.hostname === "localhost" || window.location.hostname === "127.0.0.1";

    if (isLocalhost) {
        // Em dev local, chamar Overpass direto funciona (sem CORS)
        return {
            url: `https://overpass-api.de/api/interpreter?data=${encodeURIComponent(query)}`,
            mode: "direct"
        };
    }

    // Em produção, usar o proxy serverless da Vercel
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

    // Query otimizada: node+way ao invés de nwr, timeout reduzido
    const query = `[out:json][timeout:10];(node["amenity"="hospital"](${bbox});way["amenity"="hospital"](${bbox});node["amenity"="police"](${bbox});way["amenity"="police"](${bbox});node["social_facility"](${bbox});way["social_facility"](${bbox});node["amenity"="social_facility"](${bbox});way["amenity"="social_facility"](${bbox}););out center;`;

    const endpoint = getOverpassEndpoint(query);
    console.log(`[overpass] Modo: ${endpoint.mode} | bbox: ${bbox}`);

    try {
        let response;

        if (endpoint.mode === "proxy") {
            // Proxy serverless — envia bbox como query param
            response = await fetch(`${endpoint.url}?bbox=${encodeURIComponent(bbox)}`, {
                method: "GET",
                headers: { "Accept": "application/json" }
            });
        } else {
            // Chamada direta (localhost)
            response = await fetch(endpoint.url);
        }

        if (!response.ok) {
            throw new Error(`HTTP ${response.status}: ${response.statusText}`);
        }

        const data = await response.json();

        // O proxy já retorna { elements: [...] } normalizado
        // A chamada direta retorna { elements: [...] } do Overpass puro
        let pois;

        if (endpoint.mode === "proxy") {
            // Proxy já normalizou lat/lon
            pois = data.elements || [];
            console.log(`[overpass] Proxy retornou ${pois.length} POIs (via ${data.source || 'unknown'})`);
        } else {
            // Chamada direta — precisa normalizar ways/relations
            pois = (data.elements || []).filter(el => el.tags).map(el => {
                if ((el.type === 'way' || el.type === 'relation') && el.center) {
                    return { ...el, lat: el.center.lat, lon: el.center.lon };
                }
                return el;
            });
            console.log(`[overpass] Direto retornou ${pois.length} POIs`);
        }

        // Salvar no cache
        if (pois.length > 0) {
            sessionStorage.setItem(cacheKey, JSON.stringify(pois));
        }

        return pois;

    } catch (err) {
        console.error("[overpass] Falha ao buscar POIs:", err.message);
        return [];
    }
}

window.safeMap = window.safeMap || {};
window.safeMap.fetchOverpassPOIs = fetchOverpassPOIs;
