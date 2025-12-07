// App.js - Main Controller

document.addEventListener('DOMContentLoaded', () => {
    // State
    let tasks = Store.getTasks();

    // DOM Elements
    const taskListEl = document.getElementById('task-list');
    const fabBtn = document.getElementById('fab-add');
    const modal = document.getElementById('task-modal');
    const saveBtn = document.getElementById('save-task-btn');
    const cancelBtn = document.getElementById('cancel-task-btn');
    const taskInput = document.getElementById('task-input');

    // Initial Render
    Render.tasks(tasks, taskListEl);

    // --- Actions ---

    // Open Modal
    fabBtn.addEventListener('click', () => {
        modal.classList.remove('hidden');
        modal.querySelector('.modal-content').classList.add('slide-up');
        taskInput.value = '';
        setTimeout(() => taskInput.focus(), 100);
    });

    // Close Modal
    const closeModal = () => {
        modal.classList.add('hidden');
    };
    cancelBtn.addEventListener('click', closeModal);
    modal.addEventListener('click', (e) => {
        if (e.target === modal) closeModal();
    });

    // Save Task (AI Default)
    saveBtn.addEventListener('click', async () => {
        const text = taskInput.value.trim();
        if (!text) return;

        // Show Loading state on Save button
        const originalBtnText = saveBtn.innerText;
        saveBtn.innerText = 'Processing...';
        saveBtn.disabled = true;

        try {
            // Always try AI processing first
            const apiKey = Store.getApiKey(); // Will fallback to default in AI.js if empty
            const taskList = await AI.generateTasks(text, apiKey);

            taskList.forEach(taskText => {
                const newTask = {
                    id: Date.now().toString() + Math.random().toString().slice(2, 5), // Unique ID for loop
                    text: taskText,
                    completed: false,
                    createdAt: new Date().toISOString()
                };
                tasks = Store.saveTask(newTask);
            });

            Render.tasks(tasks, taskListEl);
            closeModal();
        } catch (err) {
            // Fallback to simple add if AI fails hard
            alert("AI Error, adding normally: " + err.message);
            const newTask = {
                id: Date.now().toString(),
                text: text,
                completed: false,
                createdAt: new Date().toISOString()
            };
            tasks = Store.saveTask(newTask);
            Render.tasks(tasks, taskListEl);
            closeModal();
        } finally {
            saveBtn.innerText = originalBtnText;
            saveBtn.disabled = false;
        }
    });

    // Enter key to save
    taskInput.addEventListener('keypress', (e) => {
        if (e.key === 'Enter') saveBtn.click();
    });

    // --- Global Event Delegation (for dynamic items) ---
    taskListEl.addEventListener('click', (e) => {
        // Toggle Completion
        const item = e.target.closest('.task-item');
        if (item) {
            const id = item.dataset.id;
            tasks = Store.toggleTask(id);
            Render.tasks(tasks, taskListEl);
        }
    });

    // --- Settings & Theme ---
    const settingsBtn = document.getElementById('settings-btn');
    const settingsModal = document.getElementById('settings-modal');
    const closeSettingsBtn = document.getElementById('close-settings-btn');
    const themeBtn = document.getElementById('theme-toggle-btn');
    const themeLabel = document.getElementById('theme-label');
    const themeIcon = document.getElementById('theme-icon');

    // Theme State
    let currentTheme = Store.getTheme();
    const applyTheme = (theme) => {
        if (theme === 'light') {
            document.body.classList.add('light-mode');
            document.body.classList.remove('dark-mode');
            themeLabel.innerText = 'Light Mode';
            themeIcon.innerText = '☀️';
        } else {
            document.body.classList.add('dark-mode');
            document.body.classList.remove('light-mode');
            themeLabel.innerText = 'Dark Mode';
            themeIcon.innerText = '🌙';
        }
    };
    // Apply on load
    applyTheme(currentTheme);

    // Settings Modal
    settingsBtn.addEventListener('click', () => {
        settingsModal.classList.remove('hidden');
    });

    closeSettingsBtn.addEventListener('click', () => {
        settingsModal.classList.add('hidden');
    });

    // Toggle Theme
    themeBtn.addEventListener('click', () => {
        currentTheme = currentTheme === 'dark' ? 'light' : 'dark';
        applyTheme(currentTheme);
        Store.saveTheme(currentTheme);
    });

    // Hidden AI button (Functionality moved to Save)
    if (aiBtn) aiBtn.style.display = 'none';

});
