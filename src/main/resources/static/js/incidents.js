


const incidentLayer = L.markerClusterGroup({
    clusterPane: 'incidents',
    maxClusterRadius: 40,
    iconCreateFunction: function (cluster) {
        return L.divIcon({
            html: `<div style="background-color: #EF4444; color: white; border-radius: 50%; width: 30px; height: 30px; display: flex; align-items: center; justify-content: center; font-weight: bold; font-size: 14px; box-shadow: 0 2px 5px rgba(0,0,0,0.3);">${cluster.getChildCount()}</div>`,
            className: 'custom-cluster-icon',
            iconSize: L.point(30, 30)
        });
    }
});

const riskZoneLayer = L.layerGroup();
if (!map.getPane('riskZones')) {
    map.createPane('riskZones');
    map.getPane('riskZones').style.zIndex = 350;
}
map.addLayer(riskZoneLayer);

const staticIncidents = [
    { lat: -23.5855, lng: -46.6836, type: 'danger', address: 'Av. Brigadeiro Faria Lima, 1200', district: 'Itaim Bibi', reports: 8 },
    { lat: -23.5616, lng: -46.6559, type: 'danger', address: 'Av. Paulista, 1578', district: 'Bela Vista', reports: 12 },
    { lat: -23.5815, lng: -46.6826, type: 'danger', address: 'Rua Amauri, 45', district: 'Itaim Bibi', reports: 5 },
    { lat: -23.5641, lng: -46.6669, type: 'warning', address: 'R. Oscar Freire, 379', district: 'Jardins', reports: 3 },
    { lat: -23.5601, lng: -46.6609, type: 'warning', address: 'Al. Santos, 2200', district: 'Cerqueira César', reports: 4 },
    { lat: -23.5615, lng: -46.6974, type: 'danger', address: 'R. dos Pinheiros, 500', district: 'Pinheiros', reports: 7 },
    { lat: -23.6000, lng: -46.6600, type: 'warning', address: 'Av. Ibirapuera, 2100', district: 'Moema', reports: 2 }
];

function createIncidentIcon(type) {
    return L.divIcon({
        className: 'custom-incident-marker',
        html: `
            <div class="incident-marker ${type}" style="position: relative; top: 0; left: 0;">
                <div class="marker-glow"></div>
                <div class="marker-core"><i data-lucide="alert-circle" size="14"></i></div>
            </div>
        `,
        iconSize: [24, 24],
        iconAnchor: [12, 12]
    });
}

function buildGroupedReports(incidentsArray, userReports) {
    const groupedReports = {};

    incidentsArray.forEach(incident => {
        const key = `${incident.lat.toFixed(4)}-${incident.lng.toFixed(4)}`;
        const wReports = incident.type === 'danger' ? Math.floor(incident.reports * 0.7) : Math.floor(incident.reports * 0.4);
        const lReports = incident.reports - wReports;

        const womenReportsData = [
            { typeName: "Assédio", desc: "Assédio verbal no ponto de ônibus à noite." },
            { typeName: "Assédio", desc: "Homem de atitude suspeita seguindo pedestres." },
            { typeName: "Importunação Sexual", desc: "Importunação nas proximidades do metrô." },
            { typeName: "Assédio", desc: "Relato de perseguição no início da noite." },
            { typeName: "Assédio", desc: "Falta de iluminação pública favorecendo abordagens hostis." }
        ];
        
        const lgbtReportsData = [
            { typeName: "Agressão Verbal", desc: "Olhares e comentários ofensivos direcionados a casal homoafetivo." },
            { typeName: "Agressão Verbal", desc: "Agressão verbal homofóbica." },
            { typeName: "Assédio", desc: "Hostilidade em estabelecimento comercial da região." },
            { typeName: "Agressão Verbal", desc: "Abordagem intimidadora direcionada a pessoa trans." },
            { typeName: "Agressão Verbal", desc: "Comentários preconceituosos de transeuntes." }
        ];

        const allReports = [];
        
        for (let i = 0; i < wReports; i++) {
            const date = new Date();
            date.setHours(date.getHours() - (i * 4 + 2));
            const dataItem = womenReportsData[i % womenReportsData.length];
            allReports.push({ ...incident, typeName: dataItem.typeName, targetGroup: 'women', description: dataItem.desc, date: date.toISOString() });
        }
        
        for (let i = 0; i < lReports; i++) {
            const date = new Date();
            date.setHours(date.getHours() - (i * 6 + 5));
            const dataItem = lgbtReportsData[i % lgbtReportsData.length];
            allReports.push({ ...incident, typeName: dataItem.typeName, targetGroup: 'lgbt', description: dataItem.desc, date: date.toISOString() });
        }

        groupedReports[key] = {
            ...incident,
            typeName: incident.type === 'danger' ? 'Alto Risco' : 'Aviso',
            womenReports: wReports,
            lgbtReports: lReports,
            allReports: allReports
        };
    });

    userReports.forEach(report => {
        if (!report.lat || !report.lng) return;

        const key = `${parseFloat(report.lat).toFixed(4)}-${parseFloat(report.lng).toFixed(4)}`;
        const isWomen = Array.isArray(report.targetGroup) ? report.targetGroup.includes('women') : report.targetGroup === 'women';
        const isLgbt  = Array.isArray(report.targetGroup) ? report.targetGroup.includes('lgbt')  : report.targetGroup === 'lgbt';

        if (!groupedReports[key]) {
            groupedReports[key] = {
                lat: parseFloat(report.lat), lng: parseFloat(report.lng),
                type: report.type || 'warning',
                typeName: report.typeName || (report.type === 'danger' ? 'Alto Risco' : 'Aviso'),
                address: report.address || 'Localização relatada',
                district: report.district || 'São Paulo',
                reports: 1,
                womenReports: isWomen ? 1 : 0,
                lgbtReports:  isLgbt  ? 1 : 0,
                allReports: [report]
            };
        } else {
            groupedReports[key].reports++;
            if (isWomen) groupedReports[key].womenReports++;
            if (isLgbt)  groupedReports[key].lgbtReports++;
            if (report.type === 'danger') { groupedReports[key].type = 'danger'; groupedReports[key].typeName = report.typeName || 'Alto Risco'; }
            if (!groupedReports[key].allReports) groupedReports[key].allReports = [];
            groupedReports[key].allReports.push(report);
        }
    });

    return groupedReports;
}

