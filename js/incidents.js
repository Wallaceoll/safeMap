


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

function loadIncidents() {
    const userReports = JSON.parse(localStorage.getItem('userReports') || '[]');
    const groupedReports = {};

    staticIncidents.forEach(incident => {
        const key = `${incident.lat.toFixed(4)}-${incident.lng.toFixed(4)}`;
        const wReports = incident.type === 'danger' ? Math.floor(incident.reports * 0.7) : Math.floor(incident.reports * 0.4);
        const lReports = incident.reports - wReports;

        const womenDescs = [
            "Assédio verbal no ponto de ônibus à noite.",
            "Homem de atitude suspeita seguindo pedestres.",
            "Importunação nas proximidades do metrô.",
            "Relato de perseguição no início da noite.",
            "Falta de iluminação pública favorecendo abordagens hostis."
        ];
        
        const lgbtDescs = [
            "Olhares e comentários ofensivos direcionados a casal homoafetivo.",
            "Agressão verbal homofóbica.",
            "Hostilidade em estabelecimento comercial da região.",
            "Abordagem intimidadora direcionada a pessoa trans.",
            "Comentários preconceituosos de transeuntes."
        ];

        const allReports = [];
        
        for (let i = 0; i < wReports; i++) {
            const date = new Date();
            date.setHours(date.getHours() - (i * 4 + 2));
            allReports.push({
                ...incident,
                typeName: incident.type === 'danger' ? 'Alto Risco' : 'Aviso',
                targetGroup: 'women',
                description: womenDescs[i % womenDescs.length],
                date: date.toISOString()
            });
        }
        
        for (let i = 0; i < lReports; i++) {
            const date = new Date();
            date.setHours(date.getHours() - (i * 6 + 5));
            allReports.push({
                ...incident,
                typeName: incident.type === 'danger' ? 'Alto Risco' : 'Aviso',
                targetGroup: 'lgbt',
                description: lgbtDescs[i % lgbtDescs.length],
                date: date.toISOString()
            });
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

        if (!groupedReports[key]) {
            groupedReports[key] = {
                lat: parseFloat(report.lat),
                lng: parseFloat(report.lng),
                type: report.type || 'warning',
                typeName: report.typeName || (report.type === 'danger' ? 'Alto Risco' : 'Aviso'),
                address: report.address || 'Localização relatada',
                district: report.district || 'São Paulo',
                reports: 1,
                womenReports: report.targetGroup === 'women' ? 1 : 0,
                lgbtReports: report.targetGroup === 'lgbt' ? 1 : 0,
                allReports: [report]
            };
        } else {
            groupedReports[key].reports++;
            if (report.targetGroup === 'women') groupedReports[key].womenReports++;
            if (report.targetGroup === 'lgbt') groupedReports[key].lgbtReports++;
            if (report.type === 'danger') {
                groupedReports[key].type = 'danger';
                groupedReports[key].typeName = report.typeName || 'Alto Risco';
            }
            if (!groupedReports[key].allReports) groupedReports[key].allReports = [];
            groupedReports[key].allReports.push(report);
        }
    });

    window.safeMap.groupedReports = groupedReports;
    window.safeMap.renderIncidents();
    map.addLayer(incidentLayer);

    setTimeout(() => {
        if (window.lucide) window.lucide.createIcons();
    }, 100);
}

window.safeMap.renderIncidents = function () {
    incidentLayer.clearLayers();

    const womenActive = document.querySelector('.category-chip[data-type="incidents"]:nth-child(1).active');
    const lgbtActive = document.querySelector('.category-chip[data-type="incidents"]:nth-child(2).active');

    Object.values(window.safeMap.groupedReports).forEach(report => {
        let relevantReports = 0;
        let riskValueForColor = 0;
        let womenR = report.womenReports || 0;
        let lgbtR = report.lgbtReports || 0;

        if (womenActive && lgbtActive) {
            relevantReports = womenR + lgbtR;
            riskValueForColor = womenR + lgbtR;
        } else if (womenActive) {
            relevantReports = womenR;
            riskValueForColor = womenR;
        } else if (lgbtActive) {
            relevantReports = lgbtR;
            riskValueForColor = lgbtR;
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

    if (reportCountSpan) {
        const count = data.relevantReports || 0;
        const pluralText = count === 1 ? 'relato recente' : 'relatos recentes';
        reportCountSpan.textContent = `${count} ${pluralText}`;

        const reportRow = reportCountSpan.parentElement.parentElement;
        if (reportRow) {
            const navigateToAll = (e) => {
                if (e) e.stopPropagation();
                const reportsToShow = data.allReports || [data];
                localStorage.setItem('currentDetailReports', JSON.stringify(reportsToShow));
                window.location.href = 'detalhes-relato.html';
            };

            reportRow.style.cursor = 'pointer';
            reportRow.onclick = navigateToAll;

            const arrow = reportRow.querySelector('[data-lucide="chevron-right"], .lucide-chevron-right, svg.lucide-chevron-right');
            if (arrow) {
                arrow.style.cursor = 'pointer';
                arrow.onclick = navigateToAll;
            }
        }
    }

    const detailsBtn = incidentBlock.querySelector('.btn-help-center');
    if (detailsBtn) {
        detailsBtn.onclick = (e) => {
            e.preventDefault();
            const reportsToShow = data.allReports || [data];
            localStorage.setItem('currentDetailReports', JSON.stringify(reportsToShow));
            window.location.href = 'detalhes-relato.html';
        };
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
