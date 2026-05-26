/**
 * SafeMap - routing.js
 * Gerencia a lógica do Painel de Rota Segura (Alternativa 2) integrado com o backend.
 */

(function () {
    let routeLayers = [];
    let routeMarkers = [];
    let originCoords = null;
    let destCoords = null;
    let debounceTimerOrigin = null;
    let debounceTimerDest = null;
    let selectedRoute = null;
    let selectedRouteIsSafe = true;
    let activeSafePolyline = null;
    let activeDirectPolyline = null;

    const btnRouteTrigger = document.getElementById('btn-route-trigger');
    const routePanel = document.getElementById('route-panel');
    const closeRoutePanel = document.getElementById('close-route-panel');
    const routeOrigin = document.getElementById('route-origin');
    const routeDest = document.getElementById('route-dest');
    const routeOriginSuggestions = document.getElementById('route-origin-suggestions');
    const routeDestSuggestions = document.getElementById('route-dest-suggestions');
    const btnCalculateRoute = document.getElementById('btn-calculate-route');
    const routeResults = document.getElementById('route-results');
    const btnRouteGps = document.getElementById('btn-route-gps');
    const btnStartRoute = document.getElementById('btn-start-route');
    const activeRouteBar = document.getElementById('active-route-bar');
    const activeRouteDestName = document.getElementById('active-route-dest-name');
    const activeRouteMeta = document.getElementById('active-route-meta');
    const btnActiveMaps = document.getElementById('btn-active-maps');
    const btnActiveCancel = document.getElementById('btn-active-cancel');
    const bottomActions = document.querySelector('.bottom-actions');

    if (!btnRouteTrigger || !routePanel || !closeRoutePanel || !routeOrigin || !routeDest) {
        console.warn("Elementos do painel de rotas não encontrados.");
        return;
    }

    btnRouteTrigger.addEventListener('click', () => {
        document.getElementById('legend-overlay')?.classList.remove('active');
        document.getElementById('details-overlay')?.classList.remove('active');
        
        routePanel.classList.add('active');
        
        routeOrigin.value = "";
        const gpsLatLng = window.safeMap.userCurrentLatLng ? window.safeMap.userCurrentLatLng() : null;
        if (gpsLatLng) {
            originCoords = { lat: gpsLatLng[0], lng: gpsLatLng[1] };
        } else {
            originCoords = null;
        }
        
        routeDest.value = "";
        destCoords = null;
        routeResults.style.display = "none";
        routeResults.innerHTML = "";
        clearRouteLayers();
    });

    if (btnRouteGps) {
        btnRouteGps.addEventListener('click', async () => {
            const originalVal = routeOrigin.value;
            routeOrigin.value = "Buscando localização...";
            
            const gpsLatLng = window.safeMap.userCurrentLatLng ? window.safeMap.userCurrentLatLng() : null;
            if (!gpsLatLng) {
                if ('geolocation' in navigator) {
                    navigator.geolocation.getCurrentPosition(
                        async (pos) => {
                            const lat = pos.coords.latitude;
                            const lng = pos.coords.longitude;
                            originCoords = { lat, lng };
                            await resolveAndSetOriginAddress(lat, lng);
                        },
                        (err) => {
                            routeOrigin.value = originalVal;
                            alert("Por favor, permita o acesso à localização no navegador.");
                        }
                    );
                } else {
                    routeOrigin.value = originalVal;
                    alert("Geolocalização não é suportada pelo seu navegador.");
                }
            } else {
                const lat = gpsLatLng[0];
                const lng = gpsLatLng[1];
                originCoords = { lat, lng };
                await resolveAndSetOriginAddress(lat, lng);
            }
        });
    }

    async function resolveAndSetOriginAddress(lat, lng) {
        if (window.safeMap.getAddressFromCoords) {
            const geoData = await window.safeMap.getAddressFromCoords(lat, lng);
            if (geoData) {
                routeOrigin.value = `${geoData.street} - ${geoData.district}, SP`;
            } else {
                routeOrigin.value = "Minha Localização";
            }
        } else {
            routeOrigin.value = "Minha Localização";
        }
    }

    closeRoutePanel.addEventListener('click', () => {
        routePanel.classList.remove('active');
        clearRouteLayers();
    });

    routePanel.addEventListener('click', (e) => {
        if (e.target === routePanel) {
            routePanel.classList.remove('active');
            clearRouteLayers();
        }
    });

    routeOrigin.addEventListener('input', () => {
        clearTimeout(debounceTimerOrigin);
        const query = routeOrigin.value.trim();
        if (query.length < 4) {
            routeOriginSuggestions.style.display = 'none';
            return;
        }

        debounceTimerOrigin = setTimeout(() => {
            fetchSuggestions(query, routeOriginSuggestions, (lat, lng, name) => {
                routeOrigin.value = name;
                originCoords = { lat, lng };
                routeOriginSuggestions.style.display = 'none';
            });
        }, 500);
    });

    routeDest.addEventListener('input', () => {
        clearTimeout(debounceTimerDest);
        const query = routeDest.value.trim();
        if (query.length < 4) {
            routeDestSuggestions.style.display = 'none';
            return;
        }

        debounceTimerDest = setTimeout(() => {
            fetchSuggestions(query, routeDestSuggestions, (lat, lng, name) => {
                routeDest.value = name;
                destCoords = { lat, lng };
                routeDestSuggestions.style.display = 'none';
            });
        }, 500);
    });

    document.addEventListener('click', (e) => {
        if (!routeOrigin.contains(e.target) && !routeOriginSuggestions.contains(e.target)) {
            routeOriginSuggestions.style.display = 'none';
        }
        if (!routeDest.contains(e.target) && !routeDestSuggestions.contains(e.target)) {
            routeDestSuggestions.style.display = 'none';
        }
    });

    async function fetchSuggestions(query, suggestionsBox, onSelect) {
        try {
            const url = `https://nominatim.openstreetmap.org/search?format=json&q=${encodeURIComponent(query)}+sao+paulo&limit=5&countrycodes=br&addressdetails=1&viewbox=-46.826,-23.356,-46.365,-24.008&bounded=1`;
            const response = await fetch(url, { headers: { 'User-Agent': 'SafeMap/1.0', 'Accept-Language': 'pt-BR' } });
            let data = [];
            if (response.ok) {
                data = await response.json();
            }
            
            let spResults = Array.isArray(data) ? data.filter(item => {
                const lat = parseFloat(item.lat);
                const lon = parseFloat(item.lon);
                const insideSP = lat >= -24.008 && lat <= -23.356 && lon >= -46.826 && lon <= -46.365;
                if (!insideSP) return false;

                // Evita sugerir a cidade inteira/estado como um todo para rota
                if (item.class === "boundary" || ["administrative", "city", "state", "country", "municipality"].includes(item.type)) {
                    return false;
                }

                const addr = item.address || {};
                const city = addr.city || addr.town || addr.municipality || addr.city_district || addr.county || "";
                const cityLower = city.toLowerCase();
                const displayNameLower = (item.display_name || "").toLowerCase();
                return cityLower.includes("são paulo") || 
                       cityLower.includes("sao paulo") || 
                       displayNameLower.includes("são paulo") || 
                       displayNameLower.includes("sao paulo");
            }) : [];

            // Fallback para ArcGIS Geocoding se Nominatim falhar ou não achar resultados
            if (spResults.length === 0) {
                try {
                    const arcgisUrl = `https://geocode.arcgis.com/arcgis/rest/services/World/GeocodeServer/findAddressCandidates?f=json&singleLine=${encodeURIComponent(query + ", Sao Paulo, Brazil")}&maxLocations=5&outFields=*&searchExtent=-46.826,-24.008,-46.365,-23.356`;
                    const res = await fetch(arcgisUrl);
                    if (res.ok) {
                        const arcgisData = await res.json();
                        if (arcgisData && arcgisData.candidates) {
                            spResults = arcgisData.candidates
                                .filter(cand => {
                                    const lat = cand.location.y;
                                    const lon = cand.location.x;
                                    const insideSP = lat >= -24.008 && lat <= -23.356 && lon >= -46.826 && lon <= -46.365;
                                    if (!insideSP) return false;

                                    // Ignora resultados genéricos (cidade, estado, país)
                                    const addrType = cand.attributes?.Addr_type || "";
                                    if (["Locality", "State", "Country", "Subregion"].includes(addrType)) return false;

                                    const city = cand.attributes?.City || "";
                                    const region = cand.attributes?.Region || "";
                                    return city.toLowerCase().includes("são paulo") || 
                                           city.toLowerCase().includes("sao paulo") ||
                                           region.toLowerCase().includes("são paulo") || 
                                           region.toLowerCase().includes("sao paulo");
                                })
                                .map(cand => ({
                                    lat: cand.location.y,
                                    lon: cand.location.x,
                                    display_name: cand.attributes.LongLabel || cand.address,
                                    address: {
                                        road: cand.attributes.StAddr || (cand.attributes.StPreType ? (cand.attributes.StPreType + " " + cand.attributes.StName) : cand.attributes.StName) || "",
                                        house_number: cand.attributes.AddNum || "",
                                        suburb: cand.attributes.Nbrhd || cand.attributes.District || "",
                                        city: cand.attributes.City || "São Paulo",
                                        state: cand.attributes.RegionAbbr || "SP"
                                    }
                                }));
                        }
                    }
                } catch (arcgisErr) {
                    console.error("ArcGIS geocoding suggestions fallback error:", arcgisErr);
                }
            }

            if (spResults.length > 0) {
                suggestionsBox.innerHTML = '';
                spResults.forEach(item => {
                    const road = item.address.road || item.address.pedestrian || item.display_name.split(',')[0];
                    const suburb = item.address.suburb || item.address.neighbourhood || 'São Paulo';
                    const displayName = `${road} - ${suburb}, SP`;

                    const div = document.createElement('div');
                    div.innerHTML = `<i data-lucide="map-pin" size="12" style="margin-right:6px; opacity:0.6;"></i> ${displayName}`;
                    div.addEventListener('click', () => {
                        onSelect(parseFloat(item.lat), parseFloat(item.lon), displayName);
                    });
                    suggestionsBox.appendChild(div);
                });
                suggestionsBox.style.display = 'block';
                if (window.lucide) window.lucide.createIcons();
            } else {
                suggestionsBox.style.display = 'none';
            }
        } catch (error) {
            console.error("Erro ao buscar sugestões Nominatim:", error);
        }
    }

    btnCalculateRoute.addEventListener('click', async () => {
        if (!destCoords) {
            alert("Por favor, selecione um destino válido da lista.");
            return;
        }

        if (!originCoords || routeOrigin.value.trim() === "") {
            const gpsLatLng = window.safeMap.userCurrentLatLng ? window.safeMap.userCurrentLatLng() : null;
            if (gpsLatLng) {
                originCoords = { lat: gpsLatLng[0], lng: gpsLatLng[1] };
            } else {
                originCoords = { lat: -23.56168, lng: -46.65598 };
                routeOrigin.value = "Av. Paulista (Fallback)";
            }
        }

        btnCalculateRoute.disabled = true;
        btnCalculateRoute.innerHTML = `<i data-lucide="loader" class="animate-spin" size="18"></i> <span>Calculando...</span>`;
        if (window.lucide) window.lucide.createIcons();

        try {
            const url = `/api/rotas?origemLat=${originCoords.lat}&origemLng=${originCoords.lng}&destinoLat=${destCoords.lat}&destinoLng=${destCoords.lng}`;
            const response = await fetch(url);
            
            if (!response.ok) {
                throw new Error("Erro na comunicação com a API de rotas do backend.");
            }

            const data = await response.json();
            const routes = data.routes;

            if (!routes || routes.length === 0) {
                alert("Nenhuma rota para pedestres foi encontrada pelo OSRM.");
                btnCalculateRoute.disabled = false;
                btnCalculateRoute.innerHTML = `<i data-lucide="navigation" size="18"></i> <span>Traçar Rota Segura</span>`;
                if (window.lucide) window.lucide.createIcons();
                return;
            }

            clearRouteLayers();

            routes.sort((a, b) => (a.riskScore || 0) - (b.riskScore || 0));

            activeSafePolyline = null;
            activeDirectPolyline = null;

            const safeRoute = routes[0];
            const safeRisk = Math.round(safeRoute.riskScore || 0);

            activeSafePolyline = L.geoJSON(safeRoute.geometry, {
                style: {
                    color: '#10B981',
                    weight: 6,
                    opacity: 0.9
                }
            }).addTo(window.safeMap.map);
            routeLayers.push(activeSafePolyline);

            const startMarker = L.circleMarker([originCoords.lat, originCoords.lng], {
                radius: 8,
                fillColor: '#5A36A3',
                color: '#FFFFFF',
                weight: 2,
                fillOpacity: 1
            }).addTo(window.safeMap.map).bindPopup("Partida");
            
            const endMarker = L.marker([destCoords.lat, destCoords.lng]).addTo(window.safeMap.map).bindPopup("Destino");
            
            routeMarkers.push(startMarker);
            routeMarkers.push(endMarker);

            window.safeMap.map.fitBounds(activeSafePolyline.getBounds(), { padding: [40, 40] });

            let directRoute = null;
            let directRisk = 0;
            if (routes.length > 1) {
                directRoute = routes[1];
                directRisk = Math.round(directRoute.riskScore || 0);

                activeDirectPolyline = L.geoJSON(directRoute.geometry, {
                    style: {
                        color: directRisk > safeRisk ? '#EF4444' : '#9CA3AF',
                        weight: 4,
                        opacity: 0.5,
                        dashArray: '5, 8'
                    }
                }).addTo(window.safeMap.map);
                routeLayers.push(activeDirectPolyline);
            }

            let resultsHtml = '';
            
            const safeTime = Math.round(safeRoute.duration / 60);
            const safeDistance = (safeRoute.distance / 1000).toFixed(1);

            resultsHtml += `
                <div class="route-option-card safe selected" id="card-route-safe">
                    <div class="route-info-left">
                        <div class="route-name">Rota Recomendada (Segura)</div>
                        <div class="route-meta">${safeTime} min • ${safeDistance} km</div>
                    </div>
                    <div class="route-info-right">
                        <div class="route-risk safe-text">${safeRisk} alertas no caminho</div>
                    </div>
                </div>
            `;

            if (directRoute) {
                const directTime = Math.round(directRoute.duration / 60);
                const directDistance = (directRoute.distance / 1000).toFixed(1);

                resultsHtml += `
                    <div class="route-option-card ${directRisk > safeRisk ? 'direct' : ''}" id="card-route-direct">
                        <div class="route-info-left">
                            <div class="route-name">Rota Direta / Rápida</div>
                            <div class="route-meta">${directTime} min • ${directDistance} km</div>
                        </div>
                        <div class="route-info-right">
                            <div class="route-risk ${directRisk > safeRisk ? 'danger-text' : ''}">${directRisk} alertas no caminho</div>
                        </div>
                    </div>
                `;
            }

            routeResults.innerHTML = resultsHtml;
            routeResults.style.display = 'flex';

            selectedRoute = safeRoute;
            selectedRouteIsSafe = true;
            if (btnStartRoute) {
                btnStartRoute.style.display = 'flex';
            }

            function selectRoute(index) {
                if (index === 0) {
                    selectedRoute = safeRoute;
                    selectedRouteIsSafe = true;
                    if (activeSafePolyline) {
                        activeSafePolyline.setStyle({ color: '#10B981', weight: 6, opacity: 0.9 });
                        activeSafePolyline.bringToFront();
                        window.safeMap.map.fitBounds(activeSafePolyline.getBounds(), { padding: [40, 40] });
                    }
                    if (activeDirectPolyline) {
                        activeDirectPolyline.setStyle({
                            color: directRisk > safeRisk ? '#EF4444' : '#9CA3AF',
                            weight: 4,
                            opacity: 0.35,
                            dashArray: '5, 8'
                        });
                    }
                    document.getElementById('card-route-safe')?.classList.add('selected');
                    document.getElementById('card-route-direct')?.classList.remove('selected');
                } else if (index === 1 && activeDirectPolyline) {
                    selectedRoute = directRoute;
                    selectedRouteIsSafe = false;
                    if (activeDirectPolyline) {
                        activeDirectPolyline.setStyle({
                            color: '#EF4444',
                            weight: 6,
                            opacity: 0.9,
                            dashArray: null
                        });
                        activeDirectPolyline.bringToFront();
                        window.safeMap.map.fitBounds(activeDirectPolyline.getBounds(), { padding: [40, 40] });
                    }
                    if (activeSafePolyline) {
                        activeSafePolyline.setStyle({
                            color: '#10B981',
                            weight: 4,
                            opacity: 0.35
                        });
                    }
                    document.getElementById('card-route-direct')?.classList.add('selected');
                    document.getElementById('card-route-safe')?.classList.remove('selected');
                }
            }

            document.getElementById('card-route-safe')?.addEventListener('click', () => selectRoute(0));
            document.getElementById('card-route-direct')?.addEventListener('click', () => selectRoute(1));

            if (activeSafePolyline) {
                activeSafePolyline.on('click', (e) => {
                    L.DomEvent.stopPropagation(e);
                    selectRoute(0);
                });
            }
            if (activeDirectPolyline) {
                activeDirectPolyline.on('click', (e) => {
                    L.DomEvent.stopPropagation(e);
                    selectRoute(1);
                });
            }

        } catch (error) {
            console.error("Erro ao calcular rota:", error);
            alert("Não foi possível traçar a rota. Erro: " + error.message);
        } finally {
            btnCalculateRoute.disabled = false;
            btnCalculateRoute.innerHTML = `<i data-lucide="navigation" size="18"></i> <span>Traçar Rota Segura</span>`;
            if (window.lucide) window.lucide.createIcons();
        }
    });

    function clearRouteLayers() {
        if (btnStartRoute) {
            btnStartRoute.style.display = 'none';
        }
        selectedRoute = null;
        selectedRouteIsSafe = true;
        activeSafePolyline = null;
        activeDirectPolyline = null;

        routeLayers.forEach(layer => {
            if (window.safeMap.map.hasLayer(layer)) {
                window.safeMap.map.removeLayer(layer);
            }
        });
        routeLayers = [];

        routeMarkers.forEach(marker => {
            if (window.safeMap.map.hasLayer(marker)) {
                window.safeMap.map.removeLayer(marker);
            }
        });
        routeMarkers = [];
    }

    if (btnStartRoute) {
        btnStartRoute.addEventListener('click', () => {
            if (!selectedRoute || !originCoords || !destCoords) return;

            routePanel.classList.remove('active');

            if (bottomActions) {
                bottomActions.style.display = 'none';
            }

            if (selectedRouteIsSafe) {
                if (activeDirectPolyline && window.safeMap.map.hasLayer(activeDirectPolyline)) {
                    window.safeMap.map.removeLayer(activeDirectPolyline);
                }
            } else {
                if (activeSafePolyline && window.safeMap.map.hasLayer(activeSafePolyline)) {
                    window.safeMap.map.removeLayer(activeSafePolyline);
                }
            }

            if (activeRouteBar) {
                if (activeRouteDestName) {
                    activeRouteDestName.textContent = routeDest.value || "Destino";
                }
                if (activeRouteMeta) {
                    const time = Math.round(selectedRoute.duration / 60);
                    const dist = (selectedRoute.distance / 1000).toFixed(1);
                    activeRouteMeta.textContent = `${time} min • ${dist} km`;
                }
                activeRouteBar.style.display = 'flex';
            }

            const selectedPolyline = selectedRouteIsSafe ? activeSafePolyline : activeDirectPolyline;
            if (selectedPolyline) {
                window.safeMap.map.fitBounds(selectedPolyline.getBounds(), { padding: [40, 40] });
            }
        });
    }

    if (btnActiveMaps) {
        btnActiveMaps.addEventListener('click', () => {
            if (!selectedRoute || !originCoords || !destCoords) return;
            const origin = `${originCoords.lat},${originCoords.lng}`;
            const destination = `${destCoords.lat},${destCoords.lng}`;
            let url = `https://www.google.com/maps/dir/?api=1&origin=${origin}&destination=${destination}&travelmode=walking`;

            if (selectedRouteIsSafe && selectedRoute.geometry && selectedRoute.geometry.coordinates) {
                const coords = selectedRoute.geometry.coordinates;
                if (coords.length > 4) {
                    const wp1 = coords[Math.floor(coords.length * 0.33)];
                    const wp2 = coords[Math.floor(coords.length * 0.66)];
                    const waypoints = `${wp1[1]},${wp1[0]}|${wp2[1]},${wp2[0]}`;
                    url += `&waypoints=${encodeURIComponent(waypoints)}`;
                }
            }
            window.open(url, '_blank');
        });
    }

    if (btnActiveCancel) {
        btnActiveCancel.addEventListener('click', () => {
            clearRouteLayers();

            if (activeRouteBar) {
                activeRouteBar.style.display = 'none';
            }

            if (bottomActions) {
                bottomActions.style.display = 'flex';
            }
        });
    }

})();