// Converte ocorrência do banco → formato interno do mapa
function dbOcorrenciaToReport(oc) {
    const isWomen = oc.publicoAlvo && (oc.publicoAlvo.includes('MULHERES') || oc.publicoAlvo.includes('AMBOS'));
    const isLgbt  = oc.publicoAlvo && (oc.publicoAlvo.includes('LGBT')    || oc.publicoAlvo.includes('AMBOS'));
    const isDanger = oc.tipoOcorrencia === 'ASSEDIO' || oc.tipoOcorrencia === 'IMPORTUNACAO_SEXUAL' || oc.tipoOcorrencia === 'AGRESSAO_FISICA';

    const TIPO_LABEL = {
        ASSEDIO: 'Assédio', IMPORTUNACAO_SEXUAL: 'Importunação Sexual',
        AGRESSAO_FISICA: 'Agressão Física', AGRESSAO_VERBAL: 'Agressão Verbal'
    };

    return {
        lat: oc.latitude, lng: oc.longitude,
        type: isDanger ? 'danger' : 'warning',
        typeName: TIPO_LABEL[oc.tipoOcorrencia] || oc.tipoOcorrencia,
        description: oc.descricao || '',
        address: oc.endereco || 'Localização relatada',
        district: 'São Paulo',
        targetGroup: isWomen && isLgbt ? ['women','lgbt'] : isWomen ? ['women'] : isLgbt ? ['lgbt'] : [],
        date: oc.dataHora || oc.criadoEm || new Date().toISOString(),
        reports: 1
    };
}

async function loadIncidents() {
    const userReports = JSON.parse(localStorage.getItem('userReports') || '[]');

    // Tenta buscar do banco; em caso de falha usa só os dados locais
    let dbReports = [];
    try {
        const ocorrencias = await smListarOcorrencias();
        if (Array.isArray(ocorrencias)) {
            dbReports = ocorrencias.map(dbOcorrenciaToReport);
        }
    } catch (err) {
        console.warn('SafeMap: não foi possível carregar ocorrências do banco.', err.message);
    }

    // Mescla: dados estáticos de demonstração + banco + localStorage
    const allDynamic = [...dbReports, ...userReports];
    window.safeMap.groupedReports = buildGroupedReports(staticIncidents, allDynamic);
    window.safeMap.renderIncidents();
    map.addLayer(incidentLayer);

    setTimeout(() => { if (window.lucide) window.lucide.createIcons(); }, 100);
}

