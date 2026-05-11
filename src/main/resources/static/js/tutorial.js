/**
 * SafeMap Tutorial System
 * Handles onboarding for guest users
 */

const tutorialSteps = [
    { title: "1. Entenda o mapa", text: "Visualize zonas de risco, locais de apoio e sua localização atual de forma rápida e clara.", icon: "map", target: "map-center" },
    { title: "2. Pesquisa Rápida", text: "Pesquise bairros ou regiões para visualizar o nível de risco e ocorrências naquelas áreas.", icon: "search", target: ".search-bar" },
    { title: "3. Área de Login", text: "Clique aqui para acessar sua conta ou criar uma nova e liberar recursos exclusivos.", icon: "user", target: ".btn-user" },
    { title: "4. Filtros Especiais", text: "Filtre as ocorrências exibidas no mapa entre casos relacionados a mulheres, comunidade LGBT+ ou ambos.", icon: "sliders", target: '.category-chip[data-type="incidents"]' },
    { title: "5. Locais de Apoio", text: "Filtre para visualizar apenas os locais de suporte e acolhimento próximos a você.", icon: "shield", target: '.category-chip[data-type="support"]' },
    { title: "6. Níveis de Risco", text: "Clique para expandir e entender o que as cores significam (Mulheres, LGBT+, Ambos).", icon: "info", target: "#btn-legend-trigger" },
    { title: "7. Central de Ajuda", text: "Acesso direto a contatos de emergência e canais de suporte rápido.", icon: "life-buoy", target: ".btn-help-center" },
    { title: "8. Navegação", text: "Use os botões para alternar entre o Mapa, Relatar ocorrências e Apoio.", icon: "navigation", target: ".bottom-nav" }
];

let currentStep = 0;

function initTutorial() {
    const overlay = document.getElementById('tutorial-overlay');
    const spotlight = document.getElementById('tutorial-spotlight');
    const nextBtn = document.getElementById('btn-next');
    const skipBtn = document.getElementById('btn-skip');

    if (!overlay || !spotlight || !nextBtn || !skipBtn) return;

    function updateStep() {
        const step = tutorialSteps[currentStep];
        const card = document.querySelector('.tutorial-card');

        document.getElementById('tutorial-title').textContent = step.title;
        document.getElementById('tutorial-text').textContent = step.text;
        document.getElementById('step-counter').textContent = `${currentStep + 1}/${tutorialSteps.length}`;

        const iconBox = document.querySelector('.tutorial-icon');
        iconBox.innerHTML = `<i data-lucide="${step.icon}"></i>`;
        if (window.lucide) lucide.createIcons();

        const indicator = document.getElementById('step-indicator');
        indicator.innerHTML = '';
        for (let i = 0; i < tutorialSteps.length; i++) {
            const dot = document.createElement('div');
            dot.className = `dot-step ${i === currentStep ? 'active' : ''}`;
            indicator.appendChild(dot);
        }

        if (step.target) {
            const container = document.querySelector('.app-container');
            const containerRect = container ? container.getBoundingClientRect() : { top: 0, left: 0 };

            if (step.target === 'map-center') {
                const size = 300;
                spotlight.style.width = `${size}px`;
                spotlight.style.height = `${size - 80}px`;
                spotlight.style.top = `40%`;
                spotlight.style.left = `calc(50% - ${size / 2}px)`;
                spotlight.style.borderRadius = '50%';
                spotlight.style.display = 'block';
                card.classList.remove('at-top');
                card.classList.add('at-bottom');
            } else {
                const allTargets = document.querySelectorAll(step.target);
                const targets = Array.from(allTargets).filter(el => el.offsetWidth > 0 && el.offsetHeight > 0);

                if (targets.length > 0) {
                    let minX = Infinity, minY = Infinity, maxX = -Infinity, maxY = -Infinity;
                    targets.forEach(el => {
                        const rect = el.getBoundingClientRect();
                        minX = Math.min(minX, rect.left);
                        minY = Math.min(minY, rect.top);
                        maxX = Math.max(maxX, rect.right);
                        maxY = Math.max(maxY, rect.bottom);
                    });

                    spotlight.style.width = `${maxX - minX + 12}px`;
                    spotlight.style.height = `${maxY - minY + 12}px`;
                    spotlight.style.top = `${minY - containerRect.top - 6}px`;
                    spotlight.style.left = `${minX - containerRect.left - 6}px`;
                    spotlight.style.borderRadius = getComputedStyle(targets[0]).borderRadius;
                    spotlight.style.display = 'block';

                    if (minY > window.innerHeight / 2) {
                        card.classList.remove('at-bottom');
                        card.classList.add('at-top');
                    } else {
                        card.classList.remove('at-top');
                        card.classList.add('at-bottom');
                    }
                }
            }
        } else {
            spotlight.style.display = 'none';
            card.classList.remove('at-top');
            card.classList.add('at-bottom');
        }

        nextBtn.textContent = currentStep === tutorialSteps.length - 1 ? 'ENTENDI' : 'PRÓXIMO';
    }

    nextBtn.addEventListener('click', () => {
        if (currentStep < tutorialSteps.length - 1) {
            currentStep++;
            updateStep();
        } else {
            overlay.classList.remove('active');
            localStorage.setItem('tutorialShown', 'true');
        }
    });

    skipBtn.addEventListener('click', () => {
        overlay.classList.remove('active');
        localStorage.setItem('tutorialShown', 'true');
    });

    const isLoggedIn = localStorage.getItem('isLoggedIn') === 'true';
    const tutorialShown = localStorage.getItem('tutorialShown') === 'true';

    if (!isLoggedIn && !tutorialShown) {
        setTimeout(() => {
            overlay.classList.add('active');
            updateStep();
        }, 1000);
    }
}

document.addEventListener('DOMContentLoaded', initTutorial);
