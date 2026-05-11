/**
 * Gerenciamento de localização do usuário em tempo real
 */

let userMarker = null;
let userWatchId = null;
let userCurrentLatLng = null;

const userIcon = L.divIcon({
    className: 'user-location-marker',
    html: `
        <div class="user-location" style="position: relative; top: 0; left: 0;">
            <div class="location-pulse"></div>
            <div class="location-dot"></div>
        </div>
    `,
    iconSize: [40, 40],
    iconAnchor: [20, 20]
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

function startTracking() {
    if ('geolocation' in navigator) {
        navigator.geolocation.getCurrentPosition(
            (pos) => {
                updateUserLocation(pos.coords.latitude, pos.coords.longitude);
                map.setView([pos.coords.latitude, pos.coords.longitude], 16);
            },
            (err) => {
                console.warn('Permissão de localização negada ou falhou. Usando fallback (Paulista).', err);
            },
            { enableHighAccuracy: true, timeout: 5000, maximumAge: 0 }
        );

        userWatchId = navigator.geolocation.watchPosition(
            (pos) => {
                updateUserLocation(pos.coords.latitude, pos.coords.longitude);
            },
            (err) => {
                console.warn('Erro ao rastrear localização.', err);
            },
            { enableHighAccuracy: true, timeout: 10000, maximumAge: 0 }
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
                    updateUserLocation(pos.coords.latitude, pos.coords.longitude);
                    map.flyTo([pos.coords.latitude, pos.coords.longitude], 16);
                }
            );
        }
    }
});

startTracking();

window.safeMap.userCurrentLatLng = () => userCurrentLatLng;
window.safeMap.updateUserLocation = updateUserLocation;
