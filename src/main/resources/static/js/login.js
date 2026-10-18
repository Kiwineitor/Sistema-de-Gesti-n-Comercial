(function () {
    'use strict';
    document.addEventListener('DOMContentLoaded', function () {
        const campo = document.getElementById('password');
        const boton = document.getElementById('password-toggle');
        if (!campo || !boton) return;
        boton.addEventListener('click', function () {
            const visible = campo.type === 'password';
            campo.type = visible ? 'text' : 'password';
            boton.textContent = visible ? 'Ocultar' : 'Mostrar';
            boton.setAttribute('aria-pressed', String(visible));
            boton.setAttribute('aria-label', visible ? 'Ocultar contraseña' : 'Mostrar contraseña');
            campo.focus();
        });
        campo.form.addEventListener('submit', function () {
            campo.type = 'password';
        });
    });
})();
