/**
 * SafeMap — Cliente de pontos de apoio
 *
 * Os pontos (hospitais, delegacias, acolhimento) ficam no banco do SafeMap e são
 * atualizados pelo backend a partir do OpenStreetMap. O navegador consulta apenas
 * /api/apoio.
 *
 * As consultas são divididas em "tiles" fixos de 0.1° (~11 km), para que a
 * mesma região seja reaproveitada do cache (sessionStorage + HTTP).
 */

const POI_TILE_SIZE = 0.1;
const POI_CACHE_PREFIX = "safeMap_apoio_v1_";

function tileKey(i, j) {
    return `${i}_${j}`;
}

function tileBBox(i, j) {
    const south = (i * POI_TILE_SIZE).toFixed(1);
    const west = (j * POI_TILE_SIZE).toFixed(1);
    const north = ((i + 1) * POI_TILE_SIZE).toFixed(1);
    const east = ((j + 1) * POI_TILE_SIZE).toFixed(1);
    return `${south},${west},${north},${east}`;
}

/** Lista os tiles que cobrem o retângulo (sul, oeste, norte, leste). */
function tilesForBounds(south, west, north, east) {
    const tiles = [];
    const iMin = Math.floor(south / POI_TILE_SIZE);
    const iMax = Math.floor(north / POI_TILE_SIZE);
    const jMin = Math.floor(west / POI_TILE_SIZE);
    const jMax = Math.floor(east / POI_TILE_SIZE);
    for (let i = iMin; i <= iMax; i++) {
        for (let j = jMin; j <= jMax; j++) {
            tiles.push({ key: tileKey(i, j), bbox: tileBBox(i, j) });
        }
    }
    return tiles;
}

const TIPO_PARA_TAGS = {
    HOSPITAL: { amenity: 'hospital' },
    DELEGACIA: { amenity: 'police' },
    ACOLHIMENTO: { amenity: 'social_facility', social_facility: 'yes' }
};

/**
 * Converte a resposta de /api/apoio para o formato de "tags" do OpenStreetMap,
 * usado pelos marcadores (pois.js) e pela lista de recursos (apoio.html).
 */
function toPOI(p) {
    const tags = { ...(TIPO_PARA_TAGS[p.tipo] || {}) };
    if (p.nome) tags.name = p.nome;
    if (p.rua) tags['addr:street'] = p.rua;
    if (p.numero) tags['addr:housenumber'] = p.numero;
    if (p.bairro) tags['addr:suburb'] = p.bairro;
    if (p.cidade) tags['addr:city'] = p.cidade;
    if (p.uf) tags['addr:state'] = p.uf;
    if (p.telefone) tags.phone = p.telefone;
    if (p.horario) tags.opening_hours = p.horario;
    return { id: p.id, lat: p.latitude, lon: p.longitude, tags };
}

/**
 * Busca os POIs de um tile.
 * @returns {Promise<Array|null>} lista de POIs (pode ser vazia) ou null em caso de falha.
 */
async function fetchPOIsTile(tile) {
    const cacheKey = POI_CACHE_PREFIX + tile.key;
    try {
        const cached = sessionStorage.getItem(cacheKey);
        if (cached) return JSON.parse(cached);
    } catch (e) { /* sessionStorage indisponível */ }

    try {
        const response = await fetch(`/api/apoio?bbox=${tile.bbox}`);
        if (!response.ok) {
            console.warn(`[apoio] API retornou status ${response.status} para tile ${tile.key}`);
            return null;
        }
        const pois = (await response.json()).map(toPOI);
        try {
            sessionStorage.setItem(cacheKey, JSON.stringify(pois));
        } catch (e) { /* cota excedida ou indisponível */ }
        return pois;
    } catch (err) {
        console.warn(`[apoio] Erro ao consultar API:`, err.message);
        return null;
    }
}

/**
 * Busca os POIs dentro de um retângulo, tile a tile.
 * @param {(tile, pois) => void} [onTile] chamado a cada tile carregado com sucesso
 * @param {Set<string>} [skip] tiles já carregados que devem ser ignorados
 * @returns {Promise<{pois: Array, failed: boolean}>}
 */
async function fetchPOIsInBounds(south, west, north, east, onTile, skip) {
    const tiles = tilesForBounds(south, west, north, east).filter(t => !skip || !skip.has(t.key));
    const seen = new Set();
    const all = [];
    let failed = false;

    for (const tile of tiles) {
        const pois = await fetchPOIsTile(tile);
        if (pois === null) {
            failed = true;
            continue;
        }
        if (onTile) onTile(tile, pois);
        pois.forEach(p => {
            const id = p.id || `${p.lat}-${p.lon}`;
            if (!seen.has(id)) {
                seen.add(id);
                all.push(p);
            }
        });
    }
    return { pois: all, failed };
}

/** Busca os POIs num raio aproximado (em graus) ao redor de um ponto. */
function fetchPOIsAround(lat, lng, radius = 0.05) {
    return fetchPOIsInBounds(lat - radius, lng - radius, lat + radius, lng + radius);
}

window.safeMap = window.safeMap || {};
window.safeMap.poiTiles = { tilesForBounds };
window.safeMap.fetchPOIsInBounds = fetchPOIsInBounds;
window.safeMap.fetchPOIsAround = fetchPOIsAround;
