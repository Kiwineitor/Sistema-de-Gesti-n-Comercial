(function () {
    'use strict';
    document.addEventListener('DOMContentLoaded', function () {
        document.querySelectorAll('[data-password-toggle]').forEach(function (boton) {
            const campo = document.getElementById(boton.dataset.passwordToggle);
            if (!campo) return;
            boton.addEventListener('click', function () {
                const visible = campo.type === 'password';
                campo.type = visible ? 'text' : 'password';
                boton.textContent = visible ? 'Ocultar' : 'Mostrar';
                boton.setAttribute('aria-pressed', String(visible));
                campo.focus();
            });
        });

        const form = document.getElementById('cuenta-form');
        if (!form) return;
        const nueva = form.elements.nueva;
        const confirmacion = form.elements.confirmacion;
        const terminos = form.elements.aceptaTerminos;

        function revisar() {
            if (nueva) {
                const valor = nueva.value;
                let mensaje = '';
                if (valor.length < Number(nueva.getAttribute('minlength') || 8)) mensaje = 'La contraseña es muy corta.';
                else if (!/\p{L}/u.test(valor) || !/\d/.test(valor)) mensaje = 'Combina letras y números.';
                nueva.setCustomValidity(mensaje);
                confirmacion.setCustomValidity(confirmacion.value === valor ? '' : 'Las contraseñas no coinciden.');
            }
            if (terminos) terminos.setCustomValidity(terminos.checked ? '' : 'Debes aceptar los términos para continuar.');
        }

        form.addEventListener('input', function () {
            form.querySelectorAll('input').forEach(function (campo) { campo.setCustomValidity(''); });
        });
        form.addEventListener('submit', function (event) {
            revisar();
            if (!form.checkValidity()) {
                event.preventDefault();
                form.reportValidity();
                return;
            }
            form.querySelectorAll('.password-wrap input').forEach(function (campo) { campo.type = 'password'; });
        });
    });
})();
