(function () {
    'use strict';
    document.addEventListener('DOMContentLoaded', function () {
        const menus = Array.from(document.querySelectorAll('.sesion-menu'));
        if (!menus.length) return;

        function abrir(menu, abierto) {
            const panel = menu.querySelector('.sm-panel');
            panel.hidden = !abierto;
            menu.querySelector('.sm-toggle').setAttribute('aria-expanded', String(abierto));
            menu.classList.toggle('open', abierto);
        }

        menus.forEach(function (menu, indice) {
            const toggle = menu.querySelector('.sm-toggle');
            const panel = menu.querySelector('.sm-panel');
            panel.id = `sesion-menu-panel-${indice}`;
            toggle.setAttribute('aria-controls', panel.id);
            toggle.addEventListener('click', function () {
                const abrirlo = panel.hidden;
                menus.forEach(otro => abrir(otro, false));
                abrir(menu, abrirlo);
                if (abrirlo) panel.querySelector('a, button')?.focus();
            });
        });

        document.addEventListener('click', function (e) {
            menus.forEach(menu => {
                if (!menu.querySelector('.sm-panel').hidden && !menu.contains(e.target)) abrir(menu, false);
            });
        });
        document.addEventListener('keydown', function (e) {
            if (e.key !== 'Escape') return;
            menus.forEach(menu => {
                if (menu.querySelector('.sm-panel').hidden) return;
                abrir(menu, false);
                menu.querySelector('.sm-toggle').focus();
            });
        });
    });
})();
