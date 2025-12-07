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

    // Save Task
    saveBtn.addEventListener('click', () => {
        const text = taskInput.value.trim();
        if (!text) return;

        const newTask = {
            id: Date.now().toString(),
            text: text,
            completed: false,
            createdAt: new Date().toISOString()
        };

        tasks = Store.saveTask(newTask);
        Render.tasks(tasks, taskListEl);
        closeModal();
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

    // --- Settings & AI ---
    const settingsBtn = document.getElementById('settings-btn');
    const settingsModal = document.getElementById('settings-modal');
    const closeSettingsBtn = document.getElementById('close-settings-btn');
    const apiKeyInput = document.getElementById('api-key-input');
    const aiBtn = document.getElementById('ai-task-btn');

    // Settings Modal
    settingsBtn.addEventListener('click', () => {
        apiKeyInput.value = Store.getApiKey();
        settingsModal.classList.remove('hidden');
    });

    closeSettingsBtn.addEventListener('click', () => {
        Store.saveApiKey(apiKeyInput.value.trim());
        settingsModal.classList.add('hidden');
    });

    // AI Generation
    aiBtn.addEventListener('click', async () => {
        const text = taskInput.value.trim();
        if (!text) {
            alert("Please type a description first!");
            return;
        }

        const apiKey = Store.getApiKey();
        if (!apiKey) {
            alert("Please add your Gemini API Key in Settings first.");
            closeModal();
            settingsBtn.click();
            return;
        }

        // Loading State
        const originalIcon = aiBtn.innerHTML;
        aiBtn.innerHTML = '<div style="width:20px; height:20px; border:2px solid #a78bfa; border-top-color:transparent; border-radius:50%; animation:spin 1s linear infinite;"></div>';

        try {
            const result = await AI.generateTask(text, apiKey);
            if (result && result.text) {
                // Update input with clean task
                taskInput.value = result.text;
                // Highlight success
                taskInput.style.borderColor = '#a78bfa';
                setTimeout(() => taskInput.style.borderColor = '', 1000);
            }
        } catch (error) {
            alert("AI Error: " + error.message);
        } finally {
            aiBtn.innerHTML = originalIcon;
        }
    });

});

// Add spin animation
const style = document.createElement('style');
style.innerHTML = `
@keyframes spin { 0% { transform: rotate(0deg); } 100% { transform: rotate(360deg); } }
`;
document.head.appendChild(style);
