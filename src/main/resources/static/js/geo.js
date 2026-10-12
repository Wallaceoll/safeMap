/**
 * SafeMap — Geocodificação compartilhada (Brasil inteiro)
 *
 * - searchAddress: busca endereços e lugares (comércios, estações, bares...) em todo o Brasil.
 *   Consulta em paralelo o backend (/api/geocode, Openrouteservice) — ou o Photon, se o backend
 *   não tiver chave — e o ArcGIS, que tem boa cobertura de comércios. Os resultados são unidos,
 *   sem duplicatas, e ordenados pela semelhança com o texto digitado e pela distância do usuário/mapa.
 *   O Nominatim só é usado na busca final (Enter/envio): a política dele proíbe autocompletar.
 * - CEP (ex.: 01310-100) é resolvido pelo ViaCEP.
 * - formatAddress: "Nome do lugar — Rua, Nº - Bairro, Cidade - UF".
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

    // "Estação Oratório" → no OpenStreetMap a estação se chama só "Oratório"
    const STATION_PREFIX = /^\s*(esta[çc][ãa]o|metr[ôo]|terminal|parada|ponto( de [ôo]nibus)?)\s+(d[eoa]s?\s+)?/i;
    const STATION_TYPES = ["station", "halt", "stop", "subway_entrance", "bus_station", "platform", "stop_position"];
    // Palavras que não ajudam a comparar o nome do resultado com o texto digitado
    const STOPWORDS = new Set(["de", "da", "do", "das", "dos", "e", "a", "o", "rua", "r", "av", "avenida",
        "estacao", "metro", "terminal", "parada", "ponto", "perto", "proximo"]);
    const CEP_ONLY = /^\s*(\d{5})-?(\d{3})\s*$/;

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

    function normalize(text) {
        return (text || "").toLowerCase().normalize("NFD").replace(/[̀-ͯ]/g, "")
            .replace(/['’`´]/g, "").replace(/[^a-z0-9]+/g, " ").trim();
    }

    /** Nome do lugar (comércio, estação...), quando o resultado não é só uma rua. */
    function placeName(item) {
        const name = (item && item.name || "").trim();
        if (!name) return "";
        // Nome que é só o endereço ("Avenida Paulista, 1578") não é nome de lugar
        const road = normalize(item.address && item.address.road);
        return road && normalize(name).startsWith(road) ? "" : name;
    }

    /** Nome para exibição: estações ganham o prefixo "Estação" quando a base traz só o nome. */
    function displayName(item) {
        const name = placeName(item);
        if (name && isStation(item) && !/esta[çc][ãa]o|metr[ôo]|terminal/i.test(name)) {
            return `Estação ${name}`;
        }
        return name;
    }

    function addressPart(item, typedNumber) {
        const addr = item.address || {};
        const street = addr.road || addr.pedestrian || addr.construction || addr.path || "";
        // Número digitado só completa endereços, nunca o nome de um lugar
        const number = addr.house_number || addr.building || (placeName(item) ? "" : typedNumber) || "";
        const head = street
            ? `${street}${number ? `, ${number}` : ""}`
            : (placeName(item) ? "" : (item.display_name || "").split(',')[0]);
        const suburb = suburbOf(addr);
        const left = [head, suburb].filter(Boolean).join(' - ');
        const locality = [cityOf(addr), stateAbbr(addr)].filter(Boolean).join(' - ');
        return [left, locality].filter(Boolean).join(', ');
    }

    /** "Nome do lugar — Rua, Nº - Bairro, Cidade - UF" (o nome só aparece para lugares). */
    function formatAddress(item, typedNumber = null) {
        if (!item) return "";
        if (!item.address) return item.display_name || "";
        const name = displayName(item);
        const addr = addressPart(item, typedNumber);
        return name ? (addr ? `${name} — ${addr}` : name) : addr;
    }

    /** Rótulo para listas de sugestões (mesmo formato, sem número digitado). */
    function shortLabel(item) {
        return formatAddress(item, null);
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

    /** fetch com tempo limite: um provedor lento não pode travar a busca inteira. */
    async function fetchComLimite(url, opts = {}, ms = 6000) {
        const controller = new AbortController();
        const timer = setTimeout(() => controller.abort(), ms);
        try {
            return await fetch(url, { ...opts, signal: controller.signal });
        } finally {
            clearTimeout(timer);
        }
    }

    function distanceKm(lat1, lng1, lat2, lng2) {
        const toRad = Math.PI / 180;
        const dLat = (lat2 - lat1) * toRad, dLng = (lng2 - lng1) * toRad;
        const a = Math.sin(dLat / 2) ** 2 + Math.cos(lat1 * toRad) * Math.cos(lat2 * toRad) * Math.sin(dLng / 2) ** 2;
        return 12742 * Math.asin(Math.sqrt(a));
    }

    // ----------------------------------------------------------------
    //  Provedores — todos devolvem itens no formato do Nominatim:
    //  { lat, lon, display_name, name, class, type, address: {road, house_number, suburb, city, state, ...} }
    // ----------------------------------------------------------------

    /** @returns {Promise<Array|null>} resultados, ou null se o backend estiver indisponível/sem chave */
    async function backendSearch(query, bias, autocomplete) {
        let url = `/api/geocode/busca?q=${encodeURIComponent(query)}&autocomplete=${autocomplete}`;
        if (bias) url += `&lat=${bias.lat.toFixed(4)}&lng=${bias.lng.toFixed(4)}`;
        try {
            const res = await fetchComLimite(url, {}, 5000);
            if (!res.ok) return null;
            const data = await res.json();
            return Array.isArray(data) ? data : null;
        } catch (e) {
            console.warn("[geo] Backend de busca indisponível:", e.message);
            return null;
        }
    }

    /** Photon (komoot): busca gratuita sobre o OpenStreetMap, feita para autocompletar. */
    async function photonSearch(query, bias) {
        let url = `https://photon.komoot.io/api/?q=${encodeURIComponent(query)}&limit=10&lang=default`;
        if (bias) url += `&lat=${bias.lat.toFixed(4)}&lon=${bias.lng.toFixed(4)}`;
        try {
            const res = await fetchComLimite(url, {}, 6000);
            if (!res.ok) return [];
            const data = await res.json();
            return (data.features || [])
                .filter(f => f.properties && f.properties.countrycode === "BR")
                .map(f => {
                    const p = f.properties;
                    const [lon, lat] = f.geometry.coordinates;
                    return {
                        lat, lon,
                        name: p.name || "",
                        class: p.osm_key, type: p.osm_value,
                        display_name: [p.name, p.street, p.district, p.city, p.state].filter(Boolean).join(", "),
                        address: {
                            road: p.street || (p.osm_key === "highway" ? p.name : "") || "",
                            house_number: p.housenumber || "",
                            suburb: p.district || p.locality || "",
                            city: p.city || p.county || "",
                            state: p.state || "",
                            postcode: p.postcode || "",
                            country_code: "br"
                        }
                    };
                });
        } catch (e) {
            console.warn("[geo] Photon error:", e.message);
            return [];
        }
    }

    async function nominatimSearch(query, bias) {
        let url = `https://nominatim.openstreetmap.org/search?format=json&q=${encodeURIComponent(query)}` +
                  `&limit=10&countrycodes=br&addressdetails=1&namedetails=1`;
        if (bias) {
            // Prioriza (sem restringir) resultados próximos ao mapa/usuário
            const d = 0.5;
            url += `&viewbox=${bias.lng - d},${bias.lat + d},${bias.lng + d},${bias.lat - d}&bounded=0`;
        }
        try {
            const res = await fetchComLimite(url, { headers: { 'Accept-Language': 'pt-BR' } }, 6000);
            if (!res.ok) return [];
            const data = await res.json();
            return Array.isArray(data)
                ? data.map(i => ({ ...i, name: i.name || (i.namedetails && i.namedetails.name) || "" }))
                : [];
        } catch (e) {
            console.error("[geo] Nominatim error:", e);
            return [];
        }
    }

    async function arcgisSearch(query, bias, allowCities) {
        let url = `https://geocode.arcgis.com/arcgis/rest/services/World/GeocodeServer/findAddressCandidates?f=json` +
                  `&singleLine=${encodeURIComponent(query)}&sourceCountry=BRA&maxLocations=6&outFields=*`;
        if (bias) url += `&location=${bias.lng},${bias.lat}`;
        try {
            const res = await fetchComLimite(url, {}, 5000);
            if (!res.ok) return [];
            const data = await res.json();
            if (!data || !data.candidates) return [];

            const excluded = ["State", "Country", "Subregion", "Zone"];
            if (!allowCities) excluded.push("Locality");

            return data.candidates
                .filter(cand => isInBrazil(cand.location.y, cand.location.x))
                .filter(cand => !excluded.includes(cand.attributes?.Addr_type || ""))
                .map(cand => {
                    const a = cand.attributes;
                    const isPoi = a.Addr_type === "POI";
                    let road = a.StAddr || (a.StPreType ? (a.StPreType + " " + a.StName) : a.StName) || "";
                    if (a.AddNum && road.endsWith(" " + a.AddNum)) road = road.slice(0, -(a.AddNum.length + 1));
                    return {
                        lat: cand.location.y,
                        lon: cand.location.x,
                        name: isPoi ? (a.PlaceName || "") : "",
                        class: isPoi ? "poi" : "address",
                        type: isPoi ? normalize(a.Type).replace(/ /g, "_") : (a.Addr_type || "").toLowerCase(),
                        display_name: a.LongLabel || cand.address,
                        address: {
                            road,
                            house_number: a.AddNum || "",
                            suburb: a.Nbrhd || a.District || "",
                            city: a.City || "",
                            state: a.RegionAbbr || a.Region || "",
                            postcode: a.Postal || "",
                            country_code: "br"
                        }
                    };
                });
        } catch (e) {
            console.error("[geo] ArcGIS error:", e);
            return [];
        }
    }

    // ----------------------------------------------------------------
    //  União e ordenação
    // ----------------------------------------------------------------

    function isStation(item) {
        const type = String(item.type || "");
        return STATION_TYPES.includes(type) || /station|estacao|metro/.test(type) ||
               item.class === "railway" || item.class === "public_transport";
    }

    /** Fração das palavras digitadas que aparecem no nome/rua/bairro do resultado (0 a 1). */
    function matchScore(item, tokens) {
        if (!tokens.length) return 0;
        const addr = item.address || {};
        const alvo = " " + normalize([placeName(item), addr.road, suburbOf(addr), cityOf(addr)].join(" ")) + " ";
        const achados = tokens.filter(t => alvo.includes(" " + t) || alvo.includes(t + " ")).length;
        return achados / tokens.length;
    }

    const STREET_PREFIX = /^\s*(rua|r\.|av\.?|avenida|alameda|al\.|travessa|tv\.|estrada|rodovia|pra[çc]a|largo|viela)\s/i;

    function rankAndDedupe(items, query, bias, wantsStation) {
        const tokens = normalize(query).split(" ").filter(t => t && !STOPWORDS.has(t));
        const partes = query.split(",").map(p => normalize(p)).filter(Boolean);
        // "Rua da Aurora 100, Recife" → frase principal "rua da aurora"; demais partes: cidade/UF/bairro
        const frase = normalize(query.split(",")[0].replace(/\b\d+\b/g, "")).replace(STATION_PREFIX, "").trim();
        const fraseSemPrefixo = normalize(query.split(",")[0].replace(STATION_PREFIX, "").replace(/\b\d+\b/g, ""));
        const localidades = partes.slice(1).map(p => p.replace(/\b\d+\b/g, "").trim()).filter(p => p.length >= 2);
        const pareceEndereco = STREET_PREFIX.test(query) && /\b\d+\b/.test(query);
        // Cidade citada sem vírgula ("Shopping Iguatemi Fortaleza"): cidades dos resultados que aparecem no texto.
        // Só vale quando não há vírgula — senão "Rua São Paulo 100, Recife" confundiria rua com cidade.
        const consulta = " " + normalize(query) + " ";
        const citadas = localidades.length ? new Set() : new Set(items
            .map(i => normalize(cityOf(i.address)))
            .filter(c => c.length >= 4 && consulta.includes(" " + c + " ")));

        const scored = items.map((item, ordem) => {
            const lat = parseFloat(item.lat), lon = parseFloat(item.lon);
            const addr = item.address || {};
            const nome = normalize(placeName(item));
            const rua = normalize(addr.road);
            let score = matchScore(item, tokens);

            if (nome && tokens.some(t => nome.includes(t))) score += 0.25;
            // Frase digitada aparece inteira no nome ou na rua ("rua da aurora" ≠ "rua dona ana aurora")
            if (frase.length >= 3 && (nome.includes(frase) || rua.includes(frase) ||
                (fraseSemPrefixo.length >= 3 && (nome.includes(fraseSemPrefixo) || rua.includes(fraseSemPrefixo))))) score += 0.5;
            // Nome idêntico ao digitado ("MASP" ≠ "MASP Suplicy")
            if (nome && (nome === normalize(query) || nome === fraseSemPrefixo)) score += 0.5;
            // Cidade/UF citada depois da vírgula tem de bater ("…, Recife", "…, BH - MG")
            if (localidades.length || citadas.size) {
                const cidade = normalize(cityOf(addr)), uf = normalize(stateAbbr(addr));
                const bate = localidades.length
                    ? localidades.some(l => l === cidade || l === uf || cidade.includes(l) || l.split(" ").includes(uf))
                    : citadas.has(cidade);
                score += bate ? 0.75 : -0.5;
            }
            if (pareceEndereco && nome) score -= 0.25; // procurou endereço: endereço antes de comércio
            if (wantsStation && isStation(item)) score += 0.5;
            return {
                item, ordem, lat, lon,
                score: Math.round(score * 4) / 4, // agrupa notas parecidas; dentro do grupo vale a distância
                dist: bias ? distanceKm(bias.lat, bias.lng, lat, lon) : 0
            };
        });

        scored.sort((a, b) => (b.score - a.score) || (a.dist - b.dist) || (a.ordem - b.ordem));

        // Mesmo lugar vindo de dois provedores: mesmo rótulo-base a menos de 150 m
        const unicos = [];
        for (const s of scored) {
            const chave = normalize(placeName(s.item) || s.item.address?.road || s.item.display_name);
            const repetido = unicos.some(u => chave &&
                normalize(placeName(u.item) || u.item.address?.road || u.item.display_name) === chave &&
                distanceKm(u.lat, u.lon, s.lat, s.lon) < 0.15);
            if (!repetido) unicos.push(s);
        }
        return unicos.map(s => s.item);
    }

    /**
     * CEP → ViaCEP (rua, bairro, cidade) → coordenada da rua.
     * @returns {Promise<Array|null>} null se o CEP não existe; [] se não foi possível localizá-lo
     *          (aí a busca comum continua, ex.: ViaCEP fora do ar).
     */
    async function cepSearch(cep, bias, autocomplete) {
        let v;
        try {
            const res = await fetchComLimite(`https://viacep.com.br/ws/${cep}/json/`, {}, 4000);
            if (!res.ok) return [];
            v = await res.json();
        } catch (e) {
            console.warn("[geo] ViaCEP error:", e.message);
            return [];
        }
        if (!v || v.erro) return null;

        const cidadeUf = `${v.localidade} - ${v.uf}`;
        const daCidade = (lista) => lista.filter(i =>
            isInBrazil(parseFloat(i.lat), parseFloat(i.lon)) &&
            normalize(cityOf(i.address)) === normalize(v.localidade));
        // Com bairro primeiro; sem bairro se não achar (o nome do bairro dos Correios
        // nem sempre coincide com o dos mapas). O Photon (lento) só entra por último.
        const textos = [...new Set([
            [v.logradouro, v.bairro, cidadeUf].filter(Boolean).join(", "),
            [v.logradouro, cidadeUf].filter(Boolean).join(", ")
        ])];
        let candidatos = [];
        for (const texto of textos) {
            const [fromBackend, arc] = await Promise.all([
                backendSearch(texto, bias, autocomplete), arcgisSearch(texto, bias, true)
            ]);
            candidatos = daCidade([...(fromBackend || []), ...arc]);
            if (candidatos.length) break;
        }
        if (!candidatos.length) candidatos = daCidade(await photonSearch(textos[textos.length - 1], bias));
        const melhor = candidatos[0];
        if (!melhor) return [];

        return [{
            lat: melhor.lat, lon: melhor.lon, name: "", class: "postcode", type: "postcode",
            display_name: textos[0],
            address: {
                road: v.logradouro || "", suburb: v.bairro || "", city: v.localidade,
                state: v.uf, postcode: v.cep, country_code: "br"
            }
        }];
    }

    /**
     * Busca endereços e lugares em todo o Brasil.
     * @param {string} query
     * @param {{near?: {lat:number,lng:number}, allowCities?: boolean, autocomplete?: boolean,
     *          onUpdate?: function(Array, string|null)}} options
     *        allowCities=false descarta resultados que são apenas o centro de uma cidade
     *        (útil quando é preciso um endereço exato, ex.: relatar ocorrência).
     *        autocomplete=true para sugestões enquanto o usuário digita.
     *        onUpdate é chamado a cada provedor que responde, com a lista parcial já ordenada
     *        (para mostrar sugestões sem esperar o provedor mais lento).
     */
    async function searchAddress(query, options = {}) {
        const allowCities = options.allowCities !== false;
        const autocomplete = !!options.autocomplete;
        const bias = getBiasPoint(options.near);
        const accept = (list) => list.filter(item => isAcceptable(item, allowCities));

        const cep = query.match(CEP_ONLY);
        if (cep) {
            const doCep = await cepSearch(cep[1] + cep[2], bias, autocomplete);
            if (doCep === null) return { results: [], typedNumber: null }; // CEP inexistente: nada de "parecido"
            const results = accept(doCep);
            if (results.length) return { results, typedNumber: null };
        }

        const numberMatch = query.match(/\b\d+\b/);
        const typedNumber = numberMatch ? numberMatch[0] : null;

        const wantsStation = STATION_PREFIX.test(query);
        const semPrefixo = wantsStation ? query.replace(STATION_PREFIX, "").trim() : "";
        const variantes = semPrefixo.length > 2 ? [query, semPrefixo] : [query];

        // OSM (backend com Openrouteservice, ou Photon sem chave) + ArcGIS, em paralelo
        const buscarOsm = async (q) => {
            const doBackend = await backendSearch(q, bias, autocomplete);
            return doBackend !== null ? doBackend : photonSearch(q, bias);
        };
        const coletados = [];
        const avisar = (lista) => {
            coletados.push(...accept(lista));
            if (options.onUpdate && coletados.length) {
                options.onUpdate(rankAndDedupe(coletados, query, bias, wantsStation), typedNumber);
            }
        };
        await Promise.all([
            ...variantes.map(buscarOsm),
            arcgisSearch(query, bias, allowCities),
            ...(semPrefixo.length > 2 ? [arcgisSearch(semPrefixo, bias, allowCities)] : [])
        ].map(p => p.then(avisar)));
        let results = coletados.slice();

        // Busca final (Enter/envio) com poucos resultados: tenta também o Nominatim
        if (!autocomplete && results.length < 3) {
            let nominatim = accept(await nominatimSearch(semPrefixo || query, bias));
            if (nominatim.length === 0 && typedNumber) {
                const withoutNumber = query.replace(/\b\d+\b/g, '').replace(/,\s*,/g, ',').trim();
                if (withoutNumber.length > 2) nominatim = accept(await nominatimSearch(withoutNumber, bias));
            }
            results = results.concat(nominatim);
        }

        return { results: rankAndDedupe(results, query, bias, wantsStation), typedNumber };
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

    /**
     * Mostra uma lista de sugestões. Usa textContent: nomes de lugares vêm de bases
     * públicas (OpenStreetMap etc.) e não podem ser interpretados como HTML.
     * @param {{label: function, onSelect: function, limit?: number, iconSize?: number,
     *          iconStyle?: string, itemClass?: string}} opts
     */
    function renderSuggestions(box, items, opts) {
        box.innerHTML = '';
        const lista = (items || []).slice(0, opts.limit || 5);
        if (!lista.length) {
            box.style.display = 'none';
            return;
        }
        lista.forEach(item => {
            const div = document.createElement('div');
            if (opts.itemClass) div.className = opts.itemClass;
            const icon = document.createElement('i');
            icon.setAttribute('data-lucide', 'map-pin');
            icon.setAttribute('size', String(opts.iconSize || 14));
            if (opts.iconStyle) icon.style.cssText = opts.iconStyle;
            const texto = document.createElement('span');
            texto.textContent = ' ' + opts.label(item);
            div.append(icon, texto);
            div.addEventListener('click', () => opts.onSelect(item));
            box.appendChild(div);
        });
        box.style.display = 'block';
        if (window.lucide) window.lucide.createIcons();
    }

    window.safeMap = window.safeMap || {};
    window.safeMap.geo = {
        BRAZIL_BOUNDS,
        isInBrazil,
        stateAbbr,
        formatAddress,
        shortLabel,
        searchAddress,
        renderSuggestions,
        reverseLocality
    };
})();
