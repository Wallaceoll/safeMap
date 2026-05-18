/**
 * Vercel Serverless Proxy — Overpass API
 * Resolve CORS: o frontend chama /api/overpass e este endpoint
 * faz a requisição server-side para o Overpass, sem restrição de origem.
 *
 * Suporta fallback entre múltiplos mirrors.
 * Aceita GET (?bbox=...) ou POST (body JSON { bbox }).
 */

const MIRRORS = [
    "https://overpass-api.de/api/interpreter",
    "https://overpass.kumi.systems/api/interpreter",
    "https://overpass.openstreetmap.ru/api/interpreter"
];

const DEFAULT_BBOX = "-23.6300,-46.7300,-23.5000,-46.5500";
const QUERY_TIMEOUT = 10; // segundos — timeout da query Overpass
const FETCH_TIMEOUT = 15000; // ms — timeout do fetch HTTP

/**
 * Monta a query Overpass otimizada.
 * Usa "node" e "way" separados ao invés de "nwr" para melhor performance.
 */
function buildQuery(bbox) {
    return `[out:json][timeout:${QUERY_TIMEOUT}];(node["amenity"="hospital"](${bbox});way["amenity"="hospital"](${bbox});node["amenity"="police"](${bbox});way["amenity"="police"](${bbox});node["social_facility"](${bbox});way["social_facility"](${bbox});node["amenity"="social_facility"](${bbox});way["amenity"="social_facility"](${bbox}););out center;`;
}

/**
 * Valida formato do bbox: "lat,lng,lat,lng" com 4 números.
 */
function isValidBBox(bbox) {
    if (typeof bbox !== "string") return false;
    const parts = bbox.split(",");
    if (parts.length !== 4) return false;
    return parts.every(p => !isNaN(parseFloat(p)) && isFinite(p));
}

/**
 * Fetch com timeout usando AbortController.
 */
async function fetchWithTimeout(url, options, timeoutMs) {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), timeoutMs);

    try {
        const res = await fetch(url, { ...options, signal: controller.signal });
        clearTimeout(timer);
        return res;
    } catch (err) {
        clearTimeout(timer);
        throw err;
    }
}

module.exports = async function handler(req, res) {
    // CORS headers — permite qualquer origem (ou restrinja ao seu domínio)
    res.setHeader("Access-Control-Allow-Origin", "*");
    res.setHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
    res.setHeader("Access-Control-Allow-Headers", "Content-Type");

    // Preflight
    if (req.method === "OPTIONS") {
        return res.status(200).end();
    }

    // Extrair bbox do GET ou POST
    let bbox = DEFAULT_BBOX;

    if (req.method === "GET" && req.query && req.query.bbox) {
        bbox = req.query.bbox;
    } else if (req.method === "POST" && req.body && req.body.bbox) {
        bbox = req.body.bbox;
    }

    // Validar bbox
    if (!isValidBBox(bbox)) {
        console.error(`[overpass-proxy] bbox inválido: "${bbox}"`);
        return res.status(400).json({
            error: "Parâmetro bbox inválido. Formato esperado: lat,lng,lat,lng",
            received: bbox
        });
    }

    const query = buildQuery(bbox);
    console.log(`[overpass-proxy] bbox=${bbox} | query length=${query.length}`);

    // Tentar cada mirror em sequência
    for (let i = 0; i < MIRRORS.length; i++) {
        const mirror = MIRRORS[i];
        console.log(`[overpass-proxy] Tentando mirror ${i + 1}/${MIRRORS.length}: ${mirror}`);

        try {
            const response = await fetchWithTimeout(mirror, {
                method: "POST",
                headers: {
                    "Content-Type": "application/x-www-form-urlencoded",
                    "User-Agent": "SafeMap/1.0 (vercel-proxy)"
                },
                body: `data=${encodeURIComponent(query)}`
            }, FETCH_TIMEOUT);

            if (!response.ok) {
                console.warn(`[overpass-proxy] Mirror ${mirror} retornou status ${response.status}`);
                continue;
            }

            // Tentar parsear como JSON
            const text = await response.text();
            let data;
            try {
                data = JSON.parse(text);
            } catch (parseErr) {
                console.warn(`[overpass-proxy] Mirror ${mirror} retornou JSON inválido (${text.length} chars)`);
                continue;
            }

            // Validar estrutura mínima
            if (!data || !Array.isArray(data.elements)) {
                console.warn(`[overpass-proxy] Mirror ${mirror} retornou estrutura inválida`);
                continue;
            }

            // Normalizar: extrair lat/lon de ways/relations usando center
            const pois = data.elements
                .filter(el => el.tags)
                .map(el => {
                    if ((el.type === "way" || el.type === "relation") && el.center) {
                        return { ...el, lat: el.center.lat, lon: el.center.lon };
                    }
                    return el;
                })
                .filter(el => typeof el.lat === "number" && typeof el.lon === "number");

            console.log(`[overpass-proxy] Sucesso via ${mirror}: ${pois.length} POIs`);

            return res.status(200).json({
                source: mirror,
                count: pois.length,
                elements: pois
            });

        } catch (err) {
            const reason = err.name === "AbortError" ? "timeout" : err.message;
            console.warn(`[overpass-proxy] Falha no mirror ${mirror}: ${reason}`);
        }
    }

    // Todos os mirrors falharam
    console.error("[overpass-proxy] Todos os mirrors falharam.");
    return res.status(502).json({
        error: "Todos os mirrors do Overpass falharam. Tente novamente em alguns segundos.",
        mirrors: MIRRORS
    });
};