window.safeMap.renderIncidents = function () {
    incidentLayer.clearLayers();
    riskZoneLayer.clearLayers();

    const womenActive = document.querySelector('.category-chip[data-type="incidents"]:nth-child(1).active');
    const lgbtActive = document.querySelector('.category-chip[data-type="incidents"]:nth-child(2).active');

    const calculateDistance = (lat1, lon1, lat2, lon2) => {
        const R = 6371;
        const dLat = (lat2 - lat1) * Math.PI / 180;
        const dLon = (lon2 - lon1) * Math.PI / 180;
        const a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
            Math.cos(lat1 * Math.PI / 180) * Math.cos(lat2 * Math.PI / 180) *
            Math.sin(dLon / 2) * Math.sin(dLon / 2);
        const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    };

    const renderedCenters = [];
    const sortedReports = Object.values(window.safeMap.groupedReports)
        .map(report => {
            let areaReports = 0;
            Object.values(window.safeMap.groupedReports).forEach(other => {
                let otherReports = 0;
                if (womenActive && lgbtActive) {
                    otherReports = (other.womenReports || 0) + (other.lgbtReports || 0);
                } else if (womenActive) {
                    otherReports = other.womenReports || 0;
                } else if (lgbtActive) {
                    otherReports = other.lgbtReports || 0;
                }

                if (otherReports > 0 && calculateDistance(report.lat, report.lng, other.lat, other.lng) <= 1.0) {
                    areaReports += otherReports;
                }
            });
            return { ...report, areaReports };
        })
        .filter(r => r.areaReports > 0)
        .sort((a, b) => b.areaReports - a.areaReports);

    sortedReports.forEach(r => {
        const tooClose = renderedCenters.some(c => calculateDistance(r.lat, r.lng, c.lat, c.lng) <= 0.4);
        if (tooClose) return;

        renderedCenters.push({ lat: r.lat, lng: r.lng });

        let color = '#10B981';
        let fillOpacity = 0.08;
        let radius = 350;

        if (r.areaReports >= 10) {
            color = '#EF4444';
            fillOpacity = 0.15;
            radius = 600;
        } else if (r.areaReports >= 4) {
            color = '#F59E0B';
            fillOpacity = 0.12;
            radius = 450;
        }

        const circle = L.circle([r.lat, r.lng], {
            radius: radius,
            color: color,
            weight: 1.5,
            opacity: 0.4,
            fillColor: color,
            fillOpacity: fillOpacity,
            dashArray: '4, 4',
            interactive: false,
            pane: 'riskZones'
        });

        riskZoneLayer.addLayer(circle);
    });

    Object.values(window.safeMap.groupedReports).forEach(report => {
        let relevantReports = 0;
        let womenR = report.womenReports || 0;
        let lgbtR = report.lgbtReports || 0;

        if (womenActive && lgbtActive) {
            relevantReports = womenR + lgbtR;
        } else if (womenActive) {
            relevantReports = womenR;
        } else if (lgbtActive) {
            relevantReports = lgbtR;
        }

        if (relevantReports === 0) return;
        let dynamicType = 'safe';

        if (relevantReports >= 8) {
            dynamicType = 'danger';
        } else if (relevantReports >= 3) {
            dynamicType = 'warning';
        } else {
            dynamicType = 'safe';
        }

        const reportContextual = { ...report, dynamicType, relevantReports };

        const marker = L.marker([report.lat, report.lng], {
            icon: createIncidentIcon(dynamicType),
            pane: 'incidents'
        });

        marker.on('click', () => {
            openIncidentDetails(reportContextual);
        });

        incidentLayer.addLayer(marker);
    });

    incidentLayer.on('animationend', () => window.lucide && window.lucide.createIcons());
};

