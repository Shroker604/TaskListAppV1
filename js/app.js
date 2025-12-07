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

});
