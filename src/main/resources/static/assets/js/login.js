// Password Toggle
const togglePassword = document.getElementById('togglePassword');
const passwordInput = document.getElementById('password');
const toggleIcon = document.getElementById('toggleIcon');

if (togglePassword) {
    togglePassword.addEventListener('click', function() {
        const type = passwordInput.getAttribute('type') === 'password' ? 'text' : 'password';
        passwordInput.setAttribute('type', type);

        toggleIcon.classList.toggle('bi-eye');
        toggleIcon.classList.toggle('bi-eye-slash');
    });
}

// Form Submission
const loginForm = document.getElementById('loginForm');
const loginBtn = document.getElementById('loginBtn');
const loadingOverlay = document.getElementById('loadingOverlay');

if (loginForm) {
    loginForm.addEventListener('submit', function(e) {
        const role = document.getElementById('role').value;
        const username = document.getElementById('username').value.trim();
        const password = document.getElementById('password').value;

        if (!role || !username || !password) {
            e.preventDefault();
            alert('Please fill in all fields');
            return;
        }

        // Show loading state
        loginBtn.disabled = true;
        loginBtn.innerHTML = '<div class="spinner"></div><span>Signing In...</span>';
        if (loadingOverlay) {
            loadingOverlay.classList.add('active');
        }
    });
}

// Auto-hide alerts
document.addEventListener('DOMContentLoaded', function() {
    const alerts = document.querySelectorAll('.alert');
    alerts.forEach(alert => {
        setTimeout(() => {
            alert.style.display = 'none';
        }, 5000);
    });
});