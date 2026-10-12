/**
 * Gerenciamento de localização do usuário em tempo real
 */

let userMarker = null;
let userWatchId = null;
let userCurrentLatLng = null;

const userIcon = L.divIcon({
    className: 'user-location-marker',
    html: `
        <div class="user-location" style="position: absolute; top: 50%; left: 50%;">
            <div class="location-pulse"></div>
            <div class="location-dot"></div>
        </div>
    `,
    iconSize: [22, 22],
    iconAnchor: [11, 11]
});

function updateUserLocation(lat, lng) {
    userCurrentLatLng = [lat, lng];

    if (!userMarker) {
        userMarker = L.marker(userCurrentLatLng, {
            icon: userIcon,
            pane: 'user',
            zIndexOffset: 1000
        }).addTo(map);
    } else {
        userMarker.setLatLng(userCurrentLatLng);
    }
}

// Primeira posição: aceita a localização por rede/Wi-Fi (rápida, também em computadores sem GPS)
const OPCOES_POSICAO_RAPIDA = { enableHighAccuracy: false, timeout: 15000, maximumAge: 60000 };
// Acompanhamento contínuo: refina com o GPS quando o aparelho tiver
const OPCOES_RASTREIO = { enableHighAccuracy: true, timeout: 30000, maximumAge: 5000 };

let mapaCentralizadoNoUsuario = false;

function descreverErroLocalizacao(err) {
    switch (err?.code) {
        case 1: return 'permissão de localização negada';
        case 2: return 'localização indisponível no aparelho';
        case 3: return 'tempo esgotado ao obter a localização';
        default: return 'falha ao obter a localização';
    }
}

function aoObterPosicao(pos) {
    updateUserLocation(pos.coords.latitude, pos.coords.longitude);
    // Centraliza só na primeira posição, venha ela da busca rápida ou do rastreio
    if (!mapaCentralizadoNoUsuario) {
        mapaCentralizadoNoUsuario = true;
        map.setView(userCurrentLatLng, 16);
    }
}

function startTracking() {
    if ('geolocation' in navigator) {
        navigator.geolocation.getCurrentPosition(
            aoObterPosicao,
            (err) => {
                console.warn(`Localização: ${descreverErroLocalizacao(err)}. Usando fallback (Paulista) até o rastreio obter uma posição.`, err);
            },
            OPCOES_POSICAO_RAPIDA
        );

        userWatchId = navigator.geolocation.watchPosition(
            aoObterPosicao,
            (err) => {
                // Tempo esgotado no rastreio não é definitivo: o navegador continua tentando
                console.warn(`Rastreio de localização: ${descreverErroLocalizacao(err)}.`, err);
            },
            OPCOES_RASTREIO
        );
    } else {
        console.warn('Geolocalização não suportada no navegador.');
    }
}

document.getElementById('btn-locate')?.addEventListener('click', () => {
    if (userCurrentLatLng) {
        map.flyTo(userCurrentLatLng, 16, { animate: true, duration: 1.5 });
    } else {
        const feedback = document.createElement('div');
        feedback.style.cssText = "position: fixed; top: 100px; left: 50%; transform: translateX(-50%); background: rgba(0,0,0,0.8); color: white; padding: 8px 16px; border-radius: 20px; font-size: 12px; z-index: 10000;";
        feedback.textContent = "Buscando sua localização...";
        document.body.appendChild(feedback);
        setTimeout(() => feedback.remove(), 2000);
        
        if ('geolocation' in navigator) {
            navigator.geolocation.getCurrentPosition(
                (pos) => {
                    mapaCentralizadoNoUsuario = true;
                    updateUserLocation(pos.coords.latitude, pos.coords.longitude);
                    map.flyTo([pos.coords.latitude, pos.coords.longitude], 16);
                },
                (err) => {
                    console.warn(`Localização: ${descreverErroLocalizacao(err)}.`, err);
                },
                OPCOES_POSICAO_RAPIDA
            );
        }
    }
});

startTracking();

window.safeMap.userCurrentLatLng = () => userCurrentLatLng;
window.safeMap.updateUserLocation = updateUserLocation;
