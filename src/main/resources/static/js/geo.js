/**
 * SafeMap — Geocodificação compartilhada (Brasil inteiro)
 *
 * - searchAddress: busca endereços em todo o Brasil pelo backend (/api/geocode, Openrouteservice),
 *   priorizando resultados próximos ao mapa/usuário. Se o backend não tiver a chave
 *   configurada (ex.: desenvolvimento local), usa Nominatim + ArcGIS diretamente.
 * - formatAddress: "Rua, Nº - Bairro, Cidade - UF".
 * - isInBrazil: validação geográfica (bounding box do Brasil).
 */
(function () {
    const BRAZIL_BOUNDS = { minLat: -33.76, maxLat: 5.28, minLng: -74.00, maxLng: -28.80 };

    const UF_BY_STATE = {
        "acre": "AC", "alagoas": "AL", "amapá": "AP", "amazonas": "AM", "bahia": "BA",
        "ceará": "CE", "distrito federal": "DF", "espírito santo": "ES", "goiás": "GO",
        "maranhão": "MA", "mato grosso": "MT", "mato grosso do sul": "MS", "minas gerais": "MG",
        "pará": "PA", "paraíba": "PB", "paraná": "PR", "pernambuco": "PE", "piauí": "PI",
        "rio de janeiro": "RJ", "rio grande do norte": "RN", "rio grande do sul": "RS",
        "rondônia": "RO", "roraima": "RR", "santa catarina": "SC", "são paulo": "SP",
        "sergipe": "SE", "tocantins": "TO"
    };

    // Resultados amplos demais para servir de endereço
    const BROAD_TYPES = ["state", "country", "region"];
    const CITY_TYPES = ["administrative", "city", "town", "municipality", "village"];

    function isInBrazil(lat, lng) {
        return lat >= BRAZIL_BOUNDS.minLat && lat <= BRAZIL_BOUNDS.maxLat &&
               lng >= BRAZIL_BOUNDS.minLng && lng <= BRAZIL_BOUNDS.maxLng;
    }

    function stateAbbr(addr) {
        if (!addr) return "";
        const iso = addr["ISO3166-2-lvl4"];
        if (iso && iso.startsWith("BR-")) return iso.substring(3);
        const state = (addr.state || "").trim();
        if (state.length === 2) return state.toUpperCase();
        return UF_BY_STATE[state.toLowerCase()] || state;
    }

    function cityOf(addr) {
        if (!addr) return "";
        return addr.city || addr.town || addr.village || addr.municipality || "";
    }

    function suburbOf(addr) {
        if (!addr) return "";
        return addr.suburb || addr.neighbourhood || addr.city_district || addr.quarter ||
               addr.subdivision || addr.residential || "";
    }

    function formatAddress(item, typedNumber = null) {
        if (!item || !item.address) return item ? (item.display_name || "") : "";
        const addr = item.address;
        const street = addr.road || addr.pedestrian || addr.construction || addr.path || "";
        const number = addr.house_number || addr.building || typedNumber || "";
        const numberStr = number ? `, ${number}` : "";

        const suburb = suburbOf(addr);
        const city = cityOf(addr);
        const uf = stateAbbr(addr);

        const head = `${street || (item.display_name || "").split(',')[0]}${numberStr}`;
        const left = suburb ? `${head} - ${suburb}` : head;
        const locality = [city, uf].filter(Boolean).join(' - ');
        return [left, locality].filter(Boolean).join(', ');
    }

    /** Rótulo curto para listas de sugestões: "Rua X - Bairro, Cidade - UF". */
    function shortLabel(item) {
        const addr = item.address || {};
        const road = addr.road || addr.pedestrian || (item.display_name || "").split(',')[0];
        const suburb = suburbOf(addr);
        const locality = [cityOf(addr), stateAbbr(addr)].filter(Boolean).join(' - ');
        return [suburb ? `${road} - ${suburb}` : road, locality].filter(Boolean).join(', ');
    }

    function getBiasPoint(near) {
        if (near && typeof near.lat === "number") return near;
        const map = window.safeMap && window.safeMap.map;
        if (map) {
            const c = map.getCenter();
            return { lat: c.lat, lng: c.lng };
        }
        return null;
    }

    function isAcceptable(item, allowCities) {
        if (!item) return false;
        const lat = parseFloat(item.lat);
        const lon = parseFloat(item.lon);
        if (!isInBrazil(lat, lon)) return false;

        const country = item.address && item.address.country_code;
        if (country && country.toLowerCase() !== "br") return false;

        if (BROAD_TYPES.includes(item.type) || item.addresstype === "state" || item.addresstype === "country") {
            return false;
        }
        if (!allowCities && (item.class === "boundary" || item.class === "place") && CITY_TYPES.includes(item.type)) {
            return false;
        }
        return true;
    }

    /** @returns {Promise<Array|null>} resultados, ou null se o backend estiver indisponível */
    async function backendSearch(query, bias, autocomplete) {
        let url = `/api/geocode/busca?q=${encodeURIComponent(query)}&autocomplete=${autocomplete}`;
        if (bias) url += `&lat=${bias.lat.toFixed(4)}&lng=${bias.lng.toFixed(4)}`;
        try {
            const res = await fetch(url);
            if (!res.ok) return null;
            const data = await res.json();
            return Array.isArray(data) ? data : null;
        } catch (e) {
            console.warn("[geo] Backend de busca indisponível:", e.message);
            return null;
        }
    }

    async function nominatimSearch(query, bias) {
        let url = `https://nominatim.openstreetmap.org/search?format=json&q=${encodeURIComponent(query)}` +
                  `&limit=10&countrycodes=br&addressdetails=1`;
        if (bias) {
            // Prioriza (sem restringir) resultados próximos ao mapa/usuário
            const d = 0.5;
            url += `&viewbox=${bias.lng - d},${bias.lat + d},${bias.lng + d},${bias.lat - d}&bounded=0`;
        }
        try {
            const res = await fetch(url, { headers: { 'Accept-Language': 'pt-BR' } });
            if (!res.ok) return [];
            const data = await res.json();
            return Array.isArray(data) ? data : [];
        } catch (e) {
            console.error("[geo] Nominatim error:", e);
            return [];
        }
    }

    async function arcgisSearch(query, bias, allowCities) {
        let url = `https://geocode.arcgis.com/arcgis/rest/services/World/GeocodeServer/findAddressCandidates?f=json` +
                  `&singleLine=${encodeURIComponent(query)}&sourceCountry=BRA&maxLocations=5&outFields=*`;
        if (bias) url += `&location=${bias.lng},${bias.lat}`;
        try {
            const res = await fetch(url);
            if (!res.ok) return [];
            const data = await res.json();
            if (!data || !data.candidates) return [];

            const excluded = ["State", "Country", "Subregion", "Zone"];
            if (!allowCities) excluded.push("Locality");

            return data.candidates
                .filter(cand => isInBrazil(cand.location.y, cand.location.x))
                .filter(cand => !excluded.includes(cand.attributes?.Addr_type || ""))
                .map(cand => ({
                    lat: cand.location.y,
                    lon: cand.location.x,
                    display_name: cand.attributes.LongLabel || cand.address,
                    address: {
                        road: cand.attributes.StAddr || (cand.attributes.StPreType ? (cand.attributes.StPreType + " " + cand.attributes.StName) : cand.attributes.StName) || "",
                        house_number: cand.attributes.AddNum || "",
                        suburb: cand.attributes.Nbrhd || cand.attributes.District || "",
                        city: cand.attributes.City || "",
                        state: cand.attributes.RegionAbbr || cand.attributes.Region || "",
                        country_code: "br"
                    }
                }));
        } catch (e) {
            console.error("[geo] ArcGIS error:", e);
            return [];
        }
    }

    /**
     * Busca endereços em todo o Brasil.
     * @param {string} query
     * @param {{near?: {lat:number,lng:number}, allowCities?: boolean, autocomplete?: boolean}} options
     *        allowCities=false descarta resultados que são apenas o centro de uma cidade
     *        (útil quando é preciso um endereço exato, ex.: relatar ocorrência).
     *        autocomplete=true para sugestões enquanto o usuário digita.
     */
    async function searchAddress(query, options = {}) {
        const allowCities = options.allowCities !== false;
        const bias = getBiasPoint(options.near);
        const numberMatch = query.match(/\b\d+\b/);
        const typedNumber = numberMatch ? numberMatch[0] : null;
        const accept = (list) => list.filter(item => isAcceptable(item, allowCities));

        const fromBackend = await backendSearch(query, bias, !!options.autocomplete);
        if (fromBackend !== null) {
            return { results: accept(fromBackend), typedNumber };
        }

        let results = accept(await nominatimSearch(query, bias));

        if (results.length === 0 && typedNumber) {
            const withoutNumber = query.replace(/\b\d+\b/g, '').replace(/,\s*,/g, ',').trim();
            if (withoutNumber.length > 2) {
                results = accept(await nominatimSearch(withoutNumber, bias));
            }
        }

        if (results.length === 0) {
            const mainPart = query.split(/[,-]/)[0].trim();
            if (mainPart.length > 2 && mainPart !== query) {
                results = accept(await nominatimSearch(mainPart, bias));
            }
        }

        if (results.length === 0) {
            results = await arcgisSearch(query, bias, allowCities);
        }

        return { results, typedNumber };
    }

    /** Bairro/cidade/UF de uma coordenada (backend; Nominatim como alternativa). */
    async function reverseLocality(lat, lng) {
        try {
            const res = await fetch(`/api/geocode/reverso?lat=${lat}&lng=${lng}`);
            if (res.ok) {
                const item = await res.json();
                return { district: suburbOf(item.address), city: cityOf(item.address), state: stateAbbr(item.address) };
            }
        } catch (e) { /* backend indisponível: usa o Nominatim */ }

        try {
            const res = await fetch(`https://nominatim.openstreetmap.org/reverse?format=json&lat=${lat}&lon=${lng}&zoom=16&addressdetails=1`, {
                headers: { 'Accept-Language': 'pt-BR' }
            });
            if (!res.ok) return null;
            const data = await res.json();
            if (!data || !data.address) return null;
            return { district: suburbOf(data.address), city: cityOf(data.address), state: stateAbbr(data.address) };
        } catch (e) {
            console.warn("[geo] Reverse geocoding error:", e);
            return null;
        }
    }

    window.safeMap = window.safeMap || {};
    window.safeMap.geo = {
        BRAZIL_BOUNDS,
        isInBrazil,
        stateAbbr,
        formatAddress,
        shortLabel,
        searchAddress,
        reverseLocality
    };
})();
