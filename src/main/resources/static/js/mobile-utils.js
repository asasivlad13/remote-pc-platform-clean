function isMobileDevice() {
    return window.innerWidth <= 900 ||
        /Android|iPhone|iPad|iPod|Mobile/i.test(navigator.userAgent);
}

function isTabletDevice() {
    return window.innerWidth > 768 &&
        window.innerWidth <= 1200 &&
        /Android|iPad|Tablet/i.test(navigator.userAgent);
}

function applyMobileClass() {
    if (!document.body) {
        return;
    }

    if (isMobileDevice()) {
        document.body.classList.add('mobile-device');
    } else {
        document.body.classList.remove('mobile-device');
    }

    if (window.innerWidth > window.innerHeight) {
        document.body.classList.add('landscape-mode');
        document.body.classList.remove('portrait-mode');
    } else {
        document.body.classList.add('portrait-mode');
        document.body.classList.remove('landscape-mode');
    }
}

function showOnlyOnMobile(selector) {
    const elements = document.querySelectorAll(selector);

    elements.forEach(element => {
        element.style.display = isMobileDevice() ? '' : 'none';
    });
}

function goMobileBack(defaultUrl = '/pcs.html') {
    const scenarioModal = document.querySelector('.scenario-modal-backdrop');

    if (scenarioModal && scenarioModal.style.display === 'flex') {
        scenarioModal.style.display = 'none';
        return;
    }

    if (window.history.length > 1) {
        window.history.back();
        return;
    }

    window.location.href = defaultUrl;
}

function addMobileBackButton(defaultUrl = '/pcs.html', text = '← Назад') {
    if (!isMobileDevice()) {
        return;
    }

    if (document.getElementById('globalMobileBackButton')) {
        return;
    }

    const button = document.createElement('button');
    button.id = 'globalMobileBackButton';
    button.className = 'mobile-back-button';
    button.innerText = text;
    button.onclick = function () {
        goMobileBack(defaultUrl);
    };

    document.body.appendChild(button);
}

window.addEventListener('load', applyMobileClass);
window.addEventListener('resize', applyMobileClass);
window.addEventListener('orientationchange', () => {
    setTimeout(applyMobileClass, 300);
});