window.openIncidentDetails = async function (data) {
    const incidentBlock = document.getElementById('incident-details');
    const supportBlock = document.getElementById('support-details');
    const detailsOverlay = document.getElementById('details-overlay');
    const title = incidentBlock.querySelector('h2');
    const descP = incidentBlock.querySelector('p');
    const riskRows = incidentBlock.querySelectorAll('.legend-row');
    const reportCountSpan = incidentBlock.querySelector('span[style*="color: #111827;"]');

    const womenActive = document.querySelector('.category-chip[data-type="incidents"]:nth-child(1).active');
    const lgbtActive = document.querySelector('.category-chip[data-type="incidents"]:nth-child(2).active');

    riskRows.forEach(row => row.style.display = 'none');

    if (womenActive && lgbtActive) {
        riskRows[0].style.display = 'flex';
        riskRows[1].style.display = 'flex';
        riskRows[2].style.display = 'flex';
    } else if (womenActive) {
        riskRows[0].style.display = 'flex';
    } else if (lgbtActive) {
        riskRows[1].style.display = 'flex';
    } else {
        riskRows.forEach(row => row.style.display = 'flex');
    }

    function getRiskLevel(count) {
        let levelText = '';
        if (count >= 8) levelText = '<div class="dot red"></div> Alto';
        else if (count >= 3) levelText = '<div class="dot yellow"></div> Médio';
        else levelText = '<div class="dot green"></div> Baixo';

        return `${levelText} <span style="font-size: 12px; color: #6B7280; margin-left: 8px;">(${count} relatos)</span>`;
    }

    title.textContent = data.address || 'Buscando endereço...';
    descP.textContent = data.district ? `${data.district} — São Paulo, SP` : 'São Paulo, SP';

    riskRows[0].querySelector('.level-item').innerHTML = getRiskLevel(data.womenReports || 0);

    riskRows[1].querySelector('.level-item').innerHTML = getRiskLevel(data.lgbtReports || 0);

    const sumRisk = (data.womenReports || 0) + (data.lgbtReports || 0);
    riskRows[2].querySelector('.level-item').innerHTML = getRiskLevel(sumRisk);

    let dayCount = 0;
    let nightCount = 0;
    const reports = data.allReports || [data];
    reports.forEach(r => {
        if (r.date) {
            const hour = new Date(r.date).getHours();
            if (hour >= 6 && hour < 18) {
                dayCount++;
            } else {
                nightCount++;
            }
        }
    });

    const container = document.getElementById('period-risk-container');
    const iconWrapper = document.getElementById('period-risk-icon-wrapper');
    const textSpan = document.getElementById('period-risk-text');
    const subtextSpan = document.getElementById('period-risk-subtext');

    if (container && iconWrapper && textSpan && subtextSpan) {
        if (nightCount > dayCount) {
            let periodIcon = 'moon';
            let periodColor = '#6366F1';
            let periodBg = '#EEF2FF';
            riskText = 'Área com maior risco no período noturno';
            
            iconWrapper.style.width = '38px';
            iconWrapper.style.borderRadius = '50%';
            iconWrapper.style.gap = '0';
            iconWrapper.style.backgroundColor = periodBg;
            iconWrapper.style.color = periodColor;
            iconWrapper.innerHTML = `<i id="period-risk-icon" data-lucide="${periodIcon}" size="20"></i>`;
        } else if (dayCount > nightCount) {
            let periodIcon = 'sun';
            let periodColor = '#F59E0B';
            let periodBg = '#FEF3C7';
            riskText = 'Área com maior risco no período diurno';
            
            iconWrapper.style.width = '38px';
            iconWrapper.style.borderRadius = '50%';
            iconWrapper.style.gap = '0';
            iconWrapper.style.backgroundColor = periodBg;
            iconWrapper.style.color = periodColor;
            iconWrapper.innerHTML = `<i id="period-risk-icon" data-lucide="${periodIcon}" size="20"></i>`;
        } else {
            riskText = 'Risco semelhante em ambos os períodos';
            
            iconWrapper.style.width = '54px';
            iconWrapper.style.borderRadius = '27px';
            iconWrapper.style.gap = '4px';
            iconWrapper.style.backgroundColor = '#F3F4F6';
            iconWrapper.innerHTML = `
                <i data-lucide="sun" size="16" style="color: #F59E0B;"></i>
                <i data-lucide="moon" size="16" style="color: #6366F1;"></i>
            `;
        }
        textSpan.textContent = riskText;
        subtextSpan.textContent = `Total de ${reports.length} ${reports.length === 1 ? 'relato' : 'relatos'} (${dayCount} de dia / ${nightCount} à noite)`;

        const detailsBtn = incidentBlock.querySelector('.btn-help-center');
        if (detailsBtn) {
            detailsBtn.onclick = (e) => {
                e.preventDefault();
                localStorage.setItem('currentDetailReports', JSON.stringify(reports));
                window.location.href = 'detalhes-relato.html';
            };
        }

        if (window.lucide) window.lucide.createIcons();
    }

    incidentBlock.style.display = 'block';
    supportBlock.style.display = 'none';
    detailsOverlay.classList.add('active');

    if (window.safeMap.getAddressFromCoords && (!data.address || !data.district || data.address === 'Localização relatada')) {
        const geoData = await window.safeMap.getAddressFromCoords(data.lat, data.lng);
        if (geoData) {
            title.textContent = geoData.street;
            descP.textContent = geoData.district ? `${geoData.district} — São Paulo, SP` : 'São Paulo, SP';
        }
    }
};

loadIncidents();

window.safeMap.layers = window.safeMap.layers || {};
window.safeMap.layers.incidents = incidentLayer;
window.safeMap.layers.riskZones = riskZoneLayer;
